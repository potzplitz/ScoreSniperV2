package commands;

public class CommandList {
	
	public void registerCommands(CommandManager cmd) {
		cmd.register("help", new HelpCommand());
		cmd.register("listusers", new ListUsersCommand());
		cmd.register("toggledebug", new ToggleDebugCommand());
		cmd.register("cleardb", new ClearDatabaseCommand());
		cmd.register("exit", new ExitCommand());
	}

}
