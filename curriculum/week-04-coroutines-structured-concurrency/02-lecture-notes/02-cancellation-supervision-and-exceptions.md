# Lecture 2 — Cancellation, supervision, and the exceptions that ship to users

Lecture 1 gave you the transform and the happy-path tree. This lecture is about the three things that actually take coroutine code down in production: a **cancellation that doesn't happen** (a coroutine that ignores the request to stop and keeps burning CPU or writing to a dead screen), a **supervision choice that's wrong** (one child's failure nuking the whole screen, or a failure silently swallowed), and an **exception that vanishes** (a throw inside `async` you never `await`, or a `CoroutineExceptionHandler` placed where it never fires). These are not edge cases. They are the bugs every Android engineer ships at least once. Everything here is in service of "the coroutine stops when you tell it to, fails where you can see it, and never leaks."

We take them in the order you hit them: cancellation first (because it is the most counter-intuitive), then supervision (because it decides the blast radius of a failure), then exceptions (because the rules differ between `launch` and `async` in ways that surprise people).

---

## 1. Cancellation is cooperative — and that word is doing all the work

Here is the fact that trips up everyone coming from threads: **cancelling a coroutine does not forcibly stop it.** `job.cancel()` does not yank execution out of your code. It sets the `Job` to a *cancelling* state and asks the coroutine, politely, to stop at its next opportunity. If your code never gives it an opportunity, it never stops.

What counts as "an opportunity"? **Every suspension point in `kotlinx.coroutines` checks for cancellation.** `delay`, `withContext`, `await`, `yield`, and any well-written `suspend` function will, on resume, see that the `Job` is cancelled and throw `CancellationException` to unwind. So code that suspends regularly cancels promptly and for free:

```kotlin
suspend fun pollUntilReady() {
    while (true) {
        val status = api.checkStatus()   // suspends; checks cancellation on resume
        if (status.ready) return
        delay(1.seconds)                 // suspends; throws CancellationException if cancelled
    }
}
// Cancel the scope mid-poll and this stops at the next delay/await. Cooperative, automatic.
```

But code that does **not** suspend — a tight CPU loop — never reaches a check, so cancellation is ignored:

```kotlin
// THE BUG: a CPU loop with no suspension point never notices cancellation.
suspend fun crunch(): Long {
    var acc = 0L
    for (i in 0..10_000_000_000L) {
        acc += expensive(i)     // pure CPU; no suspend, no cancellation check
    }
    return acc                  // cancel() does NOTHING until this whole loop finishes
}
```

You cancel the scope, the loop keeps running to completion, and your "cancelled" work burns a core for ten more seconds. The fix is to *cooperate*: give the coroutine a chance to check.

### The three ways to make a loop cooperative

```kotlin
// Option A — ensureActive(): throws CancellationException if cancelled. Cheapest explicit check.
suspend fun crunchA(): Long {
    var acc = 0L
    for (i in 0..10_000_000_000L) {
        ensureActive()          // throws if the Job is cancelled
        acc += expensive(i)
    }
    return acc
}

// Option B — isActive: a Boolean; lets you bail gracefully (return a partial result, log).
suspend fun crunchB(): Long {
    var acc = 0L
    for (i in 0..10_000_000_000L) {
        if (!coroutineContext.isActive) return acc   // graceful early exit
        acc += expensive(i)
    }
    return acc
}

// Option C — yield(): a suspension point that also lets other coroutines run. Use in a
// busy loop you want to share the thread fairly, not just check cancellation.
suspend fun crunchC(): Long {
    var acc = 0L
    for (i in 0..10_000_000_000L) {
        if (i % 10_000 == 0L) yield()   // periodic suspension: checks cancellation AND yields
        acc += expensive(i)
    }
    return acc
}
```

`ensureActive()` is the default reach: cheap, throws the right exception, unwinds cleanly. Use `isActive` when you want to *return a partial result* instead of throwing. Use `yield()` when the loop is so hot it would otherwise hog the dispatcher thread. Checking every single iteration is wasteful for a truly tight loop — check every N iterations (`i % 1024 == 0`), enough to react within a few milliseconds.

You will fix exactly this bug in exercise 03, and *measure* that the cooperative version stops within milliseconds of `cancel()` while the non-cooperative one runs to completion.

---

## 2. `CancellationException` is special — never swallow it

Cancellation works by *throwing*. When a suspending call detects cancellation, it throws `CancellationException`, which unwinds your coroutine, runs your `finally` blocks, and reports the coroutine as cancelled. This is by design — it is how cleanup runs on cancel. But it has a sharp consequence:

```kotlin
// THE BUG: a blanket catch swallows the cancellation, defeating it entirely.
suspend fun loadWithRetry() {
    while (true) {
        try {
            return load()
        } catch (e: Exception) {      // <- catches CancellationException too!
            delay(1.seconds)          // retry forever, even after cancel
        }
    }
}
// You cancel the scope. load()'s cancellation throws CancellationException.
// The catch eats it, the loop retries, and the coroutine NEVER stops. Leak.
```

`CancellationException` is an `Exception`, so `catch (e: Exception)` catches it — and now your coroutine has *swallowed its own cancellation* and keeps running. This is the second cancellation bug, and it is insidious because the code looks like reasonable error handling.

The fixes:

```kotlin
// Fix A — rethrow CancellationException explicitly.
catch (e: CancellationException) {
    throw e                          // let cancellation propagate
} catch (e: Exception) {
    delay(1.seconds)                 // real errors retry
}

// Fix B — use the kotlinx helper that does exactly this.
catch (e: Exception) {
    currentCoroutineContext().ensureActive()  // rethrows if cancelled
    delay(1.seconds)
}
```

The rule to tattoo: **a `catch (Exception)` or `catch (Throwable)` inside a coroutine must rethrow `CancellationException`** (or check `ensureActive()`). Swallowing it turns cancellation into a no-op. Note that `runCatching { }` has the same trap — it catches `Throwable`, including cancellation — so be wary of it in suspending code.

### Cleanup that must run even when cancelled

`finally` runs on cancellation, but it runs in an *already-cancelled* coroutine, where any new suspension immediately throws `CancellationException`. So a `finally` that needs to suspend (close a network handle, flush a buffer) must opt out:

```kotlin
suspend fun streamToFile(url: String) {
    val handle = open(url)
    try {
        handle.copyTo(file)
    } finally {
        // We are cancelled here; a plain suspend would immediately re-throw.
        withContext(NonCancellable) {
            handle.closeAsync()      // suspend allowed: NonCancellable shields this block
        }
    }
}
```

`withContext(NonCancellable)` is the one place you *want* to ignore cancellation: a short cleanup that must complete. Do not abuse it for real work — only for "I must finish closing this resource."

---

## 3. `coroutineScope` vs `supervisorScope` — the blast radius of a failure

Both create a child scope that waits for its children. The difference is **what happens when one child fails**:

- **`coroutineScope`** (and a regular `Job`): a failing child **cancels its siblings and the parent.** All-or-nothing. If any part fails, the whole thing fails.
- **`supervisorScope`** (and a `SupervisorJob`): a failing child is **isolated** — it does not cancel its siblings, and the parent keeps going. Each child stands or falls alone.

The choice is a *semantic* one about your problem:

```kotlin
// ALL-OR-NOTHING: a Dashboard needs all three sections. If the feed fails, the
// whole load fails and we show an error — a half-built dashboard is useless.
suspend fun loadDashboard(): Dashboard = coroutineScope {
    val a = async { fetchProfile() }
    val b = async { fetchFeed() }       // if this throws, a and c are cancelled too
    val c = async { fetchSettings() }
    Dashboard(a.await(), b.await(), c.await())
}

// ISOLATED: a home screen with independent widgets. If the weather widget's
// fetch fails, the news and calendar widgets must still load.
suspend fun loadWidgets(widgets: List<Widget>) = supervisorScope {
    widgets.forEach { widget ->
        launch {
            try {
                widget.refresh()
            } catch (e: Exception) {
                ensureActive()           // rethrow cancellation
                widget.showError(e)      // one widget's failure stays local
            }
        }
    }
}
```

Get this backwards and you ship a bug either way: a `coroutineScope` where you wanted isolation means one flaky widget blanks the whole screen; a `supervisorScope` where you wanted all-or-nothing means you render a dashboard with a missing section as if it were fine.

A subtlety that surprises people: under `supervisorScope`, **you must handle each child's exception inside that child** (the `try`/`catch` above) or install a `CoroutineExceptionHandler`. Because the supervisor does *not* propagate the failure up, an unhandled throw in a supervised `launch` becomes an *uncaught* exception. Isolation cuts both ways: the parent won't crash, but it also won't catch it for you.

---

## 4. Exception handling — the rules that differ between `launch` and `async`

This is where the framework is least intuitive, so memorise the rules:

**`launch` propagates immediately.** An uncaught exception in a `launch` body propagates to the parent `Job` as soon as it is thrown. Under a regular `Job` it cancels the parent and siblings; under a `SupervisorJob` it is reported to the `CoroutineExceptionHandler` (or crashes if there is none).

**`async` defers to `await`.** An exception in an `async` body is *captured* inside the `Deferred` and re-thrown when you call `await()`. So you catch it with a `try`/`catch` around the `await`:

```kotlin
val deferred = scope.async { riskyFetch() }
try {
    val value = deferred.await()        // the exception surfaces HERE, not at async {}
} catch (e: IOException) {
    handle(e)
}
```

The trap: an `async` whose result you *never* `await` can swallow its exception entirely (under a `SupervisorJob` root) — the failure is sitting in a `Deferred` nobody ever asks. "Why didn't my error show up?" is often "you `async`-ed it and never awaited."

### Where `CoroutineExceptionHandler` fires — and where it doesn't

A `CoroutineExceptionHandler` is the *last-resort* handler for exceptions that are otherwise uncaught. The two rules people get wrong:

1. **It only fires for `launch`, never for `async`** (async exceptions go to `await`).
2. **It must be installed on the *scope* (the root), not on a child `launch`.** A handler in a child's context is ignored — the exception propagates *up* to the root before any handler runs, so only the root's handler matters.

```kotlin
val handler = CoroutineExceptionHandler { _, e ->
    log.error("uncaught in scope", e)    // last resort: log, report to Crashlytics later
}

// CORRECT: handler on the scope (the root).
val scope = CoroutineScope(SupervisorJob() + handler)
scope.launch { mayThrow() }              // uncaught throw -> handler fires

// WRONG: handler on a child launch under a regular Job — it is ignored, because
// the exception propagates to the parent before the child's handler can run.
scope.launch(handler) { mayThrow() }     // handler here does nothing useful
```

For Android, the practical pattern (Phase 2) is a single `CoroutineExceptionHandler` on the `viewModelScope`-equivalent that reports to Crashlytics, plus local `try`/`catch` for errors you can recover from. This week, you install it on the scopes you create and verify it fires for `launch` and not for unawaited `async`.

---

## 5. Bounded parallelism — "100 URLs" must not mean "100 sockets"

The mini-project fetches 100 URLs concurrently. The naive version is a one-liner:

```kotlin
// THE BITE: launches 100 concurrent connections. On a mobile network this is a
// thundering herd — connection-pool exhaustion, timeouts, the OS throttling you.
suspend fun downloadAllNaive(urls: List<String>): List<ByteArray> = coroutineScope {
    urls.map { url -> async(Dispatchers.IO) { download(url) } }.awaitAll()
}
```

`awaitAll` over 100 `async`s starts all 100 at once. On a desktop with a fat pipe you might get away with it; on a phone it is a self-inflicted denial of service. You want concurrency *bounded* — say, 8 in flight at a time. Two idiomatic tools:

```kotlin
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// Option A — a Semaphore: a permit per concurrent slot. The classic, explicit way.
suspend fun downloadBounded(urls: List<String>, maxConcurrent: Int = 8): List<ByteArray> =
    coroutineScope {
        val gate = Semaphore(permits = maxConcurrent)
        urls.map { url ->
            async(Dispatchers.IO) {
                gate.withPermit {          // acquire a slot; suspends if all 8 are taken
                    download(url)
                }
            }
        }.awaitAll()
    }

// Option B — limitedParallelism: a view of a dispatcher capped to N concurrent coroutines.
private val boundedIo = Dispatchers.IO.limitedParallelism(8)
suspend fun downloadBounded2(urls: List<String>): List<ByteArray> = coroutineScope {
    urls.map { url -> async(boundedIo) { download(url) } }.awaitAll()
}
```

`Semaphore.withPermit` is the explicit, readable choice and the one we use in the mini-project: at most `maxConcurrent` coroutines hold a permit and are actually downloading; the rest *suspend* (not block — release their thread) at `withPermit` until a slot frees. `limitedParallelism` achieves the same by capping the dispatcher. Either way the rule is: **concurrency is a resource; bound it.** Unbounded `async` over a list is a footgun the day the list is long.

A measured aside: the bounded version is often *faster* than the unbounded one on a real network, not slower, because 8 healthy connections beat 100 connections all timing out and retrying. "More concurrency" is not "more throughput" past the point your network and the server can absorb.

### Timeouts are cancellation in disguise

A related tool you will reach for constantly is `withTimeout`:

```kotlin
val result = withTimeout(5.seconds) {
    slowDownload(url)          // if this takes > 5s, the block is CANCELLED
}
```

`withTimeout` runs its block and, if it does not complete in time, **cancels it** — by throwing `TimeoutCancellationException`, which is a *subclass of `CancellationException`*. That subtype relationship is the gotcha: a blanket `catch (CancellationException)` will catch a timeout too, and a `try`/`catch` meant to turn a timeout into a `Failure` must catch it *specifically* before any cancellation rethrow, or you will either swallow real cancellations or mistake a real cancellation for a timeout. Use `withTimeoutOrNull` when you want `null` instead of a throw on timeout — it is the cleaner choice when "took too long" is an expected, non-exceptional outcome:

```kotlin
val result = withTimeoutOrNull(5.seconds) { slowDownload(url) }
    ?: return DownloadResult.Failure(url, TimeoutException())  // timeout -> data, not throw
```

The mental model: a timeout *is* a cancellation with a deadline. Everything you know about cooperative cancellation applies — the block must reach a suspension point for the timeout to fire, so a non-cooperative CPU loop ignores `withTimeout` exactly as it ignores `cancel()`.

---

## 6. The three cancellation bugs, summarised

The challenge plants exactly these. Know them cold:

1. **The non-cooperative loop.** A CPU loop with no suspension point ignores `cancel()` and runs to completion. *Fix:* `ensureActive()` / `isActive` / `yield()` periodically.
2. **The swallowed `CancellationException`.** A `catch (Exception)` (or `runCatching`) eats the cancellation and the coroutine retries forever. *Fix:* rethrow `CancellationException` (or `ensureActive()`) before handling real errors.
3. **The `GlobalScope` leak.** A coroutine launched in `GlobalScope` (or any scope nobody cancels) outlives the thing that started it and writes to a dead receiver. *Fix:* launch into an owned scope (`viewModelScope`, or a `CoroutineScope` you cancel).

Every one of these *works in the demo* and *fails under load or on a fast back-out.* That is what makes them senior-level bugs: the happy path is green, and the failure only appears when a user cancels at the wrong moment or accumulates enough work to feel the leak.

---

## 7. A production checklist

Before you call coroutine code "done," walk this list — it is the code-review checklist a senior reviewer applies:

- **Every coroutine is owned by a cancellable scope.** No `GlobalScope.launch`. Grep for it; it is almost always a bug.
- **Long CPU loops cooperate with cancellation** via `ensureActive()`/`isActive`/`yield()`, checked often enough to react within milliseconds.
- **No blanket `catch` swallows `CancellationException`.** Every `catch (Exception)`/`catch (Throwable)`/`runCatching` in suspending code rethrows cancellation or calls `ensureActive()`.
- **The supervision choice is deliberate.** Each `coroutineScope`/`supervisorScope` and `Job`/`SupervisorJob` was chosen because you decided whether one failure should take down its siblings, and you can say why in one sentence.
- **`async` results are awaited.** No `async` whose exception can vanish because nobody `await`s it.
- **The `CoroutineExceptionHandler` is on the root scope**, not a child `launch`, and you know it only catches `launch` exceptions.
- **Dispatchers match the work:** `IO` for blocking I/O, `Default` for CPU, `Main` only for UI, never `Unconfined` in production.
- **Concurrency is bounded.** Fan-out over a list uses a `Semaphore` or `limitedParallelism`, not an unbounded `async` per element.
- **Cleanup that must run on cancel uses `withContext(NonCancellable)`** — and only for short, must-finish cleanup.

---

## 8. Recap

Lecture 1 sold you on the coroutine as a suspendable computation in a `Job` tree. This lecture was the half that decides whether your code survives a user who backs out at the wrong moment and a network that drops a request. Three habits carry it:

1. **Cancellation is cooperative.** It throws at suspension points; a non-suspending loop must check `ensureActive()`/`isActive`/`yield()`, and no `catch` may swallow `CancellationException`.
2. **Choose your supervision.** `coroutineScope` for all-or-nothing, `supervisorScope` for isolated children; install the `CoroutineExceptionHandler` on the root; know `async` defers its exception to `await`.
3. **Bound your concurrency.** "100 URLs" is 8 at a time behind a `Semaphore`, not 100 sockets at once.

You now have both halves of coroutines: the transform and tree you write every day, and the cancellation/supervision/exception rules you debug when it leaks. The exercises put numbers on the cancellation bugs and the structured-vs-leaky difference; the mini-project builds a bounded, cancellable, structured downloader that never leaks a coroutine. Go make concurrency a discipline, not a hope.
