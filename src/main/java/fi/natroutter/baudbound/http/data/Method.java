package fi.natroutter.baudbound.http.data;

import java.util.Arrays;

public enum Method {

    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD,
    OPTIONS;


    public static String[] asArray() {
        return Arrays.stream(Method.values()).map(Method::name).toArray(String[]::new);
    }

    public static Method getByName(String name) {
        for (Method method : Method.values()) {
            if (method.name().equalsIgnoreCase(name)) {
                return method;
            }
        }
        return null;
    }

    public static int findIndex(String name) {
        Method[] methods = Method.values();
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return 0;
    }

}
