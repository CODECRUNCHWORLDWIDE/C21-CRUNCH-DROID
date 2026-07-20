# Mini-Project — A bounded, cancellable parallel downloader

This week you build a coroutine-based **parallel downloader** that fetches 100 URLs concurrently with *bounded* parallelism, supports cancellation the instant the user asks, and prints structured progress. It is small, but it exercises every idea in the week end-to-end: a scope that owns the work, a `Semaphore` that bounds it, cooperative cancellation that actually stops the downloads, exception handling that distinguishes "one URL failed" from "the whole job is dead," and a `runTest` suite that proves it never leaks a coroutine.

This is a *plain JVM Kotlin* project — a Gradle application plus a test source set. No Android, no emulator. The point of the week is structured concurrency as a discipline; you learn it cleanest without the lifecycle noise, and you collect the Android scopes (`viewModelScope`, `lifecycleScope`) in Phase 2 once the fundamentals are reflexes.

---

## Where you're starting from

Nothing to migrate this week — this is a fresh, self-contained tool. You need a Gradle Kotlin/JVM project with:

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.0"
    application
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation(kotlin("test"))
}
application { mainClass.set("com.crunch.droid.downloader.MainKt") }
kotlin { jvmToolchain(21) }
```

## What you're building toward

By the end you have:

- A `Downloader` that fetches a list of URLs concurrently, **at most N at a time** (default 8) via a `Semaphore`.
- **Per-URL outcomes** modelled as a sealed `DownloadResult` (`Success` with bytes/size, `Failure` with the cause) — Week 2's modelling discipline applied to concurrency.
- **Cancellation** that stops in-flight downloads cooperatively the instant the caller cancels — proven in a test.
- **Structured progress**: a callback (or a returned summary) reporting completed/total/failed as work proceeds, never racing.
- A `runTest` suite proving: same answer regardless of concurrency bound, cancellation stops the work, one failure does not sink the others, and no coroutine outlives the scope.

The downloader is abstracted over an injected `Fetch` function so tests use a fake (with virtual-time `delay`) and real use plugs in an HTTP client. We keep it client-agnostic this week — wiring OkHttp/Ktor is Phase 3.

---

## Milestone 1 — Model the outcome and the fetch boundary (≈ 1.5 h)

A download either succeeds with bytes or fails with a cause. Model it as a sealed hierarchy and an exhaustive `when` — exactly Week 2.

```kotlin
package com.crunch.droid.downloader

// The seam we inject so tests can fake the network. Real impl wraps an HTTP client.
fun interface Fetch {
    suspend fun fetch(url: String): ByteArray
}

sealed interface DownloadResult {
    val url: String

    data class Success(override val url: String, val bytes: Int) : DownloadResult
    data class Failure(override val url: String, val cause: Throwable) : DownloadResult
}

// A structured progress snapshot. Immutable; emitted as work completes.
data class Progress(val completed: Int, val total: Int, val failed: Int) {
    val remaining: Int get() = total - completed
    val percent: Int get() = if (total == 0) 100 else completed * 100 / total
}
```

Decisions you must be able to defend in review:

- **Why a sealed `DownloadResult` and not exceptions everywhere?** A failed download is a *normal outcome* of a batch, not an exceptional one — 3 of 100 URLs 404ing should not throw the whole batch away. Modelling it as data (a `Failure` you collect) lets the batch complete and report partial success, which is the correct behaviour. Real "the whole job is dead" cases (cancellation) still propagate as exceptions.
- **Why `fun interface Fetch`?** It is the testability seam (Week 3's SAM conversion). The downloader never knows whether it's talking to a real socket or a fake with `delay`; tests inject determinism.

## Milestone 2 — The bounded, structured download (≈ 2.5 h)

The core. Fetch all URLs concurrently, bounded to `maxConcurrent`, collecting per-URL results, never leaking.

```kotlin
package com.crunch.droid.downloader

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

