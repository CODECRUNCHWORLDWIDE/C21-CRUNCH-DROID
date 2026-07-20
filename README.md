# C21 · Crunch Droid — Android Engineering

> A 24-week, semester-length Crunch Labs track that turns a capable developer into a senior Android engineer. You will ship a Jetpack Compose application with an offline-first WorkManager-backed sync engine, a Hilt-wired dependency graph, Room persistence, Kotlin Coroutines and Flow throughout, a Compose for Wear OS companion, and a complete Play Store release pipeline backed by a Kotlin Multiplatform shared core. Twenty-four weeks of Kotlin 2.x, Compose, AOSP-aware patterns, production constraints, and the engineering discipline that gets you hired onto a senior Android team.

---

## Who this track is for

Crunch Droid is built around four engineers we expect to meet often:

1. **The Python or JavaScript developer going native.** You have shipped products in dynamic languages and want a fast, typed runtime that runs on three billion devices. You want to learn Kotlin properly — not as a syntax tour, but as a first-class JVM language with coroutines and a real type system — and you want Compose explained at the recomposition level, not the tutorial level.

2. **The KMP-curious cross-platform developer.** You have done React Native or Flutter. You can ship features, but you have hit the platform-channel ceiling once too often. You want native Android plus a sane way to share business logic with iOS via Kotlin Multiplatform, without paying the bridge tax.

3. **The Java or JVM backend engineer pivoting to mobile.** You know the JVM. You know Spring or Micronaut. You want to take that mental model into the mobile world — Gradle, JVM bytecode, R8, ProGuard, the lifecycle, the threading constraints — and emerge fluent in modern Kotlin idioms and the entire Jetpack stack.

4. **The new graduate targeting Google, Meta, ByteDance, or any senior mobile team.** You want a portfolio that survives a system design interview. You want to talk about Compose recomposition phases, structured concurrency, cold versus hot flows, and exact-alarm constraints after Android 12 — not just read about them.

If you fit any of these profiles, this track is for you.

---

## What you can do at the end

After 24 weeks, you can:

1. Ship a production-grade Jetpack Compose application with Material 3 dynamic color, edge-to-edge insets, predictive back, and full accessibility.
2. Architect an offline-first system with Room, DataStore, and WorkManager — including conflict resolution, exponential backoff, and constraint-aware scheduling.
3. Write idiomatic Kotlin 2.x — null-safe, sealed-class-driven, with inline value classes and K2-aware compile pragmas — and explain when each idiom earns its keep.
4. Use Kotlin Coroutines and Flow correctly: structured concurrency, supervisor scopes, StateFlow and SharedFlow, cold versus hot streams, and channelFlow for callback bridges.
5. Wire a multi-module Android project with Gradle Kotlin DSL, version catalogs, and convention plugins so build times stay sub-minute on a clean build.
6. Inject dependencies with Hilt and reason about the Dagger graph that sits underneath, including custom components and assisted injection.
7. Talk to backends with Retrofit, OkHttp interceptors, certificate pinning, and Ktor Client when you need a KMP-friendly path; speak gRPC fluently on the device side.
8. Test every layer: JUnit for logic, Turbine for Flow assertions, Robolectric for JVM-side Android, Compose UI test for screens, Paparazzi for snapshot tests, Espresso for full integration.
9. Profile with macrobenchmark and generate Baseline Profiles that meaningfully shorten cold-start time. Tune R8 rules without breaking reflection.
10. Build for multiple form factors: phones, foldables (window size classes), Wear OS via Compose for Wear, with a passing nod to Android TV and Automotive.
11. Stand up a Play Store release pipeline on GitHub Actions and fastlane: signed AABs, internal and closed tracks, Play App Signing, Play Integrity attestation, staged rollouts.
12. Hold your own in a senior Android system-design interview — Compose lifecycle, structured concurrency edge cases, mobile-constraint trade-offs, FCM versus polling, exact alarms and Doze.

---

## Prerequisites

**Required**

- **C1 · Crunch Convos** or equivalent fluency in a typed object-oriented language. You should know what an interface is, what generics are for, and what `null` costs you when you ignore it.
- **A laptop with 16 GB RAM and 100 GB free disk.** Any OS — Linux, macOS, or Windows. The Android emulator runs on x86 and Apple Silicon equally well; physical-device testing is free.
- **Android Studio (free, open core)** and the Android SDK. Both ship on every operating system at zero cost.
- **A Google account** for Play Console (one-time USD 25 developer fee; recommended but not required to complete the track — capstone has a no-fee submission path through F-Droid as a fallback).

**Helpful but not required**

- **C9 · Crunch Sharp.** The JVM mental model transfers cleanly — Kotlin and C# rhyme more than either rhymes with Python.
- **C8 · Crunch Labs Web Dev.** The REST and HTTP fluency carries straight into Retrofit, OkHttp, and gRPC clients.
- **C5 · Crunch AI / Data Science.** If you want to do on-device ML with TensorFlow Lite, MediaPipe, or ML Kit during the elective windows, this background helps.

You do **not** need a prior mobile course. We start at Kotlin and the lifecycle, and we build up.

---

## Program at a glance

