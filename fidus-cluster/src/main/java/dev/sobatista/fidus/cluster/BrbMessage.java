package dev.sobatista.fidus.cluster;

import java.io.Serializable;

/**
 * One wire message of the authenticated double-echo broadcast. {@code broadcastId} ties all messages of a single
 * broadcast instance together; {@code from} is the sending replica; {@code operation} is the value being agreed.
 */
public record BrbMessage(Tag tag, String broadcastId, String from, BftOperation operation) implements Serializable {
    public enum Tag { SEND, ECHO, READY }
}
