# Week 08 — State, side effects, snapshots

Welcome to Week 08 of **C21 · Crunch Droid**. Last week you learned the Compose runtime as a machine that re-invokes your functions when state changes — and you used `remember { mutableStateOf(...) }`, `LaunchedEffect`, and `derivedStateOf` as black boxes just deep enough to drive a timer. This week we open every one of those boxes. By Friday you will know exactly what a `MutableState` *is* (a snapshot-backed cell, not a field), how `remember` keeps it across recompositions, when `rememberSaveable` keeps it across *configuration changes and process death*, and — the heart of the week — which side-effect API is keyed to which lifecycle hook, so you reach for the right one the first time instead of cycling through all five until something works.

The mental shift this week is from "I changed a variable and the UI updated" to "I wrote to a snapshot, the snapshot system diffed it, and it notified exactly the scopes that read it." Compose state is not a plain Kotlin `var`. It is a cell managed by the **snapshot system** — a transactional, MVCC-style memory model borrowed from database design, where reads are tracked and writes are applied atomically. That sounds heavy, and you will never type the word `Snapshot` in app code, but the model explains everything that confuses people about Compose state: why writing state from a background thread is safe, why a read inside a composable subscribes that composable to the state, why `derivedStateOf` only fires when its *result* changes, and why `snapshotFlow` can turn any snapshot read into a cold `Flow` you can debounce and operate on with everything you learned in Week 5.

The thing this week hammers on is that **side effects in a declarative world need a discipline, because your composable runs again and again.** In imperative UI you start a network call in a click handler and it runs once. In Compose, if you start a coroutine directly in the composable body, it starts *again on every recomposition* — a bug factory. The side-effect APIs exist to tame this: `LaunchedEffect` runs a coroutine tied to the composition and cancels it when its key changes or the composable leaves; `DisposableEffect` pairs setup with teardown for things that must be cleaned up (listeners, callbacks); `rememberCoroutineScope` gives you a scope to launch from *event* handlers (not the composition); `produceState` bridges a non-Compose async source into `State`; `derivedStateOf` computes a value that only notifies when it actually changes; and `snapshotFlow` turns snapshot reads into a `Flow`. Each is keyed to a precise moment in the composition lifecycle, and the skill this week earns is naming that moment for each one — so you never again launch a coroutine that fires on every recomposition.

We close the week by building a **search-as-you-type** screen: a text field whose input drives a query that hits a (fake) repository, debounced so you don't fire a request per keystroke, cancelling the prior in-flight query when a new keystroke arrives, and surviving a screen rotation without losing the typed text or re-running the search from scratch. You will wire the debounce with `snapshotFlow { query }.debounce(300)` — bridging snapshot state into Flow and using `Week 5`'s `flatMapLatest`/`debounce` operators — keep the query text in `rememberSaveable` so rotation doesn't wipe it, and run the network call in a `LaunchedEffect` (or `produceState`) keyed to the debounced query so each new query cancels the last. That combination — snapshot state bridged to Flow, debounced, cancellation-correct, and configuration-change-proof, all *without* a `ViewModel* — is the senior-engineer instinct this week installs. (The `ViewModel` comes in Week 12; this week proves how much you can do correctly with snapshots alone.)

## Learning objectives

By the end of this week, you will be able to:

- **Explain** the snapshot system: a `MutableState` is a snapshot-backed cell; reads are tracked, writes are applied transactionally, and that read-tracking is what scopes recomposition to the composables that actually read the state.
- **Choose** between `remember` (survives recomposition), `rememberSaveable` (survives configuration change and process death via the saved-instance-state bundle), and a `ViewModel` (survives configuration change but not process death without `SavedStateHandle`) — and say which boundary each one crosses.
- **Hoist** state out of a composable to make it stateless and reusable: lift the state up, pass the value down and an `onValueChange` lambda back up (the state-hoisting / UDF pattern).
- **Reach** for the correct side-effect API on the first try, by naming the lifecycle hook each is keyed to: `LaunchedEffect` (enter + key change), `DisposableEffect` (enter + leave, with cleanup), `rememberCoroutineScope` (event-driven launch), `produceState` (async source → State), `derivedStateOf` (computed state that notifies on result change), `SideEffect` (publish to non-Compose code every successful recomposition).
- **Bridge** snapshot state to a Kotlin `Flow` with `snapshotFlow`, then apply Week-5 operators (`debounce`, `distinctUntilChanged`, `flatMapLatest`) to build a debounced, cancellation-correct search.
- **Distinguish** `derivedStateOf` from a plain calculation, and know the rule: use it when an expensive or recomposition-triggering computation reads frequently-changing state but produces a result that changes rarely.
- **Survive** a configuration change without a `ViewModel` by keeping essential UI state in `rememberSaveable`, and explain what `rememberSaveable` can and cannot store (only `Saveable`/`Parcelable` types, with a custom `Saver` for the rest).

## Prerequisites

