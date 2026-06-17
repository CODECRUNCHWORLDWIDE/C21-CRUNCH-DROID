# Week 05 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 05 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code is plain JVM Kotlin 2.x targeting `kotlinx-coroutines` 1.9+ with Turbine for tests. Every test is a Turbine assertion under `runTest` — **no `Thread.sleep`** — and every problem must build with **0 warnings**.

---

## Problem 1 — Prove cold per-collector re-execution and fix it with `shareIn`

**Problem statement.** Build a cold flow whose producer increments a counter, collect it twice, and assert the producer ran twice (the footgun). Then wrap it with `shareIn(scope, WhileSubscribed(5000), replay = 1)` and assert that two collectors of the *shared* flow run the producer only once. Write one sentence on when each is the right default.

**Acceptance criteria.**

- A test proving the cold flow's producer runs twice for two collections.
- A test proving the `shareIn`-shared flow's producer runs once for two collections.
- A one-sentence note on cold-default vs shared-deliberate.
- 0 warnings. Committed.

**Hint.** Use `backgroundScope` from `runTest` as the sharing scope. For the shared case, collect with two Turbine `test { }` blocks (or launch two collectors) and assert the shared counter is 1.

**Estimated time.** 40 minutes.

---

## Problem 2 — The flat-map decision table, demonstrated

**Problem statement.** For the same upstream flow of three IDs, implement `flatMapConcat`, `flatMapMerge`, and `flatMapLatest` over an inner flow whose latency depends on the ID, and write a test for each asserting the *observable* difference: concat preserves order and waits; merge interleaves; latest cancels all but the last. State, in a comment per test, which real-world case each fits.

**Acceptance criteria.**

- Three tests demonstrating ordered-sequential (concat), interleaved-concurrent (merge), and cancel-previous (latest).
- Each has a one-line real-world justification.
- 0 warnings. Committed.

**Hint.** Make ID 1 slow and ID 3 fast. Under `concat`, results come out 1,2,3 in order. Under `merge`, 3 may arrive before 1. Under `latest`, only 3's result survives if you emit all three within a tight window. Assert with Turbine on the emission order/contents.

**Estimated time.** 50 minutes.

---

## Problem 3 — `buffer` vs `conflate` vs `collectLatest`

**Problem statement.** With a fast producer and a slow collector, demonstrate the three backpressure strategies and assert the difference: `buffer` delivers every value (collector lags), `conflate` delivers only the latest of a burst, `collectLatest` cancels the collector's work-in-progress when a new value arrives. Record which values each strategy delivered in `notes/backpressure.md`.

**Acceptance criteria.**

- Three tests (or three runs) recording the delivered values for each strategy.
- `notes/backpressure.md` states which values each delivered and why.
- 0 warnings. Committed.

**Hint.** Producer emits 1..10 quickly; collector `delay`s per value. With `conflate`, you'll see 1 then maybe 10 (intermediates dropped). With `collectLatest`, the slow body is cancelled and restarted, so only the last completes. Use virtual time so it's deterministic.

**Estimated time.** 45 minutes.

---

## Problem 4 — `StateFlow` de-dup and `update { }` correctness

**Problem statement.** Build a `MutableStateFlow<Int>` counter incremented from many concurrent coroutines. First show that `value = value + 1` from N coroutines loses updates (a race), then show that `update { it + 1 }` does not. Also assert that setting `StateFlow` to an equal value does not emit.

**Acceptance criteria.**

- A test showing concurrent `value = value + 1` under-counts (race).
- A test showing concurrent `update { it + 1 }` reaches the exact total.
- A Turbine test showing an equal value is not emitted (de-dup).
- 0 warnings. Committed.

**Hint.** Launch many coroutines (e.g. on `Dispatchers.Default` via `withContext`, outside `runTest`'s single thread, or use `UnconfinedTestDispatcher` carefully) to expose the race. `update` uses compare-and-set so it's safe. Keep the de-dup test on `runTest`.

**Estimated time.** 50 minutes.

---

## Problem 5 — A `callbackFlow` that bridges and cleans up

**Problem statement.** Bridge a fake "clock tick" callback source into a `Flow<Long>` with `callbackFlow`, emitting on each tick and unregistering in `awaitClose`. Write a test asserting (a) ticks become emissions and (b) after the collector is cancelled, the source has zero registered callbacks. Then deliberately delete the `awaitClose` cleanup, observe the failing leak assertion, and restore it — note the failure in `notes/leak.md`.

**Acceptance criteria.**

- `callbackFlow` bridge with registration + `awaitClose` unregistration.
- Test (a): ticks emitted; test (b): zero registered callbacks after cancel.
- `notes/leak.md` records the failing-assertion experiment.
- 0 warnings. Committed.

**Hint.** Mirror exercise 03's `FakeSensor`/`listenerCount` shape with a clock. The leak proof is `count == 0` asserted *outside* the Turbine block after `cancelAndIgnoreRemainingEvents()`.

**Estimated time.** 45 minutes.

---

## Problem 6 — Event vs state, the rotation test

**Problem statement.** Model a one-shot navigation event two ways — a `StateFlow<Nav?>` (wrong) and a `SharedFlow<Nav>(replay = 0)` (right) — and write a Turbine test for each simulating a rotation (collect, cancel, collect again). Assert the `StateFlow` replays the event to the second subscriber (the bug) and the `SharedFlow` does not (the fix). One sentence: why is an event not state?

**Acceptance criteria.**

- A test showing the `StateFlow` version re-delivers the event to a re-subscription (bug demonstrated).
- A test showing the `SharedFlow(replay = 0)` version delivers nothing to the late subscriber (`expectNoEvents()`).
- A one-sentence justification.
- 0 warnings. Committed.

**Hint.** This is the challenge in miniature. The "rotation" is two sequential Turbine `test { }` blocks on the same flow. For the fixed version, emit while the first collector is present, then subscribe again and `expectNoEvents()`.

**Estimated time.** 45 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, code is idiomatic Kotlin/Flow, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. `value = value + 1` left where `update {}` belonged, a `collect` where a Turbine assertion was cleaner, a missing `asStateFlow()`). |
| 3 | Works, but misses one criterion (e.g. wrong flat-map for the case, event modelled as state, a `callbackFlow` without the leak assertion). |
| 2 | Compiles and partially works; a core idea is wrong (cold flow shared eagerly where lazy was the point; replay > 0 for an event; unbounded buffer). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for any `Thread.sleep` in a flow test instead of Turbine + virtual time; **−2** for a one-shot event modelled as `StateFlow`/`replay > 0` (the rotation bug); **−2** for a `callbackFlow` with no `awaitClose` cleanup; **−1** for the wrong flat-map operator for the stated semantics; **−1** for an unbounded buffer/channel under a fast producer.

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — cold-vs-hot ownership (problems 1, 6) and operator selection (`flatMapLatest`, backpressure — problems 2, 3) — so re-run exercises 01 and 02 before resubmitting.
