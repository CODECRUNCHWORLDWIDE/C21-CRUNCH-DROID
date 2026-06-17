// Exercise 3 — Signing config and per-flavor BuildConfig
//
// Goal: Add an explicit debug signing config, give each flavor its own BuildConfig
//       fields and a manifest placeholder (the app label), build both flavors, and
//       VERIFY the signature with apksigner. You prove the APK is tied to a key and
//       that flavors produce genuinely different builds.
//
// Estimated time: 40 minutes.
//
// HOW TO USE THIS FILE
//
// This is GRADLE KOTLIN DSL content for app/build.gradle.kts, building on exercise 2.
// You need a debug.keystore. Generate one (or copy ~/.android/debug.keystore):
//
//   keytool -genkey -v -keystore app/debug.keystore -storepass android \
//     -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
//     -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
//
// (Checking a DEBUG keystore into a teaching repo is fine. NEVER check in a RELEASE
//  keystore — a leaked release key is a security incident. See lecture 2, §6.)
//
// ACCEPTANCE CRITERIA
//
//   [ ] An explicit `debug` signingConfig points at app/debug.keystore and is used
//       by the debug build type.
//   [ ] free and pro each define a STRING BuildConfig field (e.g. TIER_NAME) and a
//       manifest placeholder (appLabel) so the installed app name differs per flavor.
//   [ ] `apksigner verify --print-certs` on a built APK shows the debug certificate.
//   [ ] Builds with 0 warnings; assembleFreeDebug and assembleProDebug both succeed.
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

android {
    namespace = "com.crunch.variants"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crunch.variants"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // A default placeholder so the manifest always has a value to substitute.
        manifestPlaceholders["appLabel"] = "Crunch"
    }

    signingConfigs {
        // TODO 1: define a `debug` signing config pointing at app/debug.keystore with
        //   storePassword/keyAlias/keyPassword "android"/"androiddebugkey"/"android".
        //
        //   getByName("debug") {
        //       storeFile = file("debug.keystore")
        //       storePassword = "android"
        //       keyAlias = "androiddebugkey"
        //       keyPassword = "android"
        //   }
    }

    flavorDimensions += "tier"
    productFlavors {
        create("free") {
            dimension = "tier"
            applicationIdSuffix = ".free"
            buildConfigField("boolean", "IS_PRO", "false")
            // TODO 2a: add a STRING BuildConfig field TIER_NAME = "Free" and set the
            //   appLabel manifest placeholder to "Crunch Free".
            //   buildConfigField("String", "TIER_NAME", "\"Free\"")
            //   manifestPlaceholders["appLabel"] = "Crunch Free"
        }
        create("pro") {
            dimension = "tier"
            applicationIdSuffix = ".pro"
            buildConfigField("boolean", "IS_PRO", "true")
            // TODO 2b: TIER_NAME = "Pro", appLabel = "Crunch Pro".
        }
    }

    buildTypes {
        getByName("debug") {
            // TODO 3: use the debug signing config you defined above.
            //   signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// In your AndroidManifest.xml <application>, reference the placeholder so each flavor
// installs under its own name:
//
//   <application android:label="${appLabel}" ... >
//
// And you can read the BuildConfig fields in code:
//
//   if (BuildConfig.IS_PRO) { /* pro-only feature */ }
//   title = "Tier: ${BuildConfig.TIER_NAME}"

// ============================================================================
// PROVE IT (write results into notes/signing.md):
//
//   ./gradlew assembleFreeDebug assembleProDebug
//
//   # Verify the signature — you should see CN=Android Debug:
//   apksigner verify --print-certs app/build/outputs/apk/free/debug/app-free-debug.apk
//
//   # Confirm the flavors differ: the label placeholder and applicationId:
//   apkanalyzer manifest print app/build/outputs/apk/free/debug/app-free-debug.apk | grep -i label
//   apkanalyzer apk summary  app/build/outputs/apk/pro/debug/app-pro-debug.apk
//     -> applicationId com.crunch.variants.pro
//
// Then answer in notes/signing.md: what does the signature PROVE about this APK,
// and why is the same debug key acceptable here but forbidden for a release build?
// (Lecture 2, §6.)
// ============================================================================
// HINTS (read only if stuck > 15 min)
// ============================================================================
//
// - "Keystore file not found" — the storeFile path is relative to the module
//   directory (app/). If your keystore is app/debug.keystore, use file("debug.keystore").
//
// - String BuildConfig fields need ESCAPED quotes: the value is a Java literal, so
//   buildConfigField("String", "TIER_NAME", "\"Free\"") — the inner \" produces a
//   real string in the generated BuildConfig. Forgetting them yields a compile error
//   in the generated file (TIER_NAME = Free, an unresolved symbol).
//
// - The app label doesn't change between flavors — you set android:label to a fixed
//   string instead of "${appLabel}", or you didn't set the placeholder in the flavor.
//   Both the manifest reference AND the per-flavor placeholder are required.
//
// - apksigner shows "DOES NOT VERIFY" — the APK wasn't signed, or you pointed the
//   debug build type at a signing config that doesn't exist. Confirm
//   signingConfig = signingConfigs.getByName("debug") is set on the debug buildType.
//
// - Release build complains about signing — that's expected; you only configured a
//   DEBUG signing config. Release signing is Phase 4. assembleFreeDebug is the target.
//
// ============================================================================
