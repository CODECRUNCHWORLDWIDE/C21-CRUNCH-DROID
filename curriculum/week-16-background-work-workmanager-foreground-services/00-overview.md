# Week 16 — Background work: WorkManager, foreground services, exact alarms

Welcome to Week 16 of **C21 · Crunch Droid**, deep in Phase 3. You can now build a Compose UI, inject your dependencies with Hilt, persist with Room and DataStore, and talk to a backend with Retrofit and Ktor. This week answers the question those skills inevitably raise: **how do you do work when the user isn't looking?** Sync data while the app is backgrounded. Upload a queued photo when the network comes back. Fire a reminder at exactly 8:00 AM. On a desktop you'd spawn a thread and forget about it. On Android, background work is — in the lecture's phrase — **a regulated industry**, and this week is about working *within* the regulations instead of fighting them.

The mental shift this week is from "I'll run this in the background" to "the OS decides when, and increasingly whether, my background work runs at all — my job is to express my requirements precisely enough that the OS runs my work at the right time, and to never ask for more power than I need." Every version of Android since Marshmallow has tightened the screws: **Doze** mode suspends background work when the device is idle, **App Standby** throttles apps the user ignores, the **foreground service** types were locked down hard in Android 14, and **exact alarms** became a privileged, permission-gated capability in Android 12 that most apps should never request. A senior Android engineer doesn't memorize these rules as trivia — they carry a *decision framework*: given a piece of deferrable work, which tool, with which constraints, respecting which power regime.

The thing this week hammers on is **the WorkManager constraint pipeline — "why your work did not run."** The single most common WorkManager support ticket is "I scheduled a periodic sync and it's not firing." Beginners assume WorkManager is a timer; it is not. It is a *constraint satisfaction engine* that runs your work when the device state matches the constraints you declared (network available, battery not low, charging) *and* the OS's power policy permits it (not in deep Doze, not standby-throttled). We trace that pipeline from your `WorkRequest` through the `ConstraintsTracker` to the actual execution, so that when work doesn't run, you can *reason* about why — wrong constraint, Doze window, standby bucket, a missing expedited quota — instead of sprinkling retries and hoping. The skill this week earns is WorkManager fluency *end to end*: one-time and periodic work, constraints, backoff, unique work, chaining, expedited jobs, and testing it deterministically with `WorkManagerTestInitHelper`.

We pair that with the two *other* background tools and — crucially — **when to reach for each**. **Foreground services** are for work the user is actively aware of *right now* (a music player, an active navigation, a live workout) — they show a persistent notification and survive Doze, but Android 14 requires you to declare a *foreground service type* and hold the matching permission, and getting it wrong crashes your app at `startForeground`. **Exact alarms** (`AlarmManager.setExactAndAllowWhileIdle`) are for the rare case where work must happen at a precise wall-clock time the user chose — an alarm clock, a medication reminder, a calendar event — and after Android 12 they require the `SCHEDULE_EXACT_ALARM` permission that Google Play scrutinizes. The senior judgment this week installs is knowing that **most "I need an exact alarm" instincts are wrong** — the right tool is usually deferrable WorkManager work, and reaching for an exact alarm (or a battery-optimization exemption) when you don't truly need it is a code-review red flag and a Play policy risk.

The week's mini-project is an **offline-first sync engine**: a periodic WorkManager job with `BackoffPolicy.EXPONENTIAL`, network-constraint-aware so it only runs when connected, with a **foreground-promotion** path so that if the user opens the app mid-sync the work surfaces a progress notification and keeps running, all backed by a full integration test using `WorkManagerTestInitHelper`. That sync engine is the spine of the capstone's `:feature-sync` module, and building it here — correctly, tested, compliant on Android 14+ — is the senior instinct this week installs: durable background work that respects the platform's power rules and crashes on no device.

## Learning objectives

By the end of this week, you will be able to:

