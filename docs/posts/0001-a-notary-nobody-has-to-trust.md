---
title: "I rebuilt another college project. This one is a notary that nobody has to trust."
subtitle: "What a digital notary is, why a signature is a promise you can't take back, and the one-line mistake that quietly unsigns your sequence numbers. (Fidus, part 1.)"
tags: [distributed-systems, java, cryptography, digital-signatures, security]
status: draft
date: 2026-10-05
series: Fidus
part: 1
---

A while back, for a university security class, I built a **notary**. Not the kind that stamps your passport photocopy —
a digital one. It kept track of who owned what, and when two people wanted to trade, it certified that the trade was
legit. It was a group project, it worked well enough to pass, and then it sat in a repo.

I dug it back up, threw the old code away, and rebuilt it in Java from scratch — because the best way I know to
actually understand a thing is to build it and then explain it to you. So here we are again.

## What does a notary even do?

Picture a world with a fixed set of people and a fixed set of **goods**. Alice owns good G1. Bob owns G3. Everyone
knows who everyone is. Now Alice wants to sell G1 to Bob.

The problem: how does Bob *know* Alice really owns G1 and isn't selling him something that isn't hers? How does Alice
know Bob actually agreed to buy, and won't later claim he never did? And how does anyone, later, prove the sale
happened at all?

That's the notary's job. It knows who owns what. It offers three operations:

- **intentionToSell** — an owner lists one of their goods for sale.
- **getStateOfGood** — anyone can ask who owns a good and whether it's for sale.
- **transferGood** — a seller and buyer transfer a good, and the notary certifies it.

The notary checks the seller really owns the good, that both sides agreed, and then hands back a **certificate** —
a receipt that proves the transfer happened. The whole point is that nobody has to trust anybody's *word*. They
trust the math.

## A signature is a promise you can't take back

The math is a digital signature. Alice has a private key only she has, and a public key everyone has. When she signs
a message, anyone can check — with her public key — that it was her, and that not one byte has changed since. She
can't later deny it (she's the only one who could have signed it), and nobody can forge it (they don't have her key).

In Fidus every message that crosses a boundary is wrapped in a `Signed<T>`:

```java
public record Signed<T extends Signable>(T payload, String signerId, byte[] signature) {
    public static <T extends Signable> Signed<T> sign(T payload, String signerId, PrivateKey key) {
        return new Signed<>(payload, signerId, Signatures.sign(key, payload.canonicalBytes()));
    }
    public boolean verify(PublicKey key) {
        return Signatures.verify(key, payload.canonicalBytes(), signature);
    }
}
```

A request is signed by the user. A reply — including the transfer certificate — is signed by the notary. The
certificate is the good part: it's a plain signed record,

```java
record TransferCertificate(String goodId, String sellerId, String buyerId, long sequence, long timestamp) { ... }
```

so *anyone* holding the notary's public key can verify a transfer really happened, forever, without asking the
notary again. That's non-repudiation, and it falls out of one `verify` call.

## The one-line mistake I'm proud of avoiding

Here's the detail I want you to remember, because it bit the original project and it bites a lot of real systems.

**What exactly do you sign?**

The lazy answer is "serialize the object and sign the bytes." The original code signed only the message's `content`
field. Look closely at what that leaves out: the anti-replay **sequence number** lived next to the content, not in
it. Sign only the content, and an attacker can keep the signature, bump the sequence number, and your "signed"
message now says something you never signed. The signature is real; it just doesn't cover the field that mattered.

So in Fidus, signing is never "sign the object." Every signable type says *exactly* which bytes its signature
covers:

```java
public record SellRequest(String ownerId, String goodId, long sequence) implements Signable {
    public byte[] canonicalBytes() {
        return Bytes.of("SellRequest", ownerId, goodId, sequence);   // the sequence is IN the signed bytes
    }
}
```

`Bytes.of` joins the fields with a separator byte so that `of("a","bc")` and `of("ab","c")` can never collide — a
real signing pitfall. The signature covers the type tag, the parties, the good, *and* the sequence number. Change
any of them and `verify` returns false. One boring method, and a whole class of forgeries disappears.

## So what

Right now that's a notary you can talk to: signed requests in, signed certificates out, and a `verify` that catches
anyone who lies about who they are or tampers with a message in flight. It's a few hundred lines of core code, it's
covered by tests I trust, and you can run it today.

Next post: the four specific ways people try to cheat a notary — impersonation, replay, tampering, and getting a
good transferred without the other side agreeing — and how the same signatures, plus a counter, stop all four.

The code is here: **[github.com/MRegra/fidus](https://github.com/MRegra/fidus)**. Apache-2.0, built to be read. It
started life as a project I half-understood at 22. Funny how that keeps happening.
