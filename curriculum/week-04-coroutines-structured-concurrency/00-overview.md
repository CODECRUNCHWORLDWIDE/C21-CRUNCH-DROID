# Week 04 — Coroutines: structured concurrency from first principles

Welcome to Week 04 of **C21 · Crunch Droid**. For three weeks your Kotlin has run top to bottom: a function starts, it does its work, it returns. The moment you touch the network, the disk, or a sensor on a real device, that model breaks. The work takes time, and on Android the one thread you are not allowed to block is the one drawing the screen. This week you learn the tool Kotlin gives you for that problem — coroutines — and, more importantly, the *discipline* that keeps them from becoming a leak factory: **structured concurrency**.

The headline you must internalise before anything else: **coroutines are not lightweight threads.** That sentence is on half the marketing slides and it is misleading. A coroutine is not a thread at all. It is a *suspendable computation* — a block of code the Kotlin compiler has rewritten so that it can pause at well-defined points, release the thread it was running on, and resume later, possibly on a different thread. The pause points are the `suspend` calls. The compiler turns each `suspend` function into a state machine driven by a `Continuation`, and that continuation-passing transform is the whole trick. When you understand that a `suspend fun` is a function with an extra hidden parameter — the continuation — and a `when` over an integer label, coroutines stop being magic and become something you can disassemble and reason about. We will do exactly that with `javap` on Tuesday.

The second idea is the one that separates engineers who *use* coroutines from engineers who can *debug* them: **every coroutine has a parent, and a parent does not complete until its children complete.** That is structured concurrency. A `CoroutineScope` is the parent. `launch` and `async` create children inside it. If you cancel the scope, every child is cancelled. If a child fails, the failure propagates to the parent. This is not a convenience — it is the property that makes concurrent Android code *correct*, because it means a `ViewModel` that is cleared takes all of its in-flight work down with it, and a screen that leaves the composition does not leave a coroutine writing to a dead view. The bug class that structured concurrency eliminates — the orphaned background task that outlives the thing that started it — is the single most common source of leaks and crashes in pre-coroutine Android code.

We close the week by building a **parallel downloader** that fetches 100 URLs concurrently with *bounded* parallelism (you do not get to open 100 sockets at once on a phone), supports cancellation the instant the user backs out, and prints structured progress. It exercises every idea in the week: a `coroutineScope` that owns the work, a `Semaphore` that bounds it, cooperative cancellation that actually stops the downloads, and exception handling that distinguishes "this one URL failed" from "the whole job is dead." If you can build that and explain why it never leaks a coroutine, you have the skill this week earns.

## Learning objectives

By the end of this week, you will be able to:

