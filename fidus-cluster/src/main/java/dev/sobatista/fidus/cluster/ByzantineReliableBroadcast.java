package dev.sobatista.fidus.cluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * One instance of Bracha's authenticated double-echo Byzantine reliable broadcast, from the point of view of a
 * single replica.
 *
 * <p>The guarantees, with {@code n = 3f + 1}: if the sender is correct, every correct replica delivers its value
 * (validity + totality); no two correct replicas ever deliver different values (consistency), even if the sender is
 * Byzantine and equivocates. The protocol:
 * <ol>
 *   <li>the sender sends {@code SEND(m)} to everyone;
 *   <li>on the first {@code SEND} from the designated sender, a replica {@code ECHO(m)}s to everyone;
 *   <li>on more than {@code (n+f)/2} matching {@code ECHO}s, it {@code READY(m)}s;
 *   <li><b>amplification:</b> on {@code f+1} matching {@code READY}s — at least one from a correct replica — it
 *       {@code READY(m)}s too, even without the echo quorum, so a value any correct replica is ready for reaches all;
 *   <li>on {@code 2f+1} matching {@code READY}s, it delivers {@code m}.
 * </ol>
 * The amplification step (4) is what makes delivery <em>all-or-nothing</em> across correct replicas; without it the
 * broadcast can strand some correct replicas undelivered. The original project left it out.
 */
public final class ByzantineReliableBroadcast {
    private final String selfId;
    private final String senderId;
    private final String broadcastId;
    private final Quorums quorums;
    private final Consumer<BftOperation> onDeliver;

    private boolean sentEcho = false;
    private boolean sentReady = false;
    private boolean delivered = false;
    private final Map<String, BftOperation> echoes = new HashMap<>();
    private final Map<String, BftOperation> readies = new HashMap<>();

    public ByzantineReliableBroadcast(String selfId, String senderId, String broadcastId,
                                      Quorums quorums, Consumer<BftOperation> onDeliver) {
        this.selfId = selfId;
        this.senderId = senderId;
        this.broadcastId = broadcastId;
        this.quorums = quorums;
        this.onDeliver = onDeliver;
    }

    /** The designated sender starts the broadcast. Returns the messages to disseminate to all replicas. */
    public List<BrbMessage> broadcast(BftOperation operation) {
        return List.of(new BrbMessage(BrbMessage.Tag.SEND, broadcastId, selfId, operation));
    }

    /** Feed one received message in; returns the messages this replica must now disseminate, and may deliver. */
    public List<BrbMessage> onReceive(BrbMessage m) {
        List<BrbMessage> out = new ArrayList<>();
        switch (m.tag()) {
            case SEND -> {
                if (m.from().equals(senderId) && !sentEcho) {
                    sentEcho = true;
                    out.add(message(BrbMessage.Tag.ECHO, m.operation()));
                }
            }
            case ECHO -> {
                echoes.putIfAbsent(m.from(), m.operation());
                if (!sentReady) {
                    opReaching(echoes, quorums.echo()).ifPresent(op -> {
                        sentReady = true;
                        out.add(message(BrbMessage.Tag.READY, op));
                    });
                }
            }
            case READY -> {
                readies.putIfAbsent(m.from(), m.operation());
                if (!sentReady) {
                    opReaching(readies, quorums.amplify()).ifPresent(op -> {
                        sentReady = true;
                        out.add(message(BrbMessage.Tag.READY, op));
                    });
                }
                if (!delivered) {
                    opReaching(readies, quorums.deliver()).ifPresent(op -> {
                        delivered = true;
                        onDeliver.accept(op);
                    });
                }
            }
        }
        return out;
    }

    public boolean delivered() {
        return delivered;
    }

    private BrbMessage message(BrbMessage.Tag tag, BftOperation op) {
        return new BrbMessage(tag, broadcastId, selfId, op);
    }

    /** An operation that at least {@code threshold} distinct replicas voted for, if any. */
    private Optional<BftOperation> opReaching(Map<String, BftOperation> votes, int threshold) {
        Map<BftOperation, Long> tally = votes.values().stream()
                .collect(Collectors.groupingBy(op -> op, Collectors.counting()));
        return tally.entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
