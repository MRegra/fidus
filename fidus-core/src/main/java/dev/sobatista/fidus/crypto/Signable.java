package dev.sobatista.fidus.crypto;

import java.io.Serializable;

/** Something that can be signed: it knows the exact, deterministic bytes a signature must cover. */
public interface Signable extends Serializable {
    /** Deterministic bytes covering every security-relevant field. See {@link Bytes}. */
    byte[] canonicalBytes();
}
