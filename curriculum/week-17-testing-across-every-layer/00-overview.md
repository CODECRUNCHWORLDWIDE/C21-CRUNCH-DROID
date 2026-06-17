# Week 17 — Testing across every layer

Welcome to Week 17 of **C21 · Crunch Droid**. You are deep in Phase 3 now. You have a Hilt-wired graph (Week 13), Room and DataStore persistence (Week 14), a networking stack (Week 15), and WorkManager-backed background work (Week 16). You have, in other words, an application with enough moving parts that "it ran on my emulator" stopped being a credible claim two weeks ago. This week you build the thing that makes a senior Android codebase trustworthy: a **test suite that runs on every layer**, fast where it can be and slow only where it must, and that fails loudly the moment a regression sneaks in.

The mental shift this week is from "tests prove my code works" to "tests are a design tool and a regression net, and the *kind* of test you write is a deliberate engineering choice with a cost." A unit test on a `ViewModel` runs in milliseconds on the JVM and tells you the state machine is correct. A Robolectric test runs the Android framework on the JVM and tells you the DAO's SQL is right without booting an emulator. A Compose UI test drives the composition and tells you the screen renders and reacts. A screenshot test catches the pixel-level regression no assertion would. An Espresso end-to-end test boots the whole app on a device and tells you the wiring holds. Each one buys you a different kind of confidence at a different price, and the senior skill is **picking the right one the first time** — not testing everything five ways, and not shipping a `ViewModel` with no test because "the UI test covers it" (it doesn't, and it's forty times slower).

The thing this week hammers on is the **testing pyramid for Android, and where Compose blurs it.** The classic pyramid — many small tests, fewer medium, very few large — still holds, but Compose moved the lines. A Compose UI test with `createComposeRule()` runs on the JVM via Robolectric *or* on a device, with the same API, so a test that used to be "large" (boot an emulator) can now be "medium" (run on the JVM in seconds). That is a gift, and it changes where you spend your test budget. We map JUnit 5 + Turbine + MockK to the small tier (pure logic, Flow assertions, mocked collaborators), Robolectric and JVM-side Compose tests to the medium tier (framework and UI without a device), and Espresso + on-device instrumentation to the large tier (the real thing, the smoke test you run before you tag a release). And we are explicit about **what not to test**: don't test the framework, don't test getters, don't assert on private implementation, and don't write a screenshot test for a screen whose layout is still in flux.

We close the week by building a `:feature-checkout` module tested at *every* layer at once — unit tests on the `ViewModel` with Turbine and MockK, Robolectric tests on the Room DAO, Compose UI tests on the checkout screen, Roborazzi screenshot tests on every Material 3 state (loading, content, error, empty), and one Espresso end-to-end smoke test that drives a cart to a confirmed order. By Sunday you have a module whose CI run is green, fast, and *honest* — a single red test points at a single broken layer, and you know which one before you open the file. That diagnostic precision — "the DAO test went red, not the ViewModel test, so the SQL changed" — is the senior instinct this week installs.

## Learning objectives

By the end of this week, you will be able to:

- **Map** each test type to the right pyramid tier and the right layer — JUnit 5 for logic, Turbine for Flow, MockK for collaborators, Robolectric for JVM-side Android, Compose UI test for screens, Roborazzi/Paparazzi for pixels, Espresso for end-to-end — and justify the choice on cost and confidence.
- **Write** a deterministic `ViewModel` test that drives a `StateFlow<UiState>` through its states with Turbine, controlling time with a `TestDispatcher` and `runTest`, and asserting every emission rather than only the final one.
- **Mock** collaborators idiomatically with MockK: `coEvery`/`coVerify` for suspend functions, `every`/`returns` for properties, relaxed mocks where the boilerplate isn't load-bearing, and `mockkStatic` only when you must.
- **Run** Android-framework code on the JVM with Robolectric — a Room DAO with an in-memory database, a `Context`-dependent class — and know when Robolectric is the right call versus a real instrumentation test.
- **Drive** a composable with `createComposeRule()`: find nodes by semantics (`onNodeWithText`, `onNodeWithContentDescription`, test tags), perform actions, assert state, and control the test clock with `mainClock`.
- **Catch** visual regressions with screenshot tests (Roborazzi on the JVM, Paparazzi as the alternative), recording a golden per Material 3 state and failing CI on a pixel diff.
- **Write** one honest Espresso end-to-end smoke test that exercises the real wiring through `HiltAndroidTest`, and know why you write *one*, not fifty.
- **Structure** test code: shared fakes in a test-fixtures module, a `MainDispatcherRule`, a fake-vs-mock decision you can defend, and the test-only module pattern that keeps test doubles out of `main`.

## Prerequisites

This week assumes you have completed **C21 weeks 1–16**, or have equivalent fluency. Specifically:

