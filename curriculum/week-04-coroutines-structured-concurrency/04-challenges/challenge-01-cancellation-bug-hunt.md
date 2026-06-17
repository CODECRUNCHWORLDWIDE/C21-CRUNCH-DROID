# Challenge 1 — The cancellation bug hunt (find, explain, fix, prove)

**Time.** 60–120 minutes.
**Deliverable.** A `BUGS.md` write-up (root cause + fix for each of three bugs) plus the fixed code and three passing tests, committed to your Week 04 repo.

## The premise

Every senior engineer has shipped a cancellation bug. It works perfectly in the demo, then a user backs out at the wrong moment and a coroutine keeps writing to a dead screen, or burns a core for ten seconds after you said stop, or leaks until the process dies. The skill this challenge builds is not "know the bugs exist" — it is **reproduce it with a failing test, explain the root cause in one paragraph, fix it, and prove the fix with a passing test.** A fix you can't prove is a guess.

You will start from a small `LinkFetcher` that has all three canonical cancellation bugs planted in it. Your job is to find each, write a test that *fails* because of it, fix the code, and watch the test pass.

## The starting code (copy this in, bugs and all)

```kotlin
package com.crunch.droid

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

// A fake "network": each fetch is a delay then a result, and it counts how many
// fetches actually ran so a test can see whether work kept going after cancel.
class FakeNet(val fetches: AtomicInteger = AtomicInteger(0)) {
    suspend fun fetch(url: String): String {
        delay(50)
        fetches.incrementAndGet()
        return "body-of:$url"
    }
}

class LinkFetcher(private val net: FakeNet) {

    // BUG 1 — a "normalise" step that does CPU work in a loop with no cancellation
    // check. On a big input it ignores cancel().
    fun normalise(urls: List<String>): List<String> {
        val out = ArrayList<String>(urls.size)
        for (u in urls) {
            // pretend this is expensive per-item work
            var s = u.trim().lowercase()
            repeat(50_000) { s = s.reversed().reversed() }   // burn CPU, never checks isActive
            out.add(s)
        }
        return out
    }

    // BUG 2 — a retry wrapper whose blanket catch swallows CancellationException.
    suspend fun fetchWithRetry(url: String): String {
        while (true) {
            try {
                return net.fetch(url)
            } catch (e: Exception) {       // <- swallows cancellation
                delay(10)
            }
        }
    }

    // BUG 3 — fan-out launched into GlobalScope: nothing owns it, nothing cancels it.
    @Suppress("OPT_IN_USAGE")
    fun fetchAll(urls: List<String>): List<Deferred<String>> =
        urls.map { GlobalScope.async { fetchWithRetry(it) } }
}
```

## Step 1 — Bug 3 first: the leak (it's the most dangerous)

Write a `runTest` that starts `fetchAll`, cancels the owner, and asserts the fetch counter does **not** advance afterwards. With `GlobalScope`, it *will* advance — your test fails, demonstrating the leak.

```kotlin
@Test
fun fetchAllShouldStopWhenOwnerCancelled() = runTest {
    val net = FakeNet()
    val fetcher = LinkFetcher(net)
    val urls = List(20) { "https://example.com/$it" }

    // ... start the work owned by a scope you control, cancel it,
    //     advance virtual time, and assert net.fetches stops advancing ...
}
```

**The fix:** `fetchAll` must launch into a scope the *caller* owns, not `GlobalScope`. Change the signature to take a `CoroutineScope` (or make `LinkFetcher` hold a scope it exposes a `cancel()` for, or — cleanest — make `fetchAll` a `suspend fun` that uses `coroutineScope { ... }` so it is bounded by its caller). Re-run: the counter stops on cancel.

## Step 2 — Bug 2: the swallowed cancellation

Write a test that calls `fetchWithRetry` on a URL, cancels mid-retry (force the first fetch to fail by injecting a net that throws N times), and asserts the coroutine actually cancels. With the blanket `catch`, cancellation is swallowed and the retry loop runs forever — your test hangs or times out.

**The fix:** rethrow `CancellationException` (or `currentCoroutineContext().ensureActive()`) before the `catch (Exception)` retry branch. Re-run: the coroutine cancels promptly.

You'll need a net that can be told to fail:

```kotlin
class FlakyNet(private var failsLeft: Int) : ... {
    suspend fun fetch(url: String): String {
        delay(50)
        if (failsLeft > 0) { failsLeft--; throw java.io.IOException("flaky") }
        return "ok:$url"
    }
}
```

## Step 3 — Bug 1: the non-cooperative CPU loop

Write a test that runs `normalise` on a large list inside a launched coroutine, cancels immediately, and asserts the work stops early. With the un-checked loop it runs to completion — your assertion that it stopped early fails.

**The fix:** make `normalise` a `suspend fun` and call `ensureActive()` (or check `isActive`, or `yield()` periodically) inside the loop — every iteration is fine here, or every few hundred for a truly hot loop. Re-run: cancel stops it early.

## Step 4 — Write it up

`BUGS.md`, one short section per bug:

- **Bug name** (e.g. "non-cooperative CPU loop").
- **Symptom** — what the user sees (work keeps running after back-out / app leaks / wasted battery).
- **Root cause** — one paragraph in your own words, tied to the lecture (cooperative cancellation, `CancellationException` is an `Exception`, `GlobalScope` has no owner).
- **The fix** — the diff in words, and which test now proves it.

## Acceptance criteria

- [ ] Three tests that each **failed** against the buggy code and **pass** against the fixed code (keep a note of the before/after).
- [ ] Bug 1 fixed with a cooperative check (`ensureActive`/`isActive`/`yield`).
- [ ] Bug 2 fixed by rethrowing `CancellationException` (or `ensureActive()`), with real errors still retried.
- [ ] Bug 3 fixed by removing `GlobalScope` and binding the fan-out to a caller-owned scope (`coroutineScope { }` or an injected `CoroutineScope`).
- [ ] `BUGS.md` documents symptom + root cause + fix for each, in your own words.
- [ ] Build with **0 warnings** (the only `@OptIn`/`@Suppress` allowed is the one demonstrating the `GlobalScope` bug *before* you fix it; the fixed code has none).

## What "great" looks like

A weak submission says "I removed GlobalScope and it works." A great submission says:

> **Bug 3 (leak).** Symptom: after the user leaves the screen, the fetch counter kept climbing — 20 fetches completed against a screen that was already gone. Root cause: `fetchAll` launched each `async` in `GlobalScope`, whose `Job` is the process-lifetime root nobody holds; structured concurrency's "cancel the owner, cancel the children" never applied because there was no owner. Fix: `fetchAll` is now `suspend fun fetchAll(...) = coroutineScope { urls.map { async { fetchWithRetry(it) } }.awaitAll() }`, so the fan-out is a child of the caller's scope; `fetchAllShouldStopWhenOwnerCancelled` went from 20 fetches after cancel to 0.

Quantified, explained, and tied to the mechanism. That's the senior-engineer answer — and it is almost verbatim the career-pack interview drill "coroutines pitfalls: three real production bugs and the fix for each."

## Where this reappears

These exact three bugs recur in Phase 2 (`viewModelScope` and `repeatOnLifecycle` are the structured-scope answer to bug 3 on Android) and in the capstone's chaos drills. The "reproduce with a failing test, then fix" workflow is the one you'll use every time a coroutine misbehaves for the rest of the track.
