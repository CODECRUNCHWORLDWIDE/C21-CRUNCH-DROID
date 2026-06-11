# Week 24 — Resources

Every primary resource on this page is **free**. The Play Console policy and submission docs are free. The chaos-engineering and postmortem references are free. The interview-prep material is built from primary Android docs, not paid courses. The only paid item is the USD 25 Play developer fee, and the capstone accepts an F-Droid no-fee fallback.

## The capstone specification (read this first, every time)

- **The Field-Force Companion brief** — the source of truth for the capstone, including the required deliverables and the **chaos-drill menu (all three required this week)**:
  [`SYLLABUS.md` § Capstone · Field-Force Companion](../../SYLLABUS.md)
- **The career engineering pack** — the six interview drills, the production runbook, the portfolio, and the four mock interviews you deliver this week:
  [`SYLLABUS.md` § Career engineering pack](../../SYLLABUS.md)

## Play Console submission and review

- **"Prepare and roll out a release"** — the closed-track flow, the release dashboard, and the staged rollout:
  <https://support.google.com/googleplay/android-developer/answer/9859348>
- **"Test your app — internal, closed, and open testing"** — the track taxonomy; the closed track this week's submission lands on:
  <https://support.google.com/googleplay/android-developer/answer/9845334>
- **"Provide information for Google Play's Data safety section"** — the Data Safety form; the label must match what your code collects and shares:
  <https://support.google.com/googleplay/android-developer/answer/10787469>
- **"Use of the foreground service permissions"** — the foreground-service-type justification your sync's foreground-promotion path needs at submission:
  <https://support.google.com/googleplay/android-developer/answer/13392821>
- **"Meet Google Play's target API level requirement"** — the targetSdk floor enforced at submission:
  <https://support.google.com/googleplay/android-developer/answer/11926878>
- **"Developer Program Policies"** — the policy surface review enforces (deceptive behavior, permissions, families, data):
  <https://play.google.com/about/developer-content-policy/>
- **Play App Signing** — Google holds the app-signing key; you hold the upload key:
  <https://developer.android.com/studio/publish/app-signing#app-signing-google-play>

## F-Droid (the no-fee fallback)

- **"Submitting to F-Droid"** — the metadata, the reproducible-build expectations, and the merge-request flow for the no-fee capstone path:
  <https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start/>

## Chaos engineering and the postmortem

- **"Principles of Chaos Engineering"** — the discipline: a hypothesis about steady state, an injected real failure, measured behavior:
  <https://principlesofchaos.org/>
- **Google SRE Book — "Postmortem Culture: Learning from Failure"** — the blameless postmortem structure the capstone postmortems follow:
  <https://sre.google/sre-book/postmortem-culture/>
- **Google SRE — example postmortem** — a worked example with timeline, root cause, and action items:
  <https://sre.google/sre-book/example-postmortem/>

## FCM (drill B — token rotation)

- **"Firebase Cloud Messaging — Android client"** — receiving messages, the `FirebaseMessagingService`, and `onMessageReceived`:
  <https://firebase.google.com/docs/cloud-messaging/android/client>
- **"Manage registration tokens" / `onNewToken`** — the token-rotation callback and the server re-registration path drill B exercises:
  <https://firebase.google.com/docs/cloud-messaging/android/client#monitor-token-generation>
- **"About FCM messages"** — data vs notification messages, and why a data message during rotation can be silently dropped if re-registration lags:
  <https://firebase.google.com/docs/cloud-messaging/concept-options>

## Play Integrity (drill C — attestation failure)

- **"Play Integrity API overview"** — the attestation flow and the verdict your sign-in gates on:
  <https://developer.android.com/google/play/integrity/overview>
- **"Error codes and troubleshooting"** — what an attestation failure looks like, including the no-Play-Services case drill C drives:
  <https://developer.android.com/google/play/integrity/error-codes>

## Senior-Android interview prep (the six drills)

The six syllabus drills, each grounded in a primary source you should be able to cite:

- **Compose recomposition phases and stability** — explain to a staff engineer:
  <https://developer.android.com/develop/ui/compose/phases> · <https://developer.android.com/develop/ui/compose/performance/stability>
- **Coroutines pitfalls** — three real production bugs and the fix for each (cancellation, exception propagation, `GlobalScope`):
  <https://kotlinlang.org/docs/coroutines-guide.html> · <https://kotlinlang.org/docs/exception-handling.html>
- **Cold vs hot flows** — when to pick which:
  <https://kotlinlang.org/docs/flow.html> · <https://developer.android.com/kotlin/flow/stateflow-and-sharedflow>
- **WorkManager vs foreground service vs exact alarm** — a design exercise:
  <https://developer.android.com/topic/libraries/architecture/workmanager> · <https://developer.android.com/develop/background-work/services/foreground-services>
- **Mobile system design** — design WhatsApp's message-send pipeline (offline queue, retries, delivery receipts, FCM):
  <https://developer.android.com/topic/architecture/data-layer/offline-first>
- **Memory and ANR debugging** — read a stack trace and isolate the bug:
  <https://developer.android.com/topic/performance/vitals/anr> · <https://developer.android.com/studio/profile/memory-profiler>

## The production runbook (Crashlytics, vitals, rollout halt)

- **"Android vitals"** — the Play Console health metrics an on-call rotation watches (ANR rate, crash rate, excessive wakeups):
  <https://developer.android.com/topic/performance/vitals>
- **"Firebase Crashlytics"** — crash triage and the velocity alerts:
  <https://firebase.google.com/docs/crashlytics>
- **"Halt or resume a staged rollout"** — the rollout halt criteria your runbook documents:
  <https://support.google.com/googleplay/android-developer/answer/6346149>

## Tools you'll use this week

- **The Play Console** — the closed track, the Data Safety form, the pre-launch report (which runs your app on real devices and flags crashes/accessibility before review).
- **`bundletool`** — generate the exact APKs Play will serve from your AAB, to test the real artifact:
  <https://developer.android.com/tools/bundletool>
- **Two emulators + a Play-Services-less emulator** — Pixel 8 API 35, Wear OS API 34, and an AOSP (no Google APIs) image for drill C.
- **`adb`** — drive the chaos drills: `adb shell svc data disable/enable` (network for drill A), force-stop and FCM test sends (drill B).
- **A screen recorder** — `adb shell screenrecord` or the studio recorder, two devices side by side, for the five-minute walkthrough.

## Career-pack references

- **"Tech Interview Handbook"** — the behavioral and system-design framing (free, open source):
  <https://www.techinterviewhandbook.org/>
- **Now-in-Android as a portfolio reference** — the bar a polished public Android project clears:
  <https://github.com/android/nowinandroid>

---

*If a link 404s, please open an issue so we can replace it.*
