package main;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
//Luis Mauboy - 1684115
public class UserManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Boolean> users = new ConcurrentHashMap<>();
    private String manager;
    
    //Add a new user to the system
    public synchronized boolean addUser(String username, boolean isManager) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (users.containsKey(username)) {
            return false;
        }
        
        users.put(username, isManager);
        if (isManager) {
        	if (manager != null && !manager.equals(username)) {
        		users.put(manager, false);
        	}
            manager = username;
        }
        return true;
    }
    
    //Remove a user from the system
    public synchronized void removeUser(String username) {
        if (username == null) return;
        users.remove(username);
    }
    
    //Checks if a user is the manager
    public synchronized boolean isManager(String username) {
        return username != null && username.equals(manager);
    }
    
    //Gets a list of all connected users
    public synchronized List<String> getUsers() {
        return new ArrayList<>(users.keySet());
    }
    
    //Gets the current manager's username
    public synchronized String getManagerUsername() {
        return manager;
    }
    
    //Gets the number of connected users
    public synchronized int getUserCount() {
        return users.size();
    }
 
    //Checks if a username exists
    public synchronized boolean containsUser(String username) {
        return users.containsKey(username);
    }
}