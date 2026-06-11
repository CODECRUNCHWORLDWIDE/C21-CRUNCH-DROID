# Week 06 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 06 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Android Studio Ladybug+, JDK 17, AGP 8.x, Kotlin 2.0+, compileSdk 35, targetSdk 35, minSdk 24. Every build must complete with **0 warnings** for the debug variants.

---

## Problem 1 — Map the whole pipeline to tasks

**Problem statement.** Run `./gradlew clean assembleDebug --info` on any app and capture the log. In `notes/pipeline.md`, list — in execution order — the Gradle tasks for each of the eight pipeline stages from lecture 2 (compile, desugar, dex, resources, manifest merge, package, align, sign), quoting the actual task name from your log and naming the artifact each produces. Where a stage has no distinct task (it's folded into another), say so.

**Acceptance criteria.**

- `notes/pipeline.md` lists the eight stages, each with the real task name (quoted from your log) and the artifact produced.
- Stages that are folded together (e.g. align/sign inside `packageDebug`) are noted as such.
- Committed.

**Hint.** Search the log for `Kotlin`, `Resources`, `Manifest`, `Dex`, `package`. Task names vary by AGP version — quote *yours*, not the lecture's. `--info` (or `--scan`) makes the tasks visible.

**Estimated time.** 40 minutes.

---

## Problem 2 — ART vs JVM, in your own words

**Problem statement.** Write `notes/art-vs-jvm.md` comparing ART to the desktop JVM you used in Weeks 1–5 across five axes: bytecode format, compilation strategy, heap constraints, process lifetime (who kills the process), and the threading model (the main thread / ANR). One short paragraph per axis, in your own words, each ending with "...and this changes how I write Android code because ____."

**Acceptance criteria.**

- Five axes covered, each with a concrete "...changes how I write code because" consequence.
- No copy-paste from the docs; your own phrasing.
- Committed.

**Hint.** The five consequences, roughly: DEX (your `.class` never ships); AOT/JIT/profile (Baseline Profiles later); tiny heap (bitmaps/leaks matter more); OS kills you (persist state, survive process death); one main thread (coroutines off the main thread or ANR). Lecture 1.

**Estimated time.** 40 minutes.

---

## Problem 3 — A version catalog from scratch

**Problem statement.** Take an app whose `build.gradle.kts` uses inline dependency strings (or revert your project to that state) and migrate *every* dependency and plugin into `gradle/libs.versions.toml`. Prove no inline strings remain. Then bump one library's version by changing only the `[versions]` entry and confirm the bump propagates.

**Acceptance criteria.**

- `gradle/libs.versions.toml` with `[versions]`, `[libraries]`, `[plugins]`; the build scripts use only `libs.*` accessors.
- `grep -rn '":' app/build.gradle.kts` (or similar) shows no inline `group:name:version` strings.
- `notes/catalog.md` shows the one-line version bump and confirms the build still resolves.
- 0 warnings. Committed.

**Hint.** Dash-to-dot: `androidx-core-ktx` in TOML → `libs.androidx.core.ktx` in Kotlin. The file path must be exactly `gradle/libs.versions.toml` for the `libs` accessor to auto-generate.

**Estimated time.** 45 minutes.

---

## Problem 4 — Build the variant matrix and prove it

**Problem statement.** Add two product flavors on a `tier` dimension (`free`/`pro`) to an app, each with a distinct `applicationIdSuffix`, an `IS_PRO` `BuildConfig` boolean, and a per-flavor app label via a manifest placeholder. Build all four debug+release variants' assemble tasks (at least list them), build both *debug* APKs, and confirm the `applicationId` and label differ.

**Acceptance criteria.**

- `./gradlew tasks --all | grep assemble` shows the four `assemble<Variant>` tasks; `notes/variants.md` pastes them.
- Both debug APKs built; `apkanalyzer apk summary` confirms `...free` vs `...pro` applicationId.
- The installed label differs per flavor (placeholder substituted in the merged manifest).
- 0 warnings. Committed.

**Hint.** `manifestPlaceholders["appLabel"] = "..."` per flavor + `android:label="${appLabel}"` in the manifest. `buildFeatures { buildConfig = true }` is required for `buildConfigField`. Exercise 2/3 cover the exact syntax.

**Estimated time.** 45 minutes.

---

## Problem 5 — Sign and verify

**Problem statement.** Generate a `debug.keystore` with `keytool`, wire an explicit `debug` signing config in `build.gradle.kts`, build a debug APK, and verify the certificate with `apksigner verify --print-certs`. In `notes/signing.md`, record the certificate details and answer: what does the signature prove, and why is committing a *debug* keystore acceptable while committing a *release* keystore is a security incident?

**Acceptance criteria.**

- An explicit `debug` signingConfig pointing at the generated keystore, used by the debug build type.
- `apksigner verify --print-certs` output (showing CN=Android Debug) pasted into `notes/signing.md`.
- The two written answers (what signing proves; debug-vs-release commit safety).
- 0 warnings. Committed.

**Hint.** The `keytool` command and the `signingConfigs { getByName("debug") { ... } }` block are in exercise 3. `storeFile = file("debug.keystore")` is relative to the module dir.

**Estimated time.** 35 minutes.

---

## Problem 6 — Provoke and fix an R8 keep-rule crash

**Problem statement.** Add a class instantiated by reflection (`Class.forName("...").getDeclaredConstructor().newInstance()`), turn on `isMinifyEnabled = true` for release, and build/run the *release* variant to provoke an R8-caused failure (a build-time `Missing classes` warning or a runtime `ClassNotFoundException`). Then add a `-keep` rule to fix it. Document the before/after in `notes/r8.md`.

**Acceptance criteria.**

- A reproduction: the release build/run fails because R8 removed/renamed the reflectively-used class; debug is unaffected.
- A `-keep` rule in `proguard-rules.pro` fixes it; the release build/run then succeeds.
- `notes/r8.md` explains why R8 couldn't see the class (reflection is invisible to reachability analysis) and why debug didn't reproduce it (debug skips R8).
- 0 warnings on the fixed build. Committed.

**Hint.** `-keep class com.your.pkg.YourClass { *; }`. The signature of an R8 problem is "works in debug, breaks in release." Lecture 2, §7; challenge Failure 3 walks the exact reproduction.

**Estimated time.** 45 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, configuration is idiomatic AGP/Kotlin-DSL, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. an inline dependency string left where the catalog was the point, `api` used where `implementation` belonged). |
| 3 | Works, but misses one criterion (e.g. variants build but the matrix isn't documented; signature verified but the "what it proves" answer is missing or wrong). |
| 2 | Partially works; a core idea is wrong (claims `.class` files run on ART; claims debug builds run R8). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for committing or proposing to commit a *release* keystore (a security incident, even in a teaching exercise); **−2** for confusing `compileSdk`/`targetSdk`/`minSdk` in a way that changes behavior; **−1** for inline dependency strings where the version catalog was the point.

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — the build pipeline (problems 1, 6) and Gradle DSL/catalog/variants (problems 3, 4) — so re-run exercises 01 and 02 before resubmitting.
