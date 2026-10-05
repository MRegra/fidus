package dev.sobatista.fidus.model;

import dev.sobatista.fidus.crypto.Bytes;
import dev.sobatista.fidus.crypto.Signable;

/**
 * The public state of one good: who owns it, whether it is on sale, and the logical timestamp of the
 * write that produced this state.
 *
 * <p>The {@code timestamp} turns a good's state into a versioned register value — the (1,N) Byzantine
 * atomic register picks the highest-timestamped value a quorum reports, and writes it back.
 */
public record GoodState(String goodId, String ownerId, boolean onSale, long timestamp) implements Signable {
    @Override
    public byte[] canonicalBytes() {
        return Bytes.of("GoodState", goodId, ownerId, onSale, timestamp);
    }
}
