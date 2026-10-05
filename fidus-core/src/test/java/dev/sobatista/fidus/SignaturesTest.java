package dev.sobatista.fidus;

import dev.sobatista.fidus.crypto.Signatures;
import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.message.SellRequest;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignaturesTest {
    private final KeyPair a = Signatures.generateRsa(1024);
    private final KeyPair b = Signatures.generateRsa(1024);

    @Test
    void validSignatureVerifies() {
        Signed<SellRequest> s = Signed.sign(new SellRequest("alice", "G1", 1), "alice", a.getPrivate());
        assertTrue(s.verify(a.getPublic()));
    }

    @Test
    void tamperedPayloadFails() {
        Signed<SellRequest> s = Signed.sign(new SellRequest("alice", "G1", 1), "alice", a.getPrivate());
        Signed<SellRequest> tampered = new Signed<>(new SellRequest("alice", "G1", 999), s.signerId(), s.signature());
        assertFalse(tampered.verify(a.getPublic()));
    }

    @Test
    void wrongKeyFails() {
        Signed<SellRequest> s = Signed.sign(new SellRequest("alice", "G1", 1), "alice", a.getPrivate());
        assertFalse(s.verify(b.getPublic()));
    }

    @Test
    void nullSignatureNeverThrows() {
        assertFalse(Signatures.verify(a.getPublic(), new byte[] {1, 2, 3}, null));
    }
}
