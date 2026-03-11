package fi.natroutter.baudbound.serial;

import com.fazecast.jSerialComm.SerialPort;

public class SerialHandler {

    public void test() {
        // List all available ports
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            System.out.println(port.getSystemPortName() + " - " + port.getDescriptivePortName());
        }

        // Open your device's port (change COM3 to whatever yours is)
        SerialPort device = SerialPort.getCommPort("COM3");
        device.setBaudRate(9600);        // try 9600 or 115200
        device.setNumDataBits(8);
        device.setNumStopBits(1);
//        device.setNumStopBits(1.3);
//        device.setFlowControl(SerialPort.flowControl);
//        device.setParity(SerialPort.parity);

        if (device.openPort()) {
            System.out.println("Port opened successfully");

            // Read incoming data in a loop
            byte[] buffer = new byte[1024];
            while (true) {
                if (device.bytesAvailable() > 0) {
                    int bytesRead = device.readBytes(buffer, buffer.length);
                    String barcode = new String(buffer, 0, bytesRead).trim();
                    System.out.println("Scanned: " + barcode);
                }
            }
        } else {
            System.out.println("Failed to open port");
        }
    }

}
