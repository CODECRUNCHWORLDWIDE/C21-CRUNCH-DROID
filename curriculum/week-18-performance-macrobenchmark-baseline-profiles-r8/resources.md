# Week 18 — Resources

Every primary resource on this page is **free**. Android's performance documentation is free. The Macrobenchmark library, the Baseline Profile plugin, and R8 are all part of the open-source AGP/AndroidX stack. The conference talks are free on YouTube. A couple of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"App startup time" — the cold/warm/hot reference.** The framing document: what each startup type measures, TTID vs. TTFD, and what the user sees. Read this before you measure anything:
  <https://developer.android.com/topic/performance/vitals/launch-time>
- **"Write a Macrobenchmark."** The canonical guide — the `:benchmark` module, `MacrobenchmarkRule`, `measureRepeated`, `StartupTimingMetric`, reading the output:
  <https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview>
- **"Baseline Profiles overview."** What a Baseline Profile is, why ART pre-compiles the listed methods, and the measured cold-start wins:
  <https://developer.android.com/topic/performance/baselineprofiles/overview>
- **"Create a Baseline Profile."** The Gradle plugin, the generator test, packaging into release, and verifying ART used it:
  <https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile>
- **"Shrink, obfuscate, and optimize your app" (R8).** Enabling R8, full mode, `proguard-rules.pro`, keep rules, and the mapping outputs:
  <https://developer.android.com/build/shrink-code>

## Macrobenchmark and profiling, deeper

- **"Benchmark in CI" / "Measure app startup."** Running benchmarks reproducibly, locking the device, choosing iterations:
  <https://developer.android.com/topic/performance/benchmarking/benchmarking-overview>
- **"CompilationMode."** The difference between `None()`, `Partial()`, and `Full()` and which to use to isolate the Baseline Profile's effect:
  <https://developer.android.com/reference/kotlin/androidx/benchmark/macro/CompilationMode>
- **"Inspect trace recordings" / Perfetto.** Reading a system trace to find the slow startup span:
  <https://developer.android.com/topic/performance/tracing> · <https://ui.perfetto.dev/>
- **"Capture and read a trace with `Trace`."** Labeling your own startup spans with `Trace.beginSection`/`endSection`:
  <https://developer.android.com/topic/performance/tracing/custom-events>

## R8, keep rules, and shrinking

- **"Keep rules" and the R8 troubleshooting guide.** When and how to write `-keep`, `-keepclassmembers`, `-dontwarn`, and how libraries ship `consumer-rules.pro`:
  <https://developer.android.com/build/shrink-code#keep-code>
- **The R8 repository / FAQ** — the optimizer itself, full-mode notes, and the rule syntax reference:
  <https://r8.googlesource.com/r8>
- **kotlinx-serialization R8 rules**, **Hilt/Dagger keep rules**, **Retrofit R8 rules** — the canonical examples of reflection-heavy libraries that need (and usually ship) keep rules:
  <https://github.com/Kotlin/kotlinx.serialization#android> · <https://dagger.dev/hilt/gradle-setup>

## App Startup and StrictMode

- **"App Startup library."** `Initializer<T>`, the merged `InitializationProvider`, deferring eager init:
  <https://developer.android.com/topic/libraries/app-startup>
- **"StrictMode."** Flagging main-thread disk/network and leaked closeables in debug builds:
  <https://developer.android.com/reference/android/os/StrictMode>

## Read a real benchmark setup this week

You learn more from one hour reading a real, benchmarked app than three hours of docs. The reference for the whole track:

- **`android/nowinandroid`** — Google's reference app. Read the `:benchmarks` module (the `StartupBenchmark`, the `BaselineProfileGenerator`, the `measureRepeated` setup), the `baselineProfile { }` config, and the release build's R8 setup. This is the canonical, copyable structure:
  <https://github.com/android/nowinandroid>
- **`android/performance-samples`** — focused macrobenchmark and Baseline Profile samples, smaller than Now-In-Android:
  <https://github.com/android/performance-samples>
- **`chrisbanes/tivi`** — a real large app with a Baseline Profile and benchmark module wired into its release pipeline:
  <https://github.com/chrisbanes/tivi>

## Talks (free, watch in this order)

- **"Improve app performance with Baseline Profiles"** (Google I/O) — generate, package, measure, the whole loop demonstrated.
- **"Macrobenchmark: measure your app's performance"** (Android Dev Summit) — the library, the distribution, reading the trace.
- **"R8 / app shrinking deep dive"** (recent I/O build sessions) — full mode, keep rules, the mapping file. Search the current year's I/O Android playlist.
- **"What's new in app startup / performance"** — the current-year roundup on Baseline Profiles, Startup Profiles, and the App Startup library.

## Tools you'll use this week

- **Android Studio Ladybug (2024.2)+** — the "Run benchmark" gutter action, the benchmark results panel, and the built-in profiler (CPU/startup trace viewer). `Profile 'app'` for a system trace.
- **A real physical device** — a Pixel 6a / mid-range phone is the reference; a low-end device finds *more* bottlenecks. The emulator does **not** give meaningful macrobenchmark numbers.
- **`./gradlew :benchmark:connectedBenchmarkAndroidTest`** — runs the macrobenchmark on the connected device and prints the distribution.
- **`./gradlew :app:generateBaselineProfile`** (via the plugin) — runs the generator test and writes `app/src/release/generated/baselineProfiles/baseline-prof.txt`.
- **`./gradlew :app:assembleRelease`** — builds the R8-minified, profile-packaged release; check `app/build/outputs/mapping/release/` for `mapping.txt`, `usage.txt`, `seeds.txt`.
- **`adb shell dumpsys package <pkg> | grep -A3 "Dexopt"`** (and `cmd package compile`) — verify ART's compilation status / that the profile was applied.

## Free books and codelabs

- **Android's "Inspect app performance" and "Baseline Profiles" codelabs** — effectively a free guided book on macrobenchmark and the profile loop:
  <https://developer.android.com/codelabs/android-baseline-profiles> (and the macrobenchmark codelab)
- **The "Now in Android" docs** in the repo's `docs/` — the rationale for the benchmark module structure and the profile generation flow:
  <https://github.com/android/nowinandroid/tree/main/docs>

## Paid books (optional, clearly marked)

- **"Android Performance" / "High Performance Android Apps" (O'Reilly)** (paid). Older than the Compose/Baseline-Profile era in parts, but the systems thinking — measure, profile, the cost of doing work on the main thread — is timeless.
- **"Programming Android with Kotlin" — Pierre-Olivier Laurence et al. (O'Reilly)** (paid). The performance and build chapters cover R8 and startup at a useful level.

---

*If a link 404s, please open an issue so we can replace it.*
