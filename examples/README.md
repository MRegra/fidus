# Examples

## The in-process Byzantine demo (no setup)

```bash
mvn install
mvn -q -pl fidus-node exec:java
```

Four replicas agree on a transfer while one is Byzantine (silent). This is the quickest way to see the whole thing
work; the source is `fidus-node/.../BftDemo.java`.

## A networked notary (two terminals)

```bash
# terminal 1 — the notary. On first run it writes a demo world (keys + goods) under ./storage
mvn -q -pl fidus-node exec:java -Dexec.mainClass=dev.sobatista.fidus.node.rmi.NotaryServer -Dexec.args="storage"

# terminal 2 — a client that signs, mints Proof of Work, and runs alice -> sell, bob -> buy, carol -> read
mvn -q -pl fidus-node exec:java -Dexec.mainClass=dev.sobatista.fidus.node.rmi.NotaryClient -Dexec.args="storage"
```

The demo world:

```
G1 alice
G2 alice
G3 bob
G4 carol
```

A transfer returns a `TransferCertificate` signed by the notary — verify it any time with the notary's public key,
without asking the notary again. That's the receipt the whole system exists to produce.
