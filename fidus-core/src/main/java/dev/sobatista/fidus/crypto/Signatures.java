package dev.sobatista.fidus.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

/** RSA digital signatures (SHA-256 with RSA). The one place crypto primitives live. */
public final class Signatures {
    public static final String ALGORITHM = "SHA256withRSA";

    private Signatures() {}

    public static byte[] sign(PrivateKey key, byte[] data) {
        try {
            Signature s = Signature.getInstance(ALGORITHM);
            s.initSign(key);
            s.update(data);
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("signing failed", e);
        }
    }

    /** Verify a signature. A null or malformed signature simply fails to verify — it never throws. */
    public static boolean verify(PublicKey key, byte[] data, byte[] signature) {
        if (signature == null) {
            return false;
        }
        try {
            Signature s = Signature.getInstance(ALGORITHM);
            s.initVerify(key);
            s.update(data);
            return s.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    public static KeyPair generateRsa(int bits) {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(bits);
            return g.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
