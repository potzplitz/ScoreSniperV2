package commands;

import scores.GetScores;

public class ToggleDebugCommand implements Command {
    
    private boolean toggle = false;

    @Override
    public void execute(String[] args, CommandManager cmd) {
        toggle = !toggle;

        GetScores.setDebugOutput(toggle);

        if (toggle) {
            System.out.println("Debug messages are now enabled!");
        } else {
            System.out.println("Debug messages are now disabled!");
        }
    }
}
