# Week 08 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 08 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Android Studio Ladybug+, Kotlin 2.0+, the Compose Compiler plugin, compileSdk 35, minSdk 24. Every problem must build with **0 warnings**.

---

## Problem 1 — The three retention boundaries, demonstrated

**Problem statement.** Build one screen with three counters: counter A in `remember`, counter B in `rememberSaveable`, counter C in a `ViewModel`'s `StateFlow` (you may peek ahead to a minimal `ViewModel` — `viewModel()` + a `MutableStateFlow`). Increment all three. Then: (1) rotate, (2) enable "Don't keep activities" and force process death. Record in `notes/boundaries.md` which counters survive which event.

**Acceptance criteria.**

- Three counters in the three holders; all increment.
- `notes/boundaries.md` records the survival matrix: A survives neither rotation nor death; B survives both; C survives rotation but not death (without `SavedStateHandle`).
- Committed.

**Hint.** The matrix from lecture 1, §5. The `ViewModel` survives rotation because it's scoped to the Activity/nav entry, not the composition; it dies with the process. Use `androidx.lifecycle:lifecycle-viewmodel-compose` for `viewModel()`.

**Estimated time.** 40 minutes.

---

## Problem 2 — A custom `Saver`

**Problem statement.** Define a `data class WizardState(val step: Int, val name: String, val agreed: Boolean)` and store it in `rememberSaveable` via a custom `Saver` (use `mapSaver` or `listSaver`). Drive a three-step wizard with it; rotate at step 2 and confirm you're still on step 2 with the entered name. Then re-implement using `@Parcelize` and note which you prefer.

**Acceptance criteria.**

- `WizardState` survives rotation via a custom `Saver`.
- A second commit re-implements it with `@Parcelize` (no explicit `Saver`).
- `notes/saver.md` notes the trade-off (explicit `Saver` is more verbose but needs no plugin; `@Parcelize` is terse but adds the `kotlin-parcelize` plugin).
- 0 warnings. Committed.

**Hint.** `mapSaver(save = { mapOf("step" to it.step, ...) }, restore = { WizardState(it["step"] as Int, ...) })`. For `@Parcelize`, add `id("kotlin-parcelize")` to the module plugins and annotate the class `@Parcelize` implementing `Parcelable`.

**Estimated time.** 45 minutes.

---

## Problem 3 — Pick the effect (six mini-scenarios, written)

**Problem statement.** Without writing full apps, write `notes/effect-choices.md`: for each of six one-line scenarios, name the correct side-effect API and the lifecycle hook it keys to. Scenarios: (a) log "screen viewed" exactly once; (b) reload when a filter changes; (c) launch a share from a tap; (d) register a connectivity callback that must be removed; (e) compute a boolean from a fast-scrolling index that rarely flips; (f) push the current theme to a non-Compose analytics SDK after each commit.

**Acceptance criteria.**

- All six correctly mapped: (a) `LaunchedEffect(Unit)`; (b) `LaunchedEffect(filter)`; (c) `rememberCoroutineScope().launch`; (d) `DisposableEffect` + `onDispose`; (e) `derivedStateOf`; (f) `SideEffect`.
- Each includes the hook (enter, key change, event, leave, result-change, post-commit).
- Committed.

**Hint.** This is the decision table from lecture 2, §9, applied. If two feel similar, ask "what triggers the work, and does it need cleanup?"

**Estimated time.** 30 minutes.

---

## Problem 4 — Fix a coroutine that fires every recomposition

**Problem statement.** You're given a composable that calls `repo.refresh()` directly in its body, firing on every recomposition. Reproduce the storm (log a counter), then fix it so it fires exactly once on appear and again only when a `category` key changes. Document the before/after counts.

**Acceptance criteria.**

- A reproduction logging the per-recomposition fire, and a fix using `LaunchedEffect(category)`.
- `notes/refresh-fix.md` records the before count (many) and after count (once per category).
- 0 warnings. Committed.

**Hint.** Cause recompositions by toggling unrelated state nearby. The fix is moving the call into `LaunchedEffect(category)`. The key is the dependency: `category` changing should restart the refresh; an unrelated recomposition should not.

**Estimated time.** 40 minutes.

---

## Problem 5 — `derivedStateOf` vs not

**Problem statement.** Build a long `LazyColumn` and a "scroll to top" FAB that appears once the first visible index passes 0. Implement the visibility flag two ways: (1) reading `listState.firstVisibleItemIndex > 0` directly in the composable; (2) via `derivedStateOf`. Use the Layout Inspector recomposition counts (Week 7) to show version 1 recomposes on every scroll frame and version 2 recomposes only when the flag flips.

**Acceptance criteria.**

- Both versions show/hide the FAB correctly.
- `notes/derived.md` records the recomposition counts during a scroll for each (version 1: many; version 2: ~2 per scroll session).
- 0 warnings. Committed.

**Hint.** `firstVisibleItemIndex` changes constantly during a scroll. Reading it directly subscribes the composable to it; wrapping the boolean in `derivedStateOf` subscribes the composable to the *boolean*, which rarely changes. This is lecture 2, §6's exact example.

**Estimated time.** 45 minutes.

---

## Problem 6 — A tested `snapshotFlow` debounce pipeline

**Problem statement.** Extract a pure `Flow<String> -> Flow<String>` debounce/dedupe/filter pipeline (exercise 3's shape) and write a Turbine + `runTest` test that, in virtual time, asserts: a fast burst of keystrokes emits only the settled value, a duplicate settled value is dropped, and a 1-char query is filtered out. Then wire it into a real `SearchScreen` with `snapshotFlow { query }` and confirm by hand it debounces.

**Acceptance criteria.**

- A pure pipeline function and a passing Turbine test asserting debounce, dedupe, and the length filter in virtual time.
- The pipeline wired into a `SearchScreen` via `snapshotFlow { query }`, debouncing in the app by inspection.
- 0 warnings. Committed.

**Hint.** `runTest` gives virtual time so `debounce(300)` resolves instantly; use `delay` (not `Thread.sleep`) to simulate typing gaps. `flow.test { assertEquals("kotlin", awaitItem()); awaitComplete() }`. Order operators: `trim` → `debounce` → `distinctUntilChanged` → `filter`.

**Estimated time.** 50 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, code is idiomatic Compose, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. a missing key, `remember` where `rememberSaveable` was the point, an effect keyed slightly too broadly). |
| 3 | Works, but misses one criterion (e.g. the derived version still subscribes to the raw index, the pipeline test passes but doesn't use virtual time). |
| 2 | Compiles and partially works; a core idea is wrong (a network call left in the composable body; `LaunchedEffect(Unit)` where a key was needed). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for a coroutine or request launched in the composable body (not tied to an effect); **−2** for a registered listener with no `onDispose` teardown (a leak); **−1** for `remember` where the problem required surviving a configuration change.

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — picking the right side-effect API with the right key (problems 3, 4) and the snapshot-state/Flow bridge (problems 5, 6) — so re-run exercises 02 and 03 before resubmitting.
