# Week 12 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 12 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Android 15 (`compileSdk 35`), `minSdk 24`, Kotlin 2.0+ with the Compose Compiler plugin. Tests run on the JVM with `runTest` + Turbine. Every problem must build with **0 warnings**.

---

## Problem 1 — Convert a flat-flags state to a sealed `UiState`

**Problem statement.** Find (or write) a screen using a flat `data class` state with `isLoading`/`error`/`data` flags. Model an equivalent sealed `UiState` (`Loading | Error | Success`, plus `Empty` if the screen needs it), convert the screen to an exhaustive `when`, and write down two contradictory states the old type permitted that the new one forbids.

**Acceptance criteria.**

- A `sealed interface UiState` with the right variants; the screen renders it with an exhaustive `when` and no `else`.
- `notes/illegal-states.md` lists two contradictions the flat type allowed and the new type forbids.
- Data is read via smart cast inside variants (no nullable fields). 0 warnings. Committed.

**Hint.** The classic contradictions: `isLoading = true` with non-null `data`, and `error != null` with non-empty `data`. Both are unrepresentable once the state is sealed.

**Estimated time.** 35 minutes.

---

## Problem 2 — A ViewModel that combines two flows

**Problem statement.** Build a `ViewModel` whose `StateFlow<UiState>` is derived by `combine`-ing two repository flows — e.g. an articles stream and a bookmarks set — into a `Success` state where each article carries an `isBookmarked` flag. Test that toggling a bookmark re-derives the state with the updated flag.

**Acceptance criteria.**

- `uiState` derived via `combine(articlesFlow, bookmarksFlow) { … }.stateIn(...)`.
- A Turbine test: emit articles, then change bookmarks, and assert the re-derived state reflects the new flags.
- `StateFlow` exposed read-only. 0 warnings. Committed.

**Hint.** `combine` re-emits when *either* input changes, so updating the bookmarks `MutableStateFlow` in your fake re-derives the `UiState` automatically. Assert the second `awaitItem()` has the article marked bookmarked.

**Estimated time.** 50 minutes.

---

## Problem 3 — `WhileSubscribed` vs `Eagerly`, observed

**Problem statement.** Build a `ViewModel` whose upstream repository flow *counts how many times it's collected* (increment a counter in the flow's `onStart`). Demonstrate the difference between `SharingStarted.Eagerly`, `Lazily`, and `WhileSubscribed(0)` by observing when the upstream starts and stops. Write your findings in `notes/sharing-started.md`.

**Acceptance criteria.**

- A flow that records collection start/stop; a `ViewModel` parameterized over the `SharingStarted` policy.
- `notes/sharing-started.md` describes, for each policy, when the upstream starts and when it stops relative to collectors.
- A test demonstrating at least the `WhileSubscribed` stop-after-last-collector behavior (using `runTest`/`advanceTimeBy`). 0 warnings. Committed.

**Hint.** Put a side effect in `.onStart { started++ }` / `.onCompletion { stopped++ }` on the upstream. With `WhileSubscribed(0)`, cancelling the Turbine collector stops the upstream immediately; with `Eagerly`, it started before any collector and never stops. `advanceTimeBy` lets you cross the `WhileSubscribed` timeout deterministically.

**Estimated time.** 50 minutes.

---

## Problem 4 — Place a feature in the right layer

**Problem statement.** Given a new requirement — "show a 'trending' badge on articles with >1000 views, and a 'trending' screen listing only those" — decide where each piece of code goes (data, domain, UI) and implement it. Write a one-paragraph justification of your layer placement and the dependency directions.

**Acceptance criteria.**

- The view-count data in the repository (data); the >1000 "trending" rule extracted to a use case **iff** it's reused by both the badge and the trending screen (domain); the rendering in the `ViewModel`/composable (UI).
- `notes/layering.md` justifies the placement and confirms no data-layer file imports a `ViewModel`.
- 0 warnings. Committed.

**Hint.** The "trending" rule is used in two places (badge + screen), so it earns a use case — that's the test for the domain layer (genuine reuse). If it were used once, the `ViewModel` would apply the rule directly and a use case would be ceremony.

**Estimated time.** 45 minutes.

---

## Problem 5 — A `SavedStateHandle`-backed selected-tab

**Problem statement.** Build a `ViewModel` where a selected category (an enum) lives in `SavedStateHandle` and the displayed list is *derived* from it via the repository. Write a round-trip test proving the selected category survives a `ViewModel` recreation while the list recomputes. Then verify on-device with "Don't keep activities".

**Acceptance criteria.**

- The category in `SavedStateHandle` via `getStateFlow`; the list derived with `flatMapLatest`/`combine`.
- A round-trip test: set category on one `ViewModel`, recreate from the same handle, assert the category survived and the list re-derives.
- An on-device "Don't keep activities" check noted in `notes/process-death.md`.
- 0 warnings. Committed.

**Hint.** Enums round-trip through `SavedStateHandle` fine (they're `Serializable`/`Parcelable`-compatible). Recreate the `ViewModel` from the *same* `SavedStateHandle` instance to simulate the system's restore — a fresh handle would be a fresh install.

**Estimated time.** 45 minutes.

---

## Problem 6 — Inject the dispatcher for testability

**Problem statement.** Take a `ViewModel` that does work on a background dispatcher and make it testable by *injecting* the `CoroutineDispatcher` (defaulting to `Dispatchers.Default` in production, a `StandardTestDispatcher` in tests). Write a test that uses the test dispatcher to control timing, and explain in `notes/dispatcher-injection.md` why hardcoding `Dispatchers.IO` makes a test flaky or slow.

**Acceptance criteria.**

- The `ViewModel` takes a `CoroutineDispatcher` parameter (default `Dispatchers.Default`); its background work uses it.
- A test passes `StandardTestDispatcher()` (from `runTest`'s scheduler) and drives time with `advanceUntilIdle`.
- `notes/dispatcher-injection.md` explains the flakiness of a hardcoded dispatcher.
- 0 warnings. Committed.

**Hint.** `runTest { val dispatcher = StandardTestDispatcher(testScheduler); val vm = MyViewModel(repo, dispatcher); … advanceUntilIdle() }`. A hardcoded `Dispatchers.IO` runs on real threads the test can't control, so assertions race the background work. This is the testability argument Hilt formalizes next week (it injects the dispatcher for you).

**Estimated time.** 45 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, code is idiomatic Kotlin/Compose/architecture, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. a flat-flag left in a sub-state, `Eagerly` where `WhileSubscribed` belonged, a use case that isn't actually reused). |
| 3 | Works, but misses one criterion (e.g. exposed `MutableStateFlow`, results stored instead of derived, a dependency pointing the wrong way). |
| 2 | Compiles and partially works; a core idea is wrong (UI mutates its own state, derived data saved in `SavedStateHandle`, two sources of truth). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for exposing a `MutableStateFlow` publicly or the UI mutating its own state; **−2** for storing derived output in `SavedStateHandle` (instead of saving the input and recomputing); **−1** for a data-layer file importing a `ViewModel` (wrong dependency direction).

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — sealed `UiState` with one source of truth (problems 1, 2) and save-inputs-recompute-outputs (problems 5) — so re-run exercises 01 and 03 before resubmitting.