- **Choose** the right background tool for a given task: deferrable durable work (WorkManager), user-aware ongoing work (foreground service), or precise wall-clock work (exact alarm) — and justify the choice against the power rules.
- **Build** WorkManager requests of every shape: `OneTimeWorkRequest`, `PeriodicWorkRequest`, expedited work, chained work, and unique work (`enqueueUniqueWork` / `enqueueUniquePeriodicWork`) with the right `ExistingWorkPolicy`.
- **Declare constraints** (network type, battery-not-low, charging, storage-not-low, device-idle) and explain how the constraint pipeline decides whether and when work runs.
- **Configure backoff** with `BackoffPolicy.EXPONENTIAL` vs `LINEAR`, return `Result.retry()`/`success()`/`failure()` correctly, and reason about why a failing worker re-runs when it does.
- **Promote a Worker to the foreground** with `setForeground`/`getForegroundInfo` and a `CoroutineWorker`, and declare the correct **foreground service type** and permission for Android 14+ compliance.
- **Implement and gate an exact alarm** with `AlarmManager.setExactAndAllowWhileIdle`, the `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` permissions, and `canScheduleExactAlarms()` — and articulate why you should almost never do this.
- **Reason about Doze and App Standby**: maintenance windows, standby buckets, what survives Doze (foreground services, high-priority FCM, `setExactAndAllowWhileIdle`) and what doesn't (ordinary background work), and why a battery-optimization exemption is a last resort you rarely earn.
- **Test background work deterministically** with `WorkManagerTestInitHelper`, the `SynchronousExecutor`, and `TestDriver` to drive constraints and delays in a JVM/Robolectric test.

## Prerequisites

This week assumes you have completed **C21 weeks 1–15**, or have equivalent fluency. Specifically:

- You have **coroutines and Flow** fluency — Weeks 4–5. `CoroutineWorker.doWork()` is a `suspend` function, `setForeground` is suspending, and you observe work state as a `Flow<WorkInfo>`. If structured concurrency and cancellation are shaky, re-read Week 4.
- You can **inject with Hilt** — Week 13. Real workers take dependencies (a repository, an API client), which means `@HiltWorker` with `HiltWorkerFactory`. We use that pattern; the Hilt mechanics are assumed.
- You have **Room/DataStore persistence** — Week 14. The sync engine reads a local queue and writes synced state, so the worker talks to a DAO. The persistence is assumed; this week is about *when* the work runs, not the schema.
- You can do **networking with a typed result** — Week 15. The sync worker calls a repository that returns a sealed `NetworkResult`; a transient failure maps to `Result.retry()`. The networking is assumed.
- Your toolchain is the Phase-3 standard: Android Studio Ladybug+, the multi-module project, Hilt wired, compileSdk 35/36, targetSdk 35, minSdk 24.

**Toolchain.** Android Studio Ladybug (2024.2) or newer, JDK 17, AGP 8.x, Kotlin 2.0+. WorkManager `androidx.work:work-runtime-ktx` (2.9+), `work-testing` for the integration test, `androidx.hilt:hilt-work` for `@HiltWorker`. **targetSdk 35** is required to confront the Android 14 foreground-service-type rules honestly. A Pixel 8 API 35 emulator is the reference device; you will also test Doze behavior with `adb shell dumpsys deviceidle`.

## Topics covered

