package dev.sobatista.fidus.cluster;

import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.message.TransferCertificate;
import dev.sobatista.fidus.model.GoodState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByzantineNotaryClusterTest {
    private final ClusterFixtures fx = new ClusterFixtures(4, "alice", "bob", "carol");
    private static final Map<String, String> OWNERS = Map.of("G1", "alice", "G2", "alice");

    @Test
    void transferSucceedsDespiteOneByzantineReplica() throws Exception {
        ByzantineNotaryCluster c = fx.cluster(OWNERS);
        List<ByzantineNotaryReplica> rs = new ArrayList<>(c.replicas());
        rs.get(3).makeByzantine(ByzantineFault.SILENT);

        c.intentionToSell(fx.sell("alice", "G1", 1), "N0");
        Signed<TransferCertificate> cert =
                c.transferGood(fx.transfer("alice", "bob", "G1", 2), fx.buy("bob", "alice", "G1", 9), "N0");
        assertEquals("bob", cert.payload().buyerId());

        GoodState state = c.stateOfGood(fx.state("carol", "G1", 1));
        assertEquals("bob", state.ownerId());
        assertEquals(false, state.onSale());
    }

    @Test
    void readReachesQuorumAndWritesBackToALaggingReplica() throws Exception {
        ByzantineNotaryCluster c = fx.cluster(OWNERS);
        List<ByzantineNotaryReplica> rs = new ArrayList<>(c.replicas());

        // N3 is silent through the write, so it never sees the transfer.
        rs.get(3).makeByzantine(ByzantineFault.SILENT);
        c.intentionToSell(fx.sell("alice", "G1", 1), "N0");
        c.transferGood(fx.transfer("alice", "bob", "G1", 2), fx.buy("bob", "alice", "G1", 9), "N0");

        // It recovers (now correct) but is still behind: it thinks alice owns G1.
        rs.get(3).makeByzantine(ByzantineFault.CORRECT);
        assertEquals("alice", rs.get(3).readState(fx.state("carol", "G1", 100)).payload().ownerId());

        // A client read reaches a quorum on the true value (bob) and writes it back.
        GoodState value = c.stateOfGood(fx.state("carol", "G1", 200));
        assertEquals("bob", value.ownerId());

        // The write-back brought the lagging replica up to date.
        assertEquals("bob", rs.get(3).readState(fx.state("carol", "G1", 300)).payload().ownerId());
    }

    @Test
    void writeFailsWithoutAQuorumOfCorrectReplicas() {
        ByzantineNotaryCluster c = fx.cluster(OWNERS);
        List<ByzantineNotaryReplica> rs = new ArrayList<>(c.replicas());
        // Two faulty replicas exceed what n=4, f=1 tolerates: no quorum can form.
        rs.get(2).makeByzantine(ByzantineFault.SILENT);
        rs.get(3).makeByzantine(ByzantineFault.SILENT);

        assertThrows(QuorumException.class, () -> c.intentionToSell(fx.sell("alice", "G1", 1), "N0"));
    }
}
