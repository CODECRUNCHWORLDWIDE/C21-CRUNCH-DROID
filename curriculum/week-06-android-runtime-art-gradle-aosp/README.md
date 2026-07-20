# Week 06 — The Android runtime, ART, Gradle, and an AOSP-aware mental model

Welcome to Week 06 of **C21 · Crunch Droid**, the last week of Phase 1 — and the week the course finally says the word "Android" and means the platform, not just the language. For five weeks you built the foundation entirely on the JVM: Kotlin 2.x as a real language, generics and inline machinery, coroutines and Flow as a concurrency discipline. All of that ran on a desktop JVM with `./gradlew run`. This week you cross over to the device. Not by writing UI — that's Phase 2, starting next week — but by understanding the *machine your code will actually run on* and the *build that gets it there*. By Friday you should be able to look at an `.apk`, trace every step that produced it, name what runs on ART versus what doesn't, and read a `build.gradle.kts` without flinching.

The mental shift this week is from "I write Kotlin and the JVM runs it" to "I write Kotlin, a multi-stage build turns it into DEX bytecode plus compiled resources plus a signed archive, and a *different* runtime — ART, the Android Runtime — executes it on a device with constraints the desktop JVM never had." The desktop JVM has gigabytes of heap, a fan, and wall power. ART runs on a phone with a battery the OS is fighting to preserve, a heap measured in tens of megabytes per app, an OS that will kill your process without warning, and an ahead-of-time/just-in-time hybrid compilation strategy designed around exactly those constraints. Your Kotlin doesn't change, but everything *around* it does, and a senior Android engineer carries that difference in their head at all times.

The thing this week hammers on is **"where your APK comes from."** Most Android developers treat the build as a black box — they press Run, it works, and when it breaks they paste the error into a search engine. That is the difference between someone who *uses* Gradle and someone who can *debug* it. We trace `./gradlew assembleDebug` end to end: Kotlin compiles to JVM bytecode, R8 desugars and (in release) shrinks it, D8/R8 dexes it into DEX format ART can load, the resource compiler (AAPT2) turns your XML and assets into a binary `resources.arsc` and an `R` class, the manifest merger combines your manifest with every library's, and finally the whole thing is zipped, aligned, and signed. When a build fails — and they fail constantly in real Android work — knowing *which* of those stages failed is the difference between a five-minute fix and a lost afternoon. The skill this week earns is reading a Gradle build, tracing a failure to the right task, and understanding which artifact each stage produces.

We pair that build literacy with **Gradle Kotlin DSL fluency**: version catalogs (`libs.versions.toml`) so your dependency versions live in one typed, navigable place; build variants (`free` and `pro` flavors) so one codebase produces multiple apps; signing configs so you understand how an APK is cryptographically tied to a developer identity; and a first, honest introduction to **R8** — the shrinker, optimizer, and obfuscator that runs on every release build and that you will spend real time taming in Phase 3. And we keep one eye on **AOSP**, the Android Open Source Project, so that "the framework" stops being a magic box: it's a few million lines of mostly-Java/C++ source you can read, and knowing how to navigate it at a high level is a senior superpower.

The week's mini-project is a **two-module Android project** — a `:core` library and an `:app` module — wired with version catalogs, two build variants (`free` and `pro`), and a documented signing config using a debug keystore checked into the repo. You'll build it, run `./gradlew :app:dependencies` to see the resolved graph, build both variants, and `apkanalyzer` (or unzip) the resulting APKs to *see* the DEX, the `resources.arsc`, the merged manifest, and the signature. That "I built it, then I opened it up and saw exactly what's inside" loop is the senior instinct this week installs.

## Learning objectives

By the end of this week, you will be able to:

- **Explain** how ART differs from a desktop JVM: DEX bytecode (not JVM `.class`), the AOT/JIT hybrid with profile-guided compilation, a constrained per-app heap, and an OS that kills processes — and why each constraint shapes how you write Android code.
- **Trace** `./gradlew assembleDebug` end to end: Kotlin compilation → desugaring → D8/R8 dexing → AAPT2 resource compilation → manifest merge → packaging → zipalign → signing, naming the artifact each stage produces.
- **Describe** the Android lifecycle (Activity, and Fragment as historical context) well enough to know what Compose replaced and why the lifecycle still matters underneath.
- **Read and write** a `build.gradle.kts` using the Kotlin DSL: plugins block, `android { }` configuration, dependencies, and a version catalog (`libs.versions.toml`) with `libs.plugins.*` and `libs.*` accessors.
- **Configure** build variants: product flavors (`free`/`pro`) and build types (`debug`/`release`), the variant matrix they produce, and flavor-specific source sets and dependencies.
- **Set up** a signing config with a debug keystore, explain what signing proves, and understand Play App Signing at a conceptual level (the deep dive is Phase 4).
- **Reason** about R8 at an introductory level: shrinking, desugaring, optimization, and obfuscation; why `minifyEnabled true` changes a release build; and where ProGuard/keep rules will become necessary (full treatment Phase 3).
- **Navigate** the AOSP source tree conceptually — `frameworks/base`, the framework versus the app process, the Zygote and `system_server` at a high level — using Android Code Search.

