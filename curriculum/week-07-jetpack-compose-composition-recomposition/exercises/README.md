# Week 07 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Trace a recomposition scope](exercise-01-trace-recomposition-scope.md)** — instrument a screen with a recomposition counter, *predict* which scopes recompose when a single piece of state changes, then run it and confirm. The whole point of lecture 1, in one screen. (~40 min)
2. **[Exercise 2 — Stability and skippability](exercise-02-stability-and-skippability.kt)** — take a composable the Compose Compiler report marks **not skippable**, find the one unstable parameter, fix it (an immutable collection and a `val`), and assert the type's stability with a test. You produce a before/after from the report. (~50 min)
3. **[Exercise 3 — Defer a state read to the draw phase](exercise-03-defer-state-read-to-draw.kt)** — take an animation that recomposes every frame because it reads its value in composition, move the read into the draw phase, and prove composition no longer runs per frame with a recomposition counter. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Run it on the **Android emulator** (a Pixel 8 API 35 image is the reference). See the recomposition counters move. Read the Compose Compiler report.
- The `.kt` exercises are written to drop into the `app` module of a Compose project (the `Scratch` app from exercise 1) and/or run as a JVM unit test where noted. Each file's header says which.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A non-skippable hot composable is a bug this week — the Compiler report is the arbiter, not your intuition.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-07` to compare.
