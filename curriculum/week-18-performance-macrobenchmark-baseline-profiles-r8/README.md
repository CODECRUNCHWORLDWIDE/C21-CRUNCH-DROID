# Week 18 — Performance: macrobenchmark, Baseline Profiles, R8

Welcome to Week 18 of **C21 · Crunch Droid**, and to the last week of Phase 3. You can build the app, persist its data, talk to a backend, run its background work, and — as of last week — test every layer of it. This week you make it **fast**, and more importantly, you learn to *prove* it's fast with numbers instead of vibes. Last week's discipline was "control the inputs so the assertion is deterministic." This week's is the same instinct pointed at a stopwatch: "control the device so the *measurement* is reproducible." A flaky test and a noisy benchmark are the same disease, and you already know the cure.

The mental shift this week is from "the app feels snappy on my Pixel" to "cold start on a low-end device is P50 480ms, P90 610ms, and here is the trace that tells me where the 130ms of tail went." Performance work that isn't measured is superstition — you'll meet engineers who "optimized" a screen by adding `remember` everywhere and made it *slower*, because they never measured. The senior skill is the **measure → diagnose → fix → re-measure loop**, run on a real device in a controlled state, against a benchmark that's stable enough that a 10% regression shows up as signal and not noise. We build that loop with the **Macrobenchmark library**, and we focus it on the one metric that's a first impression you can't retake: **cold start.**

