# Lecture 2 — The build pipeline, Gradle Kotlin DSL, variants, and R8

> "When an Android build fails — and they fail constantly — the difference between a five-minute fix and a lost afternoon is knowing *which stage* failed. The build is not a black box. It's a pipeline, and every stage fails in its own recognizable way."

Lecture 1 gave you the runtime your code runs on. This lecture gives you the build that gets it there: the stage-by-stage pipeline from Kotlin source to a signed APK, the Gradle Kotlin DSL you configure it with, the version catalogs that tame your dependencies, the build variants that ship one codebase as many apps, the signing config that ties an APK to an identity, and a first honest look at R8. By the end you should be able to read a `build.gradle.kts` without flinching and trace any build failure to the exact task that caused it.

---

## 1. Where your APK comes from — the pipeline

Run `./gradlew assembleDebug` and a multi-stage pipeline turns your project into an installable `.apk`. Here are the stages, in order, with the artifact each produces:

```text
your Kotlin/Java
      │  (1) kotlinc / javac
      ▼
JVM bytecode (.class)
      │  (2) desugaring (newer Java APIs backported to old API levels)
      ▼
desugared .class
      │  (3) D8 (debug) / R8 (release: shrink+optimize+dex)
      ▼
DEX (classes.dex)  ◄── this is what ART loads (lecture 1)
                                    
your res/, assets/, AndroidManifest.xml
      │  (4) AAPT2 — compile + link resources
      ▼
resources.arsc + compiled resources + R class
                                    
your manifest + every library's manifest
      │  (5) manifest merger
      ▼
merged AndroidManifest.xml
                                    
      │  (6) package: zip DEX + resources + manifest + native libs into an APK
      ▼
unsigned, unaligned APK
      │  (7) zipalign (align for efficient mmap)
      │  (8) apksigner (sign with a keystore)
      ▼
signed, aligned APK  ◄── installable on a device
```

Read each stage as a thing that can *fail on its own*, because that's the skill:

1. **Kotlin/Java compilation.** `kotlinc` compiles your source to JVM bytecode. Fails with ordinary compile errors (type mismatch, unresolved reference). The failing task is `:app:compileDebugKotlin`.
2. **Desugaring.** Newer Java language/API features (e.g. `java.time` on old API levels) are *backported* so they run on your `minSdk`. Usually invisible; relevant when you use a new API and target an old min.
3. **Dexing (D8/R8).** JVM bytecode → DEX. On debug, **D8** just converts. On release, **R8** also shrinks, optimizes, and obfuscates (§7). The classic failure here is `Duplicate class` — two dependencies bringing the same class — which is a *dependency* problem surfacing at the dex stage. Task: `:app:dexBuilderDebug` / `:app:minifyReleaseWithR8`.
4. **Resource compilation (AAPT2).** Your `res/` XML and assets become a binary `resources.arsc` and a generated `R` class of integer IDs. Fails on a malformed XML, a missing resource, or a bad reference. Task: `:app:processDebugResources`.
5. **Manifest merge.** Your `AndroidManifest.xml` is merged with the manifest of *every library you depend on*. Conflicts (two libraries demanding incompatible `minSdk`, clashing `<provider>` authorities, attribute conflicts) fail with `Manifest merger failed`. Task: `:app:processDebugMainManifest`.
6. **Packaging.** Everything is zipped into an APK (or an AAB — Android App Bundle — for Play).
7. **zipalign.** Aligns the zip entries so the OS can `mmap` them efficiently. Automatic.
8. **Signing.** `apksigner` signs the APK with a keystore (§6). An unsigned APK won't install. Task: `:app:packageDebug` (signing is part of packaging).

**The promise this enables:** when the build is red, read the *failing task name*, map it to a stage, and go straight to the cause. `Manifest merger failed` → stage 5, a manifest conflict. `Duplicate class found` → stage 3, a dependency clash. `Missing classes detected while running R8` → stage 3 release, a missing keep rule. A red squiggle in `libs.versions.toml` → the catalog, before any stage runs. The challenge has you fix four such failures, one per stage.

---

## 2. Gradle Kotlin DSL — reading `build.gradle.kts`