This week assumes you have completed **C21 weeks 1–7**, or have equivalent fluency. Specifically:

- You understand the Compose runtime from **Week 7**: composition, recomposition scoped to reads, the three phases, stability, and skippability. State is the thing those reads read; this week is what's behind the read.
- You are fluent in **coroutines and structured concurrency (Week 4)**: `CoroutineScope`, `launch`, `Job`, cancellation cooperativity. Every side-effect API this week is coroutine-shaped, and `LaunchedEffect`'s cancellation-on-key-change *is* structured concurrency applied to the composition lifecycle.
- You know **Flow, `StateFlow`, and the operator set (Week 5)**: `debounce`, `distinctUntilChanged`, `flatMapLatest`, cold vs hot. `snapshotFlow` produces a cold Flow you'll operate on with exactly these.
- You can read and write idiomatic **Kotlin 2.x** — delegated properties (`by`), lambdas, data classes — Weeks 1–3. `var x by remember { mutableStateOf(...) }` is a property delegate, and understanding that is half of why the syntax reads the way it does.

**Toolchain.** Android Studio Ladybug (2024.2)+, JDK 17, Kotlin 2.0+ with the Compose Compiler plugin, compileSdk 35 (Android 15), minSdk 24. The Compose BOM pins `androidx.compose.*`; you'll add `androidx.compose.runtime:runtime` (already transitive) and use `kotlinx.coroutines` (from Week 4). Everything this week runs in the emulator — and the rotation test is *the* test, so know how to rotate the emulator (`Ctrl+F11` / the rotate buttons).

## Topics covered

- **The snapshot system.** What a `MutableState<T>` is — a cell in the snapshot memory model. Reads tracked against the current scope; writes applied in a transaction; the MVCC-style isolation that makes background writes safe. Why a read *subscribes* and a write *notifies*. `mutableStateOf`, `mutableIntStateOf` (and the primitive-specialized variants that avoid autoboxing).
- **`remember` revisited.** Keeping a value across recompositions (Week 7) — now with the lifecycle precision: created on enter, retained across recomposition, discarded on leave. `remember(key)` as invalidation.
- **`rememberSaveable`.** Surviving configuration change and process death by writing to the saved-instance-state `Bundle`. What it can store (primitives, `Parcelable`, types with a registered `Saver`), and writing a custom `Saver` for a type that isn't `Parcelable`.
- **State hoisting and UDF.** Stateful vs stateless composables; lifting state to the lowest common owner; the `value` / `onValueChange` contract; why hoisting makes composables testable, reusable, and preview-friendly. Single source of truth.
- **`mutableStateOf` vs `MutableStateFlow`.** When state belongs in the composition (`mutableStateOf`) vs in a holder/`ViewModel` (`StateFlow`) — a preview of Week 12. The trade-offs and the `collectAsStateWithLifecycle` bridge.
- **`LaunchedEffect`.** A coroutine keyed to the composition: starts on enter, cancels and restarts when a key changes, cancels on leave. The keying rules and the classic "I keyed it on nothing / on the wrong thing" bugs.
- **`DisposableEffect`.** Setup + teardown for non-coroutine resources: register a listener on enter, `onDispose { }` to remove it on leave or key change. The `LifecycleEventObserver` pattern.
- **`rememberCoroutineScope`.** A scope tied to the composition's lifetime, for launching coroutines from *event callbacks* (a button's `onClick`), where `LaunchedEffect` (which runs in composition) is the wrong tool.
- **`produceState`.** Turning a non-Compose async source (a suspend function, a callback API, a Flow) into a `State<T>` with a loading/loaded shape, with cancellation handled for you.
- **`derivedStateOf`.** A computed `State` that recomputes when its inputs change but only *notifies readers* when its result changes. The exact rule for when it earns its keep (and when it's premature).
- **`SideEffect`.** Publishing Compose state to non-Compose code after every successful recomposition (analytics, third-party controllers).
- **`snapshotFlow`.** Converting snapshot-state reads into a cold `Flow`, so you can `debounce`, `distinctUntilChanged`, and `flatMapLatest` over UI state with Week 5's operators.
- **The decision table.** A single map from "I need to…" to "use this API," so the six side-effect tools stop blurring together.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                                | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|----------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | The snapshot system; `mutableStateOf`; `remember`; hoisting & UDF    |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | `rememberSaveable`; surviving rotation & process death; custom Saver |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | The side-effect family — each keyed to its lifecycle hook; footguns  |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | `derivedStateOf`, `snapshotFlow`; bridging to Flow; challenge        |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — search-as-you-type; debounce; cancel prior query      |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work; rotation survival; cancellation correctness  |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                           |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                      | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./README.md) | This overview (you are here) |
| [resources.md](./resources.md) | The Compose state docs, the side-effects guide, the snapshot-system deep dives, the saveable-state and lifecycle references, and the canonical talks |
| [lecture-notes/01-snapshot-state-remember-saveable.md](./lecture-notes/01-snapshot-state-remember-saveable.md) | The snapshot system end to end: `MutableState`, read-tracking and transactional writes, `remember` vs `rememberSaveable` vs `ViewModel`, state hoisting, and surviving rotation and process death |
| [lecture-notes/02-side-effects-derivedstate-snapshotflow.md](./lecture-notes/02-side-effects-derivedstate-snapshotflow.md) | Every side-effect API keyed to its lifecycle hook, `derivedStateOf` and when it earns its keep, `snapshotFlow` bridging snapshot state to Flow, and the side-effect footguns |
| [exercises/README.md](./exercises/README.md) | Index of the three exercises |
| [exercises/exercise-01-hoist-and-survive-rotation.md](./exercises/exercise-01-hoist-and-survive-rotation.md) | Hoist state to make a composable stateless, then move it to `rememberSaveable` and prove it survives rotation and process death |
| [exercises/exercise-02-pick-the-right-effect.kt](./exercises/exercise-02-pick-the-right-effect.kt) | Six small scenarios; pick and implement the correct side-effect API for each, and fix a coroutine that fires on every recomposition |
| [exercises/exercise-03-snapshotflow-debounce.kt](./exercises/exercise-03-snapshotflow-debounce.kt) | Bridge snapshot state to Flow with `snapshotFlow`, debounce it, and assert the emissions with Turbine |
| [challenges/README.md](./challenges/README.md) | Index of the challenge |
| [challenges/challenge-01-effect-footgun-then-fix.md](./challenges/challenge-01-effect-footgun-then-fix.md) | Plant the "coroutine restarts every recomposition / wrong key / leaked listener" footguns, observe the broken behavior, fix each with the right API and key, and document the before/after |
| [quiz.md](./quiz.md) | 13 questions on snapshots, `remember`/`rememberSaveable`, hoisting, the side-effect family, `derivedStateOf`, and `snapshotFlow` |
| [homework.md](./homework.md) | Six practice problems for the week |
| [mini-project/README.md](./mini-project/README.md) | Full spec for the search-as-you-type screen: `snapshotFlow` debounce, cancel-prior-query, `rememberSaveable` rotation survival |

