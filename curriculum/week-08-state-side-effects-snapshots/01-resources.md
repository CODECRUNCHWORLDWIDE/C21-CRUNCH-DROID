# Week 08 — Resources

Every primary resource on this page is **free**. Android's developer documentation is free. The AndroidX source is public on Android Code Search. The talks are free on YouTube. A couple of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"State and Jetpack Compose."** The canonical state article — `mutableStateOf`, `remember`, `rememberSaveable`, state hoisting, single source of truth. Read it end to end this week (last week you read only the first half):
  <https://developer.android.com/develop/ui/compose/state>
- **"Side-effects in Compose."** The reference for every API this week is built on — `LaunchedEffect`, `rememberCoroutineScope`, `rememberUpdatedState`, `DisposableEffect`, `SideEffect`, `produceState`, `derivedStateOf`, `snapshotFlow`. This is the single most important page of the week:
  <https://developer.android.com/develop/ui/compose/side-effects>
- **"State hoisting."** The UDF pattern — stateful vs stateless composables, the `value`/`onValueChange` contract:
  <https://developer.android.com/develop/ui/compose/state-hoisting>
- **"Save UI state in Compose."** `rememberSaveable`, custom `Saver`s, and the relationship to saved instance state and `ViewModel`:
  <https://developer.android.com/develop/ui/compose/state-saving>
- **"Lifecycle of composables."** Re-read for the enter/recompose/leave hooks the side-effect APIs key into:
  <https://developer.android.com/develop/ui/compose/lifecycle>

## The snapshot system (the model under the state)

You never write `Snapshot` code, but understanding the model demystifies the whole week.

- **`androidx.compose.runtime.snapshots`** on Android Code Search — `Snapshot`, `MutableSnapshot`, `SnapshotStateList`, the MVCC machinery:
  <https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/snapshots/>
- **`SnapshotState.kt`** — `mutableStateOf`, `MutableState`, the `SnapshotMutationPolicy` (structural/referential/never-equal):
  <https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/SnapshotState.kt>
- **Zach Klippenstein — "Introduction to the Compose Snapshot system."** The clearest long-form explanation of the transactional state model outside the source:
  <https://blog.zachklipp.com/introduction-to-the-compose-snapshot-system/>
- **"Snapshots: the magic powering Compose state."** A community deep dive on read-tracking and apply notifications; search for the current write-ups, as several engineers have covered it well.

## `derivedStateOf`, `snapshotFlow`, and the Flow bridge

- **"When to use derivedStateOf"** (Android Developers blog) — the canonical "is this the right tool" guide, with the scroll-to-top-button example:
  <https://medium.com/androiddevelopers/jetpack-compose-when-should-i-use-derivedstateof-63ce7954c11b>
- **`snapshotFlow` reference** (in the side-effects page above) — converting snapshot reads to a cold Flow.
- **"Kotlin Flow" + Compose `collectAsStateWithLifecycle`** — the lifecycle-aware collection of a `StateFlow` into Compose state:
  <https://developer.android.com/develop/ui/compose/state#use-flow-with-compose>
- **`androidx.lifecycle:lifecycle-runtime-compose`** — provides `collectAsStateWithLifecycle`; you'll use it lightly this week and heavily in Week 12.

## Talks (free, watch in this order)

- **"State holders and state production in the UI Layer"** (Google I/O / ADS) — where state lives and the production pipeline:
  <https://www.youtube.com/watch?v=pCX9wvu-Bq0>
- **"Effective state management for TextField in Compose"** — the subtleties of text-field state, debounce, and the snapshot interactions you hit in the mini-project:
  <https://www.youtube.com/watch?v=6_wK_Ud8--0>
- **"Compose: side effects and beyond"** — the side-effect APIs and their lifecycle keying, in motion.
- **Zach Klippenstein's snapshot talk(s)** — if you want the runtime-level model from an Android team engineer; search his name plus "snapshot."

## Coroutines + Compose (the Week 4/5 carry-over)

- **"Coroutines and Compose"** section of the side-effects page — how `LaunchedEffect` and `rememberCoroutineScope` relate to structured concurrency:
  <https://developer.android.com/develop/ui/compose/kotlin#coroutines>
- **Turbine** — the Flow-testing library from Week 5; you'll use it to assert `snapshotFlow` emissions in exercise 3:
  <https://github.com/cashapp/turbine>
- **`kotlinx.coroutines` Flow operators** — `debounce`, `distinctUntilChanged`, `flatMapLatest`, `mapLatest`; the operators the mini-project leans on:
  <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/>

## Community writing (current, opinionated, correct)

- **Zach Klippenstein's blog** — the deepest independent writing on Compose state, snapshots, and effects:
  <https://blog.zachklipp.com/>
- **Manuel Vivo's blog** — clear, production-focused articles on state, `ViewModel`, and the UI layer (former Android DevRel):
  <https://manuelvivo.dev/>
- **Chris Banes' blog** — practical Compose, including state and TextField subtleties:
  <https://chrisbanes.me/>
- **Android Developers Medium** — the official long-form; the "Compose state" and "side effects" series:
  <https://medium.com/androiddevelopers>

## Open-source projects to read this week

- **`android/nowinandroid`** — read how feature screens hold state and where side effects live (the `LaunchedEffect`/`collectAsStateWithLifecycle` patterns). The architecture reference for the whole track:
  <https://github.com/android/nowinandroid>
- **`android/compose-samples`** — Jetchat's text input and Crane's search are good `snapshotFlow`/effect references:
  <https://github.com/android/compose-samples>
- **`chrisbanes/tivi`** — a large real app; read its search and the `rememberSaveable`/effect usage:
  <https://github.com/chrisbanes/tivi>

## Tools you'll use this week

- **Android Studio Ladybug (2024.2)+** — and the emulator's rotate control (`Ctrl+F11`, or the rotate buttons on the emulator toolbar) for the rotation test.
- **Developer Options ▸ "Don't keep activities"** — forces an Activity to be destroyed on background, simulating process death cheaply; the harshest `rememberSaveable` test short of `adb shell am kill`.
- **`adb shell am kill <package>`** — kills your app's process so you can relaunch and verify `rememberSaveable` survived process death (after backgrounding the app).
- **The Layout Inspector** — from Week 7, still useful for watching which composables recompose when state changes.

## Free books (chapter-level, not whole books)

- **Android's "State in Jetpack Compose" codelab** — a guided, free walk through `remember`, `rememberSaveable`, and hoisting:
  <https://developer.android.com/codelabs/jetpack-compose-state>
- **"Jetpack Compose internals" (Jorge Castillo)** — the free early chapters cover the snapshot system and effects at the runtime level:
  <https://jorgecastillo.dev/book/>

## Paid books (optional, clearly marked)

- **"Jetpack Compose internals" — Jorge Castillo** (paid for the full book). The definitive runtime-level treatment of snapshots and effects; worth it if you debug Compose for a living.
- **"Programming Android with Kotlin" — O'Reilly** (paid). The coroutines + Compose chapters complement this week.

---

*If a link 404s, please open an issue so we can replace it.*
