# Mini-Project — Harden three apps: encrypt, pin, attest

This week you make a **security pass over apps you already built**: add Keystore-backed encryption to the Week-14 notes app, pin the certificate of the Week-15 weather API, and integrate Play Integrity attestation as a sign-in gate. Three real platform primitives on three real apps — and, the part that makes the lesson land, each one *proven* against the threat it's meant to stop: ciphertext you can `adb pull`, a MITM proxy that gets *connection refused*, and a sign-in that fails closed and gracefully when integrity can't be confirmed.

The point of the project is not "sprinkle some security." It is to wire each primitive to a *named threat* and then *demonstrate* it works by trying to defeat it. That "prove the mitigation, don't assert it" instinct is the senior security skill this week installs — and the three controls you build here are exactly the capstone's `:feature-auth` (attestation + Keystore token storage), `:core-network` (pinning), and chaos drill #3 (graceful no-Play-Services denial).

This builds directly on prior weeks. If you skipped them, minimal stubs are fine: a token written to `SharedPreferences` (for the encryption task), an HTTPS client hitting a real API (for pinning), and a sign-in button (for attestation). The *primitives* are the deliverable, not the apps' size.

---

## The threat-to-control map (start here)

Before any code, fix in your head *which control answers which threat* — that mapping is what the whole project is graded on, and it's what you'll write up in `SECURITY.md`:

| Asset under threat | Adversary & attack | Control you build | Proof you'll show |
|---|---|---|---|
| The auth token at rest | Attacker with the device (rooted / backup / forensic image) reads `shared_prefs` | Keystore-backed `EncryptedSharedPreferences` (hardware master key) | `adb` shows ciphertext where there was a readable token |
| The API traffic in transit | Attacker with a user-installed CA (a proxy) MITMs the HTTPS connection | OkHttp `CertificatePinner` (current + backup pin) + release config excluding user CAs | A MITM proxy is *connection-refused* (`SSLPeerUnverifiedException`) |
| The request's authenticity | Attacker runs a script or a modified APK to hit your backend | Play Integrity token (nonce-bound), decoded + decided *server-side* | On an emulator without Play Services, sign-in fails *closed, gracefully* |
| The permission surface | Over-broad permissions widen the attack surface / leak data | Least-privilege, in-context requests (runtime `POST_NOTIFICATIONS`, granular media) | The app works when a permission is denied; it asks for nothing it doesn't use |

Each milestone below builds one row, and each carries a **proof** step — because this week's contract is *prove the mitigation, don't assert it*. A control you haven't tried to defeat is one you don't know works.

---

## Where you're starting from

- The **Week-14 notes app** (Room + DataStore) with at least one sensitive value (an auth token, an encryption passphrase) currently in plaintext.
- The **Week-15 weather client** (Retrofit/OkHttp) hitting a real HTTPS API.
- A sign-in entry point (a button, a screen) to gate with attestation.
- Tools: `adb`, mitmproxy (free), `openssl`, an emulator, and — for real attestation tokens — a Play Console internal-track build (Week 21) and a Google Cloud project. The no-Play-Services *fail-closed* path needs none of that.

## What you're building toward

By the end you have:

- The notes app's sensitive storage **Keystore-encrypted**, with the on-disk bytes confirmed ciphertext via `adb`.
- The weather API **certificate-pinned** (current + backup pin), with a MITM proxy demonstrably **refused**, and a debug-only override so you can still proxy debug builds.
- Sign-in **gated on Play Integrity**, requesting a token with a nonce, forwarding it to a (stub) backend, and **failing closed gracefully** when integrity is unavailable.
- A `SECURITY.md` documenting each control, the threat it addresses, its limits, and the *proof* you tried to defeat it.

## How the three controls relate (the one-diagram view)

The three primitives are independent — each guards a different layer — but they compose into one hardened sign-in-and-sync flow. Keep this picture in mind as you build, so you don't conflate them:

```text
   ┌──────────────────────── your app ────────────────────────┐
   │                                                            │
   │  sign-in ──▶ [Play Integrity]  token ──▶ backend decides   │  (request authenticity)
   │                                                            │
   │  token stored ──▶ [Keystore-encrypted store] on disk       │  (data at rest)
   │                                                            │
   │  sync API call ──▶ [Certificate pinning] OkHttp ──▶ server  │  (data in transit)
   │                                                            │
   └────────────────────────────────────────────────────────────┘
        attest the request │ encrypt what's stored │ pin the wire
```

Notice they don't overlap: pinning does nothing for data at rest, encryption does nothing for the wire, and attestation does nothing for either — it vouches for the *client*. That's the point of defense in depth: an attacker who defeats one layer still faces the others. A reviewer who sees all three, each wired to its own threat, reads "this engineer thinks in layers," not "this engineer sprinkled security."

