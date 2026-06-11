# Week 22 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 90–150 minutes and produces something you can commit to your portfolio and point at in an interview: a written threat model for a real feature, the hardening that addresses each threat, and — the part that makes it senior-grade — *proof* that each mitigation works, demonstrated with an attacker's own tools.

## Index

1. **[Challenge 1 — Threat-model and harden a feature](challenge-01-threat-model-and-harden.md)** — pick one feature (a sign-in + sync flow), write a threat model that names the adversary and the attack for each asset, harden against each threat (encrypted storage, certificate pinning, Play Integrity, least-privilege permissions), and *prove* each control works by trying to defeat it: `adb pull` the encrypted store, route through a MITM proxy, run without Play Services. (~120 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — and "here's my threat model, here's the control for each threat, and here's the proof I tried to break each one and couldn't" is the kind of concrete, adversarial security work that lands in code reviews and senior interviews. This threat-model-then-prove discipline is exactly what the capstone's three chaos drills demand — and chaos drill #3 (graceful sign-in failure without Play Services) is a direct subset of what you build here.
