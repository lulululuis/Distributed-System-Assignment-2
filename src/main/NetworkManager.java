package main;

import java.io.*;
import java.net.Socket;

import javax.swing.SwingUtilities;

public class NetworkManager {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private WhiteboardFrame frame;

    public NetworkManager(WhiteboardFrame frame) {
        this.frame = frame;
    }

    public void connect(String ip, int port, String username) throws IOException {
        socket = new Socket(ip, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());
        
        // First send username
        out.writeObject(username);
        out.flush();
        
        // Start listening thread
        new Thread(this::receiveData).start();
    }

    public void sendShape(ShapeData shape) throws IOException {
        out.writeObject(shape);
        out.flush();
    }

    private void receiveData() {
        try {
            while (true) {
                Object received = in.readObject();
                if (received instanceof ShapeData) {
                    SwingUtilities.invokeLater(() -> {
                        frame.getCanvas().addShape((ShapeData) received);
                    });
                }
                // Add handling for other message types (chat, etc.)
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
