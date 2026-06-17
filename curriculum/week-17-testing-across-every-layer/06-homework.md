# Week 17 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 17 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Android Studio Ladybug+, Kotlin 2.0+, JUnit 5 (JVM) / JUnit 4 (instrumentation), Turbine, MockK, Robolectric, Roborazzi, Espresso. Every problem must build with **0 warnings**, and every JVM test must be **deterministic** (no real clock, no real I/O).

---

## Problem 1 — Build the `MainDispatcherExtension` and a fake

**Problem statement.** Write a reusable `MainDispatcherExtension` (JUnit 5) that swaps `Dispatchers.Main` for a `TestDispatcher` in `beforeEach` and resets it in `afterEach`. Then write a `FakeRepository` for any repository interface you have, with at least one test knob (a "fail next call" flag). Use both in a single ViewModel test that asserts a `Loading` → `Content` transition.

**Acceptance criteria.**

- `MainDispatcherExtension` registered with `@JvmField @RegisterExtension`; the test no longer throws "Main dispatcher failed to initialize."
- `FakeRepository` has a knob that drives both the success and failure paths.
- The test asserts the `Loading` emission *and* the terminal emission via Turbine.
- 0 warnings. Committed.

**Hint.** `Dispatchers.setMain(testDispatcher)` / `Dispatchers.resetMain()`. The fake's knob is just a `var` the test sets before calling the ViewModel. Remember the `StateFlow` replays its current value first.

**Estimated time.** 40 minutes.

---

## Problem 2 — Turbine on a real `StateFlow`

**Problem statement.** Take a `ViewModel` whose `uiState` is built with `.map { }.stateIn(...)`. Write a Turbine test that asserts the full sequence on a state change, using `cancelAndIgnoreRemainingEvents()` (not `awaitComplete()`), and explain in a comment why `awaitComplete` would be wrong here.

**Acceptance criteria.**

- The test collects `uiState` with `test { }`, asserts each emission, and ends with `cancelAndIgnoreRemainingEvents()`.
- A one-line comment explains: a `StateFlow` never completes, so `awaitComplete()` would hang/fail.
- 0 warnings. Committed.

**Hint.** `stateIn` produces a hot `StateFlow`. Turbine requires you consume or cancel every event; for a never-completing flow that means `cancelAndIgnoreRemainingEvents()`.

**Estimated time.** 35 minutes.

---

## Problem 3 — Mock an interaction, fake the state

**Problem statement.** In one ViewModel test, demonstrate the fakes-vs-mocks judgment: use a hand-written **fake** for the stateful repository and a MockK **mock** for a fire-and-forget `Analytics` collaborator. Verify with `coVerify` (or `verify`) that analytics was called exactly once on the success path and `exactly = 0` on the failure path.

**Acceptance criteria.**

- A fake repository (stateful) and a MockK mock analytics in the same test class.
- `verify/coVerify(exactly = 1)` on success, `exactly = 0` on failure.
- A one-line note on *why* the repo is a fake and analytics is a mock.
- 0 warnings. Committed.

**Hint.** The repo has behavior worth encoding once → fake. Analytics is fire-and-forget where you only care *that* a call happened → mock. Use the right double for each; that's the senior signal.

**Estimated time.** 40 minutes.

---

## Problem 4 — A Robolectric DAO test with the empty-set trap

**Problem statement.** Write a Room DAO with a `@Query("SELECT SUM(...)")` aggregate and a Robolectric test against an in-memory database. Deliberately write the query *without* `COALESCE` first, watch the empty-set test fail (returns null), add `COALESCE(..., 0)`, and watch it pass. Document the before/after.

**Acceptance criteria.**

- A Robolectric test (`@RunWith(RobolectricTestRunner::class)`) with an in-memory Room DB.
- The empty-cart case is tested and proves `SUM` over zero rows is `0`, not null, after the `COALESCE` fix.
- `notes/dao-coalesce.md` records the before (null/crash) and after (0).
- 0 warnings. Committed.

**Hint.** `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()`. In SQLite, `SUM` over an empty set is `NULL`; `COALESCE(SUM(x), 0)` makes it 0. This is exactly why the DAO needs a *medium* test, not a fake.

**Estimated time.** 45 minutes.

---

## Problem 5 — Compose UI test + one screenshot golden

**Problem statement.** Take any composable with at least two states (e.g. a button that's enabled/disabled, or a row with a stepper). Write a Compose UI test (JVM via Robolectric) that finds nodes by tag/content-description and asserts the interaction, then a Roborazzi screenshot test that records *two* goldens (the two states), configuration-pinned.

**Acceptance criteria.**

- The UI test finds nodes by `testTag`/`contentDescription` (not brittle literal text) and asserts a state change after an action.
- Two committed Roborazzi goldens, pinned with `@Config(qualifiers = "...")`.
- Both run on the JVM (no `connectedAndroidTest`).
- 0 warnings. Committed.

**Hint.** `recordRoborazziDebug` writes the PNGs (commit them); `verifyRoborazziDebug` compares. Pin `qualifiers = "w400dp-h800dp-mdpi"` so the golden is identical across machines.

**Estimated time.** 45 minutes.

---

## Problem 6 — Audit a suite for flakiness

**Problem statement.** Take any existing test suite (yours, or the mini-project's) and audit it against lecture 2's determinism checklist. Find at least two real or potential sources of non-determinism (a `Thread.sleep`, a missing `MainDispatcherExtension`, a shared mutable static, an order-dependent assertion, a manual flow collector) and fix each. Write the audit.

**Acceptance criteria.**

- `notes/flake-audit.md` lists at least two findings, each with the category (real clock / real dispatcher / shared state / order dependence / emission race) and the fix.
- The fixes remove the non-determinism (no retries, no longer timeouts, no `@Disabled`).
- The audited tests run 50× green (capture the loop).
- 0 warnings. Committed.

**Hint.** The checklist: no real clock, no real I/O, no shared mutable state, no order dependence, no reliance on emission timing. If you can't find two in your own suite, plant them (a `Thread.sleep`, a shared counter), see them flake, then fix — the autopsy is the learning.

**Estimated time.** 35 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, the test is deterministic and at the right tier, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. a mock where a fake was the point, a golden left unpinned, an `awaitComplete` on a never-completing flow). |
| 3 | Works, but misses one criterion (e.g. asserts only the final emission, screenshot golden not committed, DAO tested with a fake instead of Robolectric). |
| 2 | Compiles and partially works; a core idea is wrong (a flaky test "fixed" with a retry, a logic check done through Espresso, a SQL check done with a fake). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for any non-deterministic JVM test (a real clock, real network, shared mutable state) shipped as "passing"; **−2** for a `Thread.sleep`/retry/longer-timeout used to mask a flake instead of removing it; **−1** for testing the wrong tier (a logic check through Espresso, a SQL check with a fake DAO, a visual check with an assertion instead of a screenshot).

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — deterministic Flow/ViewModel testing (problems 1, 2, 3, 6) and picking the right tier (problems 4, 5) — so re-run exercises 02 and 03 before resubmitting.
