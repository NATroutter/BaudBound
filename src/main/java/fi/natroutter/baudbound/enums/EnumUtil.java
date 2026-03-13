package fi.natroutter.baudbound.enums;

/**
 * Shared enum lookup utilities used by all enums in this package.
 * <p>
 * Centralising these here prevents copy-pasting identical {@code getByName} /
 * {@code findIndex} loops across every enum class.
 */
public final class EnumUtil {

    private EnumUtil() {}

    /**
     * Returns the constant of {@code enumClass} whose {@link Enum#name()} matches
     * {@code name} (case-insensitive), or {@code null} if none matches.
     *
     * @param <E>       the enum type
     * @param enumClass the enum class to search
     * @param name      the name to look up; {@code null} returns {@code null}
     */
    public static <E extends Enum<E>> E getByName(Class<E> enumClass, String name) {
        if (name == null) return null;
        for (E value : enumClass.getEnumConstants()) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Returns the ordinal index of the constant whose {@link Enum#name()} matches
     * {@code name} (case-insensitive), or {@code 0} if none matches.
     *
     * @param <E>       the enum type
     * @param enumClass the enum class to search
     * @param name      the name to look up; {@code null} returns {@code 0}
     */
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