- You are fluent in coroutines and Flow — `StateFlow`, `SharedFlow`, cold vs. hot, cancellation — Weeks 4–5. Turbine and `runTest` are how you assert on the streams you learned to build; if "cold vs. hot" is fuzzy, re-read Week 5 before Tuesday.
- You can read a `ViewModel` driving a `StateFlow<UiState>` — Week 12. The unit-test tier this week is built on testing exactly that shape, so the `UiState` sealed type and the UDF loop should be second nature.
- You have a Hilt graph you can reason about — Week 13. The end-to-end test uses `@HiltAndroidTest` and a test component to swap real bindings for fakes; you need to know what a Hilt module and a binding are.
- You have a Room database with DAOs — Week 14. The Robolectric tier tests a real DAO against an in-memory Room database, so you need a schema and a `@Dao` to test.
- You can write a Compose screen — Weeks 7–11. The Compose UI test and screenshot tiers drive composables you already know how to build.

**Toolchain.** Android Studio Ladybug (2024.2) or newer, JDK 17, Kotlin 2.0+. JUnit 5 via the `de.mannodermaus.android-junit5` Gradle plugin (Android instrumentation still defaults to JUnit 4 — we cover the split). Turbine 1.x, MockK 1.13+, Robolectric 4.13+, Compose UI test from the Compose BOM, Roborazzi 1.x (JVM screenshot tests; Paparazzi 1.3+ as the alternative), Espresso 3.6+. `androidx.test` runner and rules. The `:feature-checkout` mini-project module runs its small and medium tiers on the JVM (no device); only the Espresso smoke test needs an emulator.

## Topics covered

- **The testing pyramid for Android.** Small/medium/large, what each tier costs (milliseconds / seconds / tens of seconds), what confidence each buys, and how Compose UI tests on the JVM moved the medium/large line. What *not* to test.
- **JUnit 5 on Android.** The `de.mannodermaus.android-junit5` plugin, `@Test`/`@BeforeEach`/`@AfterEach`, `@ParameterizedTest`, `@Nested`, `assertThrows`. Why instrumentation tests still run JUnit 4 and how to live with the split.
- **Coroutine and Flow testing.** `kotlinx-coroutines-test`: `runTest`, `TestScope`, `StandardTestDispatcher` vs. `UnconfinedTestDispatcher`, `advanceUntilIdle`, `advanceTimeBy`. The `MainDispatcherRule` to swap `Dispatchers.Main`. Turbine — `test { }`, `awaitItem()`, `awaitComplete()`, `expectNoEvents()`, `turbineScope` for multiple flows.
- **MockK.** `mockk()`, `mockk(relaxed = true)`, `every { } returns`, `coEvery { } returns` for suspend, `verify`/`coVerify`, `slot` for argument capture, `mockkStatic`/`mockkObject`, and *fakes vs. mocks* — when to hand-write a fake instead.
- **Robolectric.** Running the Android framework on the JVM, `@RunWith(RobolectricTestRunner::class)` (or the JUnit 5 extension), an in-memory Room DAO test, shadow objects, and `@Config`. When Robolectric earns its keep and when a real instrumentation test is honest instead.
- **Compose UI testing.** `createComposeRule()` vs. `createAndroidComposeRule()`, the semantics tree, finders (`onNodeWithText`, `onNodeWithTag`, `onNodeWithContentDescription`), actions (`performClick`, `performTextInput`), assertions (`assertIsDisplayed`, `assertTextEquals`), `mainClock` for animation control, and running Compose tests on the JVM via Robolectric.
- **Screenshot testing.** Roborazzi (`captureRoboImage`, JVM, no device) and Paparazzi (`@get:Rule val paparazzi`, JVM, no device) — recording a golden, comparing, failing on a diff. One golden per Material 3 state. Why screenshot tests are powerful and where they're a maintenance trap.
- **Espresso and end-to-end.** `ActivityScenarioRule`, `onView`/`withId`/`perform`/`check`, Espresso-Compose interop, `IdlingResource` for async, and `@HiltAndroidTest` with `HiltAndroidRule` to inject fakes into the real app for one honest smoke test.
- **Test structure and fixtures.** The test-only module / `testFixtures` source set, shared fakes, a `TestData` object, the `MainDispatcherRule`, and keeping test doubles out of `main`. Flaky-test hygiene: no `Thread.sleep`, no real network, no real clock.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                                | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|----------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | The pyramid; JUnit 5 on Android; unit-testing the ViewModel          |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | Coroutine/Flow testing — runTest, Turbine; MockK                     |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Robolectric; the Room DAO test; Compose UI testing                  |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | Screenshot tests (Roborazzi); Espresso; the end-to-end smoke; challenge |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — `:feature-checkout`; the small + medium tiers         |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project — screenshot goldens + the Espresso smoke; green CI     |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                           |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                      | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./00-overview.md) | This overview (you are here) |
| [resources.md](./01-resources.md) | The testing docs, JUnit 5/Turbine/MockK/Robolectric/Roborazzi/Espresso guides, the Now-In-Android test suite, and the canonical talks |
| [lecture-notes/01-the-testing-pyramid-unit-flow-mockk.md](./02-lecture-notes/01-the-testing-pyramid-unit-flow-mockk.md) | The pyramid end to end; JUnit 5 on Android; testing a `StateFlow<UiState>` ViewModel with `runTest`, Turbine, and MockK; fakes vs. mocks |
| [lecture-notes/02-robolectric-compose-ui-screenshot-espresso.md](./02-lecture-notes/02-robolectric-compose-ui-screenshot-espresso.md) | Robolectric and the JVM-side DAO test; Compose UI testing with `createComposeRule`; Roborazzi screenshot tests; Espresso + Hilt end-to-end; test structure |
| [exercises/README.md](./03-exercises/00-overview.md) | Index of the three exercises |
| [exercises/exercise-01-pick-the-right-test.md](./03-exercises/exercise-01-pick-the-right-test.md) | Given eight behaviors to verify, choose the test tier and tool for each and justify the cost/confidence trade-off |
| [exercises/exercise-02-viewmodel-turbine-mockk.kt](./03-exercises/exercise-02-viewmodel-turbine-mockk.kt) | Test a `StateFlow<UiState>` ViewModel with Turbine + MockK + a `MainDispatcherRule`; drive loading → content → error deterministically |
| [exercises/exercise-03-compose-ui-and-screenshot.kt](./03-exercises/exercise-03-compose-ui-and-screenshot.kt) | Write a Compose UI test that drives a checkout row, then a Roborazzi screenshot test that records a golden per Material 3 state |
| [challenges/README.md](./04-challenges/00-overview.md) | Index of the challenge |
| [challenges/challenge-01-flaky-test-autopsy.md](./04-challenges/challenge-01-flaky-test-autopsy.md) | Inherit a flaky test suite, diagnose each flake (real clock, real dispatcher, shared state, ordering), fix it deterministically, and document the autopsy |
| [quiz.md](./05-quiz.md) | 13 questions on the pyramid, Flow testing, MockK, Robolectric, Compose UI test, screenshots, and Espresso |
| [homework.md](./06-homework.md) | Six practice problems for the week |
| [mini-project/README.md](./07-mini-project/00-overview.md) | Full spec for the `:feature-checkout` module tested at every layer — unit, Robolectric, Compose UI, Roborazzi, Espresso — green on CI |