- **The background-work decision framework.** Deferrable + durable → WorkManager. User-aware + ongoing → foreground service. Precise wall-clock + user-chosen → exact alarm. Why "spawn a thread" is wrong on Android, and how the OS reclaims your process.
- **WorkManager fundamentals.** `Worker` vs `CoroutineWorker`, `WorkRequest`, `WorkManager.enqueue`, the `Result` (`success`/`failure`/`retry`), `inputData`/`outputData`, and observing state with `getWorkInfoByIdFlow`.
- **Constraints.** `Constraints.Builder` — `setRequiredNetworkType`, `setRequiresBatteryNotLow`, `setRequiresCharging`, `setRequiresStorageNotLow`, `setRequiresDeviceIdle` — and the constraint pipeline (`ConstraintsTracker`/`ConstraintController`) that gates execution.
- **Periodic, unique, chained, expedited work.** `PeriodicWorkRequest` (and the 15-minute minimum interval + flex), `enqueueUnique(Periodic)Work` with `ExistingWorkPolicy`/`ExistingPeriodicWorkPolicy`, `beginWith().then()` chains, and `setExpedited(OutOfQuotaPolicy)` for quota-limited expedited jobs.
- **Backoff and retry.** `setBackoffCriteria(BackoffPolicy.EXPONENTIAL, ...)` vs `LINEAR`, when to return `Result.retry()` (transient failure) vs `Result.failure()` (permanent), and how `runAttemptCount` drives the policy.
- **Foreground services.** The `Service` lifecycle, `startForeground`, the **foreground service type** taxonomy (`dataSync`, `mediaPlayback`, `location`, `connectedDevice`, ...), the Android 14 requirement to declare the type in the manifest and hold the matching permission, and WorkManager's `setForeground`/`getForegroundInfo` promotion path.
- **Exact alarms.** `AlarmManager.setExact` / `setExactAndAllowWhileIdle`, the Android 12 `SCHEDULE_EXACT_ALARM` permission and the Android 13 `USE_EXACT_ALARM` (calendar/alarm-clock) variant, `canScheduleExactAlarms()`, and the Play-policy scrutiny that makes this a last resort.
- **Doze and App Standby.** Doze maintenance windows, the App Standby buckets (active/working-set/frequent/rare/restricted), what's exempt during Doze, and why ordinary background work is deferred — plus battery-optimization exemptions (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and when *not* to ask.
- **Testing.** `WorkManagerTestInitHelper`, `SynchronousExecutor` for deterministic execution, `TestDriver.setAllConstraintsMet`/`setPeriodDelayMet`/`setInitialDelayMet` to drive the pipeline, and asserting on `WorkInfo` state.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                                | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|----------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | The decision framework; WorkManager fundamentals; constraints         |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | Periodic/unique/chained/expedited; backoff and retry; the pipeline    |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Foreground services; the Android 14 type taxonomy; promotion; footguns|    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | Exact alarms; Doze and App Standby; testing background work; challenge |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — offline-first sync engine: periodic + constraints       |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work — foreground promotion; the integration test    |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                           |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                      | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./00-overview.md) | This overview (you are here) |
| [resources.md](./01-resources.md) | The WorkManager, foreground-service, and exact-alarm docs, the Doze/App-Standby guides, the AOSP `JobScheduler`/`ConstraintsTracker` source, and the canonical background-work talks |
| [lecture-notes/01-workmanager-constraints-backoff.md](./02-lecture-notes/01-workmanager-constraints-backoff.md) | WorkManager end to end: workers, requests, constraints, the constraint pipeline, periodic/unique/chained/expedited work, backoff and retry, and deterministic testing |
| [lecture-notes/02-foreground-services-exact-alarms-doze.md](./02-lecture-notes/02-foreground-services-exact-alarms-doze.md) | Foreground services and the Android 14 type taxonomy, `setForeground` promotion, exact alarms and the Android 12 permission regime, Doze and App Standby, and the "which tool" decision |
| [exercises/README.md](./03-exercises/00-overview.md) | Index of the three exercises |
| [exercises/exercise-01-constraints-and-backoff.md](./03-exercises/exercise-01-constraints-and-backoff.md) | Schedule a network-constrained one-time worker with exponential backoff, observe its `WorkInfo` state, and force a `retry` to watch backoff fire |
| [exercises/exercise-02-coroutine-worker-foreground.kt](./03-exercises/exercise-02-coroutine-worker-foreground.kt) | Write a `CoroutineWorker` that promotes itself to the foreground with the correct `dataSync` type and a progress notification, Android-14-compliant |
| [exercises/exercise-03-workmanager-test.kt](./03-exercises/exercise-03-workmanager-test.kt) | Test a constrained periodic worker deterministically with `WorkManagerTestInitHelper` and `TestDriver` — drive the constraints, assert the state, no flakiness |
| [challenges/README.md](./04-challenges/00-overview.md) | Index of the challenge |
| [challenges/challenge-01-why-did-my-work-not-run.md](./04-challenges/challenge-01-why-did-my-work-not-run.md) | Diagnose four "my work didn't run" scenarios (wrong constraint, Doze, expedited quota exhausted, an exact alarm that should have been WorkManager), each traced to its cause with `adb`/`dumpsys` evidence |
| [quiz.md](./05-quiz.md) | 13 questions on the decision framework, constraints, backoff, foreground types, exact alarms, Doze, and testing |
| [homework.md](./06-homework.md) | Six practice problems for the week |
| [mini-project/README.md](./07-mini-project/00-overview.md) | Full spec for the offline-first sync engine: periodic + exponential backoff + network constraint + foreground promotion + a `WorkManagerTestInitHelper` integration test |

