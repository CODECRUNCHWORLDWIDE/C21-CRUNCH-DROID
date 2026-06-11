# Week 23 — Resources

Every primary resource on this page is **free**. The Field-Force Companion spec is in this repo. Android's developer documentation is free. The Now-In-Android reference app is public on GitHub under Apache-2.0. The Play Console docs are free; the USD 25 developer fee is the only paid item, and the capstone has a no-fee F-Droid fallback for next week's submission.

## The capstone specification (read this first, every time)

- **The Field-Force Companion brief** — the source of truth for the whole capstone: the required architecture, the seven modules, the eight deliverables, the chaos-drill menu, and the rubric. Re-read it at the start of the week and before every milestone:
  [`SYLLABUS.md` § Capstone · Field-Force Companion](../../SYLLABUS.md)
- **The assessment matrix** — the capstone is 35% of the course, pass/fail, gating for completion. Know what "pass" means before you build:
  [`SYLLABUS.md` § Assessment matrix](../../SYLLABUS.md)

## The architecture reference

- **`android/nowinandroid`** — Google's reference app and the architecture model for this entire track. This week, read how it splits `:core`, `:feature`, and `:sync` modules; how the data layer is the source of truth; and how the offline-first `SyncWorker` is wired. Your capstone module graph is this graph, specialized:
  <https://github.com/android/nowinandroid>
- **The Now-In-Android architecture learning journey** — the written walkthrough of the data/domain/UI layering, the module dependency rules, and the offline-first design:
  <https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md>
- **"Guide to app architecture"** — the official layering guidance (UI layer, domain layer, data layer; the repository pattern; single source of truth):
  <https://developer.android.com/topic/architecture>
- **"Build an offline-first app"** — the canonical offline-first guide: local source of truth, reads from disk, writes through a queue, conflict resolution:
  <https://developer.android.com/topic/architecture/data-layer/offline-first>

## Multi-module and the module graph

- **"Guide to Android app modularization"** — module types, dependency directions (who may depend on whom), and the "low coupling, high cohesion" rules your Exercise 1 graph enforces:
  <https://developer.android.com/topic/modularization>
- **"Modularization patterns"** — the `:core`, `:feature`, `:app` conventions and the dependency rules that keep features from depending on each other:
  <https://developer.android.com/topic/modularization/patterns>
- **Gradle dependency constraints and rules** — how to *enforce* a dependency direction so an illegal edge fails the build, not code review:
  <https://docs.gradle.org/current/userguide/dependency_constraints.html>

## The release-candidate AAB, R8, and signing

- **"About Android App Bundles"** — the AAB format, why Play wants it, and how Play App Signing splits the upload key from the app-signing key:
  <https://developer.android.com/guide/app-bundle>
- **"Sign your app" / Play App Signing** — the signing config, the upload key, and enrolling in Play App Signing:
  <https://developer.android.com/studio/publish/app-signing>
- **"Shrink, obfuscate, and optimize your app" (R8)** — R8 full mode, the keep rules your reflection-heavy code (gRPC, kotlinx-serialization, Room) needs, and how to read the missing-rules report:
  <https://developer.android.com/build/shrink-code>
- **"Upload your app to the internal test track"** — the Play Console internal track this week's RC lands on:
  <https://support.google.com/googleplay/android-developer/answer/9845334>

## Baseline Profiles

- **"Baseline Profiles overview"** — generation, packaging, and verifying the ≥20% cold-start improvement the capstone requires:
  <https://developer.android.com/topic/performance/baselineprofiles/overview>
- **"Create a Baseline Profile"** — the `baselineprofile` Gradle plugin and the macrobenchmark generator that exercises your cold-start path:
  <https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile>
- **"Measure with the Macrobenchmark library"** — proving the cold-start delta with `StartupTimingMetric`:
  <https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview>

## Wear OS — tiles, complications, ongoing activities

- **"Compose for Wear OS"** — `ScalingLazyColumn`, the Wear Material catalog, and building the watch UI against the shared core:
  <https://developer.android.com/training/wearables/compose>
