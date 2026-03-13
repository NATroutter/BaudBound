package fi.natroutter.baudbound.enums;

import java.util.Arrays;

/**
 * Standard HTTP methods available for webhook requests.
 */
public enum HttpMethod {

    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    OPTIONS;

    /** Returns all method names as a plain array, suitable for ImGui combo-boxes. */
    public static String[] asArray() {
        return Arrays.stream(HttpMethod.values()).map(HttpMethod::name).toArray(String[]::new);
    }

    /**
     * Returns the ordinal index of the constant whose name matches {@code name}
     * (case-insensitive), or {@code 0} if no match is found.
     */
    public static int findIndex(String name) {
        return EnumUtil.findIndex(HttpMethod.class, name);
    }

}
