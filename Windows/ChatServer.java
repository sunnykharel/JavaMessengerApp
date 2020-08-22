package Windows;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.*;

public class ChatServer {
	static final int PORT = 8080; //The default connection port on the server
	
	/**
	 * TODO: if there is no chat subject, make sure to re-make the subject (in the case that all users log off
	 * and want to log back on)
	 */
	private static Map<String, ChatSubject> chatIDtoChatSubjectMap = Collections.synchronizedMap(new HashMap<String, ChatSubject>());
	
	/**
	 * Map of currently logged-in users to their respective ClientWriterObservers
	 * added to on login, removed on log-off
	 */
	private static Map<String, ClientWriterObserver> usernameToWriters = Collections.synchronizedMap(new HashMap<String, ClientWriterObserver>());
	static final boolean verbose= true;
	private static final String chatsDirectory = "C:\\Users\\sunny\\Desktop\\chats";
	private static final String usersDirectory = "C:\\Users\\sunny\\Desktop\\users";

	public static void main(String[] args) {
		System.out.println("The chat server is running...");
		try {
			/*
			Once the ServerSocket accomplished its listening task 
			and detected an incoming connection,
			it will accept() it and create a new Socket instance to facilitate the communication.
			*/
			ServerSocket serverSocket = new ServerSocket(PORT);		
			System.out.println("Server started");
			System.out.println("Listening for connections on port: "+PORT);
			System.out.println("...");
			
			while(true) {
				Thread t = new Thread(new UserHandler(serverSocket.accept())); //accept new users and create new threads for them as they come
				t.start();
			}
		} catch (IOException e) {
			System.err.println("Server connection error :"+e.getMessage());

		}	
	}
	
	
    public static class UserHandler implements Runnable {
    	private boolean LOGGED_OFF;
    	private Socket socket;
    	private BufferedReader in;
        private ClientWriterObserver out;
        private ChatSubject chat1 ;
        private ChatSubject chat2 ;
   	
    	public UserHandler(Socket socket) {
    	    chat1 = null;
            chat2 = null;
            this.socket = socket;
            LOGGED_OFF=false;
            if(verbose)
		System.out.println("Connection opened. ("+new Date()+")");
        }  
    	
		@Override
		public void run() {
			//Set up reader and writer for socket connection
			try {
				in = new BufferedReader( new InputStreamReader(socket.getInputStream()));
	            		out = new ClientWriterObserver(socket.getOutputStream(), null);            
			} catch (IOException e) {
				System.err.println("Server connection error :"+e.getMessage());
			}			
            		
			/*
			Run application logic as long as user not logged off	
			*/
			LOGGED_OFF=false;
			while(!LOGGED_OFF) {
				//get first line of the request from the client
				String input;
				try {
					while( (input = in.readLine()) != null) {
						System.out.println(input);
						/*
						We parse the request with a string tokenizer, according to our custom protocol
						*/
						StringTokenizer parse = new StringTokenizer(input);
						String method = parse.nextToken().toUpperCase(); //the action requested, either POST, GET, DELETE, BROADCAST
						String actionRequested = parse.nextToken(); //we get file requested
						
						
						if(method.equals("POST")) {
							System.out.println("Post req reached");
							if(actionRequested.equals("sendMessage")) {
								handleSendMessageRequest(input);
							}
							String requestContents = parse.nextToken();
							//requestContents could for example be a new username
							handlePostRequest(actionRequested, requestContents);
						} else if(method.equals("GET")) {
							String requestContents = parse.nextToken();
							handleGetRequest(actionRequested, requestContents);
						} else if(method.equals("DELETE")) {
							handleDeleteRequest(actionRequested);
						} else if(method.equals("BROADCAST")) {
							handleBroadCastRequest(input);
						}
					}
				} catch (IOException e) {				
					usernameToWriters.remove(out.username); //remove user from currently logged-in users
					if(chat1!=null) {
						chat1.deleteClientWriterObserver(out);
					} if(chat2!=null) {
						chat2.deleteClientWriterObserver(out);
					}
					e.printStackTrace();				
					break;
				}
				
			}			
		}	
		
		private void handleBroadCastRequest(String input) {
			//Functionality for handling broadcast messages (messages to each member in the chat app)
			for(ClientWriterObserver o : usernameToWriters.values()) {
				o.println(input);
				o.flush();
			}
		}

