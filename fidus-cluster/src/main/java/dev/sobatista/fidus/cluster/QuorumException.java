package dev.sobatista.fidus.cluster;

/** Thrown when the client cannot assemble a Byzantine quorum behind a single answer. */
public class QuorumException extends Exception {
    public QuorumException(String message) {
        super(message);
    }
}
