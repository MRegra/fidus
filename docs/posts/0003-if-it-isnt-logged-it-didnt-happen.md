---
title: "\"If it isn't logged, it didn't happen\": crash recovery for a notary"
subtitle: "The notary can lose power mid-sentence. A write-ahead log and one ordering rule — durable before confirmed — turn a crash from a catastrophe into a shrug. (Fidus, part 3.)"
tags: [distributed-systems, java, durability, write-ahead-log, crash-recovery]
status: draft
date: 2026-10-05
series: Fidus
part: 3
---

So far the notary's enemy has been a person. This post's enemy is physics. The server can crash — power cut, OOM
kill, a careless `kill -9` — at the worst possible moment: right after it told Alice "yes, G1 is on sale," and right
before that fact was saved anywhere. Reboot, and the notary has forgotten something a user already believes.

The whole fix is one sentence: **if it isn't logged, it didn't happen.** Let me show you what that buys.

## A log you can replay

The notary keeps every decision in an append-only log, and — this is the important bit — it rebuilds *all* of its
mutable state by replaying that log on startup. The in-memory maps are a cache; the log is the truth.

```java
private void recover() {
    for (LogEntry entry : log.replay()) {
        if (entry instanceof LogEntry.IntentionLogged i) {
            onSale.add(i.goodId());
            lastWriteTs.put(i.goodId(), i.timestamp());
        } else if (entry instanceof LogEntry.TransferLogged t) {
            owners.put(t.certificate().goodId(), t.certificate().buyerId());
            onSale.remove(t.certificate().goodId());
        }
        lastSequence.merge(entry.user(), entry.sequence(), Math::max);
    }
}
```

Notice the sequence numbers get rebuilt too — so the replay protection from part 2 survives a reboot. An attacker
can't wait for the notary to restart and then re-send an old message; the counter came back with it.

## Durable before confirmed

Here's the ordering rule that makes the crash a non-event. The notary appends to the log and `fsync`s it **before**
it returns a confirmation. Not after. Not "eventually."

```java
long ts = lastWriteTs.getOrDefault(goodId, 0L) + 1;
log.append(new LogEntry.IntentionLogged(ownerId, goodId, sequence, ts));  // durable FIRST
onSale.add(goodId);                                                       // then change memory
return sign(stateOf(goodId));                                             // then confirm
```

Now walk the crash through it:

- Crash **before** the append? Nothing is logged, and the RMI call never returned, so the user got no confirmation.
  Consistent: nothing happened, and everyone agrees nothing happened.
- Crash **after** the append? The record is durable. On reboot, replay puts the good back on sale. Consistent: it
  happened, and it stays happened.

There is no window where the user holds a "yes" the notary will forget. That's the entire game.

## The assignment's favourite corner case

The original assignment called out a specific scenario: the notary says yes to `intentionToSell`, then crashes
before the log write. When it comes back, a `transferGood` for that good must be **refused**, forcing the seller to
re-express intention.

I didn't write a special case for that. It just... happens, because of the rule above. If the intention never made
it to the log, then after recovery the good isn't on sale — `onSale` is only ever rebuilt from logged intentions.
And `transferGood` refuses a good that isn't on sale:

```java
if (!onSale.contains(goodId)) throw new NotaryException(Reason.NOT_ON_SALE, goodId);
```

The test models exactly that — a notary over an empty log — and asserts the transfer is refused with `NOT_ON_SALE`.
The crash rule isn't a feature; it's a consequence of "if it isn't logged, it didn't happen."

## The torn record at the end

One more detail I enjoy. A crash can happen *during* the log append itself, leaving a half-written record at the
tail of the file. Replay has to not choke on it. So each entry is length-prefixed, and replay stops cleanly when the
last record is short:

```java
int len = in.readInt();
byte[] bytes = in.readNBytes(len);
if (bytes.length < len) break;   // torn write at the tail: a crash mid-append — ignore the partial record
```

A torn record is, by definition, a decision that was never confirmed (the append hadn't finished, so the call hadn't
returned). Dropping it is not just safe — it's *correct*. Same rule, one more time.

## So what

A notary that forgets is worse than useless; it actively lies. With a replayable log and one ordering rule, a crash
drops from "data-loss incident" to "the process restarts and nobody notices." The code that does it is about a
hundred lines, and most of it is the file format.

But a log only saves you from *your own* crash. It does nothing if the notary is replicated and one of the replicas
is actively malicious — telling different people different things on purpose. That's where it gets genuinely hard,
and that's the next post: Byzantine agreement.

Code: **[github.com/MRegra/fidus](https://github.com/MRegra/fidus)**.
