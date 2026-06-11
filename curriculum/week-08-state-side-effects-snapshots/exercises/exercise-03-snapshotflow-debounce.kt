// Exercise 3 — Bridge snapshot state to Flow with snapshotFlow, then debounce it
//
// Goal: Turn a snapshot-state query into a cold Flow with snapshotFlow, apply
//       Week 5's debounce + distinctUntilChanged + filter, and ASSERT the
//       emissions with Turbine. This is the mini-project's search pipeline,
//       isolated and tested without any UI.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// The `searchQueries` function below is pure pipeline logic over a Flow<String>,
// so you can unit-test it with Turbine + kotlinx-coroutines-test — no Compose
// runtime, no emulator. (In the app you feed it `snapshotFlow { query }`; in the
// test you feed it a plain flow of keystrokes.) Run with `./gradlew :app:test`.
//
//   1. Put searchQueries in app/src/main (or a shared module).
//   2. Put the @Test in app/src/test.
//   3. Add Turbine + coroutines-test to the test deps (see below).
//
// ACCEPTANCE CRITERIA
//
//   [ ] searchQueries debounces, drops duplicates, and ignores < 2-char queries.
//   [ ] The Turbine test asserts the debounced/deduped emissions deterministically.
//   [ ] Builds with 0 warnings.
//   [ ] You can explain why flatMapLatest (in the app) cancels the prior search.
//
// Test deps (app/build.gradle.kts):
//   testImplementation("app.cash.turbine:turbine:1.1.0")
//   testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package com.crunch.scratch.search

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

// ----------------------------------------------------------------------------
// THE PIPELINE — pure Flow logic. In the app, `raw` is `snapshotFlow { query }`.
// In the test, `raw` is a flow of keystrokes you control with virtual time.
// ----------------------------------------------------------------------------

@OptIn(FlowPreview::class)
fun searchQueries(raw: Flow<String>): Flow<String> =
    raw
        .map { it.trim() }              // normalize whitespace
        .debounce(300)                  // wait for the user to pause typing (Week 5)
        .distinctUntilChanged()         // ignore re-emits of the same query
        .filter { it.length >= 2 }      // don't search 0/1-char queries

// ----------------------------------------------------------------------------
// HOW IT LOOKS IN THE APP (for reference — not under test here):
//
//   @Composable
//   fun SearchScreen(repo: SearchRepository) {
//       var query by rememberSaveable { mutableStateOf("") }
//       var results by remember { mutableStateOf<List<Hit>>(emptyList()) }
//       LaunchedEffect(Unit) {
//           searchQueries(snapshotFlow { query })       // bridge snapshot -> Flow
//               .flatMapLatest { q ->                    // CANCEL prior search on new query
//                   flow { emit(repo.search(q)) }
//               }
//               .collect { results = it }                // push back into snapshot state
//       }
//       SearchUi(query, onQueryChange = { query = it }, results)
//   }
//
// flatMapLatest is the cancellation: when a new debounced query arrives, the
// in-flight repo.search(previous) is cancelled and a fresh one starts. No stale
// result ever overwrites a newer one.
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
// THE TEST — move to app/src/test/java/com/crunch/scratch/search/SearchTest.kt
//
//   import app.cash.turbine.test
//   import kotlinx.coroutines.flow.flow
//   import kotlinx.coroutines.delay
//   import kotlinx.coroutines.test.runTest
//   import kotlin.test.Test
//   import kotlin.test.assertEquals
//
//   class SearchTest {
//
//       @Test fun `debounces and dedupes, ignoring short queries`() = runTest {
//           // Simulate fast typing: "k","ko","kot","kotl","kotli","kotlin" with
//           // short gaps, then a pause. With debounce(300) only the settled value
//           // survives each burst.
//           val typing = flow {
//               emit("k");    delay(50)
//               emit("ko");   delay(50)
//               emit("kot");  delay(50)
//               emit("kotlin"); delay(400)   // pause -> debounce emits "kotlin"
//               emit("kotlin"); delay(400)   // same value -> distinctUntilChanged drops it
//               emit("go");   delay(400)      // new settled value -> emits "go"
//           }
//
//           searchQueries(typing).test {
//               assertEquals("kotlin", awaitItem())   // the burst settled to "kotlin"
//               assertEquals("go", awaitItem())        // duplicate "kotlin" dropped; "go" next
//               awaitComplete()
//           }
//       }
//
//       @Test fun `single-char queries are filtered out`() = runTest {
//           val typing = flow {
//               emit("a"); delay(400)         // 1 char -> filtered, no emission
//               emit("ab"); delay(400)        // 2 chars -> emits
//           }
//           searchQueries(typing).test {
//               assertEquals("ab", awaitItem())
//               awaitComplete()
//           }
//       }
//   }
//
// runTest gives virtual time, so debounce(300) resolves instantly in the test —
// no real 300ms waits. This is the deterministic Flow testing from Week 5.
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
// WHY flatMapLatest cancels the prior search (write it before reading):
//
//   flatMapLatest maps each upstream value to a new inner flow AND cancels the
//   previous inner flow when a new value arrives. So when "kotl" is superseded by
//   "kotlin", the repo.search("kotl") coroutine is cancelled mid-flight; only
//   repo.search("kotlin") completes. This guarantees the displayed results match
//   the LATEST query — no race where a slow old request lands after a fast new one.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - debounce/flatMapLatest are still marked @FlowPreview/experimental on some
//   versions; add @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class) as
//   the compiler directs.
//
// - In runTest, `delay` advances VIRTUAL time, so debounce(300) fires immediately
//   in test time. Don't use real Thread.sleep — it won't interact with the test
//   scheduler and the test will hang or flake.
//
// - Turbine: `flow.test { awaitItem(); awaitComplete() }`. If you forget
//   awaitComplete (or cancelAndIgnoreRemainingEvents), Turbine fails the test
//   for unconsumed events — that strictness is the point.
//
// - Order of operators matters: trim BEFORE distinctUntilChanged (so " k " and
//   "k" dedupe), and filter length AFTER debounce (so you don't waste debounce
//   windows on queries you'll discard).
//
// ----------------------------------------------------------------------------
