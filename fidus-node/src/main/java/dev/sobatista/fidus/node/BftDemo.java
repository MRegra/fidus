package dev.sobatista.fidus.node;

import dev.sobatista.fidus.cluster.ByzantineFault;
import dev.sobatista.fidus.cluster.ByzantineNotaryCluster;
import dev.sobatista.fidus.cluster.ByzantineNotaryReplica;
import dev.sobatista.fidus.cluster.QuorumException;
import dev.sobatista.fidus.cluster.Quorums;
import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.log.InMemoryTransactionLog;
import dev.sobatista.fidus.message.BuyRequest;
import dev.sobatista.fidus.message.SellRequest;
import dev.sobatista.fidus.message.StateRequest;
import dev.sobatista.fidus.message.TransferCertificate;
import dev.sobatista.fidus.message.TransferRequest;
import dev.sobatista.fidus.model.GoodState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An in-process showcase: four notary replicas agree on an ownership transfer while one of them is Byzantine
 * (silent), proving the cluster works without a quorum member. This is the 30-second demo from the README; it needs
 * no network. (Running the replicas as real processes over RMI is on the roadmap.)
 */
public final class BftDemo {
    private static final List<String> REPLICAS = List.of("N0", "N1", "N2", "N3");

    public static void main(String[] args) {
        System.out.print(run());
    }

    /** The final owner of G1 after the demo (used by tests); prints its narration via {@link #main}. */
    public static String run() {
        List<String> all = new ArrayList<>(REPLICAS);
        all.addAll(List.of("alice", "bob", "carol"));
        Identities ids = new Identities(all, 1024);
        Map<String, String> owners = Map.of("G1", "alice", "G2", "bob");

        Quorums q = Quorums.forReplicas(REPLICAS.size());
        List<ByzantineNotaryReplica> replicas = new ArrayList<>();
        for (String r : REPLICAS) {
            replicas.add(new ByzantineNotaryReplica(r, ids.priv(r), ids.directory(), owners,
                    new InMemoryTransactionLog(), q));
        }
        ByzantineNotaryCluster cluster = new ByzantineNotaryCluster(q, ids.directory(), replicas);
        new ArrayList<>(cluster.replicas()).get(3).makeByzantine(ByzantineFault.SILENT);

        StringBuilder out = new StringBuilder();
        out.append("fidus — a Byzantine-fault-tolerant notary\n");
        out.append("4 replicas (N0..N3), tolerating f=1 fault. N3 is Byzantine: it stays silent.\n\n");
        try {
            Signed<SellRequest> sell = Signed.sign(new SellRequest("alice", "G1", 1), "alice", ids.priv("alice"));
            cluster.intentionToSell(sell, "N0");
            out.append("alice -> intentionToSell(G1): a quorum agrees, G1 is on sale.\n");

            Signed<TransferRequest> transfer =
                    Signed.sign(new TransferRequest("alice", "bob", "G1", 2), "alice", ids.priv("alice"));
            Signed<BuyRequest> buy = Signed.sign(new BuyRequest("bob", "alice", "G1", 42), "bob", ids.priv("bob"));
            Signed<TransferCertificate> cert = cluster.transferGood(transfer, buy, "N0");
            out.append("bob -> buyGood(G1): transfer agreed; certificate signed by ").append(cert.signerId())
                    .append(".\n");

            Signed<StateRequest> read = Signed.sign(new StateRequest("carol", "G1", 7), "carol", ids.priv("carol"));
            GoodState state = cluster.stateOfGood(read);
            out.append("carol -> getStateOfGood(G1): read quorum says owner=").append(state.ownerId())
                    .append(", onSale=").append(state.onSale()).append(".\n");

            out.append("\nN3 never participated; the other three agreed anyway. ✓\n");
            return state.ownerId();
        } catch (QuorumException e) {
            throw new IllegalStateException("demo failed: " + e.getMessage(), e);
        } finally {
            System.out.print(out);
        }
    }
}
