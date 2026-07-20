# Week 06 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Trace the build](exercise-01-trace-the-build.md)** — run `assembleDebug` with `--info`/`--scan`, map each Gradle task to a pipeline stage from lecture 2, then crack open the resulting APK with `apkanalyzer` and find the DEX, the `resources.arsc`, the merged manifest, and the signature. The whole "where your APK comes from" lecture, made concrete in one build. (~45 min)
2. **[Exercise 2 — A version catalog and two flavors](exercise-02-version-catalog-and-variants.kt)** — complete a `build.gradle.kts` and a `libs.versions.toml`: wire the version catalog, declare `free`/`pro` flavors, and make the four-variant matrix build. You produce the variant matrix and prove it with `./gradlew tasks`. (~50 min)
3. **[Exercise 3 — Signing and BuildConfig](exercise-03-signing-and-buildconfig.kt)** — add a debug signing config, add per-flavor `BuildConfig` fields and a manifest placeholder, build both flavors, and verify the signature with `apksigner verify --print-certs`. (~40 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the build files yourself.** Do not copy-paste. The point is fluency in `build.gradle.kts`, which only comes from typing it.
- Run on the **Android toolchain** set up in the README: Android Studio Ladybug+, JDK 17, compileSdk 35, a Pixel 8 API 35 emulator. These exercises produce real APKs.
- The `.kt` exercises are **`build.gradle.kts` and `libs.versions.toml` content** (Gradle Kotlin DSL), not app source. Drop them into the matching files of a fresh Empty Activity project.
- Keep `apkanalyzer` (SDK `cmdline-tools`) and `apksigner` (SDK `build-tools`) on your `PATH` — exercises 1 and 3 use them.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. "It built" is not enough this week — you must be able to point inside the artifact and name each part.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-06` to compare.
