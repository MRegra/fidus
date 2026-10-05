package dev.sobatista.fidus.message;

import dev.sobatista.fidus.crypto.Bytes;
import dev.sobatista.fidus.crypto.Signable;

/** A seller asking the notary to transfer a good to a buyer. Signed by the seller; {@code sequence} is theirs. */
public record TransferRequest(String sellerId, String buyerId, String goodId, long sequence) implements Signable {
    @Override
    public byte[] canonicalBytes() {
        return Bytes.of("TransferRequest", sellerId, buyerId, goodId, sequence);
    }
}