An Android module's build script is Kotlin (the `.kts` Kotlin DSL), not Groovy. The anatomy of a typical `app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)   // the AGP — makes this an Android app module
    alias(libs.plugins.kotlin.android)        // Kotlin support for Android
}

android {
    namespace = "com.crunch.app"
    compileSdk = 35                           // the API level you COMPILE against

    defaultConfig {
        applicationId = "com.crunch.app"      // the unique package id on the device/Play
        minSdk = 24                           // the OLDEST API level you support
        targetSdk = 35                        // the API level you've TESTED against (behavior opt-in)
        versionCode = 1                       // integer, must increase every Play upload
        versionName = "1.0"                   // human-readable
    }

    buildTypes {
        release {
            isMinifyEnabled = true            // turn on R8 (§7)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)    // available to THIS module only
    implementation(libs.androidx.activity.compose)
    testImplementation(libs.junit)            // only on the test classpath
}
```

The three things to internalize:

- **`compileSdk` vs `targetSdk` vs `minSdk`.** `compileSdk` is which APIs you can *call*. `minSdk` is the oldest device you *run on*. `targetSdk` is which behavior changes you've *opted into* (Android gates new restrictions behind `targetSdk` so old apps don't break). Mixing these up is the single most common Android build confusion.
- **`implementation` vs `api`.** `implementation` makes a dependency available to *this* module only — consumers of your module don't see it (faster builds, better encapsulation). `api` *leaks* the dependency to consumers (use it only when your module's public API exposes that dependency's types). Default to `implementation`.
- **`alias(libs.plugins.*)` and `libs.*`** — these come from the *version catalog* (§3). The build script names dependencies by typed accessors, not strings.

One more thing worth knowing about Gradle itself, because it explains build behavior that otherwise looks like magic: Gradle runs in **two phases**. First the **configuration phase** evaluates every `build.gradle.kts` to build the *task graph* (this is when `android { }` blocks run and your catalog accessors resolve — a typo here fails *before* any compilation). Then the **execution phase** runs the tasks the graph says are needed, in dependency order, skipping any whose inputs haven't changed (Gradle's **incremental build** and **build cache**). That's why a no-op build is near-instant (nothing's inputs changed, every task is `UP-TO-DATE`) and why a catalog typo fails at "configuration," a stage *before* `compileDebugKotlin` even appears. Knowing "configuration builds the graph, execution runs it" is what lets you read Gradle output without confusion.

---

## 3. Version catalogs — `libs.versions.toml`

Hardcoding `"androidx.core:core-ktx:1.13.1"` strings across a dozen module build files is how versions drift and typos hide. A **version catalog** centralizes them in one typed file, `gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.5.0"
kotlin = "2.0.0"
coreKtx = "1.13.1"
composeBom = "2024.09.00"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }

[bundles]
# a named group of libraries you can pull in with one accessor
compose-core = ["androidx-core-ktx"]
```

Then in any build script, `libs.androidx.core.ktx` and `libs.plugins.android.application` are **typed accessors** — your IDE autocompletes them, navigates to the definition, and flags a typo at configuration time instead of failing a download later. The `version.ref` indirection means a Kotlin or AGP bump is a *one-line* change in `[versions]` that every module picks up. `[bundles]` group related libraries so `implementation(libs.bundles.composeCore)` pulls a whole set.

This is not optional polish — it is how every serious multi-module Android project (Now-In-Android, tivi) manages dependencies, and the mini-project requires it. The senior habit: dependencies live in the catalog, never as inline strings.

---

## 4. Build variants — one codebase, many apps

A real app often ships in flavors: a `free` version and a `pro` version, or `dev`/`staging`/`prod` backends. Android builds this in via two orthogonal axes:

- **Build types** — `debug` and `release` (you can add more). Differ in debuggability, signing, and whether R8 runs.
- **Product flavors** — your dimensions, e.g. `free` and `pro`.

The two axes multiply into a **variant matrix**:

```kotlin
android {
    flavorDimensions += "tier"
    productFlavors {
        create("free") {
            dimension = "tier"
            applicationIdSuffix = ".free"            // app id becomes com.crunch.app.free
            buildConfigField("boolean", "IS_PRO", "false")
        }
        create("pro") {
            dimension = "tier"
            applicationIdSuffix = ".pro"
            buildConfigField("boolean", "IS_PRO", "true")
        }
    }
}
```

`free` × {`debug`, `release`} and `pro` × {`debug`, `release`} = **four variants**: `freeDebug`, `freeRelease`, `proDebug`, `proRelease`. Each gets its own assemble task (`./gradlew assembleFreeDebug`).

What flavors give you:

- **`BuildConfig` fields.** `buildConfigField("boolean", "IS_PRO", "true")` generates `BuildConfig.IS_PRO` you can branch on in code — gate a feature behind `if (BuildConfig.IS_PRO)`.
- **Flavor-specific source sets.** Code/resources in `src/free/` and `src/pro/` are compiled *only* into that flavor. You can provide a different `PaywallScreen.kt` in `src/free/` than in `src/pro/` — the build picks the right one per variant.
- **Flavor-specific dependencies.** `freeImplementation(...)` adds a dependency only to the `free` variants (e.g. an ads SDK only in `free`).
- **Different `applicationId`.** The suffix lets `free` and `pro` install side by side on one device.

The flavor-specific source set is worth seeing concretely, because it's how you ship genuinely different code per flavor without `if (BuildConfig.IS_PRO)` littered everywhere. The directory layout drives it:

```text
app/src/
  main/java/com/crunch/app/    ← compiled into EVERY variant
  free/java/com/crunch/app/    ← compiled ONLY into free* variants
    Tier.kt                     →  object Tier { const val name = "Free"; fun showsAds() = true }
  pro/java/com/crunch/app/     ← compiled ONLY into pro* variants
    Tier.kt                     →  object Tier { const val name = "Pro";  fun showsAds() = false }
```

There's a `Tier` in *both* `free/` and `pro/` (never in `main/` — that would clash), and the build compiles exactly one into each variant. Your `main/` code calls `Tier.showsAds()` and gets the flavor-appropriate answer with no runtime branch — the *build* chose the implementation. This is the clean way to vary behavior per flavor: a shared interface or object name in `main`-callable code, distinct implementations in the flavor source sets. The trap to avoid: don't put a `Tier` in `main/` *and* a flavor — the flavor file must *replace*, not duplicate, so the symbol lives only in the flavor source sets.

The mini-project builds exactly this `free`/`pro` split so you see the matrix, the per-flavor `BuildConfig`, the flavor source sets, and the side-by-side install for real.

---

## 5. The two-module split — `:core` and `:app`

A `settings.gradle.kts` declares the modules:

```kotlin
rootProject.name = "CrunchDroid"
include(":app")
include(":core")
```

`:core` is an **Android library** module (`com.android.library` plugin) — it produces an `.aar`, not an installable app, and holds shared code (models, utilities, a repository interface). `:app` is the **application** module (`com.android.application`) that depends on it:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":core"))   // a project dependency, not an external artifact
}
```

Why split at all, this early? Three reasons, all about the *build* (not DI — that's Week 13): **faster incremental builds** (changing `:app` doesn't recompile `:core`), **enforced boundaries** (`:core` can't accidentally depend on `:app`'s UI), and **parallelism** (Gradle builds independent modules concurrently). This week the split is a *build* lesson; the architectural payoff (clean layering, testability) compounds across the whole track.

A subtlety that earns its keep as modules multiply: the `implementation` vs `api` choice (§2) is *load-bearing* across module boundaries. If `:core` declares a dependency with `implementation`, that dependency is invisible to `:app` — `:app` sees `:core`'s own types but not `:core`'s dependencies' types. That's usually what you want (encapsulation, smaller compile classpaths, faster builds). You use `api` only when `:core`'s *public signatures* expose another library's types — e.g. if a `:core` function returns a `kotlinx.datetime.Instant`, then `:core` must declare `kotlinx-datetime` as `api` so `:app` can name that return type. Get this wrong in the wrong direction and either `:app` can't compile (a leaked type wasn't `api`-exposed) or your build is slower and your boundaries leakier than they should be (everything `api`-ed). The discipline — `implementation` by default, `api` only for genuinely public-API types — is what keeps a ten-module build fast and its layers honest. You'll feel it for real in Week 13's multi-module Hilt graph.

