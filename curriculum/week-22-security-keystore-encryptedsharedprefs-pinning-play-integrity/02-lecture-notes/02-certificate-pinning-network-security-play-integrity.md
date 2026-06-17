# Lecture 2 — Certificate pinning, network security config, and Play Integrity

> "Pinning stops a forged certificate from reading your traffic — and pinning wrong stops *your own app* from connecting. Play Integrity tells your backend the request is real — and trusting it on the client tells you nothing."

Lecture 1 secured data at rest. This lecture secures data *in motion* and the *trust of the request itself*. Three controls, each guarding a named threat: **certificate pinning** stops a forged/compromised CA from man-in-the-middling your HTTPS; the **network security configuration** declares your trust policy to the platform; and **Play Integrity** lets your backend distinguish a genuine, untampered app on a real device from an impersonator. Two of these are footguns — pinning has bricked real apps, and Play Integrity is worthless if you trust its verdict on the client — so we spend as much time on *how they fail* as on how they work. By the end you can pin a server safely, prove a MITM proxy is refused, and gate sign-in on an attestation verdict that fails closed and gracefully.

---

## 1. The threat: a CA you didn't authorize

HTTPS already encrypts and authenticates your traffic — so why pin? Because HTTPS's trust model has a soft spot: your app trusts *any* certificate signed by *any* CA in the device's trust store. That store includes:

- **The ~100+ root CAs the OS ships.** Any one of them, if compromised or coerced, can issue a valid certificate for *your* domain.
- **User-installed CAs.** A user (or an attacker with the device, or a corporate MDM) can install a CA. A security researcher routes an app through a proxy by installing the proxy's CA — and then reads all the "encrypted" traffic, because the app trusts the proxy's forged certificate. That's the exact technique an attacker uses to MITM your app and lift its tokens and API calls.

**Certificate pinning** narrows the trust from "any CA the device trusts" to "*specifically my certificate (or its key)*." The app carries the expected certificate's fingerprint and rejects any TLS handshake whose certificate doesn't match — even a technically-valid one from a trusted CA. A forged cert from a compromised or user-installed CA fails the pin, and the connection is refused. The proxy can't read what it can't decrypt.

The mental model: **TLS authenticates "a CA vouches for this server"; pinning authenticates "this is *my* server's specific key."** The second is stricter, and that strictness is both the protection and the footgun.

---

## 2. Pinning with OkHttp — the right way

OkHttp's `CertificatePinner` pins by the SHA-256 hash of the certificate's **Subject Public Key Info (SPKI)** — the public key, not the whole certificate. This matters for rotation (next section): a cert can be reissued with the *same key*, and an SPKI pin survives that.

```kotlin
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

val certificatePinner = CertificatePinner.Builder()
    .add(
        "api.weather.crunch.com",
        // The SPKI SHA-256 of the CURRENT certificate (or, better, the INTERMEDIATE).
        "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        // A BACKUP pin — a second key you control, so you can rotate without bricking.
        "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
    )
    .build()

val client = OkHttpClient.Builder()
    .certificatePinner(certificatePinner)
    .build()
// Now any TLS connection to api.weather.crunch.com whose cert chain doesn't match
// one of the pins throws SSLPeerUnverifiedException — the connection is REFUSED.
```

You compute a pin from a server's certificate with `openssl`:

```bash
openssl s_client -connect api.weather.crunch.com:443 -servername api.weather.crunch.com \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | base64
# -> the sha256/... string you put in .add(...)
```

The proof (exercise 2): route the app through mitmproxy with the proxy's CA installed. **Without** pinning, the proxy reads every request — your "encrypted" traffic is plaintext to the attacker. **With** pinning, the app throws `SSLPeerUnverifiedException` and *refuses to connect* through the proxy, because the proxy's forged certificate doesn't match your pin. That connection-refused is the protection, demonstrated.

### Why pin the *public key* (SPKI), not the certificate

A subtle but load-bearing choice: OkHttp pins the SHA-256 of the **public key** (the SPKI), not the whole certificate. The difference matters for rotation. A certificate bundles a public key *plus* metadata (validity dates, issuer, serial) and an expiry; the public key inside it can be reused across reissuances. So:

- **Pinning the whole certificate** breaks the moment *anything* changes — including a routine reissue with the *same key* but new dates. Brittle.
- **Pinning the SPKI** survives a reissue that keeps the key. You only break when the *key itself* changes — which you control and can plan for (generate the next key in advance, ship it as a backup pin).

This is why every modern pinning guide pins the SPKI hash and why `CertificatePinner` works the way it does. When you compute a pin, the `openssl` pipeline above extracts the *pubkey* before hashing precisely so you pin the key, not the certificate envelope. Pin the thing that's stable across the operations you'll actually do.

### Programmatic vs declarative pinning — pick one deliberately

You have two places to pin: OkHttp's `CertificatePinner` (programmatic) and the network security config's `<pin-set>` (declarative, §4). They differ in failure behavior, which is the deciding factor:

- **OkHttp `CertificatePinner`** fails *closed, forever* — a pin mismatch throws, period. Testable in unit/integration tests, scoped to the OkHttp client. Best when you want hard, unconditional pinning you control in code.
- **Config `<pin-set>`** carries an `expiration` date after which pinning is *silently ignored* (fail-open). It applies to *all* HTTP from the app (not just OkHttp), but the expiry is both a safety valve against the rotation footgun and a control that quietly stops protecting.

For an app whose networking goes through OkHttp, the programmatic pinner is usually the right primary control (hard, testable), with the network security config used to *exclude user CAs in release* (the bigger anti-MITM win) rather than as the pinning mechanism. Don't pin in both places without understanding you now have two failure behaviors to reason about.

---

## 3. The rotation footgun — how pinning bricks apps

Here is the failure that has taken down real, large apps, and the reason pinning has a reputation as dangerous: **certificates expire and rotate, and a pin to a certificate that no longer exists makes every installed app unable to connect — and you can't fix it with a server change, because the bad pin is baked into apps already on users' phones.**

Walk the disaster:

1. You pin the SHA-256 of your **leaf certificate**.
2. A year later, that certificate expires. Your ops team rotates it — a *new* leaf with a *new* key, as is standard.
3. Every app version with the old pin now sees a certificate that doesn't match. Pin check fails. **Connection refused. The app is bricked** for everyone who hasn't updated — and they can't even reach your server to be told to update.
4. Your only fix is a forced app update, which takes days to propagate and which the bricked app can't even prompt for properly.

This is not hypothetical; it has caused multi-hour, multi-day outages at companies you've heard of. The safe strategy:

