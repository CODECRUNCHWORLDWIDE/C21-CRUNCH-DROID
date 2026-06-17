# Week 12 — Resources

Every primary resource on this page is **free**. Android's developer documentation is free without any membership. Now-In-Android is a public, open-source Google sample. The coroutines and testing libraries are open source. A handful of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"Guide to app architecture."** Google's recommended architecture: UI layer, domain layer, data layer, UDF. The source of truth for this week:
  <https://developer.android.com/topic/architecture>
- **"UI layer" and "UI state production."** State down / events up, `UiState`, the `ViewModel`'s `StateFlow`:
  <https://developer.android.com/topic/architecture/ui-layer>
  <https://developer.android.com/topic/architecture/ui-layer/state-production>
- **"Data layer."** Repositories, data sources, the single-source-of-truth principle:
  <https://developer.android.com/topic/architecture/data-layer>
- **"Save UI state — `SavedStateHandle` and `rememberSaveable`."** What survives config change vs. process death, and the two tools:
  <https://developer.android.com/topic/libraries/architecture/saving-states>
- **Now in Android — the architecture learning journey.** The canonical NiA architecture doc; read it alongside the code:
  <https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md>

## The APIs (reference, skim don't memorize)

- **`ViewModel`:** <https://developer.android.com/reference/kotlin/androidx/lifecycle/ViewModel>
- **`viewModel()` (Compose):** <https://developer.android.com/reference/kotlin/androidx/lifecycle/viewmodel/compose/package-summary>
- **`StateFlow` / `MutableStateFlow`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/>
- **`stateIn` and `SharingStarted`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/state-in.html>
- **`collectAsStateWithLifecycle`:** <https://developer.android.com/reference/kotlin/androidx/lifecycle/compose/package-summary#(kotlinx.coroutines.flow.StateFlow).collectAsStateWithLifecycle(androidx.lifecycle.Lifecycle,androidx.lifecycle.Lifecycle.State,kotlin.coroutines.CoroutineContext)>
- **`SavedStateHandle`:** <https://developer.android.com/reference/kotlin/androidx/lifecycle/SavedStateHandle>
- **`viewModelScope`:** <https://developer.android.com/topic/libraries/architecture/coroutines#viewmodelscope>

## Now in Android (read this app)

NiA is the reference implementation of everything this week teaches. You learn more from an hour in its source than from a day of tutorials.

- **The repository:** <https://github.com/android/nowinandroid>
- **Architecture learning journey** (read first): <https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md>
- **Modularization learning journey** (how the layers become modules — Week 13+ relevant): <https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md>
- **A `ViewModel` to read closely** — e.g. `ForYouViewModel`: how it `combine`s flows into a `StateFlow<UiState>` and exposes events as methods.
- **A sealed `UiState`** — e.g. the `NewsFeedUiState` / `ForYouUiState` sealed hierarchies: `Loading` and `Success(feed)` as distinct states.

## Talks and sessions (free)

- **"Architecting your Compose UI"** (Android Developers) — UDF in Compose, state hoisting to the ViewModel:
  <https://www.youtube.com/c/AndroidDevelopers>
- **"Now in Android: the app" series** — the team walking their own architecture decisions:
  <https://www.youtube.com/c/AndroidDevelopers>
- **"Migrate to a single source of truth"** (Google I/O) — the data-layer single-source-of-truth principle:
  <https://io.google/>
- **"Kotlin Flows in practice"** — `stateIn`, `SharingStarted.WhileSubscribed`, and why the timeout:
  <https://www.youtube.com/c/AndroidDevelopers>

## Unidirectional data flow and state

- **"State and Jetpack Compose"** — state hoisting, the UDF foundation in Compose:
  <https://developer.android.com/develop/ui/compose/state>
- **"State holders and UI state"** — where state lives, ViewModel vs. plain state holder:
  <https://developer.android.com/topic/architecture/ui-layer/stateholders>
- **"Use Kotlin Flow with a ViewModel"** — the `stateIn` pattern this week's exercise 2 builds:
  <https://developer.android.com/kotlin/flow/stateflow-and-sharedflow>

## Testing the architecture

- **"Test your ViewModel / business logic"** — `runTest`, fakes, the structure of a ViewModel test:
  <https://developer.android.com/training/testing/local-tests>
- **Turbine** — the Flow-testing library; assert `Loading` then `Success` deterministically:
  <https://github.com/cashapp/turbine>
- **`kotlinx-coroutines-test`** — `runTest`, `TestDispatcher`, `StandardTestDispatcher`:
  <https://github.com/Kotlin/kotlinx.coroutines/tree/master/kotlinx-coroutines-test>
- **NiA's testing approach** — fakes over mocks, test doubles for repositories:
  <https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md#testing>

## Community writing (current, opinionated, correct)

- **Android Developers Medium — the architecture series:** <https://medium.com/androiddevelopers>
- **Manuel Vivo — ViewModel, state, and coroutines articles.** Among the clearest on `stateIn` and `SharingStarted`:
  <https://manuelvivo.dev/>
- **Chris Banes — Compose state and architecture posts:** <https://chrisbanes.me/>
- **Zsmb (Márton Braun) — sealed UI state and Kotlin idioms:** <https://zsmb.co/>

## Open-source projects to read this week

- **`android/nowinandroid`** — the whole point; read a feature ViewModel and its `UiState` end to end:
  <https://github.com/android/nowinandroid>
- **`android/architecture-samples`** — the older but still instructive architecture sample set, including a TODO app in several styles:
  <https://github.com/android/architecture-samples>
- **`android/compose-samples`** (JetNews) — a smaller MVVM-with-UDF news app, close in shape to this week's mini-project:
  <https://github.com/android/compose-samples>

## Tools you'll use this week

- **"Don't keep activities"** (Settings ▸ Developer options) — simulates the system killing your backgrounded process. The only honest way to test process-death survival. Turn it on, background, return.
- **`adb shell am kill <package>`** — kill your app's process from the command line to reproduce process death scriptably.
- **Layout Inspector / recomposition counts** — confirm the UI re-renders from `UiState`, not from its own held state.
- **The Turbine + `runTest` JVM test runner** — your ViewModel and SavedStateHandle tests run here, fast, no emulator.

## Free resources (chapter-level, not whole books)

- **Android's "Architecture" pathway and the "ViewModel and StateFlow" codelab** on `developer.android.com/courses` are effectively a free book; the codelab builds the exact `stateIn` pattern.
- **The NiA architecture and modularization journeys** (linked above) are a free, deeply-worked case study.

## Paid books (optional, clearly marked)

- **"Jetpack Compose by Tutorials" — Kodeco** (paid). The architecture chapters walk MVVM-with-UDF in a structured way if you prefer a book.
- **"Kotlin Coroutines: Deep Dive" — Marcin Moskała** (paid). Not architecture per se, but the `StateFlow`/`stateIn`/`SharingStarted` chapters are the clearest print explanation of the machinery under this week's ViewModel.

---

*If a link 404s, please open an issue so we can replace it.*
