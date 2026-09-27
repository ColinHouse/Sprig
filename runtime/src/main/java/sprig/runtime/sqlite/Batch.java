package sprig.runtime.sqlite;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A batch snapshots each parameter list and executes atomically on one connection. */
public final class Batch {
    record Command(String sql, List<Object> parameters) {}
    private final List<Command> commands = new ArrayList<>();
    public Batch() {}
    public void add(String sql, Parameters parameters) {
        commands.add(new Command(Objects.requireNonNull(sql), parameters.snapshot()));
    }
    List<Command> snapshot() { return List.copyOf(commands); }
}
