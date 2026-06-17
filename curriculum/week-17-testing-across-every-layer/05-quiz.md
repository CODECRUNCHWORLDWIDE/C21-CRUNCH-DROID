# Week 17 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 18. Answer key with explanations at the bottom — don't peek.

---

**Q1.** The testing pyramid says you should write:

- A) Equal numbers of small, medium, and large tests.
- B) Many small (fast, JVM) tests, fewer medium, very few large — because cost compounds and the base gives the best confidence per second.
- C) Mostly large end-to-end tests, since they're the most realistic.
- D) Only the tests the linter requires.

---

**Q2.** In `test/` (JVM unit tests) you use JUnit 5; in `androidTest/` (instrumentation) you use JUnit 4. Why the split?

- A) JUnit 5 is buggy on Android.
- B) The `AndroidJUnitRunner` is a JUnit 4 runner, so on-device tests default to JUnit 4; JVM tests can use the cleaner JUnit 5 via the `android-junit5` plugin.
- C) JUnit 4 is required for Compose.
- D) There is no split; everything is JUnit 5.

---

**Q3.** Inside `runTest`, a `delay(1000)` in the code under test:

- A) Pauses the test for a real second.
- B) Completes instantly in virtual time; the test clock skips the delay, and `currentTime` advances by 1000.
- C) Throws, because delays aren't allowed in tests.
- D) Is ignored entirely and never runs the continuation.

---

**Q4.** You test a `ViewModel` that launches into `viewModelScope`. On the JVM the test fails with "Main dispatcher had failed to initialize." The fix is:

- A) Run the test on a device instead.
- B) Swap `Dispatchers.Main` for a `TestDispatcher` — typically via a `MainDispatcherRule`/extension calling `Dispatchers.setMain(...)` — and `resetMain()` after.
- C) Add `@Disabled` to the test.
- D) Wrap the launch in `try/catch`.

---

**Q5.** You collect a `StateFlow<UiState>` with Turbine's `test { }`. The flow currently holds `Loading`, then you trigger a change to `Content`. The first `awaitItem()` returns:

- A) `Content` — Turbine skips the current value.
- B) `Loading` — a `StateFlow` replays its current value to a new collector, so the first item is the current state and the second is the change.
- C) Nothing; `awaitItem()` blocks forever on a `StateFlow`.
- D) A list of both items.

---

**Q6.** In MockK, to stub a **suspend** function `suspend fun load(): Data` you write:

- A) `every { repo.load() } returns data`
- B) `coEvery { repo.load() } returns data` — the `co` prefix handles suspend; `every` is for non-suspend.
- C) `when (repo.load()) returns data`
- D) `mockkStatic(repo.load())`

---

**Q7.** Now-In-Android prefers hand-written **fakes** over mocks for its repositories. The main reason:

- A) Mocks don't compile in Kotlin.
- B) A fake is a real, reusable implementation that encodes the contract once and keeps tests readable; mocks scatter per-call stubbing across every test and can drift from the real contract.
- C) Fakes are required by Hilt.
- D) Fakes run on the GPU.

---

**Q8.** You need to test that a Room DAO's `SELECT SUM(...)` returns 0 (not null) for an empty cart. The right tier and tool:

- A) Small / a fake DAO — fastest.
- B) Medium / Robolectric with an in-memory Room database, so the test runs your actual SQL against real SQLite without a device.
- C) Large / Espresso on a device.
- D) You can't test SQL; trust Room.

---

**Q9.** A Compose UI test finds nodes by querying:

- A) The `View` hierarchy via `findViewById`.
- B) The **semantics tree** — the same tree TalkBack reads — with finders like `onNodeWithText`, `onNodeWithContentDescription`, and `onNodeWithTag`.
- C) Pixel coordinates only.
- D) The XML layout file.

---

**Q10.** A screenshot test (Roborazzi/Paparazzi) catches a regression that a `assertIsDisplayed` UI test would miss. Which?

- A) A crash on launch.
- B) A visual change — a wrong color, padding, or font weight — where the screen still "works" (nodes present, clicks fire) but looks wrong.
- C) A null pointer in the ViewModel.
- D) A slow cold start.

---

**Q11.** Why write exactly **one** Espresso end-to-end smoke test instead of fifty?

- A) Espresso only supports one test per module.
- B) The large tier is slow and flaky-prone; its job is to prove the *wiring* (Hilt graph, navigation, real components connect), which the base already-tested logic only needs proven once. Fifty would re-test logic the base owns, slowly.
- C) Fifty tests would exceed the device's memory.
- D) One test is enough to reach 100% coverage.

