# Mini-Project — An offline-first sync engine

This week you build an **offline-first sync engine**: a periodic WorkManager job that uploads a local queue to a backend, network-constraint-aware so it runs only when connected, with `BackoffPolicy.EXPONENTIAL` so a flaky backend backs off cleanly, a **foreground-promotion path** so that if the user opens the app mid-sync the work surfaces a progress notification and keeps running, and a full **integration test using `WorkManagerTestInitHelper`** that proves it runs under the right conditions — all of it Android-14-compliant and crashing on no device.

The point of the project is not "upload some data" — it's to build the *durable background-work spine* that every offline-first app needs and that the capstone's `:feature-sync` module is built on. It exercises the whole week at once: WorkManager fundamentals, constraints, exponential backoff, unique periodic work, foreground promotion with the correct `dataSync` type, the "least power" judgment (it's WorkManager, *not* an exact alarm or a raw foreground service), and deterministic testing. That "durable work that respects the platform's power rules, promotes to the foreground only when justified, and is tested green" is the senior instinct this week installs.

This builds on your Phase-3 stack: Hilt (Week 13) injects the worker's repository, Room (Week 14) holds the local queue, and a typed `NetworkResult` (Week 15) maps transient failures to `retry()`. We assume those; this week is about *when and how the work runs*.

---

## Where you're starting from

Your multi-module Phase-3 project with Hilt wired and a Room database. If you're building standalone, a single `:app` module with Hilt, Room, and WorkManager is enough. Add:

```kotlin
implementation("androidx.work:work-runtime-ktx:2.9.1")
implementation("androidx.hilt:hilt-work:1.2.0")
ksp("androidx.hilt:hilt-compiler:1.2.0")
testImplementation("androidx.work:work-testing:2.9.1")
```

## What you're building toward

By the end you have:

- A `SyncRepository` reading a local `PendingUpload` queue (Room) and uploading each item, returning a typed result that distinguishes transient from permanent failure.
- A `@HiltWorker SyncWorker` (a `CoroutineWorker`) that drains the queue, returns `success`/`retry`/`failure` correctly, and reports progress.
- A `SyncManager` that schedules the worker as **unique periodic work** with a `CONNECTED` network constraint and `EXPONENTIAL` backoff.
- A **foreground-promotion path**: when the user opens the app while a sync is running, the worker calls `setForeground` with a `dataSync`-typed `ForegroundInfo` and a progress notification — Android-14-compliant (declared type, held permission).
- An **integration test** with `WorkManagerTestInitHelper` proving: the worker runs when the constraint is met, returns `retry` on a (faked) transient failure, and `success` when the backend is healthy.
- A `README.md` documenting the design, the "why WorkManager and not an exact alarm/raw service" justification, and the foreground-promotion trigger.

---

## Milestone 1 — The repository and the queue (≈ 1 h)

The data layer (assumed from Weeks 14–15; build a thin version). A Room entity for the queue and a repository that uploads each item:

```kotlin
@Entity(tableName = "pending_uploads")
data class PendingUpload(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payload: String,
    val createdAt: Long
)

// From Week 15: a typed result distinguishing transient (retryable) from permanent failure.
sealed interface SyncResult {
    data object Success : SyncResult
    data object TransientFailure : SyncResult   // network blip, 503 -> retry
    data class PermanentFailure(val reason: String) : SyncResult  // 400, malformed -> give up
}

class SyncRepository @Inject constructor(
    private val dao: PendingUploadDao,
    private val api: UploadApi
) {
    suspend fun sync(): SyncResult {
        val pending = dao.getAll()
        for (item in pending) {
            when (val r = api.upload(item.payload)) {   // returns a NetworkResult (Week 15)
                is NetworkResult.Success -> dao.delete(item)
                is NetworkResult.Transient -> return SyncResult.TransientFailure
                is NetworkResult.Permanent -> return SyncResult.PermanentFailure(r.message)
            }
        }
        return SyncResult.Success
    }
}
```

Decisions you must be able to defend: **why does `sync()` stop and return `TransientFailure` on the first transient error** instead of continuing? Because retrying the *whole* batch with backoff is simpler and safe (uploads are idempotent by `id`, or the DAO delete makes them so). **Why map transient vs permanent at all?** Because the worker turns `TransientFailure → Result.retry()` (back off, try again) and `PermanentFailure → Result.failure()` (don't burn battery retrying the unfixable) — lecture 1, §2.

## Milestone 2 — The injected worker (≈ 1.5 h)

A `@HiltWorker CoroutineWorker` that drains the queue and maps the result:

```kotlin
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SyncRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Give up after a sensible number of attempts (don't retry forever).
        if (runAttemptCount > MAX_ATTEMPTS) return Result.failure()

        return when (val result = repository.sync()) {
            is SyncResult.Success -> Result.success()
            is SyncResult.TransientFailure -> Result.retry()       // back off (exponential)
            is SyncResult.PermanentFailure -> Result.failure()     // unfixable; stop
        }
    }

    companion object { const val MAX_ATTEMPTS = 5 }
}
```

Wire `HiltWorkerFactory` in your `Application` (a custom WorkManager `Configuration` via `Configuration.Provider`) so WorkManager can construct the injected worker — this is the standard `@HiltWorker` setup (Week 13; resources.md has the exact wiring). Defend in review: **why `@HiltWorker` and not a no-arg worker?** A real worker needs the repository, the DAO, the API — injected, not statically reached. A dependency-less worker is a toy.

## Milestone 3 — Schedule it: unique periodic, constrained, backed off (≈ 1 h)

A `SyncManager` that schedules the worker correctly:

```kotlin
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)     // only when online
            .setRequiresBatteryNotLow(true)                    // be a good battery citizen
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 6, repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 30, flexTimeIntervalUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueWorkName = "periodic-sync",
            existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,   // idempotent on every launch
            request = request
        )
    }
}
```

Call `schedulePeriodicSync()` from your `Application.onCreate` (or a Hilt-injected entry point). Defend in review: **why `enqueueUniquePeriodicWork` with `KEEP`?** So calling it on every app launch is idempotent — it doesn't pile up duplicate syncs (lecture 1, §4, the classic bug). **Why `EXPONENTIAL` backoff?** So a struggling backend isn't hammered and the battery isn't drained retrying every 30 seconds.

## Milestone 4 — Foreground promotion when the user opens the app (≈ 1.5 h)

The interesting part. The sync is ordinary deferrable work *most* of the time — but if the user opens the app *while a sync is in progress*, you promote the worker to the foreground so they see progress and the work isn't killed under memory pressure. Add a foreground path:

```kotlin
class SyncWorker @AssistedInject constructor(...) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // If this run was requested as user-aware (e.g. the app is in foreground),
        // promote so the user sees progress. Decide via inputData or a runtime check.
        val userAware = inputData.getBoolean(KEY_USER_AWARE, false)
        if (userAware) setForeground(buildForegroundInfo(progress = 0))

        // ... drain queue, optionally updating setForeground(buildForegroundInfo(progress)) ...
    }

    private fun buildForegroundInfo(progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Syncing your data")
            .setProgress(100, progress, false)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)   // the type — required on API 34+
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
```

**Android-14 compliance is mandatory** — declare in the manifest (or it crashes):

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name="androidx.work.impl.foreground.SystemForegroundService"
    android:foregroundServiceType="dataSync"
    tools:node="merge" />
```

Defend in review: **why promote only when the user opens the app, not always?** Because a foreground service is justified only by *user awareness* (lecture 2, §1–2). A silent background sync should stay background; promoting it always would show an unwanted notification and waste the `dataSync` time budget. The promotion is the *legitimate* foreground-service case — the user is now actively waiting — done the right way (WorkManager's `setForeground`, not a hand-written `Service`).

To trigger it for real: when the app comes to the foreground (a lifecycle observer) and a sync is in progress, enqueue an expedited sync with `KEY_USER_AWARE = true`, or have the running worker observe app state. Either is acceptable; document your trigger.

## Milestone 5 — The integration test (≈ 1.5 h)

Prove the engine works deterministically with `WorkManagerTestInitHelper` (exercise 3's pattern), injecting a *fake* repository so you control success/transient/permanent:

```kotlin
@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    @Before fun setUp() {
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(/* a factory providing a fake SyncRepository */)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test fun `sync succeeds when repository returns Success and constraint is met`() {
        // fakeRepo.result = SyncResult.Success
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        val wm = WorkManager.getInstance(context)
        wm.enqueue(request).result.get()

        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(request.id)

        assertEquals(WorkInfo.State.SUCCEEDED, wm.getWorkInfoById(request.id).get().state)
    }

    @Test fun `transient failure yields a retry`() {
        // fakeRepo.result = SyncResult.TransientFailure -> worker returns Result.retry()
        // assert the WorkInfo state reflects a retry / re-enqueue
    }
}
```

Test at least three paths: success (constraint met → SUCCEEDED), transient failure (→ retry/re-enqueued), and permanent failure (→ FAILED, no pointless retries). No `Thread.sleep`; drive everything with the `TestDriver`. This test is the deliverable that distinguishes "I wrote a sync" from "I wrote a sync I can *prove* behaves correctly under the conditions that matter."

---

## Acceptance criteria

- [ ] A `@HiltWorker SyncWorker` drains a Room-backed queue via an injected `SyncRepository` and maps results: `Success → success()`, transient → `retry()`, permanent → `failure()`, with a `runAttemptCount` cap.
- [ ] A `SyncManager` schedules it as **unique periodic work** (`KEEP`) with a `CONNECTED` (+ battery-not-low) constraint and `EXPONENTIAL` backoff.
- [ ] A **foreground-promotion path** fires `setForeground` with a `dataSync`-typed `ForegroundInfo` and a progress notification, *only* when user-aware; the manifest declares the type + permissions; it does **not** crash on targetSdk 35.
- [ ] The engine uses **no exact alarm and no battery-optimization exemption** — it is deferrable WorkManager work, the least-powerful correct tool. The README justifies this explicitly.
- [ ] An integration test with `WorkManagerTestInitHelper` proves success, transient-retry, and permanent-failure paths, deterministically (no `Thread.sleep`).
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **Chained sync.** Split into `download-changes` → `apply-locally` → `upload-pending` as a WorkManager chain (`beginWith().then().then()`), passing each step's output as the next's input. Watch BLOCKED states in the inspector.
- **Expedited "sync now."** Add a user-triggered "Sync now" button that enqueues an *expedited* one-time sync (with `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`), and observe quota behavior in a low standby bucket (the challenge's scenario 3).
- **Observe sync state in the UI.** Surface `WorkInfo` as a `Flow` into a Compose screen showing "last synced," "syncing…," or "sync failed — will retry." This is the Now-In-Android `SyncManager` pattern (resources.md).
- **Doze-aware assertions.** Add an instrumented test (or a manual `adb` script) that forces Doze (`dumpsys deviceidle force-idle`), confirms the periodic sync defers, then `unforce`s and confirms it runs — documenting the maintenance-window behavior you accept rather than fight.

## What this milestone earns you

You can now build *durable background work that respects the platform*: deferrable WorkManager work with the right constraints and exponential backoff, unique scheduling that doesn't pile up, a foreground-promotion path that's Android-14-compliant and used only when the user is genuinely aware, the judgment to use the *least* powerful tool (no exact alarm, no battery exemption), and a deterministic integration test that proves it. That is the literal "skill earned" line for the week: WorkManager fluency end to end, foreground service compliance without crashing on Android 14, and knowing when *not* to use an exact alarm. This exact engine returns in the capstone as `:feature-sync`, promoted to an FCM-aware, conflict-resolving, offline-first production system — and you'll be glad the durable-work spine is already solid. Week 17 generalizes the deterministic-test discipline you built here across the entire testing pyramid.
