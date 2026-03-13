package fi.natroutter.baudbound.enums;

import java.util.Arrays;

public enum HttpMethod {

    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    OPTIONS;


    public static String[] asArray() {
        return Arrays.stream(HttpMethod.values()).map(HttpMethod::name).toArray(String[]::new);
    }

    public static int findIndex(String name) {
        return EnumUtil.findIndex(HttpMethod.class, name);
    }

}
