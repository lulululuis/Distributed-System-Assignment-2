package main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
//Luis Mauboy - 1684115
public class WhiteboardApp {
    public static void main(String[] args) {
        //Get username and server info
        String username = JOptionPane.showInputDialog("Enter your username:");
        String serverIP = JOptionPane.showInputDialog("Enter server IP:", "localhost");
        int port = Integer.parseInt(JOptionPane.showInputDialog("Enter port:", "1234"));
        
        //Initialize GUI
        SwingUtilities.invokeLater(() -> {
            WhiteboardFrame frame = new WhiteboardFrame(username);
            try {
                NetworkManager networkManager = new NetworkManager(frame);
                networkManager.connect(serverIP, port, username);
                frame.setNetworkManager(networkManager);
                frame.setVisible(true);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Connection failed: " + e.getMessage());
                System.exit(1);
            }
        });
    }
}

//Main GUI frame for the Whiteboard
class WhiteboardFrame extends JFrame {
    private DrawingCanvas canvas;
    private NetworkManager networkManager;
    private String username;
    private boolean isManager = false;
    private Color currentColor = Color.BLACK;
    private ToolType currentTool = ToolType.PENCIL;
    
    //Chat components
    private JTextArea chatArea;
    private JTextField chatInput;
    
    //User list
    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    
    public WhiteboardFrame(String username) {
    	this.username = username;
        setTitle("Whiteboard - " + username);
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        //Add window listener for manager
        if (isManager) {
        	addWindowListener(new WindowAdapter(){
        		@Override
        		public void windowClosing(WindowEvent e) {
        			try {
        				networkManager.sendMessage(new ServerMessage(ServerMessage.MessageType.MANAGER_DISCONNECT));
        			} catch (IOException ex) {
        				ex.printStackTrace();
        			}
        		}
        	});
        }
        
        initializeComponents();
        setupMenuBar();
    }
    
    private void initializeComponents() {
    	//Drawing canvas
        canvas = new DrawingCanvas();
        add(canvas, BorderLayout.CENTER);
        
        //Right panel (chat + users)
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(250, getHeight()));
        
        //User list
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        rightPanel.add(new JScrollPane(userList), BorderLayout.NORTH);
        
