package dev.sobatista.fidus.cluster;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives the broadcast directly over the in-process network to check its Byzantine guarantees. */
class ByzantineReliableBroadcastTest {
    private final ClusterFixtures fx = new ClusterFixtures(4, "alice");
    private static final Map<String, String> OWNERS = Map.of("G1", "alice", "G2", "alice");

    private InProcessNetwork wire(List<ByzantineNotaryReplica> rs) {
        InProcessNetwork net = new InProcessNetwork();
        for (ByzantineNotaryReplica r : rs) {
            net.register(r);
            r.attach(net);
        }
        return net;
    }

    @Test
    void correctSenderIsDeliveredByEveryCorrectReplica() {
        List<ByzantineNotaryReplica> rs = fx.replicas(OWNERS);
        InProcessNetwork net = wire(rs);

        rs.get(0).startBroadcast("N0:0", fx.sellOp("alice", "G1", 1));
        net.runToQuiescence();

        for (ByzantineNotaryReplica r : rs) {
            assertTrue(r.delivered("N0:0"), r.id() + " should have delivered");
        }
    }

    @Test
    void deliversDespiteOneSilentReplica() {
        List<ByzantineNotaryReplica> rs = fx.replicas(OWNERS);
        InProcessNetwork net = wire(rs);
        rs.get(3).makeByzantine(ByzantineFault.SILENT);

        rs.get(0).startBroadcast("N0:0", fx.sellOp("alice", "G1", 1));
        net.runToQuiescence();

        long delivered = rs.stream().filter(r -> r.delivered("N0:0")).count();
        assertTrue(delivered >= fx.quorums().deliver(), "a quorum of correct replicas still delivers");
    }

    @Test
    void equivocatingSenderNeverSplitsCorrectReplicas() {
        List<ByzantineNotaryReplica> rs = fx.replicas(OWNERS);
        InProcessNetwork net = wire(rs);
        // N0 is Byzantine: it shows some replicas "sell G1", others "sell G2".
        rs.get(0).makeByzantine(ByzantineFault.EQUIVOCATE);
        rs.get(0).equivocateWith(fx.sellOp("alice", "G2", 1));

        rs.get(0).startBroadcast("N0:0", fx.sellOp("alice", "G1", 1));
        net.runToQuiescence();

        // Inspect what the correct replicas (N1..N3) delivered, via their applied state.
        Set<String> deliveredGoods = rs.stream()
                .skip(1)
                .filter(r -> r.delivered("N0:0"))
                .map(r -> r.result("N0:0").orElseThrow().payload().toString())
                .collect(Collectors.toSet());
        assertTrue(deliveredGoods.size() <= 1, "no two correct replicas may deliver different values");
    }
}
