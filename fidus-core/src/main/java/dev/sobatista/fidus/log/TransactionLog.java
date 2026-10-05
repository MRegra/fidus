package dev.sobatista.fidus.log;

import java.io.Closeable;
import java.util.List;

/** An append-only, replayable log of the notary's decisions. */
public interface TransactionLog extends Closeable {
    /** Durably append one entry. Must return only once the entry will survive a crash. */
    void append(LogEntry entry);

    /** Every entry ever appended, in order — used to rebuild state on startup. */
    List<LogEntry> replay();
}
