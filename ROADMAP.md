# Roadmap

Fidus is built in layers so each milestone is a self-contained thing to learn and to write about.

## Shipped in 0.2.0
- **Byzantine reliable broadcast** (authenticated double-echo, with amplification) and a **(1,N) Byzantine atomic
  register** with write-back — the two algorithms that make the notary tolerate `f` Byzantine replicas.
- **Proof-of-Work anti-spam** with server-side validation.
- Fault injection (silent / equivocating replicas) and an in-process demo. 21 cluster/core tests.

## Shipped in 0.1.0
- The secure single notary: signed ownership transfers, per-user sequence numbers, impersonation / tamper / replay
  prevention, dual consent, signed certificates, and a replayable crash-recovery log.

## Next
- **Networked cluster over RMI** — run the replicas as real separate processes and collect the quorum over the
  network (0.2.0 proves the algorithms in-process; this makes it a real cluster). *(post: "The notary cluster goes multi-process")*
- **A puppet master for chaos** — launch replicas and inject crashes / equivocation / delays from a config, the way
  the demo does it in-process. *(post: "Breaking a Byzantine notary on purpose")*
- **Byzantine atomic register, full `(N,N)`** — multiple writers per good with the complete read/write-back quorums.
- **Transport hardening** — the RMI deserialization risk: a safer wire format, auth, TLS, an allow-list. *(post: "Your RMI endpoint is a deserialization gadget waiting to happen")*
- **Benchmarks** — the cost of a Byzantine-safe write and read as `f` grows, with numbers.
- **CI/pipeline polish** — release automation, coverage, spotless/checkstyle. *(post: "Shipping a Java library like you mean it")*
