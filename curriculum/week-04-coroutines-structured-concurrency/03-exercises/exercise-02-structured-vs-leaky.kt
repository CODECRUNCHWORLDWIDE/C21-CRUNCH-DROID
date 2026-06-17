// Exercise 2 — Structured vs leaky: prove the leak with a test
//
// Goal: Implement the same "fan out N pieces of work" two ways — once the LEAKY
//       way with GlobalScope, once the STRUCTURED way with coroutineScope — and
//       prove, with assertions, that cancelling the owner stops the structured
//       version while the leaky one keeps running. The leak is not a vibe; it is
//       a counter that keeps incrementing after you said stop.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// This is a kotlinx-coroutines-test suite using `runTest`. Drop it into a
// Kotlin/JVM test source set (src/test/kotlin) with these on the test classpath:
//   org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0
//   kotlin("test")
//
//   1. Add this file to your test target.
//   2. Run with `./gradlew test` (or the IDE gutter).
//   3. Read the assertions: the structured fan-out leaves no live work after the
//      scope is cancelled; the leaky one is still running.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] `structuredWorkStopsWhenScopeCancelled` passes: after cancel, the
//       structured counter does not advance.
//   [ ] `leakyWorkKeepsRunningAfterOwnerGone` passes: it DEMONSTRATES the leak —
//       the GlobalScope counter keeps advancing after the "owner" is gone.
//   [ ] `structuredScopeWaitsForChildren` passes: coroutineScope returns only
//       after all children complete.
//   [ ] You can explain, in one sentence, WHY GlobalScope leaks.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.droid

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ----------------------------------------------------------------------------
// A tiny "unit of work": ticks a shared counter every 10ms, forever, until it
// is cancelled. How long it keeps ticking after `cancel()` is the whole test.
// ----------------------------------------------------------------------------

class Ticker(private val counter: AtomicInteger) {
    suspend fun tickForever() {
        while (true) {
            // delay is a suspension point, so a well-behaved coroutine cancels here.
            delay(10)
            counter.incrementAndGet()
        }
    }
}

// ----------------------------------------------------------------------------
// THE LEAKY WAY. GlobalScope has no parent you can cancel. Launching work here
// means nothing owns it; when the caller "goes away" the work keeps running.
// ----------------------------------------------------------------------------

@Suppress("OPT_IN_USAGE")   // GlobalScope is opt-in; we use it ON PURPOSE to show the leak.
class LeakyFanOut(private val counter: AtomicInteger) {
    // Returns immediately; the launched coroutine is orphaned in GlobalScope.
    fun start(workers: Int): List<Job> =
        (0 until workers).map {
            GlobalScope.launch { Ticker(counter).tickForever() }
        }
    // NB: there is no `cancelAll()` that the OWNER controls. That's the bug.
}

// ----------------------------------------------------------------------------
// THE STRUCTURED WAY. The work is launched into a scope the caller OWNS and can
// cancel. Cancelling the scope cancels every child. No orphans.
// ----------------------------------------------------------------------------

class StructuredFanOut(private val counter: AtomicInteger) {
    // The caller passes in the scope it owns; we launch children into it.
    fun start(scope: CoroutineScope, workers: Int) {
        repeat(workers) {
            scope.launch { Ticker(counter).tickForever() }
        }
    }
}

// ----------------------------------------------------------------------------
// The test suite
// ----------------------------------------------------------------------------

class StructuredVsLeakyTests {

    @Test
    fun structuredWorkStopsWhenScopeCancelled() = runTest {
        val counter = AtomicInteger(0)
        // A scope we OWN. Its Job is a child of the test's scope here, but we hold
        // the handle and cancel it ourselves.
        val ownedJob = Job()
        val ownedScope = CoroutineScope(coroutineContext + ownedJob)

        StructuredFanOut(counter).start(ownedScope, workers = 5)

        advanceTimeBy(55)             // ~5 ticks per worker
        val before = counter.get()
        assertTrue(before > 0, "work should have started")

        ownedScope.cancel()           // cancel the OWNER -> all 5 children cancelled
        val atCancel = counter.get()

        advanceTimeBy(1_000)          // let lots of virtual time pass
        val after = counter.get()

        // The counter MUST NOT advance after cancellation, because every child
        // hit `delay` (a suspension point) and saw the cancellation.
        assertEquals(atCancel, after, "structured work kept running after cancel — it leaked!")
    }

    @Test
    fun leakyWorkKeepsRunningAfterOwnerGone() = runTest {
        val counter = AtomicInteger(0)
        val leaky = LeakyFanOut(counter)

        // "Start the work and walk away" — we don't keep anything to cancel.
        val orphans = leaky.start(workers = 5)

        advanceTimeBy(55)
        val before = counter.get()
        assertTrue(before > 0, "leaky work should have started")

        // There is no owner to cancel. The work is orphaned in GlobalScope and
        // keeps ticking. We DEMONSTRATE the leak, then clean up by hand so the
        // test process doesn't keep counting forever.
        advanceTimeBy(1_000)
        val after = counter.get()
        assertTrue(after > before, "the leak: orphaned work kept advancing with no owner to stop it")

        orphans.forEach { it.cancel() }   // manual cleanup — exactly what we shouldn't have to do
    }

    @Test
    fun structuredScopeWaitsForChildren() = runTest {
        val counter = AtomicInteger(0)
        var returnedAt = -1

        // coroutineScope returns only AFTER all children complete (lecture 1, §5 rule 1).
        coroutineScope {
            repeat(3) { i ->
                launch {
                    delay((i + 1) * 10L)     // 10ms, 20ms, 30ms
                    counter.incrementAndGet()
                }
            }
            // We are still inside coroutineScope here; it has NOT returned yet.
            assertTrue(isActive)
        }
        // We reach here only after all three children finished.
        returnedAt = counter.get()
        assertEquals(3, returnedAt, "coroutineScope returned before its children completed")
    }
}

// ----------------------------------------------------------------------------
// WHY GlobalScope leaks (write it in your own words before reading):
//
//   A coroutine launched in GlobalScope has no parent Job that anyone holds and
//   cancels. Its lifetime is the whole process. Structured concurrency relies on
//   a parent-child Job tree so that cancelling an owner cancels its children;
//   GlobalScope is the root of nothing you control, so there is no owner to cancel
//   and the work runs until the process dies (or you hunt down every Job by hand).
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - `runTest` uses VIRTUAL time. `delay(10)` doesn't really wait 10ms; you advance
//   the clock with `advanceTimeBy(...)`. That's why the tests are fast and
//   deterministic — no real sleeping, no flake.
//
// - Build the owned scope as `CoroutineScope(coroutineContext + Job())` so it runs
//   on the test dispatcher (virtual time) but has its OWN Job you can cancel
//   independently of the test's scope. A plain `Job()` parented under the test
//   scheduler keeps `delay` on virtual time.
//
// - `AtomicInteger` because the counter is touched from coroutines that may resume
//   on different threads under a real dispatcher. Under `runTest` it's single-
//   threaded, but using the atomic keeps the example honest and warning-free.
//
// - The leaky test asserts `after > before` — i.e. it PROVES the leak rather than
//   pretending the leak is fine. The lesson is the contrast, not that GlobalScope
//   "works."
//
// - `@OptIn`/`@Suppress("OPT_IN_USAGE")` is needed because GlobalScope is a
//   delicate API. We opt in deliberately to demonstrate the anti-pattern; in real
//   code, the absence of that opt-in is the compiler nudging you away from it.
//
// ----------------------------------------------------------------------------
