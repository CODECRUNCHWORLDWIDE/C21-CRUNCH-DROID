# Week 04 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 05. Answer key with explanations at the bottom — don't peek.

---

**Q1.** Which statement best describes what a coroutine *is*?

- A) A lightweight thread the OS schedules with its own stack.
- B) A suspendable computation — a block of code rewritten by the compiler so it can pause at suspend points, release its thread, and resume later, multiplexed onto a small thread pool.
- C) A green thread that always runs on the main thread.
- D) A future that blocks the calling thread until it completes.

---

**Q2.** After the compiler is done with it, what does `suspend fun load(id: String): User` actually look like at the JVM level?

- A) `fun load(id: String): User` — unchanged.
- B) `fun load(id: String, completion: Continuation<User>): Any?` — an extra `Continuation` parameter and an `Any?` return that can carry the `COROUTINE_SUSPENDED` sentinel.
- C) `fun load(id: String): Future<User>`.
- D) `fun load(id: String): User` plus a hidden `Thread` field.

---

**Q3.** A local variable is read *after* a suspension point. Where does the compiler store it across the suspension, and why?

- A) On the call stack, like any local — coroutines don't change that.
- B) In a field of the generated state-machine object on the heap, because the coroutine may resume on a different thread, so the value cannot live on any one thread's stack.
- C) In a `ThreadLocal`.
- D) It is recomputed on resume; suspended coroutines keep no state.

---

**Q4.** What is the difference between a `CoroutineContext` and a `CoroutineScope`?

- A) They are the same thing with two names.
- B) A context is an indexed set of elements (`Job`, dispatcher, name, handler); a scope holds a context (containing a `Job`) and is what you launch coroutines *into*, establishing parentage.
- C) A scope is immutable; a context is mutable.
- D) A context can be cancelled; a scope cannot.

---

**Q5.** Under a regular `Job` (not `SupervisorJob`), a child coroutine throws an uncaught exception. What happens to its siblings?

- A) Nothing; siblings are independent.
- B) The failure propagates to the parent `Job`, which cancels itself and therefore all the other children too.
- C) Only the failing child is cancelled; the parent logs and continues.
- D) The whole process crashes immediately.

---

**Q6.** You need to run a heavy JSON parse (pure CPU) and then a blocking file read. Which dispatchers are correct?

- A) Both on `Dispatchers.IO`.
- B) Both on `Dispatchers.Main`.
- C) The parse on `Dispatchers.Default`, the blocking file read on `Dispatchers.IO`.
- D) Both on `Dispatchers.Unconfined`.

---

**Q7.** You call `job.cancel()` on a coroutine running a tight `for` loop of pure CPU work with no suspension point and no `isActive` check. What happens?

- A) The loop stops immediately; cancellation is pre-emptive.
- B) The loop runs to completion — cancellation is cooperative, and a loop that never reaches a suspension point or an `ensureActive()`/`isActive` check never notices it was cancelled.
- C) An exception is thrown into the middle of the loop.
- D) The JVM kills the thread.

---

**Q8.** Inside a coroutine you write `try { risky() } catch (e: Exception) { retry() }`. Why is this a bug?

- A) `retry()` can't be called from a catch block.
- B) `CancellationException` is an `Exception`, so this `catch` swallows the cancellation and the coroutine never stops when its scope is cancelled — you must rethrow `CancellationException` (or call `ensureActive()`) first.
- C) `catch` blocks aren't allowed in suspend functions.
- D) It isn't a bug; this is correct error handling.

---

**Q9.** When does an exception thrown inside an `async { }` block surface?

- A) Immediately, at the `async { }` call site.
- B) When you call `await()` on the resulting `Deferred` — the exception is captured in the `Deferred` and re-thrown there.
- C) Never; `async` swallows all exceptions.
- D) Only if a `CoroutineExceptionHandler` is installed on the child.

---

**Q10.** A home screen has three independent widgets; if one widget's refresh fails, the other two must still load. Which construct fits?

- A) `coroutineScope` with a regular `Job`.
- B) `supervisorScope` (a `SupervisorJob`), so one child's failure is isolated and does not cancel its siblings.
- C) `GlobalScope.launch` per widget.
- D) `runBlocking` per widget.

---

**Q11.** Where must a `CoroutineExceptionHandler` be installed to catch an uncaught exception from `scope.launch { }`?

