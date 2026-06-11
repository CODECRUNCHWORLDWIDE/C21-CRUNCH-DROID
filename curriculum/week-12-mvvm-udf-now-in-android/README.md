# Week 12 — MVVM, UDF, the Now-In-Android pattern

Welcome to Week 12 of **C21 · Crunch Droid**, and the close of Phase 2. For two weeks your app has had a navigated skeleton (Week 10) and a themed skin (Week 11), but its data has lived wherever was convenient — an in-memory list here, a `remember` there, state scattered through the composables. This week the app grows a nervous system. By Friday it has a real architecture: a Jetpack `ViewModel` that holds UI state as a single `StateFlow<UiState>`, **unidirectional data flow** so events go up and state comes down, a data/domain/UI layer split modelled on Google's **Now-In-Android** reference app, and survival of process death through `SavedStateHandle`. The app stops being a demo and starts being a system you could ship and a teammate could read.

MVVM with unidirectional data flow (UDF) is the architecture Google recommends for Android, and Now-In-Android (often "NiA") is the canonical implementation everyone reads to learn it. The headline idea this week hammers on is one sentence: **state flows down, events flow up, and there is exactly one source of truth for each piece of screen state.** The `ViewModel` owns the screen's state; it exposes that state as an immutable `StateFlow<UiState>`; the composable *observes* the state and *renders* it, sending user actions back up to the `ViewModel` as plain function calls. The UI never holds the real state and never mutates it directly — it is a pure function of the state the `ViewModel` hands it. That single discipline — UI as `f(state)`, with one owner per piece of state — is what makes an Android app testable, survivable across configuration change and process death, and legible to the next engineer.

