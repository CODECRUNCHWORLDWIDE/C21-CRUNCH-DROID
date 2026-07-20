# Week 06 — Resources

Every primary resource on this page is **free**. Android's developer documentation is free. The Android Open Source Project is public on Android Code Search. The Gradle and AGP docs are free. The conference talks are free on YouTube. A couple of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"Android runtime (ART) and Dalvik."** The canonical source-level overview of ART, DEX, AOT/JIT, and the compilation pipeline. Read it slowly; it's the foundation of lecture 1:
  <https://source.android.com/docs/core/runtime>
- **"Configure your build."** The top of the Android build documentation tree — the build process overview, the APK/AAB anatomy, and the task graph. The diagram of the build pipeline is the one to internalize:
  <https://developer.android.com/build>
- **"Migrate your build to version catalogs."** The official guide to `libs.versions.toml` and the typed accessors. Short and exactly what the exercises use:
  <https://developer.android.com/build/migrate-to-catalogs>
- **"Configure build variants."** Build types, product flavors, the variant matrix, flavor source sets, and `BuildConfig` — the mini-project's core:
  <https://developer.android.com/build/build-variants>
- **"Sign your app."** What signing proves, debug vs release keystores, `signingConfigs`, and the conceptual intro to Play App Signing (the deep dive is Phase 4):
  <https://developer.android.com/studio/publish/app-signing>

## The build pipeline, the runtime, and shrinking

- **"Shrink, obfuscate, and optimize your app" (R8).** The introduction to R8 — shrinking, optimization, obfuscation, and keep rules. Read the overview this week; the full keep-rule treatment is Week 18:
  <https://developer.android.com/build/shrink-code>
- **"Application fundamentals" and "Processes and threads."** The process model — every app a forked process, the OS killing processes, the main thread. The "why process death is normal" foundation:
  <https://developer.android.com/guide/components/fundamentals>
  <https://developer.android.com/guide/components/processes-and-threads>
- **"The activity lifecycle."** The canonical lifecycle diagram — `onCreate` through `onDestroy`, configuration changes, and saved state. Treat it as history-plus-host this week:
  <https://developer.android.com/guide/components/activities/activity-lifecycle>
- **"D8 and R8" / the dexing internals.** Background on D8 (the dexer) and R8 (the shrinker/dexer) and why JVM bytecode becomes DEX. The AGP release notes track the current behavior.

## AOSP — read it at the source

The framework is open source. You will not modify it this week, but knowing how to *navigate* it is a senior superpower. Use Android Code Search:

- **Android Code Search** — the searchable AOSP tree. Start here for any "how does the framework actually do X" question:
  <https://cs.android.com/>
- **`frameworks/base`** — the Java/Kotlin framework: `ActivityManager`, `Activity`, `Context`, the lifecycle machinery. Where "the framework" mostly lives:
  <https://cs.android.com/android/platform/superproject/+/master:frameworks/base/>
- **`ActivityThread.java`** — the entry point of an app process; the `main()` the Zygote fork lands in, the message loop, and how `onCreate` actually gets called. Read the top of it once and the lifecycle stops being magic:
  <https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/app/ActivityThread.java>
- **The Zygote and `system_server`** — search Code Search for `ZygoteInit` and `SystemServer` to see process forking and the system services that start/stop/kill your app.

## Gradle and the Android Gradle Plugin

- **The Gradle user manual — Kotlin DSL primer.** How the Kotlin DSL works, the `plugins`/`dependencies` blocks, and the build lifecycle (configuration vs execution). Worth a skim even if you "know Gradle":
  <https://docs.gradle.org/current/userguide/kotlin_dsl.html>
- **The Android Gradle Plugin release notes and DSL reference** — the authoritative `android { }` DSL and the AGP version compatibility table (AGP ↔ Gradle ↔ JDK):
  <https://developer.android.com/build/releases/gradle-plugin>
- **`./gradlew :app:dependencies` and `--scan`** — the two commands that turn the build graph from a mystery into a tree you can read. The exercises lean on both.

## Talks (free, watch in this order)

- **"What's new in Android development tools" / the current Google I/O Android build session** — the latest on AGP, version catalogs, and build performance. Prefer the most recent year's session.
- **"Demystifying the Android build" / "Inside the Android build system"** — the stage-by-stage pipeline walkthrough; search the Android Developers channel for the current version.
- **"Understanding ART" / any ART deep-dive from a Google runtime engineer** — DEX, dex2oat, profile-guided compilation. Sets up Week 18's Baseline Profiles.

## Community writing (current, opinionated, correct)

- **Jake Wharton's blog** — the deepest independent writing on R8, D8, dexing, and Gradle internals. The "R8 optimization" and "dex" posts are essential and current:
  <https://jakewharton.com/blog/>
- **Tony Robalik / the `dependency-analysis` plugin writing** — how to read and slim a dependency graph; directly useful for the multi-module mini-project:
  <https://dev.to/autonomousapps>
- **The Android Developers Medium publication** — official long-form on build, variants, and the runtime; filter for the build/performance series:
  <https://medium.com/androiddevelopers>

## Open-source projects to read this week

You learn more from one hour reading a real multi-module build than three hours of docs. Read the *build files*, not the app code, this week:

- **`android/nowinandroid`** — the reference multi-module build. Read its `gradle/libs.versions.toml`, its convention plugins in `build-logic/`, and its module structure. This is the architecture template for the whole track:
  <https://github.com/android/nowinandroid>
- **`chrisbanes/tivi`** — a large real app with a sophisticated version catalog and build-logic setup; read how it organizes flavors and signing:
  <https://github.com/chrisbanes/tivi>
- **Any Gradle `build-logic` / convention-plugin example** — the pattern of extracting shared build config into a `build-logic` included build. You'll meet it for real in Phase 3; read one now.

## Tools you'll use this week

- **Android Studio Ladybug (2024.2)+** — `Help ▸ About` to confirm. The Build Analyzer (`Build ▸ Build Analyzer`) shows which tasks ran and how long they took.
- **`./gradlew`** — `assembleDebug`, `assembleFreeDebug`, `:app:dependencies`, `--scan`, `--info`. The build is driven from here, not just the Run button.
- **`apkanalyzer`** (in the SDK `cmdline-tools`) — `apkanalyzer apk summary app.apk`, `apkanalyzer dex packages app.apk`, `apkanalyzer files list app.apk`. Crack open the APK and see the DEX, resources, and manifest.
- **`apksigner` and `keytool`** (SDK + JDK) — `apksigner verify --print-certs app.apk` to inspect the signature; `keytool` to look at the debug keystore.
- **The Pixel 8 API 35 emulator** — `Tools ▸ Device Manager`. The reference device for the rest of the course.

## Free books (chapter-level, not whole books)

- **"Gradle Hero" / the free Gradle guides** — task graphs, configuration vs execution, and the Kotlin DSL. Enough to stop fearing `build.gradle.kts`.
- **The AGP and Gradle documentation as a guided path** — the "Configure your build" tree above reads like a free book if you follow it top to bottom; it's the single best free resource on the Android build.

## Paid books (optional, clearly marked)

- **"Gradle in Action" / "Mastering Gradle"** (paid). Broader than Android, but the build-lifecycle and task-graph chapters demystify Gradle for good.
- **"Android Internals: A Confectioner's Cookbook" — Jonathan Levin** (paid, advanced). Deep AOSP/runtime internals — far beyond this week, but the reference if you want to go all the way down to the Zygote and the binder.

---

*If a link 404s, please open an issue so we can replace it.*
