package Windows;
import java.io.*;
import java.util.*;

/**
 * This ChatSubject only has the currently active chatters
 * If someone in the chat logs in and clicks on the chat, 
 * they are are added to the list of observers
 * @author sunny
 *
 */

public class ChatSubject extends Observable {
	public String ChatName;
	private Set<ClientWriterObserver> observers;
	private Set<PrintWriter> observers1;
    private static final String chatsDirectory = "C:\\Users\\sunny\\Desktop\\chats";
    private static String chatFile;
	
	public ChatSubject(String _ChatName) {
		/*
		 * printWriter set has all currently active observers.
		 * you only want to notify currently active observers that they've
		 * been added to a chat, therefore observer needs to be modified
		 */
		chatFile=chatsDirectory+"\\"+_ChatName+".txt";
		ChatName=_ChatName;
		observers = new HashSet<ClientWriterObserver>();
		observers1 = new HashSet<PrintWriter>();		
	}
	
	public ChatSubject(String _ChatName, ArrayList<ClientWriterObserver> activeUsers) {
		//TODO: initialize variable here
		observers = new HashSet<ClientWriterObserver>();
		chatFile=chatsDirectory+"\\"+_ChatName+".txt";
		ChatName = _ChatName;
		//send a notification to all of them that they have been added
		//They will not receive messages until they actually enter the chat
		//from their end
		for(ClientWriterObserver o : activeUsers) {
			o.println("addedToChatNotification "+ChatName);
			o.flush();
		}		
	}
	
	/**
	 * @param newLines
	 * You must send the complete message with the headers
	 */
	public synchronized void notifyAllExcept(String username, String newLines) {
		System.out.println(this);
		System.out.println(newLines+ " in notifyAllExcept");
		for(ClientWriterObserver o: observers) {
			if(!o.username.equals(username)) {
				o.sendMessageToObserver(newLines);
			}
		}addLineToFile(chatFile,  newLines.replaceFirst("newMessage", ""));
		
	}
	public synchronized void notifyAll(String message) {
		for(ClientWriterObserver obs: observers) {
			obs.println(message);
		}
		addLineToFile(chatFile, message.replaceFirst("newMessage", ""));
	}
	public synchronized void deleteClientWriterObserver(ClientWriterObserver o) {
		observers.remove(o);
		System.out.println(o.username+ " removed from "+ChatName);
		notifyAll("newMessage "+ChatName+" " +o.username+" has left the chat");
		
	}
	
	public synchronized void addClientWriterObserver(ClientWriterObserver o) {
		observers.add(o);
		notifyAllExcept(o.username, "newMessage "+ChatName+" " +o.username+" is now active in the chat!");
	}
	
//	public synchronized void addUser(String username, OutputStream w) {
//		ClientWriterObserver o = new ClientWriterObserver(w, username);
//		observers.add(o);
//		observers1.add(o);
//	}
	
	//no need to delete or add users to a chat
	
	
	private void addLineToFile(String fileName, String input) {
    	try {
	    	FileWriter fileWriter = new FileWriter(fileName, true);
	        PrintWriter printWriter = new PrintWriter(fileWriter);
	        printWriter.println(input);
	        printWriter.close();
    	}
    	catch(IOException e) {
		     System.err.println("Error while adding line to file in Java" + e);

    	}
    }
	
}
