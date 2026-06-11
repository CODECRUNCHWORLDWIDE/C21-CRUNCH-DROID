// Exercise 2 — A version catalog and two flavors
//
// Goal: Wire a Gradle version catalog (libs.versions.toml) and declare free/pro
//       product flavors so the four-variant matrix (freeDebug/freeRelease/
//       proDebug/proRelease) builds. You produce the matrix and prove it with
//       `./gradlew tasks`.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// This file is GRADLE KOTLIN DSL content, not app source. It has two parts:
//   PART A -> goes in app/build.gradle.kts
//   PART B -> goes in gradle/libs.versions.toml  (TOML, shown in a comment block)
// Start from a fresh Empty Activity project and replace/extend the matching files.
//
//   1. Put PART B into gradle/libs.versions.toml.
//   2. Put PART A into app/build.gradle.kts and complete the TODOs.
//   3. Sync, then run `./gradlew tasks --all | grep -i assemble` and confirm all
//      four assemble<Variant> tasks exist.
//   4. Build one: `./gradlew assembleFreeDebug`.
//
// ACCEPTANCE CRITERIA
//
//   [ ] The catalog is used via libs.* / libs.plugins.* accessors (no inline
//       dependency strings in the build script).
//   [ ] free and pro flavors are declared on a "tier" dimension; the matrix has
//       four variants, each with its own assemble task.
//   [ ] free and pro install side by side (different applicationId via suffix).
//   [ ] Builds with 0 warnings; `./gradlew assembleFreeDebug` succeeds.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

// ============================================================================
// PART A — app/build.gradle.kts
// ============================================================================

plugins {
    // TODO 1: reference the AGP application plugin and the Kotlin Android plugin
    //   via the version catalog, e.g. alias(libs.plugins.android.application).
    //   (Replace these two lines with alias(...) calls from the catalog in PART B.)
    // alias(libs.plugins.android.application)
    // alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.crunch.variants"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crunch.variants"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // TODO 2: declare a flavor dimension "tier" and two product flavors:
    //   - free: applicationIdSuffix ".free", a BuildConfig boolean IS_PRO = false
    //   - pro:  applicationIdSuffix ".pro",  a BuildConfig boolean IS_PRO = true
    //
    //   flavorDimensions += "tier"
    //   productFlavors {
    //       create("free") {
    //           dimension = "tier"
    //           applicationIdSuffix = ".free"
    //           buildConfigField("boolean", "IS_PRO", "false")
    //       }
    //       create("pro") { ... }
    //   }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true   // required so buildConfigField generates BuildConfig
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // TODO 3: declare these via the catalog accessors, NOT inline strings:
    //   implementation(libs.androidx.core.ktx)
    //   testImplementation(libs.junit)
}

// ============================================================================
// PART B — gradle/libs.versions.toml  (this is TOML; paste it into that file)
// ============================================================================
//
//  [versions]
//  agp = "8.5.0"
//  kotlin = "2.0.0"
//  coreKtx = "1.13.1"
//  junit = "4.13.2"
//
//  [libraries]
//  androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
//  junit = { group = "junit", name = "junit", version.ref = "junit" }
//
//  [plugins]
//  android-application = { id = "com.android.application", version.ref = "agp" }
//  kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
//
// Accessors: a dash in a name becomes a dot in code, so
//   androidx-core-ktx  ->  libs.androidx.core.ktx
//   android-application -> libs.plugins.android.application
//
// ============================================================================
// PROVE THE MATRIX (write the result into notes/variant-matrix.md):
//
//   ./gradlew tasks --all | grep -i "assemble"
//
//   You should see (among others):
//     assembleFreeDebug, assembleFreeRelease, assembleProDebug, assembleProRelease
//   plus the umbrella assembleDebug / assembleRelease.
//
//   Then build one variant and find its APK:
//     ./gradlew assembleFreeDebug
//     ls app/build/outputs/apk/free/debug/
//
//   Confirm the applicationId got the suffix:
//     apkanalyzer apk summary app/build/outputs/apk/free/debug/app-free-debug.apk
//     -> applicationId should be com.crunch.variants.free
// ============================================================================
// HINTS (read only if stuck > 15 min)
// ============================================================================
//
// - "Unresolved reference: libs" — the catalog file must be exactly
//   gradle/libs.versions.toml (that path is the convention that auto-creates the
//   `libs` accessor). A typo in the path or a TOML syntax error breaks it.
//
// - "buildConfigField was used but buildConfig feature is off" — add
//   buildFeatures { buildConfig = true } (done above). Newer AGP defaults it off.
//
// - Only assembleDebug/assembleRelease appear, no Free/Pro variants — your
//   productFlavors block didn't take. Every flavor needs `dimension = "tier"`
//   matching the declared flavorDimensions, or the sync fails silently-ish.
//
// - free and pro can't both install — you forgot applicationIdSuffix on one, so
//   they share an applicationId. The suffix is what makes them distinct apps.
//
// - The accessor name doesn't resolve — remember dash->dot: `androidx-core-ktx`
//   in TOML is `libs.androidx.core.ktx` in Kotlin. Camel-case in [versions] keys
//   is fine; the dash rule is for [libraries]/[plugins] names.
//
// ============================================================================
