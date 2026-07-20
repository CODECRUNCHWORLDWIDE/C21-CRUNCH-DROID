# C21 · Crunch Droid — Syllabus

**Length:** 24 weeks · semester intensive
**Workload:** ~36 hours per week · ~864 total hours
**Prerequisite:** C1 (or equivalent typed-OOP fluency); helpful prior work in C9 (Sharp) or C8 (Web)
**Outcome:** Senior Android engineer — ships a Jetpack Compose application with WorkManager-backed offline sync, Hilt-wired Room persistence, a Compose for Wear OS companion, a KMP shared core, and a complete Play Store release pipeline.

---

## Phases

1. **Foundations** — Weeks 1–6 — Kotlin 2.x, coroutines, Flow, the Android runtime, Gradle Kotlin DSL.
2. **Compose & Architecture** — Weeks 7–12 — Jetpack Compose, state, navigation, Material 3, MVVM with UDF.
3. **Production Engineering** — Weeks 13–18 — Hilt, Room, networking, WorkManager, testing, performance.
4. **Capstone & Polish** — Weeks 19–24 — Wear OS, foldables, CI/CD, Play submission, capstone, interview prep.

---

## Phase 1 · Foundations (Weeks 1–6)

### Week 01 · Kotlin 2.x as a first-class JVM language

**Topics.** Kotlin 2.x and the K2 compiler. Top-level functions, expressions over statements, type inference, immutability defaults. `val` versus `var`. Pragmatic equality (`==` versus `===`). Smart casts.

**Lecture.** "What Kotlin gives you that Java did not, and what it does not." We walk the K2 frontend in conceptual terms, examine bytecode for a handful of trivial functions with `javap`, and discuss why the language design choices map to JVM realities.

**Hands-on mini-project.** A command-line CLI tool — `kt-stat` — that walks a directory, counts source lines per language, and prints a colorized report. Built with Gradle Kotlin DSL, packaged as a fat JAR.

**Skills earned.** Reading bytecode for confidence. Writing idiomatic Kotlin that does not look like translated Java. Gradle Kotlin DSL fluency from day one.

---

### Week 02 · Null safety, sealed types, and the algebraic core

**Topics.** Nullable types, the `?` operator family, `let`/`run`/`apply`/`also`. Sealed classes and sealed interfaces. Data classes and component functions. Inline value classes. Enum classes with abstract members.

**Lecture.** "Algebraic data types in a language that still has `NullPointerException` somewhere." We model a parser's result type three ways — nullable, sealed, and `Result<T>` — and discuss when each one earns its keep.

**Hands-on mini-project.** A typed JSON parser that consumes a small JSON dialect and returns a sealed `JsonNode` tree. No external libraries. Exhaustive `when` over the sealed hierarchy.

**Skills earned.** Modeling state and outcomes with the type system. Writing exhaustive `when` blocks the compiler enforces. Knowing when inline value classes erase to primitives.

---

### Week 03 · Generics, inline functions, context receivers

**Topics.** Declaration-site and use-site variance. Reified type parameters. Inline and `crossinline` functions. Context receivers (Kotlin 2.x). Higher-order functions. Function types and SAM conversion.

**Lecture.** "Why `inline fun <reified T>` is not a parlor trick." We trace how reification erases at the call site, why `crossinline` exists, and how context receivers let you write extension-like code without losing type information.

**Hands-on mini-project.** A typed event bus library — `kt-bus` — using reified generics and inline subscription functions. Includes a Gradle subproject that publishes to `mavenLocal`.

**Skills earned.** Reading variance annotations and explaining them out loud. Writing inline DSL-style APIs. Publishing a Kotlin library.

---

### Week 04 · Coroutines: structured concurrency from first principles

**Topics.** Suspending functions, continuations, `CoroutineScope`, `Job`, `SupervisorJob`. Dispatchers — Main, IO, Default, Unconfined — and when each is wrong. `coroutineScope` versus `supervisorScope`. Cancellation cooperativity. Exception handling.

**Lecture.** "Coroutines are not lightweight threads. They are interruption points the compiler inserts for you." We disassemble a suspend function, trace the continuation-passing transform, and walk three real cancellation bugs.

**Hands-on mini-project.** A coroutine-based parallel downloader that fetches 100 URLs concurrently with bounded parallelism, supports cancellation, and prints structured progress.

