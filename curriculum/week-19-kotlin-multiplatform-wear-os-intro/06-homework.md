# Week 19 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 19 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Android Studio Ladybug+, Kotlin 2.0+, the Kotlin Multiplatform plugin, Ktor 3.x, kotlinx-serialization/coroutines/datetime, and Compose for Wear OS. KMP problems must have a **green iOS compile** (the proof of portability); Wear problems run on a round Wear emulator. Every problem must build with **0 warnings**.

---

## Problem 1 — Draw the share line for your own app

**Problem statement.** Take an Android app you've built earlier in the course (the reader, the checkout feature, anything with a data layer). List its classes and, in `notes/share-line.md`, sort each into `commonMain` / Android-only / `expect`-`actual`, with a one-sentence justification using the "would the answer be identical on every device?" test.

**Acceptance criteria.**

- At least eight real classes sorted, each with a justification.
- At least one `expect`/`actual` candidate identified (a UUID, time zone, secure storage, etc.).
- A note on anything that *looks* shareable but isn't (a `java.*` dependency, a `Context`-coupled class).
- Committed.

**Hint.** Decisions/calculations/parses → `commonMain`. Anything touching the screen, hardware, or an OS service → platform. The in-between (UUID, time zone) → `expect`/`actual`. A `java.*` import is your tell it can't be plain `commonMain`.

**Estimated time.** 35 minutes.

---

## Problem 2 — A minimal KMP module that compiles for iOS

**Problem statement.** Set up a `:shared` KMP module with `androidTarget()` and an iOS target. Put a pure function (e.g. `celsiusToFahrenheit`) and a `@Serializable` data class in `commonMain`. Prove it compiles for iOS with `compileKotlinIosSimulatorArm64` and capture the green output.

**Acceptance criteria.**

- A `:shared` module with the multiplatform + serialization plugins, `commonMain`, and the iOS target.
- A pure function and a `@Serializable` class in `commonMain`, using only multiplatform APIs.
- `compileKotlinIosSimulatorArm64` succeeds; the output is captured in `notes/ios-compile.md`.
- 0 warnings. Committed.

**Hint.** `kotlin { androidTarget(); iosSimulatorArm64(); sourceSets { commonMain.dependencies { ... } } }`. The iOS compile works on any OS; only *running* a simulator needs macOS.

**Estimated time.** 45 minutes.

---

## Problem 3 — An `expect`/`actual` pair, two platforms

**Problem statement.** Add an `expect`/`actual` seam to your `:shared` module: `expect fun platformName(): String` in `commonMain`, with Android and iOS actuals. Use it in some shared code. Confirm both platforms compile.

**Acceptance criteria.**

- `expect fun platformName(): String` in `commonMain` (no body).
- `actual` in `androidMain` (using `Build.VERSION`) and `iosMain` (using `UIDevice`).
- The shared code *uses* `platformName()` somewhere (not decorative).
- Both `compileKotlinIosSimulatorArm64` and the Android compile succeed. 0 warnings. Committed.

**Hint.** Every `expect` needs an `actual` in *every* target. If only Android has it, the iOS compile fails (by design). `iosMain` can import `platform.UIKit.UIDevice` because it targets iOS.

**Estimated time.** 40 minutes.

---

## Problem 4 — A shared repository tested with `MockEngine`

**Problem statement.** Add a `WeatherRepository` interface and a `KtorWeatherRepository` to `commonMain`. Write a `commonTest` using Ktor's `MockEngine` that tests the success-parse path and the error path, with `runTest`. Confirm the test passes on the JVM (and on iOS if you're on macOS).

**Acceptance criteria.**

- The interface and Ktor implementation in `commonMain`, returning a typed `NetworkResult`.
- A `commonTest` with `MockEngine` covering a 200-parse and a 500-error.
- The test passes on the JVM (`testDebugUnitTest`); note whether you also ran it on iOS.
- 0 warnings. Committed.

**Hint.** `MockEngine { request -> respond(content, status, headers) }` is a multiplatform fake HTTP engine, so the test runs on every target — a stronger guarantee than a JVM-only test. Install `ContentNegotiation { json() }` on the client.

**Estimated time.** 50 minutes.

---

## Problem 5 — A Wear screen, the right way

**Problem statement.** Build a Compose for Wear OS screen (any content) using the Wear component set: a Wear `Scaffold` with `TimeText`, a `ScalingLazyColumn` of `Chip`s with keyed items, and a render-by-state `UiState`. Run it on a round Wear emulator and confirm `TimeText` curves along the top and items scale toward the edges.

**Acceptance criteria.**

- Imports from `androidx.wear.compose.*` (not phone Material).
- A Wear `Scaffold` + `TimeText`, a `ScalingLazyColumn` with keyed `Chip` items.
- Renders Loading/Content/Error states.
- Runs on a round Wear emulator; a screenshot in `notes/wear.md` showing `TimeText` and the scaling list.
- 0 warnings. Committed.

**Hint.** The #1 mistake is importing phone Material. Use `androidx.wear.compose.material3.*` and `androidx.wear.compose.foundation.lazy.ScalingLazyColumn`. `Scaffold(timeText = { TimeText() }) { ... }`.

**Estimated time.** 45 minutes.

---

## Problem 6 — One core, two states

**Problem statement.** Given a shared `WeatherForecast` (with full detail), write two mappers: one to a *phone* UI state (all the detail) and one to a *Wear* UI state (a glance-length subset — location + a few hours). In `notes/two-states.md`, explain why the watch state is smaller and where the mapping happens (the boundary).

**Acceptance criteria.**

- One shared `WeatherForecast` and two distinct UI states (phone-rich, Wear-glance).
- Two mapper functions, each at the platform boundary (in the platform code, not the shared core).
- `notes/two-states.md` explains the glance-length subtraction and the boundary discipline.
- 0 warnings. Committed.

**Hint.** The shared core stays UI-agnostic; each platform's mapper decides how much of the shared data its surface can afford. The phone keeps it all; the watch keeps the essentials (lecture 2, §8). The mapping lives platform-side, not in `commonMain`.

**Estimated time.** 35 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, the KMP code compiles for iOS / the Wear code uses the Wear component set, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor lapse (e.g. a slightly-decorative `expect`/`actual`, a Wear screen missing `TimeText`, the iOS compile run but not captured). |
| 3 | Works, but misses one criterion (e.g. `commonTest` covers only the success path, the share-line sort omits the `expect`/`actual` candidates, the mapping put in `commonMain` by mistake). |
| 2 | Compiles for Android but a core idea is wrong (a `java.*` dependency in `commonMain` so the iOS compile fails, phone Material on a Wear screen, the shared core knows about the UI). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for any JVM-only API (`java.*`, Retrofit, `java.time`) in `commonMain` that breaks the iOS compile; **−2** for phone Material components used on a Wear screen; **−1** for UI knowledge leaking into the shared core (a `Context`, a `ForecastUiState`, a localized display string in `commonMain`).

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — the share/don't-share line and the iOS-compile proof (problems 1, 2, 3, 4) and the Wear component set / glance-length discipline (problems 5, 6) — so re-run exercises 02 and 03 before resubmitting.
