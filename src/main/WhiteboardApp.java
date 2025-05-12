package main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class WhiteboardApp {
    public static void main(String[] args) {
        // Get username and server info
        String username = JOptionPane.showInputDialog("Enter your username:");
        String serverIP = JOptionPane.showInputDialog("Enter server IP:", "localhost");
        int port = Integer.parseInt(JOptionPane.showInputDialog("Enter port:", "1234"));

        SwingUtilities.invokeLater(() -> {
            WhiteboardFrame frame = new WhiteboardFrame(username);
            try {
                NetworkManager networkManager = new NetworkManager(frame);
                networkManager.connect(serverIP, port, username);
                frame.setNetworkManager(networkManager);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Connection failed: " + e.getMessage());
                System.exit(1);
            }
            frame.setVisible(true);
        });
    }
}

//Main GUI frame for the Whiteboard
class WhiteboardFrame extends JFrame {
    private DrawingCanvas canvas;
    private NetworkManager networkManager;
    private Color currentColor = Color.BLACK;
    private ToolType currentTool = ToolType.PENCIL;

    public WhiteboardFrame(String username) {
        setTitle("Whiteboard");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        canvas = new DrawingCanvas();
        add(canvas, BorderLayout.CENTER);

        JPanel toolbar = new JPanel();

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
        clearBtn.addActionListener(e -> canvas.clear());
        
        toolbar.add(toolBox);
        toolbar.add(colorBtn);
        toolbar.add(new JLabel("Size:"));
        toolbar.add(sizeBox);
        toolbar.add(clearBtn);
        
        add(toolbar, BorderLayout.NORTH);
    }
    
    public void setNetworkManager(NetworkManager networkManager) {
    	this.networkManager = networkManager;
    	canvas.setNetworkManager(networkManager);
    }
    
    public DrawingCanvas getCanvas() {
    	return canvas;
    }
}

//Drawing canvas panel
class DrawingCanvas extends JPanel {
    private final List<ShapeData> shapes = new ArrayList<>();
    private NetworkManager networkManager;
    private ToolType currentTool = ToolType.PENCIL;
    private Color currentColor = Color.BLACK;
    private Point startPoint = null;
    private Point previewPoint = null;
    private int strokeSize = 4;
    
    public DrawingCanvas() {
        setBackground(Color.WHITE);
        
        // Mouse interaction
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
    	shapes.add(shape);
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

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        for (ShapeData s : shapes) {
            s.draw(g2d);
        }

        // Preview shape for shape tools
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
