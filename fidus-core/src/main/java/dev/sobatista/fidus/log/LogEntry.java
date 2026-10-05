package dev.sobatista.fidus.log;

import dev.sobatista.fidus.message.TransferCertificate;

import java.io.Serializable;

/**
 * One durable record of something the notary decided. The log is the notary's memory across crashes:
 * state is <em>only</em> ever rebuilt by replaying these, so anything not logged did not happen.
 */
public sealed interface LogEntry extends Serializable permits LogEntry.IntentionLogged, LogEntry.TransferLogged {

    /** The acting user whose write sequence this entry advances. */
    String user();

    /** That user's monotonic write sequence for this operation. */
    long sequence();

    /** A good was put on sale by its owner. */
    record IntentionLogged(String ownerId, String goodId, long sequence, long timestamp) implements LogEntry {
        @Override public String user() { return ownerId; }
    }

    /** A good changed hands; the certificate is the signed proof. */
    record TransferLogged(TransferCertificate certificate) implements LogEntry {
        @Override public String user() { return certificate.sellerId(); }
        @Override public long sequence() { return certificate.sequence(); }
    }
}
