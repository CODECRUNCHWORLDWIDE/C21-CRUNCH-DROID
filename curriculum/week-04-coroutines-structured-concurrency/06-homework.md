# Week 04 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 04 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code is plain JVM Kotlin 2.x targeting `kotlinx-coroutines` 1.9+ with Swift-6-equivalent rigour: **Kotlin strict** settings, every test on `runTest`, and **0 warnings**. A coroutine that outlives its scope is a bug, not a style choice.

---

## Problem 1 — Disassemble a three-step suspend function

**Problem statement.** Extend exercise 01: write a `suspend fun threeStep()` with *three* suspend calls and two locals that each survive a different suspension. Disassemble with `javap -c -p` and write `notes/three-step.md` identifying how many spilled-local fields (`L$0`, `L$1`, ...) the state machine has and why that number matches the locals-surviving-a-suspension count.

**Acceptance criteria.**

- `threeStep()` with three suspend points and two locals used after different suspensions.
- `notes/three-step.md` quotes the real `L$0`/`L$1` field declarations and the `tableswitch`, and explains the count.
- Committed.

**Hint.** Each local that is *live across* a suspension needs its own field. A local used only between two suspensions it doesn't cross stays on the stack. Arrange the reads so exactly two locals cross suspensions and confirm `L$0` and `L$1` both appear.

**Estimated time.** 35 minutes.

---

## Problem 2 — `launch` vs `async` exception timing

**Problem statement.** Write two `runTest` cases proving the exception-timing rule: in case A, a throw inside `launch` propagates to the parent (the surrounding `coroutineScope` fails); in case B, a throw inside `async` surfaces only at `await()`, and an `async` you never await (under a `supervisorScope`) does not fail the parent. State, in a comment, the rule each case proves.

**Acceptance criteria.**

- Case A: a `launch` that throws causes the enclosing `coroutineScope` to throw.
- Case B: an `async` that throws surfaces the exception at `await()`, caught with a `try`/`catch`; a never-awaited `async` under `supervisorScope` leaves the parent alive.
- Each case has a one-line comment stating the rule. 0 warnings. Committed.

**Hint.** Use `assertFailsWith<...>` around the `coroutineScope { launch { error("x") } }` for case A. For case B, `val d = async { error("y") }` then assert nothing thrown until `d.await()`.

**Estimated time.** 50 minutes.

---

## Problem 3 — `coroutineScope` vs `supervisorScope`, measured

**Problem statement.** Build a `loadSections(sections: List<Section>)` two ways: an all-or-nothing version (`coroutineScope`) where one failing section fails the whole load, and an isolated version (`supervisorScope`) where a failing section is reported as a `SectionResult.Failure` and the others still succeed. Write a test that injects one failing section and asserts the two versions behave differently.

**Acceptance criteria.**

- `loadSectionsAllOrNothing` throws when any section fails (the whole call fails).
- `loadSectionsIsolated` returns successes for the good sections and a `Failure` for the bad one.
- A test injects one failing section and asserts both behaviours.
- 0 warnings. Committed.

**Hint.** The isolated version wraps each section's work in a `try`/`catch` inside a `supervisorScope` `launch`/`async`, rethrowing `CancellationException` and turning real errors into `Failure` data — exactly the mini-project's `runFetch` pattern.

**Estimated time.** 50 minutes.

---

## Problem 4 — Make a blocking SDK call cancellable

**Problem statement.** You're given a fake "legacy SDK" with a blocking method `fun blockingFetch(url: String): String` that sleeps and returns. Wrap it in a `suspend fun fetch(url: String)` that runs on `Dispatchers.IO` and is cancellable. Write a test (using a real `Dispatchers.IO`, not virtual time) that starts the fetch, cancels it, and asserts the coroutine cancels promptly even though the underlying call is blocking.

**Acceptance criteria.**

- `suspend fun fetch` uses `withContext(Dispatchers.IO)` so the blocking call parks an IO thread, not the caller's.
- A test cancels mid-fetch and asserts the coroutine reaches a cancelled state.
- 0 warnings. Committed.

**Hint.** A purely blocking call (`Thread.sleep`) inside `withContext(Dispatchers.IO)` is not itself interruptible, but the *coroutine* cancels at the `withContext` boundary. To make the blocking call itself abandonable, run it and let `withContext`'s cancellation abandon the result. For true interruption you'd use `runInterruptible { }` — note this in a comment.

**Estimated time.** 45 minutes.

---

## Problem 5 — Bounded fan-out with a `Semaphore`

**Problem statement.** Write `processAll(items: List<Int>, maxConcurrent: Int)` that processes each item concurrently but never runs more than `maxConcurrent` at once, using a `Semaphore`. Add an `AtomicInteger` "currently in flight" gauge that increments on entry and decrements on exit, and a test asserting the gauge's observed maximum never exceeds `maxConcurrent`.

**Acceptance criteria.**

- `processAll` bounds concurrency with `Semaphore(maxConcurrent)` + `withPermit`.
- A test records the peak in-flight count and asserts it is `<= maxConcurrent`.
- The result set is independent of `maxConcurrent`. 0 warnings. Committed.

**Hint.** Track peak with `inFlight.updateAndGet { it + 1 }` then `peak.updateAndGet { max(it, current) }`, decrement in a `finally`. Run with `maxConcurrent = 3` over 50 items and assert peak `<= 3`.

**Estimated time.** 45 minutes.

---

## Problem 6 — A retry with exponential backoff that respects cancellation

**Problem statement.** Write `suspend fun <T> retry(times: Int, initialDelay: Duration, block: suspend () -> T): T` that retries `block` up to `times` with exponential backoff (`initialDelay * 2^attempt`), rethrows `CancellationException` so a cancel during backoff stops it, and gives up by rethrowing the last real exception. Test: (a) it succeeds after N failures, (b) cancelling during a backoff `delay` actually stops it.

**Acceptance criteria.**

- `retry` backs off exponentially and rethrows `CancellationException` (never swallows it).
- Test (a): a block that fails twice then succeeds returns on the third try.
- Test (b): cancelling during a backoff stops the retrier (assert with `runTest` virtual time + `advanceTimeBy`).
- 0 warnings. Committed.

**Hint.** `var delayMs = initialDelay; repeat(times - 1) { try { return block() } catch (e: CancellationException) { throw e } catch (e: Exception) { delay(delayMs); delayMs *= 2 } }; return block()`. The cancellation rethrow is the whole point — it's the lecture 02 / challenge bug 2 in a reusable helper.

**Estimated time.** 50 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, code is idiomatic Kotlin/coroutines, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. an unnecessary `runBlocking` in a test where `runTest` fits, a missing dispatcher annotation, `Thread.sleep` where `delay` belonged). |
| 3 | Works, but misses one criterion (e.g. concurrency not actually bounded, a `catch` that doesn't rethrow cancellation, a test that uses real time and flakes). |
| 2 | Compiles and partially works; a core idea is wrong (filters cancellation into a swallow; `GlobalScope` used where an owned scope was required; CPU loop never cooperates). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for any `GlobalScope.launch`/`async` used to "fix" a structure problem instead of an owned scope; **−2** for a `catch (Exception)`/`catch (Throwable)`/`runCatching` in suspending code that swallows `CancellationException`; **−1** for a CPU loop that never checks cancellation where it should; **−1** for the wrong dispatcher (blocking I/O on `Default`, CPU on `IO`).

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — structured-vs-leaky ownership (problems 3, 5) and cooperative cancellation / `CancellationException` handling (problems 4, 6) — so re-run exercises 02 and 03 before resubmitting.
