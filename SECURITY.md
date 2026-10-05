# Security Policy

## Supported versions
Fidus is pre-1.0; only the latest `0.x` release gets fixes.

## Reporting a vulnerability
Please **do not** open a public issue for a vulnerability. Email **sobatistacyber@gmail.com** with details and a
proof of concept if you have one. You'll get an acknowledgement within a few days.

## Scope and threat model
Fidus is a **teaching** implementation of a Byzantine-fault-tolerant notary. Its *application* threat model is the
interesting part and is taken seriously: forged or replayed requests, impersonation, message tampering, a crashing
notary, up to `f` Byzantine replicas out of `n = 3f + 1`, and request floods (Proof of Work) are all defended and
tested.

What it is **not** hardened against is the *transport*: the networked server uses Java RMI, which deserializes
remote input and is a well-known deserialization risk. Run the networked parts only on trusted networks. A safer
wire format, TLS and a deserialization allow-list are on the [roadmap](ROADMAP.md). User keys in the demo are 1024-bit
for speed and the Citizen Card path of the original is replaced by ordinary RSA — neither is a production choice.
