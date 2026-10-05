package dev.sobatista.fidus.message;

import dev.sobatista.fidus.crypto.Bytes;
import dev.sobatista.fidus.crypto.Signable;

/**
 * The notary's signed proof that a transfer happened: good {@code goodId} passed from {@code sellerId} to
 * {@code buyerId}. Signed by the notary and persisted, it is verifiable by anyone with the notary's public key —
 * the non-repudiable receipt the assignment asks for.
 */
public record TransferCertificate(String goodId, String sellerId, String buyerId, long sequence, long timestamp)
        implements Signable {
    @Override
    public byte[] canonicalBytes() {
        return Bytes.of("TransferCertificate", goodId, sellerId, buyerId, sequence, timestamp);
    }
}