class Downloader(
    private val fetch: Fetch,
    private val maxConcurrent: Int = 8,
) {
    /**
     * Download every URL concurrently, at most [maxConcurrent] in flight.
     * Returns a result PER url (success or failure). The whole call is bounded
     * by the caller's scope: cancel the caller, every in-flight download stops.
     *
     * @param onProgress called as each url completes, with a structured snapshot.
     */
    suspend fun downloadAll(
        urls: List<String>,
        onProgress: (Progress) -> Unit = {},
    ): List<DownloadResult> = coroutineScope {       // structured: bounded by the caller
        val gate = Semaphore(permits = maxConcurrent)
        val completed = AtomicInteger(0)
        val failed = AtomicInteger(0)

        urls.map { url ->
            async {
                gate.withPermit {                     // at most maxConcurrent here at once
                    val result = runFetch(url)
                    // Update progress atomically, then emit a consistent snapshot.
                    val done = completed.incrementAndGet()
                    val fails = if (result is DownloadResult.Failure) failed.incrementAndGet()
                                else failed.get()
                    onProgress(Progress(completed = done, total = urls.size, failed = fails))
                    result
                }
            }
        }.awaitAll()
    }

    // One URL: a failure is a DATA outcome (collected), but cancellation must
    // still propagate (it is NOT a per-url failure).
    private suspend fun runFetch(url: String): DownloadResult =
        try {
            val bytes = fetch.fetch(url)
            DownloadResult.Success(url, bytes.size)
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()  // rethrow cancellation — don't swallow it
            DownloadResult.Failure(url, e)            // real errors become data
        }
}
```

The four week-defining moves, all in one function:

1. **`coroutineScope { }`** binds the whole batch to the caller. There is no `GlobalScope`, no orphan. Cancel the caller and every `async` is cancelled. This is the "never leak a coroutine" promise.
2. **`Semaphore(maxConcurrent)` + `withPermit`** bounds concurrency. All 100 `async`s start, but only `maxConcurrent` hold a permit and actually download; the rest *suspend* at `withPermit` (releasing their thread) until a slot frees. "100 URLs" is never "100 sockets."
3. **`ensureActive()` in the catch** is the cooperative-cancellation discipline from lecture 02: a blanket `catch (Throwable)` would swallow `CancellationException` and turn a cancelled download into a fake `Failure`. We rethrow cancellation first; only real errors become `Failure` data.
4. **The sealed `DownloadResult`** means one URL 404ing is collected as a `Failure` and the batch completes — partial success, not all-or-nothing. (If you *wanted* all-or-nothing, you'd let the throw propagate out of `runFetch` and `coroutineScope` would cancel the siblings.)

## Milestone 3 — Cancellation, proven (≈ 1.5 h)

The acceptance bar for the week: the downloader must stop the instant the caller cancels. Prove it with a test, not a vibe.

```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue

@Test
fun cancellingTheOwnerStopsInFlightDownloads() = runTest {
    val started = AtomicInteger(0)
    val finished = AtomicInteger(0)
    val slowFetch = Fetch { url ->
        started.incrementAndGet()
        delay(1_000)                  // long enough that cancel lands mid-flight
        finished.incrementAndGet()
        ByteArray(10)
    }
    val downloader = Downloader(slowFetch, maxConcurrent = 4)
    val job = Job()
    val scope = CoroutineScope(coroutineContext + job)

    scope.launch { downloader.downloadAll(List(100) { "u$it" }) }
    advanceTimeBy(50)                 // 4 downloads in flight, none finished
    scope.cancel()                    // user backs out
    advanceTimeBy(5_000)              // plenty of time to (not) finish

    assertTrue(started.get() in 1..8, "only the bounded few should have started")
    assertTrue(finished.get() == 0, "no download should finish after cancel — work leaked!")
}
```

Note the structured payoff: you bounded concurrency to 4, so at the moment of cancel only ~4 downloads were in flight, and **none** of them finish because cancellation reaches each `delay` and unwinds. A leaky implementation would let all 100 run to completion.

## Milestone 4 — Failure isolation and progress (≈ 1.5 h)

Prove that one URL failing does not sink the batch, and that progress is reported correctly.

```kotlin
@Test
fun oneFailureDoesNotSinkTheBatch() = runTest {
    val fetch = Fetch { url ->
        delay(10)
        if (url.endsWith("3") || url.endsWith("7")) throw java.io.IOException("boom on $url")
        ByteArray(url.length)
    }
    val downloader = Downloader(fetch, maxConcurrent = 5)

    val snapshots = mutableListOf<Progress>()
    val results = downloader.downloadAll(List(10) { "u$it" }) { snapshots += it }

    val failures = results.filterIsInstance<DownloadResult.Failure>()
    val successes = results.filterIsInstance<DownloadResult.Success>()

    assertTrue(failures.size == 2, "u3 and u7 should fail, the rest succeed")
    assertTrue(successes.size == 8, "the other 8 must still succeed — failure is isolated")
    assertTrue(snapshots.last().completed == 10, "final progress should report all 10 completed")
    assertTrue(snapshots.last().failed == 2, "final progress should report 2 failed")
}
```

Then write the CLI entry point so you can run it for real (point `Fetch` at a real HTTP call — `java.net.http.HttpClient` is in the JDK and needs no dependency):

```kotlin
package com.crunch.droid.downloader

