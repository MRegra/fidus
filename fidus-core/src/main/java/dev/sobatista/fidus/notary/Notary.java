package dev.sobatista.fidus.notary;

import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.message.BuyRequest;
import dev.sobatista.fidus.message.SellRequest;
import dev.sobatista.fidus.message.StateRequest;
import dev.sobatista.fidus.message.TransferCertificate;
import dev.sobatista.fidus.message.TransferRequest;
import dev.sobatista.fidus.model.GoodState;

/**
 * The notary: it certifies ownership transfers of goods between users.
 *
 * <p>Every call takes a signed request and returns a signed result, so neither side can later deny what it asked
 * for or what the notary answered. Implementations range from a single trusted {@code SingleNotary} to a replica
 * in a Byzantine-fault-tolerant cluster.
 */
public interface Notary {
    /** This notary's identity, used to look up its public key. */
    String id();

    /** Read the current owner and on-sale flag of a good (authenticated to a legitimate reader). */
    Signed<GoodState> stateOfGood(Signed<StateRequest> request) throws NotaryException;

    /** An owner puts a good on sale. Returns the good's new signed state. */
    Signed<GoodState> intentionToSell(Signed<SellRequest> request) throws NotaryException;

    /**
     * Transfer a good from seller to buyer, given both sides' signed consent. Returns the signed transfer
     * certificate — the non-repudiable receipt.
     */
    Signed<TransferCertificate> transferGood(Signed<TransferRequest> sellerRequest, Signed<BuyRequest> buyerRequest)
            throws NotaryException;
}
