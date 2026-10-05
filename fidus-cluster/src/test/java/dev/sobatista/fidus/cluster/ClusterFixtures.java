package dev.sobatista.fidus.cluster;

import dev.sobatista.fidus.crypto.Signatures;
import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.log.InMemoryTransactionLog;
import dev.sobatista.fidus.message.BuyRequest;
import dev.sobatista.fidus.message.SellRequest;
import dev.sobatista.fidus.message.StateRequest;
import dev.sobatista.fidus.message.TransferRequest;
import dev.sobatista.fidus.model.UserDirectory;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Builds a directory of replica + user keys and wires clusters or raw replicas for the tests. */
final class ClusterFixtures {
    final UserDirectory directory;
    final List<String> replicaIds;
    private final Map<String, KeyPair> keys = new HashMap<>();

    ClusterFixtures(int n, String... users) {
        replicaIds = IntStream.range(0, n).mapToObj(i -> "N" + i).toList();
        Map<String, PublicKey> pub = new HashMap<>();
        for (String id : replicaIds) {
            KeyPair k = Signatures.generateRsa(1024);
            keys.put(id, k);
            pub.put(id, k.getPublic());
        }
        for (String u : users) {
            KeyPair k = Signatures.generateRsa(1024);
            keys.put(u, k);
            pub.put(u, k.getPublic());
        }
        directory = new UserDirectory(pub);
    }

    PrivateKey priv(String id) {
        return keys.get(id).getPrivate();
    }

    Quorums quorums() {
        return Quorums.forReplicas(replicaIds.size());
    }

    List<ByzantineNotaryReplica> replicas(Map<String, String> owners) {
        List<ByzantineNotaryReplica> rs = new ArrayList<>();
        for (String id : replicaIds) {
            rs.add(new ByzantineNotaryReplica(id, priv(id), directory, owners, new InMemoryTransactionLog(), quorums()));
        }
        return rs;
    }

    ByzantineNotaryCluster cluster(Map<String, String> owners) {
        return new ByzantineNotaryCluster(quorums(), directory, replicas(owners));
    }

    Signed<SellRequest> sell(String owner, String good, long seq) {
        return Signed.sign(new SellRequest(owner, good, seq), owner, priv(owner));
    }

    Signed<TransferRequest> transfer(String seller, String buyer, String good, long seq) {
        return Signed.sign(new TransferRequest(seller, buyer, good, seq), seller, priv(seller));
    }

    Signed<BuyRequest> buy(String buyer, String seller, String good, long nonce) {
        return Signed.sign(new BuyRequest(buyer, seller, good, nonce), buyer, priv(buyer));
    }

    Signed<StateRequest> state(String reader, String good, long nonce) {
        return Signed.sign(new StateRequest(reader, good, nonce), reader, priv(reader));
    }

    BftOperation.Sell sellOp(String owner, String good, long seq) {
        return new BftOperation.Sell(sell(owner, good, seq));
    }
}
