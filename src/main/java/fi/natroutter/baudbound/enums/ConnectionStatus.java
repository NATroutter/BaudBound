package fi.natroutter.baudbound.enums;

import imgui.ImVec4;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents the current state of the serial port connection.
 * Each constant carries a human-readable {@code status} string and an RGBA {@code color}
 * used to tint the status label in the main window.
 */
@AllArgsConstructor
@Getter
public enum ConnectionStatus {
    /** Port name is configured but the device is not present or could not be found. */
    NO_DEVICE("Device Not Found!", new ImVec4(1.0f, 0.549f, 0.0f, 1.0f)),
    /** Serial port is open and the read loop is running. */
    CONNECTED("Connected!", new ImVec4(0.0f, 1.0f, 0.0f, 1.0f)),
    /** Serial port was manually disconnected or was never connected in this session. */
    DISCONNECTED("Disconnected.", new ImVec4(1.0f, 0.549f, 0.0f, 1.0f)),
    /** An attempt to open the port was made but failed (port busy, driver error, etc.). */
    FAILED_TO_CONNECT("Connection failed!", new ImVec4(1.0f, 0.0f, 0.0f, 1.0f));

    private String status;
    private ImVec4 color;

}