The thing this week hammers on is that **cold start is the only first impression you get, and you have three levers to improve it: a Baseline Profile, R8, and the work you do (or don't do) before the first frame.** A **Baseline Profile** ships a list of hot methods so ART can ahead-of-time-compile them at install instead of interpreting then JIT-compiling them on the user's device — a real, measurable cold-start win (often 20–40%) for free, once you generate and package it. **R8** is the optimizer and shrinker that makes your release build smaller and faster by removing dead code, inlining, and renaming — and it's also the thing that *breaks* reflection-heavy code if you don't write the right keep rules, which is why so many engineers disable it in frustration (a mistake we will not make). And the **App Startup** library plus StrictMode catch the self-inflicted wounds — the disk read on the main thread, the eager initialization of a library you don't need until screen three.

We close the week by taking the **Week-11 reader app**, writing a Baseline Profile that exercises its cold-start path, measuring the before/after with macrobenchmark on a real device, committing the profile, and *documenting the cold-start improvement with real numbers*. A naive first measurement is noisy and the profile seems to do nothing; a controlled measurement on a locked device with enough iterations shows the profile cutting P50 cold start by a third. That before/after — "cold start was 520ms median, the Baseline Profile took it to 340ms, here's the macrobenchmark output proving it" — is the senior instinct this week installs, and it's the exact deliverable the capstone requires (a Baseline Profile demonstrated to cut cold start by ≥20%).

## Learning objectives

By the end of this week, you will be able to:

- **Explain** what cold, warm, and hot start each measure, why cold start is the metric that matters most, and what the user sees during each (the splash, the first frame, time-to-initial-display vs. time-to-full-display).
- **Write** a Macrobenchmark that measures `StartupTimingMetric` over a controlled number of iterations, with the right `StartupMode` and `CompilationMode`, and read its P50/P90 output as a distribution, not a single number.
- **Control** a benchmark's environment so the number is reproducible: a real device (not the emulator), `CompilationMode.None()` vs `Partial()`, locked clocks where possible, and enough iterations that noise averages out.
- **Generate** a Baseline Profile with the Baseline Profile Gradle plugin and a `baselineProfile { }` generator test, understand the `.prof`/`baseline-prof.txt` artifact, package it into the release APK/AAB, and **verify** ART actually used it.
- **Measure** the Baseline Profile's effect: benchmark cold start with `CompilationMode.None()` (profile off) vs `CompilationMode.Partial(baselineProfile)` (profile on), and report the delta honestly.
- **Configure** R8: enable it for release, read what full mode does (optimization + shrinking + obfuscation), write `-keep` rules for reflection-heavy code (serialization, Hilt, Room) without disabling R8, and read the mapping/usage/seeds output.
- **Catch** startup self-harm: use the App Startup library to defer eager initializers, turn on StrictMode to flag main-thread disk/network in debug, and read a system trace to find the slow span.

## Prerequisites

This week assumes you have completed **C21 weeks 1–17**, or have equivalent fluency. Specifically:

- You have a Compose app you can run on a real device — the Week-11 reader app is the reference for the mini-project. You need a release-buildable app with a cold-start path worth measuring.
- You understand the build pipeline from Week 6 — Gradle, build variants, signing — because Baseline Profiles and R8 are *release-build* concerns wired through Gradle. You'll add a `:benchmark` module and a `baselineProfile` plugin.
- You can read a `build.gradle.kts` and a version catalog without flinching (Week 6). The benchmark module, the profile plugin, and the R8 config all live in build files.
- You have the measurement discipline from Week 17 — control the inputs, trust the number only when it's reproducible. This week applies it to a stopwatch instead of an assertion.
- Helpful: the recomposition-diagnosis instinct from Week 7. The "measure, don't guess" loop is identical; the metric is now a millisecond of cold start, not a recomposition count.

**Toolchain.** Android Studio Ladybug (2024.2) or newer, JDK 17, Kotlin 2.0+. The `androidx.benchmark` Macrobenchmark library and the Baseline Profile Gradle plugin (`androidx.baselineprofile`). A **real physical device** (Macrobenchmark cannot run meaningfully on an emulator — you need real hardware to measure startup; any Pixel running Android 10+ works, a mid- or low-end device is *better* for finding real bottlenecks). R8 ships with AGP; you enable `isMinifyEnabled` / `isShrinkResources` on the release build type. StrictMode and the App Startup library (`androidx.startup`) are part of Jetpack.

## Topics covered

- **Cold, warm, hot start.** What each means (process created from scratch / process alive, activity recreated / activity in memory), what the user sees, time-to-initial-display (TTID) vs. time-to-full-display (TTFD), and why cold start is the headline metric.
- **The Macrobenchmark library.** A separate `:benchmark` module, `MacrobenchmarkRule`, `measureRepeated`, `StartupTimingMetric`/`FrameTimingMetric`, `StartupMode.COLD`, iterations, and reading the P50/P90/P95 distribution it prints (and the trace it captures).
- **CompilationMode.** `None()` (everything interpreted/JIT — the worst case, the baseline), `Partial(baselineProfile)` (the profile applied), `Full()` (everything AOT — not realistic for release), and why you benchmark `None` vs `Partial` to isolate the profile's effect.
- **Baseline Profiles.** What they are (a list of hot classes/methods ART pre-compiles at install), the Baseline Profile Gradle plugin, a generator test (`baselineProfileRule.collect { }` driving the cold-start journey), the `baseline-prof.txt` artifact, packaging into the release build, and verifying ART used it (`dumpsys`/the compilation status).
- **Startup Profiles (a note).** The newer companion that optimizes dex layout for startup; same generation flow, complementary win.
- **R8.** Enabling it (`isMinifyEnabled = true`), full mode vs. compatibility mode, the three jobs (shrink dead code, optimize/inline, obfuscate names), `proguard-rules.pro`, `-keep` rules, `-keepclassmembers`, `-dontwarn`, and the `mapping.txt`/`usage.txt`/`seeds.txt` outputs.
- **Keep rules for reflection.** Why kotlinx-serialization, Hilt/Dagger, Room, and Retrofit need keep rules (they reflect on names R8 would rename), how the libraries ship `consumer-rules.pro` so you usually don't write them yourself, and how to write one when you must.
- **App Startup library.** `Initializer<T>`, the merged `InitializationProvider`, deferring eager `ContentProvider`-based init, and why a library that "just works" by auto-initializing on a background... actually runs on the main thread before your first frame.
- **StrictMode and system traces.** `StrictMode.ThreadPolicy`/`VmPolicy` to flag main-thread disk/network and leaked closeables in debug; reading a Perfetto/system trace to find the slow startup span; `Trace.beginSection`/`endSection` to label your own spans.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                                | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|----------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | Cold/warm/hot start; the Macrobenchmark library; reading a distribution |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | CompilationMode; Baseline Profiles — generate, package, verify       |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | R8 — full mode, keep rules for reflection; mapping outputs; challenge |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | App Startup, StrictMode, system traces; measure the profile's effect; challenge |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — benchmark the reader app's cold start (the "before")  |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project — generate + package the profile; measure the "after"   |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                           |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                      | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./README.md) | This overview (you are here) |
| [resources.md](./resources.md) | The performance docs, the Macrobenchmark/Baseline Profile/R8 guides, the `now-in-android` benchmark module, and the canonical talks |
| [lecture-notes/01-cold-start-macrobenchmark-baseline-profiles.md](./lecture-notes/01-cold-start-macrobenchmark-baseline-profiles.md) | Cold/warm/hot start; the Macrobenchmark library and reading a distribution; Baseline Profiles — what they are, generate, package, verify, and measure the effect |
| [lecture-notes/02-r8-keep-rules-startup-strictmode.md](./lecture-notes/02-r8-keep-rules-startup-strictmode.md) | R8 — full mode, the three jobs, keep rules for reflection without disabling R8, mapping outputs; the App Startup library; StrictMode and reading a system trace |
| [exercises/README.md](./exercises/README.md) | Index of the three exercises |
| [exercises/exercise-01-read-a-macrobenchmark-report.md](./exercises/exercise-01-read-a-macrobenchmark-report.md) | Read a real macrobenchmark output, interpret the P50/P90 distribution, decide if a change is signal or noise, and diagnose a noisy run |
| [exercises/exercise-02-startup-macrobenchmark.kt](./exercises/exercise-02-startup-macrobenchmark.kt) | Write a `StartupTimingMetric` macrobenchmark with `StartupMode.COLD`, benchmark `CompilationMode.None()` vs `Partial()`, and report the delta |
| [exercises/exercise-03-r8-keep-rules.kt](./exercises/exercise-03-r8-keep-rules.kt) | Enable R8, watch a reflection-based serialization call break, write the minimal `-keep` rule that fixes it without disabling R8, and read `usage.txt` |
| [challenges/README.md](./challenges/README.md) | Index of the challenge |
| [challenges/challenge-01-baseline-profile-end-to-end.md](./challenges/challenge-01-baseline-profile-end-to-end.md) | Take an app with no profile, measure cold start, generate and package a Baseline Profile, re-measure, and document a ≥20% improvement with the macrobenchmark output |
| [quiz.md](./quiz.md) | 13 questions on startup metrics, macrobenchmark, CompilationMode, Baseline Profiles, R8, keep rules, and App Startup |
| [homework.md](./homework.md) | Six practice problems for the week |
| [mini-project/README.md](./mini-project/README.md) | Full spec for benchmarking the Week-11 reader app, generating + packaging a Baseline Profile, and documenting the cold-start improvement |

