package dev.sobatista.fidus.message;

import dev.sobatista.fidus.crypto.Bytes;
import dev.sobatista.fidus.crypto.Signable;

/** A buyer telling a seller it wants to buy a good (step 5 in the protocol). Signed by the buyer. */
public record BuyRequest(String buyerId, String sellerId, String goodId, long nonce) implements Signable {
    @Override
    public byte[] canonicalBytes() {
        return Bytes.of("BuyRequest", buyerId, sellerId, goodId, nonce);
    }
}
