# Exercise 1 — Constraints and backoff, made visible

**Goal.** Schedule a one-time `CoroutineWorker` that requires a network connection and uses `EXPONENTIAL` backoff, observe its `WorkInfo` state move through ENQUEUED → RUNNING → SUCCEEDED in the Background Task Inspector, then make it return `Result.retry()` and *watch the backoff delay grow* between attempts. If you can predict whether the work is RUNNING or ENQUEUED from the device's network state, you understand the constraint pipeline.

**Estimated time.** 45 minutes.

**Prerequisites.** Android Studio Ladybug+, a Pixel 8 API 35 emulator, `androidx.work:work-runtime-ktx` added. Any Compose or non-Compose app skeleton with an `Application` is fine.

---

## Step 1 — Add WorkManager

In `app/build.gradle.kts`:

```kotlin
implementation("androidx.work:work-runtime-ktx:2.9.1")
```

## Step 2 — Write a worker that you can make fail on demand

```kotlin
class FlakyWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("FlakyWorker", "attempt #$runAttemptCount starting")
        // Simulate a transient failure for the first two attempts, then succeed.
        return if (runAttemptCount < 2) {
            Log.d("FlakyWorker", "attempt #$runAttemptCount -> retry")
            Result.retry()                    // transient failure: back off and re-run
        } else {
            Log.d("FlakyWorker", "attempt #$runAttemptCount -> success")
            Result.success()
        }
    }
}
```

## Step 3 — Schedule it with a network constraint and exponential backoff

```kotlin
fun scheduleFlaky(context: Context): UUID {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)     // won't run with no network
        .build()

    val request = OneTimeWorkRequestBuilder<FlakyWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,                     // 10s, 20s, 40s, ...
            10, TimeUnit.SECONDS
        )
        .build()

    WorkManager.getInstance(context).enqueue(request)
    return request.id
}
```

Call `scheduleFlaky(this)` from a button or `onCreate`.

## Step 4 — Observe the state as a Flow

Collect the `WorkInfo` and log every transition:

```kotlin
lifecycleScope.launch {
    WorkManager.getInstance(this@MainActivity)
        .getWorkInfoByIdFlow(workId)
        .collect { info ->
            Log.d("WorkState", "state=${info?.state}, attempt=${info?.runAttemptCount}")
        }
}
```

## Step 5 — Watch it in the Background Task Inspector

Open **View ▸ Tool Windows ▸ App Inspection ▸ Background Task Inspector**. Run the app and schedule the work. You should see:

- The work appear as **ENQUEUED**, then move to **RUNNING**, then (after two retries) **SUCCEEDED**.
- In the logs, the **delay between attempts grows**: attempt 0 immediately, attempt 1 after ~10s, attempt 2 after ~20s — exponential backoff firing.

## Step 6 — Prove the constraint gates execution

Now turn **off** the emulator's network (Extended Controls ▸ Cellular/Wi-Fi off, or airplane mode) and schedule the work again. Observe:

- The work sits in **ENQUEUED** and does **not** move to RUNNING — the `NetworkType.CONNECTED` constraint is unmet.
- Turn the network back on. The constraint becomes met, the `ConstraintController` notifies WorkManager, and the work moves to RUNNING and executes.

Record in `notes/constraints.md`: *which state was the work in while the network was off, and what moved it to RUNNING?* (Lecture 1, §3 — the constraint pipeline.)

## Step 7 — Predict, then confirm, the Doze effect (optional but recommended)

Force Doze and watch ordinary work defer:

```
adb shell dumpsys deviceidle force-idle
```

Schedule the work; note it doesn't run promptly (it's deferred to a maintenance window). Then:

```
adb shell dumpsys deviceidle unforce
```

and watch it run. Record what you saw — this is lecture 2's Doze, live.

---

## Acceptance criteria

- [ ] A `FlakyWorker` that returns `retry()` twice then `success()`, scheduled with a `CONNECTED` constraint and `EXPONENTIAL` backoff.
- [ ] You observed ENQUEUED → RUNNING → SUCCEEDED in the Background Task Inspector and the growing backoff delay in logs.
- [ ] With the network off, the work stayed ENQUEUED; turning it on moved it to RUNNING. Documented in `notes/constraints.md` with the cause.
- [ ] (Optional) You forced Doze and observed the work defer, then run after `unforce`.
- [ ] Build with **0 warnings, 0 errors**.

## What you just proved

You proved lecture 1's central claim with your eyes: WorkManager is a constraint-satisfaction engine, not a timer. The work didn't run because the network constraint was unmet — and the instant it was met, the pipeline ran it. You also saw `EXPONENTIAL` backoff space out the retries, exactly as designed for network work. "My work didn't run" is now a *diagnosable* statement (which constraint? which power regime?), not a mystery.

---

## Hints (read only if stuck > 10 min)

- **The work runs immediately even with the constraint.** The emulator may report a network even in "airplane mode" via the host. Use Extended Controls to actually drop Cellular *and* Wi-Fi, or check the inspector's "Constraints" panel to confirm the network constraint is listed as pending.
- **No backoff delay visible / retries fire instantly.** Backoff has a minimum (~10s) and the test driver isn't involved here (this is a real device run), so attempts genuinely wait. Watch the timestamps in Logcat, not wall-clock impatience.
- **`getWorkInfoByIdFlow` unresolved.** It's in `work-runtime-ktx` 2.9+. Older versions used `getWorkInfoByIdLiveData`; upgrade or adapt.
- **The work never reaches SUCCEEDED.** Your `runAttemptCount` logic may be inverted — `runAttemptCount` starts at 0, so `< 2` retries on attempts 0 and 1 and succeeds on attempt 2. Check the comparison.
- **Doze `force-idle` doesn't defer the work.** Some emulator images don't fully honor Doze. Try a physical device, or trust the documented behavior — the `force-idle`/`unforce` commands are the right ones regardless.