---

## Milestone 1 — Encrypt the notes app's secrets (≈ 1.5 h)

Replace plaintext storage with a Keystore-backed encrypted store (exercise 1). Encrypt the sensitive value(s) — an auth token, and optionally a note-encryption passphrase:

```kotlin
class SecureStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context, "secure_store", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun putSecret(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun getSecret(key: String): String? = prefs.getString(key, null)
}
```

Migrate and clear any legacy plaintext. Decisions you must defend in review:

- **Why is the master key in the Keystore, not derived in-app?** Because a Keystore key's material never enters your process and can't be extracted even from a rooted device. A key hardcoded in the APK or derived from a constant is trivially recovered by reverse-engineering — no protection at all.
- **What does this *not* protect?** Data in memory while the app is running, and a fully compromised process. State the limit; don't claim encryption-at-rest covers a live-process compromise.

**Prove it:** `adb shell run-as <pkg> cat shared_prefs/secure_store.xml` shows ciphertext; the old plaintext file is gone. Record before/after in `SECURITY.md`.

## Milestone 2 — Pin the weather API (≈ 1.5 h)

Add OkHttp certificate pinning to the weather client (exercise 2), with the right cert and a backup pin:

```kotlin
val pinner = CertificatePinner.Builder()
    .add(API_HOST,
        "sha256/<current-or-intermediate-pin>=",
        "sha256/<backup-pin>=")          // ship BEFORE rotating to avoid bricking
    .build()
val client = OkHttpClient.Builder().certificatePinner(pinner).build()
```

Add a network security config that excludes user CAs in release and trusts them only in `<debug-overrides>` (so you can proxy debug builds without weakening release):

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors><certificates src="system" /></trust-anchors>
    </base-config>
    <debug-overrides>
        <trust-anchors><certificates src="user" /></trust-anchors>
    </debug-overrides>
