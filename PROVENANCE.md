# Provenance

Fidus began as a university *Highly Dependable Systems* assignment at IST ("HDS Notary", MEIC, 2018–19). This
repository is a **clean-room Java 21 reimplementation and expansion** written from an understanding of the problem,
not a port of any prior code.

- The original group's Java solution and the course's assignment/report PDFs are **not** included here — they are
  third-party / course material and stay out of the public tree (kept locally under a git-ignored `reference/`).
- All code in this repository is original work, licensed under [Apache-2.0](LICENSE).
- The semantics Fidus targets are described in our own words in [docs/SPEC.md](docs/SPEC.md).

Fidus also *finishes* what the original report listed as unfinished: the Byzantine reliable broadcast amplification
step, the Byzantine atomic register's write-back phase, and server-side validation of the anti-spam proof of work.

Fidus is its own project now: a teaching-first, Byzantine-fault-tolerant notary.
