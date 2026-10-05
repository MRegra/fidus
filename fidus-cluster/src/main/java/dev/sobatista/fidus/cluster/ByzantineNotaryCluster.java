package dev.sobatista.fidus.cluster;

import dev.sobatista.fidus.crypto.Signable;
import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.message.BuyRequest;
import dev.sobatista.fidus.message.SellRequest;
import dev.sobatista.fidus.message.StateRequest;
import dev.sobatista.fidus.message.TransferCertificate;
import dev.sobatista.fidus.message.TransferRequest;
import dev.sobatista.fidus.model.GoodState;
import dev.sobatista.fidus.model.UserDirectory;
import dev.sobatista.fidus.notary.NotaryException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's view of the notary cluster. A write is disseminated through Byzantine reliable broadcast and
 * accepted only once a quorum of {@code 2f+1} replicas return the same signed result; a read gathers signed register
 * values, takes the one a read quorum agrees on (highest version), and writes it back — the (1,N) Byzantine atomic
 * register, write-back phase included.
 *
 * <p>Because the client needs a quorum of matching, individually-verified signatures, up to {@code f} Byzantine
 * replicas can lie, stay silent or equivocate without changing the answer.
 */
public final class ByzantineNotaryCluster {
    private final Map<String, ByzantineNotaryReplica> replicas = new LinkedHashMap<>();
    private final InProcessNetwork net = new InProcessNetwork();
    private final Quorums quorums;
    private final UserDirectory directory;
    private int counter = 0;

    public ByzantineNotaryCluster(Quorums quorums, UserDirectory directory, List<ByzantineNotaryReplica> replicas) {
        this.quorums = quorums;
        this.directory = directory;
        for (ByzantineNotaryReplica r : replicas) {
            this.replicas.put(r.id(), r);
            net.register(r);
            r.attach(net);
        }
    }

    public Quorums quorums() {
        return quorums;
    }

    public Collection<ByzantineNotaryReplica> replicas() {
        return replicas.values();
    }

    public Signed<GoodState> intentionToSell(Signed<SellRequest> request, String viaReplica) throws QuorumException {
        return write(new BftOperation.Sell(request), viaReplica);
    }

    public Signed<TransferCertificate> transferGood(Signed<TransferRequest> sellerRequest,
                                                    Signed<BuyRequest> buyerRequest,
                                                    String viaReplica) throws QuorumException {
        return write(new BftOperation.Transfer(sellerRequest, buyerRequest), viaReplica);
    }

    @SuppressWarnings("unchecked")
    private <T extends Signable> Signed<T> write(BftOperation op, String viaReplica) throws QuorumException {
        String broadcastId = viaReplica + ":" + (counter++);
        replicas.get(viaReplica).startBroadcast(broadcastId, op);
        net.runToQuiescence();

        Map<Signable, List<Signed<?>>> byPayload = new HashMap<>();
        Map<NotaryException.Reason, Integer> byReason = new HashMap<>();
        for (ByzantineNotaryReplica r : replicas.values()) {
            r.result(broadcastId).ifPresent(sig -> {
                boolean ok = directory.lookup(sig.signerId()).map(sig::verify).orElse(false);
                if (ok) {
                    byPayload.computeIfAbsent(sig.payload(), k -> new ArrayList<>()).add(sig);
                }
            });
            r.failure(broadcastId).ifPresent(reason -> byReason.merge(reason, 1, Integer::sum));
        }
        for (Map.Entry<Signable, List<Signed<?>>> e : byPayload.entrySet()) {
            if (distinctSigners(e.getValue()) >= quorums.deliver()) {
                return (Signed<T>) e.getValue().get(0);
            }
        }
        for (Map.Entry<NotaryException.Reason, Integer> e : byReason.entrySet()) {
            if (e.getValue() >= quorums.deliver()) {
                throw new QuorumException("notary refused by quorum: " + e.getKey());
            }
        }
        throw new QuorumException("no quorum of matching results for " + broadcastId);
    }

    public GoodState stateOfGood(Signed<StateRequest> request) throws QuorumException {
        String goodId = request.payload().goodId();
        List<Signed<GoodState>> responses = new ArrayList<>();
        for (ByzantineNotaryReplica r : replicas.values()) {
            try {
                Signed<GoodState> s = r.readState(request);
                boolean ok = directory.lookup(s.signerId()).map(s::verify).orElse(false)
                        && s.payload().goodId().equals(goodId);
                if (ok) {
                    responses.add(s);
                }
            } catch (NotaryException ignored) {
                // a replica may legitimately refuse (e.g. duplicate nonce); it just doesn't count
            }
        }

        Map<GoodState, List<Signed<GoodState>>> byValue = new HashMap<>();
        for (Signed<GoodState> s : responses) {
            byValue.computeIfAbsent(s.payload(), k -> new ArrayList<>()).add(s);
        }

        GoodState chosen = null;
        List<Signed<GoodState>> certificate = null;
        for (Map.Entry<GoodState, List<Signed<GoodState>>> e : byValue.entrySet()) {
            long signers = e.getValue().stream().map(Signed::signerId).distinct().count();
            if (signers >= quorums.read() && (chosen == null || e.getKey().timestamp() > chosen.timestamp())) {
                chosen = e.getKey();
                certificate = e.getValue();
            }
        }
        if (chosen == null) {
            throw new QuorumException("no read quorum for good " + goodId);
        }
        for (ByzantineNotaryReplica r : replicas.values()) {
            r.writeBack(chosen, certificate);
        }
        return chosen;
    }

    private static long distinctSigners(List<Signed<?>> signed) {
        return signed.stream().map(Signed::signerId).distinct().count();
    }
}
