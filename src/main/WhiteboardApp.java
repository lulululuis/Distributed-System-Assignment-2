package main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public class WhiteboardApp {
	public static void main(String args[]) {
		SwingUtilities.invokeLater(() -> new WhiteboardFrame().setVisible(true));
	}
}

class WhiteboardFrame extends JFrame {
	private DrawingCanvas canvas;
	private Color currentColor = Color.BLACK;
	private ToolType currentTool = ToolType.PENCIL;
	
	public WhiteboardFrame(){
		setTitle("Whiteboard");
		setSize(1200, 900);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLayout(new BorderLayout());
		
		canvas = new DrawingCanvas();
		add(canvas, BorderLayout.CENTER);
		
		JPanel toolbar = new JPanel();
		
		String[] tools = {"Pencil", "Line", "Rectangle" , "Oval", "Triangle", "Text", "Eraser"};
		JComboBox<String> toolBox = new JComboBox<>(tools);
		toolBox.addActionListener(e -> {
			String selected = (String) toolBox.getSelectedItem();
			currentTool = ToolType.fromString(selected);
			canvas.setTool(currentTool);
		});
		
		JButton colorBtn = new JButton("Color");
		colorBtn.addActionListener(e -> {
			Color chosen = JColorChooser.showDialog(null, "Pick Color", currentColor);
			if(chosen != null) {
				currentColor = chosen;
				canvas.setColor(currentColor);
			}
		});
		
		JButton clearBtn = new JButton("Clear");
		clearBtn.addActionListener(e -> canvas.clear());
		
		toolbar.add(toolBox);
		toolbar.add(colorBtn);
		toolbar.add(clearBtn);
		add(toolbar, BorderLayout.NORTH);
	}
}

class DrawingCanvas extends JPanel{
	private java.util.List<ShapeData> shapes = new ArrayList<>();
	private ToolType currentTool = ToolType.PENCIL;
	private Color currentColor = Color.BLACK;
	private Point startPoint = null;
	private Point endPoint = null;
	private String inputText = "Sample Text";
	
	public DrawingCanvas() {
		setBackground(Color.WHITE);
		
		addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				startPoint = e.getPoint();
				if(currentTool == ToolType.TEXT) {
					String text = JOptionPane.showInputDialog("Enter text: ");
					if(text != null) {
						shapes.add(new ShapeData(currentTool, currentColor, startPoint, startPoint, text));
						repaint();
					}
				} else if(currentTool == ToolType.PENCIL || currentTool == ToolType.ERASER) {
					shapes.add(new ShapeData(currentTool, currentTool == ToolType.ERASER ? Color.WHITE : currentColor, e.getPoint(), e.getPoint()));
				}
			}
			
			public void mouseReleased(MouseEvent e) {
				if(startPoint != null && currentTool.isShapeTool()) {
					endPoint = e.getPoint();
					shapes.add(new ShapeData(currentTool, currentColor, startPoint, endPoint));
					repaint();
				}
				startPoint = null;
			}
		});
		
		addMouseMotionListener(new MouseMotionAdapter() {
			public void mouseDragged(MouseEvent e) {
				if((currentTool == ToolType.PENCIL || currentTool == ToolType.ERASER) && startPoint != null) {
					shapes.add(new ShapeData(currentTool, currentTool == ToolType.ERASER ? Color.WHITE : currentColor, startPoint, e.getPoint()));
					startPoint = e.getPoint();
					repaint();
				}
			}
		});
	}
	
	public void setTool(ToolType tool) {
		this.currentTool = tool;
	}
	
	public void setColor(Color color) {
		this.currentColor = color;
	}
	
	public void clear() {
		shapes.clear();
		repaint();
	}
	
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		for(ShapeData s : shapes) {
			s.draw(g);
		}
	}
}

enum ToolType {
	PENCIL, LINE, RECTANGLE, OVAL, TRIANGLE, TEXT, ERASER;
	
	public boolean isShapeTool() {
		return this != PENCIL && this != TEXT && this != ERASER;
	}
	
	public static ToolType fromString(String str) {
		switch(str.toLowerCase()) {
			case "line": return LINE;
			case "rectangle": return RECTANGLE;
			case "oval": return OVAL;
			case "triangle": return TRIANGLE;
			case "text": return TEXT;
			case "eraser": return ERASER;
			default: return PENCIL;
		}
	}
}

class ShapeData{
	ToolType type;
	Color color;
	Point start;
	Point end;
	String text;
	
	public ShapeData(ToolType type, Color color, Point start, Point end) {
		this(type, color, start, end, null);
	}
	
	public ShapeData(ToolType type, Color color, Point start, Point end, String text) {
		this.type = type;
		this.color = color;
		this.start = start;
		this.end = end;
		this.text = text;
	}
	
	public void draw(Graphics g) {
		g.setColor(color);
		int x = Math.min(start.x, end.x);
		int y = Math.min(start.y, end.y);
		int w = Math.abs(start.x - end.x);
		int h = Math.abs(start.y - end.y);
		
		switch(type) {
			case LINE:
				g.drawLine(start.x, start.y, end.x, end.y);
				break;
			case RECTANGLE:
				g.drawRect(x, y, w, h);
				break;
			case OVAL:
				g.drawOval(x, y, w, h);
				break;
			case TRIANGLE:
				int[] xs = {start.x, end.x, start.x - (end.x - start.x)};
				int[] ys = {start.y, end.y, end.y};
				g.drawPolygon(xs, ys, 3);
				break;
			case PENCIL:
			case ERASER:
				g.drawLine(start.x, start.y, end.x, end.y);
				break;
			case TEXT:
				g.drawString(text, start.x, start.y);
				break;
		}
	}
}