- **"Create tiles"** — the tile service, the tile layout, and refresh; your active-dispatch tile:
  <https://developer.android.com/training/wearables/tiles>
- **"Expose data to complications"** — the complication data source service for the system watch face:
  <https://developer.android.com/training/wearables/wear-os/complications>
- **"Ongoing activities"** — the ongoing-activity API for an in-progress dispatch surfaced on the watch:
  <https://developer.android.com/training/wearables/ongoing-activities>

## Kotlin Multiplatform — the shared core

- **"Kotlin Multiplatform overview"** — `commonMain`/`androidMain`, `expect`/`actual`, and the KMP-friendly libraries (Ktor, kotlinx-serialization, kotlinx-coroutines, kotlinx-datetime) the `:shared-core` is built from:
  <https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform.html>
- **"Make your Android application work on iOS"** — the practical guide to a shared module consumed by Android (the iOS half is stubbed for the capstone):
  <https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-integrate-in-existing-app.html>

## gRPC on Android

- **`grpc/grpc-kotlin`** — the Kotlin/coroutines gRPC stubs the `:core-network` client is built on; read the Android example:
  <https://github.com/grpc/grpc-kotlin>
- **"gRPC on Android"** — the OkHttp channel, TLS, and the lifecycle of a managed channel on a mobile device:
  <https://grpc.io/docs/platforms/android/kotlin/quickstart/>

## Play Integrity and Keystore

- **"Play Integrity API overview"** — the attestation flow, the integrity verdict, and the server-side decryption you gate sign-in on:
  <https://developer.android.com/google/play/integrity/overview>
- **"Standard API requests"** — the standard (cached) integrity request, the right shape for a sign-in gate:
  <https://developer.android.com/google/play/integrity/standard>
- **"Android Keystore system"** — hardware-backed key storage for the token the attestation gate protects:
  <https://developer.android.com/training/articles/keystore>
- **"Work with data more securely" (EncryptedFile / Keystore-backed)** — the encrypted token store pattern for `:feature-auth`:
  <https://developer.android.com/topic/security/data>

## Architecture Decision Records

- **Michael Nygard's original ADR template** — the decision / context / consequences structure your four ADRs follow:
  <https://github.com/joelparkerhenderson/architecture-decision-record>
- **"Architecture decision records" (GitHub Engineering / ThoughtWorks Tech Radar)** — why a short, immutable, append-only ADR log beats a wiki page:
  <https://www.thoughtworks.com/radar/techniques/lightweight-architecture-decision-records>

## Mermaid for the architecture diagram

- **Mermaid flowchart syntax** — the diagram-as-code you check into `docs/architecture.md`; renders on GitHub natively:
  <https://mermaid.js.org/syntax/flowchart.html>

## Tools you'll use this week

- **Android Studio Ladybug (2024.2)+** — the build-variants panel, the APK/AAB analyzer (`Build ▸ Analyze APK`) to confirm R8 shrank what you expected, and the two emulators (Pixel 8 API 35, Wear OS API 34).
- **`./gradlew :app:bundleRelease`** — assembles the signed release AAB. `:app:assembleRelease` for the APK if you want to sideload.
- **`./gradlew :wear:assembleRelease`** — the signed Wear OS APK.
- **The Play Console** — the internal test track for this week's upload; the closed track is next week.
- **`bundletool`** — Google's tool to build APKs from an AAB and test the exact artifact Play will serve:
  <https://developer.android.com/tools/bundletool>

## Open-source capstones to read (structure, not copy)

- **`android/nowinandroid`** — again, the structural reference; read its `:sync` module and its CI workflow.
- **`chrisbanes/tivi`** — a large, real multi-module Compose app with KMP, a Compose Compiler report in CI, and a clean module graph:
  <https://github.com/chrisbanes/tivi>
- **`google/horologist`** — Wear OS components (tiles, complications, media) you can read for the Wear half:
  <https://github.com/google/horologist>

---

*If a link 404s, please open an issue so we can replace it.*
