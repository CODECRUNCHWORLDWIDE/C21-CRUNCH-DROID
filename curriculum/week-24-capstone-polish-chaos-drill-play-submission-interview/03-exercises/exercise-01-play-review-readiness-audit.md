# Exercise 1 — Play review readiness audit

**Goal.** Audit the capstone against the *actually-enforced* Play policies before you submit, so you land on the first attempt instead of resubmitting Thursday. This is Lecture 1 §1–2 and §6 made into a row-by-row gate you can check.

**Estimated time.** 40 minutes.

**Prerequisites.** The locked `v1.0.0-rc1` AAB from Week 23 on the internal track, your `libs.versions.toml`, your `AndroidManifest.xml`, and a Play Console account with the app record. No code change — this is an audit that produces `docs/play-readiness-audit.md`.

---

## Why this exists

Play review rejects far more capstones for *declaration mismatches* than for hard policy violations. The Data Safety form says "no data collected" while the app bundles Firebase; a foreground service has no justified type; an unused `ACCESS_FINE_LOCATION` lingers from a tutorial. Every one is a five-minute fix before submission and a multi-day rejection after. This audit walks every row that actually rejects apps, so you walk in clean.

## Step 1 — Map every SDK to its data declaration

Open `libs.versions.toml` and list every dependency that touches the network or collects anything. For each, write what data it collects or sends, because the Data Safety form is a *function of this list* (Lecture 1 §2):

| SDK | Data it collects / sends | Data Safety declaration |
|-----|--------------------------|-------------------------|
| Firebase Cloud Messaging | a registration token (device identifier) | Device or other IDs — collected; shared with Google |
| Crashlytics (if present) | crash stacks, device model, OS version | Crash logs, diagnostics — collected; shared with Google |
| Play Integrity client | talks to Google for the verdict | (Google-internal; declare per the SDK guidance) |
| your gRPC backend | the dispatch data the worker enters | App activity — collected; NOT shared (first-party) |

If a row's data is one you do not want to declare, the fix is to **remove the SDK**, not to under-declare. Review's automated scanning finds the SDK regardless.

## Step 2 — Audit the manifest permissions

Open `AndroidManifest.xml` (merged). For every `<uses-permission>`, confirm the app actually *uses* it and that sensitive ones are justified:

- `INTERNET` — used (gRPC). Fine.
- `POST_NOTIFICATIONS` — used (FCM, the ongoing activity). Fine; runtime-requested on Android 13+.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` — used (the sync promotion). **Needs a Play justification** (Step 3).
- `ACCESS_FINE_LOCATION` / `READ_MEDIA_*` / `MANAGE_EXTERNAL_STORAGE` — if present and unused, **remove them**. An unused sensitive permission is a rejection.
- `SCHEDULE_EXACT_ALARM` — the capstone should NOT need this (sync is WorkManager, not an exact alarm). If it's there, remove it.

Record any permission you cannot justify as a FAIL with a removal plan.

## Step 3 — Justify the foreground service

Your `:feature-sync` promotes to a foreground service mid-sync. Write the justification Play asks for at submission (Lecture 1 §1):

> Type: `dataSync`. Justification: when a user opens the app while a sync is in progress, we promote the sync to a foreground service so the OS does not kill it before the user's pending dispatch updates reach the server. It runs only while a sync is active and is dismissed on completion. It is not used for deferrable background work (that uses WorkManager without promotion).

If you cannot write a true justification, the service type is wrong — fix the code, not the form.

## Step 4 — The account-deletion path

The capstone has accounts (the Play Integrity sign-in gate implies one). Play requires an in-app account-deletion path *and* a deletion URL. Confirm:

- [ ] There is an in-app "delete account" action that deletes the user's server-side data.
- [ ] There is a publicly reachable deletion-request URL in the store listing.

A missing deletion path is a guaranteed rejection for an app with accounts.

## Step 5 — Fill the audit table

Produce `docs/play-readiness-audit.md` with every row from Lecture 1 §6, each PASS/FAIL with one line of evidence:

```text
PLAY REVIEW READINESS AUDIT — Field-Force Companion v1.0.0

  [PASS] targetSdk 35 meets the Play floor.            evidence: app/build.gradle.kts
  [PASS] Data Safety mapped from every SDK; no mismatch. evidence: Step 1 table
  [PASS] Account deletion path + URL present.          evidence: SettingsScreen + URL
  [PASS] Foreground-service type dataSync justified.    evidence: Step 3 text
  [FAIL] ACCESS_FINE_LOCATION declared but unused.      plan: remove before submit
  [PASS] Privacy policy URL resolves.                   evidence: <url>
  [PASS] Store screenshots match the build (phone+Wear). evidence: listing
  [PASS] No crash in the release-variant smoke.         evidence: CI run <link>
  [PASS] Signed v1.0.0-rc1; upload key fingerprint OK.  evidence: console
  [PASS] No credentials in repo; no token logging.      evidence: grep clean
```

Every FAIL is your pre-submission homework. Clear them all, then submit Monday.

---

## Acceptance criteria

- [ ] `docs/play-readiness-audit.md` exists with every Lecture 1 §6 row, each PASS/FAIL with evidence.
- [ ] Every SDK is mapped to a Data Safety declaration (Step 1 table); no mismatch remains.
- [ ] Every manifest permission is justified or removed; no unused sensitive permission.
- [ ] The foreground-service type is justified in writing.
- [ ] The account-deletion path and URL are confirmed present.
- [ ] Any FAIL has a removal/fix plan executed before submission.

## What you just proved

You proved you can walk into Play review clean — by auditing the declarations that actually reject apps (Data Safety, foreground service, permissions, account deletion) against the real code, not from memory. This is the difference between a Monday pass and a Thursday resubmission, and it is the same readiness discipline a senior engineer runs before every production release.

---

## Hints (read only if stuck > 10 min)

- **"Do I really collect data? It's my backend."** Collecting means the data leaves the device. Dispatch data going to your backend is *collected* (declare it) but not *shared* (your backend is first-party). Crashlytics data going to Google is *shared*.
- **"Why remove an unused permission instead of leaving it?"** An unused sensitive permission is both a rejection risk and a real privacy/attack-surface cost. The merged manifest is what review sees; check it, not just your module's manifest.
- **Can't find the merged manifest.** `Build ▸ Analyze APK` on the AAB, or `app/build/intermediates/merged_manifests/`. Library modules and SDKs contribute permissions you didn't write.
- **Foreground service justification feels hand-wavy.** If you can't write a true one-sentence justification for why the service must run *now* and *user-visibly*, the work is deferrable — use WorkManager without promotion, and drop the foreground-service permission entirely.
