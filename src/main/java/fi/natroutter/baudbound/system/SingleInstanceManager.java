package fi.natroutter.baudbound.system;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Enforces a single running instance of the application.
 * <p>
 * Detection uses a temp file containing an OS-assigned loopback port. On
 * startup, a {@code BAUDBOUND_PING}/{@code BAUDBOUND_PONG} handshake is
 * attempted on the stored port. Only a live BaudBound instance will respond
 * correctly, so stale files left after a crash or forced shutdown are
 * automatically ignored — the OS releases the port when the process dies, and
 * no other app will speak the protocol.
 * <p>
 * Protocol: second instance sends {@code BAUDBOUND_PING}, first instance
 * replies {@code BAUDBOUND_PONG} then calls {@code onShowRequest}.
 */
public class SingleInstanceManager {

    private static final String PING       = "BAUDBOUND_PING";
    private static final String PONG       = "BAUDBOUND_PONG";
    private static final int    TIMEOUT_MS = 500;

    private static final Path PORT_FILE =
            Path.of(System.getProperty("java.io.tmpdir"), "baudbound.port");

    private static ServerSocket serverSocket;

    /**
     * Tries to become the single running instance.
     * <p>
     * If the stored port responds with the correct handshake, a show-request
     * signal is sent and this method returns {@code false}. Otherwise (file
     * absent, port not listening, or wrong response — all indicating no live
     * instance), this instance binds a loopback socket, writes the port file,
     * starts the IPC listener, and returns {@code true}.
     *
     * @param onShowRequest called (on a daemon thread) when a second instance starts
     * @return {@code true} if this is the first instance and the app should continue,
     *         {@code false} if another instance is already running
     */
    public static boolean tryAcquire(Runnable onShowRequest) {
        if (pingRunningInstance()) {
            return false;
        }

        // No live BaudBound instance responded — start as first instance.
        try {
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            Files.writeString(PORT_FILE, String.valueOf(serverSocket.getLocalPort()));
            startListener(onShowRequest);
        } catch (IOException ignored) {
            // IPC listener failed to bind — not fatal, the app still starts.
        }

        return true;
    }

    /**
     * Closes the IPC server socket and removes the port file.
     * Call this from {@code dispose()} when the application exits normally.
     */
    public static void release() {
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        try { Files.deleteIfExists(PORT_FILE); } catch (IOException ignored) {}
    }

    // -------------------------------------------------------------------------

    private static void startListener(Runnable onShowRequest) {
        Thread t = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try (Socket client = serverSocket.accept()) {
                    client.setSoTimeout(TIMEOUT_MS);
                    String msg = new String(client.getInputStream().readNBytes(PING.length()), StandardCharsets.UTF_8);
                    if (PING.equals(msg)) {
                        client.getOutputStream().write(PONG.getBytes(StandardCharsets.UTF_8));
                        onShowRequest.run();
                    }
                } catch (IOException ignored) {}
            }
        }, "single-instance-listener");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Reads the port file and attempts the PING/PONG handshake.
     *
     * @return {@code true} if a live BaudBound instance acknowledged the ping
     */
    private static boolean pingRunningInstance() {
        try {
            int port = Integer.parseInt(Files.readString(PORT_FILE).trim());
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
                socket.setSoTimeout(TIMEOUT_MS);
                socket.getOutputStream().write(PING.getBytes(StandardCharsets.UTF_8));
                String response = new String(socket.getInputStream().readNBytes(PONG.length()), StandardCharsets.UTF_8);
                return PONG.equals(response);
            }
        } catch (IOException | NumberFormatException ignored) {
            return false;
        }
    }
}