## The "least power that does the job" promise

Every week of Crunch Droid adds one production-grade contract a senior reviewer actually checks. This week's:

> **Use the least powerful background mechanism that satisfies the requirement, and never crash on Android 14+.** Deferrable work is WorkManager with constraints — not a foreground service, not an exact alarm. A foreground service is justified only when the user is actively aware of the work *right now*, and on Android 14+ it must declare its type and hold the matching permission or it crashes at `startForeground`. An exact alarm is justified only for precise, user-chosen wall-clock events, and after Android 12 it's permission-gated and Play-scrutinized. Reaching for more power than the task needs — an exact alarm for a sync, a battery-optimization exemption to "make sure it runs" — is a code-review rejection and a Play-policy risk.

You'll prove it in the mini-project: the sync is WorkManager (deferrable), it promotes to the foreground *only* when the user opens the app mid-sync (justified user-awareness) with the correct `dataSync` type, and it never touches an exact alarm or a battery exemption. "It runs" is not the test — the test is whether it runs *with the least power*, *within the power rules*, and *without crashing on a current-SDK device*.

## A note on what's not here

Week 16 is the *durable background work* week. It deliberately does **not** cover:

- **FCM (push) end to end.** High-priority FCM messages *can* wake an app from Doze (we mention it as a Doze exemption), but the full Firebase Cloud Messaging integration — token rotation, the data-vs-notification message split, the server side — is capstone material (the chaos drill). This week is about *local* scheduling and durable work.
- **The full networking stack.** The sync worker calls a repository that returns Week 15's sealed `NetworkResult`; we don't re-teach Retrofit/Ktor. Transient-vs-permanent failure mapping to `retry`/`failure` is the only networking concern here.
- **Conflict resolution.** The sync engine *uploads a queue*; the offline-edit conflict-resolution logic (two devices edit the same record) is the capstone's first chaos drill, not this week. We build the *transport*, not the merge.
- **Testing depth beyond WorkManager.** `WorkManagerTestInitHelper` is here; the full testing pyramid (Robolectric, Compose UI test, Paparazzi, Espresso) was Week 17's preview and is its own week. We test *that the work runs under the right conditions*, not the whole app.

## Up next

Continue to **Week 17 — Testing across every layer** once you have shipped the sync engine and its `WorkManagerTestInitHelper` integration test is green. Week 17 generalizes the deterministic-testing instinct you built here: the `TestDriver`-driven WorkManager test is one corner of a testing pyramid that spans JUnit 5, Turbine, Robolectric, Compose UI test, Paparazzi, and Espresso. Everything in Week 17 assumes you can already make asynchronous, OS-scheduled work *deterministic in a test* — the exact discipline this week's `WorkManagerTestInitHelper` work installed. And the sync engine itself returns in the capstone as `:feature-sync`, promoted to a real FCM-aware, conflict-resolving offline-first system. Earn the durable-work foundation here.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
