// Exercise 2 — A cold-start macrobenchmark, profile off vs. profile on
//
// Goal: Write a StartupTimingMetric macrobenchmark that launches your app COLD,
//       run it twice — once with CompilationMode.None() (no profile, worst case)
//       and once with CompilationMode.Partial() (profile applied) — and report the
//       delta as a distribution. This is lecture 1, §2–4 and §8, made concrete on
//       real hardware.
//
// Estimated time: 50 minutes. REQUIRES A REAL PHYSICAL DEVICE (the emulator's
// numbers are meaningless — lecture 1, §2).
//
// HOW TO USE THIS FILE
//
//   This goes in a :benchmark module (com.android.test plugin, targetProjectPath
//   = ":app"). The build setup is in the comment block below; the @Test class is
//   the deliverable. Run:
//     ./gradlew :benchmark:connectedBenchmarkAndroidTest
//   on a connected physical device, and read the printed distribution.
//
// ACCEPTANCE CRITERIA
//
//   [ ] The benchmark uses StartupTimingMetric, StartupMode.COLD, and >= 15 iterations.
//   [ ] You run it with CompilationMode.None() AND CompilationMode.Partial(Require)
//       (two test methods, or a parameterized run) and capture both distributions.
//   [ ] You report P50 AND P90 for both, on a NAMED real device, in notes/startup.md.
//   [ ] You ran the SAME config twice to establish the noise floor, and your reported
//       delta exceeds it. No emulator numbers.
//   [ ] Builds with 0 warnings.
//
// Build setup (benchmark/build.gradle.kts):
//   plugins { id("com.android.test"); id("org.jetbrains.kotlin.android") }
//   android {
//       targetProjectPath = ":app"
//       experimentalProperties["android.experimental.self-instrumenting"] = true
//       defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
//       // a benchmark build type matching release signing, minified:
//   }
//   dependencies {
//       implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
//       implementation("androidx.test.ext:junit:1.2.1")
//       implementation("androidx.test.uiautomator:uiautomator:2.3.0")
//   }
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package com.crunch.reader.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.crunch.reader"
private const val ITERATIONS = 15

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    // TODO 1: The "before" — no profile, worst case.
    //   Call benchmarkRule.measureRepeated(...) with:
    //     packageName = TARGET_PACKAGE
    //     metrics = listOf(StartupTimingMetric())
    //     iterations = ITERATIONS
    //     startupMode = StartupMode.COLD
    //     compilationMode = CompilationMode.None()
    //   and a setupBlock that does pressHome(), measureBlock that does startActivityAndWait().
    @Test
    fun coldStartup_noProfile() {
        // your measureRepeated call here
    }

    // TODO 2: The "after" — profile applied.
    //   Same as above, but compilationMode = CompilationMode.Partial(
    //       baselineProfileMode = BaselineProfileMode.Require   // fail if no profile is present
    //   ). This isolates the profile's effect: None() minus Partial() = the win.
    @Test
    fun coldStartup_withProfile() {
        // your measureRepeated call here
    }

    // STRETCH TODO 3: Add a FrameTimingMetric() run that scrolls the article list,
    //   to measure jank (frame durations) as well as startup. (See hints.)
}

// ============================================================================
// HOW TO READ + REPORT (write this in notes/startup.md):
//
//   For each test method, the run prints something like:
//     timeToInitialDisplayMs   min X, median Y, P90 Z, max W
//
//   1. Record P50 (median) and P90 for BOTH None() and Partial().
//   2. Compute the delta: (none_P50 - partial_P50) and the %; same for P90.
//   3. Run coldStartup_withProfile TWICE and note how much the two runs differ —
//      that's your noise floor. Your reported win must exceed it to be real.
//   4. Name the device (e.g. "Pixel 6a, Android 14") and the iteration count.
//
//   A good report: "P50 cold start 521 -> 342ms (-34%); P90 612 -> 408ms (-33%),
//   15 iterations, Pixel 6a. Noise floor ~12ms from a repeated Partial() run, so the
//   delta is real." (Lecture 1, §8.)
// ============================================================================
// WHY None() vs Partial() AND NOT just Partial() (write before reading):
//
//   If you only ever benchmark with the profile applied, you have no baseline to
//   compare against — you can't tell whether the profile did anything. None() forces
//   the no-profile worst case (interpret/JIT), Partial() applies the profile (AOT the
//   listed methods), and the DIFFERENCE is the profile's effect. Isolating the one
//   variable — profile off vs on, everything else identical — is the measurement.
// ============================================================================
// HINTS (read only if stuck > 15 min)
// ============================================================================
//
// - "CompilationMode.Partial fails: no profile found." BaselineProfileMode.Require
//   demands a packaged profile. Either generate one first (exercise 3 of the mini-
//   project / the challenge), or use BaselineProfileMode.UseIfAvailable to not fail.
//   For THIS exercise the point is to see None() (slow) — Partial() needs the profile
//   from the challenge/mini-project; run them together.
//
// - "Numbers are wildly noisy / huge spread." You're on an emulator (don't), the
//   device is thermal-throttling (let it cool, plug it in), or background work is
//   running. Close other apps, lock the screen brightness, and raise iterations.
//
// - "startupMode = COLD but it seems warm." measureRepeated with StartupMode.COLD
//   force-stops the app between iterations. Make sure your measureBlock uses
//   startActivityAndWait() (not just startActivity) so it waits for the first frame.
//
// - "FrameTimingMetric (stretch) is empty." FrameTimingMetric needs actual frames
//   produced during the measureBlock — drive a scroll with UiAutomator
//   (device.findObject(By.res("article_list")).fling(Direction.DOWN)) so frames render.
//
// - "Where's the trace?" Each iteration captures a Perfetto trace; the benchmark
//   output links it. Open it to see WHERE the time went (lecture 2, §6) when the
//   number is worse than you expect.
// ============================================================================
