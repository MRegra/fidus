package dev.sobatista.fidus.model;

import java.security.PublicKey;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The immutable, globally-known registry of identities and their public keys — users and notaries alike.
 * The assignment fixes the set of users up front; this is that set.
 */
public final class UserDirectory {
    private final Map<String, PublicKey> keys;

    public UserDirectory(Map<String, PublicKey> keys) {
        this.keys = Map.copyOf(keys);
    }

    public boolean knows(String id) {
        return keys.containsKey(id);
    }

    public PublicKey publicKey(String id) {
        PublicKey k = keys.get(id);
        if (k == null) {
            throw new IllegalArgumentException("unknown identity: " + id);
        }
        return k;
    }

    public Optional<PublicKey> lookup(String id) {
        return Optional.ofNullable(keys.get(id));
    }

    public Set<String> ids() {
        return keys.keySet();
    }
}
