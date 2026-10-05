package dev.sobatista.fidus.cluster;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A deterministic, in-process message bus. Sends enqueue a delivery; {@link #runToQuiescence()} drains the queue
 * until the protocol settles. No threads and no timing — so the broadcast's correctness is tested exactly, the way
 * fidus-cluster proves the algorithm before fidus-node carries it over the real network.
 */
final class InProcessNetwork {
    private final Map<String, ByzantineNotaryReplica> replicas = new LinkedHashMap<>();
    private final Deque<Runnable> queue = new ArrayDeque<>();

    void register(ByzantineNotaryReplica r) {
        replicas.put(r.id(), r);
    }

    List<String> ids() {
        return new ArrayList<>(replicas.keySet());
    }

    void send(String to, BrbMessage m) {
        ByzantineNotaryReplica r = replicas.get(to);
        if (r != null) {
            queue.add(() -> r.receive(m));
        }
    }

    void broadcast(BrbMessage m) {
        for (String id : replicas.keySet()) {
            send(id, m);
        }
    }

    void runToQuiescence() {
        int steps = 0;
        while (!queue.isEmpty()) {
            queue.poll().run();
            if (++steps > 2_000_000) {
                throw new IllegalStateException("broadcast did not quiesce");
            }
        }
    }
}