**Skills earned.** Structured concurrency as a discipline, not a buzzword. Cancellation cooperation. Picking the right dispatcher for the right work.

---

### Week 05 · Flow, StateFlow, SharedFlow, channels

**Topics.** Cold flows. Operators — `map`, `filter`, `flatMapConcat`, `flatMapLatest`, `transformLatest`. Backpressure and buffering. `StateFlow` and `SharedFlow` as hot flows. `channelFlow` and `callbackFlow` for bridging callback APIs. Turbine for testing.

**Lecture.** "Cold versus hot, and why most of the production bugs you will see are about confusing the two." We build a state-of-the-world store with `StateFlow` and a one-shot event stream with `SharedFlow(replay = 0)`.

**Hands-on mini-project.** A reactive ticker module: a cold `Flow` of timestamps, a hot `StateFlow` of computed price-deltas, and a `SharedFlow` of alerts when a delta crosses a threshold. All tested with Turbine.

**Skills earned.** Operator selection without guessing. Bridging legacy callback APIs into Flow. Asserting on Flow emissions deterministically.

---

### Week 06 · The Android runtime, ART, Gradle, AOSP-aware mental model

**Topics.** ART versus the desktop JVM. The Android lifecycle (Activity, Fragment) as historical context. The AOSP source tree at a high level. Gradle Kotlin DSL, version catalogs (`libs.versions.toml`), build variants, signing configs. R8 in introduction.

**Lecture.** "Where your APK comes from." We trace `gradlew assembleDebug` end to end: Kotlin compilation, R8 desugaring, dexing, manifest merging, resource compilation, signing.

**Hands-on mini-project.** A two-module Android project (a `:core` library and an `:app` module) with version catalogs, two build variants (`free` and `pro`), and a documented signing config that uses a debug keystore checked into the repo.

**Skills earned.** Reading `build.gradle.kts` without flinching. Tracing a build failure to the right Gradle task. Understanding what runs on ART and what does not.

---

## Phase 2 · Compose & Architecture (Weeks 7–12)

### Week 07 · Jetpack Compose: composition, recomposition, the three phases

**Topics.** Declarative UI. The composition tree. Recomposition phases (composition, layout, draw). Stability and the `@Stable` / `@Immutable` annotations. Skippable functions. The Compose compiler plugin.

**Lecture.** "Recomposition is not free, but it is not what you think." We instrument a screen with the Compose Compiler report, identify non-skippable functions, and fix them.

**Hands-on mini-project.** A pure-Compose Pomodoro timer with a circular progress indicator, animated tick, and a recomposition counter overlay in debug builds.

**Skills earned.** Reading the Compose Compiler report. Diagnosing unnecessary recomposition. Writing stable parameters by intent.

---

### Week 08 · State, side effects, snapshots

**Topics.** `remember`, `rememberSaveable`, `mutableStateOf`. State hoisting. The Snapshot system. Side-effect APIs — `LaunchedEffect`, `DisposableEffect`, `rememberCoroutineScope`, `produceState`, `derivedStateOf`. `snapshotFlow` for bridging snapshot state to Flow.

**Lecture.** "Side effects in a declarative world." We walk every side-effect API and the exact lifecycle hook each one is keyed to.

**Hands-on mini-project.** A search-as-you-type screen that debounces input with `snapshotFlow`, cancels prior queries, and survives configuration changes via `rememberSaveable`.

**Skills earned.** Picking the right side-effect API the first time. Composing snapshot state with Flow. Surviving rotation without a `ViewModel`.

---

### Week 09 · Layout, gestures, animation, accessibility

**Topics.** `Layout` and custom layouts. `Modifier` chains and ordering. Gesture detectors (`pointerInput`, drag, transform). Animation APIs — `animate*AsState`, `updateTransition`, `AnimatedVisibility`, `AnimatedContent`. Compose semantics for TalkBack. Large-text and color-contrast compliance.

**Lecture.** "Modifier order matters more than people realize." We walk a half-dozen `Modifier` chains where reordering changes paint, touch targets, and accessibility.

**Hands-on mini-project.** A swipe-to-dismiss card stack with elastic resistance, spring-back animation, full accessibility actions (TalkBack swipe-up-to-dismiss), and a written WCAG contrast audit.

