# Week 07 — Jetpack Compose: composition, recomposition, the three phases

Welcome to Week 07 of **C21 · Crunch Droid**, and to Phase 2. For six weeks you built the foundation: Kotlin 2.x as a real language, coroutines and Flow as a concurrency discipline, and the Android runtime as a thing you can reason about instead of fear. This week the foundation meets the screen. You start writing UI — but not the UI your Java predecessors wrote. There is no XML layout, no `findViewById`, no `RecyclerView.Adapter`, no `notifyDataSetChanged`. There is a function that takes data and emits UI, and a runtime that calls it again when the data changes. That function is a **composable**, the re-call is **recomposition**, and the whole of modern Android UI is built on understanding exactly what that runtime does and — more importantly — what it does *not* do.

The mental shift this week is from "I hold a view hierarchy and mutate it" to "I describe what the UI should look like for the current state, and the runtime figures out the diff." In the old `View` world, you created a `TextView` once and then called `setText` on it forever; the widget was a long-lived mutable object you owned. In Compose there is no `TextView` you keep. You write `Text(state.title)`, and when `state.title` changes the runtime re-invokes your function, compares the result to what it emitted last time, and updates only the bytes that actually changed on screen. Your composable is not a constructor. It is a description re-evaluated on every state change, and the single biggest source of Compose performance bugs is writing it as though it were not.

The thing this week hammers on is that **recomposition is not free, but it is also not what you think.** Beginners imagine that when state changes, Compose "redraws the whole screen," and they write defensive code to avoid it. That is wrong in both directions. Compose does *not* redraw the whole screen — it recomposes only the composables that read the state that changed, and only if it cannot prove they would produce the same output (that is the *skippability* story). And recomposition is *not the same thing as drawing* — Compose runs three separate phases (composition, layout, draw), state can be read in any one of them, and reading state in a later phase than necessary is how you make a screen smooth instead of janky. The skill this week earns is reading the **Compose Compiler report** to see which of your functions are skippable and which are not, understanding *why* a function is non-skippable (almost always an unstable parameter), and fixing it by intent rather than by cargo-culted `remember` calls.

We close the week by building a pure-Compose **Pomodoro timer** with a circular progress ring, an animated per-second tick, and — the part that makes the lesson concrete — a recomposition-counter overlay that lights up in debug builds so you can *watch* which parts of the screen recompose as the timer runs. A naive first version recomposes the entire screen every second; a deferred-read version recomposes almost nothing, animating the ring in the draw phase while the composition stays put. You will build the naive one, see the counters spin, and fix it. That before/after — "the whole screen recomposed every tick, now only the draw phase runs" — is the senior-engineer instinct this week installs.

## Learning objectives

By the end of this week, you will be able to:

- **Explain** the declarative model: a composable is a function from state to UI that the Compose runtime re-invokes when the state it reads changes, and the runtime diffs the emitted tree rather than mutating widgets you own.
- **Describe** the three phases — **composition** (what to show), **layout** (where it goes), **draw** (paint it) — name what each phase produces, and predict which phase a given piece of state should be read in.
- **Read** a Compose Compiler report (`stabilityReport`, `composables.txt`), identify which functions are `skippable`/`restartable` and which are not, and trace a non-skippable function to its unstable parameter.
- **Reason** about stability: why `@Stable` and `@Immutable` exist, why `List<T>` is unstable but `ImmutableList<T>` is stable, why a `var` in a class breaks stability, and what the compiler infers automatically.
- **Diagnose** unnecessary recomposition with the layout-inspector recomposition counts and a debug recomposition-counter overlay, and tell the difference between "recomposed too much" and "recomposed correctly."
- **Defer** state reads to the latest phase that needs them — read offsets in layout via `Modifier.offset { }`, read animating values in draw — so an animation runs without recomposing.
- **Write** stable parameters by intent: hoist state, pass lambdas that don't capture unstable receivers, use `key()` and stable collection types, and mark your own types `@Immutable` when they truly are.

## Prerequisites

This week assumes you have completed **C21 weeks 1–6**, or have equivalent fluency. Specifically:

