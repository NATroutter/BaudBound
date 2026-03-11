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

            @SerializedName("start_with_os")
            private boolean startWithOS;

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

        @SerializedName("type")
        private String type;

        @SerializedName("conditions")
        private List<Condition> conditions = new ArrayList<>();

        @SerializedName("action_webhook")
        private String actionWebhook;

        @SerializedName("action_open_url")
        private String actionOpenUrl;

        @SerializedName("action_open_program_path")
        private String actionOpenProgramPath;

        @SerializedName("action_open_program_args")
        private String actionOpenProgramArgs;

        @SerializedName("action_type_text")
        private String actionTypeText;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Condition {

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

        @SerializedName("websites")
        private List<Websites> websites = new ArrayList<>();


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
        public static class Websites implements Named {

            @SerializedName("name")
            private String name;

            @SerializedName("url")
            private String url;

        }

    }

    public interface Named {
        String getName();
    }

    public static DataStore fromJson(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, DataStore.class);
    }

    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

}