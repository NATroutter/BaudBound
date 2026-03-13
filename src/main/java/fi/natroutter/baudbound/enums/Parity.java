package fi.natroutter.baudbound.enums;

import com.fazecast.jSerialComm.SerialPort;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * Serial port parity modes, mapping each constant to the corresponding
 * jSerialComm {@code SerialPort} constant via {@code bit}.
 */
@Getter
@AllArgsConstructor
public enum Parity {

    /** No parity bit. */
    NO(SerialPort.NO_PARITY),
    /** Odd parity. */
    ODD(SerialPort.ODD_PARITY),
    /** Even parity. */
    EVEN(SerialPort.EVEN_PARITY),
    /** Mark parity (parity bit always 1). */
    MARK(SerialPort.MARK_PARITY),
    /** Space parity (parity bit always 0). */
    SPACE(SerialPort.SPACE_PARITY);

    /** The jSerialComm integer constant to pass to {@link SerialPort#setParity}. */
    final int bit;

    /** Returns all constant names as a plain array, suitable for ImGui combo-boxes. */
    public static String[] asArray() {
        return Arrays.stream(Parity.values()).map(Parity::name).toArray(String[]::new);
    }
}
