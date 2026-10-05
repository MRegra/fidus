package dev.sobatista.fidus.notary;

import dev.sobatista.fidus.crypto.Signable;
import dev.sobatista.fidus.crypto.Signed;
import dev.sobatista.fidus.log.LogEntry;
import dev.sobatista.fidus.log.TransactionLog;
import dev.sobatista.fidus.message.BuyRequest;
import dev.sobatista.fidus.message.SellRequest;
import dev.sobatista.fidus.message.StateRequest;
import dev.sobatista.fidus.message.TransferCertificate;
import dev.sobatista.fidus.message.TransferRequest;
import dev.sobatista.fidus.model.GoodState;
import dev.sobatista.fidus.model.UserDirectory;

import java.security.PrivateKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A single, trusted notary with durable, crash-recoverable state.
 *
 * <p>Correctness rests on a handful of checks applied to every request:
 * <ul>
 *   <li><b>authentication / integrity</b> — the request must be signed by exactly the user it claims to be from;
 *   <li><b>replay / duplication</b> — each state-changing request carries the user's monotonic sequence number,
 *       and anything not strictly newer than the last is refused;
 *   <li><b>authorization</b> — a good can only be sold by its current owner, and only while it is on sale;
 *   <li><b>dual consent</b> — a transfer needs both the seller's and the buyer's signatures.
 * </ul>
 *
 * <p>Durability rule: a decision is written to the {@link TransactionLog} <em>before</em> it is confirmed, and
 * state is only ever rebuilt by replaying that log. So if the notary crashes after saying "yes" but before the
 * record is durable, the "yes" never happened — a later {@code transferGood} finds the good not on sale and
 * forces the seller to re-express intention, exactly as the assignment requires.
 */
public final class SingleNotary implements Notary {
    private final String id;
    private final PrivateKey privateKey;
    private final UserDirectory directory;
    private final TransactionLog log;

    private final Map<String, String> owners = new HashMap<>();
    private final Set<String> onSale = new HashSet<>();
    private final Map<String, Long> lastWriteTs = new HashMap<>();
    private final Map<String, Long> lastSequence = new HashMap<>();
    private final Map<String, Set<Long>> seenReadNonces = new HashMap<>();

    public SingleNotary(String id, PrivateKey privateKey, UserDirectory directory,
                        Map<String, String> initialOwners, TransactionLog log) {
        this.id = id;
        this.privateKey = privateKey;
        this.directory = directory;
        this.log = log;
        this.owners.putAll(initialOwners);
        recover();
    }

