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

    public static HttpMethod getByName(String name) {
        for (HttpMethod httpMethod : HttpMethod.values()) {
            if (httpMethod.name().equalsIgnoreCase(name)) {
                return httpMethod;
            }
        }
        return null;
    }

    public static int findIndex(String name) {
        HttpMethod[] httpMethods = HttpMethod.values();
        for (int i = 0; i < httpMethods.length; i++) {
            if (httpMethods[i].name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return 0;
    }

}
