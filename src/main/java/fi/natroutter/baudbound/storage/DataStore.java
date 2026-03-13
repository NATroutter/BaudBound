package fi.natroutter.baudbound.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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

    public interface Named {
        String getName();
    }

    private static final Gson GSON = new Gson();
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

    public static DataStore fromJson(String json) {
        return GSON.fromJson(json, DataStore.class);
    }

    public String toJson() {
        return GSON_PRETTY.toJson(this);
    }

}