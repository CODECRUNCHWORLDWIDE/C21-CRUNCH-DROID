# Week 22 — Security: Keystore, EncryptedSharedPreferences, certificate pinning, Play Integrity

Welcome to Week 22 of **C21 · Crunch Droid**, the last of the Production Engineering arc before the capstone. Last week you secured the *release* — signing keys, encrypted secrets, a pipeline nothing untrusted can touch. This week you secure the *app itself*: the data it stores, the network it talks to, and the question every backend eventually asks — "is this request really coming from a genuine, untampered copy of my app on a real device, or from a script someone wrote to impersonate it?" Security on Android is not a feature you bolt on at the end; it is a set of platform primitives — the hardware-backed Keystore, encrypted storage, certificate pinning, and Play Integrity attestation — that you wire in deliberately, each guarding a specific threat.

The mental shift this week is from "my app works" to **"my app works *and* an attacker with the device in their hands, a proxy on the wire, and a rooted emulator still can't get what they want."** The phone-app instinct is to store a token in `SharedPreferences`, trust the system CA store for HTTPS, and assume the request hitting your backend came from your app. Every one of those is wrong under a real threat model. `SharedPreferences` is a plaintext XML file any rooted device or backup extraction reads. The system CA store can be subverted by a user-installed CA (the classic way a security researcher — or an attacker — MITMs an app to read its traffic). And your backend has *no idea* whether a request came from your signed APK on a Pixel or from a `curl` loop on a server farm. This week installs the four primitives that answer each threat: the **Android Keystore** generates keys that never leave secure hardware, so a key used to encrypt data can't be extracted even from a rooted device; **EncryptedSharedPreferences / EncryptedFile** wrap that key around your stored data so the on-disk bytes are ciphertext; **certificate pinning** in OkHttp rejects any TLS certificate that isn't yours, so a user-installed or compromised CA can't MITM you; and **Play Integrity** gives your backend a signed verdict about whether the app and device are genuine.

The thing this week hammers on is that **Play Integrity is not SafetyNet — and the SafetyNet shutdown is the deadline most teams missed.** SafetyNet Attestation, the old device-integrity API, was deprecated and shut down; teams that didn't migrate to Play Integrity found their attestation silently failing in production. We integrate Play Integrity end to end — request a token on the client, send it to your backend, decode the verdict server-side — and we are honest about what it *is* and *isn't*: it is a strong signal your backend weighs, not a client-side boolean you trust, and it can fail for innocent reasons (no Play Services, an emulator) so your app must degrade *gracefully* and *closed* (deny, with a clear message and a documented fallback), never *open* (silently allow). The other deep, dangerous topic is **certificate pinning done safely**: pinning is a footgun that has bricked real apps at certificate rotation, because a pin to a leaf certificate that expires and rotates leaves every installed app unable to connect. We teach the safe way — pin to the intermediate/backup, include a backup pin, and have a rotation plan — because a pin without a rotation plan is a time bomb.

We close the week by building a security pass over apps you already have: **add Keystore-backed encryption to the Week-14 notes app, pin the certificate of the Week-15 weather API, and integrate Play Integrity attestation as a sign-in gate.** Three real primitives on three real apps — encrypted notes whose on-disk bytes you can `adb pull` and confirm are ciphertext, a weather client that *refuses* to connect through a MITM proxy, and a sign-in that asks Play Integrity "is this genuine?" and fails closed with a clear message when it isn't. That before/after — "the token was plaintext in an XML file; now it's hardware-key-encrypted; the proxy that read my traffic last week now gets a connection refused; and a request from a tampered app is rejected at sign-in" — is the senior-engineer instinct this week installs.

## Learning objectives

By the end of this week, you will be able to:

- **Generate** a key in the Android Keystore (`KeyGenParameterSpec`, `AndroidKeyStore` provider) that never leaves secure hardware, and explain why a hardware-backed key can't be extracted even from a rooted device.
- **Encrypt** data at rest with `EncryptedSharedPreferences` and `EncryptedFile` (the Jetpack Security / Tink-backed APIs), and confirm the on-disk bytes are ciphertext by pulling the file off the device.
- **Reason** about key attestation: what a Keystore attestation certificate proves about where a key lives, and when you'd ask a backend to verify it.
- **Pin** a server certificate with OkHttp's `CertificatePinner`, choosing the *right* certificate in the chain to pin (intermediate/SPKI, with a backup pin) so rotation doesn't brick the app.
- **Explain** the certificate-pinning failure mode that has bricked real apps — pinning a leaf that rotates — and design a rotation-safe pinning strategy.
- **Integrate** Play Integrity end to end: request a token client-side, send it to a backend, decode the verdict (device/app/account integrity) server-side, and explain why the verdict is a *backend* decision, not a client boolean.
- **Migrate** the SafetyNet mental model to Play Integrity, and articulate why the SafetyNet shutdown stranded teams that didn't move.
- **Configure** the network security configuration (`network_security_config.xml`) — cleartext policy, trust anchors, debug overrides — and the post-Android-13 permission model (granular media permissions, notification runtime permission, `POST_NOTIFICATIONS`).

