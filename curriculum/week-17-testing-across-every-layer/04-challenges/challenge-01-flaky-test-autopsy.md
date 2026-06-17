# Challenge 1 — Flaky-test autopsy (find the non-determinism, kill it)

**Time.** 60–120 minutes.
**Deliverable.** A short report (`FLAKES.md`) with, for each of the five tests: the symptom, the root cause, the fix, and the before/after run counts; plus the fixed test code committed, and a CI-style loop showing 100 consecutive green runs.

## The premise

Every team has lived this: a test goes red on CI, someone hits "re-run," it goes green, and the failure is forgotten. Do that a hundred times and the team has trained itself to ignore red — so the day a *real* regression turns a test red, it gets re-run away too. A flaky test is worse than no test. The senior skill is not "write tests"; it's **make tests deterministic**, and when one flakes, *autopsy it* — find the exact source of non-determinism and remove it, rather than adding a retry.

You inherit five tests that pass *sometimes*. Each flakes for a different, classic reason. Your job: run each enough times to see it flake, diagnose the root cause, fix it so it's deterministic, and document the autopsy.

## The five flaky tests (the WRONG versions)

Drop these into a Compose/coroutines project's `test/` source set. They are written to flake.

### Flake 1 — the real clock

```kotlin
@Test
fun `debounced search fires after the delay`() = runTest {
    val results = mutableListOf<String>()
    val searcher = Searcher(debounceMs = 300)
    searcher.onResult = { results.add(it) }

    searcher.query("ab")
    Thread.sleep(350)                       // FLAKE: real wall-clock sleep
    assertEquals(listOf("ab"), results)     // sometimes the result hasn't landed yet
}
```

### Flake 2 — the real Main dispatcher

```kotlin
@Test
fun `viewmodel loads on the main dispatcher`() = runTest {
    val vm = CheckoutViewModel(FakeOrderRepository().apply { cart = sample }, RecordingAnalytics())
    vm.load()                               // launches in viewModelScope (Dispatchers.Main)
    // FLAKE: no MainDispatcherExtension, so Dispatchers.Main isn't the test dispatcher;
    // on the JVM this throws "Module with the Main dispatcher had failed to initialize"
    // — or, if some other test set Main first, it races on ordering.
    assertEquals(CheckoutUiState.Content(sample, 500), vm.uiState.value)
}
```

### Flake 3 — shared mutable state across tests

```kotlin
object TestCart {                            // FLAKE: a shared singleton mutated by tests
    val items = mutableListOf<CartItem>()
}

@Test fun `cart starts empty`() {
    assertEquals(0, TestCart.items.size)     // passes ONLY if no earlier test added to it
}

@Test fun `add puts one item in the cart`() {
    TestCart.items.add(CartItem("sku-1", 500))
    assertEquals(1, TestCart.items.size)     // leaks into the next test
}
```

### Flake 4 — order dependence

```kotlin
private var nextId = 0                        // FLAKE: shared counter, order-dependent

@Test fun `first order gets id 0`() {
    assertEquals(0, nextId++)                 // passes only if this runs FIRST
}
@Test fun `second order gets id 1`() {
    assertEquals(1, nextId++)                 // passes only if it runs SECOND
}
```

### Flake 5 — an emission-timing race

```kotlin
@Test
fun `state flow emits content`() = runTest {
    val vm = CheckoutViewModel(FakeOrderRepository().apply { cart = sample }, RecordingAnalytics())
    val emissions = mutableListOf<CheckoutUiState>()
    val job = launch { vm.uiState.toList(emissions) }   // FLAKE: races the producer
    vm.load()
    // sometimes we read emissions before the collector has caught up
    assertTrue(emissions.any { it is CheckoutUiState.Content })
    job.cancel()
}
```

## What to do

### Step 1 — see each flake

Run each test in a loop until it fails (or reason about why it must). A quick loop:

