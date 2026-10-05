package dev.sobatista.fidus.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/** Reads and writes RSA keys as DER files: {@code <id>.pub} (X.509) and {@code <id>.key} (PKCS#8). */
public final class KeyFiles {
    private KeyFiles() {}

    public static void write(Path dir, String id, KeyPair kp) throws IOException {
        Files.createDirectories(dir);
        Files.write(dir.resolve(id + ".pub"), kp.getPublic().getEncoded());
        Files.write(dir.resolve(id + ".key"), kp.getPrivate().getEncoded());
    }

    public static PublicKey readPublic(Path file) throws IOException {
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IOException("bad public key: " + file, e);
        }
    }

    public static PrivateKey readPrivate(Path file) throws IOException {
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IOException("bad private key: " + file, e);
        }
    }
}
