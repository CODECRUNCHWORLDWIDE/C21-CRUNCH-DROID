# Week 07 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 07 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Android Studio Ladybug+, Kotlin 2.0+, the Compose Compiler plugin, compileSdk 35, minSdk 24. Every problem must build with **0 warnings**.

---

## Problem 1 — Read the report on your own screen

**Problem statement.** Turn on the Compose Compiler report in any Compose screen you have (the exercise-1 `Scratch` app is fine). Build it, open `composables.txt` and `classes.txt`, and write your findings into `notes/report-anatomy.md`: list every composable and whether it's `restartable`/`skippable`, list every class and its stability, and for any non-skippable composable, name the unstable parameter and the field in `classes.txt` that caused it. Add one sentence: what would you change to make the worst offender skippable?

**Acceptance criteria.**

- `notes/report-anatomy.md` exists with the per-function and per-class findings, quoted from your actual report (not invented).
- The unstable parameter and the offending field are identified for at least one non-skippable composable.
- Committed.

**Hint.** `composeCompiler { reportsDestination = layout.buildDirectory.dir("compose_compiler") }`, then `./gradlew :app:assembleRelease`, then look under `app/build/compose_compiler/` for `*-composables.txt` and `*-classes.txt`.

**Estimated time.** 30 minutes.

---

## Problem 2 — Three parameters, three stabilities

**Problem statement.** Write three versions of the same row composable, each taking a different parameter type: (a) `List<Item>`, (b) `ImmutableList<Item>`, (c) a single `@Immutable` `ItemUi`. For each, build the report and record whether the composable is skippable. Write a one-line comment on each explaining *why* it has the stability the report shows.

**Acceptance criteria.**

- Three composables with the three parameter types; the report excerpt for each pasted into `notes/three-stabilities.md`.
- (a) not skippable, (b) and (c) skippable, with a one-line justification each.
- 0 warnings. Committed.

**Hint.** Add `kotlinx-collections-immutable` for `ImmutableList`. The `List` one is unstable; `ImmutableList` carries the `@Immutable` annotation; a `val`-only `data class` is inferred stable.

**Estimated time.** 40 minutes.

---

## Problem 3 — Predict-then-confirm recomposition

**Problem statement.** Build a screen with four regions: a header, a counter text, a static image placeholder, and a button. Wire a single `count` state that the counter text reads. Wrap each region in the `recompositionCounter()` modifier. *Write your prediction* of which regions recompose on a tap into `notes/predictions.md` **before running**, then run, confirm, and record any surprises.

**Acceptance criteria.**

- `notes/predictions.md` has the prediction written before the run, plus the confirmed result.
- The counter text recomposes; the static regions do not (assuming they're skippable).
- 0 warnings. Committed.

**Hint.** Reuse the `recompositionCounter()` modifier from exercise 1. The rule: only the scope that *read* `count` recomposes. If a "static" region recomposes, it has an unstable parameter — note that as a surprise and explain it.

**Estimated time.** 40 minutes.

---

## Problem 4 — Move a read across a phase

**Problem statement.** Animate a colored box pulsing its size with `animateFloatAsState` (or `rememberInfiniteTransition`). Implement it two ways: (1) read the scale in the composable body and apply it via `Modifier.size(scale.dp)` (composition read); (2) read it inside a `graphicsLayer { scaleX = ...; scaleY = ... }` lambda (layout/draw read). Use the Layout Inspector recomposition counts to show version 1 recomposes per frame and version 2 does not.

**Acceptance criteria.**

- Both versions render an identical pulse.
- A note in `notes/phase-read.md` records the recomposition count per second for each (version 1 ≈ 60/s, version 2 ≈ 0/s after first composition).
- 0 warnings. Committed.

**Hint.** `graphicsLayer { }` runs in the draw phase, so reading the scale there invalidates only draw. `Modifier.size(scale.dp)` reads in composition. Same value, different phase — exactly exercise 3's lesson with scale instead of an arc.

**Estimated time.** 45 minutes.

---

## Problem 5 — Key a list, prove it matters

**Problem statement.** Build a `LazyColumn` of items where each row holds a small piece of `remember`ed state (e.g. an expanded/collapsed toggle, or a `remember`ed random color). Provide a "shuffle" button that reorders the list. Run it twice: once with `items(list)` (no key) and once with `items(list, key = { it.id })`. Document what happens to the per-row remembered state on shuffle in each case.

**Acceptance criteria.**

- A `LazyColumn` whose rows hold `remember`ed state and a shuffle button.
- `notes/keying.md` records: without a key, remembered state stays with the *position* (so it appears to "jump" to whatever item lands there); with a key, it follows the *item*.
- 0 warnings. Committed.

**Hint.** Make the per-row state visible — a `remember { randomColor() }` border, or an expanded flag. Without a key, positional memoization matches by index; with a key, by `id`. The difference is obvious on shuffle.

**Estimated time.** 45 minutes.

---

## Problem 6 — A non-skippable function you can't immediately see

**Problem statement.** You're handed a composable `fun OrderSummary(order: Order)` that the report marks not skippable, but `Order` *looks* like a clean `val`-only data class. Investigate why: the cause is a nested field (a `List`, or a property of a type from another module the compiler can't inspect). Find it via `classes.txt`, fix it (immutable collection, or map the cross-module type to a UI type defined in your UI module), and confirm the report goes green.

**Acceptance criteria.**

- A reproduction: `Order` (or a nested type) is initially unstable; the report names the offending field.
- The fix (immutable collection and/or a UI-module mapping type) makes `OrderSummary` skippable.
- `notes/nested-stability.md` explains the root cause and the fix.
- 0 warnings. Committed.

**Hint.** Stability is transitive. A `val`-only class with a `List` field is still unstable because the `List` is. A class from a non-Compose module is unstable because the compiler can't see it. `classes.txt` shows the nested field's stability — follow it down. The cross-module fix is the Now-In-Android pattern: composables take UI types, not domain types (lecture 2, footgun 4).

**Estimated time.** 40 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, code is idiomatic Compose, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. a bare `List` left where `ImmutableList` was the point, a read left in composition where it could defer). |
| 3 | Works, but misses one criterion (e.g. prediction written *after* running, phase-read version still recomposes, list keyed but the proof not documented). |
| 2 | Compiles and partially works; a core idea is wrong (claims a `var`-bearing class is stable; reads an animating value in composition and calls it deferred). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for marking a type `@Immutable`/`@Stable` that actually mutates (a correctness lie, not just a perf miss); **−2** for reading an animating value in composition where the problem asked you to defer it; **−1** for a bare `List`/`Map`/`Set` parameter on a hot composable where an immutable type was the point.

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — stability-and-skippability (problems 1, 2, 6) and reading state in the right phase (problems 4, 5) — so re-run exercises 02 and 03 before resubmitting.
