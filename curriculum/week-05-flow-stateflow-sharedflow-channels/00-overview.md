# Week 05 — Flow, StateFlow, SharedFlow, channels

Welcome to Week 05 of **C21 · Crunch Droid**. Last week your concurrency was *one-shot*: fetch this, compute that, return a value, done. The downloader you built ran to completion and handed back a list. But most of an Android app is not one-shot — it is *streams*. A location provider emits coordinates over time. A WebSocket pushes messages. A database table changes and you want the new rows. A UI has *state* that evolves and views that must re-render when it does. The tool for "values arriving over time, inside structured concurrency" is **Flow**, and this week is all of it.

The mental shift is from "a function returns a value" to "a `Flow` *emits* values, lazily, when someone collects it." A `Flow<T>` is a *cold* stream: it is a recipe, not a running process. Declaring a flow runs no code. The producer block executes only when a collector calls `collect`, and it runs *again, from scratch* for each new collector. That coldness is the first big idea, and the source of the week's central bug class: confusing a cold flow (re-runs per collector, no shared state) with a *hot* flow (`StateFlow`/`SharedFlow` — always live, shared across collectors, holding state). Most of the production Flow bugs you will ever debug are someone treating one as the other — collecting a cold flow twice and being surprised it did the work twice, or expecting a `SharedFlow` to replay the last value to a late subscriber when it was configured not to.

The operators are the second big idea. `map`, `filter`, `transform` are the easy ones. The ones that separate engineers are the **flat-mapping** operators — `flatMapConcat`, `flatMapMerge`, `flatMapLatest` — and `transformLatest`, because they decide what happens when a new upstream value arrives *while you are still processing the previous one*. `flatMapLatest` is the one you will reach for constantly on Android: a new search query arrives, *cancel* the in-flight request for the old query and start the new one. Picking the wrong flat-map is how you ship a search box that shows results for a query the user already deleted. We teach operator selection as a decision, not a guess.

The third idea is **hot flows as the bridge to UI**. `StateFlow` is a flow that always has a current value and emits it to every collector — it is the "state of the world" primitive that backs a Compose screen's `UiState` in Phase 2. `SharedFlow` is a flow for *events* — one-shot things like "show a snackbar," where replaying the last event to a new collector would be a bug (the snackbar would re-fire on rotation). `channelFlow` and `callbackFlow` are the adapters that turn a *callback-based* legacy API (a listener, a `LocationManager`, a `BroadcastReceiver`) into a Flow you can compose. And **Turbine** is how you test all of it deterministically — asserting on exact emissions instead of sleeping and hoping.

We close the week by building a **reactive ticker module**: a cold `Flow<Long>` of timestamps, a hot `StateFlow<PriceDelta>` of computed price changes, and a `SharedFlow<Alert>(replay = 0)` that fires when a delta crosses a threshold — all tested with Turbine. If you can build that, explain why the timestamps are cold and the deltas are hot, and assert on the alert emissions deterministically, you have the skill this week earns.

## Learning objectives

By the end of this week, you will be able to:

- **Explain** what makes a `Flow` *cold* — that it is a suspending producer recipe that runs from scratch per collector, with no value until collected — and predict the behaviour of collecting one cold flow multiple times.
- **Select** the right operator without guessing: `map`/`filter`/`transform` for shaping, and `flatMapConcat` vs `flatMapMerge` vs `flatMapLatest` (and `transformLatest`) by reasoning about what should happen to in-flight work when a new upstream value arrives.
- **Manage** backpressure with `buffer`, `conflate`, and `collectLatest`, and explain how a slow collector affects a fast producer for each.
- **Distinguish** cold `Flow` from hot `StateFlow` and `SharedFlow`, and choose `StateFlow` for state and `SharedFlow(replay = 0)` for one-shot events, articulating why replaying an event is a bug.
- **Configure** a `SharedFlow` deliberately — `replay`, `extraBufferCapacity`, `onBufferOverflow` — and a `StateFlow` via `MutableStateFlow`, and convert a cold flow to hot with `stateIn`/`shareIn` and the right `SharingStarted` policy.
- **Bridge** a callback-based API into a Flow with `callbackFlow`/`channelFlow`, registering and (critically) *unregistering* the listener with `awaitClose`.
- **Test** flows deterministically with Turbine — asserting exact emissions, completion, and errors with `test { }`, `awaitItem()`, `awaitComplete()` — and with `runTest` virtual time.
- **Recognise** the cold-vs-hot footguns: re-collecting a cold flow and re-doing work, a `SharedFlow` event replayed on rotation, a `callbackFlow` that leaks its listener, and an unbounded buffer that grows without limit.

## Prerequisites

This week assumes you have completed **C21 weeks 1–4**, or have equivalent fluency. Specifically:

- You understand structured concurrency, `CoroutineScope`, `Job`, and cooperative cancellation (Week 4). A `Flow` is *collected inside a coroutine*; collection is cancelled when its scope is cancelled; `flatMapLatest` works by *cancelling* the previous inner flow. Last week's cancellation discipline is load-bearing this week.
- You can pick a dispatcher and use `withContext` (Week 4). `flowOn` changes the dispatcher of the *upstream* of a flow, and the context-preservation rule that makes it safe is a direct consequence of last week.
- You model outcomes with sealed classes and `Result<T>` (Week 2). A flow of `UiState` (Loading/Success/Error) is a sealed type emitted over time.
- You are comfortable with the Gradle Kotlin DSL and `runTest` (Weeks 1, 4). This week adds `kotlinx-coroutines-core` (which contains Flow) and `app.cash.turbine:turbine` for tests.

**Toolchain.** Kotlin 2.x (K2), `kotlinx-coroutines` 1.9+, Turbine 1.x, JDK 21. Everything this week runs as **plain JVM Kotlin** — a Gradle library plus a `runTest`/Turbine test source set. We stay off Android for one more week, deliberately: `Flow`, `StateFlow`, `SharedFlow`, and channels are pure `kotlinx.coroutines`, not Android. Learning them without `viewModelScope`, `repeatOnLifecycle`, and `collectAsStateWithLifecycle` in the way keeps the cold/hot distinction sharp. Those Android collection patterns arrive in Phase 2 the moment you have the fundamentals.

## Topics covered

- **Cold flows.** The `Flow<T>` interface, `flow { emit(...) }` builders, `flowOf`/`asFlow`, laziness, per-collector re-execution, and the suspend-based producer/collector contract.
- **Shaping operators.** `map`, `filter`, `transform`, `onEach`, `take`/`drop`, `distinctUntilChanged`, `scan`/`runningReduce`, terminal operators (`collect`, `toList`, `first`, `single`, `reduce`, `fold`).
- **Flat-mapping.** `flatMapConcat` (sequential), `flatMapMerge` (concurrent, bounded), `flatMapLatest` (cancel-previous), `transformLatest`; choosing by in-flight semantics; the search-as-you-type case for `flatMapLatest`.
- **Backpressure and buffering.** The default rendezvous (suspend the producer), `buffer(capacity, onBufferOverflow)`, `conflate`, `collectLatest`; what each does to a fast producer and slow collector.
- **Context and threading.** `flowOn` (upstream dispatcher), context preservation (why you can't `emit` from a different context inside `flow {}`), and where collection runs.
- **Hot flows — `StateFlow`.** `MutableStateFlow`, the always-present `value`, conflation, equality-based de-duplication, why it's the "state of the world" primitive, `update { }`.
- **Hot flows — `SharedFlow`.** `MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow)`, `tryEmit` vs `emit`, `replay = 0` for one-shot events, and why replaying an event is a rotation bug.
- **Cold → hot.** `stateIn` and `shareIn`, the `SharingStarted` policies (`Eagerly`, `Lazily`, `WhileSubscribed(stopTimeoutMillis)`), and why `WhileSubscribed(5000)` is the Android default.
- **Channels.** `Channel<T>` as a hot, *not*-broadcast primitive; rendezvous/buffered/conflated/unlimited capacity; fan-out; `Channel` vs `SharedFlow` for events; when a channel is the right tool.
- **Bridging callbacks.** `callbackFlow` and `channelFlow`, `trySend`/`send`, registering a listener, and `awaitClose` to unregister it — the leak you must never ship.
- **Testing flows.** Turbine `test { }`, `awaitItem`, `awaitComplete`, `awaitError`, `expectNoEvents`, `cancelAndIgnoreRemainingEvents`; testing hot flows; `runTest` virtual time.
- **The cold-vs-hot footguns.** Re-collecting a cold flow and re-doing work; a `SharedFlow` event replayed on rotation; a leaked `callbackFlow` listener; an unbounded buffer.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                            | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | Cold flows; builders; shaping operators; terminal operators      |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | Flat-mapping; `flatMapLatest`; backpressure; `flowOn`            |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Hot flows: `StateFlow` & `SharedFlow`; cold→hot; `SharingStarted` |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | Channels; `callbackFlow` bridges; Turbine testing; challenge      |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — cold ticker + hot delta StateFlow scaffold         |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work; alert SharedFlow + Turbine tests          |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                       |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                  | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./00-overview.md) | This overview (you are here) |
| [resources.md](./01-resources.md) | The Flow guide, the StateFlow/SharedFlow design notes, the Android "things to know about Flow" series, Turbine docs, and primary sources |
| [lecture-notes/01-cold-flows-operators-and-backpressure.md](./02-lecture-notes/01-cold-flows-operators-and-backpressure.md) | Cold flows end to end: laziness, the operator catalogue, flat-mapping by in-flight semantics, backpressure, and `flowOn` |
| [lecture-notes/02-hot-flows-channels-and-callback-bridges.md](./02-lecture-notes/02-hot-flows-channels-and-callback-bridges.md) | `StateFlow` vs `SharedFlow`, cold→hot with `stateIn`/`shareIn` and `SharingStarted`, channels, `callbackFlow` bridges, and the cold-vs-hot footguns |
| [exercises/README.md](./03-exercises/00-overview.md) | Index of the three exercises |
| [exercises/exercise-01-cold-is-lazy-and-per-collector.md](./03-exercises/exercise-01-cold-is-lazy-and-per-collector.md) | Prove a cold flow runs nothing until collected and re-runs per collector; then make it hot and prove the difference |
| [exercises/exercise-02-flatmaplatest-search.kt](./03-exercises/exercise-02-flatmaplatest-search.kt) | Build search-as-you-type with `flatMapLatest`; prove that a new query cancels the in-flight request for the old one, with Turbine |
| [exercises/exercise-03-callbackflow-bridge.kt](./03-exercises/exercise-03-callbackflow-bridge.kt) | Bridge a callback listener into a Flow with `callbackFlow`; prove `awaitClose` unregisters it and no listener leaks |
| [challenges/README.md](./04-challenges/00-overview.md) | Index of the challenge |
| [challenges/challenge-01-event-replay-bug.md](./04-challenges/challenge-01-event-replay-bug.md) | Reproduce the "snackbar fires twice on rotation" event-replay bug, then fix it with `SharedFlow(replay = 0)` (and contrast a `Channel`), proven with Turbine |
| [quiz.md](./05-quiz.md) | 13 questions on cold/hot, operators, flat-mapping, backpressure, `SharingStarted`, channels, and bridges |
| [homework.md](./06-homework.md) | Six practice problems for the week |
| [mini-project/README.md](./07-mini-project/00-overview.md) | Full spec for the reactive ticker: cold timestamp flow, hot price-delta `StateFlow`, threshold-alert `SharedFlow`, all Turbine-tested |