## Prerequisites

This week assumes you have completed **C21 weeks 1–21**, or have equivalent fluency. Specifically:

- You have the **Week-14 notes app** (Room + DataStore) — this week you encrypt its sensitive storage with a Keystore-backed key. If you skipped it, a minimal app that writes a token to `SharedPreferences` is enough to harden.
- You have the **Week-15 weather client** (Retrofit/OkHttp) — this week you pin its API certificate. The OkHttp interceptor and client-configuration skills from then are the foundation for `CertificatePinner`.
- You understand coroutines and `Flow` well enough to model an async attestation call and a sealed result — Weeks 4–5. The Play Integrity token request is a suspending call returning a sealed `AttestationResult`.
- You understand the secrets discipline from **Week 21** — base64 secrets, service accounts, nothing in the repo. Play Integrity has a server-side credential (a service account / API key) that follows the same hygiene.

**Toolchain.** Android Studio Ladybug (2024.2) or newer, JDK 17, Kotlin 2.0+. The Jetpack Security library (`androidx.security:security-crypto` — note its stability caveats below) or the modern Tink-direct approach; OkHttp 4.12+ for `CertificatePinner`; the Play Integrity API (`com.google.android.play:integrity`). Target SDK 35 (Android 15), minSdk 24. For pinning practice you need a **MITM proxy** (mitmproxy or Charles, both free for this) and an emulator you can route through it. Play Integrity requires the app to be known to the Play Console (an internal-track upload from Week 21 is the clean way) and a Google Cloud project; a documented emulator-without-Play-Services failure path is part of the lesson.

## Topics covered

- **The Android Keystore.** `KeyStore.getInstance("AndroidKeyStore")`, `KeyGenParameterSpec` (purposes, block modes, padding, `setUserAuthenticationRequired`), hardware-backed vs software keystores, StrongBox, and why a Keystore key's private material never enters your app's process memory.
- **Key attestation.** The attestation certificate chain a Keystore key can produce, what it proves (the key lives in verified hardware, the OS is in a known state), and the backend-verification use case.
- **EncryptedSharedPreferences and EncryptedFile.** The Jetpack Security APIs, the master-key wrapping model (a Keystore master key encrypts the data keys), the AEAD scheme, and the maintenance-mode caveat (the library is effectively frozen — the modern path is Tink directly or a thin wrapper, which we cover).
- **Data-at-rest threat model.** What plaintext `SharedPreferences` exposes (rooted device, ADB backup, file extraction), what encryption buys you, and what it does *not* (it doesn't protect data in memory, or against a fully compromised running process).
- **Certificate pinning with OkHttp.** `CertificatePinner.Builder().add(host, "sha256/...")`, pinning the SPKI hash of the *right* certificate (intermediate or a stable leaf), the backup pin, and wiring it into the client.
- **The pinning rotation footgun.** Why pinning a leaf that rotates bricks the app, the real outages this has caused, and the rotation-safe strategy (pin the intermediate or include backup pins; ship the new pin *before* the cert rotates; have a remote-config kill switch).
- **The network security configuration.** `network_security_config.xml`: `cleartextTrafficPermitted`, `<trust-anchors>`, `<domain-config>`, debug-only `<debug-overrides>` (so a proxy works in debug but not release), and declarative pinning as an alternative to OkHttp pinning.
- **SafetyNet → Play Integrity.** Why SafetyNet was deprecated and shut down, what stranded teams that didn't migrate, and the conceptual mapping from the old API to the new.
- **Play Integrity end to end.** `IntegrityManagerFactory`, requesting a standard or classic integrity token with a nonce, sending the token to your backend, decoding the verdict server-side (Google Play API or local decoding), the three verdict groups (app/device/account), and the nonce's anti-replay role.
- **Fail-closed-gracefully.** Why an attestation failure must deny with a clear user message and a documented fallback, never silently allow (fail-open) and never crash; handling the no-Play-Services and emulator cases.
- **The post-Android-13 permission model.** `POST_NOTIFICATIONS` runtime permission, granular media permissions (`READ_MEDIA_IMAGES`/`VIDEO`/`AUDIO` replacing `READ_EXTERNAL_STORAGE`), and the principle of requesting the least permission in context.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                                | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|----------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | The Keystore; hardware-backed keys; EncryptedSharedPreferences/File  |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | Key attestation; the data-at-rest threat model; the Tink-direct path |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Certificate pinning; the rotation footgun; network security config   |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | SafetyNet → Play Integrity; the verdict; fail-closed; challenge       |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — encrypt the notes app; pin the weather API            |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work — Play Integrity sign-in gate; prove each fix  |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                           |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                      | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./00-overview.md) | This overview (you are here) |
| [resources.md](./01-resources.md) | The Keystore and Jetpack Security docs, the OkHttp pinning guide, the network-security-config reference, the Play Integrity docs and SafetyNet migration guide, and the canonical talks |
| [lecture-notes/01-keystore-encrypted-storage-data-at-rest.md](./02-lecture-notes/01-keystore-encrypted-storage-data-at-rest.md) | The Android Keystore, hardware-backed keys, key attestation, EncryptedSharedPreferences/EncryptedFile and the Tink-direct path, and the data-at-rest threat model |
| [lecture-notes/02-certificate-pinning-network-security-play-integrity.md](./02-lecture-notes/02-certificate-pinning-network-security-play-integrity.md) | Certificate pinning the safe way, the rotation footgun, the network security configuration, SafetyNet → Play Integrity end to end, and fail-closed-gracefully |
| [exercises/README.md](./03-exercises/00-overview.md) | Index of the three exercises |
| [exercises/exercise-01-encrypt-a-token-keystore.md](./03-exercises/exercise-01-encrypt-a-token-keystore.md) | Replace a plaintext `SharedPreferences` token with a Keystore-backed encrypted store, then `adb pull` the file and confirm it's ciphertext |
| [exercises/exercise-02-certificate-pinning-okhttp.kt](./03-exercises/exercise-02-certificate-pinning-okhttp.kt) | Pin a server certificate in OkHttp, prove a MITM proxy is now refused, and add a rotation-safe backup pin |
| [exercises/exercise-03-play-integrity-verdict.kt](./03-exercises/exercise-03-play-integrity-verdict.kt) | Request a Play Integrity token with a nonce, model the verdict as a sealed result, and fail closed with a clear message when integrity can't be confirmed |
| [challenges/README.md](./04-challenges/00-overview.md) | Index of the challenge |
| [challenges/challenge-01-threat-model-and-harden.md](./04-challenges/challenge-01-threat-model-and-harden.md) | Write a threat model for one feature, then harden it against each threat — encrypted storage, pinning, attestation — and *prove* each mitigation works with an attacker's tools |
| [quiz.md](./05-quiz.md) | 13 questions on the Keystore, encrypted storage, pinning, the rotation footgun, network security config, and Play Integrity |
| [homework.md](./06-homework.md) | Six practice problems for the week |
| [mini-project/README.md](./07-mini-project/00-overview.md) | Full spec: Keystore-encrypt the notes app, pin the weather API certificate, and gate sign-in with Play Integrity |

