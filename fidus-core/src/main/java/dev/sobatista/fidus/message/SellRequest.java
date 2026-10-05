package dev.sobatista.fidus.message;

import dev.sobatista.fidus.crypto.Bytes;
import dev.sobatista.fidus.crypto.Signable;

/** A seller's intention to put one of their goods on sale. {@code sequence} is the seller's monotonic write counter. */
public record SellRequest(String ownerId, String goodId, long sequence) implements Signable {
    @Override
    public byte[] canonicalBytes() {
        return Bytes.of("SellRequest", ownerId, goodId, sequence);
    }
}
