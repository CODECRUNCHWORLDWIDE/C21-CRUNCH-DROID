# Week 13 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Annotate a hand-wired app for Hilt](./exercise-01-annotate-an-app-for-hilt.md)** — take an app that constructs its dependencies by hand (a `ServiceLocator`), annotate it for Hilt, delete the manual wiring, and prove the graph builds the same objects. The whole point of the week, in one exercise. (~40 min)
2. **[Exercise 2 — Modules, `@Binds`, scopes, qualifiers](./exercise-02-modules-binds-scopes-qualifiers.kt)** — write `@Provides` and `@Binds` modules, scope a singleton, and disambiguate two `OkHttpClient`s with a `@Qualifier`. Runs as a plain JVM test with a hand-built Dagger-style component — no emulator. (~50 min)
3. **[Exercise 3 — Assisted injection](./exercise-03-assisted-injection.kt)** — build an `@AssistedInject` type that needs an injected dependency *and* a runtime id, with an `@AssistedFactory`, and test that the factory threads the runtime value through. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Build with `./gradlew :app:assembleDebug` (exercise 1, on the emulator) or `./gradlew test` (exercises 2 and 3, plain JVM). See the output. Read the error if it failed — the DI errors *are* the lesson this week.
- The `.kt` exercises are written to drop into a JVM test source set (`src/test/kotlin`) using either real Hilt test infrastructure or a minimal hand-assembled graph; each file's header says which and why. They deliberately avoid an emulator so the focus stays on the graph, not the Android lifecycle.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A `MissingBinding` or `DuplicateBindings` is not a warning you suppress — it is a graph bug you fix.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-13` to compare.