- You can read and write idiomatic Kotlin 2.x — `val`/`var`, data classes, lambdas, trailing-lambda syntax, function types — Weeks 1–3. Compose is *built* on trailing-lambda syntax: `Column { ... }` is a function call whose last argument is a `@Composable () -> Unit`. If that sentence is opaque, re-read Week 3.
- You understand immutability and why `val` and read-only collection interfaces matter — Weeks 1–2. **Stability**, the central performance concept this week, is immutability viewed through the Compose compiler's lens.
- You have working coroutines fluency — `CoroutineScope`, `launch`, cancellation — Week 4. The animated tick in the mini-project is driven by a coroutine inside the composition, and next week's side-effect APIs are coroutine-shaped.
- Your toolchain is set up from Week 6: Android Studio (Ladybug or newer), the Android SDK, a working emulator or device, and a Gradle Kotlin DSL project with version catalogs. You will add the Compose BOM and the Compose compiler this week.

**Toolchain.** Android Studio Ladybug (2024.2) or newer, JDK 17, Kotlin 2.0+ with the **Compose Compiler Gradle plugin** (since Kotlin 2.0 the Compose compiler ships as a Kotlin plugin, `org.jetbrains.kotlin.plugin.compose`, not a separate `kotlinCompilerExtensionVersion`). Target SDK 35 (Android 15), compile SDK 35 or 36, minSdk 24. The Compose BOM pins all `androidx.compose.*` versions together. Everything this week runs in the emulator — no physical device, no Play Console account.

## Topics covered

- **The declarative model.** What a `@Composable` function is (a function the compiler rewrites to participate in the composition), what "emitting" UI means, and why there is no view object you hold a reference to. The `Composer` and the slot table in conceptual terms.
- **The composition tree.** How `Column { Text(a); Text(b) }` builds a tree of "group" nodes in the slot table, how positional memoization keys each call site, and why calling a composable in a loop or a conditional needs `key()`.
- **Recomposition.** What triggers it (a read of a `State` that changes), what *scope* recomposes (the nearest enclosing restartable function that read the state), and why recomposition is "intelligent" — it skips functions whose inputs did not change.
- **The three phases.** **Composition** produces the tree of what to show; **layout** measures and places each node (measure pass + placement pass); **draw** paints to the canvas. Each phase can read state, and each runs independently — a state change read only in draw skips composition and layout entirely.
- **Stability.** What "stable" means to the compiler (a type whose equality is well-behaved and whose public properties don't change without notifying composition). `@Stable` and `@Immutable` as promises you make to the compiler. Why `List` is unstable, why `kotlinx.collections.immutable` fixes it, and stability inference for your own classes.
- **Skippability.** A composable is *skippable* when the compiler can prove that, if all its parameters are equal to last time, it will produce the same output — so the runtime can skip re-invoking it. *Restartable* means it can be re-invoked independently. The conditions for each.
- **The Compose Compiler report.** Turning on `reportsDestination`, reading `composables.txt` (which functions are `restartable skippable`), reading `classes.txt` (which of your classes are `stable`), and using the report to find the one unstable parameter dragging a function out of skippable.
- **Diagnosing recomposition.** The Layout Inspector's recomposition counts, a `Modifier` that increments a counter on each recomposition, and the discipline of *measuring* recomposition rather than guessing.
- **Deferring reads.** `Modifier.offset { IntOffset(...) }` (lambda form) reads state in layout, not composition; `drawBehind { }` and `Canvas` read in draw. Lifting an animating value's read into a later phase so the animation does not recompose.
- **`remember` as a first taste.** `remember { }` to keep a value across recompositions; `remember(key)` to recompute when a key changes. (Full state and side-effect treatment is Week 8 — here it is just "how do I not recreate this object every recomposition.")
- **Practical stability fixes.** Hoisting unstable state up, wrapping collections in immutable types, marking lambdas stable, using `key()` in lists, and reaching for `@Immutable` data classes for UI state.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                                | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|----------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | Declarative model; the composition tree; recomposition scope         |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | The three phases; deferring reads; `remember` first taste            |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Stability, `@Stable`/`@Immutable`, skippability; footguns            |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | The Compose Compiler report; diagnosing recomposition; challenge     |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — Pomodoro timer; recomposition-counter overlay         |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work; defer the tick to draw; measure the fix      |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                           |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                      | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./00-overview.md) | This overview (you are here) |
| [resources.md](./01-resources.md) | The Compose docs, the "Thinking in Compose" and phases articles, the stability and Compiler-report guides, the Compose runtime source, and the canonical conference talks |
| [lecture-notes/01-declarative-ui-composition-recomposition.md](./02-lecture-notes/01-declarative-ui-composition-recomposition.md) | The declarative model end to end: composables, the composition tree, the slot table, recomposition scope, the three phases, and where state reads belong |
| [lecture-notes/02-stability-skippability-compiler-report.md](./02-lecture-notes/02-stability-skippability-compiler-report.md) | Stability, `@Stable`/`@Immutable`, skippable/restartable, reading the Compose Compiler report, diagnosing unnecessary recomposition, and the practical fixes |
| [exercises/README.md](./03-exercises/00-overview.md) | Index of the three exercises |
| [exercises/exercise-01-trace-recomposition-scope.md](./03-exercises/exercise-01-trace-recomposition-scope.md) | Instrument a screen with a recomposition counter, predict which scopes recompose on a state change, and confirm it |
| [exercises/exercise-02-stability-and-skippability.kt](./03-exercises/exercise-02-stability-and-skippability.kt) | Make a non-skippable composable skippable by fixing its unstable parameter; assert the stability inference with a test |
| [exercises/exercise-03-defer-state-read-to-draw.kt](./03-exercises/exercise-03-defer-state-read-to-draw.kt) | Move an animating value's read from composition into the draw phase and prove composition no longer runs per frame |
| [challenges/README.md](./04-challenges/00-overview.md) | Index of the challenge |
| [challenges/challenge-01-recomposition-footgun-then-fix.md](./04-challenges/challenge-01-recomposition-footgun-then-fix.md) | Plant a recomposition footgun (unstable lambda + `List` parameter), measure it with the Compiler report and Layout Inspector, refactor to skippable, and document before/after |
| [quiz.md](./05-quiz.md) | 13 questions on the declarative model, the three phases, stability, skippability, and the Compiler report |
| [homework.md](./06-homework.md) | Six practice problems for the week |
| [mini-project/README.md](./07-mini-project/00-overview.md) | Full spec for the pure-Compose Pomodoro timer with a circular progress ring, animated tick, and debug recomposition-counter overlay |

