package dev.sobatista.fidus.node;

import dev.sobatista.fidus.crypto.KeyFiles;
import dev.sobatista.fidus.crypto.Signatures;
import dev.sobatista.fidus.model.UserDirectory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Sets up (or loads) a small notary world on disk so a server and a client, started separately, agree on identities
 * and keys. Keys live under {@code <storage>/keys/<id>.pub|.key}; ownership under {@code <storage>/goods.txt}
 * ({@code GOOD OWNER} per line). A teaching convenience — real users would each hold only their own private key.
 */
public final class Bootstrap {
    public static final String NOTARY_ID = "NOTARY";

    private final Path storage;
    private final Path keysDir;

    public Bootstrap(Path storage) {
        this.storage = storage;
        this.keysDir = storage.resolve("keys");
    }

    public Path storage() {
        return storage;
    }

    public Path logFile() {
        return storage.resolve("notary.log");
    }

    /** Generate the demo world (notary + alice/bob/carol, goods G1..G4) if it is not already present. */
    public void createIfAbsent() {
        try {
            if (Files.exists(keysDir) && Files.list(keysDir).findAny().isPresent()) {
                return;
            }
            Files.createDirectories(keysDir);
            for (String id : List.of(NOTARY_ID, "alice", "bob", "carol")) {
                KeyFiles.write(keysDir, id, Signatures.generateRsa(2048));
            }
            Files.writeString(storage.resolve("goods.txt"),
                    "G1 alice\nG2 alice\nG3 bob\nG4 carol\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public UserDirectory directory() {
        try (Stream<Path> files = Files.list(keysDir)) {
            Map<String, PublicKey> pub = new LinkedHashMap<>();
            files.filter(p -> p.getFileName().toString().endsWith(".pub")).forEach(p -> {
                String id = p.getFileName().toString().replaceFirst("\\.pub$", "");
                try {
                    pub.put(id, KeyFiles.readPublic(p));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            return new UserDirectory(pub);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public PrivateKey privateKey(String id) {
        try {
            return KeyFiles.readPrivate(keysDir.resolve(id + ".key"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Map<String, String> owners() {
        try {
            Map<String, String> owners = new LinkedHashMap<>();
            for (String line : Files.readAllLines(storage.resolve("goods.txt"))) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    String[] parts = trimmed.split("\\s+");
                    owners.put(parts[0], parts[1]);
                }
            }
            return owners;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
