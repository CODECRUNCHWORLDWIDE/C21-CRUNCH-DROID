// Exercise 3 — R8 broke my serialization; fix it WITHOUT disabling R8
//
// Goal: Enable R8 on a release build, watch a reflection-style serialization call
//       produce wrong output (or crash) because R8 renamed your fields, then write
//       the MINIMAL keep rule that fixes it — keeping the optimizer on — and read
//       usage.txt/seeds.txt to confirm. This is lecture 2, §1–2, made concrete.
//
// Estimated time: 45 minutes. A real device or emulator is fine here (this is about
// the RELEASE BUILD behaving, not about timing).
//
// HOW TO USE THIS FILE
//
//   The Kotlin goes in app/src/main. The keep rule goes in app/proguard-rules.pro.
//   The build config (below) enables R8 on release. Then:
//     ./gradlew :app:assembleRelease   (build the minified release)
//     install + run it, exercise the serialization path, observe the bug, fix it.
//
// ACCEPTANCE CRITERIA
//
//   [ ] R8 is enabled on the release build (isMinifyEnabled = true).
//   [ ] You reproduce a release-ONLY failure of the reflection-based path (debug works,
//       release is wrong/crashes) — proving R8 caused it.
//   [ ] You fix it with the NARROWEST keep rule that works (NOT -keep class ** { *; }).
//   [ ] You read app/build/outputs/mapping/release/{usage,seeds,mapping}.txt and can
//       point to evidence the rule took effect.
//   [ ] R8 stays ENABLED. Builds with 0 warnings.
//
// Build config (app/build.gradle.kts):
//   android { buildTypes { release {
//       isMinifyEnabled = true
//       isShrinkResources = true
//       proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
//   } } }
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package com.crunch.reader.config

// We deliberately use a REFLECTION-BASED serializer (Gson) here, because Gson reflects
// on FIELD NAMES at runtime — exactly the thing R8 obfuscation renames. (Note:
// kotlinx-serialization is COMPILE-time and largely R8-safe via its consumer rules;
// Gson is the classic R8 footgun, which is why we use it for the lesson.)

import com.google.gson.Gson

// A config model serialized by reflection. R8 will rename `serverUrl` -> `a`,
// `featureFlags` -> `b`, etc., and Gson will then emit {"a":...,"b":...} — WRONG.
data class RemoteConfig(
    val serverUrl: String,
    val featureFlags: Map<String, Boolean>,
    val maxRetries: Int
)

object ConfigSerializer {
    private val gson = Gson()

    // In DEBUG this round-trips perfectly. In a minified RELEASE build, R8 renames the
    // fields, so toJson produces {"a":"...","b":{...},"c":3} and fromJson can't read a
    // server payload keyed by the REAL names. Same code, different program.
    fun toJson(config: RemoteConfig): String = gson.toJson(config)
    fun fromJson(json: String): RemoteConfig = gson.fromJson(json, RemoteConfig::class.java)
}

// A tiny harness you can call from a screen to SEE the bug in release:
object ConfigDemo {
    fun roundTrip(): String {
        val original = RemoteConfig(
            serverUrl = "https://api.crunch.example",
            featureFlags = mapOf("darkMode" to true),
            maxRetries = 3
        )
        val json = ConfigSerializer.toJson(original)
        // In release WITHOUT a keep rule, this json has obfuscated keys ("a","b","c").
        return json    // print it on screen / log it; compare debug vs release output.
    }
}

// ============================================================================
// THE KEEP RULE  —  goes in app/proguard-rules.pro (NOT in this file).
//
// TODO 1: Reproduce the bug FIRST. Build release, run, observe the obfuscated JSON
//         keys (or a fromJson failure). Confirm debug works and release doesn't.
//         THAT is the proof R8 caused it.
//
// TODO 2: Write the NARROWEST keep rule that fixes it. The minimal fix keeps the
//         field NAMES of just this model (and any model Gson serializes), not the
//         whole world. The right rule is something like:
//
//   # Keep the field names of classes Gson serializes by reflection.
//   -keepclassmembers class com.crunch.reader.config.RemoteConfig {
//       <fields>;
//   }
//
//   (A broader-but-still-targeted alternative: keep all @SerializedName members, or
//   all members of a marker-annotated set of models. AVOID -keep class ** { *; } —
//   that keeps everything and defeats R8's shrinking entirely.)
//
// TODO 3: Rebuild release, confirm the JSON now has the REAL keys ("serverUrl", ...),
//         and verify in the mapping outputs:
//           app/build/outputs/mapping/release/seeds.txt  -> RemoteConfig's fields are SEEDS (kept)
//           app/build/outputs/mapping/release/usage.txt  -> they are NOT listed as removed
//           app/build/outputs/mapping/release/mapping.txt -> the fields are NOT renamed
// ============================================================================
// WHY NOT JUST DISABLE R8 (write your answer before reading):
//
//   Disabling R8 (isMinifyEnabled = false) makes the crash go away by throwing out
//   ALL the shrinking and optimization — a bigger, slower release to avoid writing
//   four lines. The surgical keep rule preserves 99% of R8's win (every OTHER class
//   still shrinks and optimizes) and protects only the handful of reflected members.
//   A senior engineer reads the release crash, finds the reflected name, and keeps
//   the NARROWEST thing. (Lecture 2, §2.)
// ============================================================================
// HINTS (read only if stuck > 15 min)
// ============================================================================
//
// - "Release build works fine, no bug." You may already have Gson's consumer rules,
//   or R8 didn't rename these fields this build. Force the issue: confirm
//   isMinifyEnabled = true, and add a second model that's clearly only used reflectively
//   so R8 has no static reason to keep its names. The point is to SEE the rename, then
//   stop it.
//
// - "I want to fix it for ALL my models, not just one." Mark them with an annotation
//   (e.g. @Keep, or a custom @WireModel) and write ONE rule:
//   -keepclassmembers @com.crunch.reader.config.WireModel class * { <fields>; }
//   Still narrow (only annotated models), still keeps R8 on for everything else.
//
// - "seeds.txt doesn't show my fields." The rule didn't match — check the fully-
//   qualified class name and that you used -keepclassmembers (keep the members) not
//   just -keep (keep the class but maybe not field names). For reflection on field
//   NAMES you need the members kept and NOT obfuscated.
//
// - "kotlinx-serialization didn't break for me." Right — it's compile-time and ships
//   consumer-rules.pro, so it's largely R8-safe out of the box. That's the lesson's
//   contrast: Gson (runtime reflection) needs your help; kotlinx-serialization usually
//   doesn't. Know which of your libraries reflect.
// ============================================================================
