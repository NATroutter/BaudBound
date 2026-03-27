package fi.natroutter.baudbound.serial;

import fi.natroutter.baudbound.enums.ConnectionStatus;
import fi.natroutter.baudbound.storage.DataStore;

import java.util.IdentityHashMap;
import java.util.List;

/**
 * Manages {@link SerialHandler} instances for all configured devices.
 * <p>
 * An {@link IdentityHashMap} keyed on {@link DataStore.Device} object identity is used so
 * that in-place field edits (e.g. renaming or changing the port) on a device object are
 * automatically visible to its handler on the next {@link #connect(DataStore.Device)} call,
 * without needing to re-register the device.
 */
public class DeviceConnectionManager {

    private final IdentityHashMap<DataStore.Device, SerialHandler> handlers = new IdentityHashMap<>();

    /**
     * Connects the given device. A new {@link SerialHandler} is created on first call for
     * each device object. Subsequent calls for the same object reuse the existing handler.
     *
     * @param device the device to connect
     */
    public void connect(DataStore.Device device) {
        handlers.computeIfAbsent(device, SerialHandler::new).connect();
    }

    /**
     * Disconnects the given device. No-op if the device has no active handler.
     *
     * @param device the device to disconnect
     */
    public void disconnect(DataStore.Device device) {
        SerialHandler handler = handlers.get(device);
        if (handler != null) handler.disconnect();
    }

    /**
     * Returns the current {@link ConnectionStatus} for the given device.
     * Returns {@link ConnectionStatus#DISCONNECTED} if no handler exists yet.
     *
     * @param device the device to query
     */
    public ConnectionStatus getStatus(DataStore.Device device) {
        SerialHandler handler = handlers.get(device);
        return handler != null ? handler.getStatus() : ConnectionStatus.DISCONNECTED;
    }

    /**
     * Disconnects and removes the handler for the given device.
     * Call this when a device is deleted from the device list.
     *
     * @param device the device to unregister
     */
    public void unregister(DataStore.Device device) {
        SerialHandler handler = handlers.remove(device);
        if (handler != null) handler.disconnect();
    }

    /**
     * Connects all devices in the list that have {@code autoConnect} enabled.
     * Called once on application startup.
     *
     * @param devices the full device list from {@link fi.natroutter.baudbound.storage.DataStore}
     */
    public void autoConnectAll(List<DataStore.Device> devices) {
        devices.stream()
                .filter(DataStore.Device::isAutoConnect)
                .forEach(this::connect);
    }

    /**
     * Sends {@code data} to the device whose custom name matches {@code deviceName}.
     * Returns {@code true} if the data was written successfully, {@code false} if no matching
     * connected device is found or the write fails.
     *
     * @param deviceName the custom name of the target device
     * @param data       the string to send to the device
     */
    public boolean sendToDevice(String deviceName, String data) {
        for (var entry : handlers.entrySet()) {
            if (entry.getKey().getName() != null && entry.getKey().getName().equals(deviceName)) {
                return entry.getValue().send(data);
            }
        }
        return false;
    }

    /**
     * Disconnects all active device connections. Called on application shutdown.
     */
    public void disconnectAll() {
        handlers.values().forEach(SerialHandler::disconnect);
    }

}