        //Chat area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        rightPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        
        //Chat input
        JPanel chatInputPanel = new JPanel(new BorderLayout());
        chatInput = new JTextField();
        chatInput.addActionListener(e -> sendChatMessage());
        chatInputPanel.add(chatInput, BorderLayout.CENTER);
        
        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendChatMessage());
        chatInputPanel.add(sendButton, BorderLayout.EAST);
        
        rightPanel.add(chatInputPanel, BorderLayout.SOUTH);
        add(rightPanel, BorderLayout.EAST);
        
        //Toolbar
        setupToolbar();
    }
    
    private void setupToolbar() {
    	JToolBar toolBar = new JToolBar();
    	toolBar.setFloatable(false);
    	
    	//Tool selector
        String[] tools = {"Pencil", "Line", "Rectangle", "Oval", "Triangle", "Text", "Eraser"};
        JComboBox<String> toolBox = new JComboBox<>(tools);
        toolBox.addActionListener(e -> {
            String selected = (String) toolBox.getSelectedItem();
            currentTool = ToolType.fromString(selected);
            canvas.setTool(currentTool);
        });
        
        //Color selector
        JButton colorBtn = new JButton("Color");
        colorBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(null, "Pick Color", currentColor);
            if (chosen != null) {
                currentColor = chosen;
                canvas.setColor(currentColor);
            }
        });
        
        //Size selector
        JComboBox<Integer> sizeBox = new JComboBox<>(new Integer[]{4, 6, 8, 10});
        sizeBox.addActionListener(e -> canvas.setStrokeSize((Integer) sizeBox.getSelectedItem()));

        //Clear button
        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
        	if (isManager) {
        		canvas.clear();
        		try {
        			networkManager.sendMessage(new ServerMessage(ServerMessage.MessageType.CLEAR_CANVAS));
        		} catch (IOException ex) {
        			showError("Failed to clear canvas");
        		}
        	} else {
        		showError("Only manager can clear canvas");
        	}
        });
        
        toolBar.add(toolBox);
        toolBar.add(colorBtn);
        toolBar.add(new JLabel(" Size:"));
        toolBar.add(sizeBox);
        toolBar.add(clearBtn);
        
        add(toolBar, BorderLayout.NORTH);
    }
    
    
    private void setupMenuBar() {
    	JMenuBar menuBar = new JMenuBar();
    	
    	//File menu (manager only)
    	JMenu fileMenu = new JMenu("File");
    	if (isManager) {
    		fileMenu.add(createMenuItem("Save", e -> saveWhiteboard()));
    		fileMenu.add(createMenuItem("Load", e -> loadWhiteboard()));
    	}
    	
    	//User menu (manager only)
    	if (isManager) {
    		JMenu userMenu = new JMenu("Users");
    		userMenu.add(createMenuItem("Kick User", e -> kickUser()));
    		menuBar.add(userMenu);
    	}
    	setJMenuBar(menuBar);
    	menuBar.add(fileMenu);
    }
    
    private JMenuItem createMenuItem(String text, ActionListener action) {
    	JMenuItem item = new JMenuItem(text);
    	item.addActionListener(action);
    	return item;
    }
    
    public void processMessage(ServerMessage message) {
        SwingUtilities.invokeLater(() -> {
            try {
                switch (message.getType()) {
                    case SHAPE:
                        canvas.addShape(message.getShape());
                        break;
                    case CLEAR_CANVAS:
                        canvas.clear();
                        break;
                    case CHAT_MESSAGE:
                        addChatMessage(message.getChatMessage());
                        break;
                    case JOIN_REQUEST:
                        if (isManager) {
                            boolean approved = showApprovalDialog(message.getUsername());
                            networkManager.sendApprovalResponse(approved, message.getUsername());
                        }
                        break;
                    case USER_LIST:
                        updateUserList(message.getUserList());
                        break;
                    case ASSIGN_MANAGER:
                        setManagerPrivileges(true);
                        break;
                    case KICK_NOTIFICATION:
                        JOptionPane.showMessageDialog(this, "You have been kicked by the manager", "Disconnected", JOptionPane.WARNING_MESSAGE);
                        System.exit(0);
                        break;
                    case APPROVAL_RESPONSE:
                    	if (message.isApproved()) {
                    		//Handle successful approval
                    	} else {
                    		showError("Your join request was denied");
                    		System.exit(0);
                    	}
                    	break;
                    case FILE_DATA:
                        canvas.loadShapes(message.getShapes());
                        break;
                    case ERROR:
                        showError(message.getErrorText());
                        break;
                    case SERVER_SHUTDOWN:
                    	JOptionPane.showMessageDialog(this, "Manager disconnected. Application will close.", "Server Shutdown", JOptionPane.WARNING_MESSAGE);
                    	System.exit(0);
                    	break;
                    default:
                        System.err.println("Unknown message type: " + message.getType());
                }
            } catch (Exception e) {
                showError("Error processing message: " + e.getMessage());
            }
        });
    }
    
    public void setNetworkManager(NetworkManager networkManager) {
    	this.networkManager = networkManager;
    	canvas.setNetworkManager(networkManager);
    }
    
    public void setManagerPrivileges(boolean isManager) {
    	this.isManager = isManager;
    	SwingUtilities.invokeLater(() -> {
    		setupMenuBar(); //Rebuild menu with manager options
    		if (isManager) {
    			JOptionPane.showMessageDialog(this, "You are now the manager", "Manager Status", JOptionPane.INFORMATION_MESSAGE);
    		}
    	});
    }
    
    public DrawingCanvas getCanvas() {
    	return canvas;
    }
    
    public void addChatMessage(String message) {
        chatArea.append(message + "\n");
    }
    
    public void updateUserList(List<String> users) {
    	SwingUtilities.invokeLater(() -> {
    		userListModel.clear();
        	users.forEach(userListModel::addElement);
    	});
    }
    
    public boolean showApprovalDialog(String requestingUser) {
    	int response = JOptionPane.showConfirmDialog(this, requestingUser + " wants to join. Approve?", "New User Request", JOptionPane.YES_NO_OPTION);
    	return response == JOptionPane.YES_OPTION;
    }
    
    private void sendChatMessage() {
        String message = chatInput.getText();
        if (!message.trim().isEmpty()) {
            try {
            	//Send message
                networkManager.sendChatMessage(message);
                chatInput.setText("");
            } catch (IOException e) {
                showError("Failed to send message");
            }
        }
    }
    
    public void saveWhiteboard() {
    	JFileChooser fc = new JFileChooser();
    	if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
    		try {
    			//Save file
    			networkManager.saveWhiteboard(fc.getSelectedFile());
    		} catch (IOException e) {
    			showError("Save failed: " + e.getMessage());
    		}
    	}
    }
    
    public void loadWhiteboard() {
    	JFileChooser fc = new JFileChooser();
    	if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
    		try  {
    			//Load file
    			List<ShapeData> shapes = loadShapesFromFile(fc.getSelectedFile());
    			canvas.loadShapes(shapes); //Update local canvas
    			//Broadcast to others
    			if (isManager) {
    				networkManager.sendMessage(new ServerMessage(ServerMessage.MessageType.FILE_DATA, shapes));
    			}
    		} catch (/*IOException | ClassNotFoundException*/ Exception e) {
    			showError("Load failed: " + e.getMessage());
    		}
    	}
    }
    
    private List<ShapeData> loadShapesFromFile(File file) throws IOException, ClassNotFoundException {
    	try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
    		@SuppressWarnings("unchecked")
			List<ShapeData> shapes = (List<ShapeData>) ois.readObject();
    		return shapes;
    	}
    }
    
    private void kickUser() {
    	String selected = userList.getSelectedValue();
    	if (selected != null && !selected.equals(username)) {
    		try {
    			//Send the username of the user to kick
    			networkManager.kickUser(selected);
    		} catch (IOException e) {
    			showError("Kick failed: " + e.getMessage());
    		}
    	} else {
    		JOptionPane.showMessageDialog(this, "Select a user to kick");
    	}
    }
    
    public void showError(String message) {
    	//Show error message
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
     
}

