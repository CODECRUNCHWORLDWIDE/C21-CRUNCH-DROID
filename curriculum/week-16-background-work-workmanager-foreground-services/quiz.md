# Week 16 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 17. Answer key with explanations at the bottom — don't peek.

---

**Q1.** Which kind of work is WorkManager for?

- A) Work that must run at an exact wall-clock moment.
- B) **Deferrable + durable** work — must run eventually, can wait for good conditions, and must survive process death and reboot (sync, upload queue, cache refresh).
- C) Work the user is actively watching right now.
- D) Any work that takes longer than 5 seconds.

---

**Q2.** A `CoroutineWorker.doWork()` should return `Result.retry()` in which case?

- A) On any failure.
- B) On a **transient** failure (network blip, server 503) where re-running later (with backoff) could succeed — not on a permanent failure like a 400, which should be `Result.failure()`.
- C) When the work succeeds.
- D) Never; `retry()` is deprecated.

---

**Q3.** Your network-constrained worker sits in ENQUEUED and never runs. Most likely cause?

- A) WorkManager is broken.
- B) A constraint is unmet — e.g. you required `UNMETERED` (Wi-Fi) and the device is on cellular — or the device is in Doze. The constraint pipeline correctly holds the work until conditions are met. Read which constraint is pending in the inspector.
- C) You forgot to call `start()`.
- D) The worker threw an exception.

---

**Q4.** What's the minimum repeat interval for `PeriodicWorkRequest`, and is the interval exact?

- A) 1 second, exact.
- B) **15 minutes**, and the interval is **approximate** — the OS batches and Doze-defers it; periodic work is "roughly every N, eventually," never exact.
- C) 1 hour, exact.
- D) There's no minimum.

---

**Q5.** Why use `enqueueUniquePeriodicWork(name, KEEP, request)` instead of `enqueue`?

- A) It's faster.
- B) So scheduling on every app launch is **idempotent** — `KEEP` leaves the existing schedule alone, preventing duplicate parallel syncs from piling up (the classic battery-draining bug).
- C) `enqueue` doesn't work for periodic work.
- D) To run it expedited.

---

**Q6.** What does `BackoffPolicy.EXPONENTIAL` do, and when is it the right default?

- A) Runs the work twice as fast each time.
- B) Roughly **doubles the retry delay** each failed attempt (10s, 20s, 40s, ...) up to a cap — the right default for network work, so a struggling backend isn't hammered and the battery isn't drained.
- C) Retries instantly forever.
- D) Only retries once.

---

**Q7.** On Android 14, what must a foreground service do or it crashes?

- A) Nothing changed on Android 14.
- B) **Declare a `foregroundServiceType` in the manifest, hold the matching permission, and pass the type to `startForeground`/`ForegroundInfo`** — missing any of these throws at promotion. (e.g. `dataSync` needs `FOREGROUND_SERVICE_DATA_SYNC`.)
- C) Request the user's location.
- D) Run on the main thread.

---

**Q8.** The recommended way to do *user-aware data sync* with a notification is:

- A) A hand-written `Service` calling `startForeground`.
- B) WorkManager's **`setForeground`/`getForegroundInfo` promotion path** on a `CoroutineWorker` (with the `dataSync` type) — you get durability + constraints + a user-visible notification without writing a raw `Service`, and `dataSync` foreground services are time-limited anyway.
- C) An exact alarm.
- D) A plain background thread.

---

**Q9.** When is an exact alarm (`setExactAndAllowWhileIdle`) the right tool?

- A) For any periodic work.
- B) Only for **precise, user-chosen wall-clock moments** (an alarm clock, a medication reminder, a calendar event). It's permission-gated since Android 12 (`SCHEDULE_EXACT_ALARM`), Play-scrutinized, and a last resort — most "I need an exact alarm" instincts are deferrable work that should be WorkManager.
- C) Whenever you want work to run promptly.
- D) To bypass Doze for a sync.

---

**Q10.** On Android 13+, before calling `setExactAndAllowWhileIdle`, you must:

- A) Nothing; it always works.
- B) Check **`alarmManager.canScheduleExactAlarms()`** (the permission is denied by default for most apps and revocable), and fall back to an inexact alarm/WorkManager or send the user to grant it — never assume you have it.
- C) Request location permission.
- D) Call it on a background thread.

---

**Q11.** What happens to ordinary background work when the device enters Doze?