- **Pin the intermediate CA, not the leaf.** Intermediates rotate far less often than leaves, and a reissued leaf under the same intermediate still passes. This alone removes most of the risk.
- **Always include a backup pin** — a second key you control (e.g. the key of the *next* certificate you'll rotate to, generated in advance). Ship the backup pin *before* you rotate, so when you switch certs, the new one already matches a pin in the installed apps. This is the single most important practice.
- **Ship the new pin before the cert changes.** Pinning is a two-step dance: release an app version that trusts *both* the old and new pins, wait for adoption, *then* rotate the server cert. Never rotate the cert first.
- **Have a kill switch.** A remote-config flag that can *disable* pinning lets you recover from a botched rotation without a forced update. (It slightly weakens the control — an attacker who flips the flag disables pinning — so guard it; but a kill switch you never need beats an outage you can't fix.)

The senior framing: **pinning without a rotation plan is a time bomb with a one-year fuse.** The control is good; the operational discipline around it is what makes it safe. If you can't commit to the backup-pin-and-rotation discipline, *don't pin* — a missing pin is a smaller risk than a self-inflicted outage.

---

## 4. The network security configuration

Beyond OkHttp, the platform offers a *declarative* trust policy: `res/xml/network_security_config.xml`, referenced from the manifest (`android:networkSecurityConfig`). It controls cleartext, trust anchors, and even declarative pinning:

```xml
<network-security-config>
    <!-- App-wide default: no cleartext (HTTP) traffic anywhere. -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />   <!-- trust the system CA store... -->
            <!-- ...but NOT user-installed CAs in release (omit src="user") -->
        </trust-anchors>
    </base-config>

    <!-- Per-domain: declarative pinning as an alternative/complement to OkHttp. -->
    <domain-config>
        <domain includeSubdomains="true">api.weather.crunch.com</domain>
        <pin-set expiration="2026-12-31">
            <pin digest="SHA-256">AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=</pin>
            <pin digest="SHA-256">BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=</pin>  <!-- backup -->
        </pin-set>
    </domain-config>

    <!-- DEBUG ONLY: trust user CAs so a proxy works in debug builds — never in release. -->
    <debug-overrides>
        <trust-anchors>
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

Three high-value points:

- **Exclude user CAs in release.** By default, apps targeting modern Android already *don't* trust user-installed CAs (a big anti-MITM win the platform gave you for free). Don't accidentally re-add `<certificates src="user" />` to your release config — that reopens the proxy-MITM hole.
- **`<debug-overrides>` is the right place for proxy trust.** You *want* to MITM your own debug builds (to inspect traffic, to test pinning). Put the user-CA trust in `<debug-overrides>` so it applies only to debuggable builds and *never* ships in release. This is how you keep a usable debug proxy without weakening production.
- **Declarative `<pin-set>` has an `expiration`.** Unlike OkHttp pinning, the config's `pin-set` carries an expiry after which pinning is *ignored* (fail-open on expiry) — a built-in safety valve against the rotation footgun, but also a control that silently weakens. Know which behavior you want: OkHttp pinning fails closed forever; the config's expiring pin-set fails open after the date.

For most apps, OkHttp `CertificatePinner` (programmatic, testable, fails closed) plus a network security config that excludes user CAs in release and enables them only in `<debug-overrides>` is the clean combination.

### The debug-vs-release tension, resolved

There's a real friction pinning creates: in *development* you constantly want to proxy your own app (mitmproxy, Charles) to inspect traffic — which pinning *prevents*, because the proxy's cert fails the pin. Three clean ways to keep development sane without weakening release:

- **`<debug-overrides>` for the CA trust** (above) lets the proxy's user-installed CA be trusted in debug builds only. But OkHttp's `CertificatePinner` *also* pins, and it doesn't read the network security config — so you may still need to:
- **Skip the OkHttp pinner in debug builds.** Wire the `CertificatePinner` only for release: `if (!BuildConfig.DEBUG) builder.certificatePinner(pinner)`. Now debug builds proxy freely and release builds pin hard. This is the common, pragmatic split.
- **Add the proxy's pin to a debug-only pin set.** If you want to test the *pinning path itself* in debug, pin the proxy's key in a debug variant — but never let that leak to release.

The rule: **release must pin and must refuse user CAs; debug may relax both so you can develop.** Drive the difference off `BuildConfig.DEBUG` / build variants, and make a release-build test (or a CI check) that confirms the release variant actually pins and actually excludes user CAs — because the most dangerous bug here is a debug relaxation accidentally shipping in release. Test the *release* variant against the proxy and confirm it's refused; that's the proof that the production posture is intact.

---

## 5. SafetyNet → Play Integrity — the migration teams missed

Now the request-trust problem. Your backend receives a request claiming to be from your app. But *anyone* can replay your API calls from a script — your signed APK isn't required to hit your endpoints. How does the backend know the request came from a *genuine, untampered* copy of your app on a *real* device, not from a bot farm or a modified APK?

The old answer was **SafetyNet Attestation**. It was **deprecated and shut down**, replaced by **Play Integrity**. This matters even if you never used SafetyNet, because it's the canonical "deadline most teams missed" story: SafetyNet didn't fail with a bang — it was wound down on a published schedule, and teams that didn't migrate found their attestation *silently failing* in production, breaking sign-in or fraud checks for users with no obvious cause. The lesson: **deprecations with shutdown dates are real deadlines; track them.** Play Integrity is the current, supported API, and it's where all new work goes.

Conceptually, Play Integrity answers three questions (the **verdict groups**):

- **App integrity** — is this the *unmodified* APK you published, signed by your key, from Google Play (not a tampered or sideloaded copy)?
- **Device integrity** — is this a *genuine Android device* that passes basic integrity (not an emulator, not a rooted/compromised device, depending on the labels)?
- **Account/licensing** — does the user have a legitimate Play license for the app?

---

## 6. Play Integrity end to end — and why the verdict is the backend's

The flow has a non-negotiable shape: **request on the client, decide on the backend.**

**Client side — request a token with a nonce:**

```kotlin
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest

suspend fun requestIntegrityToken(context: Context, nonce: String): AttestationResult {
    return try {
        val manager = IntegrityManagerFactory.create(context)
        val token = manager.requestIntegrityToken(
            IntegrityTokenRequest.builder()
                .setNonce(nonce)              // server-provided, single-use — anti-replay
                .build()
        ).await()                             // suspending bridge over the Play Task
        AttestationResult.Token(token.token())  // an OPAQUE token — DO NOT decode on the client
    } catch (e: Exception) {
        // No Play Services, an emulator, network failure — fail CLOSED (see §7).
        AttestationResult.Unavailable(e.message ?: "integrity unavailable")
    }
}

sealed interface AttestationResult {
    data class Token(val value: String) : AttestationResult
    data class Unavailable(val reason: String) : AttestationResult
}
```

**Server side — send the token to your backend, which decodes and decides:**

```text
client: nonce = GET /auth/nonce         (server issues a fresh single-use nonce)
client: token = requestIntegrityToken(nonce)
client: POST /auth/sign-in { token }
server: decode token (via Google Play Integrity API), check:
          - nonce matches the one it issued (anti-replay)
          - appRecognitionVerdict == PLAY_RECOGNIZED
          - deviceRecognitionVerdict contains MEETS_DEVICE_INTEGRITY
        -> ALLOW or DENY the sign-in, with the verdict as ONE signal among several.
```

The two cardinal rules:

1. **The token is opaque; decode it on the *backend*, never the client.** The integrity token is meant to be decoded server-side (via Google's Play Integrity API or local decryption with your server-held keys). If you decode it on the client and trust the result, an attacker who controls the client just *forges a passing verdict* — the whole point is to move the trust decision off the device the attacker controls. **A client-side integrity boolean is worthless.**
2. **The nonce is anti-replay.** The server issues a fresh, single-use nonce; the client includes it in the request; the server checks the decoded token carries *that* nonce. This stops an attacker from capturing one valid token and replaying it forever. No nonce, no replay protection.

The verdict is a **signal your backend weighs**, not a gate the client enforces. A genuine device that briefly lacks integrity labels, a verdict that's `UNEVALUATED` because of a transient Play issue — the backend decides how strict to be, and combines integrity with its other fraud signals. Integrity is *input to* a backend trust decision, never *the* decision made on the client.

---

## 7. Fail closed, gracefully — the behavior the capstone grades

What happens when integrity *can't* be confirmed — no Play Services (an AOSP device, an emulator), a network failure, an `UNAVAILABLE` verdict? There are three wrong answers and one right one:

- **Fail open (WRONG, dangerous).** Silently allow sign-in when integrity is unavailable. Now any attacker just disables Play Services to bypass attestation entirely. Fail-open defeats the control.
- **Crash (WRONG, user-hostile).** An unhandled exception on the no-Play-Services path. The user sees a crash, not a reason.
- **Fail closed silently (WRONG, baffling).** Deny with no message. The user is locked out with no explanation and no path forward.
- **Fail closed *gracefully* (RIGHT).** Deny the integrity-gated action, show a **clear user-facing message** ("We couldn't verify your device. Sign-in requires Google Play Services."), and offer a **documented fallback** where one exists (a web flow, a support path, a reduced-privilege mode) — and *log* it so you can see attestation-failure rates in production.

```kotlin
when (val result = requestIntegrityToken(context, nonce)) {
    is AttestationResult.Token -> proceedToBackendSignIn(result.value)   // backend decides
    is AttestationResult.Unavailable -> showSignInBlocked(
        message = "We couldn't verify your device. Sign-in requires Google Play Services.",
        fallback = SupportFallback,        // a documented path, not a silent dead end
        reason = result.reason             // logged for production monitoring
    )
}
```

This is **chaos drill #3 of the capstone, verbatim**: "Run on an emulator without Google Play Services. Demonstrate graceful sign-in failure with a clear user-facing message and a documented fallback path (do not silently fail open)." You build that exact behavior this week. The discipline: **an attestation failure denies, explains, and offers a path — never silently allows, never crashes, never silently locks out.**

---

## 7b. What attestation does NOT do — and defense in depth

Play Integrity is powerful and easy to over-trust. Be precise about its boundaries, the same way you were about encryption-at-rest in lecture 1.

**It is a *signal*, not a guarantee.** A passing verdict says "this looks like a genuine, Play-recognized app on a device meeting integrity at the moment of the request." It does *not* say "this user is honest" or "this request is benign." A genuine app can still send malicious data; a legitimate user can still abuse your API within the app. Integrity authenticates the *client software and device*, not the *intent*. Your backend still needs rate limits, input validation, authorization checks, and fraud heuristics — attestation is *one* input to that stack, not a replacement for it.

**It can be unavailable for innocent reasons.** No Play Services (AOSP builds, many emulators, some enterprise devices), a transient Play outage, an offline device — all produce no verdict or an `UNEVALUATED` one. If you treat "no verdict" as "attacker," you lock out legitimate users; if you treat it as "fine," you've failed open. The graceful middle (deny the *high-assurance* path, offer a fallback) is the only defensible behavior, and *how strict* to be is a product decision weighed against your fraud risk.

**Rooted ≠ malicious, and unrooted ≠ safe.** Device-integrity labels flag rooted/compromised devices, but plenty of legitimate developers and power users root their phones, and plenty of attacks come from perfectly stock devices running a modified APK or a script. Don't build a security model whose *only* control is "block rooted devices" — it punishes honest power users and stops none of the script-based attacks. Attestation's *app* verdict (is this *my unmodified APK*?) is often the more useful signal than the device verdict.

**Defense in depth is the frame.** No single control on this week's list is sufficient alone. Encryption-at-rest protects disk but not a live process; pinning protects the wire but not a compromised endpoint; attestation signals client genuineness but not intent; least-privilege shrinks the surface but doesn't eliminate it. Security is the *layering*: an attacker who gets past one control meets the next. The senior posture is "what does each layer cost the attacker, and what's left if they defeat it?" — never "we have security because we added X."

A quick self-audit you can run on any attestation integration:

- **Is the nonce server-issued and single-use?** If the client invents the nonce, replay protection is gone.
- **Is the token decoded only on the backend?** A client-side decode-and-trust is forgeable and worthless.
- **Does the verdict feed a backend decision, not a client gate?** Integrity is one fraud signal the server weighs, not a boolean the client enforces.
- **Does the unavailable path fail closed, gracefully?** Deny + message + fallback + log — never fail-open, never crash, never silent lockout.
- **Are you treating "rooted" as the whole model?** It isn't; the *app* verdict (is this my unmodified APK?) is usually the stronger, fairer signal than the device verdict.

If any answer is "no," the integration has a hole an attacker who controls the client will walk through.

---

## 8. The post-Android-13 permission model (a brief, important aside)

Security includes *asking for the least you need*. Modern Android tightened permissions:

- **`POST_NOTIFICATIONS` is a runtime permission** (Android 13+). You must *request* it at runtime, in context, when the user understands why you'd notify them — not assume it.
- **Granular media permissions.** `READ_EXTERNAL_STORAGE` is replaced by `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`. Request only the media type you need; don't ask for all storage to read one image. (And prefer the Photo Picker, which needs *no* permission.)
- **Least privilege, in context.** Request a permission at the moment its value is clear to the user, request the narrowest one that does the job, and degrade gracefully if denied. An app that demands every permission at launch is both a security smell and a conversion killer.

The principle ties back to the whole week: **the least access that does the job is the most secure design** — fewer permissions, narrower key purposes, the tightest trust policy.

---

## 8b. A worked sequence — sign-in, end to end

Pulling the attestation pieces into one trace makes the client/backend split concrete. The flow, with every actor's responsibility:

```text
  CLIENT (your app)                         BACKEND (your server)
  ─────────────────                         ─────────────────────
  1. user taps "Sign in"
  2.  ──── GET /auth/nonce ────────────────▶  issue a fresh, single-use nonce;
                                              remember it against this session
  3.  ◀─── { nonce } ──────────────────────
  4. requestIntegrityToken(nonce)
       -> opaque token  (or Unavailable)
  5a. if Token:
       ──── POST /auth/sign-in {token} ────▶  decode token via Play Integrity API
                                              check token.nonce == issued nonce  (replay)
                                              check appRecognitionVerdict == PLAY_RECOGNIZED
                                              check deviceVerdict has MEETS_DEVICE_INTEGRITY
                                              weigh with other fraud signals
                                              -> issue session   OR   deny
      ◀─── { session }  or  { denied, reason }
  5b. if Unavailable (no Play Services / emulator / offline):
       DO NOT call sign-in with a forged "ok".
       Show: "We couldn't verify your device…" + a fallback (web sign-in) + log it.
```

Walk the decision points an attacker would probe:

- **Step 4, the token request fails.** This is the fail-closed branch (§7). The attacker's move is to *make* it fail (kill Play Services) hoping you fall open. You don't: you deny the high-assurance path and offer a fallback, never a silent allow.
- **Step 5a, the attacker replays an old token.** The backend rejects it because the decoded token's nonce doesn't match the *fresh* nonce issued in step 2 for *this* session. No nonce check, and one captured token works forever.
- **Step 5a, the attacker decodes/forges client-side.** Irrelevant — the client never decodes or decides. The verdict that matters is computed in step 5a on the server, from the opaque token, on infrastructure the attacker doesn't control.
- **A genuine user with a transient `UNEVALUATED` verdict.** The backend, weighing integrity as *one* signal among its fraud stack, can choose to allow with reduced privileges or step up another check — a product decision, made server-side, not a hard client gate.

The shape to memorize: **nonce out, opaque token back, decode-and-decide on the server, fail closed on the client when the token can't be obtained.** Every secure attestation integration is this sequence; every insecure one drops the nonce, decodes on the client, or falls open. The mini-project and capstone chaos drill #3 build exactly steps 4 and 5b — the client request and the graceful no-Play-Services denial — against a stub backend you can extend to do step 5a for real.

---

## 9. Recap

Lecture 1 secured the bytes on disk. This lecture secured the wire and the request:

1. **Certificate pinning** narrows TLS trust from "any CA" to "my key," refusing a forged cert from a compromised or user-installed CA — *proven* by a MITM proxy being refused.
2. **The rotation footgun** is the catch: pinning a leaf that rotates bricks installed apps. Pin the intermediate, always carry a backup pin, ship the new pin before rotating, keep a kill switch. Pinning without a rotation plan is a time bomb.
3. **The network security config** declares trust declaratively — exclude user CAs in release, trust them only in `<debug-overrides>`, optionally pin with an expiring `pin-set`.
4. **SafetyNet → Play Integrity** is the deprecation deadline teams missed; Play Integrity is the current attestation API, answering app/device/account integrity.
5. **Request on the client, decide on the backend.** The token is opaque — decode and judge it *server-side*; a client-side integrity boolean is worthless. The nonce stops replay. The verdict is a backend signal, not a client gate.
6. **Fail closed, gracefully** — deny, explain, offer a fallback, log it; never fail open, never crash, never silently lock out. That's capstone chaos drill #3.
7. **Least-privilege permissions** — runtime `POST_NOTIFICATIONS`, granular media, request the narrowest thing in context.
8. **Defense in depth, with honest limits** — no single control suffices; attestation is a signal not a guarantee, pinning protects only the wire, encryption only the disk. Layer them, and know what each costs an attacker and what's left if they defeat it.

The throughline for the whole week, both lectures: **name the threat, pick the layer that addresses it, state the layer's limit out loud, and prove it with the attacker's tools.** A token you `adb pull` and read is not protected; a connection a proxy can read is not pinned; a verdict you trust on the client is not attestation. The senior posture is never "we added security" — it is "here is the threat model, here is the control per threat, here is the proof I tried to break each one." Bring that to the mini-project, the challenge, and — non-negotiably — the capstone, where these exact controls become `:feature-auth`, `:core-network`, and chaos drill #3.

The exercises put encrypted storage, a refused MITM proxy, and a fail-closed attestation result under your hands; the challenge has you threat-model a feature and prove each mitigation with an attacker's tools; the mini-project hardens three real apps — encrypted notes, a pinned weather API, an attested sign-in. Go break your own app, then make it unbreakable against the threats you named — and prove it.