## The "recompose the minimum" promise

Every week of Crunch Droid adds one production-grade contract a senior reviewer actually checks. This week's:

> **A state change must recompose the smallest scope that read it — and an animation must not recompose at all.** If a once-per-second timer tick recomposes your whole screen, the screen is wrong, no matter how clean the code looks. Read the Compose Compiler report, confirm your composables are `skippable`, watch the recomposition counters under the Layout Inspector, and make a smoothly animating ring cost *zero* recompositions by reading the animating value in the draw phase.

You will *prove* this with the recomposition-counter overlay in the mini-project: a debug-only badge on each region that increments every time that region recomposes. The naive timer makes the badges spin once a second; the fixed timer freezes them while the ring keeps sweeping. "It looks smooth" is not the test — a screen can look smooth and still recompose forty times a second, burning battery and dropping frames under load. Watch the counters.

## A note on what's not here

Week 07 is the *Compose runtime* week. It deliberately does **not** cover:

- **State and side effects in depth.** `remember`, `mutableStateOf`, `LaunchedEffect`, `derivedStateOf`, `snapshotFlow` — the whole snapshot system and every side-effect API — is **Week 08**. This week uses `remember { mutableStateOf(...) }` as a black box just enough to drive the timer; next week opens it up.
- **Layout, gestures, animation, accessibility.** Custom `Layout`, `pointerInput`, the animation APIs proper, and Compose semantics for TalkBack are **Week 09**. This week's "draw phase" treatment is the conceptual groundwork; Week 09 is the toolkit.
- **Material 3 and theming.** We use bare `Text`, `Box`, `Canvas`, and the default theme. Material 3 components, dynamic color, and edge-to-edge are **Week 11**.
- **ViewModel and architecture.** State lives in `remember` in a composable this week. Hoisting it into a `ViewModel` with a `StateFlow<UiState>` is **Week 12**. The point this week is the *runtime*, not the architecture around it.

The point of Week 07 is narrow and deep: one runtime, the tree it builds, the three phases it runs, the stability that decides what it skips, and the report that tells you whether you got it right.

## Up next

Continue to **Week 08 — State, side effects, snapshots** once you have shipped this week's mini-project and proven the animated ring costs zero recompositions. Week 08 opens the box this week treated as opaque: what `mutableStateOf` actually is (a snapshot-backed object), how `remember` keeps it across recompositions, and the full family of side-effect APIs — `LaunchedEffect`, `DisposableEffect`, `rememberCoroutineScope`, `produceState`, `derivedStateOf`, `snapshotFlow` — each keyed to the exact lifecycle hook it belongs to. Everything in Week 08 assumes you can already say *which scope recomposes when* and *which phase a read belongs in* — the two ideas this week is graded on. Earn them here.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
