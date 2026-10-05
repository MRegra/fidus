package dev.sobatista.fidus.node;

import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.log.InMemoryTransactionLog;
import dev.sobatista.fidus.message.SellRequest;
import dev.sobatista.fidus.message.StateRequest;
import dev.sobatista.fidus.notary.NotaryException;
import dev.sobatista.fidus.notary.SingleNotary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuardedNotaryTest {
    private static final int DIFFICULTY = 12;
    private final Identities ids = new Identities(List.of("NOTARY", "alice", "bob"), 1024);

    private GuardedNotary guarded() {
        SingleNotary notary = new SingleNotary("NOTARY", ids.priv("NOTARY"), ids.directory(),
                Map.of("G1", "alice"), new InMemoryTransactionLog());
        return new GuardedNotary(notary, DIFFICULTY);
    }

    private Signed<SellRequest> sell() {
        return Signed.sign(new SellRequest("alice", "G1", 1), "alice", ids.priv("alice"));
    }

    @Test
    void writeWithoutProofOfWorkIsRefused() {
        NotaryException e = assertThrows(NotaryException.class, () -> guarded().intentionToSell(sell(), null));
        assertEquals(NotaryException.Reason.INSUFFICIENT_PROOF_OF_WORK, e.reason());
    }

    @Test
    void writeWithUnderpoweredProofIsRefused() {
        Signed<SellRequest> req = sell();
        ProofOfWork.Stamp weak = ProofOfWork.mint(req.payload().canonicalBytes(), 2);
        NotaryException e = assertThrows(NotaryException.class, () -> guarded().intentionToSell(req, weak));
        assertEquals(NotaryException.Reason.INSUFFICIENT_PROOF_OF_WORK, e.reason());
    }

    @Test
    void writeWithValidProofSucceeds() {
        Signed<SellRequest> req = sell();
        ProofOfWork.Stamp pow = ProofOfWork.mint(req.payload().canonicalBytes(), DIFFICULTY);
        assertDoesNotThrow(() -> guarded().intentionToSell(req, pow));
    }

    @Test
    void readsNeedNoProofOfWork() {
        assertDoesNotThrow(() -> guarded().stateOfGood(
                Signed.sign(new StateRequest("bob", "G1", 5), "bob", ids.priv("bob"))));
    }
}
