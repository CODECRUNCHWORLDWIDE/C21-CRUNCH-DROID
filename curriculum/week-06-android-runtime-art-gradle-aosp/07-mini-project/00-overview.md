# Mini-Project — Two-module Android project: `:core` + `:app`, version catalog, `free`/`pro`

This week you build a **two-module Android project** — a `:core` Android library and an `:app` application module — wired with a **version catalog** (`libs.versions.toml`), **two product flavors** (`free` and `pro`), and a documented **signing config** using a debug keystore checked into the repo. You'll build both flavors, inspect the resulting APKs with `apkanalyzer`, and confirm the variant matrix, the per-flavor `BuildConfig`, and the signature with your own eyes.

The point of the project is not "build an app" — there's barely any UI (one screen showing the flavor name; Compose is next week). The point is to build the **project skeleton** you'll recognize in every serious Android codebase: multi-module, catalog-managed dependencies, flavored variants, and a real signing setup — and then to *open the build's output and explain every part of it*. That "I built it, then I took the APK apart and named the DEX, the resources, the manifest, and the signature" loop is the senior instinct this week installs, and the skeleton itself is the foundation every later week's code drops into.

This is your **first Android project** in the course. It produces real `.apk` files. Everything runs with `./gradlew` and Android Studio; an emulator is optional (you can build and inspect without running).

---

## Where you're starting from

Nothing — this is a fresh project. Create it in Android Studio (**File ▸ New ▸ New Project ▸ Empty Activity**, Kotlin, minSdk 24, package `com.crunch.droid`), which gives you a single `:app` module with a version catalog already wired. You'll *add* the `:core` module, the flavors, and the signing config.

## What you're building toward

By the end you have:

- A `:core` Android **library** module (`com.android.library`) holding a tiny shared API (a `Greeter` that returns a tier-aware string).
- An `:app` **application** module depending on `:core` via `implementation(project(":core"))`.
- A `gradle/libs.versions.toml` version catalog; **zero inline dependency strings** in any build script.
- Two product flavors, `free` and `pro`, on a `tier` dimension, each with its own `applicationIdSuffix`, `BuildConfig` fields, and app label — installable side by side.
- A documented `debug` signing config using a checked-in `debug.keystore`, with a written explanation of why that's acceptable only for a debug key.
- Four built APKs (`freeDebug`, `freeRelease`, `proDebug`, `proRelease`) — at least the two debug ones built and inspected.
- A `README.md` documenting the module graph, the variant matrix, the catalog, and an `apkanalyzer` walkthrough of what's inside one APK.

---

## Milestone 1 — The module graph (≈ 1 h)

Declare both modules in `settings.gradle.kts`:

```kotlin
rootProject.name = "CrunchDroid"
include(":app")
include(":core")
```

Create `:core` as a **library** module (`New ▸ Module ▸ Android Library`, package `com.crunch.droid.core`). Its `core/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)      // a LIBRARY, not an application
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.crunch.droid.core"
    compileSdk = 35
    defaultConfig { minSdk = 24 }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
```

Put a tiny shared API in `core/src/main/java/com/crunch/droid/core/Greeter.kt`:

```kotlin
package com.crunch.droid.core

class Greeter(private val tierName: String) {
    fun greeting(): String = "Welcome to Crunch Droid — $tierName tier"
}
```

Make `:app` depend on it:

```kotlin
// app/build.gradle.kts -> dependencies { }
implementation(project(":core"))
```

Decisions you must be able to defend in review:

