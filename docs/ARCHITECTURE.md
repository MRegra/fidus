# Fidus — architecture

Fidus is three layers, each a Maven module, each usable on its own. The split keeps the interesting
security and distributed-systems code away from transport plumbing.

```
                 clients (NotaryClient, your app)
                          │  signed requests  ·  signed replies  ·  Proof of Work
        ┌─────────────────┴───────────────────────────────┐
        │ fidus-node    RMI server · client library · ProofOfWork gate · BftDemo
        └─────────────────┬───────────────────────────────┘
                          │  BftOperation (agreed) · read quorum + write-back
        ┌─────────────────┴───────────────────────────────┐
        │ fidus-cluster ByzantineNotaryReplica                                   │
        │               ├─ ByzantineReliableBroadcast  (authenticated 2x echo)   │
        │               └─ Byzantine atomic register   (read quorum + write-back)│
        │               n replicas, each wrapping a SingleNotary                 │
        └─────────────────┬───────────────────────────────┘
                          │  Signed<SellRequest> / <TransferRequest> / <StateRequest>
        ┌─────────────────┴───────────────────────────────┐
        │ fidus-core    SingleNotary · GoodState · certificates · TransactionLog │
        │               Signatures · Signed<T> · canonical bytes                 │
        └──────────────────────────────────────────────────┘
```

## fidus-core — the notary

The heart is `SingleNotary`: it owns the map of good → owner, the set of goods on sale, and a per-user sequence
counter, and it certifies transfers. Every request is a `Signed<T>`; before anything else, the notary checks the
request is signed by exactly the user it claims to be from (impersonation), that the signature matches the payload's
canonical bytes (tampering), and that the sequence number is strictly newer than the last it saw from that user
(replay / duplication). A transfer additionally needs the **buyer's** signed `BuyRequest` to match the **seller's**
`TransferRequest` — both sides must consent — and the good must currently be on sale and owned by the seller.

`Signed<T>` is the only thing that crosses a boundary. It signs `T.canonicalBytes()`, an explicit field-by-field
encoding with separators, so the signature covers the sequence number and the parties, not just an opaque blob —
the subtle bug where "signing the content" silently omits the anti-replay field can't happen here.

**Durability** is a write-ahead `TransactionLog`: the notary appends and `fsync`s a decision *before* it returns it,
and rebuilds all mutable state purely by replaying the log on startup. So if it crashes after saying "yes" but
before the record is durable, the yes never happened — a later `transferGood` finds the good not on sale and makes
the seller re-express intention, exactly as the assignment requires. Replay on startup also stops cleanly at a torn
record at the tail, the fingerprint of a crash mid-write.

## fidus-cluster — making it Byzantine-fault-tolerant

A single notary can crash and can lie. Replication fixes both, with two algorithms over `n = 3f + 1` replicas.

1. **`ByzantineReliableBroadcast`** — Bracha's authenticated double-echo. A replica that receives a client write
   broadcasts it; everyone echoes; on more than `(n+f)/2` matching echoes a replica says READY; on `f+1` matching
   READYs it says READY too (**amplification**); on `2f+1` READYs it delivers. The guarantees: if one correct
   replica delivers a value, all correct replicas do (agreement), and no two correct replicas ever deliver different
   values even if the sender equivocates (consistency). The amplification step is what makes delivery
   all-or-nothing; without it some correct replicas can be stranded. Each correct replica then applies the delivered
   operation to its own `SingleNotary`, so they all make the same state change.

2. **(1,N) Byzantine atomic register** for reads. Each good is a register whose single writer is its current owner.
   A client read gathers each replica's **signed** `GoodState`, keeps the value a read quorum (`2f+1`) agrees on —
   which no `f` liars can fake and which carries the highest version — and then **writes it back**: it sends the
   value plus the quorum of signatures to every replica, and a replica adopts it only if that certificate really
   carries `2f+1` distinct valid signatures and the value is newer than its own. Write-back is what gives *atomic*
   (not merely regular) semantics — a later read never returns an older value — and it repairs a replica that missed
   the original write.

Timestamps are **per good**, not a global clock, so every correct replica assigns the same version to the same
operation regardless of the order it saw unrelated goods in — which is why their signed results match byte-for-byte
and a quorum forms. That single-writer-per-good structure is also why the notary needs reliable broadcast, not full
total-order consensus.

## fidus-node — making it runnable

`NotaryService` is a Java RMI interface; `GoodState`, the requests and the certificate are `Serializable`, so they
cross the wire as-is. `NotaryServer` binds a `SingleNotary` into an RMI registry behind a `GuardedNotary`, and
`NotaryClient` signs each request and mints the Proof of Work the server demands. `ProofOfWork` is hashcash bound to
the request bytes: minting costs the client ~`2^difficulty` hashes, **checking is one hash, and the server does it** —
the anti-spam validation the original project left client-side only. `BftDemo` wires the whole cluster in-process so
you can watch agreement happen with a Byzantine replica in the mix, no network required.

RMI is deliberate: it's the JDK-native descendant of the Java/.NET remoting these course projects used, so there
are zero transport dependencies and the code stays about the *algorithms*. Its deserialization risk is real — see
SECURITY.md and the roadmap.

## Why the seams are where they are

`Notary` (the operation API), `BftOperation` (what broadcast agrees on) and the signed `GoodState` register value
are the interfaces everything plugs into. The replica is just "a `SingleNotary` + broadcast + a register"; a real
networked cluster is the same replica behind an RMI link instead of the in-process bus, which is why it lands on the
roadmap without a rewrite of the core.
