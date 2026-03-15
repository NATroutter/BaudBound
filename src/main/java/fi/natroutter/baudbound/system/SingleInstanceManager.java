package fi.natroutter.baudbound.system;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Enforces a single running instance of the application.
 * <p>
 * Detection uses an OS-level {@link FileLock} — no port conflicts possible.
 * IPC uses a loopback socket on an OS-assigned port (port 0), stored in a
 * temp file so the second instance knows where to connect.
 * <p>
 * Protocol: second instance sends {@code BAUDBOUND_PING}, first instance
 * replies {@code BAUDBOUND_PONG} then calls {@code onShowRequest}.
 * <p>
 * Crash recovery: if the lock file is stale (held by a dead process after a
 * crash or forced shutdown), the stale files are cleared and the lock is
 * re-acquired so the app starts normally on the next launch.
 */
public class SingleInstanceManager {

    private static final String PING = "BAUDBOUND_PING";
    private static final String PONG = "BAUDBOUND_PONG";
    private static final int TIMEOUT_MS = 500;

    private static final Path LOCK_FILE = Path.of(System.getProperty("java.io.tmpdir"), "baudbound.lock");
    private static final Path PORT_FILE = Path.of(System.getProperty("java.io.tmpdir"), "baudbound.port");

    private static RandomAccessFile lockRaf;
    private static FileChannel      lockChannel;
    private static FileLock         fileLock;
    private static ServerSocket     serverSocket;

    /**
     * Tries to acquire the single-instance lock.
     * <p>
     * If the lock appears stale (held by a dead process after a crash), the
     * stale files are removed and the lock is re-acquired automatically.
     *
     * @param onShowRequest called (on a daemon thread) when a second instance starts
     * @return true if this is the first instance, false if another BaudBound is already running
     */
    public static boolean tryAcquire(Runnable onShowRequest) {
        if (tryAcquireLock(onShowRequest)) {
            return true;
        }

        // Lock held by another process — check if a live BaudBound is running.
        if (signalRunningInstance()) {
            return false; // Another live instance acknowledged us.
        }

        // IPC failed: the lock is stale (left over from a crash or forced shutdown).
        // Release our partial state, clear the dead files, and try again.
        release();
        silentDelete(PORT_FILE);
        silentDelete(LOCK_FILE);

        if (tryAcquireLock(onShowRequest)) {
            return true;
        }

        // Another instance started in the race window between our delete and retry.
        // Give it a moment to bind its IPC listener, then signal it.
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        return !signalRunningInstance();
    }

    /**
     * Attempts to acquire the OS-level file lock and start the IPC listener.
     *
     * @param onShowRequest forwarded to {@link #startListener}
     * @return true if the lock was acquired and the IPC server is running
     */
    private static boolean tryAcquireLock(Runnable onShowRequest) {
        try {
            lockRaf     = new RandomAccessFile(LOCK_FILE.toFile(), "rw");
            lockChannel = lockRaf.getChannel();
            fileLock    = lockChannel.tryLock();

            if (fileLock != null) {
                // First instance — bind on an OS-assigned free port and publish it.
                serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                Files.writeString(PORT_FILE, String.valueOf(serverSocket.getLocalPort()));
                startListener(onShowRequest);
                return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    /** Deletes a path, silently ignoring any failure. */
    private static void silentDelete(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
    }

    public static void release() {
        try { if (serverSocket  != null) serverSocket.close();  } catch (IOException ignored) {}
        try { if (fileLock      != null) fileLock.release();    } catch (IOException ignored) {}
        try { if (lockChannel   != null) lockChannel.close();   } catch (IOException ignored) {}
        try { if (lockRaf       != null) lockRaf.close();       } catch (IOException ignored) {}
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

    private static boolean signalRunningInstance() {
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