</network-security-config>
```

Decisions to defend:

- **Why a backup pin, and why pin the intermediate?** Leaves rotate (yearly+); a leaf-only pin bricks every installed app at rotation. Pinning the intermediate (and always carrying a backup pin shipped *before* rotating) is what makes pinning safe rather than a time bomb.
- **Why user CAs only in `<debug-overrides>`?** So you can MITM-inspect *debug* builds, while release builds refuse user-installed CAs — closing the proxy-MITM hole in production.

**Prove it:** route the app through mitmproxy with its CA installed. Without pinning, the proxy reads your traffic. With pinning, the app throws `SSLPeerUnverifiedException` and refuses. Screenshot both; record in `SECURITY.md`.

## Milestone 3 — Gate sign-in with Play Integrity (≈ 2 h)

Add an attestation gate to sign-in (exercise 3): request a token with a nonce, model the result, and fail closed gracefully:

```kotlin
suspend fun attemptSignIn(context: Context): SignInOutcome {
    val nonce = backend.issueNonce()                          // fresh, single-use
    return when (val r = requestIntegrityToken(context, nonce)) {
        is AttestationResult.Token ->
            backend.signIn(r.value)                            // backend decodes + DECIDES
        is AttestationResult.Unavailable ->
            SignInOutcome.Blocked(                             // FAIL CLOSED, GRACEFULLY
                message = "We couldn't verify your device. Sign-in requires Google Play " +
                    "Services. You can sign in on the web instead.",
                fallback = Fallback.WebSignIn,
                reason = r.reason
            )
    }
}
```

Decisions to defend:

- **Why decode the verdict on the backend, never the client?** The token is opaque by design; a client that decodes and trusts it can be made to forge a passing verdict. Attestation only means something when the decision lives on a server the attacker doesn't control. Bind it to the nonce for anti-replay.
- **Why fail closed and not open?** Fail-open lets an attacker bypass attestation by disabling Play Services. Closed-and-graceful (deny + message + fallback + log) denies the impersonator while treating the legitimate no-Play-Services user with respect.

**Prove it:** run on an emulator *without* Google Play Services. Confirm sign-in returns `Blocked` with a clear message and a fallback — not a crash, not a silent allow, not a silent lockout. Screenshot the denial; this *is* capstone chaos drill #3.

## Milestone 4 — Least-privilege permissions + `SECURITY.md` (≈ 0.5 h)

Audit permissions: request `POST_NOTIFICATIONS` at runtime in context (when enabling alerts), use granular media permissions or the Photo Picker if you touch media, and remove anything unused. Confirm the app degrades gracefully if a permission is denied.

Write `SECURITY.md`: for each control (encryption, pinning, attestation, permissions), document the **threat addressed**, the **limit** of the control, and the **proof** (the `adb`/proxy/no-Play-Services demonstration).

---

## The verification matrix — how each control is proven

Before you call the project done, run this matrix. Each row is a control, the *attack you mount against it*, and the *evidence* that it held. This is the artifact that turns "I added security" into "I tried to break my security and couldn't":

| Control | The attack you run | Pass evidence | Fail (what a bug looks like) |
|---|---|---|---|
| Encrypted storage | `adb shell run-as <pkg> cat shared_prefs/secure_store.xml` | base64 ciphertext; no readable secret; legacy `auth.xml` empty/gone | your token visible in the XML, or the old plaintext file still present |
| Certificate pinning | route through mitmproxy with its CA installed; trigger the API call | unpinned build: proxy reads it (the *before*). pinned build: `SSLPeerUnverifiedException`, connection refused | pinned build still talks to the proxy (wrong cert pinned, or pinner not attached) |
| Play Integrity gate | run on an emulator *without* Google Play Services; tap sign-in | `SignInGate.Blocked` with a clear message + a fallback; reason logged | a crash, a silent allow (fail-open), or a silent lockout (no message) |
| Least-privilege permissions | deny `POST_NOTIFICATIONS` (and any media permission); use the app | the app still works (degraded gracefully); requests nothing it doesn't use | a crash on denial, or a launch-time wall of permission requests |

If any row's "fail" column describes your app, that control isn't done — fix it and re-run the attack. A row you didn't actually attack (you "trust it works") doesn't count: the contract this week is *proof*, and the proof is you, holding the attacker's tool, failing to get in.

A pragmatic ordering: do the encryption row first (fastest feedback — just `adb` and look), then pinning (needs the proxy set up), then the integrity gate (needs the no-Play-Services emulator), then permissions. Capture a screenshot per row as you go; those screenshots *are* your `SECURITY.md` proof section.

---

## Acceptance criteria

- [ ] The notes app's sensitive storage is Keystore-backed encrypted; legacy plaintext migrated and cleared; `adb` confirms ciphertext on disk.
- [ ] The weather API is pinned with a current *and* backup pin; the network security config excludes user CAs in release (debug-only override); a MITM proxy is demonstrably **refused**.
- [ ] Sign-in is gated on Play Integrity, requests a token with a nonce, forwards the opaque token to a backend, and **fails closed gracefully** when integrity is unavailable (proven on an emulator without Play Services).
- [ ] Permissions are least-privilege and requested in context; the app degrades gracefully on denial.
- [ ] `SECURITY.md` documents each control's threat, limit, and *proof*.
- [ ] No real secret committed (no keystore, no service-account JSON, no token).
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **Key attestation.** For the notes app's encryption key, generate it with `setAttestationChallenge(serverNonce)` and have a (stub) backend verify the attestation chain proves the key is hardware-backed. Document what the chain asserts.
- **`setUserAuthenticationRequired`.** Bind the encryption key's *use* to a recent biometric/PIN unlock, so the data can't be decrypted on an unlocked-but-idle device without re-auth. Prove it prompts.
- **A pinning kill switch.** Add a remote-config flag that can disable pinning, and document the trade-off (recover from a botched rotation vs an attacker flipping the flag). Guard it.
- **Backend verdict decode.** Stand up a tiny backend (Ktor) that actually decodes the Play Integrity token via the Play API and checks the nonce, so the attestation is end-to-end real, not just a client request. (Capstone-grade.)

## What this milestone earns you

You can now harden a real app against named threats with the platform's vetted primitives — hardware-backed keys, encrypted storage, certificate pinning done *safely*, and Play Integrity attestation that fails closed gracefully — and you can *prove* each control works with an attacker's tools rather than asserting it. That is the literal "skill earned" line for the week: Keystore-backed secrets, certificate pinning that doesn't catastrophically fail at rotation, and Play Integrity as a real attestation flow, not a checkbox.

More than the three APIs, you leave with the *posture*: name the threat, choose the layer that addresses it, accept the layer's limits out loud, and then attack your own control to confirm it holds. That "threat-model, layer, prove" loop is what separates an app that *has* security from one an engineer has actually tried to break — and it's the mindset a security reviewer is really grading, far more than any single API call.

These three controls drop straight into the capstone's `:feature-auth`, `:core-network`, and chaos drill #3 — so next week's capstone build inherits working, proven security instead of inventing it under deadline. Week 23 is the capstone build itself: everything from Weeks 1–22 — including this week's hardening — pulled into one shipping, multi-form-factor, secured, CI-released system.
