package fi.natroutter.baudbound.command;

import fi.natroutter.baudbound.BaudBound;

/**
 * Abstract base for all console commands.
 * <p>
 * Subclasses call {@code super(name, description)} in their constructor and
 * implement {@link #execute(String[])} to define the command behaviour.
 * Commands are registered with {@link CommandHandler#register(Command)} and
 * dispatched by name when the user types input on the system console.
 * <p>
 * Use the protected {@link #log}, {@link #logWarn}, and {@link #logError} helpers to record
 * command actions to the application log file. All three use {@code silent=true} so the
 * entry is written to disk without printing a second line to the console.
 */
public abstract class Command {

    private final String name;
    private final String description;

    /**
     * @param name        the command keyword (case-insensitive)
     * @param description short human-readable description shown in {@code help}
     */
    protected Command(String name, String description) {
        this.name = name.toLowerCase();
        this.description = description;
    }

    /**
     * Executes the command.
     *
     * @param args any tokens that followed the command name on the input line
     */
    public abstract void execute(String[] args);

    public String getName()        { return name; }
    public String getDescription() { return description; }

    /** Logs an INFO-level message silently (file only, no console output). */
    protected void log(String msg)      { BaudBound.getLogger().info(msg,  true); }
    /** Logs a WARN-level message silently (file only, no console output). */
    protected void logWarn(String msg)  { BaudBound.getLogger().warn(msg,  true); }
    /** Logs an ERROR-level message silently (file only, no console output). */
    protected void logError(String msg) { BaudBound.getLogger().error(msg, true); }
}