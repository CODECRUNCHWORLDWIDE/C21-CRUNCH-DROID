# Week 22 — Resources

Every primary resource on this page is **free**. Android's developer documentation is free. OkHttp, Tink, and the AndroidX source are open source. mitmproxy is free. The Play Integrity docs are free (the API has a generous free quota). The conference talks are free on YouTube. A couple of paid items are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"Android Keystore system."** The framing document for hardware-backed keys — `KeyGenParameterSpec`, the `AndroidKeyStore` provider, what never leaves secure hardware. Read this before you generate a single key:
  <https://developer.android.com/privacy-and-security/keystore>
- **"Work with data more securely" / Jetpack Security.** `EncryptedSharedPreferences`, `EncryptedFile`, the master-key model — and note the library's maintenance status (lecture 1 covers the Tink-direct alternative):
  <https://developer.android.com/privacy-and-security/cryptography>
- **"Security with network protocols" / certificate pinning (OkHttp).** `CertificatePinner`, choosing the right cert, the backup pin — central to lecture 2:
  <https://square.github.io/okhttp/features/https/>
- **"Network security configuration."** `network_security_config.xml` — cleartext policy, trust anchors, debug overrides, declarative pinning:
  <https://developer.android.com/privacy-and-security/security-config>
- **"Play Integrity API overview."** The attestation API that replaced SafetyNet — requesting a token, the verdict, the backend decode. The spine of lecture 2's second half:
  <https://developer.android.com/google/play/integrity/overview>

## The Keystore and encryption, deeper

- **"Verifying hardware-backed key pairs with key attestation"** — what an attestation certificate proves and how a backend verifies it:
  <https://developer.android.com/privacy-and-security/security-key-attestation>
- **Google Tink** — the crypto library that backs Jetpack Security; the modern direct-use path when you outgrow `EncryptedSharedPreferences`:
  <https://github.com/tink-crypto/tink-java>
- **"Cryptography" best practices (Android)** — AEAD, key rotation, the don't-roll-your-own-crypto rules:
  <https://developer.android.com/privacy-and-security/cryptography#choose-algorithm>

## Certificate pinning — the footgun and the safe path

- **OWASP "Certificate and Public Key Pinning"** — the canonical treatment of pinning, including the rotation hazard:
  <https://owasp.org/www-community/controls/Certificate_and_Public_Key_Pinning>
- **"Pinning failures and how to avoid bricking your app"** — read OkHttp's `CertificatePinner` Javadoc and the backup-pin guidance; the SPKI-hash approach:
  <https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/>
- **mitmproxy docs** — the free MITM proxy you'll use to *prove* pinning works (the app should refuse to connect through it):
  <https://docs.mitmproxy.org/stable/>

## Play Integrity and the SafetyNet migration

- **"Migrate from SafetyNet Attestation to Play Integrity."** Why SafetyNet was shut down and what to move — read this even if you never used SafetyNet, to understand the deadline teams missed:
  <https://developer.android.com/google/play/integrity/migrate>
- **"Request an integrity verdict"** — `IntegrityManagerFactory`, standard vs classic requests, the nonce:
  <https://developer.android.com/google/play/integrity/standard>
- **"Decode and verify the integrity verdict"** — the server-side decode, the app/device/account verdict groups, the Play API:
  <https://developer.android.com/google/play/integrity/verdicts>

## The post-Android-13 permission model

- **"Permissions on Android" + "Notification runtime permission"** — `POST_NOTIFICATIONS`, granular media permissions, requesting least-privilege in context:
  <https://developer.android.com/guide/topics/permissions/overview>
  <https://developer.android.com/develop/ui/views/notifications/notification-permission>

## Read at the source

You learn more from one hour reading real hardening code than three hours of docs:

- **`google/tink` Android examples** — real AEAD encrypt/decrypt with a Keystore-wrapped key:
  <https://github.com/tink-crypto/tink-java/tree/main/examples>
- **`square/okhttp` `CertificatePinner`** — read the implementation and Javadoc to understand SPKI hashing and the backup-pin semantics:
  <https://github.com/square/okhttp/blob/master/okhttp/src/main/kotlin/okhttp3/CertificatePinner.kt>
- **`android/identity-samples` / the Play Integrity sample** — the official end-to-end attestation flow:
  <https://github.com/android/identity-samples>

## Talks (free, watch in this order)

- **"Security on Android"** (Google I/O, the current year's security session) — the platform primitives and the threat model:
  <https://www.youtube.com/results?search_query=google+io+android+security>
- **"Play Integrity API deep dive"** — the verdict, the backend decode, fail-closed in practice:
  <https://www.youtube.com/results?search_query=play+integrity+api+android>
- **"Mobile app pinning done right"** — the rotation footgun and the safe strategy, from the security community:
  <https://www.youtube.com/results?search_query=mobile+certificate+pinning+done+right>

## Tools you'll use this week

- **mitmproxy** (`brew install mitmproxy`, or `pip install mitmproxy`) — the free MITM proxy. Route the emulator through it (proxy settings) to *prove* an unpinned app's traffic is readable and a pinned app's is refused.
- **`adb pull`** — extract a file off the device/emulator to confirm encrypted-at-rest bytes are ciphertext: `adb pull /data/data/<pkg>/shared_prefs/<file>.xml`.
- **`keytool` / `openssl`** — compute an SPKI SHA-256 pin from a server certificate: `openssl s_client -connect host:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64`.
- **A Play Console internal-track build** (Week 21) — Play Integrity requires the app be known to Play; the internal-track upload is the clean way to test attestation.
- **A Google Cloud project** (free) — Play Integrity's server-side decode uses a project + API enablement.

## Free books and codelabs (chapter-level, not whole books)

- **"Secure your app data" pathway / codelabs** — a free guided build of EncryptedSharedPreferences and Keystore usage:
  <https://developer.android.com/courses>
- **The Play Integrity quickstart** — a runnable end-to-end attestation sample:
  <https://developer.android.com/google/play/integrity/setup>

## Paid books (optional, clearly marked)

- **"Android Security Internals" — Nikolay Elenkov (No Starch)** (paid). Older but still the definitive deep dive on the Keystore, the permission model, and the platform's security architecture. The Keystore chapter is gold.
- **"Bulletproof Android" — Godfrey Nolan** (paid). Practical app-hardening patterns; some chapters predate Play Integrity, so cross-check the attestation material against the current docs.

---

*If a link 404s, please open an issue so we can replace it.*