**Skills earned.** Building custom layouts. Writing accessible Compose UIs by default. Animation timing that does not feel toy-like.

---

### Week 10 · Navigation 3 with type-safe routes

**Topics.** Navigation 3 (the modern type-safe nav). Serializable route types. Nested graphs. Deep links. Predictive back. Bottom-bar navigation. Argument passing without string-key brittleness.

**Lecture.** "Type-safe routes end the most boring bug class in Android." We migrate a small string-key nav graph to Navigation 3 and remove every `findNavController` cast on the way.

**Hands-on mini-project.** A three-tab application — Home, Catalog, Profile — with deep links, nested graphs, predictive back fully wired, and end-to-end Compose UI tests of every transition.

**Skills earned.** Designing a navigation graph as a data model. Wiring deep links without manifest spelunking. Predictive back working everywhere.

---

### Week 11 · Material 3, Material You, dynamic color, edge-to-edge

**Topics.** Material 3 components. The Material You color system. Dynamic color extraction from the user's wallpaper. Theming with `ColorScheme` and `Typography`. Edge-to-edge with `WindowCompat`. Window insets and inset padding modifiers. Dark theme.

**Lecture.** "Why Material You changed how we ship color." We walk the dynamic-color extraction pipeline, fall back gracefully on older OS versions, and audit a component palette for contrast.

**Hands-on mini-project.** A reader app with full Material 3 theming, dynamic color on Android 12+, a hand-tuned fallback palette on older devices, edge-to-edge layout, and an audited dark theme.

**Skills earned.** Theming a real app — not a tutorial app — in Material 3. Insets-aware Compose layouts. Dynamic color without it looking gaudy.

---

### Week 12 · MVVM, UDF, the Now-In-Android pattern

**Topics.** Jetpack `ViewModel`. Unidirectional data flow. UI state as a sealed `UiState` type. The Now-In-Android architecture: data layer, domain layer, UI layer. Where Compose state ends and ViewModel state begins. Process death and `SavedStateHandle`.

**Lecture.** "Architectures are spelling, not grammar." We compare three patterns — MVI, MVVM-with-UDF, and pure Compose state — and discuss which one the Now-In-Android team picked and why.

**Hands-on mini-project.** A two-screen feed app with a `ViewModel`-driven `StateFlow<UiState>`, full process-death survival, and a tested `SavedStateHandle` round-trip.

**Skills earned.** Drawing the line between Compose state and ViewModel state. Surviving Doze, process death, and configuration change. Reading Now-In-Android source without getting lost.

---

## Phase 3 · Production Engineering (Weeks 13–18)

### Week 13 · Dependency injection with Hilt (and the Dagger graph beneath)

**Topics.** Hilt as a Dagger overlay. `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`. Modules, components, scopes. Assisted injection. Multi-module Hilt setup. Reading Dagger-generated code.

**Lecture.** "Hilt is opinionated Dagger. Today we unwrap the opinions." We compile a small Hilt graph, open the generated factories, and trace the component hierarchy from `@HiltAndroidApp` down to a `@HiltViewModel`.

**Hands-on mini-project.** A multi-module Hilt graph: `:core-network`, `:core-database`, `:feature-auth`, `:app`. Each module exposes its bindings via Hilt modules; `:app` consumes them.

**Skills earned.** Debugging Hilt errors that read like Dagger errors. Designing a multi-module DI graph. Knowing when assisted injection is the right tool.

---

### Week 14 · Persistence: Room, DataStore, the file system

**Topics.** Room — entities, DAOs, type converters, relations, Paging 3 integration. DataStore Preferences and Proto DataStore. The file system on Android (internal versus external, scoped storage post-Android-11). Room with Flow for reactive queries. Migrations.

**Lecture.** "Schema migrations are where every Android app eventually breaks." We write three migrations end-to-end and add a Room schema export to source control.

**Hands-on mini-project.** A local-first notes app: Room database with a `Note` entity, Proto DataStore for user preferences, scoped-storage backup of attachments, a tested migration from v1 to v3.

**Skills earned.** Designing a Room schema that survives migrations. Picking Preferences vs. Proto DataStore on the right criteria. Scoped-storage compliance.

---

### Week 15 · Networking: Retrofit, OkHttp, Ktor Client, gRPC