Twenty-four weeks, four phases, escalating in production weight as you go.

### Phase 1 · Foundations (Weeks 1–6)

Kotlin 2.x as a language. Null safety, sealed classes, data classes, inline value classes, context receivers, K2 compiler notes. Then coroutines and Flow — structured concurrency before anything Compose. The Android runtime, the lifecycle, the Activity-Fragment-View history (so you understand what Compose replaced), and your Gradle Kotlin DSL toolchain.

### Phase 2 · Compose & Architecture (Weeks 7–12)

Jetpack Compose as a discipline, not a tutorial. Recomposition phases, stability, snapshots, side effects, animation, gestures, accessibility. State hoisting and unidirectional data flow. Navigation 3 with type-safe routes. Material 3, Material You, dynamic color, edge-to-edge. MVVM with Jetpack ViewModel and the Now-In-Android architecture as a reference.

### Phase 3 · Production Engineering (Weeks 13–18)

Hilt and the Dagger graph beneath it. Room, DataStore (Preferences and Proto), and the file system. WorkManager and foreground services under the post-Android-12 exact-alarm regime. Retrofit, OkHttp, Ktor Client, and gRPC on Android. Macrobenchmark, Baseline Profiles, R8, ProGuard rules. Testing across every layer — Robolectric, Compose UI test, Espresso, Paparazzi, Turbine, MockK.

### Phase 4 · Capstone & Polish (Weeks 19–24)

Multi-form-factor: Wear OS via Compose for Wear, foldables via window size classes, Android Automotive and TV as overview material. CI/CD on GitHub Actions and fastlane. Play Console API. Play Integrity attestation. Capstone build, chaos drill, postmortem, and Play submission. Senior-interview prep and portfolio polish.

---

## Weekly cadence

Each week of Crunch Droid carries the same shape every other Labs track does:

- **Reading group** (Monday) — primary sources only. AOSP commits, Jetpack release notes, KEEP proposals, official docs. No marketing blog posts.
- **Lectures** (Tuesday + Thursday) — 2 to 3 markdown lectures of 250 to 400 lines, every code block runnable on a real device or emulator.
- **Lab** (Wednesday) — exercises and challenges. Three exercises minimum, two stretch challenges, all with starter code and reference solutions.
- **Mini-project** (weekend) — one small but real Android module that exercises that week's topics end-to-end.
- **Quiz** (Friday) — ten questions, answer key at the bottom, scoring threshold matters for capstone gating.

Plan on roughly 35 to 40 hours per week if you treat it as a full-time semester (~864 total hours), or 18 to 22 hours per week if you split it across two semesters.

---

## Hardware and license expectations

- **Laptop:** any 64-bit machine with 16 GB RAM. The Android emulator is fully hardware-accelerated on Apple Silicon and on x86 Linux/Windows. Chromebooks with developer mode work for everything except macrobenchmark.
- **Physical device (optional but recommended):** a USB-C Android phone with developer mode enabled. Any phone running Android 10 or later. Used Pixels work fine.
- **OS:** Linux, macOS, or Windows are all first-class. We test the curriculum on Ubuntu 24.04 LTS, macOS 14, and Windows 11.
- **Software cost:** zero. Android Studio is free. The Android SDK is free. Kotlin is free and open source. The JVM is OpenJDK. The emulator is free. Git is free.
- **Optional paid items:** the USD 25 one-time Play Console developer fee. Optional Firebase usage during the capstone (sits inside the free tier for the project size we ship).

We design every assignment so an engineer on the lowest-cost laptop in the lowest-cost market can complete it.

---

## Recommended pre/post tracks

- **Standard mobile pathway:** C1 → C8 (Web) → C21 (Droid). The web detour gives you HTTP, REST, and the browser model before you face Android networking.
- **Cross-platform mobile pathway:** C1 → C20 (Swift) → C21 (Droid). Take both mobile platforms in sequence; finish with a Kotlin Multiplatform capstone that shares a typed core between iOS and Android.
- **Java/JVM crossover pathway:** C1 → C9 (Sharp) → C21 (Droid). The C# detour gives you a typed managed-runtime mental model that transfers cleanly to Kotlin and the JVM.
- **AI-on-the-edge pathway:** C1 → C21 (Droid) → C5 (AI / DS) → C23 (Agents). Finish Droid first, then specialize into on-device ML and agent orchestration on Android.
- **Distributed mobile pathway:** C1 → C21 (Droid) → C22 (Mesh). After Droid you understand the client; Mesh teaches the typed-RPC backend that talks to it.

---

## License and maintainers

Licensed **GPL-3.0**, like the rest of the Code Crunch academy. The Kotlin language, the Android Open Source Project, and the entire Jetpack stack are open source. We embrace that lineage and contribute back: any reusable module a learner writes during the capstone is published to the C21 learner-modules repository under GPL-3.0.

Maintained by the Code Crunch Labs curriculum council. Open an issue on the master curriculum repository to propose a topic addition, retire a deprecated API from the syllabus, or contribute a chaos drill to the capstone.

*See [`SYLLABUS.md`](SYLLABUS.md) for the full week-by-week curriculum and [`CHARTER.md`](CHARTER.md) for the design rationale.*
