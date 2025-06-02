package commands;

import java.util.Scanner;

public interface Command {
    void execute(String[] args, CommandManager manager, Scanner sc);
}

