# Week 16 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 16 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Android Studio Ladybug+, JDK 17, AGP 8.x, Kotlin 2.0+, compileSdk 35, **targetSdk 35** (so Android 14 rules apply), minSdk 24. Every build must complete with **0 warnings**.

---

## Problem 1 — The decision framework, applied

**Problem statement.** For each of these eight tasks, write into `notes/decisions.md` which tool you'd use (WorkManager / foreground service / exact alarm) and one sentence of justification against the power rules: (a) upload a queue of analytics events; (b) play a podcast in the background; (c) fire an alarm at the user-set wake time; (d) refresh a news feed roughly hourly; (e) track a run with live GPS the user is watching; (f) download a large file over Wi-Fi only; (g) remind the user to take medication at 9:00 AM that they configured; (h) back up photos when charging overnight.

**Acceptance criteria.**

- All eight tasks classified with the right tool and a power-rules justification.
- At least one task where the *naive* instinct (exact alarm) is wrong and WorkManager is right (e.g. d).
- Committed.

**Hint.** Deferrable+durable → WorkManager (a, d, f, h). User-aware+ongoing → foreground service (b, e). User-chosen exact moment → exact alarm (c, g). The trap is (d) — "hourly" is deferrable, not exact. Lecture 2, §1.

**Estimated time.** 30 minutes.

---

## Problem 2 — Constraints and the pipeline

**Problem statement.** Schedule three one-time workers with different constraints: (a) `CONNECTED`, (b) `UNMETERED`, (c) `requiresCharging(true)`. With the device on cellular and unplugged, predict which run and which stay ENQUEUED, then confirm in the Background Task Inspector and `adb shell dumpsys jobscheduler`. Record predictions-then-results in `notes/constraints.md`.

**Acceptance criteria.**

- Three constrained workers; a written prediction before running.
- Confirmed: (a) runs (cellular is "connected"); (b) stays ENQUEUED (no Wi-Fi); (c) stays ENQUEUED (not charging).
- `dumpsys jobscheduler` evidence pasted for at least one pending job.
- 0 warnings. Committed.

**Hint.** `CONNECTED` accepts cellular; `UNMETERED` needs Wi-Fi; `requiresCharging` needs a plug. The inspector's Constraints panel shows which are pending. Lecture 1, §3.

**Estimated time.** 45 minutes.

---

## Problem 3 — Backoff and retry semantics

**Problem statement.** Write a worker that returns `retry()` for the first three attempts then `success()`, with `EXPONENTIAL` backoff (initial 10s). Log each attempt's timestamp and `runAttemptCount`. Run it and record the actual delays between attempts into `notes/backoff.md`; confirm they roughly double. Then change one worker to cap at `runAttemptCount > 2` with `failure()` and confirm it gives up.

**Acceptance criteria.**

- One worker shows roughly-doubling delays (10s, 20s, 40s) across retries, logged with timestamps.
- A second worker caps retries via `runAttemptCount` and ends FAILED.
- `notes/backoff.md` records the measured delays and the cap behavior.
- 0 warnings. Committed.

**Hint.** `setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)`. `runAttemptCount` starts at 0. Read timestamps in Logcat — the delays are real (no test driver here). Lecture 1, §5.

**Estimated time.** 45 minutes.

---

## Problem 4 — An Android-14-compliant foreground worker

**Problem statement.** Write a `CoroutineWorker` that promotes to the foreground with a `dataSync` type and a progress notification. First *omit* the manifest `FOREGROUND_SERVICE_DATA_SYNC` permission/type and observe the crash on targetSdk 35; then add them and observe it works. Document the before/after in `notes/foreground.md`.

**Acceptance criteria.**

- A reproduction: without the declared type/permission, `setForeground` crashes on Android 14.
- With the type + permission declared and passed to `ForegroundInfo`, it runs and shows the notification.
- `notes/foreground.md` quotes the crash and explains the Android 14 requirement.
- 0 warnings on the fixed build. Committed.

**Hint.** Exercise 2 has the full setup. The crash is a `MissingForegroundServiceTypeException`/`SecurityException`. You also need `POST_NOTIFICATIONS` granted on Android 13+. Lecture 2, §2.

**Estimated time.** 50 minutes.

---

## Problem 5 — Exact alarm: gate it, then replace it

**Problem statement.** Implement a "reminder at a user-chosen time" with `setExactAndAllowWhileIdle`, correctly gated by `canScheduleExactAlarms()` with a graceful fallback when the permission isn't granted. Then write a second version of a *different* feature ("refresh every 2 hours") that you initially tried as an exact alarm and *correctly* reimplement as periodic WorkManager. Document both in `notes/alarms.md`.

**Acceptance criteria.**

- The reminder checks `canScheduleExactAlarms()` and falls back gracefully (never assumes the permission).
- The "refresh every 2 hours" is implemented as periodic WorkManager (not an exact alarm), with a written justification (it's deferrable, the user didn't pick a clock time).
- `notes/alarms.md` contrasts the two: when an exact alarm is justified vs when it's over-powered.
- 0 warnings. Committed.

**Hint.** `if (alarmManager.canScheduleExactAlarms()) { setExactAndAllowWhileIdle(...) } else { fallback }`. The refresh is deferrable → `PeriodicWorkRequest`. Lecture 2, §1, §3.

**Estimated time.** 45 minutes.

---

## Problem 6 — A deterministic WorkManager test

**Problem statement.** Write a Robolectric test using `WorkManagerTestInitHelper` + `SynchronousExecutor` + `TestDriver` that proves a constrained worker (a) does NOT run before constraints are met, (b) runs and reaches SUCCEEDED after `setAllConstraintsMet`, and (c) a worker returning `retry()` re-enqueues. No `Thread.sleep`.

**Acceptance criteria.**

- The test asserts the worker hasn't run before `setAllConstraintsMet`, and SUCCEEDED after.
- A second case drives a worker that returns `retry()` and asserts the re-enqueue/retry state.
- Zero `Thread.sleep`, zero flakiness; both cases green.
- `notes/testing.md` explains why `SynchronousExecutor` + `TestDriver` make it deterministic.
- 0 warnings. Committed.

**Hint.** Exercise 3 is the template. `SynchronousExecutor` runs work on the test thread; `setAllConstraintsMet(id)` stands in for the constraint actually being satisfied. Lecture 1, §7.

**Estimated time.** 45 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, code is idiomatic WorkManager/Kotlin, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. `retry()` returned for a permanent failure, a missing `runAttemptCount` cap, non-unique periodic scheduling). |
| 3 | Works, but misses one criterion (e.g. foreground worker runs but the Android-14 crash wasn't reproduced/explained; backoff used but delays not measured). |
| 2 | Partially works; a core idea is wrong (uses an exact alarm for deferrable work; claims WorkManager runs at an exact time; foreground service without the type). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for reaching for an exact alarm or a battery-optimization exemption where deferrable WorkManager was correct (the "least power" violation); **−2** for a foreground service that crashes on Android 14 (missing type/permission); **−1** for non-unique periodic scheduling that would pile up duplicate work.

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — the constraint/backoff pipeline (problems 2, 3, 6) and the decision framework / Android-14 compliance (problems 1, 4, 5) — so re-run exercises 01 and 03 before resubmitting.