		/*
		Handles the pseudo-POST requests of our custom protocol
		*/
		private void handlePostRequest(String actionRequested, String requestContents){
			
			
			String[] splitRequestContents = requestContents.split("_");
			if(actionRequested.equals("addUser")) {
				//need to add user to the user files
				String username = splitRequestContents[0];
				String password = splitRequestContents[1];
				String message;
				if(!addUser(username, password)) {
					message = "UsernameTaken";
				} else {
					message = "UsernameAdded";
				}
				out.println(message);
				out.flush();
			} else if(actionRequested.equals("createChat")){
				handleCreateChatRequest(requestContents);				
			}
		}
		
	        /*
		*Helper function to take care of sending a message given an input
		*/
		private void handleSendMessageRequest(String input) {
			//POST sendMessage chatName message
			StringTokenizer parse = new StringTokenizer(input);
			String method = parse.nextToken().toUpperCase(); //we get the HTTP method of the client
			//we get file requested
			String actionRequested = parse.nextToken();
			String chatName = parse.nextToken();
			String message = out.username+":";
			while(parse.hasMoreTokens()) {
				message+= " "+parse.nextToken();
			}
			chatIDtoChatSubjectMap.get(chatName).notifyAllExcept(out.username,"newMessage "+chatName+" "+ message);	
		}
		
	        /**
		*Helper function to create a chat given the required request contents in the format:
		*chatName_username1_username2_..._usernameN
		*/
		private void handleCreateChatRequest(String requestContents) {
			String[] splitRequestContents = requestContents.split("_");
			String chatName = splitRequestContents[0];
			String[] usersInChat = Arrays.copyOfRange(splitRequestContents, 1, splitRequestContents.length);
			//Check if all UserNames are valid
			
			if(checkFileExistsInDirectory(chatsDirectory, chatName+".txt" )) {
				out.println("createChat fail chatNameTaken");
				out.flush();
				return;
			}
			for(String user: usersInChat) {
				if(!checkFileExistsInDirectory(usersDirectory, user+".txt")) {
					out.println("createChat fail invalidUsername "+user);
					out.flush();
					return; // if one of the usernames is invalid
				}
			}
			 
			//create new Chat
			createNewChat(chatName, usersInChat);
			out.println("createChat success none");
			out.flush();


		}
		
	        /**
		*Helper function to add users given their username and password
		*/
		private boolean addUser(String username, String password) {
			if(checkFileExistsInDirectory(usersDirectory, username+".txt")) {
				return false;
			}
//			File[] files = new File("C:\\Users\\sunny\\Desktop\\users").listFiles();
//			//If this pathname does not denote a directory, then listFiles() returns null. 
//			for (File file : files) {
//			    if (file.isFile()) {
//			    	System.out.print(file.getName().split(".txt")[0]);
//			        String userInDataBase = file.getName().split(".txt")[0];
//			        if(userInDataBase.equals(username)) {
//			        	return false;
//			        }
//			    }
//			}
			else {
				addFileToDirectory(usersDirectory, username+".txt");
				addLineToFile(usersDirectory+"\\"+username+".txt", password);
				return true;
			}
		}
	
	    
	    /**
	     *Handler method to take care of the pseudo-GET requests of our communication protocol
	     */
		private void handleGetRequest(String actionRequested, String requestContents) {
			String[] splitRequestContents = requestContents.split("_");
			if(actionRequested.equals("login")){
				String username = splitRequestContents[0];
				String password = splitRequestContents[1];
				handleLoginRequest(username, password);				
			} else if(actionRequested.equals("chatContents")) {
				String username = splitRequestContents[0];
				String chatName = splitRequestContents[1];
				handleChatContentsRequest(username, chatName);
			}
		}
	    
	    
		