//Drawing canvas panel
class DrawingCanvas extends JPanel {
    private final List<ShapeData> shapes = Collections.synchronizedList(new ArrayList<>());
    private NetworkManager networkManager;
    private ToolType currentTool = ToolType.PENCIL;
    private Color currentColor = Color.BLACK;
    private Point startPoint = null;
    private Point previewPoint = null;
    private int strokeSize = 4;
    
    public DrawingCanvas() {
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        //Mouse interaction
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                startPoint = e.getPoint();
                
                if (currentTool == ToolType.TEXT) {
                    String text = JOptionPane.showInputDialog("Enter text:");
                    if (text != null) {
                    	ShapeData shape = new ShapeData(currentTool, currentColor, strokeSize, startPoint, startPoint, text);
                        shapes.add(shape);
                        sendShapeToNetwork(shape); //Send to other clients
                        repaint();
                    }
                } else if (currentTool == ToolType.PENCIL || currentTool == ToolType.ERASER) {
                    ShapeData shape = new ShapeData(currentTool, getEffectiveColor(), strokeSize, e.getPoint(), e.getPoint());
                	shapes.add(shape);
                	sendShapeToNetwork(shape); //Send to other clients
                    repaint();
                }
            }

            public void mouseReleased(MouseEvent e) {
                if (startPoint != null && currentTool.isShapeTool()) {
                    Point endPoint = e.getPoint();
                    ShapeData shape = new ShapeData(currentTool, currentColor, strokeSize, startPoint, endPoint);
                    shapes.add(shape);
                    sendShapeToNetwork(shape); //Send to other clients
                    previewPoint = null;
                    repaint();
                } 
                startPoint = null;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if ((currentTool == ToolType.PENCIL || currentTool == ToolType.ERASER) && startPoint != null) {
                    ShapeData shape = new ShapeData(currentTool, getEffectiveColor(), strokeSize, startPoint, e.getPoint());
                	shapes.add(shape);
                	sendShapeToNetwork(shape); //Send to other clients
                    startPoint = e.getPoint();
                    repaint();
                } else if (currentTool.isShapeTool() && startPoint != null) {
                    previewPoint = e.getPoint();
                    repaint();
                }
            }
        });
    }
    
    public void loadShapes(List<ShapeData> shapes) {
		synchronized(this.shapes) {
			this.shapes.clear();
			if(shapes != null) {
				this.shapes.addAll(shapes);
			}
			repaint();
		}
	}

	private void sendShapeToNetwork(ShapeData shape) {
    	if (networkManager != null) {
    		try {
    			networkManager.sendShape(shape);
    		} catch (IOException ex) {
    			JOptionPane.showMessageDialog(this, "Network error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    		}
    	}
    }
    
    private Color getEffectiveColor() {
    	return currentTool == ToolType.ERASER ? Color.WHITE : currentColor;
    }
    
    public void setNetworkManager(NetworkManager networkManager) {
    	this.networkManager = networkManager;
    }

    public void addShape(ShapeData shape) {
    	synchronized(shapes) {
    		shapes.add(shape);
    	}
    	repaint();
    }
    
    public void setTool(ToolType tool) {
        this.currentTool = tool;
    }

    public void setColor(Color color) {
        this.currentColor = color;
    }

    public void setStrokeSize(int size) {
        this.strokeSize = size;
    }

    public void clear() {
    	shapes.clear();
        repaint();
    }
    
    public List<ShapeData> getShapes(){
    	return shapes;
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        for (ShapeData s : shapes) {
            s.draw(g2d);
        }

        //Preview shape for shape tools
        if (startPoint != null && previewPoint != null && currentTool.isShapeTool()) {
            g2d.setColor(currentColor);
            g2d.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f,
                    new float[]{5.0f}, 0.0f));
            new ShapeData(currentTool, currentColor, strokeSize, startPoint, previewPoint).draw(g2d);
        }
    }
}

