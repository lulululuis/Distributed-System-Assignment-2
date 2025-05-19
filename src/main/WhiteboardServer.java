package main;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
//Luis Mauboy - 1684115
public class WhiteboardServer {
    private static final int DEFAULT_PORT = 1234;
    private ServerSocket serverSocket;
    private final UserManager userManager = new UserManager();
    private final List<ShapeData> whiteboardState = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
    private boolean isRunning;
    private String currentManager;

    public void start(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        isRunning = true;
        System.out.println("Server started on port " + port);

        //Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        
        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                threadPool.execute(clientHandler);
            } catch (SocketException e) {
                if (isRunning) {
                    System.err.println("Server socket error: " + e.getMessage());
                }
            }
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String username;
        private boolean isApproved = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                //First message must be username
                ServerMessage usernameMsg = (ServerMessage) in.readObject();
                if (usernameMsg.getType() != ServerMessage.MessageType.USER_JOIN) {
                    throw new ProtocolException("First message must be USER_JOIN");
                }
                this.username = (String) usernameMsg.getData();

                //First user assigned as manager
                userManager.addUser(username, userManager.getUserCount() == 1);
                isApproved = true;
                if (userManager.getUserCount() == 1) {
                	currentManager = username;
                    sendMessage(new ServerMessage(ServerMessage.MessageType.ASSIGN_MANAGER));
                }

                //Send current state to new client
                sendInitialState();
                broadcastUserListUpdate();
                

