# Data model

## DataStore

`storage/DataStore.java` is the root config POJO. Structure:

```
DataStore
├── Settings
│   ├── Generic  (startHidden)
│   └── Event    (runFirstOnly, conditionEventsFirst, skipEmptyConditions)
├── Actions
│   ├── List<Webhook>  (name, url, method, headers, body, urlEscape)
│   └── List<Program>  (name, path, arguments, runAsAdmin)
├── List<Device>       (name, port, baudRate, dataBits, stopBits, parity, flowControl, autoConnect)
└── List<Event>        (name, conditions, actions)
    ├── Condition  (type: ConditionType name, value, caseSensitive)
    └── Action     (type: ActionType name, value)
```

All classes use Lombok `@Data` + `@NoArgsConstructor` + `@AllArgsConstructor`. Gson serialization uses `@SerializedName`.

`DataStore` exposes `static final GSON` (plain) and `GSON_PRETTY` (pretty-printed) — use `fromJson` / `toJson` methods, don't create Gson instances elsewhere.

## Device connections

`serial/DeviceConnectionManager.java` manages one `SerialHandler` per `DataStore.Device` using an `IdentityHashMap` (keyed on object identity so in-place edits are reflected automatically).

- `connect(device)` / `disconnect(device)` — toggle a specific device
- `getStatus(device)` — returns the current `ConnectionStatus`
- `unregister(device)` — disconnect and remove (call on delete)
- `autoConnectAll(devices)` — called on startup; connects devices with `autoConnect = true`
- `disconnectAll()` — called on shutdown

## StorageProvider

`storage/StorageProvider.java` — call `getData()` to read and `save()` to persist. Always call `save()` after mutating any list or field.

## Variable substitution

`EventHandler.resolve(template, input)` replaces placeholders in webhook URLs, bodies, header values, program arguments, open-URL values, and typed text:

| Placeholder | Replaced with |
|---|---|
| `{input}` | Raw serial line that triggered the event |
| `{timestamp}` | `LocalDateTime.now()` formatted as ISO local date-time |

See `event/EventHandler.java:178`.

## Enum utilities

`EnumUtil.getByName(Class<E>, String)` — case-insensitive lookup, returns null if not found.
`EnumUtil.findIndex(Class<E>, String)` — returns index, 0 if not found.

`ActionType`, `ConditionType`, and `HttpMethod` each expose `getByName` and `findIndex` that delegate to `EnumUtil`. Use those — do not add new loop implementations.