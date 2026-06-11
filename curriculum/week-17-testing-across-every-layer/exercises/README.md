# Week 17 — Exercises

Short, focused drills. Each one should take 30–55 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Pick the right test](exercise-01-pick-the-right-test.md)** — given eight behaviors to verify, choose the pyramid tier and the tool for each, justify the choice on cost vs. confidence, and name what you would *not* test. The whole point of lecture 1's pyramid, on paper, before you write a line. (~40 min)
2. **[Exercise 2 — ViewModel with Turbine + MockK](exercise-02-viewmodel-turbine-mockk.kt)** — test a `StateFlow<UiState>` ViewModel through loading → content → error, with a `MainDispatcherExtension`, a fake repository, Turbine assertions, and one MockK interaction verification. Deterministic, millisecond-fast, JVM-only. (~50 min)
3. **[Exercise 3 — Compose UI test + screenshot](exercise-03-compose-ui-and-screenshot.kt)** — write a Compose UI test that drives a checkout row (find by tag, click, assert), then a Roborazzi screenshot test that records a golden for the content and error states. Both run on the JVM. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Run the JVM tiers with `./gradlew :app:testDebugUnitTest` (or your module). The screenshot tier: `recordRoborazziDebug` once, then `verifyRoborazziDebug`. No emulator needed for exercises 2 and 3.
- The `.kt` exercises are written to drop into a Compose project's `test/` (and `main/`) source sets. Each file's header says which source set each piece belongs in.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A flaky test is a *failing* exercise this week — determinism is the grade.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-17` to compare.
