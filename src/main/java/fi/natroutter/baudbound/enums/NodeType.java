package fi.natroutter.baudbound.enums;

import fi.natroutter.baudbound.event.TriggerContext;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Enumerates every node type available in the Blueprint-style node graph event editor.
 *
 * <p>Each constant declares:
 * <ul>
 *   <li>a human-readable {@code friendlyName} (accessible via {@code getFriendlyName()})</li>
 *   <li>a {@link Category} classifying the node's role in the graph</li>
 *   <li>an ordered list of {@link PinDef} descriptors defining the node's input/output pins</li>
 * </ul>
 *
 * <p>The four categories map to distinct graph behaviours:
 * <ul>
 *   <li>{@link Category#TRIGGER} — entry-point nodes; have exec-out and data-out pins, no exec-in.</li>
 *   <li>{@link Category#CONDITION} — branching nodes; exec-in → pass / fail exec-out pins.</li>
 *   <li>{@link Category#VALUE} — pure data nodes; no exec pins, only data-out.</li>
 *   <li>{@link Category#ACTION} — side-effect nodes; exec-in → exec-out plus optional data-in pins.</li>
 * </ul>
 */
@AllArgsConstructor
@Getter
public enum NodeType {

    // -------------------------------------------------------------------------
    // TRIGGERS
    // -------------------------------------------------------------------------

    /** Fires when a line is received from a serial port. */
    SERIAL_TRIGGER("Serial Input", Category.TRIGGER, List.of(
            new PinDef("exec_out",    PinKind.EXEC,   true),
            new PinDef("data_input",  PinKind.STRING, true),
            new PinDef("data_device", PinKind.STRING, true)
    )),

    /** Fires when the built-in WebSocket server receives a message. */
    WEBSOCKET_TRIGGER("WebSocket", Category.TRIGGER, List.of(
            new PinDef("exec_out",     PinKind.EXEC,   true),
            new PinDef("data_input",   PinKind.STRING, true),
            new PinDef("data_channel", PinKind.STRING, true)
    )),

    /** Fires when a configured serial device successfully connects. */
    DEVICE_CONNECTED("Device Connected", Category.TRIGGER, List.of(
            new PinDef("exec_out",    PinKind.EXEC,   true),
            new PinDef("data_device", PinKind.STRING, true)
    )),

    /** Fires when a configured serial device disconnects. */
    DEVICE_DISCONNECTED("Device Disconnected", Category.TRIGGER, List.of(
            new PinDef("exec_out",    PinKind.EXEC,   true),
            new PinDef("data_device", PinKind.STRING, true)
    )),

    // -------------------------------------------------------------------------
    // CONDITIONS
    // -------------------------------------------------------------------------

    /** Passes when data_a equals data_b (case-sensitive). */
    EQUALS("Equals", Category.CONDITION, List.of(
            new PinDef("exec_in", PinKind.EXEC,   false),
            new PinDef("pass",    PinKind.EXEC,   true),
            new PinDef("fail",    PinKind.EXEC,   true),
            new PinDef("data_a",  PinKind.STRING, false),
            new PinDef("data_b",  PinKind.STRING, false)
    )),

    /** Passes when data_a does not equal data_b. */
    NOT_EQUALS("Not Equals", Category.CONDITION, List.of(
            new PinDef("exec_in", PinKind.EXEC,   false),
            new PinDef("pass",    PinKind.EXEC,   true),
            new PinDef("fail",    PinKind.EXEC,   true),
            new PinDef("data_a",  PinKind.STRING, false),
            new PinDef("data_b",  PinKind.STRING, false)
    )),

    /** Passes when data_a contains data_b. */
    CONTAINS("Contains", Category.CONDITION, List.of(
            new PinDef("exec_in", PinKind.EXEC,   false),
            new PinDef("pass",    PinKind.EXEC,   true),
            new PinDef("fail",    PinKind.EXEC,   true),
            new PinDef("data_a",  PinKind.STRING, false),
            new PinDef("data_b",  PinKind.STRING, false)
    )),

    /** Passes when data_a does not contain data_b. */
    NOT_CONTAINS("Not Contains", Category.CONDITION, List.of(
            new PinDef("exec_in", PinKind.EXEC,   false),
            new PinDef("pass",    PinKind.EXEC,   true),
            new PinDef("fail",    PinKind.EXEC,   true),
            new PinDef("data_a",  PinKind.STRING, false),
            new PinDef("data_b",  PinKind.STRING, false)
    )),

    /** Passes when data_a starts with data_b. */
    STARTS_WITH("Starts With", Category.CONDITION, List.of(
            new PinDef("exec_in", PinKind.EXEC,   false),
            new PinDef("pass",    PinKind.EXEC,   true),
            new PinDef("fail",    PinKind.EXEC,   true),
            new PinDef("data_a",  PinKind.STRING, false),
            new PinDef("data_b",  PinKind.STRING, false)
    )),

    /** Passes when data_a does not start with data_b. */
    NOT_STARTS_WITH("Not Starts With", Category.CONDITION, List.of(
            new PinDef("exec_in", PinKind.EXEC,   false),
            new PinDef("pass",    PinKind.EXEC,   true),
            new PinDef("fail",    PinKind.EXEC,   true),
            new PinDef("data_a",  PinKind.STRING, false),
            new PinDef("data_b",  PinKind.STRING, false)
    )),

    /** Passes when data_a ends with data_b. */
    ENDS_WITH("Ends With", Category.CONDITION, List.of(
            new PinDef("exec_in", PinKind.EXEC,   false),
            new PinDef("pass",    PinKind.EXEC,   true),
            new PinDef("fail",    PinKind.EXEC,   true),
            new PinDef("data_a",  PinKind.STRING, false),
            new PinDef("data_b",  PinKind.STRING, false)
    )),

    /** Passes when data_a does not end with data_b. */
    NOT_ENDS_WITH("Not Ends With", Category.CONDITION, List.of(
            new PinDef("exec_in",  PinKind.EXEC,   false),
            new PinDef("pass",     PinKind.EXEC,   true),
            new PinDef("fail",     PinKind.EXEC,   true),
            new PinDef("data_a",   PinKind.STRING, false),
            new PinDef("data_b",   PinKind.STRING, false)
    )),

    /** Passes when data_value matches the regex in data_pattern. */
    REGEX("Regex Match", Category.CONDITION, List.of(
            new PinDef("exec_in",      PinKind.EXEC,   false),
            new PinDef("pass",         PinKind.EXEC,   true),
            new PinDef("fail",         PinKind.EXEC,   true),
            new PinDef("data_value",   PinKind.STRING, false),
            new PinDef("data_pattern", PinKind.STRING, false)
    )),

    /** Passes when data_a (parsed as a number) is greater than data_b. */
    GREATER_THAN("Greater Than", Category.CONDITION, List.of(
            new PinDef("exec_in", PinKind.EXEC,   false),
            new PinDef("pass",    PinKind.EXEC,   true),
            new PinDef("fail",    PinKind.EXEC,   true),
            new PinDef("data_a",  PinKind.STRING, false),
            new PinDef("data_b",  PinKind.STRING, false)
    )),

    /** Passes when data_a (parsed as a number) is less than data_b. */
    LESS_THAN("Less Than", Category.CONDITION, List.of(
            new PinDef("exec_in", PinKind.EXEC,   false),
            new PinDef("pass",    PinKind.EXEC,   true),
            new PinDef("fail",    PinKind.EXEC,   true),
            new PinDef("data_a",  PinKind.STRING, false),
            new PinDef("data_b",  PinKind.STRING, false)
    )),

    /** Passes when data_value (parsed as a number) is between data_min and data_max (inclusive). */
    BETWEEN("Between", Category.CONDITION, List.of(
            new PinDef("exec_in",   PinKind.EXEC,   false),
            new PinDef("pass",      PinKind.EXEC,   true),
            new PinDef("fail",      PinKind.EXEC,   true),
            new PinDef("data_value", PinKind.STRING, false),
            new PinDef("data_min",  PinKind.STRING, false),
            new PinDef("data_max",  PinKind.STRING, false)
    )),

    /** Passes when data_value can be parsed as a number. */
    IS_NUMERIC("Is Numeric", Category.CONDITION, List.of(
            new PinDef("exec_in",    PinKind.EXEC,   false),
            new PinDef("pass",       PinKind.EXEC,   true),
            new PinDef("fail",       PinKind.EXEC,   true),
            new PinDef("data_value", PinKind.STRING, false)
    )),

    /** Passes when data_value is null or empty. */
    IS_EMPTY("Is Empty", Category.CONDITION, List.of(
            new PinDef("exec_in",    PinKind.EXEC,   false),
            new PinDef("pass",       PinKind.EXEC,   true),
            new PinDef("fail",       PinKind.EXEC,   true),
            new PinDef("data_value", PinKind.STRING, false)
    )),

    // -------------------------------------------------------------------------
    // VALUES
    // -------------------------------------------------------------------------

    /** Outputs the current value of a named runtime state. */
    GET_STATE("Get State", Category.VALUE, List.of(
            new PinDef("data_value", PinKind.STRING, true)
    )),

    /** Outputs a hard-coded literal string. */
    LITERAL("Literal", Category.VALUE, List.of(
            new PinDef("data_value", PinKind.STRING, true)
    )),

    // -------------------------------------------------------------------------
    // ACTIONS
    // -------------------------------------------------------------------------

    /** Fires a configured webhook. */
    WEBHOOK("Call Webhook", Category.ACTION, List.of(
            new PinDef("exec_in",  PinKind.EXEC, false),
            new PinDef("exec_out", PinKind.EXEC, true)
    )),

    /** Launches a configured program. */
    OPEN_PROGRAM("Open Program", Category.ACTION, List.of(
            new PinDef("exec_in",  PinKind.EXEC, false),
            new PinDef("exec_out", PinKind.EXEC, true)
    )),

    /** Opens a URL in the default browser. */
    OPEN_URL("Open URL", Category.ACTION, List.of(
            new PinDef("exec_in",  PinKind.EXEC,   false),
            new PinDef("exec_out", PinKind.EXEC,   true),
            new PinDef("data_url", PinKind.STRING, false)
    )),

    /** Simulates keyboard input to type the given text. */
    TYPE_TEXT("Type Text", Category.ACTION, List.of(
            new PinDef("exec_in",   PinKind.EXEC,   false),
            new PinDef("exec_out",  PinKind.EXEC,   true),
            new PinDef("data_text", PinKind.STRING, false)
    )),

    /** Stores a value in a named runtime state. */
    SET_STATE("Set State", Category.ACTION, List.of(
            new PinDef("exec_in",    PinKind.EXEC,   false),
            new PinDef("exec_out",   PinKind.EXEC,   true),
            new PinDef("data_name",  PinKind.STRING, false),
            new PinDef("data_value", PinKind.STRING, false)
    )),

    /** Removes a named runtime state. */
    CLEAR_STATE("Clear State", Category.ACTION, List.of(
            new PinDef("exec_in",   PinKind.EXEC,   false),
            new PinDef("exec_out",  PinKind.EXEC,   true),
            new PinDef("data_name", PinKind.STRING, false)
    )),

    /** Writes text to a connected serial device. */
    SEND_TO_DEVICE("Send to Device", Category.ACTION, List.of(
            new PinDef("exec_in",    PinKind.EXEC,   false),
            new PinDef("exec_out",   PinKind.EXEC,   true),
            new PinDef("data_device", PinKind.STRING, false),
            new PinDef("data_text",  PinKind.STRING, false)
    )),

    /** Sends a message over a WebSocket channel. */
    SEND_WEBSOCKET("Send WebSocket", Category.ACTION, List.of(
            new PinDef("exec_in",      PinKind.EXEC,   false),
            new PinDef("exec_out",     PinKind.EXEC,   true),
            new PinDef("data_channel", PinKind.STRING, false),
            new PinDef("data_message", PinKind.STRING, false)
    )),

    /** Runs a shell command. */
    RUN_COMMAND("Run Command", Category.ACTION, List.of(
            new PinDef("exec_in",      PinKind.EXEC,   false),
            new PinDef("exec_out",     PinKind.EXEC,   true),
            new PinDef("data_command", PinKind.STRING, false)
    )),

    /** Copies text to the system clipboard. */
    COPY_TO_CLIPBOARD("Copy to Clipboard", Category.ACTION, List.of(
            new PinDef("exec_in",   PinKind.EXEC,   false),
            new PinDef("exec_out",  PinKind.EXEC,   true),
            new PinDef("data_text", PinKind.STRING, false)
    )),

    /** Shows a desktop notification. */
    SHOW_NOTIFICATION("Show Notification", Category.ACTION, List.of(
            new PinDef("exec_in",      PinKind.EXEC,   false),
            new PinDef("exec_out",     PinKind.EXEC,   true),
            new PinDef("data_title",   PinKind.STRING, false),
            new PinDef("data_message", PinKind.STRING, false)
    )),

    /** Writes (overwrites) text to a file. */
    WRITE_TO_FILE("Write to File", Category.ACTION, List.of(
            new PinDef("exec_in",      PinKind.EXEC,   false),
            new PinDef("exec_out",     PinKind.EXEC,   true),
            new PinDef("data_path",    PinKind.STRING, false),
            new PinDef("data_content", PinKind.STRING, false)
    )),

    /** Appends text to a file. */
    APPEND_TO_FILE("Append to File", Category.ACTION, List.of(
            new PinDef("exec_in",      PinKind.EXEC,   false),
            new PinDef("exec_out",     PinKind.EXEC,   true),
            new PinDef("data_path",    PinKind.STRING, false),
            new PinDef("data_content", PinKind.STRING, false)
    )),

    /** Plays an audio file. */
    PLAY_SOUND("Play Sound", Category.ACTION, List.of(
            new PinDef("exec_in",   PinKind.EXEC,   false),
            new PinDef("exec_out",  PinKind.EXEC,   true),
            new PinDef("data_path", PinKind.STRING, false)
    ));

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final String friendlyName;
    private final Category category;
    private final List<PinDef> pins;

    // -------------------------------------------------------------------------
    // Nested types
    // -------------------------------------------------------------------------

    /**
     * Broad classification of a node's role in the graph.
     */
    public enum Category {
        /** Entry-point nodes that start an execution flow. */
        TRIGGER,
        /** Branching nodes that route execution based on a test. */
        CONDITION,
        /** Side-effect nodes that perform an operation. */
        ACTION,
        /** Pure data nodes that supply a value without affecting execution flow. */
        VALUE
    }

    /**
     * The data type carried by a pin's connection.
     */
    public enum PinKind {
        /** Execution flow wire — controls sequencing. */
        EXEC,
        /** String data wire — carries a text value. */
        STRING
    }

    /**
     * Describes a single pin on a node.
     *
     * @param id       unique pin identifier within this node type (e.g. {@code "exec_out"}, {@code "data_input"})
     * @param kind     the type of data this pin carries
     * @param isOutput {@code true} for output pins, {@code false} for input pins
     */
    public record PinDef(String id, PinKind kind, boolean isOutput) {}

    // -------------------------------------------------------------------------
    // Instance methods
    // -------------------------------------------------------------------------

    /**
     * Returns only the input pins of this node (where {@link PinDef#isOutput()} is {@code false}).
     *
     * @return an unmodifiable list of input pin descriptors; never {@code null}
     */
    public List<PinDef> inputPins() {
        return pins.stream().filter(p -> !p.isOutput()).toList();
    }

    /**
     * Returns only the output pins of this node (where {@link PinDef#isOutput()} is {@code true}).
     *
     * @return an unmodifiable list of output pin descriptors; never {@code null}
     */
    public List<PinDef> outputPins() {
        return pins.stream().filter(PinDef::isOutput).toList();
    }

    /**
     * Returns {@code true} if this trigger node type corresponds to the given {@link TriggerSource}.
     * Always returns {@code false} for non-{@link Category#TRIGGER} nodes.
     *
     * @param source the trigger source to test against
     */
    public boolean matchesSource(TriggerSource source) {
        return switch (this) {
            case SERIAL_TRIGGER      -> source == TriggerSource.SERIAL;
            case WEBSOCKET_TRIGGER   -> source == TriggerSource.WEBSOCKET;
            case DEVICE_CONNECTED    -> source == TriggerSource.DEVICE_CONNECTED;
            case DEVICE_DISCONNECTED -> source == TriggerSource.DEVICE_DISCONNECTED;
            default -> false;
        };
    }

    /**
     * For trigger nodes: maps a data output pin id to its value from the given {@link TriggerContext}.
     * Returns an empty string for unknown pin ids or when called on non-trigger nodes.
     *
     * @param ctx   the trigger context carrying the runtime values
     * @param pinId the pin id to resolve (e.g. {@code "data_input"}, {@code "data_device"})
     * @return the resolved string value, or {@code ""} if the pin id is unrecognised
     */
    public String resolveContextPin(TriggerContext ctx, String pinId) {
        return switch (pinId) {
            case "data_input"   -> ctx.input()   != null ? ctx.input()            : "";
            case "data_device"  -> ctx.device()  != null ? ctx.device().getName() : "";
            case "data_channel" -> ctx.channel() != null ? ctx.channel()          : "";
            default -> "";
        };
    }

    // -------------------------------------------------------------------------
    // Static helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the constant whose {@link Enum#name()} matches {@code name} (case-insensitive),
     * or {@code null} if no match is found.
     *
     * @param name the enum name to look up; {@code null} returns {@code null}
     */
    public static NodeType getByName(String name) {
        return EnumUtil.getByName(NodeType.class, name);
    }
}