- A) On the child `launch` itself: `scope.launch(handler) { }`.
- B) On the scope (the root): `CoroutineScope(SupervisorJob() + handler)` — a handler on a child is ignored because the exception propagates to the root before any handler runs.
- C) On the `Deferred`.
- D) Anywhere; placement doesn't matter.

---

**Q12.** You must fetch 100 URLs but never open more than 8 connections at once. What's the idiomatic tool?

- A) `urls.map { async { fetch(it) } }.awaitAll()` — `awaitAll` automatically bounds to 8.
- B) A `Semaphore(permits = 8)` with `withPermit { fetch(it) }` inside each `async` (or `Dispatchers.IO.limitedParallelism(8)`), so at most 8 coroutines actually fetch and the rest suspend at the gate.
- C) `runBlocking` with a thread pool of 8.
- D) `GlobalScope.async` with a `Thread.sleep`.

---

**Q13.** A `finally` block needs to `suspend` (close a network handle) during cleanup, but the coroutine is already cancelled. How do you let the cleanup's suspending call run?

- A) You can't; suspending in a cancelled coroutine always throws.
- B) Wrap it in `withContext(NonCancellable) { ... }`, which shields that short cleanup block from the cancellation so its suspension can complete.
- C) Catch and ignore the `CancellationException`.
- D) Move the cleanup outside the coroutine entirely.

---

## Answer key

**Q1 — B.** A coroutine is a suspendable computation, not a thread. It is multiplexed onto a dispatcher's small thread pool; a suspended coroutine costs heap state and no thread. "Lightweight thread" is the wrong model. (Lecture 1, §1–2.)

**Q2 — B.** The CPS transform adds a `Continuation<User>` parameter and changes the return to `Any?` so it can return either the result or `COROUTINE_SUSPENDED`. You verified this in `javap` in exercise 01. (Lecture 1, §3; exercise 01.)

**Q3 — B.** The local is spilled to a field of the state-machine object on the heap, because resume may land on a different thread, so the value cannot live on a single thread's stack. (Lecture 1, §3; exercise 01's `L$0`.)

**Q4 — B.** A context is the typed bag of configuration elements; a scope holds a context containing a `Job` and is what you launch into, establishing the parent-child relationship. The distinction trips everyone up once. (Lecture 1, §4.)

**Q5 — B.** Under a regular `Job`, a child's failure propagates up and cancels the parent and all siblings — all-or-nothing. Use `SupervisorJob`/`supervisorScope` if you want isolation. (Lecture 1, §5; lecture 2, §3.)

**Q6 — C.** CPU-bound work goes on `Default` (a pool sized to cores); blocking I/O goes on `IO` (a large elastic pool, because blocking parks a thread). Mixing them up starves CPU work or wastes IO threads. (Lecture 1, §6.)

**Q7 — B.** Cancellation is cooperative. A loop with no suspension point and no `ensureActive()`/`isActive` check never notices cancellation and runs to completion. The fix is a periodic cooperative check. (Lecture 2, §1; exercise 03.)

**Q8 — B.** `CancellationException` is an `Exception`, so a blanket `catch (Exception)` swallows it and the coroutine never stops. Rethrow `CancellationException` (or call `ensureActive()`) before handling real errors. (Lecture 2, §2; exercise 03; challenge bug 2.)

**Q9 — B.** `async` captures the exception in the `Deferred` and re-throws it at `await()`. An `async` you never await can hide a failure entirely. (Lecture 2, §4.)

**Q10 — B.** Independent children that must survive each other's failures call for `supervisorScope`/`SupervisorJob` — failure isolation. `coroutineScope` would cancel the siblings on the first failure. (Lecture 2, §3.)

**Q11 — B.** The handler must be on the root scope; a handler on a child `launch` is ignored because the exception propagates to the root before any handler runs. It also only catches `launch` exceptions, not `async` ones. (Lecture 2, §4.)

**Q12 — B.** Bound concurrency with a `Semaphore(8)` + `withPermit` (or `limitedParallelism(8)`). `awaitAll` does *not* bound — it starts all 100 at once. (Lecture 2, §5; mini-project Milestone 2.)

**Q13 — B.** `withContext(NonCancellable)` shields a short must-finish cleanup block so its suspending call can complete even though the coroutine is cancelled. Use it only for cleanup, never for real work. (Lecture 2, §2.)

---

*Score 11+? On to Week 05. Below 9? Re-read both lecture notes and re-run exercises 02 and 03 — the structured-vs-leaky distinction and the cooperative-cancellation contract are the two ideas this week is graded on.*
