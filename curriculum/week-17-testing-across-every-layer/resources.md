# Week 17 — Resources

Every primary resource on this page is **free**. Android's testing documentation is free. The testing libraries — JUnit 5, Turbine, MockK, Robolectric, Roborazzi, Paparazzi, Espresso — are all open source. The conference talks are free on YouTube. A couple of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"Test apps on Android" — the testing fundamentals.** The framing document for the whole pyramid: what to test, the small/medium/large taxonomy, and where instrumentation begins. Read this before you write a single test:
  <https://developer.android.com/training/testing/fundamentals>
- **"What to test" / the testing strategies guide.** The official guidance on the pyramid shape, fidelity vs. speed, and where to spend your budget:
  <https://developer.android.com/training/testing/fundamentals/strategies>
- **"Test your Compose layout."** The canonical Compose UI test article — `createComposeRule`, the semantics tree, finders, actions, the test clock:
  <https://developer.android.com/develop/ui/compose/testing>
- **"Test coroutines on Android."** `runTest`, `TestDispatcher`, `advanceUntilIdle`, swapping `Dispatchers.Main` — the deterministic-time half of every ViewModel test:
  <https://developer.android.com/kotlin/coroutines/test>
- **"Test your database" (Room testing).** Testing a DAO with an in-memory database, on the JVM via Robolectric or on a device:
  <https://developer.android.com/training/data-storage/room/testing-db>

## The libraries, at the source

- **Turbine** — the Flow-testing library from Cash App. The README is the whole API: `test { }`, `awaitItem`, `awaitComplete`, `turbineScope`:
  <https://github.com/cashapp/turbine>
- **MockK** — Kotlin-native mocking. The guide covers `coEvery`/`coVerify`, relaxed mocks, slots, `mockkStatic`:
  <https://mockk.io/>
- **Robolectric** — the Android framework on the JVM. Read "Getting Started" and the section on `@Config` and shadows:
  <https://robolectric.org/>
- **Roborazzi** — JVM screenshot testing (Compose and View), the modern no-device option. The README shows `captureRoboImage` and the compare/record/verify tasks:
  <https://github.com/takahirom/roborazzi>
- **Paparazzi** — Cash App's JVM screenshot library (the alternative to Roborazzi). The README shows the `@get:Rule val paparazzi` pattern:
  <https://github.com/cashapp/paparazzi>
- **JUnit 5 for Android** (`de.mannodermaus.android-junit5`) — the Gradle plugin that brings JUnit 5 to the JVM test source set, plus the JUnit 5 user guide:
  <https://github.com/mannodermaus/android-junit5> · <https://junit.org/junit5/docs/current/user-guide/>
- **Espresso** — the instrumentation UI-testing framework, plus Espresso-Compose interop:
  <https://developer.android.com/training/testing/espresso>

## Hilt and instrumentation testing

- **"Hilt testing guide."** `@HiltAndroidTest`, `HiltAndroidRule`, `@UninstallModules`, `@BindValue`, and the custom `TestRunner` — how to inject fakes into the real app for an end-to-end test:
  <https://developer.android.com/training/dependency-injection/hilt-testing>
- **`androidx.test` — runner, rules, core.** `AndroidJUnitRunner`, `ActivityScenarioRule`, `IdlingResource`:
  <https://developer.android.com/training/testing/instrumented-tests>

## Read a real test suite this week

You learn more from one hour reading a real, large, well-tested Android codebase than three hours of docs. The reference for the whole track:

- **`android/nowinandroid`** — Google's reference app. Read the ViewModel tests (Turbine + a fake repository, *not* MockK — note the choice), the `:core:testing` module (the `MainDispatcherRule`, the test data, the fakes), the Roborazzi screenshot tests, and the `@HiltAndroidTest` UI tests:
  <https://github.com/android/nowinandroid>
- **`cashapp/turbine`** — the Turbine repo's own tests are a master class in Flow assertions:
  <https://github.com/cashapp/turbine>
- **`chrisbanes/tivi`** — a real large app with screenshot tests and a layered suite wired into CI:
  <https://github.com/chrisbanes/tivi>

## Talks (free, watch in this order)

- **"Testing in Compose"** (Android Dev Summit) — the semantics tree, finders, and the test clock, demonstrated live.
- **"Write better tests, faster" / "Testing best practices"** (recent Google I/O Android testing sessions) — the pyramid, fakes vs. mocks, and determinism. Search the current year's I/O Android playlist.
- **"Coroutines testing"** (Manuel Vivo / Android Developers) — `runTest`, `TestDispatcher`, and the `MainDispatcherRule`, the deterministic-time talk.
- **"Screenshot testing"** sessions — the Roborazzi/Paparazzi workflow: record a golden, compare, fail on diff, and where it's a maintenance trap.

## Tools you'll use this week

- **Android Studio Ladybug (2024.2)+** — `Run ▸ Run Tests`, the test results panel, and the gutter run icons. The Compose UI test "semantics" inspector shows you the node tree the finders search.
- **`./gradlew :feature-checkout:testDebugUnitTest`** — runs the JVM small + medium tiers (JUnit 5, Turbine, MockK, Robolectric, Compose-on-JVM, Roborazzi).
- **`./gradlew :feature-checkout:recordRoborazziDebug`** / **`verifyRoborazziDebug`** — records and verifies screenshot goldens (Paparazzi: `recordPaparazziDebug` / `verifyPaparazziDebug`).
- **`./gradlew :feature-checkout:connectedDebugAndroidTest`** — runs the Espresso end-to-end smoke on a device or emulator (a Pixel 8 API 35 image is the reference).

## Free books and codelabs

- **Android's "Advanced testing" and "Testing" codelabs** — effectively a free guided book on the pyramid, Compose UI test, and Hilt testing:
  <https://developer.android.com/courses/android-basics-compose/course> (testing pathway)
- **The official "Now in Android" architecture and testing docs** in the repo's `docs/` folder — the rationale for fakes-over-mocks and the testing strategy:
  <https://github.com/android/nowinandroid/tree/main/docs>

## Paid books (optional, clearly marked)

- **"Android Test-Driven Development by Tutorials" — raywenderlich/Kodeco** (paid). Dated in places (pre-Compose-test in early editions) but the unit-testing and TDD discipline holds up.
- **"Programming Android with Kotlin" — Pierre-Olivier Laurence et al. (O'Reilly)** (paid). The testing chapter is solid and current on coroutine testing and Compose.

---

*If a link 404s, please open an issue so we can replace it.*
