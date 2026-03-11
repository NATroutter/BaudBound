package fi.natroutter.baudbound.serial.data;

import com.fazecast.jSerialComm.SerialPort;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum Parity {

    NO(SerialPort.NO_PARITY),
    ODD(SerialPort.ODD_PARITY),
    EVEN(SerialPort.EVEN_PARITY),
    MARK(SerialPort.MARK_PARITY),
    SPACE(SerialPort.SPACE_PARITY);

    final int bit;

    public static String[] asArray() {
        return Arrays.stream(Parity.values()).map(Parity::name).toArray(String[]::new);
    }
}
