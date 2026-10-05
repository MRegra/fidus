package dev.sobatista.fidus.crypto;

import java.io.Serializable;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

/**
 * A signed envelope: a payload, the id of who signed it, and the signature over {@link Signable#canonicalBytes()}.
 *
 * <p>This is the only thing that crosses the wire. An entity verifies it against the signer's known public key;
 * a forged or tampered envelope fails {@link #verify(PublicKey)}. Equality is by value (bytes included) so signed
 * envelopes can live in the sets that Byzantine broadcast counts.
 */
public record Signed<T extends Signable>(T payload, String signerId, byte[] signature) implements Serializable {

    public static <T extends Signable> Signed<T> sign(T payload, String signerId, PrivateKey key) {
        return new Signed<>(payload, signerId, Signatures.sign(key, payload.canonicalBytes()));
    }

    public boolean verify(PublicKey key) {
        return Signatures.verify(key, payload.canonicalBytes(), signature);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Signed<?> s
                && signerId.equals(s.signerId)
                && payload.equals(s.payload)
                && Arrays.equals(signature, s.signature);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * payload.hashCode() + signerId.hashCode()) + Arrays.hashCode(signature);
    }

    @Override
    public String toString() {
        return "Signed[" + signerId + " -> " + payload + "]";
    }
}
