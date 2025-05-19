package main;

import java.io.Serializable;
import java.util.List;
//Luis Mauboy - 1684115
public class ServerMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum MessageType {
        //Drawing operations
        SHAPE,				
        CLEAR_CANVAS,		
        
        //User management
        USER_JOIN,
        JOIN_REQUEST,       
        APPROVAL_RESPONSE,  
        USER_LIST,          
        ASSIGN_MANAGER,     
        KICK_NOTIFICATION,  
        
        //Chat system
        CHAT_MESSAGE,		
        
        //File operations
        FILE_DATA,			
        SAVE_REQUEST,		
        LOAD_REQUEST,
        SAVE_RESPONSE,
        LOAD_RESPONSE,
        
        //System messages
        MANAGER_DISCONNECT,
        ERROR,
        SERVER_SHUTDOWN
    }

    private final MessageType type;
    private final Object data;

    public ServerMessage(MessageType type) {
        this(type, null);
    }

    public ServerMessage(MessageType type, Object data) {
        this.type = type;
        this.data = data;
    }
    
    //Getters
    public MessageType getType() {
        return type;
    }

    public Object getData() {
        return data;
    }
    
    public String getErrorText() {
    	return type == MessageType.ERROR && data instanceof String ? (String)data : "";
    }
    
    @SuppressWarnings("unchecked")
	public List<String> getUserList(){
    	return type == MessageType.USER_LIST && data instanceof List ? (List<String>)data : null;
    }
    
    public static ServerMessage createError(String message) {
    	return new ServerMessage(MessageType.ERROR, message);
    }
    
    //Convenience methods for specific message types
    public String getUsername() {
        return (type == MessageType.JOIN_REQUEST) ? (String) data : null;
    }

    public String getKickedUsername() {
    	return (type == MessageType.KICK_NOTIFICATION && data instanceof String) ? (String) data : null;
    }
    
    public ShapeData getShape() {
        return (type == MessageType.SHAPE) ? (ShapeData) data : null;
    }
    
    public String getChatMessage() {
        return (type == MessageType.CHAT_MESSAGE) ? (String) data : null;
    }
    
    @SuppressWarnings("unchecked")
	public List<ShapeData> getShapes() {
        return (type == MessageType.FILE_DATA) ? (List<ShapeData>) data : null;
    }
    
    public ApprovalResult getApprovalResult() {
        return (type == MessageType.APPROVAL_RESPONSE) ? (ApprovalResult) data : null;
    }
    
   public static class ApprovalResult implements Serializable {
        private static final long serialVersionUID = 1L;
        public final boolean approved;
        public final String username;

        public ApprovalResult(boolean approved, String username) {
            this.approved = approved;
            this.username = username;
        }
    }

	public boolean isApproved() {
		return type == MessageType.APPROVAL_RESPONSE && data instanceof Boolean ? (Boolean) data : false;
	}
}