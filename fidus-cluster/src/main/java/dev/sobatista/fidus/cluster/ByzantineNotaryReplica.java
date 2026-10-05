package dev.sobatista.fidus.cluster;

import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.log.TransactionLog;
import dev.sobatista.fidus.message.StateRequest;
import dev.sobatista.fidus.message.TransferCertificate;
import dev.sobatista.fidus.model.GoodState;
import dev.sobatista.fidus.model.UserDirectory;
import dev.sobatista.fidus.notary.NotaryException;
import dev.sobatista.fidus.notary.SingleNotary;

import java.security.PrivateKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One notary replica: a local {@link SingleNotary} (its own state and signing key) wrapped in Byzantine reliable
 * broadcast. State-changing operations are agreed by broadcast before they are applied, so every correct replica
 * applies the same operations; reads are served as signed register values and backed up by write-back.
 */
public final class ByzantineNotaryReplica {
    private final String id;
    private final Quorums quorums;
    private final UserDirectory directory;
    private final SingleNotary notary;

    private InProcessNetwork net;
    private ByzantineFault fault = ByzantineFault.CORRECT;
    private BftOperation equivocationAlternative;

    private final Map<String, ByzantineReliableBroadcast> instances = new HashMap<>();
    private final Map<String, Signed<?>> results = new HashMap<>();
    private final Map<String, NotaryException.Reason> failures = new HashMap<>();

    public ByzantineNotaryReplica(String id, PrivateKey key, UserDirectory directory,
                                  Map<String, String> initialOwners, TransactionLog log, Quorums quorums) {
        this.id = id;
        this.quorums = quorums;
        this.directory = directory;
        this.notary = new SingleNotary(id, key, directory, initialOwners, log);
    }

    public String id() {
        return id;
    }

    public ByzantineFault fault() {
        return fault;
    }

    public void makeByzantine(ByzantineFault f) {
        this.fault = f;
    }

    /** When equivocating as sender, the alternative operation shown to the "other half" of the replicas. */
    public void equivocateWith(BftOperation alternative) {
        this.equivocationAlternative = alternative;
    }

    void attach(InProcessNetwork net) {
        this.net = net;
    }

    // ---- write path: Byzantine reliable broadcast ----

    void startBroadcast(String broadcastId, BftOperation op) {
        if (fault == ByzantineFault.SILENT) {
            return;
        }
        if (fault == ByzantineFault.EQUIVOCATE) {
            equivocate(broadcastId, op);
            return;
        }
        for (BrbMessage m : instance(broadcastId).broadcast(op)) {
            net.broadcast(m);
        }
    }

    void receive(BrbMessage m) {
        if (fault == ByzantineFault.SILENT) {
            return;
        }
        for (BrbMessage out : instance(m.broadcastId()).onReceive(m)) {
            net.broadcast(out);
        }
    }

    private void equivocate(String broadcastId, BftOperation op) {
        List<String> ids = net.ids();
        int half = (ids.size() + 1) / 2;
        BftOperation alt = equivocationAlternative != null ? equivocationAlternative : op;
        for (int i = 0; i < ids.size(); i++) {
            BftOperation shown = i < half ? op : alt;
            net.send(ids.get(i), new BrbMessage(BrbMessage.Tag.SEND, broadcastId, id, shown));
        }
    }

    private ByzantineReliableBroadcast instance(String broadcastId) {
        return instances.computeIfAbsent(broadcastId, b -> {
            String sender = b.substring(0, b.indexOf(':'));
            return new ByzantineReliableBroadcast(id, sender, b, quorums, op -> apply(b, op));
        });
    }

    private void apply(String broadcastId, BftOperation op) {
        try {
            Signed<?> result = switch (op) {
                case BftOperation.Sell s -> notary.intentionToSell(s.request());
                case BftOperation.Transfer t -> notary.transferGood(t.sellerRequest(), t.buyerRequest());
            };
            results.put(broadcastId, result);
        } catch (NotaryException e) {
            failures.put(broadcastId, e.reason());
        }
    }

    boolean delivered(String broadcastId) {
        ByzantineReliableBroadcast brb = instances.get(broadcastId);
        return brb != null && brb.delivered();
    }

    Optional<Signed<?>> result(String broadcastId) {
        return Optional.ofNullable(results.get(broadcastId));
    }

    Optional<NotaryException.Reason> failure(String broadcastId) {
        return Optional.ofNullable(failures.get(broadcastId));
    }

    // ---- read path: Byzantine atomic register ----

    Signed<GoodState> readState(Signed<StateRequest> req) throws NotaryException {
        return notary.stateOfGood(req);
    }

    /** Write-back: adopt {@code value} only if a valid read quorum of distinct replicas vouches for it. */
    boolean writeBack(GoodState value, List<Signed<GoodState>> certificate) {
        long valid = certificate.stream()
                .filter(s -> value.equals(s.payload()))
                .filter(s -> directory.lookup(s.signerId()).map(s::verify).orElse(false))
                .map(Signed::signerId)
                .distinct()
                .count();
        if (valid < quorums.read()) {
            return false; // unvouched write-back is refused
        }
        return notary.adoptIfNewer(value);
    }
}