---

## 6. Signing — what it proves

Every APK installed on a device is **signed** with a cryptographic key. The signature proves two things:

1. **Integrity** — the APK hasn't been tampered with since it was signed.
2. **A stable identity across updates** — Android will only install an update if it's signed with the *same* key as the installed version. This is what stops a malicious actor from pushing a fake "update" to your app: they don't have your key.

A **debug keystore** is auto-generated by the SDK (`~/.android/debug.keystore`) and used for `debug` builds — it's fine for development, never for release. A **release keystore** is one you generate and guard with your life (lose it and you can never update your app under the same identity — Play App Signing mitigates this, Phase 4).

In the Kotlin DSL:

```kotlin
android {
    signingConfigs {
        // For LEARNING this week we check a debug keystore into the repo. NEVER do this
        // with a real release keystore — a leaked release key is a security incident.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
```

Verify a signature on the output with `apksigner verify --print-certs app-debug.apk` — you'll see the debug certificate. The mini-project documents this debug-keystore-in-repo setup *and* explains loudly why it's only acceptable for a debug key in a teaching repo. **Play App Signing** (conceptually): you upload an *upload key*-signed AAB, Google re-signs it with the *app signing key* it holds, so even if your upload key leaks, the app identity is safe. Full treatment Phase 4.

