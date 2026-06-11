// Exercise 2 — flatMapLatest search-as-you-type: prove the stale result never shows
//
// Goal: Build a debounced search where a new query CANCELS the in-flight request
//       for the previous query and starts the new one, using flatMapLatest. Then
//       prove with Turbine that the stale query's result is never emitted — a thing
//       you can only test deterministically, which is the whole point.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// A Turbine + kotlinx-coroutines-test suite. Drop into src/test/kotlin with
// kotlinx-coroutines-test and app.cash.turbine:turbine on the test classpath.
//
//   1. Add to your test target.
//   2. Run with `./gradlew test`.
//   3. Read the assertions: only the latest query's result survives; the slow,
//      stale query's result is cancelled and never emitted.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] `latestQueryWins` passes — a fast follow-up query cancels the slow one.
//   [ ] `debounceCollapsesRapidTyping` passes — rapid keystrokes collapse to the
//       final query.
//   [ ] `blankQueryClearsResults` passes — an empty query emits empty results
//       without hitting the "network".
//   [ ] You can explain WHY flatMapLatest, not flatMapMerge, is correct here.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.droid

import app.cash.turbine.test
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

// ----------------------------------------------------------------------------
// A fake search API. Each query "takes" `latencyFor(query)` ms to return, so we
// can make one query slow and the next fast and watch flatMapLatest cancel the
// slow one. It counts how many searches actually COMPLETED (returned a result),
// so a test can prove the cancelled one never finished.
// ----------------------------------------------------------------------------

class FakeSearchApi(
    private val latencyFor: (String) -> Long,
) {
    val completed = AtomicInteger(0)

    fun search(query: String): Flow<List<String>> = flow {
        delay(latencyFor(query))                 // simulate network latency
        completed.incrementAndGet()              // got here only if NOT cancelled
        emit(List(3) { "$query-result-$it" })    // three hits for the query
    }
}

// ----------------------------------------------------------------------------
// The search pipeline. This is lecture 1, §3 verbatim: debounce, distinct, then
// flatMapLatest so a new query cancels the previous request.
// ----------------------------------------------------------------------------

class Searcher(private val api: FakeSearchApi) {
    fun results(queries: Flow<String>): Flow<List<String>> =
        queries
            .debounce(100.milliseconds)          // wait for typing to settle
            .distinctUntilChanged()              // ignore non-changes
            .flatMapLatest { query ->            // CANCEL the previous query's request
                if (query.isBlank()) flowOf(emptyList())
                else api.search(query)
            }
}

// ----------------------------------------------------------------------------
// Tests
// ----------------------------------------------------------------------------

class FlatMapLatestSearchTests {

    @Test
    fun latestQueryWins() = runTest {
        // "slow" takes 1000ms; "fast" takes 10ms. We emit slow then, after a short
        // pause (past debounce), fast. flatMapLatest must cancel slow when fast lands.
        val api = FakeSearchApi(latencyFor = { q -> if (q == "slow") 1_000 else 10 })
        val queries = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val searcher = Searcher(api)

        searcher.results(queries).test {
            queries.emit("slow")
            delay(150)                           // past debounce; "slow" request starts
            queries.emit("fast")                 // arrives mid-flight -> cancels "slow"
            delay(150)                           // past debounce; "fast" runs and returns

            // The ONLY result we see is for "fast". The "slow" result was cancelled
            // before its 1000ms delay elapsed, so it never emitted.
            assertEquals(List(3) { "fast-result-$it" }, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // Proof the stale request never completed: only ONE search finished ("fast").
        assertEquals(1, api.completed.get(), "the cancelled 'slow' query must never complete")
    }

    @Test
    fun debounceCollapsesRapidTyping() = runTest {
        val api = FakeSearchApi(latencyFor = { 10 })
        val queries = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val searcher = Searcher(api)

        searcher.results(queries).test {
            // Type k -> ko -> kot -> kotlin FAST, all within the debounce window.
            queries.emit("k"); delay(20)
            queries.emit("ko"); delay(20)
            queries.emit("kot"); delay(20)
            queries.emit("kotlin")
            delay(200)                           // now let debounce fire on the last one

            // Only "kotlin" survives debounce -> only one search runs.
            assertEquals(List(3) { "kotlin-result-$it" }, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, api.completed.get(), "rapid typing should collapse to ONE search")
    }

    @Test
    fun blankQueryClearsResults() = runTest {
        val api = FakeSearchApi(latencyFor = { 10 })
        val queries = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val searcher = Searcher(api)

        searcher.results(queries).test {
            queries.emit("")
            delay(200)
            assertEquals(emptyList(), awaitItem())   // empty results, no network
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, api.completed.get(), "a blank query must not hit the network")
    }
}

// ----------------------------------------------------------------------------
// WHY flatMapLatest and not flatMapMerge (write it before reading):
//
//   flatMapMerge would START a request for every query and merge all their results
//   as they arrive — so "slow" and "fast" both run, and "slow" (returning last after
//   its 1000ms) would overwrite the screen with results for a query the user already
//   replaced. flatMapLatest CANCELS the inner flow for the previous query the moment
//   a new query arrives, so only the latest request's result can ever be emitted.
//   For latest-wins UI (search, location, "show the newest"), cancel-previous is the
//   correct semantic; merge would ship the stale-result flicker bug.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - `debounce` and `delay` run on VIRTUAL time under runTest, so the 100ms debounce
//   and 1000ms latency cost no real time and are perfectly deterministic.
//
// - Use a MutableSharedFlow(extraBufferCapacity = ...) as the query source so you
//   can `emit` into it from the test while Turbine collects downstream. A plain
//   flowOf can't interleave emits with assertions.
//
// - `completed` is the proof. The cancelled "slow" search never reaches
//   `completed.incrementAndGet()` because flatMapLatest cancels its `delay(1000)`
//   before it elapses. Assert `completed == 1`, not just the emitted value.
//
// - flatMapLatest needs ExperimentalCoroutinesApi in some versions; it is stable in
//   1.9. If your version warns, you may need `@OptIn(ExperimentalCoroutinesApi::class)`
//   on the test class — but prefer bumping coroutines to a version where it's stable.
//
// - If `latestQueryWins` flakes, make sure the gap after "slow" (delay 150) exceeds
//   the 100ms debounce so "slow"'s request actually STARTS before "fast" cancels it
//   — the point is cancelling an in-flight request, not debouncing it away.
//
// ----------------------------------------------------------------------------
