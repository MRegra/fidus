# Changelog

All notable changes to Fidus are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and Fidus uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-10-05
### Added
- **Byzantine-fault-tolerant notary** (`fidus-cluster`): `n` replicas tolerate `f` Byzantine faults with `n = 3f + 1`.
- **Byzantine reliable broadcast** — Bracha's authenticated double-echo, **including the amplification step** (the
  original project left it out): a value any correct replica is ready for reaches all of them, so delivery is
  all-or-nothing across correct replicas.
- **(1,N) Byzantine atomic register** for reads — a read quorum agrees the value and **writes it back** (the phase the
  original left unimplemented), so a later read never returns an older value and a lagging replica is repaired.
- **Proof-of-Work anti-spam** (`fidus-node`) with **server-side validation** (the third gap the original noted): a
  write must carry a hashcash stamp bound to its exact bytes.
- Byzantine fault injection (`SILENT`, `EQUIVOCATE`) and an in-process `BftDemo`.

## [0.1.0] - 2026-10-05
### Added
- `fidus-core`: a secure single notary — goods and ownership, RSA-signed requests and results, per-user sequence
  numbers (replay/duplication), impersonation and message-tampering prevention, dual seller+buyer consent, and signed
  transfer certificates.
- Crash-recovery: a durable write-ahead `TransactionLog` replayed on startup; a decision is logged before it is
  confirmed, so an intention lost to a crash is never honoured.
- 15 tests covering signatures, the four attack classes, dual consent, and recovery.

[Unreleased]: https://github.com/MRegra/fidus/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/MRegra/fidus/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/MRegra/fidus/releases/tag/v0.1.0
