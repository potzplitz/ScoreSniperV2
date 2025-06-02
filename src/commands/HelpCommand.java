package commands;

import java.util.ArrayList;
import java.util.List;

public class HelpCommand implements Command{

	@Override
	public void execute(String[] args, CommandManager cmd) {
        List<String> commandNames = new ArrayList<>(cmd.getCommands().keySet());
        
		System.out.println("Avaliable commands: ");

		for(int i = 0; i < commandNames.size(); i++) {
			System.out.print(commandNames.get(i) + "\n");
		}
		
	}
	

}
