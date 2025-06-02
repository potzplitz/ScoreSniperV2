package commands;

import java.util.Scanner;

public class ExitCommand implements Command{

	@Override
	public void execute(String[] args, CommandManager cmd, Scanner sc) {
		System.out.println("Stopping ScoreSniper...");
		System.exit(0);
	}

}