	       /**
		 * sends user the whole file first then adds him to the observable list of chatName
		 * @param username
		 * @param chatName
		 */
		private void handleChatContentsRequest(String username, String chatName) {
			//send the User the whole chat immediately
			ClientWriterObserver userOutWriter = usernameToWriters.get(username);
			sendUserChatHistory(userOutWriter, chatName);
			//add user as Observable to the chat
			System.out.println(chatIDtoChatSubjectMap.keySet());
			if(!chatIDtoChatSubjectMap.keySet().contains(chatName)) {
				//this is the case that everyone in the chat is logged off				
				System.out.println("Creating new ChatSubject");
				ChatSubject chatSubj = new ChatSubject(chatName);
				chatSubj.addClientWriterObserver(out);
				chatIDtoChatSubjectMap.put(chatName, chatSubj);
			} else {
				
				ChatSubject chatSubj = chatIDtoChatSubjectMap.get(chatName);
				chatSubj.addClientWriterObserver(out);
			}
		}

	    
	        /*
		 * potential bug: user enters chat and immediately leaves it
		 * somehow have to make the user unable to enter any chat until this 
		 * one fully loads
		 */
		private void sendUserChatHistory(ClientWriterObserver userOutWriter, String chatName) {
			//local copy of ChatSubjectMap
			if(chat1==null) {
				chat1=chatIDtoChatSubjectMap.get(chatName);
			} else {
				chat2 = chat1;
				chat1 = chatIDtoChatSubjectMap.get(chatName);
			}
			System.out.println("Sending user chatHistory");
			File file = new File(chatsDirectory+"\\"+chatName+".txt");
			Scanner scan;
			try {
				scan = new Scanner(file);
				while(scan.hasNextLine()) {
					String line = scan.nextLine();
					userOutWriter.println("initializeChat "+chatName+" "+line);
					userOutWriter.flush();
			        
				}
				scan.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			
		}
		
		/*
		 * if valid username/password combo, you send loginAccepted then send 
		 * an array of the chats the user is in
		 */
		private void handleLoginRequest(String username, String password){
			if(checkFileExistsInDirectory(usersDirectory, username+".txt")) {
				String passwordOnFile = getFirstLineOfFile(usersDirectory, username+".txt")
						.replaceAll("[^A-Za-z0-9]", "");
				if(passwordOnFile==null) {
					System.err.println("password on file is null");
				}else if(passwordOnFile.equals(password)) {
					String chatIDs = getAllChatIDs(username);
					if(chatIDs.length()==0) {
						chatIDs="noChats";
					}
					out.username=username;
					//add to Maps
					usernameToWriters.put(username, out);
					out.println("loginAccepted "+ chatIDs);					
				} else {
					out.println("wrongPassword");
				}
			}else {
				out.println("usernameNotFound");
			}
			out.flush();
		}
		
    private void handleDeleteRequest(String actionRequested) {

    }
	    
    }
    
	
//Helper functions to access/manipulate files in file system
	
	
    private static String getAllChatIDs(String username) {
    	try {
	    	File file = new File(usersDirectory+"\\"+username+".txt");
	    	String chatIDs = "";
	    	Scanner scan = new Scanner(file);
	    	scan.nextLine();
	    	while(scan.hasNext()) {
	    		if(chatIDs.length()>1) {
	    			chatIDs+="_";
	    		}
	    		chatIDs+=scan.nextLine();
	    	}
	    	return chatIDs;
    	} catch (FileNotFoundException e) {
    		System.err.println("File not found when getting all chatIDs"+e);
    		return null;
    	}
    }
    
    private static String getFirstLineOfFile(String directory, String fileName) {
		try {
			File file = new File(directory+"\\"+fileName);
			Scanner scan = new Scanner(file);
			String line = scan.nextLine();
	        scan.close();
	        return line;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}         
    }
    
    private static void addFileToDirectory(String directory, String file) {
    	File stockFile = new File(directory+"\\"+file);
		try {
		    stockFile.createNewFile();
		    if(verbose)
		    	System.out.println("File "+ file+" created");
		} catch (IOException ioe) {
		     System.err.println("Error while Creating File in Java" + ioe);
		}
    }
    
    private static void addLineToFile(String fileName, String input) {
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
    
    private static boolean checkFileExistsInDirectory(String directory, String fileName) {
    	File[] files = new File(directory).listFiles();
		//If this pathname does not denote a directory, then listFiles() returns null. 
    	if(files != null) {
			for (File file : files) {
			    if (file.isFile()) {
			        if(file.getName().equals(fileName)) {
			        	return true;
			        }
			    }
			}
    	}
		return false;
    }
    
    /**
     * Creates a new chat with the given name. Will also add the chat to the list of users
     * Synchronized
     * @param chatName
     * @param usersInChat
     */
    private static void createNewChat(String chatName, String[] usersInChat) {
    	synchronized(UserHandler.class) {
	    	//idea: have the chatSubj constructor send a notification to all clients that 
	    	//they've been added to a chat.
	    	addFileToDirectory(chatsDirectory, chatName+".txt" );
	    	for(String username : usersInChat) {
	    		addLineToFile(usersDirectory+"//"+username+".txt",chatName);
	    		addLineToFile(chatsDirectory+"//"+chatName+".txt", username+" was added to chat.");
	    	}	    	
	    	//need to get all users that are currently active and notify them them
	    	ArrayList<ClientWriterObserver> activeUsers = new ArrayList<ClientWriterObserver>();
	    	for(String username: usersInChat) {
	    		if(usernameToWriters.containsKey(username)) {
	    			activeUsers.add(usernameToWriters.get(username));
	    		}
	    	}
	    	ChatSubject chatSubject = new ChatSubject(chatName, activeUsers);
	    	chatIDtoChatSubjectMap.put(chatName, chatSubject);  
    	}  
    }
    
    

}
