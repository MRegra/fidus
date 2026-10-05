package dev.sobatista.fidus.node;

import dev.sobatista.fidus.crypto.Signatures;
import dev.sobatista.fidus.model.UserDirectory;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Generates and holds RSA key pairs for a set of identities, and hands out the public {@link UserDirectory}. */
public final class Identities {
    private final Map<String, KeyPair> keys = new HashMap<>();

    public Identities(Collection<String> ids, int bits) {
        for (String id : ids) {
            keys.put(id, Signatures.generateRsa(bits));
        }
    }

    public UserDirectory directory() {
        Map<String, PublicKey> pub = new HashMap<>();
        keys.forEach((id, kp) -> pub.put(id, kp.getPublic()));
        return new UserDirectory(pub);
    }

    public PrivateKey priv(String id) {
        return keys.get(id).getPrivate();
    }

    public PublicKey pub(String id) {
        return keys.get(id).getPublic();
    }
}
