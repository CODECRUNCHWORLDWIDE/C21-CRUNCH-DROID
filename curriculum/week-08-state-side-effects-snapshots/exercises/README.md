# Week 08 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Hoist state, then survive rotation](exercise-01-hoist-and-survive-rotation.md)** — take a stateful composable, hoist its state to make it stateless and reusable, then move the owner's state from `remember` to `rememberSaveable` and *prove* it survives rotation and process death. The whole point of lecture 1, in one screen. (~40 min)
2. **[Exercise 2 — Pick the right effect](exercise-02-pick-the-right-effect.kt)** — six small scenarios; for each, choose and implement the correct side-effect API, and fix a coroutine that fires on every recomposition. You produce the decision-table reasoning in comments. (~50 min)
3. **[Exercise 3 — `snapshotFlow` + debounce](exercise-03-snapshotflow-debounce.kt)** — bridge a snapshot-state query into a `Flow` with `snapshotFlow`, debounce it, drop duplicates, and assert the emissions with Turbine. The mini-project's core pipeline, isolated and tested. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Run the UI exercises on the **Android emulator** (a Pixel 8 API 35 image is the reference). Rotate it (`Ctrl+F11`) and force process death ("Don't keep activities") for exercise 1.
- The `.kt` exercises drop into your `app` module; exercise 3's test runs as a JVM unit test with Turbine and `kotlinx-coroutines-test`. Each file's header says which.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A coroutine that fires on every recomposition is a bug this week — the lifecycle is the arbiter, not "it seems to work."

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-08` to compare.
