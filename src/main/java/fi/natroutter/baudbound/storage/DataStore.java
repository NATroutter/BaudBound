package fi.natroutter.baudbound.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Root JSON model for all persisted application state.
 * <p>
 * The object graph mirrors the {@code storage.json} structure exactly:
 * <ul>
 *   <li>{@link Settings} — generic, event-processing, and device configuration</li>
 *   <li>{@link Actions} — saved webhook and program definitions</li>
 *   <li>events list — ordered list of named {@link Event} entries</li>
 * </ul>
 * Serialization is handled by {@link #fromJson} / {@link #toJson}; all field names
 * are mapped via {@code @SerializedName} so that renaming Java fields won't break
 * existing config files.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataStore {

    @SerializedName("settings")
    private Settings settings = new Settings();

    @SerializedName("actions")
    private Actions actions = new Actions();

    @SerializedName("events")
    private List<Event> events = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Settings {

        @SerializedName("generic")
        private Generic generic = new Generic();

        @SerializedName("event")
        private Event event = new Event();

        @SerializedName("device")
        private Device device = new Device();

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Generic {

            @SerializedName("start_hidden")
            private boolean startHidden;

            @SerializedName("auto_connect")
            private boolean autoConnect;

        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Event {

            @SerializedName("run_first_only")
            private boolean runFirstOnly;

            @SerializedName("condition_events_first")
            private boolean conditionEventsFirst;

            @SerializedName("skip_empty_conditions")
            private boolean skipEmptyConditions;

        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Device {

            @SerializedName("port")
            private String port;

            @SerializedName("baud_rate")
            private int baudRate;

            @SerializedName("data_bits")
            private int dataBits;

            @SerializedName("stop_bits")
            private int stopBits;

            @SerializedName("parity")
            private String parity;

            @SerializedName("flow_control")
            private String flowControl;

        }

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Event implements Named {

        @SerializedName("name")
        private String name;

        @SerializedName("conditions")
        private List<Condition> conditions = new ArrayList<>();

        @SerializedName("actions")
        private List<Action> actions = new ArrayList<>();

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Condition {

            @SerializedName("type")
            private String type;

            @SerializedName("value")
            private String value;

            @SerializedName("case_sensitive")
            private boolean caseSensitive;

        }

        /** Returns a fully independent deep copy of this event (no shared list or condition/action references). */
        public Event deepCopy() {
            List<Condition> conditionsCopy = conditions == null ? new ArrayList<>() :
                    conditions.stream().map(c -> new Condition(c.getType(), c.getValue(), c.isCaseSensitive())).toList();
            List<Action> actionsCopy = actions == null ? new ArrayList<>() :
                    actions.stream().map(a -> new Action(a.getType(), a.getValue())).toList();
            return new Event(name, conditionsCopy, actionsCopy);
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Action {

            @SerializedName("type")
            private String type;

            @SerializedName("value")
            private String value;

        }

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Actions {

        @SerializedName("webhooks")
        private List<Webhook> webhooks = new ArrayList<>();

        @SerializedName("programs")
        private List<Program> programs = new ArrayList<>();

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Webhook implements Named {

            @SerializedName("name")
            private String name;

            @SerializedName("url")
            private String url;

            @SerializedName("method")
            private String method;

            @SerializedName("headers")
            private List<Header> headers = new ArrayList<>();

            @SerializedName("body")
            private String body;

            /**
             * When {@code true}, the values substituted for {@code {input}} and
             * {@code {timestamp}} are URL-encoded before being inserted into the
             * URL, headers, and body templates.
             */
            @SerializedName("url_escape")
            private boolean urlEscape;

            /** Returns a fully independent deep copy of this webhook (headers list is not shared). */
            public Webhook deepCopy() {
                List<Header> headersCopy = headers == null ? new ArrayList<>() :
                        headers.stream().map(h -> new Header(h.getKey(), h.getValue())).toList();
                return new Webhook(name, url, method, headersCopy, body, urlEscape);
            }

            @Data
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Header {

                @SerializedName("key")
                private String key;

                @SerializedName("value")
                private String value;

            }
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Program implements Named {

            /** Returns a fully independent deep copy of this program entry. */
            public Program deepCopy() {
                return new Program(name, path, arguments, runAsAdmin);
            }


            @SerializedName("name")
            private String name;

            @SerializedName("path")
            private String path;

            @SerializedName("arguments")
            private String arguments;

            @SerializedName("run_as_admin")
            private boolean runAsAdmin;

        }

    }

    /** Marker interface for list items that have a display name (used by {@code GuiHelper}). */
    public interface Named {
        String getName();
    }

    private static final Gson GSON = new Gson();
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Deserializes a {@code DataStore} from the given JSON string.
     *
     * @param json the JSON content to parse
     * @return the populated {@code DataStore} instance
     */
    public static DataStore fromJson(String json) {
        return GSON.fromJson(json, DataStore.class);
    }

    /**
     * Serializes this instance to a pretty-printed JSON string suitable for writing to disk.
     */
    public String toJson() {
        return GSON_PRETTY.toJson(this);
    }

}