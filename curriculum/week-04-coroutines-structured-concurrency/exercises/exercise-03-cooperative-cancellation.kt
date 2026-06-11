// Exercise 3 — Cooperative cancellation: make a CPU loop stop, and stop a catch
//               from swallowing CancellationException
//
// Goal: Take a non-cooperative CPU loop that ignores cancel(), make it cooperate
//       three ways (ensureActive / isActive / yield), and fix a retry loop whose
//       blanket catch eats CancellationException so the coroutine never stops.
//       Prove each fix with an assertion, not a feeling.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// kotlinx-coroutines-test suite using `runTest`. Drop into src/test/kotlin with
// kotlinx-coroutines-test and kotlin("test") on the test classpath.
//
//   1. Add to your test target.
//   2. Run with `./gradlew test`.
//   3. Read the assertions: cooperative versions stop on cancel; the swallow-bug
//      version is shown to NOT stop until fixed.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] `nonCooperativeLoopIgnoresCancellation` passes — it documents the bug.
//   [ ] `ensureActiveStopsTheLoop`, `isActiveStopsTheLoop`, `yieldStopsTheLoop`
//       all pass — three correct cooperative fixes.
//   [ ] `swallowedCancellationNeverStops` documents the bug; `rethrowFixesIt`
//       proves the fix.
//   [ ] You can explain why cancellation is cooperative, not pre-emptive.
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package com.crunch.droid

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException as KCancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ----------------------------------------------------------------------------
// The work under test. A loop that increments a counter "per chunk of CPU work".
// We model the CPU work as a no-op so the test runs fast; the point is whether
// the loop CHECKS for cancellation between chunks, not how heavy each chunk is.
// ----------------------------------------------------------------------------

class Cruncher(private val counter: AtomicInteger) {

    // BUG: no suspension point, no isActive/ensureActive check. cancel() is ignored.
    suspend fun crunchNonCooperative(chunks: Int) {
        for (i in 0 until chunks) {
            counter.incrementAndGet()        // pure "CPU"; never checks cancellation
        }
    }

    // FIX A: ensureActive() throws CancellationException if the Job is cancelled.
    suspend fun crunchEnsureActive(chunks: Int) {
        for (i in 0 until chunks) {
            currentEnsureActive()            // see helper below — throws if cancelled
            counter.incrementAndGet()
        }
    }

    // FIX B: isActive lets you bail GRACEFULLY with a partial result (no throw).
    suspend fun crunchIsActive(chunks: Int): Int {
        var done = 0
        for (i in 0 until chunks) {
            if (!currentIsActive()) return done   // graceful early return
            counter.incrementAndGet()
            done++
        }
        return done
    }

    // FIX C: yield() is a suspension point — it checks cancellation AND lets other
    // coroutines run. Good for a hot loop you also want to share the thread.
    suspend fun crunchYield(chunks: Int) {
        for (i in 0 until chunks) {
            yield()                          // suspends; throws if cancelled
            counter.incrementAndGet()
        }
    }
}

// Small helpers so the loop bodies above read cleanly and still see the coroutine
// context. (In real code you'd call ensureActive()/isActive directly inside the
// suspend function's own context.)
private suspend fun currentEnsureActive() = kotlin.coroutines.coroutineContext.ensureActive()
private suspend fun currentIsActive(): Boolean = kotlin.coroutines.coroutineContext[Job]?.isActive ?: true

// ----------------------------------------------------------------------------
// The swallowed-cancellation bug: a retry loop whose blanket catch eats
// CancellationException, so cancelling the scope never stops the retries.
// ----------------------------------------------------------------------------

class Retrier(private val attempts: AtomicInteger) {

    // BUG: catch (Exception) swallows CancellationException -> infinite retry,
    // immune to cancellation.
    suspend fun loadSwallowing(failNTimes: Int) {
        var failures = 0
        while (true) {
            try {
                attempts.incrementAndGet()
                if (failures < failNTimes) { failures++; error("boom") }
                return
            } catch (e: Exception) {          // <- also catches CancellationException
                delay(10)                     // retry; if we were cancelled, delay re-throws...
                // ...but we'd catch that on the NEXT loop, so we'd never stop. Worse,
                // if the failure is the cancellation itself surfacing here, we loop.
            }
        }
    }

