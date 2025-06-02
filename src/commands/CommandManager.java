package commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class CommandManager {
	
	private final Map<String, Command> commands = new HashMap<>();
	
	public void register(String name, Command command) {
		commands.put(name.toLowerCase(), command);
	}
	
	public void execute(String name, String[] args) {
		Command cmd = commands.get(name.toLowerCase());
		
        if (cmd != null) {
            cmd.execute(args, this);
        } else {
            System.out.println("unknown command: " + name + ". type 'help' for a list of commands!");
        }
	}
	
	public Map<String, Command> getCommands() {
		return commands;
	}
	
	public void startCommandListener() {
		
		System.out.print("> ");
		
		CommandList cmdlist = new CommandList();
		cmdlist.registerCommands(this);
		
		Scanner sc = new Scanner(System.in);
		String currentCommand = "";
		
		while(true) {
			currentCommand = sc.next();
			if(!currentCommand.equals("")) {
				execute(currentCommand, new String[]{});
				System.out.print("\n> ");
			}
		}
	}
}
