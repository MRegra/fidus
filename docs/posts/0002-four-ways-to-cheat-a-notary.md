---
title: "Four ways to cheat a notary, and how a signature and a counter stop all of them"
subtitle: "Impersonation, replay, tampering, and the transfer nobody agreed to — the whole attack surface of a notary, and the surprisingly small amount of code that closes it. (Fidus, part 2.)"
tags: [distributed-systems, java, security, replay-attacks, digital-signatures]
status: draft
date: 2026-10-05
series: Fidus
part: 2
---

In part 1 we built a notary that signs everything. Signing is necessary but it isn't the whole story. Let's put on
the attacker hat and actually try to cheat it. There are four classic moves. The nice thing is that they're all shut
down by a tiny amount of code — if you put the checks in the right order.

The notary runs the same four checks, in this order, on every state-changing request:

```java
authenticate(request, claimedUser, payload);  // 1 & 3: who are you, really?
requireKnownGood(goodId);
requireOwner(goodId, ownerId);                 // 4: is it yours to sell?
requireFreshSequence(userId, sequence);        // 2: have I seen this before?
```

## Attack 1: pretend to be someone else

Mallory wants to sell Alice's good. So she sends a `SellRequest` that *says* `ownerId = "alice"`.

Two ways that goes wrong for her. If she signs it with her own key but claims to be Alice, the signer and the claim
don't match:

```java
if (!directory.knows(claimedId) || !request.signerId().equals(claimedId)) {
    throw new NotaryException(Reason.UNKNOWN_USER, ...);
}
```

If instead she sets the `signerId` to "alice" but still signs with her own key — because she doesn't *have* Alice's
key — the signature simply won't verify against Alice's public key:

```java
if (!request.verify(directory.publicKey(claimedId))) {
    throw new NotaryException(Reason.BAD_SIGNATURE, claimedId);
}
```

Impersonation needs Alice's private key. That's the whole point of the key.

## Attack 2: replay a real message

This one is sneakier, because Mallory doesn't forge anything. She just *records* a legitimate message Alice sent —
say, a transfer — and sends it again. Everything verifies, because it really was signed by Alice. If the notary
processes it twice, a good moves twice.

The fix is a per-user **sequence number**. Every state-changing request carries one, and the notary refuses anything
that isn't strictly newer than the last it accepted from that user:

```java
if (sequence <= lastSequence.getOrDefault(userId, 0L)) {
    throw new NotaryException(Reason.STALE_SEQUENCE, ...);
}
```

The replayed message carries the *old* sequence number, so the second time around it's stale and bounced. And
because — from part 1 — the sequence number is inside the signed bytes, Mallory can't just increment it: that breaks
the signature. Replay and tampering close each other's escape hatches.

## Attack 3: change a message in flight

Mallory sits between Alice and the notary and flips a field — say, the good id — hoping to redirect the sale. But
the signature covers the canonical bytes of *every* field:

```java
Signed<SellRequest> original = alice.sell("G1", 1);
Signed<SellRequest> tampered  = new Signed<>(new SellRequest("alice", "G2", 1),
                                             original.signerId(), original.signature());
// notary: verify() -> false -> BAD_SIGNATURE
```

She kept the signature but changed the payload, so verification fails. There's no man in this middle who can do
anything but pass messages along unchanged.

## Attack 4: transfer a good nobody agreed to sell (or buy)

This is the one specific to a notary, and it's my favourite. A transfer isn't one person's decision — both the
seller *and* the buyer have to agree. If the notary only checked the seller's signature, a seller could "sell" a
good to someone who never asked for it, or a buyer could be bound to a purchase they never made.

So `transferGood` takes **two** signed requests, and checks they describe the same deal:

```java
BuyRequest b = buyerRequest.payload();
boolean buyerConsents = buyerRequest.signerId().equals(seller's buyerId)
        && b.buyerId().equals(buyerId) && b.sellerId().equals(sellerId) && b.goodId().equals(goodId)
        && buyerRequest.verify(directory.publicKey(buyerId));
if (!buyerConsents) throw new NotaryException(Reason.BUYER_DID_NOT_CONSENT, ...);
```

The seller signs "I sell G1 to Bob." The buyer signs "I buy G1 from Alice." Only if both signatures are present and
they agree on every field does the good move. Neither side can bind the other.

And — reusing attack 2's defence — the good can only be sold by its current owner, only while it's on sale, and the
seller's sequence number guards against replaying the transfer. Double-spend closed.

## So what

Four attacks, four checks, and most of the work is done by one idea (sign the canonical bytes) and one counter (the
sequence number). I find that deeply satisfying: the security isn't a bolt-on, it's the shape of the data.

The tests read like a list of attacks, which is how security code should read:

```
impersonationByWrongKeyIsRefused
replayOfAStateChangeIsRefused
messageManipulationIsRefused
transferWithoutBuyerConsentIsRefused
```

Next post: the notary has one more enemy that isn't an attacker at all — its own power cord. What happens when it
crashes in the middle of saying "yes"?

Code: **[github.com/MRegra/fidus](https://github.com/MRegra/fidus)**.