## The "assert on emissions, never sleep" promise

Week 4 gave you "never leak a coroutine." Week 5 adds the contract a senior reviewer checks for streaming code:

> **Every flow assertion is deterministic.** You assert on the *exact sequence of emissions* with Turbine and `runTest` virtual time — `awaitItem()`, `awaitComplete()`, `expectNoEvents()` — never `Thread.sleep(100); assertEquals(...)`. A test that sleeps is a test that flakes; a flow test that flakes is a flow you do not understand. If you cannot assert the precise emissions a flow produces, you do not yet understand the flow.

You will *prove* this throughout: every exercise and the mini-project assert emission-by-emission with Turbine, and the `flatMapLatest` exercise asserts the *absence* of the cancelled query's result — a thing you can only test deterministically.

## A note on what's not here

Week 5 is the *Flow fundamentals* week. It deliberately does **not** cover:

- **Compose collection.** `collectAsStateWithLifecycle`, `collectAsState`, and the `repeatOnLifecycle` collection pattern are Phase 2, once you have Compose. This week you collect flows in plain coroutine scopes and Turbine, which is where the cold/hot distinction is clearest.
- **Room/Retrofit Flow integration.** Room returns `Flow<List<Entity>>` and Retrofit can return flows, but wiring those is Phase 3. This week's flows come from `flow { }`, `MutableStateFlow`, and `callbackFlow` so the *mechanism* is in view, not a library hiding it.
- **`RxJava` interop and migration.** Real teams have RxJava to migrate; `kotlinx-coroutines-rx3` bridges it. That is a footnote, not a topic — we mention it exists and move on.

The point of Week 5 is narrow and deep: one distinction (cold vs hot) that explains most Flow bugs, the operator-selection discipline (especially `flatMapLatest`), the two hot primitives (state vs events) and when each is right, the callback bridge done without leaking, and Turbine for deterministic assertions.

## Up next

Continue to **Week 06 — The Android runtime, ART, Gradle, AOSP-aware mental model** once you have shipped this week's mini-project and Turbine-tested every emission. Week 6 is the last foundations week: it puts your Kotlin-and-coroutines fluency onto an actual Android build — ART vs the desktop JVM, the lifecycle as history, version catalogs, build variants, and tracing `assembleDebug` end to end. Then Phase 2 begins, and every screen you build collects a `StateFlow<UiState>` exactly the way you learned this week. The Flow you master now is the data layer the entire rest of the track stands on.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
