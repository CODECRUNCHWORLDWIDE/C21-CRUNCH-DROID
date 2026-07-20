# Exercise 1 — Trace the build, then crack open the APK

**Goal.** Run `./gradlew assembleDebug` on a fresh app, watch the pipeline run as Gradle tasks, map each task to a stage from lecture 2, then open the resulting APK with `apkanalyzer` and *find with your own eyes* the DEX, the compiled resources, the merged manifest, and the signature. If you can point inside the artifact and name every part, you understand where your APK comes from.

**Estimated time.** 45 minutes.

**Prerequisites.** Android Studio Ladybug+, JDK 17, the Android SDK with `apkanalyzer` and `apksigner` on your `PATH` (they're in the SDK `cmdline-tools` and `build-tools`). No emulator needed — we only build and inspect.

---

## Step 1 — Scaffold a fresh app

In Android Studio: **File ▸ New ▸ New Project ▸ Empty Activity.** Name it `BuildTrace`, package `com.crunch.buildtrace`, language **Kotlin**, minSdk **24**. Let it sync. Confirm `./gradlew` works from the project root:

```
./gradlew --version
```

## Step 2 — Run the build with the pipeline visible

Run the debug assemble with info logging so you see the tasks:

```
./gradlew assembleDebug --info 2>&1 | tee build.log
```

(Or use `--scan` for a hosted, browsable build report — it prints a URL. Either works.)

In `build.log`, find these task names and **map each to a pipeline stage** from lecture 2, §1. Write your mapping into `notes/build-stages.md`:

| Gradle task (find it in the log) | Pipeline stage |
|---|---|
| `:app:compileDebugKotlin` | ? |
| `:app:processDebugResources` | ? |
| `:app:processDebugMainManifest` (or `:app:processDebugManifest`) | ? |
| `:app:dexBuilderDebug` (or `mergeDexDebug`) | ? |
| `:app:packageDebug` | ? |

For each, write one sentence: *what artifact does this stage produce?* (e.g. "compileDebugKotlin produces JVM bytecode `.class` files.")

## Step 3 — Locate the output APK

The debug APK lands at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Confirm it exists:

```
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

## Step 4 — Crack it open with `apkanalyzer`

Now look *inside* the artifact. Run each of these and record what you see in `notes/apk-anatomy.md`:

```
# High-level summary: applicationId, versionCode, versionName, minSdk, targetSdk
apkanalyzer apk summary app/build/outputs/apk/debug/app-debug.apk

# The DEX — the bytecode ART actually loads (lecture 1). List the packages it contains.
apkanalyzer dex packages app/build/outputs/apk/debug/app-debug.apk

# The file tree inside the APK — find classes.dex, resources.arsc, AndroidManifest.xml, META-INF/
apkanalyzer files list app/build/outputs/apk/debug/app-debug.apk

# The MERGED manifest, as it ended up after stage 5
apkanalyzer manifest print app/build/outputs/apk/debug/app-debug.apk
```

(If you don't have `apkanalyzer`, an APK is just a zip: `unzip -l app-debug.apk` lists the same entries. Use that as a fallback.)

In `notes/apk-anatomy.md`, point to and name:

- **`classes.dex`** — "this is the DEX bytecode ART loads, produced by D8 at stage 3."
- **`resources.arsc`** — "the compiled binary resource table, produced by AAPT2 at stage 4."
- **`AndroidManifest.xml`** (the binary one in the APK) — "the merged manifest from stage 5."
- **`META-INF/` (e.g. `*.RSA`/`*.SF`/`MANIFEST.MF` or the v2/v3 signature block)** — "the signature from stage 8."

## Step 5 — Verify the signature

```
apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk
```

You'll see the **debug certificate** (CN=Android Debug). Record it. Note in `notes/apk-anatomy.md`: *which keystore signed this, and why is that fine for a debug build but never for release?* (Lecture 2, §6.)

## Step 6 — Connect it back to the runtime

One sentence in `notes/apk-anatomy.md`: *when this APK is installed and run, what does ART do with `classes.dex`, and when might `dex2oat` AOT-compile parts of it?* (Lecture 1, §2.)

---

## Acceptance criteria

- [ ] `notes/build-stages.md` maps all five tasks to their pipeline stages with the artifact each produces.
- [ ] `notes/apk-anatomy.md` points to `classes.dex`, `resources.arsc`, the merged `AndroidManifest.xml`, and the signature block — each named with the stage that produced it.
- [ ] You ran `apksigner verify --print-certs` and recorded the debug certificate, and explained why a debug key is fine here but not for release.
- [ ] One sentence connecting `classes.dex` back to ART and `dex2oat`.
- [ ] The build completed with **0 errors**.

## What you just proved

You proved lecture 2's central claim with your eyes: the build is a *pipeline* of stages, each a Gradle task producing a named artifact, and the APK at the end is a zip you can open and explain entirely — DEX from dexing, `resources.arsc` from AAPT2, a merged manifest, and a signature. You also closed the loop to lecture 1: the `classes.dex` you found is exactly what ART loads and `dex2oat` may compile. The build is no longer a black box.

---

## Hints (read only if stuck > 10 min)

- **`apkanalyzer` not found.** It's in `$ANDROID_HOME/cmdline-tools/latest/bin/`. Add that to your `PATH`, or use the full path, or fall back to `unzip -l app-debug.apk` to list entries.
- **No `META-INF` signature visible / only a v2 block.** Modern APKs use the v2/v3 signature scheme, which signs the whole archive rather than per-file `META-INF` entries. `apksigner verify --print-certs` is the reliable way to see the certificate regardless of scheme.
- **`processDebugMainManifest` isn't in the log.** Task names vary slightly by AGP version (`processDebugManifest`, `processDebugMainManifest`). Search the log for `Manifest` — whatever task has it is your stage-5 task.
- **`dexBuilderDebug` missing.** On some AGP versions debug dexing shows as `mergeDexDebug` or is folded into `mergeProjectDexDebug`. Any task with `Dex` in the name is stage 3.
- **The build is too fast to see tasks.** Run `./gradlew clean assembleDebug --info` so nothing is cached and every task actually executes.
