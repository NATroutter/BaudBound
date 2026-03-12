package fi.natroutter.baudbound.serial;

import com.fazecast.jSerialComm.SerialPort;
import fi.natroutter.baudbound.BaudBound;
import fi.natroutter.baudbound.enums.ConnectionStatus;
import fi.natroutter.baudbound.enums.FlowControl;
import fi.natroutter.baudbound.enums.Parity;
import fi.natroutter.baudbound.event.EventHandler;
import fi.natroutter.baudbound.storage.DataStore;
import fi.natroutter.baudbound.storage.StorageProvider;
import fi.natroutter.foxlib.logger.FoxLogger;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

public class SerialHandler {

    private final FoxLogger logger = BaudBound.getLogger();
    private final StorageProvider storage = BaudBound.getStorageProvider();
    private final EventHandler eventHandler = BaudBound.getEventHandler();


    @Getter
    private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;
    private volatile boolean shuttingDown = false;

    private SerialPort port;
    private Thread listenerThread;

    public SerialHandler() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shuttingDown = true;
            disconnect();
        }));

        if (storage.getData().getSettings().getGeneric().isAutoConnect()) {
            connect();
        }
    }

    public void connect() {
        if (status == ConnectionStatus.CONNECTED) return;

        DataStore.Settings.Device device = storage.getData().getSettings().getDevice();

        if (device.getPort() == null || device.getPort().isBlank()) {
            status = ConnectionStatus.NO_DEVICE;
            logger.error("No port configured.");
            return;
        }

        port = SerialPort.getCommPort(device.getPort());
        port.setBaudRate(device.getBaudRate() > 0 ? device.getBaudRate() : 9600);
        port.setNumDataBits(device.getDataBits() > 0 ? device.getDataBits() : 8);
        port.setNumStopBits(device.getStopBits() > 0 ? device.getStopBits() : 1);

        if (device.getParity() != null) {
            Parity parity = Parity.valueOf(device.getParity());
            port.setParity(parity.getBit());
        }

        if (device.getFlowControl() != null) {
            FlowControl flowControl = FlowControl.valueOf(device.getFlowControl());
            port.setFlowControl(flowControl.getBit());
        }

        port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

        if (!port.openPort()) {
            status = ConnectionStatus.FAILED_TO_CONNECT;
            logger.error("Failed to open port: " + device.getPort());
            return;
        }

        status = ConnectionStatus.CONNECTED;
        logger.info("Connected to " + device.getPort());

        listenerThread = Thread.ofVirtual().start(this::readLoop);
    }

    public void disconnect() {
        if (status != ConnectionStatus.CONNECTED) return;

        status = ConnectionStatus.DISCONNECTED;

        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }

        if (port != null && port.isOpen()) {
            port.closePort();
            port = null;
        }

        logger.info("Disconnected from serial port.");
    }

    // -------------------------------------------------------------------------
    // Background read loop
    // -------------------------------------------------------------------------

    private void readLoop() {
        StringBuilder lineBuffer = new StringBuilder();
        byte[] buf = new byte[1024];

        while (status == ConnectionStatus.CONNECTED && !Thread.currentThread().isInterrupted()) {
            try {
                // Physical disconnect: port reports itself closed or bytesAvailable returns -1
                if (!port.isOpen()) {
                    if (!shuttingDown && status == ConnectionStatus.CONNECTED) {
                        logger.error("Serial port closed unexpectedly — device disconnected.");
                        status = ConnectionStatus.NO_DEVICE;
                    }
                    break;
                }

                int available = port.bytesAvailable();

                if (available < 0) {
                    // -1 means port error / physical disconnection
                    if (!shuttingDown && status == ConnectionStatus.CONNECTED) {
                        logger.error("Serial port read failed — device disconnected.");
                        status = ConnectionStatus.NO_DEVICE;
                    }
                    break;
                }

                if (available == 0) {
                    try { Thread.sleep(10); } catch (InterruptedException e) { break; }
                    continue;
                }

                int bytesRead = port.readBytes(buf, Math.min(available, buf.length));
                if (bytesRead <= 0) continue;

                // Normalise all line endings (\r\n, \r, \n) to \n so the
                // split logic works regardless of what the device sends.
                String chunk = new String(buf, 0, bytesRead)
                        .replace("\r\n", "\n")
                        .replace("\r", "\n");
                lineBuffer.append(chunk);

                // Process all complete lines
                int newlineIndex;
                while ((newlineIndex = lineBuffer.indexOf("\n")) >= 0) {
                    String line = lineBuffer.substring(0, newlineIndex).strip();
                    lineBuffer.delete(0, newlineIndex + 1);

                    if (!line.isEmpty()) {
                        logger.info("Serial input: " + line);
                        eventHandler.process(line);
                    }
                }
            } catch (Exception e) {
                if (!shuttingDown && status == ConnectionStatus.CONNECTED) {
                    logger.error("Serial read error: " + e.getMessage());
                    status = ConnectionStatus.FAILED_TO_CONNECT;
                }
                break;
            }
        }

        // Always clean up the port when the loop exits for any reason
        if (port != null && port.isOpen()) {
            port.closePort();
        }
        port = null;
        listenerThread = null;

        // Auto-reconnect if the disconnect was unexpected and the setting is enabled
        if (!shuttingDown
                && status == ConnectionStatus.NO_DEVICE
                && storage.getData().getSettings().getGeneric().isAutoConnect()) {
            startReconnectLoop();
        }
    }

    private void startReconnectLoop() {
        Thread.ofVirtual().start(() -> {
            logger.info("Auto-reconnect enabled — retrying every 5 seconds...");
            while (status == ConnectionStatus.NO_DEVICE) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (status != ConnectionStatus.NO_DEVICE) break;
                logger.info("Attempting to reconnect...");
                connect();
            }
        });
    }

    public List<SerialPort> getDevices() {
        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports.length == 0) {
            logger.error("No serial ports found");
            return null;
        }
        return Arrays.asList(ports);
    }
}