## The "one red test, one broken layer" promise

Every week of Crunch Droid adds one production-grade contract a senior reviewer actually checks. This week's:

> **A test failure must point at exactly one layer, and the suite must be deterministic enough to run a thousand times with the same result.** If a ViewModel change turns the DAO test red, your tests are coupled to the wrong layer. If a test passes on your machine and fails on CI, it has a real clock, a real dispatcher, a real network call, or shared mutable state — and it is worse than no test, because it teaches the team to ignore red. Control time with `TestDispatcher`, isolate collaborators with fakes or mocks, run the small and medium tiers on the JVM, and make every failure legible.

You will *prove* this in the mini-project: the `:feature-checkout` suite is layered so the ViewModel test, the DAO test, the Compose UI test, the screenshot test, and the Espresso smoke each break independently. Introduce a SQL bug and only the DAO test goes red. Introduce a state-machine bug and only the ViewModel test goes red. Change a color and only the screenshot test goes red. That is what a real test suite buys you: not "the tests pass," but "the one red test tells me where to look."

## A note on what's not here

Week 17 is the *testing* week. It deliberately does **not** cover:

- **Performance testing.** Macrobenchmark, Baseline Profiles, and R8 are **Week 18** — that's a different kind of "test" (measuring, not asserting), and it gets its own week. This week's screenshot tests catch *visual* regressions, not *speed* regressions.
- **CI/CD wiring.** Running this suite on GitHub Actions with a build matrix, caching, and signed artifacts is **Week 21**. This week you run the suite locally and reason about determinism; Week 21 makes it run on every push.
- **Architecture.** We test the MVVM-with-UDF shape from Week 12 and the Hilt graph from Week 13; we don't redesign them. The `:feature-checkout` module reuses patterns you already own.
- **Security and integrity testing.** Testing Play Integrity and Keystore flows is **Week 22**. This week's `@HiltAndroidTest` swaps a fake repository, not a fake attestation provider.

The point of Week 17 is one suite, layered correctly: the right test at the right tier, deterministic, and legible when it fails.

## Up next

Continue to **Week 18 — Performance: macrobenchmark, Baseline Profiles, R8** once you have shipped this week's mini-project and your `:feature-checkout` suite is green and layered. Week 18 swaps assertions for *measurements*: you'll profile the cold-start path with the Macrobenchmark library, generate a Baseline Profile that warms the hot code paths before the user's first frame, and tune R8 keep rules without disabling R8 in frustration. The discipline carries straight over — "measure, don't guess," the same instinct your recomposition counters and your Compiler report taught in Week 7 — but now the number you watch is a millisecond of cold start, not a count of recompositions. A flaky test and a noisy benchmark are the same disease; you'll cure both with the same medicine: control the inputs.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
