package dev.sobatista.fidus;

import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.log.InMemoryTransactionLog;
import dev.sobatista.fidus.message.SellRequest;
import dev.sobatista.fidus.message.TransferCertificate;
import dev.sobatista.fidus.model.GoodState;
import dev.sobatista.fidus.notary.NotaryException;
import dev.sobatista.fidus.notary.SingleNotary;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SingleNotaryTest {
    private final Fixtures fx = new Fixtures("alice", "bob", "carol", "mallory");

    private SingleNotary freshNotary() {
        return fx.notary(new InMemoryTransactionLog(), Map.of("G1", "alice", "G2", "alice"));
    }

    @Test
    void happyPathTransferProducesVerifiableCertificate() throws Exception {
        SingleNotary n = freshNotary();
        Signed<GoodState> afterSell = n.intentionToSell(fx.sell("alice", "G1", 1));
        assertTrue(afterSell.payload().onSale());
        assertTrue(afterSell.verify(fx.pub(Fixtures.NOTARY)), "notary signs the state it returns");

        Signed<TransferCertificate> cert =
                n.transferGood(fx.transfer("alice", "bob", "G1", 2), fx.buy("bob", "alice", "G1", 77));
        assertTrue(cert.verify(fx.pub(Fixtures.NOTARY)), "certificate is the notary's non-repudiable receipt");
        assertEquals("bob", cert.payload().buyerId());

        Signed<GoodState> now = n.stateOfGood(fx.state("carol", "G1", 1));
        assertEquals("bob", now.payload().ownerId());
        assertFalse(now.payload().onSale());
    }

    @Test
    void replayOfAStateChangeIsRefused() throws Exception {
        SingleNotary n = freshNotary();
        n.intentionToSell(fx.sell("alice", "G1", 1));
        NotaryException e = assertThrows(NotaryException.class, () -> n.intentionToSell(fx.sell("alice", "G1", 1)));
        assertEquals(NotaryException.Reason.STALE_SEQUENCE, e.reason());
    }

    @Test
    void impersonationByWrongKeyIsRefused() {
        SingleNotary n = freshNotary();
        // Mallory forges a request that claims to be from alice, but signs it with mallory's key.
        Signed<SellRequest> forged = new Signed<>(new SellRequest("alice", "G1", 1), "alice",
                Signed.sign(new SellRequest("alice", "G1", 1), "mallory", fx.priv("mallory")).signature());
        NotaryException e = assertThrows(NotaryException.class, () -> n.intentionToSell(forged));
        assertEquals(NotaryException.Reason.BAD_SIGNATURE, e.reason());
    }

    @Test
    void impersonationByClaimingAnotherOwnerIsRefused() {
        SingleNotary n = freshNotary();
        // Mallory signs with her own id but claims alice is the owner in the payload.
        Signed<SellRequest> r = Signed.sign(new SellRequest("alice", "G1", 1), "mallory", fx.priv("mallory"));
        NotaryException e = assertThrows(NotaryException.class, () -> n.intentionToSell(r));
        assertEquals(NotaryException.Reason.UNKNOWN_USER, e.reason());
    }

    @Test
    void messageManipulationIsRefused() throws Exception {
        SingleNotary n = freshNotary();
        // A man in the middle flips the good id but keeps alice's original signature.
        Signed<SellRequest> original = fx.sell("alice", "G1", 1);
        Signed<SellRequest> tampered = new Signed<>(new SellRequest("alice", "G2", 1),
                original.signerId(), original.signature());
        NotaryException e = assertThrows(NotaryException.class, () -> n.intentionToSell(tampered));
        assertEquals(NotaryException.Reason.BAD_SIGNATURE, e.reason());
    }

    @Test
    void duplicateReadNonceIsRefused() throws Exception {
        SingleNotary n = freshNotary();
        n.stateOfGood(fx.state("bob", "G1", 42));
        NotaryException e = assertThrows(NotaryException.class, () -> n.stateOfGood(fx.state("bob", "G1", 42)));
        assertEquals(NotaryException.Reason.DUPLICATE_READ, e.reason());
    }

    @Test
    void sellingAGoodYouDoNotOwnIsRefused() {
        SingleNotary n = freshNotary();
        NotaryException e = assertThrows(NotaryException.class, () -> n.intentionToSell(fx.sell("bob", "G1", 1)));
        assertEquals(NotaryException.Reason.NOT_OWNER, e.reason());
    }

    @Test
    void transferBeforeIntentionIsRefused() {
        SingleNotary n = freshNotary();
        NotaryException e = assertThrows(NotaryException.class,
                () -> n.transferGood(fx.transfer("alice", "bob", "G1", 1), fx.buy("bob", "alice", "G1", 5)));
        assertEquals(NotaryException.Reason.NOT_ON_SALE, e.reason());
    }

    @Test
    void transferWithoutBuyerConsentIsRefused() throws Exception {
        SingleNotary n = freshNotary();
        n.intentionToSell(fx.sell("alice", "G1", 1));
        // Buyer's signed request is for a different good: it does not consent to THIS transfer.
        NotaryException e = assertThrows(NotaryException.class,
                () -> n.transferGood(fx.transfer("alice", "bob", "G1", 2), fx.buy("bob", "alice", "G2", 5)));
        assertEquals(NotaryException.Reason.BUYER_DID_NOT_CONSENT, e.reason());
    }
}
