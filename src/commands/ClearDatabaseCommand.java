package commands;

import java.io.Console;
import java.util.Scanner;

import database.Database;

public class ClearDatabaseCommand implements Command {
	
	private static String password = "";
	
	public static void setDeletePassword(String pass) {
		password = pass;
	}

	@Override
	public void execute(String[] args, CommandManager cmd, Scanner sc) {
		Database db = new Database();
		db.setDatabase("ScoreSniper");
		db.connect();

		Console console = System.console();
		String pass;
		if (console != null) {
		    char[] passwordChars = console.readPassword("Password: ");
		    pass = new String(passwordChars);
		} else {
			
		    System.out.print("Password: ");
		    pass = sc.next();
		}
		
		if(pass.equals(password)) {
			System.out.println("Truncating Tables UserScores, UserMostPlayed, RegisteredUsers...");
			db.query("TRUNCATE `RegisteredUsers`");
			db.query("TRUNCATE `UserMostPlayed`");
			db.query("TRUNCATE `UserScores`");
			
		} else {
			System.out.println("Invalid Password! Tables will not be truncated.");
		}
	}
}
