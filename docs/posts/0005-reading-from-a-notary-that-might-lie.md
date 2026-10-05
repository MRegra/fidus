---
title: "Reading from a notary that might lie, and the write-back nobody implemented"
subtitle: "A quorum read keeps f liars from faking a value. But without write-back, two honest reads can go backwards in time — and the proof-of-work that stops spam only counts if the server actually checks it. (Fidus, part 5.)"
tags: [distributed-systems, java, byzantine-fault-tolerance, atomic-register, proof-of-work]
status: draft
date: 2026-10-05
series: Fidus
part: 5
---

Part 4 got writes agreed across a cluster where up to `f` replicas lie. This post is about the other half: reading,
and keeping the spammers out. Both have a phase that's easy to skip — and that the original project's report admits
it skipped.

## A read is a vote too

When a client asks "who owns G1?", it can't trust any single replica — that one might be lying. So it asks all of
them and looks for agreement. Each replica returns its **signed** `GoodState`, and the client keeps the value that a
read quorum of `2f+1` distinct replicas vouches for:

```java
for (var e : byValue.entrySet()) {
    long signers = e.getValue().stream().map(Signed::signerId).distinct().count();
    if (signers >= quorums.read() && (chosen == null || e.getKey().timestamp() > chosen.timestamp())) {
        chosen = e.getKey();
        certificate = e.getValue();
    }
}
if (chosen == null) throw new QuorumException("no read quorum for good " + goodId);
```

Why `2f+1`? Because any two quorums of that size overlap in at least one *correct* replica, and `f` liars can't form
one on their own. A Byzantine replica can sign a convincing-looking lie, but it's one signature; it never reaches the
quorum, so the client never picks it. The value a quorum agrees on is the true one.

## The trap: reads that go backwards

Here's the subtle bug. Suppose a write just happened. Three replicas have the new value; one correct replica was
briefly behind and still has the old one. A read might catch a quorum on the new value — good. But imagine the timing
is unluckier and a *later* read happens to gather its quorum from replicas that include the stale one, landing on the
**old** value. Two honest reads, and the second one went back in time. That's not atomic; that's barely even regular.

The fix is a **write-back**: after a read decides on a value, the client writes that value back to everyone before
returning it.

```java
for (ByzantineNotaryReplica r : replicas.values()) {
    r.writeBack(chosen, certificate);
}
return chosen;
```

Once a value has been read, write-back guarantees a quorum now holds it, so no later read can find a quorum on
anything older. That's what upgrades the register from "regular" to **atomic** — reads are monotonic, and they
linearize. It also quietly *repairs* the cluster: the replica that was behind catches up the moment someone reads.

But a write-back is a client telling replicas "adopt this value," and a Byzantine client could abuse that to inject
garbage. So a replica doesn't take the client's word — it demands the **quorum certificate**, the set of `2f+1`
signatures the client collected, and verifies them itself before adopting:

```java
long valid = certificate.stream()
        .filter(s -> value.equals(s.payload()))
        .filter(s -> directory.lookup(s.signerId()).map(s::verify).orElse(false))
        .map(Signed::signerId).distinct().count();
if (valid < quorums.read()) return false;          // an unvouched write-back is refused
return notary.adoptIfNewer(value);
```

The original report listed "the write-back phase of the Byzantine Atomic Register was not implemented" under known
vulnerabilities. It's the single most skippable-looking part of the whole design — reads *seem* to work without it —
and it's exactly the part that makes the guarantee real. There's a test that silences a replica through a write, heals
it, and shows a client read writing the true value back into it:

```java
readReachesQuorumAndWritesBackToALaggingReplica
```

One subtlety that makes the quorum even possible: timestamps are **per good**, not a global clock. Every correct
replica assigns the same version to the same operation, so their signed `GoodState`s are byte-identical and actually
*form* a quorum. A global counter would drift with delivery order and no two replicas would ever match.

## Making spam expensive: proof of work, checked

Last enemy: a flood. Nothing above stops someone from hammering the notary with a million requests. The classic
cheap defence is a **proof of work** — make the client burn CPU before the server will listen. Fidus uses hashcash:
the client must find a nonce whose hash over the request has `difficulty` leading zero bits.

```java
public static Stamp mint(byte[] request, int difficulty) {
    long nonce = 0;
    while (!solved(request, nonce, difficulty)) nonce++;
    return new Stamp(nonce, difficulty);
}
```

Minting costs ~`2^difficulty` hashes. Checking is **one** hash. And here's the part the original left as a known gap
— "proof of work does not use server-side validation" — so the server does the check, binding the stamp to the exact
request so it can't be reused for another:

```java
private void requireWork(byte[] request, ProofOfWork.Stamp pow) throws NotaryException {
    if (!ProofOfWork.validate(request, pow, minDifficulty))
        throw new NotaryException(Reason.INSUFFICIENT_PROOF_OF_WORK, ...);
}
```

A proof of work the server doesn't check is just a comment. The asymmetry — expensive to make, cheap to verify — only
helps you if the verify actually runs.

## So what

Three features, three "known vulnerabilities" from the original report, now closed: broadcast amplification (part 4),
atomic-register write-back, and server-side proof of work. None of them were hard. All of them were the *un-skippable*
part of something that looked finished without them — which is, I think, the real lesson of rebuilding an old project.
The happy path is the easy 80%. The guarantees live in the 20% everyone skips.

That's the series. The whole thing — a Byzantine-fault-tolerant notary you can read front to back — is here:
**[github.com/MRegra/fidus](https://github.com/MRegra/fidus)**. Apache-2.0. Clone it, run the demo, break a replica,
watch it hold.