The mental shift this week is from "the composable owns and mutates state" to "**the `ViewModel` owns the state; the composable renders a snapshot of it and reports events.**" A `UiState` is best modelled as a *sealed* type — `Loading | Error | Success(data)` — so that "showing data while still loading" is not a representable state, the same make-illegal-states-unrepresentable move you met with typed routes in Week 10. The `ViewModel` produces that `UiState` by combining inputs (a repository's `Flow`, user actions, saved arguments) and exposes it via `StateFlow`, which the UI reads with `collectAsStateWithLifecycle()` so collection is tied to the screen's lifecycle and stops when the screen is backgrounded. And because the `ViewModel` survives configuration change but *not* process death, the small slice of state that must outlive a system-initiated kill — a search query, a selected tab, a scroll anchor — goes in `SavedStateHandle`, which is the `ViewModel`'s `Bundle`-backed key-value store that the system restores.

We close the week — and Phase 2 — by building **News Feed**, a two-screen app (a feed list and an article detail) with a `ViewModel`-driven `StateFlow<UiState>`, a Now-In-Android-shaped layer split (a `NewsRepository` in the data layer, a thin domain use case, a `ViewModel` in the UI layer), full process-death survival, and a tested `SavedStateHandle` round-trip. You will compare three architectures on Monday — MVI, MVVM-with-UDF, and pure Compose state — and articulate which one NiA picked and why. You will then draw the line, in your own app, between where Compose state ends and `ViewModel` state begins, because "I put the search query in a `remember` and it vanished on process death" is a real bug, and knowing exactly which state lives where is the skill this week earns.

## Learning objectives

By the end of this week, you will be able to:

- **Explain** unidirectional data flow — state down, events up, one source of truth per piece of state — and predict which bugs (lost state on rotation, duplicate sources of truth, untestable UI) the discipline eliminates.
- **Model** UI state as a sealed `UiState` type (`Loading | Error | Success`) so that contradictory states are unrepresentable, and justify why a sealed type beats a flat data class with `isLoading`/`error`/`data` flags.
- **Build** a Jetpack `ViewModel` that owns screen state, exposes it as an immutable `StateFlow<UiState>`, and turns user actions into state transitions via plain methods — never exposing a `MutableStateFlow` or mutating from the UI.
- **Combine** inputs into state with the Flow operators from Phase 1 (`map`, `combine`, `stateIn`) so the `ViewModel`'s `StateFlow` is derived from a repository `Flow` plus user actions, with the right `SharingStarted` policy and initial value.
- **Collect** state in Compose with `collectAsStateWithLifecycle()`, and explain why lifecycle-aware collection (vs. plain `collectAsState`) matters for stopping work when the screen is backgrounded.
- **Draw** the Now-In-Android layer boundaries — data (repository), domain (optional use cases), UI (ViewModel + composables) — and place a new feature's code in the right layer, reasoning about dependency direction (UI → domain → data, never the reverse).
- **Survive** process death with `SavedStateHandle`: store the small, identity-shaped slice of UI state that must outlive a system kill, restore it on recreation, and distinguish it from the larger derived state that the `ViewModel` recomputes.
- **Test** the architecture: unit-test the `ViewModel`'s `StateFlow<UiState>` transitions with Turbine and a fake repository, and assert a `SavedStateHandle` round-trip — without a device.

## Prerequisites

This week assumes you have completed **C21 weeks 1–11**, or have equivalent fluency. Specifically:

- You understand coroutines, structured concurrency, and `viewModelScope` — Week 4. The `ViewModel` runs its work in `viewModelScope`, cancelled automatically when the `ViewModel` is cleared; if scope lifetimes are fuzzy, leaks and cancelled-too-early bugs will be.
- You are fluent in cold vs. hot flows, `StateFlow`/`SharedFlow`, the operators, and Turbine — Week 5. This week's `StateFlow<UiState>` *is* a hot flow derived from a cold repository flow via `stateIn`; the cold/hot distinction is load-bearing, and Turbine is how you test it.
- You can write composables, hoist state, and reason about recomposition — Weeks 7–9. UDF is state hoisting taken to its conclusion: the state is hoisted all the way up to a `ViewModel`.
- You have **Catalog Companion** / **Pocket Reader** (Weeks 10–11) — a navigated, themed multi-screen app. This week gives *that* app an architecture: the screens and theme stay, the data layer underneath them gets drawn properly. The Nav3 ViewModel-scoped-to-an-entry seam from Week 10 is exactly where this plugs in.

**Toolchain.** Android Studio Ladybug (2024.2) or newer, AGP 8.5+, Kotlin 2.0+ with the Compose Compiler plugin, `compileSdk 35`, `minSdk 24`. `androidx.lifecycle:lifecycle-viewmodel-compose` (for `viewModel()`), `lifecycle-runtime-compose` (for `collectAsStateWithLifecycle`), and `lifecycle-viewmodel-savedstate` (for `SavedStateHandle`). `app.cash.turbine:turbine` and `org.jetbrains.kotlinx:kotlinx-coroutines-test` for testing the `StateFlow`. We do **not** use Hilt this week — dependency injection is Week 13; this week we construct dependencies by hand (a `ViewModel` factory) so the wiring is visible. Tests run on the JVM; the process-death demo runs on an emulator with "Don't keep activities" enabled.

## Topics covered

- **Three architectures, compared.** MVI (intents → reducer → state), MVVM-with-UDF (ViewModel exposes state, methods take actions), and pure Compose state (state in composables). What each optimizes for and which one Now-In-Android picked.
- **Unidirectional data flow.** State down, events up; one source of truth per piece of state; the UI as a pure function of state; why bidirectional binding and scattered mutation are the bugs UDF prevents.
- **`UiState` as a sealed type.** `sealed interface UiState { Loading; data class Error; data class Success }`; why a sealed type makes contradictory states unrepresentable; the flat-flags anti-pattern (`isLoading` + `error` + `data` that can all be set at once) and why it rots.
- **The Jetpack `ViewModel`.** Owns state, scoped to a lifecycle owner (or a Nav3 entry, Week 10), survives configuration change, cleared on real teardown; `viewModelScope`; exposing `StateFlow` not `MutableStateFlow`.
- **Deriving state with Flow.** `repository.flow.map { … }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)`; combining multiple inputs with `combine`; the `WhileSubscribed` timeout and why it exists.
- **Lifecycle-aware collection.** `collectAsStateWithLifecycle()` vs. `collectAsState()`; collecting only while the screen is at least `STARTED`; stopping upstream work when backgrounded.
- **The Now-In-Android layers.** Data layer (repositories over data sources), domain layer (use cases, optional), UI layer (ViewModel + composables); the dependency rule (UI → domain → data); a `Result`-style sealed wrapper for async outcomes.
- **`SavedStateHandle`.** The `ViewModel`'s saved-state store; what to put in it (small, identity-shaped UI state) vs. what to recompute; reading/writing it; getting a `StateFlow` from it (`getStateFlow`); navigation arguments arriving through it.
- **Process death vs. configuration change.** What survives each; why the `ViewModel` survives rotation but not a system kill; why `rememberSaveable` and `SavedStateHandle` are the two tools for the kill, at different layers.
- **Testing the architecture.** A fake repository, `runTest`, Turbine on the `StateFlow<UiState>`, asserting `Loading → Success` transitions; a `SavedStateHandle` round-trip test.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                                | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|----------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | Three architectures; UDF; `UiState` as a sealed type                  |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | The `ViewModel`; deriving `StateFlow<UiState>` from a repository Flow  |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | The Now-In-Android layers; the dependency rule; `Result` wrapping      |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | `SavedStateHandle`; process death vs. config change; challenge        |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — News Feed: ViewModel + StateFlow<UiState> + layers      |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work; process-death survival + tests                 |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                           |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                      | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./README.md) | This overview (you are here) |
| [resources.md](./resources.md) | Google's architecture guide, the Now-In-Android repo and its architecture docs, the `ViewModel`/`StateFlow`/`SavedStateHandle` references, and the testing guides |
| [lecture-notes/01-mvvm-udf-and-uistate.md](./lecture-notes/01-mvvm-udf-and-uistate.md) | The three architectures, unidirectional data flow, `UiState` as a sealed type, the `ViewModel` owning a `StateFlow<UiState>`, and lifecycle-aware collection |
| [lecture-notes/02-now-in-android-layers-savedstate-process-death.md](./lecture-notes/02-now-in-android-layers-savedstate-process-death.md) | The Now-In-Android layer split and dependency rule, deriving state with Flow, `SavedStateHandle`, process death vs. configuration change, and testing the architecture |
| [exercises/README.md](./exercises/README.md) | Index of the three exercises |
| [exercises/exercise-01-uistate-sealed-type.md](./exercises/exercise-01-uistate-sealed-type.md) | Model UI state as a sealed `UiState`, render it with an exhaustive `when`, and convert a flat-flags screen to it |
| [exercises/exercise-02-viewmodel-stateflow.kt](./exercises/exercise-02-viewmodel-stateflow.kt) | Build a `ViewModel` exposing `StateFlow<UiState>` derived from a fake repository; test the `Loading → Success/Error` transitions with Turbine |
| [exercises/exercise-03-savedstatehandle-roundtrip.kt](./exercises/exercise-03-savedstatehandle-roundtrip.kt) | Put a search query in `SavedStateHandle`, expose it as a `StateFlow`, and test the round-trip that survives process death |
| [challenges/README.md](./challenges/README.md) | Index of the challenge |
| [challenges/challenge-01-process-death-bug-then-fix.md](./challenges/challenge-01-process-death-bug-then-fix.md) | Plant a "lost on process death" bug (state in a `remember`), reproduce it with "Don't keep activities", and fix it by moving the right slice to `SavedStateHandle` — measured |
| [quiz.md](./quiz.md) | 13 questions on UDF, `UiState`, the `ViewModel`, `StateFlow` derivation, the NiA layers, `SavedStateHandle`, and process death |
| [homework.md](./homework.md) | Six practice problems for the week |
| [mini-project/README.md](./mini-project/README.md) | Full spec for "News Feed": ViewModel-driven `StateFlow<UiState>`, NiA layers, process-death survival, tested `SavedStateHandle` round-trip |