//Tool types for shapes drawing
enum ToolType {
    PENCIL, LINE, RECTANGLE, OVAL, TRIANGLE, TEXT, ERASER;

    public boolean isShapeTool() {
        return this == LINE || this == RECTANGLE || this == OVAL || this == TRIANGLE;
    }

    public static ToolType fromString(String str) {
        return switch (str.toLowerCase()) {
            case "line" -> LINE;
            case "rectangle" -> RECTANGLE;
            case "oval" -> OVAL;
            case "triangle" -> TRIANGLE;
            case "text" -> TEXT;
            case "eraser" -> ERASER;
            default -> PENCIL;
        };
    }
}

//Shapes drawn on canvas
class ShapeData implements Serializable{
    private static final long serialVersionUID = 1L;
    
	ToolType type;
    Color color;
    int stroke;
    Point start;
    Point end;
    String text;

    public ShapeData(ToolType type, Color color, int stroke, Point start, Point end) {
        this(type, color, stroke, start, end, null);
    }

    public ShapeData(ToolType type, Color color, int stroke, Point start, Point end, String text) {
        this.type = type;
        this.color = color;
        this.stroke = stroke;
        this.start = start;
        this.end = end;
        this.text = text;
    }

    public void draw(Graphics2D g2d) {
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(stroke));
        int x = Math.min(start.x, end.x);
        int y = Math.min(start.y, end.y);
        int w = Math.abs(start.x - end.x);
        int h = Math.abs(start.y - end.y);
        
        switch (type) {
            case LINE -> g2d.drawLine(start.x, start.y, end.x, end.y);
            case RECTANGLE -> g2d.drawRect(x, y, w, h);
            case OVAL -> g2d.drawOval(x, y, w, h);
            case TRIANGLE -> {
                int[] xs = {start.x, end.x, start.x - (end.x - start.x)};
                int[] ys = {start.y, end.y, end.y};
                g2d.drawPolygon(xs, ys, 3);
            }
            case PENCIL, ERASER -> g2d.drawLine(start.x, start.y, end.x, end.y);
            case TEXT -> g2d.drawString(text, start.x, start.y);
        }
    }
    
}