**Topics.** Retrofit with kotlinx-serialization. OkHttp interceptors — logging, auth, cache. Certificate pinning. Ktor Client for KMP-friendly contexts. gRPC on Android with `grpc-kotlin`. Retry, timeout, and connection pooling defaults.

**Lecture.** "Retrofit is great for REST; Ktor is great for KMP; gRPC is great for binary contracts." We benchmark all three against the same backend and discuss the trade-offs.

**Hands-on mini-project.** A weather client implemented twice — once with Retrofit, once with Ktor — backed by a typed sealed `NetworkResult`. A bonus path implements the same client over gRPC.

**Skills earned.** Writing networking code that retries correctly. Pinning certificates without bricking your app at every cert rotation. Choosing between three solid networking stacks.

---

### Week 16 · Background work: WorkManager, foreground services, exact alarms

**Topics.** WorkManager — one-time, periodic, expedited, chained, unique work. Constraints (network, battery, charging). Foreground services and the foreground service type taxonomy. Exact alarms after Android 12 (`SCHEDULE_EXACT_ALARM`). Doze and App Standby. Battery optimization exemptions and when not to ask for them.

**Lecture.** "Background work on Android is a regulated industry now." We trace the constraint pipeline from `WorkRequest` to the `ConstraintsTracker` and explain why your work did not run.

**Hands-on mini-project.** An offline-first sync engine: periodic WorkManager job with `BackoffPolicy.EXPONENTIAL`, network-constraint-aware, foreground-promotion when the user opens the app mid-sync, full integration test using `WorkManagerTestInitHelper`.

**Skills earned.** WorkManager fluency end to end. Foreground service compliance without crashing on Android 14. Knowing when *not* to use an exact alarm.

---

### Week 17 · Testing across every layer

**Topics.** JUnit 5. Turbine for Flow assertions. Robolectric for JVM-side Android tests. Compose UI test (`createComposeRule`). Espresso for full instrumentation. Paparazzi for screenshot tests. MockK for Kotlin-native mocking. Test fixtures and the test-only module pattern.

**Lecture.** "Testing pyramid for Android: small, medium, large — and where Compose blurs the lines." We map each test type to the right layer and discuss what to *not* test.

**Hands-on mini-project.** A `:feature-checkout` module with: unit tests on the ViewModel (Turbine + MockK), Robolectric tests on the DAO, Compose UI tests on the screen, Paparazzi screenshot tests on every Material 3 state, an Espresso end-to-end smoke test.

**Skills earned.** Picking the right test type the first time. Writing deterministic Flow tests. Catching visual regressions before users do.

---

### Week 18 · Performance: macrobenchmark, Baseline Profiles, R8

**Topics.** Macrobenchmark library. Cold-, warm-, and hot-start measurements. Baseline Profiles — generation, packaging, verification. R8 — full mode, ProGuard rules, keep rules for reflection-heavy code. App startup library. Strict Mode.

**Lecture.** "Cold start is the only first impression you get." We profile a real app to a Baseline Profile, measure the improvement, and discuss the ceiling.

**Hands-on mini-project.** Take the Week-11 reader app, write a Baseline Profile that exercises the cold-start path, measure with macrobenchmark, commit the profile, and document the cold-start improvement.

**Skills earned.** Reading a macrobenchmark report. Generating and packaging Baseline Profiles. Writing R8 keep rules without disabling R8 in frustration.

---

## Phase 4 · Capstone & Polish (Weeks 19–24)

### Week 19 · Kotlin Multiplatform overview, Wear OS introduction

**Topics.** Kotlin Multiplatform — `commonMain`, `androidMain`, `iosMain`. `expect`/`actual`. KMP-friendly libraries (Ktor, kotlinx-serialization, kotlinx-coroutines, kotlinx-datetime). Compose Multiplatform overview. Compose for Wear OS — tiles, complications, ongoing activities.

**Lecture.** "KMP is for the business layer, not the UI layer (yet)." We split a shared `:domain` module into KMP code and discuss what *not* to share.

**Hands-on mini-project.** A KMP `:shared-core` module that exposes a typed `WeatherForecast` model, a Ktor-backed repository, and serialization — consumed by an Android app and stubbed for iOS.

**Skills earned.** KMP module setup. Picking the right code to share. Compose for Wear OS basics.

---

### Week 20 · Multi-form-factor: foldables, Wear OS deep, TV and Automotive overview

