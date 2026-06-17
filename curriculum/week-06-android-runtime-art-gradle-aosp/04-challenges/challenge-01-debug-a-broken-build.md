# Challenge 1 — Debug a broken build (four failures, four stages)

**Time.** 60–120 minutes.
**Deliverable.** A `BUILD-DEBUGGING.md` runbook in your Week 06 repo with four entries — one per planted failure — each recording the failing Gradle task, the pipeline stage it maps to, the root cause, the exact fix, and the rebuilt-green confirmation. Commit the fixed project too.

## The premise

Every Android engineer spends real hours staring at a red build. The ones who are *fast* don't paste the error into a search engine and try random fixes — they read the **failing task name**, map it to a **pipeline stage** (lecture 2, §1), and go straight to the cause. This challenge builds that reflex by making you do it four times, deliberately, across four different stages. You'll plant each failure yourself (so you understand it), reproduce it, trace it, and fix it. A build you can't *diagnose* you can only *flail* at; this challenge is the diagnosis muscle.

## Setup

Start from a working two-module project (your exercise-2 project, or the mini-project skeleton). Confirm it builds green first: `./gradlew assembleFreeDebug`. Then plant the four failures below *one at a time* — fix each before planting the next, so you isolate the signal.

## Failure 1 — Manifest merge conflict (stage 5)

**Plant it.** In `app/src/main/AndroidManifest.xml`, set `android:allowBackup="true"` on `<application>`. Then add a dependency whose manifest sets `allowBackup="false"` — or, to simulate it without hunting for one, add a `<application>` attribute that conflicts with a library you already have, OR add a second `<provider>` with an authority that collides. The simplest reliable plant: add `tools:node="merge"` problems by declaring `android:allowBackup` in `app` and a library forcing the opposite. (If you can't find a conflicting library, declare a duplicate `<activity>` with the same `android:name` twice — the merger rejects it.)

**Trace it.** Build. You get:

```
> Task :app:processDebugMainManifest FAILED
Manifest merger failed : ...
```

Record: the failing task is `:app:processDebugMainManifest` → **stage 5, manifest merge**. You knew it was a manifest problem from the *task name alone*, before reading the message.

**Fix it.** Resolve per the merger's own suggestion — usually `tools:replace="android:allowBackup"` on your `<application>` (your value wins), or remove the duplicate declaration. Rebuild green.

## Failure 2 — Version-catalog typo (before any stage)

**Plant it.** In `gradle/libs.versions.toml`, rename a library entry — change `androidx-core-ktx` to `androidx-core-kts` (a typo), but leave the build script referencing `libs.androidx.core.ktx`.

**Trace it.** Sync/build:

```
Unresolved reference: ktx   (or: could not find accessor libs.androidx.core.ktx)
```

Record: this fails at **configuration time, before any pipeline stage runs** — the catalog accessor doesn't exist, so Gradle can't even configure the build. The "stage" is the catalog itself. This is *why* version catalogs are good: a typo is caught at configuration with a precise message, not as a mysterious failed download mid-build.

**Fix it.** Correct the TOML entry name (dash-to-dot: `androidx-core-ktx` → `libs.androidx.core.ktx`). Rebuild green.

## Failure 3 — Missing R8 keep rule (stage 3, release only)

**Plant it.** Add a class that's instantiated *reflectively* so R8 can't see it's used. The cleanest reproduction: add a data class and use a serialization library (or a manual `Class.forName`) that creates it by name, and turn on `isMinifyEnabled = true` for release. For a self-contained plant without a library, add:

```kotlin
// app/src/main/java/.../ReflectiveThing.kt
package com.crunch.app
class ReflectiveThing { fun greet() = "hi" }

// somewhere reached at runtime:
fun makeIt(): Any = Class.forName("com.crunch.app.ReflectiveThing")
    .getDeclaredConstructor().newInstance()
```

**Trace it.** Build the *release* variant: `./gradlew assembleProRelease`. Depending on R8 config you'll either get a build-time `Missing classes detected while running R8` warning, or — more insidiously — a green build that **crashes only in release** at runtime with `ClassNotFoundException`. Record: the failure is **stage 3, R8** (`:app:minifyReleaseWithR8`), and the tell is "works in debug, breaks in release" — debug doesn't run R8 (lecture 2, §7).

**Fix it.** Add a keep rule to `app/proguard-rules.pro`:

```proguard
-keep class com.crunch.app.ReflectiveThing { *; }
```

Rebuild release green and confirm the class survives (no crash). Note in the runbook: R8 shrank/renamed a class reached only by reflection because reflection is invisible to its reachability analysis.

## Failure 4 — Duplicate-class dependency clash (stage 3, dex)

**Plant it.** Add two dependencies that bring the *same* class — the classic is an old support-library artifact alongside its AndroidX equivalent, or the same library at two coordinates. A reliable plant: add the same library twice at different coordinates/versions that both ship a class, e.g. a JSON library and its repackaged fork, or `org.jetbrains:annotations` at two versions.

**Trace it.** Build:

```
> Task :app:checkDebugDuplicateClasses FAILED   (or a Duplicate class error at dexing)
Duplicate class com.x.Y found in modules ...
```

Record: failing task involves `DuplicateClasses` / dexing → **stage 3**. It *looks* like a code problem but is really a **dependency** problem surfacing at the dex stage — two artifacts on the classpath define the same class, and DEX can't have two.

**Fix it.** Resolve the duplicate: exclude the transitive offender (`implementation("...") { exclude(group = "...", module = "...") }`), or align to a single coordinate/version. Rebuild green. Run `./gradlew :app:dependencies` and note how you'd *find* such a clash by reading the dependency tree.

## Acceptance criteria

- [ ] `BUILD-DEBUGGING.md` has four entries, each with: the failing Gradle task (quoted), the pipeline stage it maps to, the root cause, the exact fix applied, and a "rebuilt green" confirmation.
- [ ] Each failure was reproduced and then fixed; the final project builds `assembleFreeDebug` and `assembleProRelease` green.
- [ ] For Failure 3, you explicitly note the "works in debug, crashes in release" R8 signature and that debug skips R8.
- [ ] For Failure 4, you ran `./gradlew :app:dependencies` and noted how the tree reveals the duplicate.
- [ ] A one-paragraph reflection: the general algorithm — *failing task → pipeline stage → cause → targeted fix* — in your own words.

## What "great" looks like

A weak submission says "I fixed the build." A great submission says:

> Failure 1 failed at `:app:processDebugMainManifest`, which is stage 5 (manifest merge) — I knew it was a manifest conflict from the task name before reading the message; my `allowBackup=true` clashed with a library's `false`, and `tools:replace="android:allowBackup"` resolved it. Failure 3 was the subtle one: the debug build was green but `assembleProRelease` crashed with `ClassNotFoundException`, the textbook R8 signature, because R8 shrank a class reached only via `Class.forName` — invisible to its reachability analysis — and a `-keep` rule fixed it. The pattern across all four: read the failing task, map it to the pipeline stage, and the cause is right there; I never once guessed.

Systematic, stage-aware, and calm. That's the senior build-debugging answer.

## Where this reappears

Literally every week from here. You'll hit manifest merges (every library you add), version-catalog edits (every dependency bump), R8 keep rules (Week 18, and any reflection-using library — kotlinx-serialization, Hilt, Retrofit), and dependency clashes (multi-module Hilt in Week 13). The "failing task → stage → cause" algorithm you drilled here is the single most-used skill of the entire track. Internalize it now and the rest of the course's builds stop scaring you.