- **Why is `:core` a `com.android.library` and not `com.android.application`?** A library produces an `.aar` (reusable code), not an installable APK. Only `:app` is an application. Mixing these up is a classic early mistake.
- **Why `implementation(project(":core"))` and not `api`?** `implementation` keeps `:core` an internal dependency of `:app` and doesn't leak its transitive deps; `:app` is the top of the graph so there's nobody to leak to, but `implementation` is the right default everywhere (lecture 2, §2).
- **Why split into two modules at all this early?** Faster incremental builds, enforced layering (`:core` can't depend on `:app`), and build parallelism. It's a *build* decision this week; the architectural payoff compounds across the track (lecture 2, §5).

## Milestone 2 — The version catalog (≈ 1 h)

Move *every* dependency and plugin into `gradle/libs.versions.toml`. The catalog the Empty Activity template generated is your starting point; extend it so both modules reference only `libs.*` accessors.

```toml
[versions]
agp = "8.5.0"
kotlin = "2.0.0"
coreKtx = "1.13.1"
activityCompose = "1.9.2"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

Acceptance gate for this milestone: `grep -rn '"androidx' app/build.gradle.kts core/build.gradle.kts` returns **nothing** — no inline dependency strings survive. Every dependency goes through the catalog. Document in your README *why* (one-line version bumps, typed accessors, typo-caught-at-config — lecture 2, §3).

## Milestone 3 — The flavors and the variant matrix (≈ 1.5 h)

Add `free`/`pro` flavors to `app/build.gradle.kts`:

```kotlin
android {
    // ...
    defaultConfig {
        // ...
        manifestPlaceholders["appLabel"] = "Crunch Droid"
    }

    flavorDimensions += "tier"
    productFlavors {
        create("free") {
            dimension = "tier"
            applicationIdSuffix = ".free"
            buildConfigField("boolean", "IS_PRO", "false")
            buildConfigField("String", "TIER_NAME", "\"Free\"")
            manifestPlaceholders["appLabel"] = "Crunch Droid Free"
        }
        create("pro") {
            dimension = "tier"
            applicationIdSuffix = ".pro"
            buildConfigField("boolean", "IS_PRO", "true")
            buildConfigField("String", "TIER_NAME", "\"Pro\"")
            manifestPlaceholders["appLabel"] = "Crunch Droid Pro"
        }
    }
    buildFeatures { buildConfig = true }
}
```

Wire the label in `app/src/main/AndroidManifest.xml`: `<application android:label="${appLabel}" ...>`. Then have your one screen (or `MainActivity` log/title) read the flavor through `:core` and `BuildConfig`:

```kotlin
val greeter = Greeter(BuildConfig.TIER_NAME)
val message = greeter.greeting()    // "Welcome to Crunch Droid — Free tier" / "— Pro tier"
```

Confirm the matrix: `./gradlew tasks --all | grep -i assemble` shows `assembleFreeDebug`, `assembleFreeRelease`, `assembleProDebug`, `assembleProRelease`. Build the two debug variants and confirm they install side by side (different `applicationId`).

Stretch the point: put a flavor-specific file in `app/src/free/java/.../Tier.kt` and a different one in `app/src/pro/java/.../Tier.kt`, and observe that each variant compiles only its own — the per-flavor *source set* (lecture 2, §4).

## Milestone 4 — The signing config (≈ 1 h)

Generate a debug keystore and check it in (debug only!):

```
keytool -genkey -v -keystore app/debug.keystore -storepass android \
  -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
  -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

Wire it:

```kotlin
android {
    signingConfigs {
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
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // release signing is Phase 4 — leave it unsigned/default here
        }
    }
}
```

**Document, loudly, in the README:** checking a *debug* keystore into a teaching repo is fine because the debug key has no security value (every Android SDK ships the same one). A *release* keystore must NEVER be committed — a leaked release key lets an attacker sign fraudulent updates to your app, and you can't rotate it without Play App Signing. Explain what the signature *proves* (integrity + stable identity across updates — lecture 2, §6).

## Milestone 5 — Inspect the artifact (≈ 0.5 h)

Build and crack open an APK, the way exercise 1 taught:

```
./gradlew assembleFreeDebug assembleProDebug
apkanalyzer apk summary  app/build/outputs/apk/free/debug/app-free-debug.apk
apkanalyzer files list   app/build/outputs/apk/free/debug/app-free-debug.apk
apkanalyzer dex packages app/build/outputs/apk/free/debug/app-free-debug.apk
apksigner verify --print-certs app/build/outputs/apk/free/debug/app-free-debug.apk
```

In the README, walk through one APK and name: the `classes.dex` (DEX for ART), `resources.arsc` (AAPT2 output), the merged `AndroidManifest.xml` (with your `${appLabel}` substituted), and the debug signature. Confirm the `free` APK's `applicationId` is `com.crunch.droid.free` and `pro`'s is `...pro`.

---

## Acceptance criteria

- [ ] Two modules: `:core` (`com.android.library`) and `:app` (`com.android.application`); `:app` depends on `:core` via `project(":core")`.
- [ ] A `gradle/libs.versions.toml` catalog; **no inline dependency/plugin strings** in any build script (grep returns nothing).
- [ ] `free`/`pro` flavors on a `tier` dimension; the variant matrix has four `assemble<Variant>` tasks; the flavors install side by side (distinct `applicationId`).
- [ ] Per-flavor `BuildConfig` (`IS_PRO`, `TIER_NAME`) read through `:core`'s `Greeter`; the app shows the right tier string per flavor.
- [ ] A documented `debug` signing config with a checked-in `debug.keystore`, and a written explanation of why that's debug-only and what the signature proves.
- [ ] At least `freeDebug` and `proDebug` built; an `apkanalyzer` walkthrough in the README names the DEX, resources, merged manifest, and signature.
- [ ] Build with **0 warnings, 0 errors** for the debug variants.

## Stretch goals

- **Add a third flavor dimension.** Add an `env` dimension (`dev`/`prod`) alongside `tier`, producing 2×2×2 = eight variants. Wire a different `BuildConfig.BASE_URL` per `env`. Watch the matrix grow and note in the README how flavor dimensions multiply (and why you keep the count small).
- **A convention plugin.** Extract the shared `android { compileSdk = 35; minSdk = 24; ... }` config into a `build-logic` included build / convention plugin, so `:core` and `:app` both apply `id("crunchdroid.android.library")` instead of repeating the block. This is the Now-In-Android pattern (resources.md) — and a real time-saver as modules multiply.
- **Inspect the dependency graph.** Run `./gradlew :app:dependencies` and `:app:dependencies --configuration debugRuntimeClasspath`, and document the tree: where `:core` sits, what it pulls in transitively, and how you'd spot a duplicate (the Failure-4 skill from the challenge).
- **Compare debug vs release size.** Build `assembleFreeRelease` (with `isMinifyEnabled = true`) and `assembleFreeDebug`, and compare APK sizes with `apkanalyzer apk file-size`. Note how much R8 shrank — your first taste of the Week 18 payoff.

## What this milestone earns you

You can now stand up the *project skeleton* every serious Android codebase uses — multi-module, catalog-managed, flavored, signed — and *take its output apart* to name every artifact the build produced. That is the literal "skill earned" line for the week: reading `build.gradle.kts` without flinching, tracing a build to the right task, and understanding what runs on ART and what doesn't. Every later week's code — Compose screens, Hilt graphs, Room databases, WorkManager jobs — drops into modules shaped exactly like this. Week 07 begins Phase 2: you'll add the Compose BOM and the Compose Compiler plugin to the `build.gradle.kts` you can now read fluently, and start writing UI that runs on the ART runtime, in the Activity host, that you now understand from the metal up.