**Topics.** Window size classes. Adaptive layouts. Foldable APIs — `WindowInfoTracker`, hinge state. Compose for Wear OS deeper — `ScalingLazyColumn`, complications, tiles. Android TV and Automotive as overview.

**Lecture.** "One codebase, four form factors, three of them weird." We walk the window-size-class API and demonstrate the same screen rendering correctly on phone, foldable, and Wear.

**Hands-on mini-project.** A Wear OS companion to the Week-19 weather app — a tile that shows the current forecast, a complication for the system watch face, and ongoing-activity support for a rain alert.

**Skills earned.** Adaptive Compose layouts. Wear OS tile and complication authoring. Knowing what *not* to build for TV and Automotive in a 24-week course.

---

### Week 21 · CI/CD: GitHub Actions, fastlane, Play Console API

**Topics.** GitHub Actions for Android — caching, parallel build matrix, signed AAB artifacts. fastlane lanes for screenshots and Play upload. Play Console API. Internal, closed, open, and production tracks. Staged rollouts. Play App Signing.

**Lecture.** "Releasing on Android is a four-step pipeline; we will automate three of them." We wire a complete GitHub Actions workflow that builds, tests, screenshots, and uploads to the Play internal track on every tag.

**Hands-on mini-project.** A GitHub Actions workflow that, on a tagged commit, builds a signed AAB, runs the full test suite, generates Paparazzi screenshots, and uploads to the Play Console internal track via fastlane.

**Skills earned.** Android CI/CD without surprises. fastlane fluency. Play Console API access and secrets management.

---

### Week 22 · Security: Keystore, EncryptedSharedPreferences, certificate pinning, Play Integrity

**Topics.** Android Keystore — key generation, key attestation. EncryptedSharedPreferences and EncryptedFile. Certificate pinning with OkHttp. SafetyNet → Play Integrity migration. Network security configuration. The permission model post-Android 13.

**Lecture.** "Play Integrity is not SafetyNet — and SafetyNet shutdown is the deadline most teams missed." We integrate Play Integrity end to end and discuss what an attestation failure looks like in production.

**Hands-on mini-project.** Add Keystore-backed encryption to the Week-14 notes app, pin the certificate of the Week-15 weather API, and integrate Play Integrity attestation as a sign-in gate.

**Skills earned.** Keystore-backed secrets. Certificate pinning that does not catastrophically fail at rotation. Play Integrity as a real attestation flow, not a checkbox.

---

### Week 23 · Capstone build week — Field-Force Companion

**Topics.** Capstone integration. Pulling Weeks 1–22 into one shipping system.

**Lecture.** "From lab modules to a release candidate." We walk a release-readiness checklist and a pre-submission audit.

**Hands-on mini-project — capstone build.** Build the Field-Force Companion (specified below). End of week: a release-candidate AAB, a signed Wear OS APK, a shared KMP core, and a Play Console internal track upload.

**Skills earned.** End-to-end production integration. Pre-submission discipline. Living with your own architectural decisions.

---

### Week 24 · Capstone polish, chaos drill, Play submission, interview prep

**Topics.** Chaos drill execution. Postmortem write-up. Play Store closed-track submission. Senior Android interview prep — Compose recomposition deep questions, coroutines pitfalls, mobile system design.

**Lecture.** "Closing the loop." Live capstone reviews. Final interview drills.

**Hands-on mini-project — capstone polish.** Run the three chaos drills below. Write postmortems. Submit to the Play Store closed track. Sit four mock senior-Android interviews.

**Skills earned.** Postmortem discipline. Calm under interview pressure. The confidence that comes from having actually shipped a multi-form-factor production system.

---

## Assessment matrix

| Component | Weight | Gating? |
|---|---|---|
| Weekly quizzes (24 × 10 questions) | 10% | Must score ≥ 70% average to enter capstone |
| Weekly mini-projects (24 modules) | 25% | At least 20 of 24 must pass rubric |
| Phase exams (4 phase-end take-home exams) | 15% | Each must pass at ≥ 70% |
| Capstone — Field-Force Companion | 35% | Pass/fail; required for completion |
| Chaos drill postmortems (3 incidents) | 5% | All three required |
| Career engineering pack (interview drills + portfolio) | 10% | Required for completion |

