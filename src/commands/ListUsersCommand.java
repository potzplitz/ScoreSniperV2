package commands;

import java.util.Scanner;

import database.Database;

public class ListUsersCommand implements Command{

	@Override
	public void execute(String[] args, CommandManager cmd, Scanner sc) {
		Database db = new Database();
		db.setDatabase("ScoreSniper");
		db.connect();
		
		db.query("SELECT * from RegisteredUsers");
		
		System.out.println("Registered Users: ");
		
		for(int i = 0; i < db.result().size(); i++) {
			System.out.println(db.result().get(i).get("username"));
		}
		
	}

}
