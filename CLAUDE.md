# BaudBound

A Java 21 desktop app that listens to a serial port and maps incoming lines to system actions (webhooks, programs, URLs, typed text). GUI is immediate-mode ImGui over GLFW.

## Stack

- **Java 21**, **Maven** — `mvn package` produces a shaded fat JAR
- **imgui-java-app** — ImGui bindings + GLFW application loop (`BaudBound extends Application`)
- **jSerialComm** — serial port access
- **foxlib** — logging, update checker, URL opener
- **Gson** — JSON config persistence
- **Lombok** — `@Data`, `@Getter`, `@AllArgsConstructor`, `@NoArgsConstructor` on data classes

## Package map

```
fi.natroutter.baudbound/
├── BaudBound.java            # Entry point; all singletons initialized here
├── enums/                    # ActionType, ConditionType, HttpMethod, Parity, FlowControl, DialogMode
│   └── EnumUtil.java         # Shared getByName / findIndex — delegates here, never copy-paste
├── event/EventHandler.java   # Processes serial input, matches conditions, fires actions
├── http/HttpHandler.java     # Fires webhook HTTP requests
├── serial/SerialHandler.java          # Connect / disconnect / read loop (per-device)
├── serial/DeviceConnectionManager.java # Manages one SerialHandler per DataStore.Device
├── storage/                           # DataStore (POJO model) + StorageProvider (load/save)
├── system/AppArgs.java          # picocli CLI flags (--hidden, --debug, --nogui, --version)
├── system/StartupManager.java
├── system/ShortcutManager.java
├── system/UpdateManager.java    # Download-and-restart for JAR self-update
└── gui/
    ├── MainWindow.java       # Fullscreen event-list window
    ├── DebugOverlay.java     # Real-time debug overlay (FPS, memory, JVM, devices, states)
    ├── MenuBar.java
    ├── theme/GuiTheme.java
    ├── util/GuiHelper.java   # listAndEditorButtons, keyValueTable, renderClickableLink, instructions
    └── dialog/
        ├── BaseDialog.java           # All dialogs extend this
        ├── MessageDialog.java        # Generic popup (does NOT extend BaseDialog)
        ├── AboutDialog / SettingsDialog / EventEditorDialog
        ├── UpdateDialog.java             # Update available: version info, release notes, download flow
        ├── components/DialogButton.java
        ├── device/   DevicesDialog, DeviceEditorDialog
        ├── webhook/  WebhooksDialog, WebhookEditorDialog
        └── program/  ProgramsDialog, ProgramEditorDialog
```

## How to build

```bash
mvn package
java -jar target/baudbound-1.0.0.jar
```

## Code quality expectations

- **No duplicate logic** — before writing a loop, helper, or utility, check whether it already exists (e.g. `EnumUtil`, `GuiHelper`, `BaseDialog`). Extract shared logic rather than copy-pasting.
- **Readable over clever** — prefer clear variable names and straightforward control flow. If something needs a comment to be understood, simplify it first.
- **Keep Javadocs up to date** — public classes, methods, and non-obvious parameters must have Javadoc comments. When modifying existing code, update any Javadoc that no longer accurately describes the behaviour.
- **Keep documentation up to date** — when adding, removing, or renaming classes, packages, enums, or features, update the package map and any relevant `agent_docs/` files in the same change. If a new area warrants its own doc, add it under `agent_docs/` and link it in the "Further reading" section.
- **Keep Javadocs up to date** — public classes, methods, and non-obvious parameters must have Javadoc comments. When modifying existing code, update any Javadoc that no longer accurately describes the behaviour.

## Critical constraints — read before touching these areas

- **Cross-thread**: GLFW calls must happen on the GLFW main thread. AWT tray callbacks set `volatile boolean` flags (`pendingShow`, `pendingExit`) that are consumed in `process()`. Never call GLFW from an AWT or virtual thread.
- **Cleanup**: Use `dispose()` (called after the GLFW loop exits), not shutdown hooks.
- **Dialog navigation**: Editor dialogs override `onClose()` in `BaseDialog` to reopen their parent list dialog when the X button is clicked.
- **Storage**: Always call `storage.save()` after mutating `DataStore`.
- **Null returns**: Collection-returning methods must return `List.of()`, never `null`.

## Further reading

For deeper context, read these before working in the relevant area:

- `agent_docs/dialog_system.md` — BaseDialog API, modal sizing, dialog flow
- `agent_docs/data_model.md` — DataStore structure, variable substitution (`{input}`, `{timestamp}`)