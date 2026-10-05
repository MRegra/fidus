package dev.sobatista.fidus;

import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.log.FileTransactionLog;
import dev.sobatista.fidus.log.TransactionLog;
import dev.sobatista.fidus.message.TransferCertificate;
import dev.sobatista.fidus.model.GoodState;
import dev.sobatista.fidus.notary.NotaryException;
import dev.sobatista.fidus.notary.SingleNotary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrashRecoveryTest {
    private final Fixtures fx = new Fixtures("alice", "bob", "carol");
    private static final Map<String, String> OWNERS = Map.of("G1", "alice", "G2", "alice");

    @Test
    void stateAndReplayProtectionSurviveARestart(@TempDir Path dir) throws Exception {
        Path logFile = dir.resolve("notary.log");

        // First life of the notary: alice sells G1 to bob (alice's write sequences 1 and 2).
        try (TransactionLog log = new FileTransactionLog(logFile)) {
            SingleNotary n1 = fx.notary(log, OWNERS);
            n1.intentionToSell(fx.sell("alice", "G1", 1));
            Signed<TransferCertificate> cert =
                    n1.transferGood(fx.transfer("alice", "bob", "G1", 2), fx.buy("bob", "alice", "G1", 9));
            assertEquals("bob", cert.payload().buyerId());
        }

        // Crash and restart: a brand-new notary rebuilds purely from the log.
        try (TransactionLog log = new FileTransactionLog(logFile)) {
            SingleNotary n2 = fx.notary(log, OWNERS);
            Signed<GoodState> state = n2.stateOfGood(fx.state("carol", "G1", 1));
            assertEquals("bob", state.payload().ownerId(), "ownership recovered from the log");

            // The replay guard is durable too: reusing one of alice's old sequence numbers (2) on a good she
            // still owns (G2) is refused, because recovery restored her last sequence.
            NotaryException e = assertThrows(NotaryException.class, () -> n2.intentionToSell(fx.sell("alice", "G2", 2)));
            assertEquals(NotaryException.Reason.STALE_SEQUENCE, e.reason());

            // A genuinely newer sequence still works after recovery.
            Signed<GoodState> sold = n2.intentionToSell(fx.sell("alice", "G2", 3));
            assertEquals(true, sold.payload().onSale());
        }
    }

    @Test
    void anIntentionLostToACrashIsNotHonoured(@TempDir Path dir) throws Exception {
        // Models "the notary said yes to intentionToSell, then crashed before the record was durable":
        // the fresh notary's log has no such intention, so the good is not on sale and the transfer is refused.
        try (TransactionLog log = new FileTransactionLog(dir.resolve("empty.log"))) {
            SingleNotary n = fx.notary(log, OWNERS);
            NotaryException e = assertThrows(NotaryException.class,
                    () -> n.transferGood(fx.transfer("alice", "bob", "G1", 1), fx.buy("bob", "alice", "G1", 3)));
            assertEquals(NotaryException.Reason.NOT_ON_SALE, e.reason());
        }
    }
}
