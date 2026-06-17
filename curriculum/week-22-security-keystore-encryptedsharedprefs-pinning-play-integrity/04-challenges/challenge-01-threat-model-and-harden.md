# Challenge 1 — Threat-model and harden a feature

**Time.** 90–150 minutes.
**Deliverable.** A `THREAT-MODEL.md` (assets, adversaries, attacks, mitigations), the hardened code for one feature, and a `PROOF.md` showing each control defeated an actual attack attempt (with screenshots: `adb pull` ciphertext, a refused MITM proxy, a graceful no-Play-Services denial). Committed to your Week 22 repo.

## The premise

Junior security work is a checklist: "I added EncryptedSharedPreferences, I added pinning, done." It's brittle because it's untethered from a threat model — you can't say *what* you're defending against, so you can't say whether you succeeded, and you can't tell which controls are theater. The skill this challenge builds is the opposite: **name the assets, name the adversary and the attack for each, choose a control per threat, and then *try to break each control with the attacker's tools* and show it holds.** A security control you haven't attacked is a control you don't know works. A threat model you can defend in a review is engineering; a checklist is hope.

You will threat-model and harden one realistic feature — a **sign-in + offline-sync flow** — and the grading is the *coherence* of the model (does each control address a named threat?) plus the *proof* (did you actually try to defeat each one?).

## What to build

### Step 1 — The feature (the thing under threat)

A small but realistic flow with genuine secrets and a real network call:

- **Sign-in** that obtains an auth token from a backend (a fake/stub backend is fine).
- **Token storage** — the token is persisted so the user stays signed in.
- **Sync** — an authenticated API call (the Week-15 weather client, or any HTTPS endpoint) that sends the token.

Build the *insecure* baseline first (plaintext token, no pinning, no attestation) so you have a "before" to attack.

### Step 2 — `THREAT-MODEL.md`

The graded artifact. For each **asset**, name the **adversary**, the **attack**, and the **mitigation**:

| Asset | Adversary | Attack | Mitigation |
|---|---|---|---|
| Auth token at rest | Attacker with the device (rooted / backup / forensic) | `adb pull` / extract `shared_prefs/auth.xml` → read token | Keystore-backed `EncryptedSharedPreferences` (hardware key, ciphertext on disk) |
| Token / API data in transit | Attacker with a user-installed CA (proxy) | MITM the HTTPS connection → read & modify traffic | OkHttp `CertificatePinner` (refuse forged certs) + exclude user CAs in release |
| Backend request authenticity | Attacker running a script / modified APK | Replay/forge API calls without the real app | Play Integrity attestation; backend decodes verdict + nonce |
| Over-permission surface | Any | Excess permissions widen the attack surface / leak data | Least-privilege: runtime `POST_NOTIFICATIONS`, granular media, request-in-context |

Add, for each row, **what the control does NOT protect against** (e.g. encryption-at-rest doesn't protect a compromised running process; pinning doesn't help if you ship no backup pin and the cert rotates). Honesty about limits is part of the grade.

### Step 3 — Harden, control by control

Implement each mitigation:

- **Encrypted token storage** (exercise 1): a `SecureTokenStore` with a Keystore master key; migrate and clear any legacy plaintext.
- **Certificate pinning** (exercise 2): an OkHttp `CertificatePinner` with a current pin *and* a backup pin, plus a network security config that excludes user CAs in release and trusts them only in `<debug-overrides>`.
- **Play Integrity** (exercise 3): a sign-in gate that requests a token with a nonce, forwards it to the backend, and **fails closed gracefully** when integrity is unavailable.
- **Least-privilege permissions**: request `POST_NOTIFICATIONS` at runtime in context; use granular media permissions (or the Photo Picker) if you touch media; don't over-ask at launch.

### Step 4 — `PROOF.md` — attack each control

This is what separates senior from checklist. For each control, *try to defeat it* and show it holds:

- **Encrypted storage:** `adb pull` (or `run-as cat`) the store before (readable token) and after (ciphertext). Screenshot both.
- **Pinning:** route the app through mitmproxy with the proxy's CA installed. Before pinning: the proxy *reads* your traffic (screenshot the flow). After pinning: the app throws `SSLPeerUnverifiedException` and refuses (screenshot the failure). 
- **Play Integrity:** run on an emulator *without* Google Play Services. Show the sign-in fails *closed and gracefully* — a clear message and a fallback, not a crash, not a silent allow. Screenshot the denial UI.
- **Permissions:** show the app functions when a permission is denied (graceful degradation), and that it doesn't request permissions it doesn't use.

## Acceptance criteria

- [ ] An insecure baseline feature exists (so the "before" attacks succeed).
- [ ] `THREAT-MODEL.md` names asset / adversary / attack / mitigation for each asset, *and* the limit of each control.
- [ ] Token storage is Keystore-backed encrypted; legacy plaintext migrated and cleared.
- [ ] The API client pins with a current *and* backup pin; the network security config excludes user CAs in release (debug-only override for the proxy).
- [ ] Sign-in is gated on Play Integrity and **fails closed gracefully** (message + fallback) when integrity is unavailable.
- [ ] Permissions are least-privilege and requested in context; the app degrades gracefully on denial.
- [ ] `PROOF.md` shows each control *attacked* and *holding*: `adb` ciphertext, a refused MITM proxy, a graceful no-Play-Services denial. Screenshots included.
- [ ] No real secret committed (no keystore, no service-account JSON, no token in the repo).
- [ ] Build with **0 warnings**.

## What "great" looks like

A weak submission says "I added encryption, pinning, and Play Integrity." A great submission says:

> The threat model names three assets and their adversaries: the auth token at rest (attacker with the device → `adb pull` the prefs), the traffic in transit (attacker with a user-installed CA → MITM proxy), and the request authenticity (attacker with a script/modified APK → forged API calls). For each I chose one control and *proved it holds*. Encryption: `run-as cat shared_prefs/secure_auth.xml` shows base64 ciphertext where `auth.xml` previously showed the JWT verbatim — the master key is in the Keystore, so even rooting won't extract it; this protects data at rest but *not* a compromised running process, which I note. Pinning: through mitmproxy with its CA installed, the unpinned build's traffic was fully readable in the proxy window, and the pinned build threw `SSLPeerUnverifiedException` and refused to connect — I pinned the intermediate and shipped a backup pin so a leaf rotation won't brick the app. Play Integrity: on an emulator without Play Services, sign-in returns `SignInGate.Blocked` with a clear message and a web-sign-in fallback, logged — it fails closed, gracefully, never open, exactly the capstone's chaos drill #3. The verdict is decoded on my (stub) backend, never the client, and bound to a server nonce. Permissions are least-privilege: I request `POST_NOTIFICATIONS` only when enabling alerts, and the app works if it's denied.

A coherent model, a control per threat, honest limits, and proof you tried to break each one. That's the senior security answer.

## Where this reappears

This is the capstone's security spine. `:feature-auth` requires "Play Integrity attestation at sign-in, Keystore-backed token storage" — exactly steps 3 and 1 here. `:core-network` requires "certificate pinning with structured retry" — step 2. And chaos drill #3 *is* the no-Play-Services graceful-denial proof from your `PROOF.md`. Doing this challenge well means the capstone's hardest security requirements are already designed, built, and *proven* — you'll be transplanting working, tested controls, not inventing them under capstone deadline pressure.