One signing concept to lodge now, because it bites people in production: the signature schemes have versions. **v1** (the old JAR-style `META-INF` per-file signing), **v2/v3** (whole-archive signing, faster verification and tamper-evidence over the entire APK), and **v4** (incremental, for streamed installs). Modern AGP applies v2+ automatically, and `apksigner verify --print-certs` reads the certificate regardless of scheme — which is why, in exercise 1, you won't always *see* a `META-INF` signature block by unzipping (v2+ doesn't put it there) but `apksigner` still prints the cert. The takeaway: trust `apksigner verify` over manual archive inspection, and know that "I don't see a `META-INF` signature" doesn't mean unsigned — it means v2+. The full release-signing and Play App Signing flow is Phase 4; this is the conceptual hook so the exercise's output doesn't surprise you.

---

## 7. R8 — introduced

On a **release** build with `isMinifyEnabled = true`, **R8** runs (replacing the old ProGuard). R8 does four things in one pass:

1. **Shrinking (tree-shaking).** Removes classes, methods, and fields that are never reached from your entry points. Your APK gets dramatically smaller — unused library code is stripped.
2. **Optimization.** Inlines methods, removes dead branches, merges classes — makes the code faster and smaller.
3. **Obfuscation.** Renames classes/methods/fields to short meaningless names (`a`, `b`, `c`) — smaller, and a mild deterrent to reverse engineering.
4. **Dexing.** It also produces the DEX (it's the release-build dexer, replacing D8).

The catch — and the reason R8 frustrates people — is **reflection**. R8 reasons about what's *statically reachable*. Code reached by *reflection* is invisible to its reachability analysis, so R8 may **shrink it away or rename it**, and then your app crashes at runtime. The reflection patterns that bite:

- `Class.forName("com.x.Y")` — a string-named class R8 can't see is referenced.
- A serializer (Gson, Moshi, kotlinx-serialization) instantiating a model class by name, or reading its field names — R8 may rename the fields the JSON keys must match.
- Anything resolving a type or member by string at runtime — `Class.getMethod("...")`, reflective DI.

In each case R8 sees no static reference and concludes the code is dead, so it strips or renames it — and you get a release-only `ClassNotFoundException`, `NoSuchMethodError`, or a serialization mismatch.

The fix is a **keep rule** in `proguard-rules.pro` telling R8 "don't touch this":

```proguard
# Keep a class R8 can't see is used (e.g. instantiated reflectively by a serializer)
-keep class com.crunch.app.model.** { *; }
```

This week you only need to *recognize* the pattern: a release-only crash that doesn't happen in debug is very often R8 shrinking or renaming something reached by reflection, and the fix is a keep rule. The classic signal is `Missing classes detected while running R8` at assembly, or a `ClassNotFoundException`/serialization error that's release-only. The **full** treatment — full-mode R8, writing precise keep rules, keeping reflection-heavy libraries working, and measuring the size/perf win — is **Week 18**, alongside Baseline Profiles. For now: debug builds don't run R8; release builds do; and "works in debug, crashes in release" is R8 until proven otherwise.

A nuance worth carrying: most well-behaved libraries **ship their own keep rules** as "consumer ProGuard rules" bundled in their `.aar`, so you don't hand-write rules for Retrofit, Hilt, Room, or kotlinx-serialization — they bring the rules R8 needs. You write keep rules mainly for *your own* reflectively-accessed classes, or for an older library that forgot to ship them. So the practical posture isn't "write keep rules everywhere" (that defeats R8's whole purpose — every kept class is a class that *can't* be shrunk or renamed); it's "let R8 do its job, and write the *minimum* keep rule for the specific thing it can't see." Over-keeping is as much a code smell as under-keeping — it bloats the APK and weakens obfuscation. The discipline (Week 18) is precision: keep exactly what reflection touches, nothing more.

---

## 8. A worked diagnosis — a red build, traced to its stage

Let's run the skill once, the way you would on a real ticket: the build is red, and you need the cause, not a guess.

You run `./gradlew assembleDebug` and see:

```text
> Task :app:processDebugMainManifest FAILED
Manifest merger failed : Attribute application@allowBackup value=(true)
  from AndroidManifest.xml is also present at [com.somelib:lib:1.2] AndroidManifest.xml
  value=(false). Suggestion: add 'tools:replace="android:allowBackup"' ...
```

**Step 1 — read the task name.** `:app:processDebugMainManifest`. From §1, that's **stage 5, the manifest merge**. You don't even need to read the message yet — you know it's a manifest conflict, not a Kotlin error, not R8, not a dependency dex clash.

**Step 2 — read the conflict.** Your manifest says `allowBackup=true`; a library's manifest says `false`. The merger can't pick. It even suggests the fix.

**Step 3 — apply the right fix.** Tell the merger your value wins:

```xml
<application
    android:allowBackup="true"
    tools:replace="android:allowBackup">
```

**Step 4 — rebuild and confirm.** Green. Total time: a minute, because you mapped the task to the stage instead of pasting the message into a search engine and trying random fixes.

That is the entire senior loop for builds: **failing task → pipeline stage → cause → targeted fix.** A `Duplicate class` would have been stage 3 (a dependency bringing a class twice — fix by excluding one or aligning versions); a `processDebugResources` failure would have been stage 4 (a bad resource); a `compileDebugKotlin` failure is just a code error. The pipeline diagram in §1 is the map you read every failing build against.

---

## 9. Recap

Lecture 1 was the runtime; this lecture was the build that feeds it. Three habits carry it:

1. **The build is a pipeline, not a black box.** Kotlin → bytecode → desugar → dex (D8/R8) → resources (AAPT2) → manifest merge → package → align → sign. Each stage fails recognizably; the failing *task name* tells you the stage.
2. **Configure it with the Kotlin DSL and a version catalog.** `build.gradle.kts` with `alias(libs.plugins.*)`, `implementation(libs.*)`, and a `libs.versions.toml` that centralizes every version. No inline dependency strings; `implementation` over `api` by default.
3. **Variants, signing, and R8 are levers you understand, not magic.** Flavors × build types make the variant matrix; signing ties the APK to a stable identity; R8 shrinks/optimizes/obfuscates release builds and needs keep rules for reflection. "Works in debug, crashes in release" is R8 until proven otherwise.

You now have both halves of Phase 1's finale: the runtime your code executes on (ART, the process, the lifecycle) and the build that produces it (the pipeline, Gradle, variants, signing, R8). The exercises have you trace a real build and crack open the APK, wire a version catalog and flavors, and add a signing config. The challenge plants four failures across four stages and makes you fix each. The mini-project builds the two-module, two-flavor, signed project you'll recognize in every real Android codebase. Go build something you can take apart — then take it apart, and explain every piece.