                //Main message loop
                while (isApproved && isRunning) {
                    ServerMessage message = (ServerMessage) in.readObject();
                    processClientMessage(message);
                }

            } catch (Exception e) {
                System.err.println("Client handling error: " + e.getMessage());
            } finally {
                cleanupClient();
            }
        }

        private void processClientMessage(ServerMessage message) throws IOException, ClassNotFoundException {
            switch (message.getType()) {
                case SHAPE:
                    whiteboardState.add(message.getShape());
                    broadcastExcept(message, this);
                    break;
                    
                case CLEAR_CANVAS:
                	whiteboardState.clear();
                    broadcast(message);
                    break;
                    
                case CHAT_MESSAGE:
                    broadcast(message);
                    break;
                    
                case KICK_NOTIFICATION:
                	if (userManager.isManager(username)) {
                		String userToKick = message.getKickedUsername();
                		if (userToKick != null && !userToKick.equals(username)) {
                			kickUser(userToKick);
                		}
                	}
                	break;
                	
                case SAVE_REQUEST:
                    if (userManager.isManager(username)) {
                        saveWhiteboard((String) message.getData());
                    }
                    break;
                    
                case LOAD_REQUEST:
                	if (userManager.isManager(username)) {
						loadWhiteboard((String) message.getData());
                	}
                	break;
                	
                case APPROVAL_RESPONSE:
                	if (!message.getUsername().equals(username)) {
                		if (!message.isApproved()) {
                			System.exit(0);
                		}
                	}
                	break;
                	
                case FILE_DATA:
                	if (message.getShapes() != null) {
                		whiteboardState.clear();
                		whiteboardState.addAll(message.getShapes());
                		broadcast(message);
                	}
                break;
                	
                default:
                    throw new ProtocolException("Unsupported message type: " + message.getType());
            }
        }

        private void sendInitialState() throws IOException {
            //Send all existing shapes
            synchronized (whiteboardState) {
                for (ShapeData shape : whiteboardState) {
                    sendMessage(new ServerMessage(ServerMessage.MessageType.SHAPE, shape));
                }
            }
            //Send current user list
            sendMessage(new ServerMessage(ServerMessage.MessageType.USER_LIST, userManager.getUsers()));
        }
        

        public void sendMessage(ServerMessage message) throws IOException {
            out.writeObject(message);
            out.flush();
        }
        
        private void cleanupClient() {
        	//If manager disconnects
            if (username != null && username.equals(currentManager)) {
            	System.out.println("Manager disconnected. Shutting down...");
            	shutdown();
            }
            //If other clients disconnect
            if (username != null) {
                userManager.removeUser(username);
                broadcastUserListUpdate();
            }
            clients.remove(this);
            try {
            	if (socket != null && !socket.isClosed()) {
            		socket.close();
            	}
            } catch (IOException e) {
            	System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }
    
    private void broadcast(ServerMessage message) {
    	synchronized(clients) {
    		Iterator<ClientHandler> iterator = clients.iterator();
    		while (iterator.hasNext()) {
    			ClientHandler client = iterator.next();
    			try {
    				//Broadcast to everyone
    				synchronized(client.out) {
    					client.out.writeObject(message);
    					client.out.flush();
    				}
    			} catch (IOException e) {
    				System.err.println("Error broadcasting to " + client.username + ": " + e.getMessage());
    				iterator.remove();
    				client.cleanupClient();
    			}
    		}
    	}
    }

    private synchronized void broadcastExcept(ServerMessage message, ClientHandler exclude) {
        for (ClientHandler client : clients) {
            if (client != exclude && client.isApproved) {
                try {
                    client.sendMessage(message);
                } catch (IOException e) {
                    System.err.println("Error broadcasting to client: " + e.getMessage());
                    clients.remove(client);
                }
            }
        }
    }
    
    private void broadcastUserListUpdate() {
        broadcast(new ServerMessage(ServerMessage.MessageType.USER_LIST, userManager.getUsers()));
    }

    private void kickUser(String usernameToKick) {
        synchronized(clients) {
        	Iterator<ClientHandler> iterator = clients.iterator();
        	while (iterator.hasNext()) {
        		ClientHandler client = iterator.next();
        		if (client.username.equals(usernameToKick)) {
        			try {
        				//Notify client
                        client.sendMessage(new ServerMessage(ServerMessage.MessageType.KICK_NOTIFICATION));
                        
                        //Close their connection
                        client.cleanupClient();
                        iterator.remove();
                        
                        //Update user list
                        userManager.removeUser(usernameToKick);
                        broadcastUserListUpdate();
                        
                        System.out.println("Kicked user: " + usernameToKick);
                        return;
        			} catch (IOException e) {
                    	System.err.println("Error kicking user: " + e.getMessage());
                    }
        		}
        	}
        } System.err.println("User to kick not found: " + usernameToKick);	
    }

    private void saveWhiteboard(String filename) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
            oos.writeObject(whiteboardState);
            broadcast(new ServerMessage(ServerMessage.MessageType.SAVE_RESPONSE, "Whiteboard saved successfully"));
        } catch (IOException e) {
        	broadcast(ServerMessage.createError("Save failed: " + e.getMessage()));
        }
    }
    
    private void loadWhiteboard(String filename) throws ClassNotFoundException {
    	try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filename))) {
    		@SuppressWarnings("unchecked")
			List<ShapeData> shapes = (List<ShapeData>)ois.readObject();
    		whiteboardState.clear();
    		whiteboardState.addAll(shapes);
    		broadcast(new ServerMessage(ServerMessage.MessageType.FILE_DATA, shapes));
    	} catch (IOException e) {
    		broadcast(ServerMessage.createError("Load failed: " + e.getMessage()));
    	}
    }
    
    private void shutdown() {
    	isRunning = false;
    	try {
    		broadcast(new ServerMessage(ServerMessage.MessageType.SERVER_SHUTDOWN));
    		threadPool.shutdown();
    		if (serverSocket != null) {
    			serverSocket.close();
    		}
    		System.exit(0); //Force terminate all clients
    	} catch (IOException e) {
    		System.err.println("Shutdown error: " + e.getMessage());
    	}
    }

    public static void main(String[] args) {
        try {
            int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
            WhiteboardServer server = new WhiteboardServer();
            server.start(port);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number");
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}