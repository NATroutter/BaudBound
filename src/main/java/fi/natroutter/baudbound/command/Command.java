package fi.natroutter.baudbound.command;

/**
 * Abstract base for all console commands.
 * <p>
 * Subclasses call {@code super(name, description)} in their constructor and
 * implement {@link #execute(String[])} to define the command behaviour.
 * Commands are registered with {@link CommandHandler#register(Command)} and
 * dispatched by name when the user types input on the system console.
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
}