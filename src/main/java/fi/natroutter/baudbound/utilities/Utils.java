package fi.natroutter.baudbound.utilities;

import java.util.Arrays;
import java.util.Comparator;

public class Utils {

    public static String getLongest(String[] array) {
        return Arrays.stream(array)
                .max(Comparator.comparingInt(String::length))
                .orElse(null);
    }
}