## The "prove the mitigation" promise

Every week of Crunch Droid adds one production-grade contract a senior reviewer actually checks. This week's:

> **Every security claim must be *proven* with the attacker's own tools, not asserted.** "The token is encrypted" is not a claim — `adb pull` the file and show it's ciphertext. "The API is pinned" is not a claim — route the app through a MITM proxy and show the connection is *refused*. "Sign-in is attested" is not a claim — run on an emulator without Play Services and show it fails *closed* with a clear message. A security control you haven't tried to defeat is a security control you don't know works.

You will *prove* this in the mini-project: a token whose on-disk bytes you confirm are ciphertext, a weather client whose traffic a proxy could read last week and now *cannot* (connection refused at the pin check), and a sign-in gate that denies — gracefully, with a message and a documented fallback — when Play Integrity can't vouch for the app. Three controls, each demonstrated against the threat it's meant to stop.

## A note on what's not here

Week 22 is the *app-hardening* week. It deliberately does **not** cover:

- **Release signing and CI secrets.** The *app signing* keystore, encrypted CI secrets, and the release pipeline were **Week 21**. Don't confuse the two keystores: Week 21's *app signing* keystore (proves the APK is from you) versus this week's Android *system Keystore* (encrypts the user's data on the device). Different keys, different jobs.
- **Backend security in depth.** We send a Play Integrity token to a backend and decode the verdict, but designing the backend's auth, token issuance, and the full server-side trust model is a backend course's job (and the capstone's gRPC server). This week is the *client* side and the *contract* with the server.
- **Cryptography theory.** We use AEAD, SPKI hashes, and attestation as *tools* with correct defaults; the number theory behind AES-GCM or ECDSA is not re-derived here. Use the platform's vetted primitives; don't roll your own crypto.
- **Obfuscation as security.** R8 obfuscation (Week 18) raises the bar for reverse-engineering but is *not* a security control — a determined attacker deobfuscates. We're explicit that real controls (hardware keys, attestation) are the line, not name-mangling.

The point of Week 22 is narrow and adversarial: four platform primitives, each guarding a named threat, each *proven* against an attacker's tools — the difference between an app that "has security" and an app you've actually tried to break.

## Up next

Continue to **Week 23 — Capstone build week (Field-Force Companion)** once you have hardened the three apps and proven each control. The capstone integrates everything: the offline-sync, the Compose UI, the Wear companion, the KMP core, the CI/CD pipeline from last week — and the security primitives from this week are non-negotiable in it. The capstone's `:feature-auth` requires Play Integrity attestation at sign-in with Keystore-backed token storage; its `:core-network` requires certificate pinning with structured retry; its chaos drill #3 is *literally* "run on an emulator without Google Play Services and demonstrate graceful sign-in failure" — the exact fail-closed-gracefully behavior you build this week. You're not learning these primitives for an exam; you're building the parts the capstone is assembled from.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