    /** Rebuild all mutable state by replaying the log over the initial ownership. */
    private void recover() {
        for (LogEntry entry : log.replay()) {
            if (entry instanceof LogEntry.IntentionLogged i) {
                onSale.add(i.goodId());
                lastWriteTs.put(i.goodId(), i.timestamp());
            } else if (entry instanceof LogEntry.TransferLogged t) {
                TransferCertificate c = t.certificate();
                owners.put(c.goodId(), c.buyerId());
                onSale.remove(c.goodId());
                lastWriteTs.put(c.goodId(), c.timestamp());
            }
            lastSequence.merge(entry.user(), entry.sequence(), Math::max);
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public synchronized Signed<GoodState> stateOfGood(Signed<StateRequest> request) throws NotaryException {
        StateRequest r = request.payload();
        authenticate(request, r.readerId(), r);
        if (!seenReadNonces.computeIfAbsent(r.readerId(), k -> new HashSet<>()).add(r.nonce())) {
            throw new NotaryException(NotaryException.Reason.DUPLICATE_READ, "nonce reused by " + r.readerId());
        }
        requireKnownGood(r.goodId());
        return sign(stateOf(r.goodId()));
    }

    @Override
    public synchronized Signed<GoodState> intentionToSell(Signed<SellRequest> request) throws NotaryException {
        SellRequest r = request.payload();
        authenticate(request, r.ownerId(), r);
        requireKnownGood(r.goodId());
        requireOwner(r.goodId(), r.ownerId());
        requireFreshSequence(r.ownerId(), r.sequence());

        long ts = lastWriteTs.getOrDefault(r.goodId(), 0L) + 1; // per-good version: deterministic across replicas
        log.append(new LogEntry.IntentionLogged(r.ownerId(), r.goodId(), r.sequence(), ts)); // durable first
        onSale.add(r.goodId());
        lastWriteTs.put(r.goodId(), ts);
        lastSequence.put(r.ownerId(), r.sequence());
        return sign(stateOf(r.goodId()));
    }

    @Override
    public synchronized Signed<TransferCertificate> transferGood(Signed<TransferRequest> sellerRequest,
                                                                 Signed<BuyRequest> buyerRequest)
            throws NotaryException {
        TransferRequest s = sellerRequest.payload();
        authenticate(sellerRequest, s.sellerId(), s);
        requireKnownGood(s.goodId());
        requireOwner(s.goodId(), s.sellerId());
        if (!onSale.contains(s.goodId())) {
            throw new NotaryException(NotaryException.Reason.NOT_ON_SALE, s.goodId());
        }
        if (!directory.knows(s.buyerId())) {
            throw new NotaryException(NotaryException.Reason.UNKNOWN_USER, s.buyerId());
        }
        // Both sides must consent: the buyer's own signed request must match this transfer.
        BuyRequest b = buyerRequest.payload();
        boolean buyerConsents = buyerRequest.signerId().equals(s.buyerId())
                && b.buyerId().equals(s.buyerId())
                && b.sellerId().equals(s.sellerId())
                && b.goodId().equals(s.goodId())
                && buyerRequest.verify(directory.publicKey(s.buyerId()));
        if (!buyerConsents) {
            throw new NotaryException(NotaryException.Reason.BUYER_DID_NOT_CONSENT, s.buyerId());
        }
        requireFreshSequence(s.sellerId(), s.sequence());

        long ts = lastWriteTs.getOrDefault(s.goodId(), 0L) + 1; // per-good version
        TransferCertificate cert = new TransferCertificate(s.goodId(), s.sellerId(), s.buyerId(), s.sequence(), ts);
        Signed<TransferCertificate> signed = Signed.sign(cert, id, privateKey);
        log.append(new LogEntry.TransferLogged(cert)); // durable first
        owners.put(s.goodId(), s.buyerId());
        onSale.remove(s.goodId());
        lastWriteTs.put(s.goodId(), ts);
        lastSequence.put(s.sellerId(), s.sequence());
        return signed;
    }

    /**
     * Adopt a good's state if it is newer than what we hold. Used by the Byzantine atomic register's write-back
     * phase so a replica that missed a write can catch up to a value a read quorum already vouched for.
     */
    public synchronized boolean adoptIfNewer(GoodState v) {
        if (v.timestamp() > lastWriteTs.getOrDefault(v.goodId(), 0L)) {
            owners.put(v.goodId(), v.ownerId());
            if (v.onSale()) {
                onSale.add(v.goodId());
            } else {
                onSale.remove(v.goodId());
            }
            lastWriteTs.put(v.goodId(), v.timestamp());
            return true;
        }
        return false;
    }

    private GoodState stateOf(String goodId) {
        return new GoodState(goodId, owners.get(goodId), onSale.contains(goodId), lastWriteTs.getOrDefault(goodId, 0L));
    }

    private <T extends Signable> Signed<T> sign(T payload) {
        return Signed.sign(payload, id, privateKey);
    }

    private void authenticate(Signed<?> request, String claimedId, Signable payload) throws NotaryException {
        if (!directory.knows(claimedId) || !request.signerId().equals(claimedId)) {
            throw new NotaryException(NotaryException.Reason.UNKNOWN_USER, String.valueOf(request.signerId()));
        }
        if (!request.verify(directory.publicKey(claimedId))) {
            throw new NotaryException(NotaryException.Reason.BAD_SIGNATURE, claimedId);
        }
    }

    private void requireKnownGood(String goodId) throws NotaryException {
        if (!owners.containsKey(goodId)) {
            throw new NotaryException(NotaryException.Reason.UNKNOWN_GOOD, goodId);
        }
    }

    private void requireOwner(String goodId, String ownerId) throws NotaryException {
        if (!ownerId.equals(owners.get(goodId))) {
            throw new NotaryException(NotaryException.Reason.NOT_OWNER, ownerId + " does not own " + goodId);
        }
    }

    private void requireFreshSequence(String userId, long sequence) throws NotaryException {
        if (sequence <= lastSequence.getOrDefault(userId, 0L)) {
            throw new NotaryException(NotaryException.Reason.STALE_SEQUENCE,
                    userId + " sequence " + sequence + " not newer than " + lastSequence.getOrDefault(userId, 0L));
        }
    }
}
