package fi.natroutter.baudbound.command;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Registry of named boolean statuses that can be read and written via the console command system.
 * <p>
 * Each entry has a name, a human-readable description, a getter to read the current value,
 * and a setter to apply a new value. Setters may contain side-effects (e.g. hiding the window)
 * or validation (e.g. rejecting changes in headless mode).
 * <p>
 * Register statuses with {@link #register} before starting {@link CommandHandler#startListening()}.
 */
public class StatusRegistry {

    /**
     * A single named status entry.
     *
     * @param name        lower-cased status identifier
     * @param description human-readable description shown in the status listing
     * @param getter      returns the current boolean value
     * @param setter      applies a new boolean value; may include validation or side-effects
     */
    public record Entry(String name, String description, BooleanSupplier getter, Consumer<Boolean> setter) {}

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /**
     * Registers a new status. If a status with the same name already exists it is replaced.
     *
     * @param name        the status identifier (stored lower-cased)
     * @param description human-readable description
     * @param getter      supplies the current value
     * @param setter      accepts a new value and applies it
     */
    public void register(String name, String description, BooleanSupplier getter, Consumer<Boolean> setter) {
        entries.put(name.toLowerCase(), new Entry(name.toLowerCase(), description, getter, setter));
    }

    /** Returns all registered entries in registration order. */
    public Collection<Entry> getAll() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /** Returns the entry for {@code name} (case-insensitive), or empty if not found. */
    public Optional<Entry> find(String name) {
        return Optional.ofNullable(entries.get(name.toLowerCase()));
    }
}