---

**Q12.** In an `@HiltAndroidTest` end-to-end test, you avoid hitting a real backend by:

- A) Mocking the entire Activity.
- B) Using `@TestInstallIn` (or `@UninstallModules` + a test module) to replace the production network module with one providing a fake `OrderApi`, plus a custom runner using `HiltTestApplication`.
- C) Turning off the network on the device.
- D) Catching all exceptions in the test.

---

**Q13.** A test passes on your laptop but fails ~1 run in 6 on CI. The most likely cause and the fix:

- A) CI is broken; add a retry.
- B) Non-determinism — a real clock (`Thread.sleep`), a real dispatcher, a real network call, or shared mutable state — and the fix is to *remove* it: virtual clock (`TestDispatcher`), fakes, fresh state per test. Never a retry or a longer timeout.
- C) The test is fine; mark it `@Disabled`.
- D) The CI machine needs more RAM.

---

## Answer key

**Q1 — B.** The pyramid is a budget: many fast small tests at the base, very few slow large tests at the tip, because cost compounds and the base gives the best confidence per second. A rectangle (equal counts) makes CI slow and flaky. (Lecture 1, §1.)

**Q2 — B.** `AndroidJUnitRunner` is a JUnit 4 runner, so instrumentation stays JUnit 4; JVM unit tests use the cleaner JUnit 5 via `de.mannodermaus.android-junit5`. Live with the split. (Lecture 1, §3.)

**Q3 — B.** `runTest` gives a virtual clock; `delay` completes instantly and `currentTime` advances by the delay amount. That's what makes coroutine tests fast and deterministic. (Lecture 1, §4.)

**Q4 — B.** `viewModelScope` uses `Dispatchers.Main`, which doesn't exist on the JVM; swap it with `Dispatchers.setMain(testDispatcher)` (a `MainDispatcherRule`/extension) and `resetMain()` after. (Lecture 1, §4.)

**Q5 — B.** A `StateFlow` replays its current value to every new collector, so the first `awaitItem()` is the current state (`Loading`) and the second is the change (`Content`). The classic off-by-one. (Lecture 1, §5.)

**Q6 — B.** `coEvery { } returns` stubs suspend functions; plain `every` is for non-suspend. Likewise `coVerify` for suspend calls. (Lecture 1, §6.)

**Q7 — B.** A fake is a real, reusable implementation encoding the contract once; it keeps tests readable and catches contract drift, where per-test mock stubbing scatters and can lie. Use mocks for interaction verification, fakes for stateful behavior. (Lecture 1, §6.)

**Q8 — B.** The behavior *is* the SQL, so test the real query against real SQLite — Robolectric + in-memory Room, the medium tier. A fake DAO would test your fake's arithmetic and miss the `SUM`-is-`NULL`-over-zero-rows bug. (Lecture 2, §1.)

**Q9 — B.** Compose UI tests query the semantics tree — the same one TalkBack uses — via `onNodeWithText`/`onNodeWithContentDescription`/`onNodeWithTag`. Accessible composables are testable composables. (Lecture 2, §2.)

**Q10 — B.** Screenshot tests catch *visual* regressions — color, padding, font — that assertions miss, because the screen still functions. They don't catch logic bugs; that's the UI test's job. (Lecture 2, §3.)

**Q11 — B.** The large tier is slow and flaky-prone; its job is proving the wiring connects, which only needs proving once since the base owns the logic. Fifty Espresso tests is a CI tax re-testing logic slowly. (Lecture 2, §4.)

**Q12 — B.** `@TestInstallIn`/`@UninstallModules` swaps the production network module for one providing a fake `OrderApi`, and a custom runner boots `HiltTestApplication`. The smoke drives the real graph against an in-memory API — deterministic. (Lecture 2, §4.)

**Q13 — B.** A pass-here-fail-there flake is non-determinism: real clock, real dispatcher, real I/O, or shared state. Remove it (virtual clock, fakes, fresh state per test). A retry or longer timeout hides the flake and trains the team to ignore red. (Lecture 2, §5; challenge 1.)

---

*Score 11+? On to Week 18. Below 9? Re-read both lecture notes and re-run exercises 02 and 03 — the deterministic ViewModel test and the Compose-UI-plus-screenshot pair are the two skills this week is graded on.*