    // FIX: rethrow CancellationException (or ensureActive) before handling real errors.
    suspend fun loadRethrowing(failNTimes: Int) {
        var failures = 0
        while (true) {
            try {
                attempts.incrementAndGet()
                if (failures < failNTimes) { failures++; error("boom") }
                return
            } catch (e: CancellationException) {
                throw e                       // let cancellation propagate
            } catch (e: Exception) {
                delay(10)                     // only REAL errors retry
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Tests
// ----------------------------------------------------------------------------

class CooperativeCancellationTests {

    @Test
    fun nonCooperativeLoopIgnoresCancellation() = runTest {
        val counter = AtomicInteger(0)
        val job = Job()
        val scope = CoroutineScope(coroutineContext + job)

        scope.launch { Cruncher(counter).crunchNonCooperative(chunks = 1_000) }
        scope.cancel()                       // ask it to stop IMMEDIATELY
        runCurrent()                         // let the dispatcher run the launched body

        // The loop never checked cancellation, so it ran to completion anyway.
        // This DOCUMENTS the bug: a non-cooperative loop ignores cancel().
        assertEquals(1_000, counter.get(), "expected the non-cooperative loop to finish despite cancel")
    }

    @Test
    fun ensureActiveStopsTheLoop() = runTest {
        val counter = AtomicInteger(0)
        val job = Job()
        val scope = CoroutineScope(coroutineContext + job)

        val work = scope.launch { Cruncher(counter).crunchEnsureActive(chunks = 1_000) }
        scope.cancel()
        runCurrent()

        assertTrue(work.isCancelled, "ensureActive() should have thrown CancellationException")
        assertTrue(counter.get() < 1_000, "ensureActive() should stop the loop early on cancel")
    }

    @Test
    fun isActiveStopsTheLoop() = runTest {
        val counter = AtomicInteger(0)
        val job = Job()
        val scope = CoroutineScope(coroutineContext + job)

        scope.launch { Cruncher(counter).crunchIsActive(chunks = 1_000) }
        scope.cancel()
        runCurrent()

        // isActive returns a partial result rather than throwing; either way the
        // loop stops short of all 1000.
        assertTrue(counter.get() < 1_000, "isActive should let the loop bail early on cancel")
    }

    @Test
    fun yieldStopsTheLoop() = runTest {
        val counter = AtomicInteger(0)
        val job = Job()
        val scope = CoroutineScope(coroutineContext + job)

        val work = scope.launch { Cruncher(counter).crunchYield(chunks = 1_000) }
        scope.cancel()
        runCurrent()

        assertTrue(work.isCancelled, "yield() should observe cancellation and stop")
        assertTrue(counter.get() < 1_000, "yield() should stop the loop early on cancel")
    }

    @Test
    fun rethrowFixesIt() = runTest {
        val attempts = AtomicInteger(0)
        val job = Job()
        val scope = CoroutineScope(coroutineContext + job)

        // This one fails twice then would succeed; we cancel mid-retry.
        val work = scope.launch { Retrier(attempts).loadRethrowing(failNTimes = 100) }
        advanceTimeBy(15)                    // let it fail once and enter a delay(10)
        scope.cancel()                       // cancel during the retry backoff
        advanceTimeBy(1_000)                 // give it lots of time to (not) keep going

        val countAtStop = attempts.get()
        advanceTimeBy(1_000)
        assertTrue(work.isCancelled, "rethrowing CancellationException lets cancel propagate")
        assertEquals(countAtStop, attempts.get(), "fixed retrier kept retrying after cancel")
    }
}

// ----------------------------------------------------------------------------
// WHY cancellation is cooperative, not pre-emptive (write it before reading):
//
//   cancel() does not yank execution out of your code — that would risk leaving
//   data half-written and locks held. Instead it flips the Job to "cancelling"
//   and relies on the coroutine reaching a CHECK: a suspension point (delay, yield,
//   await) or an explicit ensureActive()/isActive. Code that never reaches a check
//   never notices. So you, the author, must give long CPU work a place to check.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - `runCurrent()` runs everything currently scheduled on the test dispatcher
//   without advancing virtual time — perfect for "launch then immediately cancel,
//   then see what ran."
//
// - `ensureActive()` lives on CoroutineContext and on CoroutineScope. Inside a
//   suspend function, `kotlin.coroutines.coroutineContext.ensureActive()` works.
//
// - The non-cooperative test asserts the loop FINISHED (1000) despite cancel —
//   that's the bug, demonstrated, not a passing "fix." Don't try to make it stop;
//   the point is the contrast with the three fixes.
//
// - For the swallow bug, the key insight is `catch (Exception)` includes
//   `CancellationException`. Rethrow it first, or call ensureActive() at the top
//   of the catch. `runCatching {}` has the same trap — it catches Throwable.
//
// - If `ensureActiveStopsTheLoop` flakes, make sure you cancel BEFORE runCurrent()
//   so the very first check sees the cancellation.
//
// ----------------------------------------------------------------------------
