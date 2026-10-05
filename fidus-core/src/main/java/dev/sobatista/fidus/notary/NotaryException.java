package dev.sobatista.fidus.notary;

/** A request the notary refuses, with a precise reason. Refusing is a normal, signed outcome — not a crash. */
public class NotaryException extends Exception {
    public enum Reason {
        UNKNOWN_USER,
        UNKNOWN_GOOD,
        BAD_SIGNATURE,
        STALE_SEQUENCE,
        DUPLICATE_READ,
        NOT_OWNER,
        NOT_ON_SALE,
        BUYER_DID_NOT_CONSENT,
        INSUFFICIENT_PROOF_OF_WORK
    }

    private final Reason reason;

    public NotaryException(Reason reason, String detail) {
        super(reason + ": " + detail);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
