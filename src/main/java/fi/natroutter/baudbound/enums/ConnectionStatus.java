package fi.natroutter.baudbound.enums;

import imgui.ImVec4;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ConnectionStatus {
    NO_DEVICE("Device Not Found!", new ImVec4(1.0f, 0.549f, 0.0f, 1.0f)),
    CONNECTED("Connected!", new ImVec4(0.0f, 1.0f, 0.0f, 1.0f)),
    DISCONNECTED("Disconnected.", new ImVec4(1.0f, 0.549f, 0.0f, 1.0f)),
    FAILED_TO_CONNECT("Connection failed!", new ImVec4(1.0f, 0.0f, 0.0f, 1.0f));

    private String status;
    private ImVec4 color;

}