## The "survives process death" promise

Week 7 gave you "renders exactly once." Week 10 gave you "no string routes." Week 12 adds the architecture contract a senior reviewer actually checks:

> **There is one source of truth for each piece of screen state, the UI is a pure function of it, and the state the user created survives a system-initiated process death.** Set a search query, background the app, let the system kill it (or enable "Don't keep activities"), return — and the query, the selected article, the scroll anchor are all restored. If a relaunch from a system kill loses user-entered state, the architecture is broken, no matter how clean the `ViewModel` looks. And if a reviewer can find the UI mutating its own state directly, or two places that both claim to own the same state, the UDF is broken.

You will *prove* this by enabling "Don't keep activities" in developer options — which simulates the system killing your backgrounded process — and confirming the state restores. "It survived rotation" is not the test; rotation keeps the `ViewModel` alive. The test is surviving the *process* dying, which is exactly what `SavedStateHandle` is for.

## A note on what's not here

Week 12 is the *architecture* week. It deliberately does **not** cover:

- **Dependency injection.** We construct the `ViewModel` and its repository by hand (a factory) so the dependency graph is *visible*. Hilt — which wires this graph for you and explains the Dagger beneath — is Week 13. This week the point is the *shape* of the architecture, not the tool that assembles it.
- **Real persistence and networking.** The `NewsRepository` is backed by an in-memory data source with a simulated delay. Room (Week 14) and Retrofit/Ktor (Week 15) are the real data sources that slot in behind the same repository interface — which is exactly why we draw the repository boundary now.
- **Advanced MVI.** We compare MVI on Monday and pick MVVM-with-UDF (as NiA does). A full MVI framework (a reducer, a single intent channel, time-travel) is a valid choice we discuss but don't build; the trade-offs are the lecture, not the lab.

The point of Week 12 is narrow and deep: one screen's state owned by one `ViewModel`, modelled as a sealed `UiState`, derived from a repository `Flow`, collected lifecycle-aware, surviving process death via `SavedStateHandle`, laid out in the Now-In-Android layers — and tested without a device.

## Up next

This is the last week of Phase 2. You now have a navigated (Week 10), themed (Week 11), architected (this week) Compose app — a real system, not a demo. **Phase 3 (Production Engineering)** begins with **Week 13 — Dependency injection with Hilt**, which takes the `ViewModel`-and-repository graph you wired *by hand* this week and shows you how Hilt assembles it for you (and the Dagger graph beneath it). Then Week 14 puts Room behind the repository, Week 15 puts networking behind it, and the rest of the phase makes it production-grade — testing, WorkManager, performance. Every one of those weeks assumes you can draw the layer boundaries and own state in a `ViewModel`. The architecture you build this week is the chassis the entire production phase bolts onto. Earn it.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
