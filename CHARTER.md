# C21 · Crunch Droid — Charter

This is the design document for Crunch Droid. It explains *why* the curriculum is shaped the way it is, *why* the topic order is what it is, and *why* the open-source-first stance is non-negotiable. The Crunch Labs Charter at [`../CRUNCH-LABS-CHARTER.md`](../CRUNCH-LABS-CHARTER.md) governs the family; this document governs the track.

---

## Why Android as a discipline

Android is the largest mobile platform on Earth by installed base. As of the writing of this charter, more than three billion active devices run some variant of the Android Open Source Project. The platform spans phones costing thirty dollars in Lagos and folding flagships costing two thousand dollars in Seoul. The same APK, with adaptive layout, can run on both.

That breadth is the engineering opportunity. It is also the engineering tax. Android engineers reason about hardware diversity, OEM customization, OS-version fragmentation, doze-mode constraints, foreground-service taxonomy, exact-alarm permissions, and per-vendor battery optimization quirks — all on top of the actual product work. No 12-week track can responsibly cover this surface. That is why Crunch Droid is a 24-week semester intensive.

Equally important: Android is open source. The platform is AOSP. The language is Kotlin (JetBrains, Apache-2.0). The compiler toolchain is open. The build system is open. The JVM at runtime is OpenJDK. An engineer who learns Android with an AOSP-aware mental model — who can read `frameworks/base/` when the docs are insufficient — graduates with a different ceiling than one who learned "Android Studio + tutorials." This track teaches the AOSP-aware version on purpose.

---

## Why 24 weeks

Three subjects make this track unavoidably long:

1. **Kotlin Coroutines and Flow.** Structured concurrency is the foundation of every other modern Android API. We do not introduce it in passing — we spend two weeks on it before we touch Compose. Compose state without a sound coroutines mental model is impossible to reason about. ViewModels without it are easy to break.

2. **Jetpack Compose at the recomposition level.** Tutorials teach Compose as "declarative UI with `@Composable` annotations." That is enough to ship a hobby app. It is not enough to ship a 60-fps production app on a four-year-old budget device in São Paulo. We teach the three-phase composition model, stability, skippability, snapshot state, and side-effect APIs deliberately. Compose takes six weeks because it is six weeks deep.

3. **Production constraints unique to mobile.** Doze. App Standby. Exact alarms after Android 12. Foreground service types after Android 14. The permission model after Android 13. Scoped storage after Android 11. Play Integrity replacing SafetyNet. Each of these has bricked production teams who learned Android from a 2018 tutorial. We teach the current regime and the history of why it changed.

A 15-week track that tries to cover this is either a survey course (insufficient depth on Compose) or a Compose course (insufficient depth on production). A 24-week semester gives us room to do both, plus multi-form-factor (Wear OS, foldables), plus a real capstone with a chaos drill, plus interview prep.

---

## Topic ordering: why Kotlin first, Compose second

Most external curricula introduce Compose in Week 1 alongside Kotlin syntax. We do not. The reason is concrete: Compose state is built on `Snapshot`, which composes with `Flow`, which composes with structured concurrency. A learner who has never thought hard about cancellation, dispatchers, or hot-versus-cold streams cannot reason about why a `LaunchedEffect` with the wrong key recomposes incorrectly. They will write code that works in development and breaks under process death.

So we spend six weeks on the foundations — Kotlin 2.x, then coroutines, then Flow, then the Android runtime and Gradle — before we draw a single Composable. By Week 7 every learner can answer: *what is suspending, what is canceling, what dispatcher is this on, why is this Flow cold.* From that footing, Compose lectures land.

The other deliberate order choice: dependency injection, persistence, and networking come **after** Compose, not before. This is contrary to some industry curricula that teach DI first because "professional projects use it." We teach Compose first because a learner who understands state hoisting and unidirectional flow will architect their DI graph well; a learner who learned Hilt before they understood state will inject things into Composables that should not be injected there. Architecture is downstream of state, not upstream.

---

## Open-source-first stance

Android is the most open-source platform in mainstream mobile computing. We refuse to teach it as if it were a proprietary platform. Specifically:

- **Kotlin** is open source under Apache-2.0. We use it from day one without apology.
- **AOSP** is open source. When the official documentation is ambiguous, we read the AOSP source. Learners who do this become senior faster than learners who do not.
- **Push notifications** are taught with Firebase Cloud Messaging (the de facto standard) and with **UnifiedPush** (an open-source alternative). Learners who graduate Crunch Droid know both paths. UnifiedPush enables distribution to F-Droid and to users without Google Play Services.
- **Distribution** is taught with the Play Store (the largest channel) and with **F-Droid** (an open-source alternative). The capstone has an F-Droid submission path for learners who choose not to pay the Play developer fee. Both paths satisfy track completion.
- **Maps and location** are taught with Google Maps SDK and with **MapLibre** and OpenStreetMap. Vendor lock-in on maps is one of the most common technical-debt traps in mobile; learners who graduate know the way out.
- **Analytics** are taught with Firebase Analytics and with self-hosted alternatives (Matomo on Android). The capstone uses one of each.

This is not anti-vendor. We teach Firebase. We teach Google Play. We teach Android Studio. We just refuse to teach them as if the open-source equivalent does not exist, because an engineer in a market where Google services are unavailable or unreliable still needs to ship.

---

## Relationship to C20 (Swift)

C20 · Crunch Swift is the sibling track. They are deliberately symmetric:

- Both are 24-week semester intensives.
- Both teach a single mobile platform with full depth.
- Both end in a multi-form-factor capstone (Wear OS for Droid, watchOS for Swift).
- Both have a Kotlin Multiplatform / Swift Concurrency story for cross-platform code sharing.

Learners who take both — typically in sequence — finish with a senior cross-platform mobile profile. They are uniquely valuable on small teams that ship to both stores. We have a deliberate pathway recommendation for this: **C1 → C20 → C21**, ending with a KMP capstone whose shared core compiles to both Android and iOS.

---

## Relationship to C5 / C23 (on-device ML on Android)

On-device machine learning on Android — TensorFlow Lite, MediaPipe Tasks, ML Kit — is a major specialization surface in 2026. We intentionally do not cover it inside C21. The reason is scope: doing on-device ML well requires the classical ML mental model that C5 supplies and the agentic-orchestration discipline that C23 supplies. Bolting a half-week of TensorFlow Lite onto the end of Droid would teach learners to call inference APIs without teaching them to evaluate model quality.

Instead, the recommended pathway is **C1 → C21 → C5 → C23**, ending with a capstone in C23 that puts an on-device LLM agent inside an Android app the learner already knows how to ship. Crunch Droid teaches the *vessel*; C5 and C23 teach the *cargo*.

---

## What we deliberately under-cover

A 24-week track still has to cut. Here is what we under-cover, and why:

- **Android Automotive and Android TV.** Both get overview lectures but no mini-projects. Both have small enough market shares that a 24-week course cannot justify a full week each. Learners who target either are pointed to the Now-In-Android samples and the Automotive OS documentation as next steps.
- **NDK and JNI.** We give one lecture and a mini-exercise. Production Android engineers reach for the NDK rarely; when they do, they need much more depth than one week can supply. We point to a future Crunch Labs track for systems-level mobile.
- **Cross-platform UI frameworks.** Compose Multiplatform gets an honest overview in the KMP week, but we do not teach Flutter or React Native. Those are separate disciplines; mixing them into a native-Android track dilutes both.
- **Legacy Views.** We teach the existence of the View system as historical context and a brief lecture on interop, but no week is spent on XML layouts, custom Views, or RecyclerView. New code is Compose; legacy migration is a job skill learners will pick up on the job.

---

## What this track does *not* compromise on

The non-negotiables, in order of priority:

1. **Every code block in every lecture is runnable** on a real device or emulator. No pseudocode, no "this would work in production." If we write it, it runs.
2. **The capstone ships.** Either to a Play Console closed track or to F-Droid. A capstone that does not ship is a capstone that does not count.
3. **Three chaos drills are mandatory.** Production discipline is not a lecture topic; it is a practiced behavior. Postmortems are how that behavior is built.
4. **The career engineering pack is required for completion.** A learner who cannot articulate Compose recomposition to a senior engineer in an interview did not finish the track.

---

## Status

This charter is live as of **2026-05-13**, the same date the Crunch Labs Charter was signed. It supersedes any earlier Droid-track notes in the curriculum tree. Maintained by the Code Crunch Labs curriculum council. Open an issue on the master curriculum repository to propose amendments.

*Licensed GPL-3.0.*
