# Week 19 — Exercises

Short, focused drills. Each one should take 30–55 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — What to share](./exercise-01-what-to-share.md)** — given a phone app's modules and classes, decide for each whether it belongs in `commonMain` (shared), an Android-only source set, or split via `expect`/`actual` — and justify the line you draw. The whole point of lecture 1's share/don't-share rule, on paper, before you set up a module. (~40 min)
2. **[Exercise 2 — `expect`/`actual` across platforms](./exercise-02-expect-actual.kt)** — declare an `expect` API in `commonMain` for a platform-specific concern (a UUID and a platform name) and provide `actual` Android *and* iOS implementations, keeping the common code platform-agnostic. (~50 min)
3. **[Exercise 3 — A Wear forecast screen](./exercise-03-wear-forecast-screen.kt)** — build a Compose for Wear OS screen: the Wear `Scaffold` with `TimeText`, a `ScalingLazyColumn` of forecast rows as `Chip`s, and Wear Material components — rendering the shared model on the wrist. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Exercise 2 needs a KMP module (the iOS target must *compile* — that's the proof). Compile with `./gradlew :shared:compileKotlinIosSimulatorArm64` (any OS; running a simulator needs macOS). Exercise 3 needs a **Wear OS emulator** (a round Wear API 34 image).
- The `.kt` exercises say in their header which source set (`commonMain`/`androidMain`/`iosMain`, or a Wear module) each piece belongs in.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A `commonMain` that only compiles for Android is a *failing* exercise this week — the iOS compile is the grade.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-19` to compare.