**Track completion** requires: ≥ 70% on quizzes, ≥ 20/24 passing mini-projects, all four phase exams passed, capstone passed, three chaos drill postmortems submitted, and the career engineering pack delivered.

---

## Capstone · Field-Force Companion

**Brief.** An offline-first Jetpack Compose application for a fictional field-force operations team, with a Compose for Wear OS companion, a Kotlin Multiplatform shared core, gRPC sync to a typed backend, WorkManager-backed exponential-backoff synchronization, encrypted local storage, Play Integrity attestation, and a complete Play Console release pipeline. One substantial multi-form-factor system, not a portfolio of toys.

**Required architecture.**

- `:shared-core` (KMP) — typed domain model, Ktor-based API surface, kotlinx-serialization wire format, kotlinx-coroutines flows, kotlinx-datetime for time math.
- `:app` (Android) — Jetpack Compose UI, Material 3 with dynamic color, Navigation 3, MVVM with `StateFlow<UiState>`, Hilt-wired dependency graph.
- `:wear` (Wear OS) — Compose for Wear, one tile, one complication, ongoing activity for active dispatches.
- `:feature-sync` — WorkManager periodic job, exponential backoff, network and battery constraints, foreground-promotion path.
- `:feature-auth` — Play Integrity attestation at sign-in, Keystore-backed token storage.
- `:core-network` — gRPC client (`grpc-kotlin`) with certificate pinning, structured retry, typed `NetworkResult` sealed return type.
- `:core-database` — Room with three entities, Proto DataStore for preferences, schema export checked into source control, two migrations exercised in tests.

**Required deliverables.**

1. Public GitHub repository under GPL-3.0.
2. Architecture diagram (Mermaid) checked into `docs/architecture.md`.
3. Signed AAB uploaded to a Play Console closed track. (F-Droid fallback acceptable for learners who choose not to pay the Play developer fee.)
4. README with a five-minute screencast walkthrough (phone screen + Wear screen side by side).
5. Baseline Profile generated, packaged, and demonstrated to reduce cold-start time by ≥ 20%.
6. Full test suite — unit, Robolectric, Compose UI test, Paparazzi snapshots, one Espresso end-to-end smoke — green on CI.
7. GitHub Actions workflow that builds, tests, screenshots, and uploads to the Play internal track on tag.
8. Three chaos-drill postmortems (see below).

**Chaos drill — required.** Execute all three, write a postmortem for each, link them from the capstone README.

1. **Offline-sync conflict.** Two devices edit the same record while offline. Reconnect both. Document how your conflict resolution decides which wins and why; include screenshots of the conflict UI.
2. **FCM token rotation.** Force a Firebase Cloud Messaging token rotation mid-session. Document the path from rotated token to server re-registration; demonstrate that no message is silently dropped.
3. **Play Integrity attestation failure.** Run the capstone on an emulator without Google Play Services. Demonstrate graceful sign-in failure with a clear user-facing message and a documented fallback path (do not silently fail open).

Each postmortem must include: timeline, root cause, blast radius, what we changed, and what we would do differently with another week.

---

## Career engineering pack

Every Crunch Droid learner delivers the following before track completion:

1. **`interview-prep/`** — six topic drills with worked answers:
   - Compose recomposition phases and stability — explain to a staff engineer.
   - Coroutines pitfalls — three real production bugs and the fix for each.
   - Cold versus hot flows — when to pick which.
   - WorkManager versus foreground service versus exact alarm — design exercise.
   - Mobile system design — design WhatsApp's message-send pipeline for a senior interview.
   - Memory and ANR debugging — read a stack trace out loud and isolate the bug.

2. **`production-runbook.md`** — what an on-call rotation for a senior Android team actually looks like. Crashlytics triage. Play Console vitals. ANR rate budgets. Staged rollout halt criteria.

3. **`portfolio.md`** — three polished projects suitable for a recruiter. One must be the capstone. The other two must be original, public, GPL-3.0 (or a permissive license of the learner's choice), and accompanied by a one-page engineering narrative.

4. **Four mock interviews** — two technical (live coding in Kotlin), one system design (mobile-constrained), one behavioral. Recorded and reviewed.

---

*Licensed GPL-3.0. See [`LICENSE`](LICENSE). See [`CHARTER.md`](CHARTER.md) for the design rationale of this syllabus.*
