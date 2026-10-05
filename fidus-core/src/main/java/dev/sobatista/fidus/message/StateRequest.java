package dev.sobatista.fidus.message;

import dev.sobatista.fidus.crypto.Bytes;
import dev.sobatista.fidus.crypto.Signable;

/** A read of a good's state. Signed by the asker so the notary serves only legitimate users; {@code nonce} is fresh per read. */
public record StateRequest(String readerId, String goodId, long nonce) implements Signable {
    @Override
    public byte[] canonicalBytes() {
        return Bytes.of("StateRequest", readerId, goodId, nonce);
    }
}
