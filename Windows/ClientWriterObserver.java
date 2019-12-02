package Windows;
import java.io.*;
import java.util.*;

public class ClientWriterObserver extends PrintWriter implements Observer {
	public String username;
	

	public ClientWriterObserver(OutputStream out, String _username) {
		//for flushing
		super(out, true);
		username = _username;
	}

	public static void main(String[] args) {
	}

	
	//this method basically writes across the printwriter.
	@Override
	public void update(Observable o, Object arg) {
		// TODO Auto-generated method stub
		String s = (String) arg;
		this.println(s);
		this.flush();
		
	}
	
	//this class extends PrintWriter
	public void sendMessageToObserver(String arg) {
		this.println(arg);
		this.flush();
	}

}
