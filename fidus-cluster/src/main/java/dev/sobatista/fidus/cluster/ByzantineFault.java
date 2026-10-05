package dev.sobatista.fidus.cluster;

/** How a replica (mis)behaves, for fault-injection tests. */
public enum ByzantineFault {
    /** Follows the protocol. */
    CORRECT,
    /** Says nothing and does nothing — a crashed or muted replica. */
    SILENT,
    /** As a broadcast sender, tells different replicas different things (equivocates). */
    EQUIVOCATE
}
