# Contributing to Fidus

Thanks for looking! Fidus is a teaching-first project about dependable, secure distributed systems, so clear code
and clear explanations both count as contributions.

## Build & test
Requires **JDK 21** and **Maven 3.9+**.

```bash
mvn verify        # build + run all tests
mvn install       # build the modules into ~/.m2 (needed before exec:java below)
```

Run the in-process Byzantine demo (no network setup):

```bash
mvn -q -pl fidus-node exec:java
```

## How it's laid out
- `fidus-core` — the notary itself: goods and ownership, signed messages, certificates, the replay-protected
  single notary, and the crash-recovery log. Start here.
- `fidus-cluster` — the dependable part: Byzantine reliable broadcast, the Byzantine atomic register, the replica.
- `fidus-node` — RMI transport, the server and client, and the Proof-of-Work anti-spam gate.

## Sending a change
1. Branch from `main`.
2. Keep the change small and focused. Add a test — a bug fix gets a test that fails without it.
3. Use [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`; `feat!:` or a `BREAKING CHANGE:` footer for anything that breaks the public API.
4. `mvn verify` is green.
5. Open a PR describing what and why.

By contributing you agree your work is licensed under the project's [Apache-2.0](LICENSE) license.

## Style
Readable over clever. This code is also documentation — someone should be able to learn the algorithm by reading it.
Comments explain *why*, not *what*. Anything that signs or verifies spells out exactly which bytes it covers.
