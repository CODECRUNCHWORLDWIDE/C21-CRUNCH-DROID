# Week 22 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 23 (the capstone build). Answer key with explanations at the bottom — don't peek.

---

**Q1.** Why is a token stored in plaintext `SharedPreferences` a vulnerability?

- A) `SharedPreferences` is slow.
- B) It's an XML file on disk (`/data/data/<pkg>/shared_prefs/...`) readable by `adb pull`, an ADB backup, a rooted device, or a forensic image — the secret is right there in plaintext.
- C) It can only hold strings.
- D) It isn't thread-safe.

---

**Q2.** What makes the Android Keystore protect a key even on a *rooted* device?

- A) The key is obfuscated by R8.
- B) The key material never enters your process and, on hardware-backed devices, never leaves a secure element (TEE/StrongBox) — so there's nowhere for root to read it from. You ask the Keystore to *use* the key; you never see its bytes.
- C) The key is hidden in a file with a random name.
- D) Nothing; root can read any key.

---

**Q3.** What does `EncryptedSharedPreferences` give you over plain `SharedPreferences`?

- A) Faster reads.
- B) The same API, but the on-disk bytes (keys and values) are ciphertext, encrypted by a Keystore-backed master key — transparent on read/write.
- C) Cloud sync.
- D) Type safety.

---

**Q4.** Encryption-at-rest with a Keystore key does NOT protect against which of these?

- A) A rooted device reading the file.
- B) An ADB/cloud backup leaking the data.
- C) A *compromised running process* that has already decrypted the data into memory.
- D) A forensic image of the flash.

---

**Q5.** Why pin a certificate when HTTPS already encrypts and authenticates the connection?

- A) Pinning makes it faster.
- B) HTTPS trusts *any* cert from *any* CA the device trusts — including user-installed CAs (a proxy) and compromised CAs. Pinning narrows trust to *your specific key*, refusing a forged-but-CA-valid cert.
- C) Pinning replaces HTTPS.
- D) It isn't necessary; HTTPS is enough.

---

**Q6.** You pin the SHA-256 of your *leaf* certificate. A year later it expires and ops rotates it to a new leaf. What happens to installed apps?

- A) Nothing; they keep working.
- B) The new cert doesn't match the pin → pin check fails → connection refused → **every installed app is bricked** until a forced update, which they may not even be able to fetch.
- C) The OS auto-updates the pin.
- D) Only new installs are affected.

---

**Q7.** What is the single most important practice that makes certificate pinning rotation-safe?

- A) Pinning more certificates from more CAs.
- B) Always include a **backup pin** (a key you control / your next cert's key) and ship it *before* rotating the server cert — so installed apps already trust the new key when you switch.
- C) Rotating the certificate as often as possible.
- D) Never rotating the certificate.

---

**Q8.** Where do you put `<certificates src="user" />` so a debug proxy works but release stays protected?

- A) In `<base-config>`, for all builds.
- B) In `<debug-overrides>`, so user-installed CAs are trusted only in debuggable builds and never ship in release.
- C) In the manifest directly.
- D) Nowhere; you can't trust user CAs.

---

**Q9.** What replaced SafetyNet Attestation, and what's the lesson of the SafetyNet shutdown?

- A) Nothing replaced it; attestation is gone.
- B) **Play Integrity** replaced it; the lesson is that deprecations with shutdown dates are real deadlines — teams that didn't migrate found attestation *silently failing* in production.
- C) Firebase replaced it.
- D) SafetyNet is still the current API.

---

**Q10.** A Play Integrity token comes back to your app. What must you do with it, and what must you NOT do?

- A) Decode it on the client and trust the verdict.
- B) Treat it as **opaque** and send it to your **backend**, which decodes it and decides — because a client-side verdict can be forged by an attacker who controls the client. Bind it to a server nonce for anti-replay.
- C) Store it in `SharedPreferences`.
- D) Display the verdict to the user.

---

**Q11.** What is the nonce's role in a Play Integrity request?

