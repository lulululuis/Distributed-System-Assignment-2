package main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public class WhiteboardApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new WhiteboardFrame().setVisible(true));
    }
}

class WhiteboardFrame extends JFrame {
    private DrawingCanvas canvas;
    private Color currentColor = Color.BLACK;
    private ToolType currentTool = ToolType.PENCIL;

    public WhiteboardFrame() {
        setTitle("Whiteboard");
        setSize(1200, 900);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        canvas = new DrawingCanvas();
        add(canvas, BorderLayout.CENTER);

        JPanel toolbar = new JPanel();

        String[] tools = {"Pencil", "Line", "Rectangle", "Oval", "Triangle", "Text", "Eraser"};
        JComboBox<String> toolBox = new JComboBox<>(tools);
        toolBox.addActionListener(e -> {
            String selected = (String) toolBox.getSelectedItem();
            currentTool = ToolType.fromString(selected);
            canvas.setTool(currentTool);
        });

        JButton colorBtn = new JButton("Color");
        colorBtn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(null, "Pick Color", currentColor);
            if (chosen != null) {
                currentColor = chosen;
                canvas.setColor(currentColor);
            }
        });

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> canvas.clear());

        JComboBox<Integer> sizeBox = new JComboBox<>(new Integer[]{4, 6, 8, 10});
        sizeBox.addActionListener(e -> canvas.setStrokeSize((Integer) sizeBox.getSelectedItem()));

        toolbar.add(toolBox);
        toolbar.add(colorBtn);
        toolbar.add(new JLabel("Size:"));
        toolbar.add(sizeBox);
        toolbar.add(clearBtn);
        add(toolbar, BorderLayout.NORTH);
    }
}

class DrawingCanvas extends JPanel {
    private final java.util.List<ShapeData> shapes = new ArrayList<>();
    private ToolType currentTool = ToolType.PENCIL;
    private Color currentColor = Color.BLACK;
    private Point startPoint = null;
    private Point previewPoint = null;
    private int strokeSize = 4;

    public DrawingCanvas() {
        setBackground(Color.WHITE);

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                startPoint = e.getPoint();
                if (currentTool == ToolType.TEXT) {
                    String text = JOptionPane.showInputDialog("Enter text:");
                    if (text != null) {
                        shapes.add(new ShapeData(currentTool, currentColor, strokeSize, startPoint, startPoint, text));
                        repaint();
                    }
                } else if (currentTool == ToolType.PENCIL || currentTool == ToolType.ERASER) {
                    shapes.add(new ShapeData(currentTool, currentTool == ToolType.ERASER ? Color.WHITE : currentColor,
                            strokeSize, e.getPoint(), e.getPoint()));
                }
            }

            public void mouseReleased(MouseEvent e) {
                if (startPoint != null && currentTool.isShapeTool()) {
                    Point endPoint = e.getPoint();
                    shapes.add(new ShapeData(currentTool, currentColor, strokeSize, startPoint, endPoint));
                    previewPoint = null;
                    repaint();
                }
                startPoint = null;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if ((currentTool == ToolType.PENCIL || currentTool == ToolType.ERASER) && startPoint != null) {
                    shapes.add(new ShapeData(currentTool, currentTool == ToolType.ERASER ? Color.WHITE : currentColor,
                            strokeSize, startPoint, e.getPoint()));
                    startPoint = e.getPoint();
                    repaint();
                } else if (currentTool.isShapeTool() && startPoint != null) {
                    previewPoint = e.getPoint();
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

        // Preview shape drawing while dragging
        if (startPoint != null && previewPoint != null && currentTool.isShapeTool()) {
            g2d.setColor(currentColor);
            g2d.setStroke(new BasicStroke(strokeSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1.0f,
                    new float[]{5.0f}, 0.0f)); // dashed preview
            new ShapeData(currentTool, currentColor, strokeSize, startPoint, previewPoint).draw(g2d);
        }
    }
}

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

class ShapeData {
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