- A) It runs immediately.
- B) It's **deferred to the next maintenance window** (Doze batches deferred work into periodic windows that grow further apart) — by design, to save battery. Foreground services, `setExactAndAllowWhileIdle` alarms, and high-priority FCM are exempt.
- C) It's cancelled permanently.
- D) The app crashes.

---

**Q12.** An app the user hasn't opened in two weeks runs its sync only rarely. Why, and what should you do?

- A) A bug; file a WorkManager issue.
- B) **App Standby** has bucketed it as `rare`/`restricted`, heavily throttling its jobs and expedited quota — because the user isn't using it. You **don't fight this**; it reflects user behavior. A battery-optimization exemption would be a Play-policy risk, not a fix.
- C) Request `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for every app.
- D) Switch to an exact alarm.

---

**Q13.** How do you test that a constrained worker runs, deterministically and without flakiness?

- A) Add `Thread.sleep(5000)` and hope.
- B) Initialize WorkManager with `WorkManagerTestInitHelper` + a `SynchronousExecutor`, then use the `TestDriver` (`setAllConstraintsMet`, `setPeriodDelayMet`) to drive the constraint pipeline manually and assert on `WorkInfo` — no waiting, no real network.
- C) Run it on a physical device and watch.
- D) You can't test background work.

---

## Answer key

**Q1 — B.** WorkManager is for deferrable + durable work: it persists the request (surviving process death and reboot) and runs it when conditions allow. Exact-moment work is an alarm; user-watched work is a foreground service. (Lecture 1, §1.)

**Q2 — B.** `retry()` is for transient failures where a later attempt (with backoff) could succeed; permanent failures should return `failure()`. Mapping transient vs permanent is a per-error design decision. (Lecture 1, §2.)

**Q3 — B.** ENQUEUED-but-not-RUNNING means a constraint is unmet (read which in the inspector) or the power policy (Doze) is deferring it. The pipeline is working correctly — you asked for a condition that isn't satisfied. (Lecture 1, §3.)

**Q4 — B.** The minimum periodic interval is 15 minutes and intervals are approximate — batched and Doze-deferred. Periodic work is never an exact timer. (Lecture 1, §4.)

**Q5 — B.** `enqueueUniquePeriodicWork` with `KEEP` makes per-launch scheduling idempotent, preventing duplicate syncs. Forgetting it is the classic "N parallel jobs draining the battery" bug. (Lecture 1, §4.)

**Q6 — B.** `EXPONENTIAL` doubles the retry delay each attempt up to a cap — the right default for network work so a down backend isn't hammered and the battery isn't drained. (Lecture 1, §5.)

**Q7 — B.** Android 14 requires a declared `foregroundServiceType`, the matching permission, and the type passed to `startForeground`/`ForegroundInfo`; missing any crashes at promotion (the #1 Android-14 migration crash). (Lecture 2, §2.)

**Q8 — B.** Use WorkManager's `setForeground` promotion on a `CoroutineWorker` with the `dataSync` type — durability + constraints + a notification without a raw `Service`; `dataSync` foreground services are time-limited, another reason to prefer this path. (Lecture 2, §2.)

**Q9 — B.** Exact alarms are only for precise, user-chosen wall-clock moments; they're permission-gated since Android 12 and Play-scrutinized. Most "I need an exact alarm" cases are deferrable WorkManager work. (Lecture 2, §1, §3.)

**Q10 — B.** Check `canScheduleExactAlarms()` first (denied by default for most apps on Android 13+, revocable) and fall back gracefully; never assume the permission. (Lecture 2, §3.)

**Q11 — B.** Doze defers ordinary background work to maintenance windows (which grow further apart) to save battery; foreground services, `setExactAndAllowWhileIdle`, and high-priority FCM are exempt. It's by design. (Lecture 2, §4.)

**Q12 — B.** App Standby bucketed the unused app as `rare`/`restricted`, throttling its work and expedited quota — a reflection of user behavior you don't fight. A battery exemption is a Play-policy risk, not a fix. (Lecture 2, §4.)

**Q13 — B.** `WorkManagerTestInitHelper` + `SynchronousExecutor` + `TestDriver` (`setAllConstraintsMet`/`setPeriodDelayMet`) makes OS-scheduled work deterministic — drive the pipeline, assert on `WorkInfo`, no sleeps. (Lecture 1, §7.)

---

*Score 11+? On to Week 17. Below 9? Re-read both lecture notes and re-run exercises 1 and 3 — the constraint pipeline and the deterministic test are the two ideas this week is graded on.*
