// Exercise 3 — A deterministic WorkManager test
//
// Goal: Test a constrained periodic worker WITHOUT flakiness using
//       WorkManagerTestInitHelper + SynchronousExecutor + TestDriver. Drive the
//       constraints and the period delay manually, assert the WorkInfo state.
//       Async, OS-scheduled work made deterministic and fast.
//
// Estimated time: 40 minutes.
//
// HOW TO USE THIS FILE
//
// This is a Robolectric/JVM test (runs without an emulator). Add work-testing:
//
//   testImplementation("androidx.work:work-testing:2.9.1")
//   testImplementation("org.robolectric:robolectric:4.13")
//   testImplementation("androidx.test:core:1.6.1")
//   testImplementation("junit:junit:4.13.2")
//
// Put this in app/src/test/. The worker under test lives in app/src/main.
//
//   1. Implement CountingWorker (it records that it ran).
//   2. Implement the TODOs in the two tests to drive the TestDriver.
//   3. Run the tests — they must pass with zero Thread.sleep and zero flakiness.
//
// ACCEPTANCE CRITERIA
//
//   [ ] WorkManager is initialized with a SynchronousExecutor in @Before.
//   [ ] The constraint test uses setAllConstraintsMet and asserts the worker ran.
//   [ ] The periodic test uses setPeriodDelayMet to fire the next run.
//   [ ] No Thread.sleep, no waiting — fully deterministic. Both tests green.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.work

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

// The worker under test. It just records that it ran (a real one would sync).
class CountingWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Log.d("CountingWorker", "ran")
        ranCount++
        return Result.success()
    }
    companion object {
        // A test-visible counter. (In a real test you'd inject a fake repository and
        // verify on it; this keeps the exercise self-contained.)
        @JvmStatic var ranCount: Int = 0
    }
}

@RunWith(RobolectricTestRunner::class)
class WorkManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CountingWorker.ranCount = 0

        // Initialize WorkManager for tests: SynchronousExecutor runs work immediately,
        // on the calling thread, so there's nothing to wait for.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun `constrained one-time work runs once constraints are met`() {
        val request = OneTimeWorkRequestBuilder<CountingWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)   // gated on network
                    .build()
            )
            .build()

        val wm = WorkManager.getInstance(context)
        wm.enqueue(request).result.get()    // enqueue completes synchronously

        // Before constraints are met, the worker has NOT run.
        assertEquals(0, CountingWorker.ranCount)

        val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
        // TODO 1: tell the TestDriver the constraints are now met, so the work runs.
        //   driver.setAllConstraintsMet(request.id)

        // Now it should have run exactly once, and the state should be SUCCEEDED.
        val info = wm.getWorkInfoById(request.id).get()
        assertEquals(WorkInfo.State.SUCCEEDED, info.state)
        assertEquals(1, CountingWorker.ranCount)
    }

    @Test
    fun `periodic work fires again when the period delay is met`() {
        val request = PeriodicWorkRequestBuilder<CountingWorker>(
            repeatInterval = 1, repeatIntervalTimeUnit = TimeUnit.HOURS
        ).build()

        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "periodic-test",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        ).result.get()

        val driver = WorkManagerTestInitHelper.getTestDriver(context)!!

        // The first run fires when constraints (none here) are met / it's eligible.
        // TODO 2a: fire the FIRST run by meeting any initial conditions.
        //   driver.setAllConstraintsMet(request.id)   // (no constraints, but this releases it)

        val afterFirst = CountingWorker.ranCount

        // TODO 2b: simulate the 1-hour interval elapsing so the NEXT periodic run fires.
        //   driver.setPeriodDelayMet(request.id)

        // The worker should have run again after the period was met.
        // (Exact count semantics depend on the WorkManager version; assert it INCREASED.)
        assert(CountingWorker.ranCount > afterFirst) {
            "expected periodic work to run again after setPeriodDelayMet"
        }
    }
}

// ----------------------------------------------------------------------------
// WHY this is deterministic (write it before reading):
//
//   - SynchronousExecutor runs doWork() on the test thread, immediately, so there's
//     no async race and no Thread.sleep.
//   - TestDriver.setAllConstraintsMet stands in for the real constraint pipeline
//     (lecture 1, §3): instead of waiting for an actual network, you DECLARE the
//     constraint satisfied and the work runs.
//   - TestDriver.setPeriodDelayMet stands in for the 1-hour wall clock: you fast-
//     forward the interval instead of waiting an hour.
//
//   The result: OS-scheduled, eventually-maybe work becomes "runs now, assert the
//   result" — the deterministic-async discipline Week 17 generalizes.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - "getTestDriver returned null" — you didn't call initializeTestWorkManager in
//   @Before, or you called the production WorkManager.initialize. Use
//   WorkManagerTestInitHelper.initializeTestWorkManager.
//
// - TODO 1: driver.setAllConstraintsMet(request.id). This is the test analog of the
//   network actually connecting.
//
// - The worker doesn't run even after setAllConstraintsMet — check you used the
//   SynchronousExecutor; with the default executor the work runs on a background
//   thread and your assertion races it.
//
// - Periodic count assertions are finicky across WorkManager versions; assert the
//   count INCREASED (> afterFirst) rather than an exact number. setPeriodDelayMet is
//   the right driver method for "the interval elapsed."
//
// - Robolectric can't find the Application — add a test Application or rely on the
//   default; ApplicationProvider.getApplicationContext() gives you the Robolectric one.
//
// ----------------------------------------------------------------------------
