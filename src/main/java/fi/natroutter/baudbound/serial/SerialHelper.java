package fi.natroutter.baudbound.serial;

import com.fazecast.jSerialComm.SerialPort;
import fi.natroutter.foxlib.logger.FoxLogger;
import fi.natroutter.baudbound.BaudBound;

import java.util.Arrays;
import java.util.List;

public class SerialHelper {

    private static FoxLogger logger = BaudBound.getLogger();

    public static List<SerialPort> getDevices() {
        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports.length == 0) {
            logger.error("No serial ports found");
            return null;
        }
        return Arrays.asList(ports);
    }

    public static String getDeviceName(SerialPort port) {
        return port.getSystemPortName() + " - " + port.getDescriptivePortName();
    }

    public static List<String> getDeviceNames() {
        List<SerialPort> devices = getDevices();
        if (devices == null) return null;
        return devices.stream().map(SerialHelper::getDeviceName).toList();
    }

}
