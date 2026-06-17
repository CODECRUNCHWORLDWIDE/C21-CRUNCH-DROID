# Lecture 1 — WorkManager: constraints, backoff, and "why your work did not run"

> "WorkManager is not a timer. It is a constraint-satisfaction engine. It runs your work when the device state matches the constraints you declared and the OS's power policy permits — and 'why didn't my work run' is always answered by one of those two clauses."

This is the lecture that turns WorkManager from a mysterious "schedule and pray" API into a system you can reason about. We build it bottom-up: what a `Worker` is, what a `WorkRequest` carries, how constraints gate execution through the constraint pipeline, the shapes of work (one-time, periodic, unique, chained, expedited), how backoff and retry actually behave, and — woven through — how to test all of it deterministically. By the end, when work doesn't run, you'll diagnose *which* clause failed instead of sprinkling retries.

---

## 1. What WorkManager is for, and what it is not

WorkManager is for **deferrable, durable** background work:

- **Deferrable** — it doesn't have to run *right now*; it has to run *eventually*, when conditions are right. Syncing data, uploading logs, refreshing a cache, processing a queue.
- **Durable** — it must survive process death, app restart, and even device reboot. You enqueue it once, and WorkManager *guarantees* it runs (within the power rules), persisting the request to a local database so a reboot doesn't lose it.

That durability is the headline. When you `enqueue` a `WorkRequest`, WorkManager writes it to an internal Room database and schedules it (onto `JobScheduler` under the hood). If the OS kills your process, if the user reboots, if the app crashes — the work is still queued and will run. That is something a raw `Thread`, a `Coroutine`, or an `Executor` *cannot* give you, because all of those die with your process (lecture from Week 06: the OS kills your process freely).

What WorkManager is **not**:

- **Not a precise timer.** It does not run work at an exact moment. A "periodic" 15-minute job runs *approximately* every 15 minutes, batched with other work and deferred by Doze. If you need a precise wall-clock moment, that's an exact alarm (lecture 2) — and you almost never do.
- **Not for "right now, user is watching" work.** If the user is actively waiting on the result (an active download they're staring at, music playing), that's a foreground service (lecture 2).

The one-line filter: **deferrable + must-eventually-run-even-after-reboot → WorkManager.** Everything else is a different tool.

---

## 2. A Worker and a WorkRequest

The unit of work is a `Worker` (or, better, a `CoroutineWorker` for `suspend` code):

```kotlin
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    // doWork() is a suspend function — you can call suspend repository functions directly.
    override suspend fun doWork(): Result {
        return try {
            val pending = readPendingUploads()          // a suspend DAO call
            uploadAll(pending)                          // a suspend network call
            Result.success()                            // done, don't run again
        } catch (e: IOException) {
            Result.retry()                              // transient failure -> back off and retry
        } catch (e: Exception) {
            Result.failure()                            // permanent failure -> give up
        }
    }
}
```

`doWork()` returns one of three results, and **getting the distinction right is half of WorkManager competence**:

- **`Result.success()`** — the work is done; don't run it again (for one-time work).
- **`Result.retry()`** — a *transient* failure (network blip, server 503). WorkManager will re-run the worker later, applying your backoff policy (§5).
- **`Result.failure()`** — a *permanent* failure (a 400 bad request, malformed data). Don't retry; it'll never succeed.

Returning `retry()` for a permanent failure burns battery re-running work that can't succeed; returning `failure()` for a transient one drops work that would have succeeded on the next try. The mapping is a design decision you make per error.

You wrap the worker in a `WorkRequest`:

```kotlin
val request = OneTimeWorkRequestBuilder<SyncWorker>()
    .setConstraints(constraints)                       // §3
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,     // §5
        10, TimeUnit.SECONDS)
    .setInputData(workDataOf("queueId" to 42))         // pass data in
    .build()

WorkManager.getInstance(context).enqueue(request)
```

And you observe its state as a `Flow` (Week 5 pays off):

```kotlin
WorkManager.getInstance(context)
    .getWorkInfoByIdFlow(request.id)
    .collect { info ->
        when (info?.state) {
            WorkInfo.State.ENQUEUED -> { /* waiting for constraints */ }
            WorkInfo.State.RUNNING  -> { /* executing now */ }
            WorkInfo.State.SUCCEEDED -> { /* done */ }
            WorkInfo.State.FAILED   -> { /* gave up */ }
            else -> {}
        }
    }
```

The states are the vocabulary you debug in: **ENQUEUED** means "scheduled, waiting for constraints/policy"; **RUNNING** means "executing now"; **BLOCKED** means "waiting on a prerequisite in a chain." If your work sits in ENQUEUED forever, the answer is in §3.

---

## 3. Constraints — the gate, and the pipeline behind it

A `WorkRequest` can declare **constraints** — conditions the device must satisfy before the work runs:

```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)     // any network
    .setRequiresBatteryNotLow(true)                    // not in low-battery
    .setRequiresCharging(false)                        // doesn't need to be plugged in
    .setRequiresStorageNotLow(true)                    // enough free storage
    // .setRequiresDeviceIdle(true)                     // only when device is idle
    .build()
```

Common network types: `CONNECTED` (any), `UNMETERED` (Wi-Fi, not cellular data), `METERED`, `NOT_REQUIRED`, `NOT_ROAMING`. A sync that downloads a lot should require `UNMETERED` so you don't burn the user's cellular data.

**The constraint pipeline — the "why didn't it run" machinery.** This is the part beginners skip and seniors live in. When you enqueue constrained work:

1. WorkManager persists the request and registers its constraints with a **`ConstraintsTracker`**, which owns a set of **`ConstraintController`**s — one per constraint type (network, battery, charging, storage, idle).
2. Each controller listens to a system broadcast/callback for its condition (e.g. the network controller listens to `ConnectivityManager`). It reports whether its constraint is currently *met*.
3. The work is **ENQUEUED but not RUNNING** until *every* controller reports its constraint met *and* the OS's power policy (Doze, standby — lecture 2) permits execution.
4. When the last unmet constraint becomes met (network connects, battery charges up), the tracker notifies WorkManager, which moves the work to RUNNING and executes your `doWork()`.

So when your sync "doesn't run," the diagnosis is mechanical: **which constraint is unmet, or which power regime is deferring it?** A `NetworkType.UNMETERED` job on cellular sits in ENQUEUED until Wi-Fi. A `setRequiresCharging(true)` job sits there until plugged in. A perfectly unconstrained job can *still* sit there if the device is in deep Doze (lecture 2). The `Background Task Inspector` and `adb shell dumpsys jobscheduler` show you exactly which constraint is pending — you read it, you don't guess.

The senior reflex: **"my work didn't run" → open the inspector, read the pending constraint or the Doze state.** Never add a retry to "fix" a constraint problem.

---

## 4. The shapes of work — periodic, unique, chained, expedited

**Periodic work.** For recurring background work (a sync every few hours):

```kotlin
val periodic = PeriodicWorkRequestBuilder<SyncWorker>(
    repeatInterval = 6, repeatIntervalTimeUnit = TimeUnit.HOURS,
    flexTimeInterval = 30, flexTimeIntervalUnit = TimeUnit.MINUTES   // run in the last 30 min of each interval
).setConstraints(constraints).build()
```

Two hard facts: the **minimum interval is 15 minutes** (the OS batches work to save battery; you cannot run periodic work more often), and the interval is *approximate* — the OS may defer it. Periodic work is "roughly every N, eventually," never "exactly every N."

**Unique work.** The most important correctness tool, and the most-forgotten. If you enqueue a periodic sync on every app launch, you'll pile up *duplicate* syncs. `enqueueUniquePeriodicWork` dedupes by name:

```kotlin
WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    uniqueWorkName = "periodic-sync",
    existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,   // keep the existing one
    request = periodic
)
```

The policy decides what happens if work with that name already exists:

- **`KEEP`** — leave the existing schedule alone (the common choice for "ensure a sync is scheduled" on every launch — idempotent).
- **`UPDATE`** — replace the schedule but preserve its state where possible (use when constraints/interval changed).
- **`REPLACE`** (one-time: `CANCEL_AND_REENQUEUE`) — cancel and start fresh.

Forgetting unique work is the classic bug: a periodic job enqueued un-uniquely on every `onCreate` becomes N parallel jobs draining the battery.

**Chained work.** Run workers in sequence, passing output to input:

```kotlin
WorkManager.getInstance(context)
    .beginWith(downloadRequest)        // runs first
    .then(processRequest)              // runs after download SUCCEEDS, gets its outputData
    .then(uploadRequest)               // then this
    .enqueue()
```

If any link returns `failure()`, the rest of the chain is cancelled. Chained workers are BLOCKED until their prerequisite succeeds. `outputData` from one becomes `inputData` to the next (small key-value data only — for large payloads, pass a file path or a DB id, never the bytes).

**Expedited work.** For work that's important and should run *soon* but is still deferrable — a user-initiated upload, say. `setExpedited` asks the OS to run it promptly (it may run as a short foreground job):

```kotlin
val expedited = OneTimeWorkRequestBuilder<SyncWorker>()
    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)   // fall back if quota exhausted
    .build()
```

Expedited work has a **quota** the OS grants and refills over time (based on the app's standby bucket). When the quota is exhausted, `OutOfQuotaPolicy` decides: `RUN_AS_NON_EXPEDITED_WORK_REQUEST` (run later as ordinary work) or `DROP_WORK_REQUEST`. Expedited is not "ignore the rules" — it's "spend my limited expedited budget," and a common "why didn't it run expedited" cause is an exhausted quota (challenge scenario 3).

---

## 5. Backoff and retry — what actually happens on `retry()`

When a worker returns `Result.retry()`, WorkManager re-runs it later, after a delay determined by the **backoff policy**:

```kotlin
.setBackoffCriteria(
    BackoffPolicy.EXPONENTIAL,     // delay doubles each attempt
    10, TimeUnit.SECONDS           // the INITIAL delay
)
```

- **`EXPONENTIAL`** — the delay roughly doubles each failed attempt: 10s, 20s, 40s, 80s, ... up to a cap (~5 hours). This is the right default for network work — it backs off hard when the backend is down, so you don't hammer a struggling server (and you don't drain the battery retrying every 10 seconds for an hour).
- **`LINEAR`** — the delay grows linearly: 10s, 20s, 30s, ... Use it when you expect a quick, predictable recovery.

`runAttemptCount` (available in the worker) tells you which attempt you're on, so you can give up after N tries by returning `failure()`:

```kotlin
override suspend fun doWork(): Result {
    if (runAttemptCount > 5) return Result.failure()   // stop retrying after 5 attempts
    return try { sync(); Result.success() }
    catch (e: IOException) { Result.retry() }
}
```

The mental model: **`retry()` schedules a re-run after the backoff delay; `EXPONENTIAL` backoff is the right default for anything network-bound; and you cap retries yourself with `runAttemptCount`.** A worker that returns `retry()` forever on a permanent failure is a battery bug; a worker that returns `failure()` on a transient one drops work that would have succeeded. Map your errors deliberately (§2).

### Passing data in and out

Workers don't take constructor arguments for their *data* (the framework constructs them); you pass data through `Data`, a small key-value bundle:

```kotlin
// In: schedule with input data.
val request = OneTimeWorkRequestBuilder<UploadWorker>()
    .setInputData(workDataOf("fileId" to 42L, "retryable" to true))
    .build()

// In the worker: read it.
override suspend fun doWork(): Result {
    val fileId = inputData.getLong("fileId", -1L)
    // ... do the upload ...
    // Out: return data for an observer or the next worker in a chain.
    return Result.success(workDataOf("uploadedAt" to System.currentTimeMillis()))
}
```

`Data` is **small** — it's persisted to WorkManager's database and capped (~10 KB). It's for *identifiers and flags*, never payloads: pass a `fileId` and let the worker read the file from disk/Room, never the file's bytes. Cross a worker chain (§4), one worker's `outputData` becomes the next's `inputData` — again, ids and flags only. The discipline mirrors the whole platform: persist references, not large state.

### Progress and observation

A long worker can report progress with `setProgress`, which an observer reads off `WorkInfo.progress`:

```kotlin
// In the worker:
setProgress(workDataOf("percent" to 40))

// In the UI (a Compose screen, say):
workManager.getWorkInfoByIdFlow(id).collect { info ->
    val percent = info?.progress?.getInt("percent", 0) ?: 0
    // render a progress bar
}
```

This is how the Now-In-Android-style "Syncing… / Last synced 2m ago" UI is built: the UI collects `WorkInfo` as a `Flow` and renders the worker's state and progress, all reactively (Week 5 again). The worker and the UI never call each other directly — the worker writes state, the UI observes it, fully decoupled.

---

## 6. Injecting dependencies — `@HiltWorker`

A real worker needs a repository, an API client, a DAO. You inject them with Hilt (Week 13):

```kotlin
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SyncRepository       // injected
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = repository.sync().toWorkResult()
}
```

`@HiltWorker` plus a `HiltWorkerFactory` (wired in your `Application` via a custom WorkManager `Configuration`) lets WorkManager construct the worker *with* its injected dependencies. The `@Assisted` parameters are the ones WorkManager supplies at runtime (context, params); the rest come from the Hilt graph. This is the production pattern — a worker with no dependencies is a toy; a real one talks to your data layer.

---

## 7. Testing background work deterministically

Background work is asynchronous and OS-scheduled, which sounds untestable — but `WorkManagerTestInitHelper` makes it *fully deterministic*. The trick: initialize WorkManager with a **`SynchronousExecutor`** (work runs immediately, on the calling thread) and drive constraints/delays manually with a **`TestDriver`**:

```kotlin
@Before
fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val config = Configuration.Builder()
        .setMinimumLoggingLevel(Log.DEBUG)
        .setExecutor(SynchronousExecutor())          // run work synchronously
        .build()
    WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
}

@Test
fun syncRunsWhenConstraintsMet() {
    val request = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    val wm = WorkManager.getInstance(context)
    wm.enqueue(request).result.get()

    val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
    driver.setAllConstraintsMet(request.id)          // pretend the network constraint is satisfied

    val info = wm.getWorkInfoById(request.id).get()
    assertEquals(WorkInfo.State.SUCCEEDED, info.state)  // deterministic — no waiting, no flakiness
}
```

The `TestDriver` methods are the constraint pipeline (§3) under your control:

- **`setAllConstraintsMet(id)`** — pretend every constraint is satisfied, so the work runs.
- **`setInitialDelayMet(id)`** — fast-forward past an initial delay.
- **`setPeriodDelayMet(id)`** — for periodic work, simulate the interval elapsing so the next run fires.

This turns "OS-scheduled, eventually, maybe" into "runs now, assert the result" — no `Thread.sleep`, no flakiness. The mini-project's integration test is exactly this shape, and Week 17 generalizes the deterministic-async discipline across the whole testing pyramid.

One subtlety to internalize: you inject a **fake repository** into the worker (via a test `WorkerFactory`) so you control success/transient/permanent at will, and you drive the *scheduling* with the `TestDriver`. The two together let you test the *whole* decision matrix:

```kotlin
// Pseudocode for the matrix you can now cover deterministically:
//   fakeRepo.result = Success           -> setAllConstraintsMet -> assert SUCCEEDED
//   fakeRepo.result = TransientFailure  -> setAllConstraintsMet -> assert retry/re-enqueued
//   fakeRepo.result = PermanentFailure  -> setAllConstraintsMet -> assert FAILED (no retry)
//   no constraints met                  -> (don't drive)        -> assert ENQUEUED, never ran
```

That four-row matrix — success, transient, permanent, blocked — is the *entire behavioral contract* of a worker, and you can assert every row in milliseconds with zero real network and zero waiting. A worker you've tested this way is one you can *prove* behaves; an untested one is a hope. This is the difference between "I wrote a sync" and "I wrote a sync I can defend in review."

---

## 8. Recap — the one-question habit

The reflex that turns WorkManager from "schedule and pray" into a system you reason about is to ask, when work doesn't run, **"which clause failed — a constraint, or the power policy?"**

- Work stuck in ENQUEUED → a constraint is unmet (read which one in the inspector) or the device is in Doze (lecture 2).
- Duplicate syncs piling up → you enqueued non-uniquely; use `enqueueUniquePeriodicWork` with `KEEP`.
- A failing worker hammers the backend every few seconds → wrong backoff; use `EXPONENTIAL`.
- Work retries forever on a permanent error → you returned `retry()` where you meant `failure()`; map transient vs permanent.
- Periodic work "isn't every 15 minutes exactly" → it never is; periodic is approximate and Doze-deferred by design.
- A worker can't get its repository → it isn't a `@HiltWorker`, or the `HiltWorkerFactory` isn't wired in your `Application`'s WorkManager `Configuration`.
- Expedited work ran late → the expedited quota for your standby bucket was exhausted; it fell back to ordinary work (lecture 2).

Each line is the same move: name the failed clause (constraint, policy, uniqueness, error-mapping, quota, DI wiring) and the cause is immediate — no retries-as-prayer.

The model: **WorkManager is a durable constraint-satisfaction engine — it persists your request, gates it on the constraints you declared and the OS power policy, runs your `doWork()` when both allow, and re-runs with backoff on `retry()`.** You declare requirements; the OS picks the moment; and "why didn't it run" is always a constraint or a power-regime answer you can *read*, never guess.

In lecture 2 we cover the other two tools and the judgment to choose between all three: foreground services (and the Android 14 type rules that crash you if you get them wrong), exact alarms (and why Android 12 made them a last resort), and the Doze/App-Standby power regimes that defer the ordinary background work this lecture scheduled. WorkManager is the default; lecture 2 is when — rarely — you need more.
