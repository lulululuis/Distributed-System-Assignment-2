package main;

import java.io.*;
import java.net.Socket;
import java.util.List;
import javax.swing.*;
//Luis Mauboy - 1684115
public class NetworkManager {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private WhiteboardFrame frame;
    private String username;
    private boolean isConnected = false;

    public NetworkManager(WhiteboardFrame frame) {
        this.frame = frame;
    }
    
    //Connection method
    public void connect(String ip, int port, String username) throws IOException {
        try {
        	this.username = username;
        	socket = new Socket(ip, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            isConnected = true;
            
            //Send join message
            sendMessage(new ServerMessage(ServerMessage.MessageType.USER_JOIN, username));
            
            //Start receiver thread
            new Thread(this::receiveMessages).start();
        } catch (IOException e) {
        	isConnected = false;
        	throw new IOException("Connection failed: " + e.getMessage());
        }
    }
    
    //Message receiving thread
    private void receiveMessages() {
    	try {
    		while (isConnected) {
    			Object received = in.readObject();
    			
    			if (received instanceof ServerMessage) {
    				SwingUtilities.invokeLater(() -> frame.processMessage((ServerMessage)received));
    			}
    		}
    	} catch (Exception e) {
    		if (isConnected) { //Only notify if unexpected disconnect
    			SwingUtilities.invokeLater(() -> frame.showError("Connection lost: " + e.getMessage()));
    		}
    	} finally {
    		closeConnection();
    	}
    }
    
    //Outgoing message methods
    public void sendClearCanvas() throws IOException {
    	sendMessage(new ServerMessage(ServerMessage.MessageType.CLEAR_CANVAS));
    }
    
    public void sendApprovalResponse(boolean approved, String username) throws IOException {
    	sendMessage(new ServerMessage(ServerMessage.MessageType.APPROVAL_RESPONSE, approved));
    }
    
   public void kickUser(String username) throws IOException {
	   sendMessage(new ServerMessage(ServerMessage.MessageType.KICK_NOTIFICATION, username));
   }
   
  public void saveWhiteboard(File file) throws IOException {
       List<ShapeData> shapes = frame.getCanvas().getShapes();
       try (ObjectOutputStream fileOut = new ObjectOutputStream(new FileOutputStream(file))) {
           fileOut.writeObject(shapes);
       }
   }
   
   public void sendMessage(ServerMessage message) throws IOException {
	   System.out.println("Sending message: " + message.getType() + " Data: " + message.getData());
       if (!isConnected) throw new IOException("Not connected to server");
       synchronized(out) {
    	   try {
        	   out.writeObject(message);
               out.flush();
    	   } catch (IOException e) {
    		   isConnected = false;
    		   throw e;
    	   }
       }
   }
   
   public void sendShape(ShapeData shape) throws IOException {
       sendMessage(new ServerMessage(ServerMessage.MessageType.SHAPE, shape));
   }
   
   public void sendChatMessage(String message) throws IOException {
	   sendMessage(new ServerMessage(ServerMessage.MessageType.CHAT_MESSAGE, username + ": " + message));
   }
   
   private void closeConnection() {
       isConnected = false;
       try {
           if (socket != null) socket.close();
           if (out != null) out.close();
           if (in != null) in.close();
       } catch (IOException e) {
           System.err.println("Error closing connection: " + e.getMessage());
       }
   }
}
