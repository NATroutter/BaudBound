package fi.natroutter.baudbound.enums;

public final class EnumUtil {

    private EnumUtil() {}

    public static <E extends Enum<E>> E getByName(Class<E> enumClass, String name) {
        if (name == null) return null;
        for (E value : enumClass.getEnumConstants()) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }

    public static <E extends Enum<E>> int findIndex(Class<E> enumClass, String name) {
        if (name == null) return 0;
        E[] values = enumClass.getEnumConstants();
        for (int i = 0; i < values.length; i++) {
            if (values[i].name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return 0;
    }

}