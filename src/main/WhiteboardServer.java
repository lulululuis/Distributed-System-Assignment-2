package main;

import java.io.*;
import java.net.*;
import java.util.*;

public class WhiteboardServer {
    private List<ObjectOutputStream> clients = new ArrayList<>();
    private List<ShapeData> shapes = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        new WhiteboardServer().start(1234);
    }

    public void start(int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server running on port " + port);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(() -> handleClient(clientSocket)).start();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
            
            // First message is username
            String username = (String) in.readObject();
            
            synchronized (this) {
                clients.add(out);
                // Send existing shapes to new client
                for (ShapeData shape : shapes) {
                    out.writeObject(shape);
                }
                out.flush();
            }

            while (true) {
                ShapeData shape = (ShapeData) in.readObject();
                synchronized (this) {
                    shapes.add(shape);
                    broadcast(shape);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void broadcast(ShapeData shape) {
        for (ObjectOutputStream client : clients) {
            try {
                client.writeObject(shape);
                client.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