import kotlinx.coroutines.runBlocking
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

fun main(args: Array<String>) = runBlocking {
    val urls = if (args.isNotEmpty()) args.toList()
               else List(20) { "https://httpbin.org/bytes/${(it + 1) * 100}" }

    val client = HttpClient.newHttpClient()
    val fetch = Fetch { url ->
        // Blocking HTTP belongs on Dispatchers.IO; runFetch is called inside the
        // downloader, which you can dispatch to IO via withContext if you wish.
        val req = HttpRequest.newBuilder(URI.create(url)).GET().build()
        val resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray())
        resp.body()
    }

    val downloader = Downloader(fetch, maxConcurrent = 8)
    val results = downloader.downloadAll(urls) { p ->
        println("[${p.percent}%] ${p.completed}/${p.total} done, ${p.failed} failed")
    }

    val ok = results.count { it is DownloadResult.Success }
    val bytes = results.filterIsInstance<DownloadResult.Success>().sumOf { it.bytes }
    println("Done: $ok/${results.size} succeeded, $bytes bytes total")
}
```

(For the real HTTP path, wrap the blocking `client.send` in `withContext(Dispatchers.IO)` inside your `Fetch` so you don't block the `Default`/main caller — that's the dispatcher discipline from lecture 1, §6.)

## Milestone 5 — The bound matters (≈ 0.5 h)

Add a test proving the *answer* is identical regardless of `maxConcurrent`, so bounding concurrency is purely a resource decision, not a correctness one:

```kotlin
@Test
fun resultIsIdenticalRegardlessOfBound() = runTest {
    val fetch = Fetch { url -> delay(5); ByteArray(url.length) }
    val urls = List(50) { "url-$it" }

    val with2 = Downloader(fetch, 2).downloadAll(urls).map { it.url }.sorted()
    val with16 = Downloader(fetch, 16).downloadAll(urls).map { it.url }.sorted()

    assertTrue(with2 == with16, "the bound changes throughput, never the result set")
}
```

---

## Acceptance criteria

- [ ] `DownloadResult` is a sealed hierarchy (`Success`/`Failure`); the batch reports a result **per URL** and completes even when some fail.
- [ ] `downloadAll` is bounded by a `Semaphore` (or `limitedParallelism`) to `maxConcurrent` — "100 URLs" never means "100 concurrent fetches."
- [ ] The whole batch is wrapped in `coroutineScope { }` (no `GlobalScope`, no orphan); cancelling the caller stops in-flight work.
- [ ] The per-URL `catch` rethrows `CancellationException`/`ensureActive()` before turning a real error into a `Failure`.
- [ ] A `runTest` suite proves: cancellation stops in-flight downloads, one failure doesn't sink the batch, progress is reported correctly, and the result set is independent of the bound.
- [ ] A runnable `main` that downloads real URLs with structured progress output.
- [ ] Build with **0 warnings, 0 errors** (no `@OptIn(DelicateCoroutinesApi)` — there is no `GlobalScope` in the finished project).

## Stretch goals

- **Retry with backoff.** Add per-URL exponential-backoff retry (`delay(base * 2^attempt)`), capped at N attempts, rethrowing `CancellationException` so a cancel during backoff still stops. This is the exact pattern the capstone's WorkManager sync uses.
- **Timeout per download.** Wrap each `runFetch` in `withTimeout(...)`; a timed-out URL becomes a `Failure(TimeoutCancellationException)` — note that `withTimeout` throws a `CancellationException` subtype, so handle it deliberately, not by swallowing.
- **A `Flow` of progress (preview of Week 5).** Instead of an `onProgress` callback, expose progress as a `Flow<Progress>` via `channelFlow`. Don't go deep — just feel the shape; Week 5 is all of it.
- **Dispatcher discipline.** Run the real `Fetch` on `Dispatchers.IO` and assert (with a custom dispatcher in the test) that no fetch runs on the main/default thread.

## What this milestone earns you

You can now decompose concurrent work the structured way: bounded, cancellable, failure-isolated, and leak-free, with tests that prove each property instead of hoping. That is the literal "skills earned" line for the week — structured concurrency as a discipline, cancellation cooperation, and the right dispatcher for the right work. Week 5 takes this exact downloader shape and turns the one-shot batch into a *stream* of progress and results with `Flow`, `StateFlow`, and `channelFlow`; you'll be glad the coroutine fundamentals are solid before you add time-varying streams on top.