## The "prove it with a distribution" promise

Every week of Crunch Droid adds one production-grade contract a senior reviewer actually checks. This week's:

> **A performance claim must be a reproducible distribution on real hardware, not a single number from one run on your laptop's emulator.** "It feels faster" is not an engineering statement; "P50 cold start dropped from 520ms to 340ms over 20 iterations on a Pixel 6a, here's the macrobenchmark output and the trace" is. Run the benchmark on a real device, take enough iterations that noise averages out, report P50 *and* P90 (the tail is where users actually suffer), and only believe a change is real when it clears the run-to-run noise.

You will *prove* this in the mini-project: you benchmark the reader app's cold start *before* any profile, generate and package a Baseline Profile, benchmark *after*, and report the delta as a distribution. A first attempt that measures once and declares victory is the failure mode this week trains out of you — the senior move is to measure enough that the number is trustworthy, and to be honest when a "win" is inside the noise.

## A note on what's not here

Week 18 is the *performance* week. It deliberately does **not** cover:

- **Correctness testing.** That was Week 17. Macrobenchmark *measures*, it doesn't *assert* — a benchmark that runs without crashing but reports a bad number is "passing" in the JUnit sense and failing in the sense that matters. Different discipline, different week.
- **CI/CD wiring.** Running benchmarks and generating profiles on GitHub Actions (a Firebase Test Lab device, or a self-hosted runner) is **Week 21**. This week you run on a device on your desk.
- **Microbenchmarks in depth.** The Microbenchmark library (timing a tight function in isolation) gets a mention, but the week is about *macro*benchmark — the whole-app, on-device, user-journey measurement. Cold start is a macro concern.
- **Network and rendering performance beyond startup.** `FrameTimingMetric` for jank gets introduced, but the deep frame-by-frame jank hunt and network profiling are beyond this week's startup focus.

The point of Week 18 is narrow and deep: cold start, measured reproducibly with macrobenchmark, improved with a Baseline Profile and R8, and *proven* with a distribution.

## Up next

Continue to **Week 19 — Kotlin Multiplatform overview, Wear OS introduction** once you have shipped this week's mini-project and documented a ≥20% cold-start improvement with the macrobenchmark output. Phase 4 begins: you pull the business layer out of the Android-only world and into Kotlin Multiplatform (`commonMain`, `expect`/`actual`), and you meet your second form factor — Compose for Wear OS. The measurement discipline carries forward in spirit — a KMP `:shared-core` has to be *fast* too, and a Wear app's startup budget is even tighter than a phone's — but the new skill is *sharing* code across platforms without sharing what shouldn't be shared. You finish Phase 3 having made the app fast and proven it; Phase 4 makes it portable.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
