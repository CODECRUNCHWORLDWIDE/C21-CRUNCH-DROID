# Week 12 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — `UiState` as a sealed type](./exercise-01-uistate-sealed-type.md)** — model UI state as a sealed `Loading | Error | Success` type, render it with an exhaustive `when`, and convert a flat-flags (`isLoading` + `error` + `data`) screen to it. The whole point of the week's *state shape*, in one exercise: make contradictory states unrepresentable. (~40 min)
2. **[Exercise 2 — A ViewModel with a tested `StateFlow<UiState>`](./exercise-02-viewmodel-stateflow.kt)** — build a `ViewModel` that derives `StateFlow<UiState>` from a fake repository `Flow` via `map`/`stateIn`, and test the `Loading → Success` and error transitions with Turbine and `runTest`. (~50 min)
3. **[Exercise 3 — A `SavedStateHandle` round-trip](./exercise-03-savedstatehandle-roundtrip.kt)** — put a search query in `SavedStateHandle`, expose it as a `StateFlow`, derive results from it, and test the round-trip that proves the query survives process death while the results recompute. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- The `.kt` exercises are **JVM tests** — `runTest`, Turbine, a fake repository. They need no emulator and run in milliseconds. That speed is the architecture's payoff; feel it.
- The process-death *demo* (exercise 3's premise) is verified on an emulator with "Don't keep activities" on, but the *test* runs on the JVM by recreating the `ViewModel` from the same `SavedStateHandle`.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A flat-flags `UiState`, an exposed `MutableStateFlow`, or saving derived data are this week's hidden bugs.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-12` to compare.