- **Explain** what a `suspend` function compiles to — a continuation-passing state machine — and read the `javap` bytecode of a trivial suspend function well enough to point at the `Continuation` parameter and the label-driven `when`.
- **Distinguish** a coroutine from a thread, and explain why "lightweight thread" is the wrong mental model and what the right one (a suspendable computation scheduled onto a dispatcher's thread pool) buys you.
- **Build** structured concurrency by hand: create a `CoroutineScope`, launch children with `launch`/`async`, and explain the parent-child `Job` hierarchy that makes cancellation and failure propagate.
- **Choose** the correct `Dispatcher` for a piece of work — `Main` for UI, `IO` for blocking I/O, `Default` for CPU-bound work, and never `Unconfined` in production — and explain what goes wrong with each wrong choice.
- **Contrast** `coroutineScope` and `supervisorScope`, and `Job` versus `SupervisorJob`, and pick the right one based on whether one child's failure should cancel its siblings.
- **Write** cancellation-cooperative code: check `isActive`/`ensureActive()`, understand why `CancellationException` must be rethrown, and never swallow cancellation in a blanket `catch`.
- **Handle** coroutine exceptions correctly — the difference between `launch` (propagates to the parent) and `async` (holds the exception until `await`), and where a `CoroutineExceptionHandler` does and does not fire.
- **Recognise** the three canonical cancellation bugs — the non-cooperative tight loop, the swallowed `CancellationException`, and the leak from `GlobalScope` — and the fix for each.

## Prerequisites

This week assumes you have completed **C21 weeks 1–3**, or have equivalent fluency. Specifically:

- You can read and write idiomatic Kotlin 2.x — `val`/`var`, expressions over statements, type inference, lambdas, and higher-order functions (Weeks 1, 3). A coroutine builder like `launch { ... }` is just a higher-order function taking a suspending lambda; the Week 3 work on function types and SAM conversion is load-bearing here.
- You understand sealed classes and `Result<T>` (Week 2). We model a download's outcome as a sealed `DownloadResult` and use exhaustive `when`, exactly the modelling discipline Week 2 drilled.
- You have read bytecode with `javap` at least once (Week 1). This week we point it at a `suspend` function and read the continuation-passing transform out of the disassembly. If `javap -c -p` is unfamiliar, re-skim the Week 1 lecture before Tuesday.
- You are comfortable with the Gradle Kotlin DSL toolchain from Week 1 — adding a dependency to `libs.versions.toml` and a `build.gradle.kts`. This week adds `kotlinx-coroutines-core` and, for tests, `kotlinx-coroutines-test`.

**Toolchain.** Kotlin 2.x (K2 compiler), `kotlinx-coroutines` 1.9+, JDK 21. Everything this week runs as **plain JVM Kotlin** — a Gradle application or a test target. We deliberately stay off Android for one more week: coroutines are a Kotlin language-plus-library feature, not an Android one, and learning them without the lifecycle noise makes the structured-concurrency discipline clearer. The Android dispatchers (`Dispatchers.Main`, `viewModelScope`, `lifecycleScope`) arrive in Phase 2 once you have the fundamentals. You need no emulator and no device this week.

## Topics covered

- **Suspending functions and continuations.** What `suspend` means, the continuation-passing-style (CPS) transform the compiler performs, the hidden `Continuation<T>` parameter, the `COROUTINE_SUSPENDED` sentinel, and the label-driven state machine. Reading it in `javap`.
- **Coroutines vs threads.** A coroutine as a suspendable computation, not a thread; how dispatchers multiplex many coroutines onto a small thread pool; why you can have a million coroutines and not a million threads; the cost model.
- **`CoroutineScope` and `CoroutineContext`.** The scope as the owner of structured concurrency; the context as a map of elements (`Job`, `CoroutineDispatcher`, `CoroutineName`, `CoroutineExceptionHandler`); context inheritance and the `+` operator.
- **`Job` and the parent-child tree.** Every coroutine is a `Job`; parents wait for children; cancelling a parent cancels children; a failing child (under a regular `Job`) cancels the parent and siblings. `Job` lifecycle states.
- **`launch` vs `async`.** Fire-and-forget vs a `Deferred<T>` result; how exceptions differ between the two; when each is right; `awaitAll`.
- **Dispatchers.** `Main`, `Main.immediate`, `IO`, `Default`, `Unconfined`; the thread pools behind them; `withContext` for switching; why blocking on `Default` starves CPU work and why `IO` exists.
- **`coroutineScope` vs `supervisorScope`.** All-or-nothing failure vs isolated child failure; `Job` vs `SupervisorJob`; where each belongs (a screen-level supervisor, an all-or-nothing parallel decomposition).
- **Cancellation.** Cooperative cancellation, `isActive`, `ensureActive()`, `yield()`; why `CancellationException` is special and must propagate; `withContext(NonCancellable)` for cleanup; cancellation of blocking calls.
- **Exception handling.** Propagation rules, `CoroutineExceptionHandler`, `try`/`catch` around `await`, why a `CoroutineExceptionHandler` on a child does nothing, and the `SupervisorJob` interaction.
- **Bounded parallelism.** `Semaphore`, `limitedParallelism`, and why "fetch 100 URLs" must not mean "open 100 connections" on a mobile network.
- **The three cancellation bugs.** A CPU loop that never checks `isActive`; a `catch (e: Exception)` that eats `CancellationException`; a `GlobalScope.launch` that leaks past the screen. The fix for each.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                              | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|-------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | Suspend & continuations; `javap` the state machine; coroutines vs threads |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | Scope, `Job`, parent-child tree; `launch`/`async`; dispatchers     |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | `coroutineScope` vs `supervisorScope`; cancellation cooperativity  |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | Exception handling; bounded parallelism; the three bugs; challenge |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — parallel downloader scaffold; cancellation wiring   |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work; bounded parallelism + progress             |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                        |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                   | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./00-overview.md) | This overview (you are here) |
| [resources.md](./01-resources.md) | The KotlinX coroutines guide, Roman Elizarov's structured-concurrency writing, the KEEP, and the dispatcher/cancellation primary sources |
| [lecture-notes/01-suspend-continuations-and-the-state-machine.md](./02-lecture-notes/01-suspend-continuations-and-the-state-machine.md) | What `suspend` compiles to: the CPS transform, the `Continuation`, the label state machine, coroutines vs threads, and scope/`Job`/dispatchers from first principles |
| [lecture-notes/02-cancellation-supervision-and-exceptions.md](./02-lecture-notes/02-cancellation-supervision-and-exceptions.md) | `coroutineScope` vs `supervisorScope`, cooperative cancellation, the three cancellation bugs measured, exception propagation, and bounded parallelism |
| [exercises/README.md](./03-exercises/00-overview.md) | Index of the three exercises |
| [exercises/exercise-01-disassemble-a-suspend-function.md](./03-exercises/exercise-01-disassemble-a-suspend-function.md) | Write a two-suspend-call function, disassemble it with `javap`, and find the continuation, the label, and the suspend points |
| [exercises/exercise-02-structured-vs-leaky.kt](./03-exercises/exercise-02-structured-vs-leaky.kt) | Build the same fan-out two ways — leaky `GlobalScope` vs structured `coroutineScope` — and prove with tests that one leaks and one doesn't |
| [exercises/exercise-03-cooperative-cancellation.kt](./03-exercises/exercise-03-cooperative-cancellation.kt) | Make a non-cooperative CPU loop cancellable, and stop a `catch` from swallowing `CancellationException` |
| [challenges/README.md](./04-challenges/00-overview.md) | Index of the challenge |
| [challenges/challenge-01-cancellation-bug-hunt.md](./04-challenges/challenge-01-cancellation-bug-hunt.md) | Three planted cancellation bugs in a small fetcher; find, explain, fix, and prove each fix with a test |
| [quiz.md](./05-quiz.md) | 13 questions on suspension, scopes, dispatchers, cancellation, supervision, and exceptions |
| [homework.md](./06-homework.md) | Six practice problems for the week |
| [mini-project/README.md](./07-mini-project/00-overview.md) | Full spec for the bounded-parallelism parallel downloader: 100 URLs, cancellation, structured progress |

