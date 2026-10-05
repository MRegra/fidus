# Fidus — specification

This is what Fidus promises, in our own words. It targets the IST HDS Notary problem; the wording here is original.

## Entities and state

- A fixed, globally-known set of **users**, each with an RSA key pair. Their public keys live in a `UserDirectory`
  known to everyone. Replicas are identities too, each with its own key pair.
- A fixed set of **goods**, each initially owned by one user: a set of `⟨goodId, ownerId⟩` pairs.
- One logical **notary** (realised as `n` replicas) that certifies transfers.

Per good, the notary tracks its **owner**, whether it is **on sale**, and a **version** (a per-good counter bumped on
every state change). Per user, it tracks the last **sequence number** it accepted.

## Operations

All requests are signed by their sender; all replies are signed by the notary (or, in the cluster, by each replica).

- `stateOfGood(StateRequest{reader, good, nonce})` → `Signed<GoodState{good, owner, onSale, version}>`
  — a read. The reader must be a known user and sign the request; a nonce is remembered to reject duplicate reads.
- `intentionToSell(SellRequest{owner, good, sequence})` → `Signed<GoodState>`
  — the current owner lists a good for sale. Refused unless the signer owns the good and the sequence is fresh.
- `transferGood(TransferRequest{seller, buyer, good, sequence}, BuyRequest{buyer, seller, good, nonce})`
  → `Signed<TransferCertificate{good, seller, buyer, sequence, version}>`
  — transfer, needing both the seller's and the buyer's signatures. Refused unless the good is on sale, owned by the
  seller, the buyer is a known user whose `BuyRequest` matches this transfer, and the sequence is fresh.

## Security properties

1. **Authenticity / non-repudiation.** Every request and reply is signed. The signature is over the payload's
   canonical byte encoding, which includes every security-relevant field. A reply (especially a `TransferCertificate`)
   is verifiable by anyone holding the notary's public key.
2. **Integrity (no tampering / MITM).** Any change to a signed payload breaks its signature; altered messages are
   refused.
3. **No impersonation.** A request is accepted only if its signer id equals the acting user named in the payload and
   the signature verifies against that user's public key.
4. **No replay / duplication.** Each state-changing request carries the user's monotonic `sequence`; the notary
   refuses anything not strictly newer than the last it accepted from that user. Reads use a remembered nonce.
5. **Correct transfers only.** A good is transferred only if it is on sale, owned by the claimed seller, and both
   seller and buyer have signed consistent requests — preventing double-spends and non-consensual transfers.

## Dependability properties

6. **Crash recovery.** State is rebuilt only by replaying a durable log. A decision is made durable before it is
   confirmed, so no confirmed decision is ever lost, and no unlogged decision is ever honoured after a crash.
7. **Byzantine fault tolerance.** With `n = 3f + 1` replicas, the system stays correct while up to `f` replicas are
   faulty in any way — crashing, silent, or lying:
   - *Writes* are disseminated by Byzantine reliable broadcast. If one correct replica delivers an operation, all do;
     no two correct replicas deliver conflicting operations.
   - *Reads* return a value a read quorum of `2f+1` replicas agrees on (so no `f` liars can forge it), and write it
     back, giving atomic register semantics.
8. **DoS resistance.** State-changing requests must carry a Proof of Work bound to the request and meeting a minimum
   difficulty, checked by the server.

## Assumptions and non-goals

- Each good has a **single writer** at a time (its current owner), which is why reliable broadcast suffices and full
  total-order consensus is not needed.
- The set of users and replicas is **fixed and known**; there is no membership change protocol.
- The **transport is trusted**. Java RMI deserializes remote input; the networked parts assume a trusted network.
  Hardening it is on the roadmap.
- Keys are managed out of band; the demo generates them for convenience.