- A) It speeds up the request.
- B) It's a fresh, single-use, server-issued value the backend checks the decoded token carries — preventing an attacker from capturing one valid token and replaying it forever.
- C) It encrypts the token.
- D) It identifies the user.

---

**Q12.** Integrity can't be confirmed (no Play Services, an emulator). What's the correct behavior?

- A) Fail open — silently allow sign-in.
- B) Fail **closed, gracefully** — deny the gated action, show a clear message, offer a documented fallback, and log the reason. Never fail open (an attacker would just disable Play Services), never crash, never silently lock out.
- C) Crash with the exception.
- D) Deny silently with no message.

---

**Q13.** Post-Android-13, how should an app handle notifications and media access?

- A) Declare every permission in the manifest and assume they're granted.
- B) Request `POST_NOTIFICATIONS` at runtime in context, use granular media permissions (`READ_MEDIA_IMAGES`/`VIDEO`/`AUDIO`) or the Photo Picker instead of broad storage, and request the least permission needed when its value is clear.
- C) Use `READ_EXTERNAL_STORAGE` for everything.
- D) Skip permissions; they're optional now.

---

## Answer key

**Q1 — B.** A plaintext token sits in a readable XML file an attacker with the device (rooted), a backup, or a forensic image reads verbatim. That's the vulnerability encryption-at-rest fixes. (Lecture 1, §1.)

**Q2 — B.** The Keystore's key material never enters your process and, on hardware-backed devices, never leaves the secure element — so root has nowhere to read it. You use the key via the Keystore; you never see its bytes. (Lecture 1, §2.)

**Q3 — B.** Same drop-in API, but a Keystore-backed master key encrypts the on-disk keys and values to ciphertext, transparently. (Lecture 1, §3.)

**Q4 — C.** Encryption at rest protects *disk* bytes, not a compromised *running* process that has already decrypted the data into memory. State the limit; don't overclaim. (Lecture 1, §5.)

**Q5 — B.** HTTPS trusts any cert from any trusted CA — including a user-installed proxy CA and compromised CAs. Pinning narrows trust to your specific key, refusing a forged-but-valid cert. (Lecture 2, §1.)

**Q6 — B.** A leaf pin that rotates makes the new cert fail the pin in every installed app — bricked until a forced update they may not be able to fetch. This is the rotation footgun that's caused real outages. (Lecture 2, §3.)

**Q7 — B.** A backup pin (a key you control), shipped *before* rotating, so installed apps already trust the new key when you switch. Plus: pin the intermediate, ship-then-rotate, keep a kill switch. (Lecture 2, §3.)

**Q8 — B.** `<debug-overrides>` applies only to debuggable builds, so a proxy CA works in debug and release stays protected. Never put user-CA trust in the base/release config. (Lecture 2, §4.)

**Q9 — B.** Play Integrity replaced SafetyNet. The shutdown stranded teams that didn't migrate — attestation silently failed in production. Deprecation deadlines are real. (Lecture 2, §5.)

**Q10 — B.** The token is opaque; send it to the backend to decode and decide. A client-side verdict is forgeable by an attacker controlling the client. Bind to a nonce for anti-replay. (Lecture 2, §6.)

**Q11 — B.** A fresh, single-use, server-issued value the backend checks the token carries — anti-replay, so a captured token can't be reused. (Lecture 2, §6.)

**Q12 — B.** Fail closed, gracefully: deny, explain, offer a fallback, log. Fail-open lets an attacker bypass attestation by disabling Play Services; crashing or silent denial is user-hostile. This is capstone chaos drill #3. (Lecture 2, §7.)

**Q13 — B.** Runtime `POST_NOTIFICATIONS`, granular media permissions or the Photo Picker, least-privilege requested in context. The narrowest access that does the job is the most secure design. (Lecture 2, §8.)

---

*Score 11+? On to Week 23, the capstone build. Below 9? Re-read both lecture notes and re-run exercises 2 and 3 — certificate pinning (and the rotation footgun) and the fail-closed Play Integrity gate are the two ideas this week is graded on, and both are non-negotiable in the capstone.*
