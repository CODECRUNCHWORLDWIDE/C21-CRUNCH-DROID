# Week 22 — Exercises

Short, focused drills. Each one should take 35–55 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Encrypt a token with the Keystore](exercise-01-encrypt-a-token-keystore.md)** — replace a plaintext `SharedPreferences` token with a Keystore-backed `EncryptedSharedPreferences` store, then `adb pull` the file and *confirm with your eyes* that the on-disk bytes are ciphertext, not your token. Lecture 1's core claim, proven. (~45 min)
2. **[Exercise 2 — Certificate pinning in OkHttp](exercise-02-certificate-pinning-okhttp.kt)** — pin a server's certificate with `CertificatePinner`, route the app through a MITM proxy, and prove the connection is *refused* (where an unpinned client's traffic was readable). Then add a rotation-safe backup pin. (~50 min)
3. **[Exercise 3 — A Play Integrity verdict that fails closed](exercise-03-play-integrity-verdict.kt)** — request an integrity token with a nonce, model the result as a sealed type, and handle the no-Play-Services / emulator case by failing *closed and gracefully* — deny with a clear message and a fallback, never silently allow. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- **Prove each control with the attacker's tools.** Exercise 1: `adb pull` and read the file. Exercise 2: route through mitmproxy and watch the refusal. Exercise 3: run on an emulator without Play Services and watch it fail closed. A control you haven't tried to defeat is one you don't know works.
- The `.kt` exercises drop into the `app` module (the Week-14 notes app and Week-15 weather client are the natural hosts; a minimal app works too).
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A plaintext secret, a pin that doesn't refuse a proxy, or an attestation that fails *open* is a bug this week — the "prove the mitigation" rule is the arbiter, not your intuition.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-22` to compare.
