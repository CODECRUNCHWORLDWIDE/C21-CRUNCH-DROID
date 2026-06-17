# Week 22 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 22 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Android Studio Ladybug+, Kotlin 2.0+, compileSdk 35, minSdk 24. Every problem must build with **0 warnings**. **Never commit a real secret** (keystore, service-account JSON, token).

---

## Problem 1 — Prove your encryption with adb

**Problem statement.** Take any app that stores a secret in `SharedPreferences`. Record the plaintext on disk (`adb shell run-as <pkg> cat shared_prefs/<file>.xml`). Migrate it to `EncryptedSharedPreferences` with a Keystore master key, then record the on-disk bytes again. Write `notes/at-rest-proof.md` with both readings (redact the actual secret value) and one sentence on the threat this defends against.

**Acceptance criteria.**

- `notes/at-rest-proof.md` shows the before (readable) and after (ciphertext) `adb` output.
- The migration clears the legacy plaintext file.
- The defended threat (data at rest: rooted device / backup / forensic) is stated.
- 0 warnings. Committed.

**Hint.** `run-as` works on debuggable builds. The "after" file shows base64 for both key names and values, nothing resembling your secret. Exercise 1 is your template.

**Estimated time.** 40 minutes.

---

## Problem 2 — A key that requires authentication

**Problem statement.** Generate a Keystore AES key with `setUserAuthenticationRequired(true)` (and a timeout). Use it to encrypt a value. Show that using the key without a recent device unlock fails (you'll get a `UserNotAuthenticatedException` or be prompted), and works after a biometric/PIN unlock. Document the behavior in `notes/auth-bound-key.md`.

**Acceptance criteria.**

- A Keystore key generated with `setUserAuthenticationRequired(true)`.
- A demonstration (description + screenshot/log) that key use requires a recent unlock.
- `notes/auth-bound-key.md` explains what threat this adds over a plain Keystore key (use on an unlocked-but-idle device).
- 0 warnings. Committed.

**Hint.** `KeyGenParameterSpec.Builder(...).setUserAuthenticationRequired(true).setUserAuthenticationParameters(timeoutSeconds, AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL)`. You may need a `BiometricPrompt` to authenticate before the crypto operation.

**Estimated time.** 50 minutes.

---

## Problem 3 — Compute and verify a pin

**Problem statement.** Pick a real HTTPS API. Compute its leaf *and* intermediate SPKI SHA-256 pins with `openssl`. Pin it in OkHttp using the *intermediate* pin plus the leaf as a backup. Then deliberately pin a *wrong* value and capture OkHttp's `SSLPeerUnverifiedException` message (which prints the actual pins it saw). Record both in `notes/pins.md`, and explain why pinning the intermediate is rotation-safer than the leaf.

**Acceptance criteria.**

- The leaf and intermediate SPKI pins, computed with `openssl`, recorded in `notes/pins.md`.
- A working pinned client (intermediate + backup), and the captured exception message from the deliberate wrong-pin test.
- An explanation of why the intermediate pin survives a leaf rotation.
- 0 warnings. Committed.

**Hint.** The openssl pipeline is in exercise 2's header. The "wrong pin to read the real pins from the exception" trick is the fastest practical way to get a correct pin — OkHttp tells you what it expected.

**Estimated time.** 45 minutes.

---

## Problem 4 — Refuse a MITM proxy

**Problem statement.** Route your app through mitmproxy (with the proxy's CA installed on the emulator). Show that an *unpinned* client's traffic is fully readable in the proxy window, and a *pinned* client refuses to connect (`SSLPeerUnverifiedException`). Add a network security config that trusts user CAs only in `<debug-overrides>`. Document the before/after in `notes/mitm.md` with screenshots of the proxy.

**Acceptance criteria.**

- Screenshots: the proxy reading unpinned traffic, and the pinned client refusing.
- A network security config trusting user CAs only in `<debug-overrides>`.
- `notes/mitm.md` explains why excluding user CAs in release closes the MITM hole.
- 0 warnings. Committed.

**Hint.** Set the emulator's proxy to your host:8080, install mitmproxy's CA via http://mitm.it. The pinned client throws at the pin check; the unpinned one happily talks to the proxy's forged cert. Exercise 2 is your template.

**Estimated time.** 50 minutes.

---

## Problem 5 — Fail closed, gracefully

**Problem statement.** Build a sign-in gate that requests a Play Integrity token with a nonce and models the result as a sealed type. Run it on an emulator *without* Google Play Services and confirm it fails *closed and gracefully* — denies with a clear message and a fallback, does not crash, does not silently allow. Then deliberately write the *wrong* (fail-open) version and note in `notes/fail-closed.md` exactly how an attacker would exploit it.

**Acceptance criteria.**

- A sealed `AttestationResult` and a sign-in gate that fails closed gracefully on `Unavailable`.
- A demonstration on an emulator without Play Services (screenshot of the graceful denial).
- `notes/fail-closed.md` contrasts the correct (closed) behavior with the fail-open bug and the attack it enables (disable Play Services to bypass attestation).
- 0 warnings. Committed.

**Hint.** Exercise 3 is your template. The fail-open trap is tempting because it "unblocks testing" — name why it's a security hole: an attacker disables Play Services and walks past attestation entirely.

**Estimated time.** 45 minutes.

---

## Problem 6 — The two-keystores memo

**Problem statement.** Write `notes/two-keystores.md` distinguishing the *two* keystores this course uses: Week 21's **app signing keystore** (proves the APK is from you; the upload key) and this week's Android **system Keystore** (encrypts the user's data on the device; hardware-backed). For each: what it protects, where the key lives, what happens if it leaks, and why confusing them is a common and dangerous mistake.

**Acceptance criteria.**

- A clear, correct contrast of the app-signing keystore vs the Android system Keystore.
- For each: purpose, key location, leak consequence.
- A note on why mixing them up (e.g. trying to store user data with the signing key, or shipping the system Keystore key in the APK) is wrong.
- Committed.

**Hint.** Week 21's keystore is about *release identity* (a file you guard as a CI secret, Play re-signs). This week's Keystore is about *runtime data protection* (keys that never leave the device's secure hardware). Different keys, different threats, different lifecycles.

**Estimated time.** 30 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, the control is correctly wired to a named threat, the mitigation is *proven* (not asserted), and the written explanation is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. pinning the leaf without a backup, a key generated without get-or-create). |
| 3 | Works, but misses one criterion (e.g. the proof step skipped, the fail-open contrast missing, the wrong cert pinned). |
| 2 | Compiles and partially works; a core idea is wrong (a fail-open attestation gate, a key derived in-app instead of in the Keystore, claiming encryption protects a running process). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−3** for any committed real secret (keystore, service-account JSON, token); **−3** for a fail-*open* attestation gate (silently allowing when integrity is unavailable — the cardinal security sin of this week); **−2** for a leaf-only pin with no backup pin (a rotation time bomb); **−1** for asserting a control works without *proving* it (no `adb`/proxy/no-Play-Services demonstration).

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — certificate pinning done safely (problems 3, 4) and the fail-closed-gracefully attestation gate (problem 5) — so re-run exercises 02 and 03 before resubmitting. Both are non-negotiable in next week's capstone.
