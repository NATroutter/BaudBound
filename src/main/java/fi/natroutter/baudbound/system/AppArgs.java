package fi.natroutter.baudbound.system;

import fi.natroutter.baudbound.BaudBound;
import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;

/**
 * Parsed command-line arguments for BaudBound.
 * <p>
 * Parsed once in {@code BaudBound.main()} via picocli and accessible globally through
 * {@code BaudBound.getArgs()}. To add a new flag, declare a field here with an
 * {@link Option} annotation — no parsing logic needed anywhere else.
 * <p>
 * Flags are runtime overrides and do not modify or persist any {@code DataStore} settings.
 */
@Getter
@Command(
        name            = "BaudBound",
        mixinStandardHelpOptions = true,
        versionProvider = AppArgs.VersionProvider.class,
        description     = "Serial port event mapper — listens to serial devices and fires configurable actions."
)
@SuppressWarnings("unused") // fields are assigned by picocli via reflection
public class AppArgs {

    @Option(
            names       = {"--hidden"},
            description = "Start minimized to the system tray, overriding the saved start-hidden setting."
    )
    private boolean hidden;

    @Option(
            names       = {"--debug"},
            description = "Enable the debug overlay on startup, overriding the saved debug-overlay setting."
    )
    private boolean debug;

    @Option(
            names       = {"--nogui"},
            description = "Run in headless mode — serial processing and event actions work normally, " +
                          "but no window or tray icon is shown."
    )
    private boolean noGui;

    /** Supplies the version string printed by {@code --version}. */
    public static class VersionProvider implements IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[]{
                    BaudBound.APP_NAME + " " + BaudBound.VERSION,
                    "Build date: " + BaudBound.BUILD_DATE
            };
        }
    }

}