## Prerequisites

This week assumes you have completed **C21 weeks 1–5**, or have equivalent fluency. Specifically:

- You can read and write idiomatic Kotlin 2.x and understand that Kotlin compiles to **JVM bytecode** — Week 01 (where you used `javap`). This week adds the *second* compilation step, JVM bytecode → DEX, that only happens for Android.
- You understand Gradle Kotlin DSL at the single-module level — Week 01 (the `kt-stat` fat JAR) and Week 03 (`maven-publish`). This week scales it to multi-module Android with the `com.android.*` plugins, version catalogs, and variants.
- You have working coroutines fluency — Week 04–05 — because the lifecycle discussion connects to `lifecycleScope` and structured concurrency, even though we don't build UI yet.
- **Your toolchain is now Android-complete.** This is the week Android Studio and the Android SDK become required. Install Android Studio (Ladybug or newer), the Android SDK (compileSdk 35/36, an API 35 platform), and create a Pixel 8 API 35 emulator. The mini-project is the first thing in the course that builds an `.apk`.

**Toolchain.** Android Studio Ladybug (2024.2) or newer, JDK 17 (the Android Gradle Plugin requires it), the Android SDK with **compileSdk 35** (Android 15) or 36, **targetSdk 35**, **minSdk 24**. Android Gradle Plugin (AGP) 8.x, Gradle 8.x, Kotlin 2.0+. A Pixel 8 API 35 emulator is the reference device. `apkanalyzer` ships in the Android SDK `cmdline-tools`. No Play Console account needed this week.

## Topics covered

- **ART versus the desktop JVM.** DEX bytecode and why it exists (register-based, designed for constrained devices), the Dalvik → ART history, AOT compilation at install/idle plus JIT plus profile-guided compilation (the `dex2oat` / Baseline Profile story, previewed for Phase 3), and the per-app heap limit.
- **The process model.** Every app is a forked Zygote process; the OS (`system_server`, the ActivityManager) starts, stops, and *kills* your process to reclaim memory. Why "process death" is a first-class scenario you design for, not an edge case.
- **The Android lifecycle.** `Activity` lifecycle callbacks (`onCreate`/`onStart`/`onResume`/`onPause`/`onStop`/`onDestroy`), configuration changes, and `Fragment` as historical context. What Compose replaced (manual view inflation and lifecycle-tied view state) and what it *didn't* (the Activity is still the host).
- **The build pipeline, stage by stage.** Kotlin → JVM bytecode (kotlinc), desugaring (newer Java APIs backported), D8 (dexing) / R8 (shrink + dex), AAPT2 (resource compilation → `resources.arsc` + `R`), manifest merging, packaging into an APK/AAB, zipalign, and APK signature schemes.
- **Gradle Kotlin DSL.** The `plugins { }` block with `alias(libs.plugins.*)`, the `android { }` extension, dependency configurations (`implementation`, `api`, `testImplementation`), and how the AGP injects Android-specific tasks.
- **Version catalogs.** `gradle/libs.versions.toml` — `[versions]`, `[libraries]`, `[plugins]`, `[bundles]` — and the typed `libs.androidx.core` / `libs.plugins.android.application` accessors that replace stringly-typed dependencies.
- **Build variants.** Build types (`debug`/`release`), product flavors (`free`/`pro`), the flavor × build-type variant matrix, flavor-specific source sets (`src/free/`, `src/pro/`), and `BuildConfig` fields per variant.
- **Signing configs.** What an APK signature proves (integrity + a stable developer identity across updates), a debug keystore versus a release keystore, `signingConfigs { }` in the Kotlin DSL, and a conceptual intro to Play App Signing.
- **R8, introduced.** What `minifyEnabled true` turns on: tree-shaking (shrinking), optimization, and obfuscation (renaming). Why reflection-heavy code needs keep rules, and a forward pointer to the full R8/Baseline-Profile treatment in Week 18.
- **AOSP navigation.** What "the framework" is, the `frameworks/base` tree, the boundary between framework code (in `system_server`/your process) and your app, and how to read it on Android Code Search.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                                | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|----------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | ART vs JVM; DEX; the process model; the lifecycle as history          |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | The build pipeline stage by stage; tracing `assembleDebug`            |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Gradle Kotlin DSL; version catalogs; reading a build graph; footguns  |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | Build variants; signing configs; R8 intro; AOSP navigation; challenge |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — `:core` + `:app`, version catalog, two flavors         |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work — signing config; build both variants; inspect |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                           |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                      | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./README.md) | This overview (you are here) |
| [resources.md](./resources.md) | The ART and Dalvik docs, the AGP and build docs, the version-catalog and variant guides, Android Code Search for AOSP, and the canonical build-internals talks |
| [lecture-notes/01-art-runtime-process-lifecycle.md](./lecture-notes/01-art-runtime-process-lifecycle.md) | ART vs the JVM, DEX bytecode, AOT/JIT, the Zygote process model, the lifecycle as history, and a conceptual AOSP tour |
| [lecture-notes/02-gradle-build-pipeline-variants-r8.md](./lecture-notes/02-gradle-build-pipeline-variants-r8.md) | The `assembleDebug` pipeline stage by stage, Gradle Kotlin DSL, version catalogs, build variants, signing configs, and an R8 introduction |
| [exercises/README.md](./exercises/README.md) | Index of the three exercises |
| [exercises/exercise-01-trace-the-build.md](./exercises/exercise-01-trace-the-build.md) | Run `assembleDebug` with `--scan`/`--info`, identify each pipeline stage as a Gradle task, then crack open the APK and find the DEX, resources, and manifest |
| [exercises/exercise-02-version-catalog-and-variants.kt](./exercises/exercise-02-version-catalog-and-variants.kt) | A `build.gradle.kts` + `libs.versions.toml` to complete: wire a version catalog and two flavors so the variant matrix is correct |
| [exercises/exercise-03-signing-and-buildconfig.kt](./exercises/exercise-03-signing-and-buildconfig.kt) | Add a debug signing config and per-flavor `BuildConfig`/manifest placeholders, then verify the signature with `apksigner` |
| [challenges/README.md](./challenges/README.md) | Index of the challenge |
| [challenges/challenge-01-debug-a-broken-build.md](./challenges/challenge-01-debug-a-broken-build.md) | Diagnose and fix four planted build failures (a manifest-merge conflict, a version-catalog typo, a missing R8 keep rule, a duplicate-class dependency clash), tracing each to the right stage |
| [quiz.md](./quiz.md) | 13 questions on ART, DEX, the build pipeline, version catalogs, variants, signing, and R8 |
| [homework.md](./homework.md) | Six practice problems for the week |
| [mini-project/README.md](./mini-project/README.md) | Full spec for the two-module `:core` + `:app` project with version catalogs, `free`/`pro` flavors, and a documented signing config |