## The "survives rotation" promise

Every week of Crunch Droid adds one production-grade contract a senior reviewer actually checks. This week's:

> **State the user created must survive a configuration change.** Type a search query, rotate the device, and the query text is still there — the field doesn't clear, the keyboard doesn't lose focus's worth of work, and the search doesn't restart from scratch. If a rotation wipes the typed text, the state ownership is wrong, no matter how clean the code looks.

You will *prove* this by rotating the emulator (`Ctrl+F11`) and, harder, by forcing process death (`adb shell am kill`, or "Don't keep activities" in Developer Options) and relaunching — `rememberSaveable` survives both; bare `remember` survives neither. "It stayed when I tapped around" is not the test; rotate it, and kill the process. The difference between `remember` and `rememberSaveable` is exactly the boundary this promise checks.

## A note on what's not here

Week 08 is the *state and side effects* week. It deliberately does **not** cover:

- **`ViewModel` and architecture.** We survive rotation with `rememberSaveable`, *on purpose*, to prove how far snapshots alone go. Hoisting state into a Jetpack `ViewModel` with a `StateFlow<UiState>`, `SavedStateHandle` for process death, and the Now-In-Android layering is **Week 12**. This week is the runtime-level state primitives; next-level architecture is later.
- **Layout, gestures, animation.** Custom `Layout`, `pointerInput`, and the animation APIs proper are **Week 09**. We use `animate*AsState` only as much as last week did.
- **Material 3 and theming.** Bare `TextField`, `Text`, `Column` and the default theme. Material 3, dynamic color, and edge-to-edge are **Week 11**.
- **Real networking.** The search hits a *fake* in-memory repository with an artificial delay. Retrofit, OkHttp, and a real backend are **Phase 3 (Week 15)**. This week the point is the *state and effect plumbing* around the call, not the call.

The point of Week 08 is narrow and deep: one memory model (snapshots), three retention boundaries (`remember`/`rememberSaveable`/`ViewModel`), six side-effect APIs each keyed to a lifecycle hook, and the `snapshotFlow` bridge that connects this week's UI state to Week 5's Flow operators.

## Up next

Continue to **Week 09 — Layout, gestures, animation, accessibility** once you have shipped this week's mini-project and proven the search survives rotation. Week 09 is the Compose *toolkit* week: custom `Layout`, `Modifier` ordering, `pointerInput` gesture detectors, the animation APIs in full, and Compose semantics for TalkBack. It builds directly on the state and side-effect plumbing you wired this week — a draggable, dismissable card needs gesture state managed exactly the way you learned here, and a spring-back animation is an `animate*AsState` reading a hoisted state value. Everything in Week 09 assumes you can pick the right side-effect API and survive a configuration change — the two ideas this week is graded on. Earn them here.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