```bash
# Run a single test 100 times; stop on first failure.
for i in $(seq 1 100); do
  ./gradlew :app:testDebugUnitTest --tests "*Flake1*" -q || { echo "FAILED on run $i"; break; }
done
```

(Flakes 2 and 4 may fail *deterministically* depending on test order — that's still a flake: the result depends on something other than the code under test.)

### Step 2 — diagnose each to its root cause

For each, write the *category* of non-determinism. They map to lecture 2's determinism checklist:

- **Flake 1** — real clock. The `Thread.sleep`/real `delay` races the debounce.
- **Flake 2** — real `Dispatchers.Main`. No `MainDispatcherExtension` to swap it.
- **Flake 3** — shared mutable state. A singleton leaks between tests.
- **Flake 4** — order dependence. A shared counter assumes execution order.
- **Flake 5** — emission-timing race. A manual collector races the producer instead of using Turbine.

### Step 3 — fix each deterministically

- **Flake 1:** drop `Thread.sleep`; advance the *virtual* clock with `advanceTimeBy(350)` (and make `Searcher` use a passed-in `CoroutineScope`/dispatcher, not a hard-coded real one). The debounce now fires in virtual time.
- **Flake 2:** add the `MainDispatcherExtension` (`Dispatchers.setMain(testDispatcher)`); `advanceUntilIdle()` before asserting `uiState.value`.
- **Flake 3:** kill the singleton; build a fresh cart (or fresh fake) in `@BeforeEach`. State must not survive a test.
- **Flake 4:** remove the shared counter; each test creates its own id generator, or assert a property (ids are distinct and increasing) instead of exact values.
- **Flake 5:** replace the manual collector with Turbine's `vm.uiState.test { ... }` — `awaitItem()` suspends for the producer, so there's no race.

### Step 4 — prove the fix

Run each fixed test 100× green. Capture the loop output (all-pass) for `FLAKES.md`.

## Acceptance criteria

- [ ] All five tests are fixed and pass **100 consecutive runs** (capture the loop output).
- [ ] `FLAKES.md` has, per test: symptom, root-cause category, the specific fix, and before/after run counts (e.g. "before: failed ~1 in 6; after: 100/100 green").
- [ ] No fix uses a retry, a `Thread.sleep`, an increased timeout, or `@Disabled` — the non-determinism is *removed*, not hidden.
- [ ] Flakes 3 and 4 demonstrate state isolation: a fresh world per test, no shared mutable statics.
- [ ] Flake 5 uses Turbine, not a manual collector.
- [ ] Build with **0 warnings**.

## What "great" looks like

A weak submission says "I fixed the flaky tests." A great submission says:

> Flake 1 failed ~1 run in 6 on the CI machine (a slower box than my laptop, which masked it) because the `Thread.sleep(350)` raced a debounce scheduled on a real dispatcher — on a loaded machine the coroutine hadn't run by the time the assertion read `results`. The fix removed the sleep entirely: the `Searcher` now takes its scope from the test, and `advanceTimeBy(350)` advances the *virtual* clock, so the debounce fires deterministically before the assertion, every run. After: 100/100 green, and the test runs in 2ms instead of 350. Flake 2 wasn't timing — it was missing infrastructure: with no `MainDispatcherExtension`, `viewModelScope.launch` had no Main dispatcher on the JVM, so it threw or raced whichever other test happened to call `setMain` first. Adding the extension made `Dispatchers.Main` a controlled `TestDispatcher`, and `advanceUntilIdle()` made the load complete before the assert.

Each flake named, each root cause categorized, each fix removing the non-determinism rather than papering over it. That's the autopsy a senior engineer writes in the PR.

## Where this reappears

The "control the inputs so the output is deterministic" discipline is exactly what Week 18 needs to get a *stable* macrobenchmark: a noisy benchmark and a flaky test are the same disease (uncontrolled inputs — thermal state, background work, a real clock), cured the same way (lock down the variables). And the "fresh world per test" isolation is the foundation of the green, layered CI run your mini-project ships this week and your capstone ships in Week 23.
