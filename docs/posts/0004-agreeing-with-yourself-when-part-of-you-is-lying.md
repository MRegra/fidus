---
title: "How a notary agrees with itself when part of it is lying"
subtitle: "One notary is a single point of failure and a single point of lying. Replicating it means Byzantine reliable broadcast — and the one amplification step the original project (and a lot of tutorials) quietly skip. (Fidus, part 4.)"
tags: [distributed-systems, java, byzantine-fault-tolerance, reliable-broadcast, consensus]
status: draft
date: 2026-10-05
series: Fidus
part: 4
---

Everything so far assumed one honest notary that might crash. Real dependability means dropping both halves of that
assumption: **many** notaries, some of which are **not honest**. A replica might crash, go silent, or — the scary
one — stay up and actively lie, telling different clients different things. That's a *Byzantine* fault, and it's the
model you need if the notary is valuable enough that someone would want to subvert it.

The classic result: to tolerate `f` Byzantine replicas you need `n = 3f + 1` of them. For `f = 1`, that's four. The
job of this post is to make four replicas agree on a transfer even when one of them is doing its best to split them.

## The problem a malicious sender creates

Say replica N0 receives a client's transfer and has to tell N1, N2, N3 about it. If N0 is honest, easy. If N0 is
Byzantine, it can **equivocate**: tell N1 "transfer G1 to Bob" and tell N2 "transfer G1 to Carol." Now the honest
replicas have heard different things. If they each just believe what they were told, the notary's state forks, and
you've got a double-spend across replicas.

We need a broadcast with a strong promise: **if any correct replica delivers a value, every correct replica delivers
the same value — and a Byzantine sender cannot make two correct replicas deliver different values.** That's
*Byzantine reliable broadcast*, and Bracha's authenticated double-echo is the classic way to get it.

## Double echo, in four rules

Every replica runs the same little state machine for a broadcast:

1. The sender sends `SEND(m)` to everyone.
2. On the first `SEND` from the designated sender, a replica sends `ECHO(m)` to everyone.
3. On more than `(n+f)/2` matching `ECHO`s, it sends `READY(m)`.
4. On `2f+1` matching `READY`s, it **delivers** `m`.

The echo round is a vote. A value only gets a `READY` once a strict majority of replicas — more than can be
faked by `f` liars plus half the honest ones — have vouched for it. If N0 equivocates, neither of its two stories
can gather an echo majority, so neither gets delivered. The fork is prevented not by catching N0, but by no bad value
ever clearing the bar.

Here's the core of it, straight from the code:

```java
case ECHO -> {
    echoes.putIfAbsent(m.from(), m.operation());
    if (!sentReady) opReaching(echoes, quorums.echo())
        .ifPresent(op -> { sentReady = true; out.add(message(READY, op)); });
}
case READY -> {
    readies.putIfAbsent(m.from(), m.operation());
    // ... amplification goes here ...
    if (!delivered) opReaching(readies, quorums.deliver())
        .ifPresent(op -> { delivered = true; onDeliver.accept(op); });
}
```

`opReaching(votes, threshold)` just asks "did any single value get at least this many distinct votes?" — one method,
counting ballots.

## The step everyone skips (and the original left out)

Rules 1–4 have a hole. Picture a correct replica that, through unlucky timing, ends up with `2f+1` `READY`s and
delivers — but a *different* correct replica only ever sees `f` `READY`s, never reaches the delivery threshold, and
is stranded forever. One correct replica delivered; another never will. That violates the "all correct replicas
deliver" promise.

The fix is an **amplification** step between rules 3 and 4:

> On `f+1` matching `READY`s, send `READY(m)` yourself — even if you never saw the echo majority.

Why `f+1`? Because among `f+1` replicas, at least one is correct (there are only `f` liars). So `f+1` `READY`s means
a *correct* replica is genuinely ready, which means the value cleared the echo bar somewhere legitimate. Joining in
is safe, and it pulls everyone else along: readiness spreads until all `2f+1`-correct replicas deliver. All-or-nothing
restored.

```java
case READY -> {
    readies.putIfAbsent(m.from(), m.operation());
    if (!sentReady) opReaching(readies, quorums.amplify())        // amplify = f + 1
        .ifPresent(op -> { sentReady = true; out.add(message(READY, op)); });
    if (!delivered)  opReaching(readies, quorums.deliver())       // deliver = 2f + 1
        .ifPresent(op -> { delivered = true; onDeliver.accept(op); });
}
```

Three lines. The original project's report listed "the amplification phase of the Byzantine Reliable Broadcast was
not implemented" under *known vulnerabilities*. It's an easy thing to leave out — the happy path works fine without
it, and it only matters under adversarial timing, which is exactly when you least want a replica stranded. So Fidus
puts it back, and there's a test that drives a silent replica and an equivocating sender to prove the guarantees
hold:

```java
equivocatingSenderNeverSplitsCorrectReplicas  // no two correct replicas deliver different values
deliversDespiteOneSilentReplica               // a quorum still delivers with f silent
```

## From broadcast to notary

Once a transfer is reliably broadcast, every correct replica delivers the *same* operation and applies it to its own
local notary — the exact `SingleNotary` from parts 1–3, signing with that replica's own key. They all make the same
state change, independently, from the same input. The client then waits for `2f+1` replicas to return the same
signed result before believing it. Up to `f` replicas can return garbage, stay silent, or equivocate; they can't
reach the quorum, so they can't change the answer.

## So what

Byzantine agreement has a reputation for being black magic. It isn't — it's a voting protocol where the thresholds
are chosen so that `f` liars can never tip a vote, and one amplification step makes sure the honest replicas don't
get left behind. Four rules and a careful count.

Next post: reading. Writes go through this broadcast, but a *read* from a cluster where `f` replicas might lie has
its own trap — and its own phase that the original left unfinished. That's the Byzantine atomic register, and its
write-back.

Code: **[github.com/MRegra/fidus](https://github.com/MRegra/fidus)**.
