package fi.natroutter.baudbound.serial.data;

import com.fazecast.jSerialComm.SerialPort;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@AllArgsConstructor
@Getter
public enum FlowControl {

    DISABLED(SerialPort.FLOW_CONTROL_DISABLED),
    RTS(SerialPort.FLOW_CONTROL_RTS_ENABLED),
    CTS(SerialPort.FLOW_CONTROL_CTS_ENABLED),
    DSR(SerialPort.FLOW_CONTROL_DSR_ENABLED),
    DTR(SerialPort.FLOW_CONTROL_DTR_ENABLED),
    XONXOFF_IN(SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED),
    XONXOFF_OUT(SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED);

    final int bit;

    public static String[] asArray() {
        return Arrays.stream(FlowControl.values()).map(FlowControl::name).toArray(String[]::new);
    }
}