## The "you can trace your own build" promise

Every week of Crunch Droid adds one production-grade contract a senior reviewer actually checks. This week's:

> **You must be able to trace any build failure to the exact pipeline stage that produced it.** "The build is red" is not a diagnosis. Kotlin compilation, desugaring, dexing, resource compilation, manifest merge, and signing each fail in their own recognizable way and produce their own error. A senior engineer reads the failing Gradle task name, knows which stage it belongs to, and goes straight to the cause — a duplicate class is a dexing/dependency problem, a `Manifest merger failed` is a manifest problem, a `Missing classes` warning at assembly is R8, a red squiggle in `libs.versions.toml` is the catalog. You will prove this in the challenge by fixing four planted failures, each in a different stage.

You'll prove it by *inspecting the artifact*: `apkanalyzer` (or `unzip`) the APK your build produced and point to the DEX files, the `resources.arsc`, the merged `AndroidManifest.xml`, and the `META-INF` signature block. "It built" is not the test — the test is whether you can open the box and explain every part of what came out.

## A note on what's not here

Week 06 is the *runtime-and-build* week. It deliberately does **not** cover:

- **Writing UI.** No Compose, no XML layouts beyond the template. The lifecycle is treated as history and host-context, not as something you hook into for UI — that's Phase 2, starting Week 07.
- **The full R8 / Baseline Profile treatment.** We *introduce* R8 (what `minifyEnabled` does, why keep rules exist). Macrobenchmark, Baseline Profile generation, full-mode R8, and writing keep rules for reflection-heavy code are **Week 18**.
- **Hilt / multi-module DI.** This week's two-module split (`:core` + `:app`) is about the *build*, not dependency injection. Wiring a multi-module Hilt graph across `:core-network`/`:feature-*` modules is **Week 13**.
- **CI/CD and Play submission.** Signing here uses a debug keystore checked into the repo (for learning). Release keystores, Play App Signing, fastlane, and GitHub Actions are **Weeks 21–22**.

## Up next

Continue to **Week 07 — Jetpack Compose: composition, recomposition, the three phases** once you have built both variants of the mini-project and cracked open an APK to confirm what's inside. Phase 2 begins. Week 07 is where you finally write UI — but every code block there builds with the exact toolchain you set up this week (the Compose BOM and the Compose Compiler plugin are *additions to* the `build.gradle.kts` you can now read fluently), runs on the ART runtime you can now reason about, and lives in the Activity host whose lifecycle you now understand. The foundation phase ends here; you cross into Compose knowing what your code runs on and how it got there.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
