package fi.natroutter.baudbound.command.commands;

import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.command.Command;
import fi.natroutter.baudbound.command.ConsoleUI;

import java.util.List;

/**
 * Prints the running application version and build date to the console.
 */
public class VersionCommand extends Command {

    public VersionCommand() {
        super("version", "Display the running version and build date");
    }

    @Override
    public void execute(String[] args) {
        log("Console: version displayed (" + BaudBound.VERSION + ")");
        ConsoleUI.printBox(BaudBound.APP_NAME, List.of(
                "{BLUE}Version    {WHITE}:{RESET} {CYAN}" + BaudBound.VERSION,
                "{BLUE}Build date {WHITE}:{RESET} {CYAN}" + BaudBound.BUILD_DATE
        ));
    }
}