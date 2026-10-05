# Fidus

**A Byzantine-fault-tolerant notary — in Java, built to be read.**

[![CI](https://github.com/MRegra/fidus/actions/workflows/ci.yml/badge.svg)](https://github.com/MRegra/fidus/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)
![status](https://img.shields.io/badge/status-0.2.0%20(pre--1.0)-orange)

A notary certifies that something happened: here, that a **good changed owner**. Users own goods and can put them
up for sale, and a buyer and seller transfer one only with the notary's blessing — it checks the seller really owns
the good, that both sides agreed, and hands back a **signed certificate** anyone can verify. Nobody has to trust
anybody's word; they trust the signatures.

Fidus makes that notary **dependable**. A single notary is a single point of failure and a single point of lying,
so Fidus replicates it: the state lives on several servers, and the system keeps giving correct answers even if up
to `f` of `n = 3f + 1` replicas **crash, go silent, or actively lie** (Byzantine faults). It's a teaching-first
codebase — the goal is that you can read it and *learn how* digital-signature protocols and Byzantine agreement
actually work, not just import a jar.

## 30-second demo

Requires JDK 21 and Maven.

```bash
mvn install                        # build the modules
mvn -q -pl fidus-node exec:java    # run the in-process Byzantine demo
```

Four replicas agree on a transfer while one of them is Byzantine (it stays completely silent):

```
fidus — a Byzantine-fault-tolerant notary
4 replicas (N0..N3), tolerating f=1 fault. N3 is Byzantine: it stays silent.

alice -> intentionToSell(G1): a quorum agrees, G1 is on sale.
bob   -> buyGood(G1): transfer agreed; certificate signed by N0.
carol -> getStateOfGood(G1): read quorum says owner=bob, onSale=false.

N3 never participated; the other three agreed anyway. ✓
```

## The operations

```java
// every request is signed by its sender; every reply is signed by the notary
Signed<GoodState> state = notary.stateOfGood(sign(new StateRequest("carol", "G1", nonce)));   // who owns it, on sale?
Signed<GoodState> onSale = notary.intentionToSell(sign(new SellRequest("alice", "G1", 1)));   // owner lists it

// a transfer needs BOTH sides' signatures — the seller's request and the buyer's consent
Signed<TransferCertificate> receipt = notary.transferGood(
        sign(new TransferRequest("alice", "bob", "G1", 2)),   // seller
        sign(new BuyRequest("bob", "alice", "G1", nonce)));   // buyer
receipt.verify(notaryPublicKey);   // the non-repudiable proof the transfer happened
```

Every message is a `Signed<T>` — a payload plus a signature over its **canonical bytes**, so the signature covers
every field that matters (the good, the parties, *and* the sequence number). Change one byte and verification fails.

| Protection | How |
|---|---|
| **Impersonation** | a request must be signed by exactly the user it claims to be from |
| **Replay / duplication** | each state-changing request carries the user's monotonic sequence number; nothing old is reprocessed |
| **Tampering (MITM)** | the signature is over canonical bytes, so any change breaks it |
| **Double-spend** | a good can only be sold by its current owner, only while on sale, with both sides' consent |
| **Lost-to-a-crash writes** | a decision is logged *before* it's confirmed; what isn't durable never happened |
| **Byzantine replicas** | reliable broadcast + signed quorums: up to `f` of `3f+1` can lie without changing the answer |
| **Request floods (DoS)** | a Proof-of-Work stamp, bound to the request and validated server-side |

## Modules

| Module | What's inside |
|---|---|
| **fidus-core** | The notary itself — `GoodState`, the signed message types, `TransferCertificate`, RSA `Signatures`/`Signed`, the replay-protected `SingleNotary`, and the replayable crash-recovery `TransactionLog`. Start here. |
| **fidus-cluster** | The dependable part — `ByzantineReliableBroadcast` (authenticated double-echo **with amplification**), the (1,N) `ByzantineNotary` atomic register **with write-back**, quorum math, and the replica. Plus silent / equivocating fault injection. |
| **fidus-node** | The runnable part — an RMI `NotaryServer` and `NotaryClient`, `ProofOfWork` anti-spam **with server-side validation**, and the in-process `BftDemo`. |

## Run a server and a client

```bash
# terminal 1 — a notary server (creates a demo world of keys + goods on first run)
mvn -q -pl fidus-node exec:java -Dexec.mainClass=dev.sobatista.fidus.node.rmi.NotaryServer \
    -Dexec.args="storage"

# terminal 2 — a client that signs, mints Proof of Work, sells and buys
mvn -q -pl fidus-node exec:java -Dexec.mainClass=dev.sobatista.fidus.node.rmi.NotaryClient \
    -Dexec.args="storage"
```

## How it works

Read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the design, [`docs/SPEC.md`](docs/SPEC.md) for the exact
semantics and fault model, and [`ROADMAP.md`](ROADMAP.md) for what's next (the networked cluster, a chaos puppet
master, transport hardening). Write-ups that explain the algorithms live in [`docs/posts/`](docs/posts).

## Status & scope

Pre-1.0: the API can still move. This is a **teaching implementation**. The *application* threat model — forgery,
replay, impersonation, crash and Byzantine faults, floods — is defended and tested; the networked *transport* (Java
RMI) is **not** hardened for untrusted networks (see [SECURITY.md](SECURITY.md)). The Byzantine algorithms are proven
in-process; running the replicas as real separate processes over RMI is on the roadmap.

## Provenance

Fidus grew out of a university distributed-systems assignment (IST, "HDS Notary"). It is a clean-room Java
reimplementation and expansion — the original code is not included — and it *finishes* the three things that
project's report listed as undone: broadcast amplification, atomic-register write-back, and server-side Proof of
Work. See [PROVENANCE.md](PROVENANCE.md).

## License

[Apache-2.0](LICENSE).
