# Week 05 — Resources

Every primary resource on this page is **free**. The Kotlin Flow guide and the `kotlinx.coroutines` reference are free and open. The Android Developers Flow series is free on Medium. Turbine is open source on GitHub. A paid book is listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **Kotlin Flow — official guide.** Read "Asynchronous Flow" end to end, then "Channels" and "Shared flows and state flows" before Wednesday:
  <https://kotlinlang.org/docs/flow.html>
- **"Shared flows, state flows."** The hot-flow reference — `StateFlow`, `SharedFlow`, `stateIn`/`shareIn`, `SharingStarted`. Central to lecture 02:
  <https://kotlinlang.org/docs/flow.html#stateflow-and-sharedflow>
- **"Channels."** The channel primitive, capacities, fan-out. Central to lecture 02:
  <https://kotlinlang.org/docs/channels.html>
- **"Flow context" and "Buffering."** `flowOn`, context preservation, `buffer`/`conflate`/`collectLatest` — central to lecture 01:
  <https://kotlinlang.org/docs/flow.html#flow-context>

## The Android Flow canon (why this matters for the platform)

The Android team's writing is the clearest treatment of *why* the cold/hot distinction matters for a UI, and it is exactly the Phase 2 payoff:

- **"Things to know about Flow's shareIn and stateIn operators" — Manuel Vivo.** The single best article on cold→hot conversion and `SharingStarted`. Read it twice:
  <https://medium.com/androiddevelopers/things-to-know-about-flows-sharein-and-statein-operators-20e6ccb2bc74>
- **"A safer way to collect flows from Android UIs" — Manuel Vivo.** Why `WhileSubscribed(5000)` and `repeatOnLifecycle` (the Phase 2 collection pattern):
  <https://medium.com/androiddevelopers/a-safer-way-to-collect-flows-from-android-uis-23080b1f8bda>
- **"Migrating from LiveData to Kotlin's Flow" — Jose Alcérreca.** The `StateFlow`-as-state, `SharedFlow`-as-event framing the mini-project uses:
  <https://medium.com/androiddevelopers/migrating-from-livedata-to-kotlins-flow-379292f419fb>

## The API reference (skim, don't memorize)

- **`Flow`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/>
- **`flow { }` builder:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/flow.html>
- **`StateFlow` / `MutableStateFlow`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/>
- **`SharedFlow` / `MutableSharedFlow`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-shared-flow/>
- **`stateIn` / `shareIn`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/state-in.html> and <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/share-in.html>
- **`SharingStarted`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-sharing-started/>
- **`flatMapLatest` / `flatMapMerge` / `flatMapConcat`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/flat-map-latest.html>
- **`callbackFlow` / `channelFlow`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/callback-flow.html> and <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/channel-flow.html>
- **`Channel`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.channels/-channel/>

## The design documents (the "why" underneath)

- **"Cold flows, hot channels" — Roman Elizarov.** The essay that names the distinction this whole week hangs on:
  <https://elizarov.medium.com/cold-flows-hot-channels-d74769805f9>
- **"Execution context of Kotlin Flows" — Roman Elizarov.** `flowOn` and context preservation explained by the designer:
  <https://elizarov.medium.com/execution-context-of-kotlin-flows-b8c151c9309b>
- **"Kotlin Flow design" (KEEP / reactive-streams notes).** Why Flow is sequential-by-default and how it relates to Reactive Streams:
  <https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/README.md#flow>

## Testing flows

- **Turbine — README and recipes.** The deterministic Flow-testing library. `test { }`, `awaitItem`, `awaitComplete`, `awaitError`, `expectNoEvents`. You use it in every exercise and the mini-project:
  <https://github.com/cashapp/turbine>
- **`kotlinx-coroutines-test` — `runTest` and virtual time.** Pairs with Turbine; virtual time makes timed flows deterministic:
  <https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-test/README.md>
- **"Testing Kotlin flows on Android" (Android Developers).** Turbine + `runTest` patterns and the `StandardTestDispatcher` story:
  <https://developer.android.com/kotlin/flow/test>

## Talks (free, watch in this order)

- **Roman Elizarov — "Kotlin Flow in practice" (KotlinConf).** The operator catalogue and cold/hot with live demos:
  <https://www.youtube.com/watch?v=fSB6_KE95bU>
- **Manuel Vivo — "Sharing flows in Android" (Android Dev Summit).** `shareIn`/`stateIn`/`SharingStarted` on a real app — the Phase 2 preview:
  <https://www.youtube.com/results?search_query=manuel+vivo+sharing+flows+android>

## Open-source code to read this week

You learn more from one hour reading real Flow code than three hours of tutorials. Pick one and trace how it splits cold producers from hot state:

- **`android/nowinandroid`** — the reference app. Search for `stateIn`, `WhileSubscribed`, `MutableStateFlow`, and `MutableSharedFlow`; the `ViewModel`s are a master class in `StateFlow<UiState>`:
  <https://github.com/android/nowinandroid>
- **`Kotlin/kotlinx.coroutines`** — read `Flow.kt`, `StateFlow.kt`, `SharedFlow.kt`, and `Channels.kt`. `SharedFlow.kt` in particular demystifies replay and buffer overflow:
  <https://github.com/Kotlin/kotlinx.coroutines>
- **`cashapp/turbine`** — small enough to read in full; seeing how it drives a flow under `runTest` makes flow testing click:
  <https://github.com/cashapp/turbine>

## Tools you'll use this week

- **Gradle Kotlin DSL.** Add `org.jetbrains.kotlinx:kotlinx-coroutines-core` (Flow lives here), `kotlinx-coroutines-test`, and `app.cash.turbine:turbine` (test) via `libs.versions.toml`.
- **`runTest` virtual time.** Timed flows (`delay`, `sample`, `debounce`) advance on the test scheduler, so tests are instant and deterministic. Never `Thread.sleep` in a flow test.
- **IntelliJ / Android Studio flow debugging.** Breakpoints inside `flow { }` and inside `collect { }` show the per-collector re-execution of a cold flow concretely — set one and collect twice.

## Free books (chapter-level, not whole books)

- **The official Flow guide (above) is effectively a free book** — "Asynchronous Flow," "Channels," and "Shared flows and state flows" read end to end are a complete primer with runnable examples.
- **Kodeco "Kotlin Coroutines by Tutorials" free chapters** — the Flow chapters cover operators and the cold/hot split.

## Paid books (optional, clearly marked)

- **"Kotlin Coroutines: Deep Dive" — Marcin Moskała** (paid). The Flow half of the book — cold/hot, the operator internals, `SharedFlow`/`StateFlow`, and testing — is the clearest long-form treatment in print and maps directly onto this week.

---

*If a link 404s, please open an issue so we can replace it.*
