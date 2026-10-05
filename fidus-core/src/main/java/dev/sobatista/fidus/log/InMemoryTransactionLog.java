package dev.sobatista.fidus.log;

import java.util.ArrayList;
import java.util.List;

/** A non-durable log for tests and in-process demos. */
public final class InMemoryTransactionLog implements TransactionLog {
    private final List<LogEntry> entries = new ArrayList<>();

    @Override
    public synchronized void append(LogEntry entry) {
        entries.add(entry);
    }

    @Override
    public synchronized List<LogEntry> replay() {
        return List.copyOf(entries);
    }

    @Override
    public void close() {
        // nothing to release
    }
}
