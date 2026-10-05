package dev.sobatista.fidus.node;

import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.message.BuyRequest;
import dev.sobatista.fidus.message.SellRequest;
import dev.sobatista.fidus.message.StateRequest;
import dev.sobatista.fidus.message.TransferCertificate;
import dev.sobatista.fidus.message.TransferRequest;
import dev.sobatista.fidus.model.GoodState;
import dev.sobatista.fidus.notary.Notary;
import dev.sobatista.fidus.notary.NotaryException;

/**
 * Wraps a notary with a Proof-of-Work gate on state-changing requests, so a flood of writes costs the sender real
 * CPU. Reads are not gated. The gate is enforced <em>here, on the server side</em> — a client that skips the work,
 * reuses a stamp for a different request, or under-pays the difficulty is refused.
 */
public final class GuardedNotary {
    private final Notary notary;
    private final int minDifficulty;

    public GuardedNotary(Notary notary, int minDifficulty) {
        this.notary = notary;
        this.minDifficulty = minDifficulty;
    }

    public String id() {
        return notary.id();
    }

    public Signed<GoodState> stateOfGood(Signed<StateRequest> request) throws NotaryException {
        return notary.stateOfGood(request);
    }

    public Signed<GoodState> intentionToSell(Signed<SellRequest> request, ProofOfWork.Stamp pow)
            throws NotaryException {
        requireWork(request.payload().canonicalBytes(), pow);
        return notary.intentionToSell(request);
    }

    public Signed<TransferCertificate> transferGood(Signed<TransferRequest> sellerRequest,
                                                    Signed<BuyRequest> buyerRequest,
                                                    ProofOfWork.Stamp pow) throws NotaryException {
        requireWork(sellerRequest.payload().canonicalBytes(), pow);
        return notary.transferGood(sellerRequest, buyerRequest);
    }

    private void requireWork(byte[] request, ProofOfWork.Stamp pow) throws NotaryException {
        if (!ProofOfWork.validate(request, pow, minDifficulty)) {
            throw new NotaryException(NotaryException.Reason.INSUFFICIENT_PROOF_OF_WORK,
                    "a valid proof of work (difficulty >= " + minDifficulty + ") is required");
        }
    }
}