## The "never leak a coroutine" promise

Week 1 gave you "reads like Kotlin, not translated Java." Weeks 2 and 3 gave you "model it in the type system." Week 4 adds the concurrency contract a senior reviewer actually checks:

> **Every coroutine you start is owned by a scope you can cancel.** No `GlobalScope.launch`. No coroutine that outlives the thing that started it. When the owner (the test, the scope, eventually the `ViewModel`) is cancelled, every coroutine under it stops cooperatively — promptly, without swallowing the cancellation. If a background job keeps running after its owner is gone, the concurrency is broken, no matter how clean the happy path looks.

You will *prove* this in the exercises by asserting that a structured fan-out leaves no live job after its scope is cancelled, while the leaky `GlobalScope` version keeps running — a concrete, testable difference, not a vibe.

## A note on what's not here

Week 4 is the *structured-concurrency fundamentals* week. It deliberately does **not** cover:

- **Flow.** Cold streams, `StateFlow`, `SharedFlow`, and channels are all of Week 5. This week is one-shot suspending work — fetch this, compute that, return — not streams of values over time. We mention `Flow` only to say "next week."
- **Android coroutine scopes.** `viewModelScope`, `lifecycleScope`, `Dispatchers.Main` on a real Looper, and `repeatOnLifecycle` arrive in Phase 2 with Compose and the lifecycle. This week's scopes are plain `CoroutineScope`s you create and cancel yourself, which is the right place to learn the discipline.
- **Coroutine internals beyond the transform.** We read the state machine in `javap` to demystify it; we do not study the `kotlinx-coroutines` scheduler's work-stealing internals. Knowing the transform is the conceptual win; the scheduler is an implementation detail you can profile later.

The point of Week 4 is narrow and deep: one mental model (suspend = continuation-passing state machine), one discipline (structured concurrency), and the cancellation and exception rules that make concurrent Android code correct instead of merely working in the demo.

## Up next

Continue to **Week 05 — Flow, StateFlow, SharedFlow, channels** once you have shipped this week's mini-project and proven your downloader never leaks a coroutine. Week 5 takes the one-shot suspending work you mastered here and extends it to *streams* — values arriving over time — with cold `Flow`, hot `StateFlow`/`SharedFlow`, and channels for bridging callback APIs. Everything in Week 5 is built on a coroutine; a `Flow` is collected inside a coroutine scope, cancelled when that scope is cancelled, and dispatched on the dispatchers you learned this week. Earn the structured-concurrency reflex now and Flow will feel like a natural extension instead of a second system to learn.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
