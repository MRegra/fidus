package dev.sobatista.fidus.node;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProofOfWorkTest {
    private final byte[] request = "intentionToSell:alice:G1:1".getBytes(StandardCharsets.UTF_8);

    @Test
    void mintedProofValidates() {
        ProofOfWork.Stamp stamp = ProofOfWork.mint(request, 12);
        assertTrue(ProofOfWork.validate(request, stamp, 12));
    }

    @Test
    void proofIsBoundToTheRequest() {
        ProofOfWork.Stamp stamp = ProofOfWork.mint(request, 12);
        byte[] other = "intentionToSell:alice:G2:1".getBytes(StandardCharsets.UTF_8);
        assertFalse(ProofOfWork.validate(other, stamp, 12), "a stamp cannot be moved to another request");
    }

    @Test
    void underDifficultyIsRejected() {
        ProofOfWork.Stamp weak = ProofOfWork.mint(request, 4);
        assertFalse(ProofOfWork.validate(request, weak, 12), "a stamp below the required difficulty is refused");
    }

    @Test
    void nullProofIsRejected() {
        assertFalse(ProofOfWork.validate(request, null, 12));
    }
}
