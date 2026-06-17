# Week 19 — Kotlin Multiplatform overview, Wear OS introduction

Welcome to Week 19 of **C21 · Crunch Droid**, and to Phase 4 — Capstone & Polish. For eighteen weeks you built one app for one form factor: a Compose phone application, tested at every layer, made fast and proven fast. This week the world widens twice. First, your *business logic* escapes the Android-only runtime and into **Kotlin Multiplatform (KMP)** — the same typed domain model, the same Ktor-backed repository, the same coroutines and serialization, compiled for Android *and* iOS from one `commonMain` source set. Second, you meet your *second form factor*: **Wear OS**, the watch on the user's wrist, with its own Compose toolkit, its own tiny screen, its own glanceable surfaces — tiles, complications, ongoing activities. You are no longer building "an Android app." You are building a *system* whose core is portable and whose UI reaches the wrist.

The mental shift this week is from "share everything" to "share the *right* thing." The seductive promise of cross-platform — write once, run everywhere — is also its biggest trap, and the engineers who got burned by React Native and Flutter (some of whom are in this very cohort) got burned by sharing the *UI* across platforms and hitting the platform-channel ceiling. KMP makes a more disciplined bet: **share the business layer, not the UI layer (yet).** The domain model, the networking, the serialization, the time math, the validation rules — the parts that are genuinely platform-agnostic — live in `commonMain` and compile to every target. The UI — which must feel native, obey each platform's conventions, and use each platform's accessibility and input model — stays platform-specific: Compose on Android, SwiftUI on iOS. The skill this week earns is drawing that line *correctly*, and knowing the `expect`/`actual` escape hatch for the handful of things that are common in *shape* but platform-specific in *implementation* (a UUID, the current time zone, a secure-storage handle).

The thing this week hammers on is that **KMP is a module strategy, not a UI strategy, and Wear OS is a different Compose, not a smaller phone.** A KMP `:shared-core` module is a normal Gradle module with extra source sets (`commonMain`, `androidMain`, `iosMain`) and a constraint: `commonMain` may only use KMP-compatible libraries (Ktor, kotlinx-serialization, kotlinx-coroutines, kotlinx-datetime — *not* Retrofit, *not* `java.time`, *not* anything JVM-only). And Compose for Wear OS is its *own* artifact set (`androidx.wear.compose.*`) with components built for a round, tiny, glanceable screen — `ScalingLazyColumn` instead of `LazyColumn`, `TimeText` baked into the scaffold, and entirely separate surfaces (tiles, complications) that aren't activities at all. You'll feel both constraints this week, and feeling them is how you learn what *not* to share and what *not* to port.

We close the week by building a KMP **`:shared-core` module** that exposes a typed `WeatherForecast` model, a Ktor-backed repository, and kotlinx-serialization wire format — consumed by an Android app and stubbed for iOS — and a first taste of a Compose for Wear OS screen that renders the forecast on the wrist. By Sunday you have a module whose `commonMain` compiles for both Android and iOS, an Android app that consumes it through a clean interface, an `expect`/`actual` pair for one genuinely platform-specific concern, and a Wear scaffold that proves you can render the shared model on a watch. That portability — "the same forecast model and repository run on a phone, a watch, and (stubbed) an iPhone, from one source set" — is the senior instinct this week installs, and it's the architectural spine of the capstone's `:shared-core` and `:wear` modules.

## Learning objectives

By the end of this week, you will be able to:

- **Explain** what Kotlin Multiplatform shares and what it deliberately does not — the business layer in `commonMain`, the UI per platform — and articulate *why* sharing UI across platforms is the trap KMP avoids.
- **Set up** a KMP module with `commonMain`, `androidMain`, and `iosMain` source sets, the `kotlin { }` multiplatform block, and the right targets, and reason about which dependency belongs in which source set.
- **Use** `expect`/`actual` to declare a common API in `commonMain` and provide platform implementations in `androidMain`/`iosMain` — for the handful of concerns that are common in shape but platform-specific in implementation.
- **Pick** KMP-friendly libraries (Ktor Client, kotlinx-serialization, kotlinx-coroutines, kotlinx-datetime) over their JVM-only equivalents (Retrofit, `java.time`), and explain the compatibility constraint that drives the choice.
- **Consume** a KMP `:shared-core` module from an Android app through a clean interface, mapping shared domain types to UI types at the boundary (the Now-In-Android discipline from Week 12, now across a module boundary).
- **Describe** Compose Multiplatform's scope and status — UI shared on Android/desktop/iOS — and judge honestly when it's ready for the business you're building and when KMP-business-plus-native-UI is the safer bet.
- **Build** a first Compose for Wear OS screen: the Wear `Scaffold`, `TimeText`, `ScalingLazyColumn`, Wear Material components, and an understanding that tiles and complications are separate glanceable surfaces, not activities.

## Prerequisites

This week assumes you have completed **C21 weeks 1–18**, or have equivalent fluency. Specifically:

- You are fluent in coroutines, Flow, and serialization — Weeks 4–5, 15. `commonMain` code is coroutines-and-Flow code; the shared repository exposes a `Flow`, and the wire format is kotlinx-serialization. If those are shaky, the shared core won't make sense.
- You can build a Compose screen and reason about state — Weeks 7–12. Compose for Wear OS *is* Compose, with a different component set; everything you learned about composition, recomposition, and state carries to the wrist.
- You understand multi-module Gradle with the Kotlin DSL and version catalogs — Week 6, 13. A KMP module is a Gradle module with extra source sets; you need to be comfortable in `build.gradle.kts`.
- You know the MVVM-with-UDF boundary and mapping domain types to UI types — Week 12. Consuming a shared core cleanly is that same boundary discipline, now across a module that targets two platforms.
- You've built a networking layer with a typed `NetworkResult` — Week 15. The shared repository returns exactly that shape, now over Ktor instead of Retrofit so it compiles for iOS too.

**Toolchain.** Android Studio Ladybug (2024.2) or newer with the Kotlin Multiplatform plugin, JDK 17, Kotlin 2.0+. The Kotlin Multiplatform Gradle plugin (`org.jetbrains.kotlin.multiplatform`). Ktor Client 3.x, kotlinx-serialization 1.7+, kotlinx-coroutines 1.9+, kotlinx-datetime 0.6+. Compose for Wear OS from the Wear Compose BOM (`androidx.wear.compose:compose-material3`, `compose-foundation`, `compose-navigation`). A Wear OS emulator (a round Wear API 34 image is the reference) for the Wear screen; the iOS target *compiles* on any OS but only *runs* a simulator on macOS — students on Linux/Windows stub iOS and verify it compiles, which is the week's actual requirement.

## Topics covered

- **What KMP is and isn't.** Sharing the business layer, not the UI; `commonMain` as the shared source set; the bet KMP makes versus React Native/Flutter; "the business layer is where the real logic and the real bugs live, and the UI is where the platform conventions live."
- **KMP module setup.** The `kotlin { }` multiplatform block, declaring `androidTarget()` and `iosX64()`/`iosArm64()`/`iosSimulatorArm64()`, the `sourceSets { }` hierarchy (`commonMain`, `androidMain`, `iosMain`, and the `commonTest`/platform test sets), and dependency placement.
- **`expect`/`actual`.** Declaring an `expect fun`/`expect class` in `commonMain` and providing `actual` implementations per platform; the common-shape-platform-implementation pattern; what it's for (UUID, time zone, secure storage, platform name) and what it's *not* for (don't `expect`/`actual` your way around bad design).
- **KMP-friendly libraries.** Ktor Client (multiplatform HTTP) vs. Retrofit (JVM-only); kotlinx-serialization (multiplatform) vs. Gson/Moshi; kotlinx-coroutines (multiplatform); kotlinx-datetime (multiplatform) vs. `java.time`. The constraint: `commonMain` can only depend on multiplatform libraries.
- **The shared core architecture.** A typed domain model in `commonMain`, a Ktor-backed repository returning `Flow<NetworkResult<T>>`, serialization at the wire boundary, and a clean interface the Android (and iOS) UI consumes — mapping shared types to UI types at the platform boundary.
- **Compose Multiplatform — the overview.** What it is (Compose UI compiled for Android, desktop, iOS, web), its current status, and the honest judgment of when it's production-ready for shared UI vs. when KMP-business-plus-native-UI is the safer architecture today.
- **Compose for Wear OS — the basics.** The Wear `Scaffold`, `TimeText` (the always-present clock), `ScalingLazyColumn` (the scaling/curving list built for a round screen), Wear Material 3 components (`Chip`, `Button`, `ToggleChip`), and how a Wear app differs from a phone app in layout and input.
- **Wear glanceable surfaces — the introduction.** Tiles (a swipeable glanceable surface, not an activity), complications (data your app provides *to* a watch face), and ongoing activities (a persistent surface for active tasks). What each is for, and why they're separate from your main Wear activity. (Deep authoring is Week 20.)
- **What to share, what to port, what to rebuild.** The decision framework: share the business layer (KMP), port the *concepts* of your UI to Wear's idioms (don't shrink the phone screen), and rebuild the glanceable surfaces fresh (a tile is not a screen).

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                                | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|----------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | What KMP is/isn't; the module strategy; `commonMain`/`androidMain`/`iosMain` |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | `expect`/`actual`; KMP-friendly libraries; the shared repository     |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Consuming the shared core from Android; Compose Multiplatform overview; challenge |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | Compose for Wear OS — Scaffold, ScalingLazyColumn; tiles/complications intro; challenge |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — the `:shared-core` KMP module + Android consumer       |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project — the iOS stub + the Wear forecast screen               |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                           |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                      | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./00-overview.md) | This overview (you are here) |
| [resources.md](./01-resources.md) | The KMP docs, the Ktor/serialization/datetime guides, the Compose Multiplatform and Compose for Wear OS docs, real KMP/Wear samples, and the canonical talks |
| [lecture-notes/01-kotlin-multiplatform-shared-core.md](./02-lecture-notes/01-kotlin-multiplatform-shared-core.md) | What KMP shares and doesn't; the module setup with `commonMain`/`androidMain`/`iosMain`; `expect`/`actual`; KMP-friendly libraries; the shared-core architecture; Compose Multiplatform overview |
| [lecture-notes/02-compose-for-wear-os-intro.md](./02-lecture-notes/02-compose-for-wear-os-intro.md) | Compose for Wear OS — the Wear `Scaffold`, `TimeText`, `ScalingLazyColumn`, Wear Material; tiles, complications, and ongoing activities introduced; what to share, port, and rebuild |
| [exercises/README.md](./03-exercises/00-overview.md) | Index of the three exercises |
| [exercises/exercise-01-what-to-share.md](./03-exercises/exercise-01-what-to-share.md) | Given a phone app's modules, decide for each whether it belongs in `commonMain`, an Android-only module, or split via `expect`/`actual` — and justify the line you draw |
| [exercises/exercise-02-expect-actual.kt](./03-exercises/exercise-02-expect-actual.kt) | Declare an `expect` API in `commonMain` and provide `actual` Android + iOS implementations for a platform-specific concern; keep the common code platform-agnostic |
| [exercises/exercise-03-wear-forecast-screen.kt](./03-exercises/exercise-03-wear-forecast-screen.kt) | Build a Compose for Wear OS screen: the Wear `Scaffold` with `TimeText`, a `ScalingLazyColumn` of forecast rows, and Wear Material components — rendering the shared model on the wrist |
| [challenges/README.md](./04-challenges/00-overview.md) | Index of the challenge |
| [challenges/challenge-01-shared-core-two-platforms.md](./04-challenges/challenge-01-shared-core-two-platforms.md) | Build a KMP `:shared-core` that compiles for Android *and* iOS, with a Ktor repository, a shared test in `commonTest`, and an `expect`/`actual` pair — proving real portability, not aspiration |
| [quiz.md](./05-quiz.md) | 13 questions on KMP, source sets, `expect`/`actual`, KMP-friendly libraries, Compose Multiplatform, and Compose for Wear OS |
| [homework.md](./06-homework.md) | Six practice problems for the week |
| [mini-project/README.md](./07-mini-project/00-overview.md) | Full spec for the `:shared-core` KMP module (typed `WeatherForecast`, Ktor repository, serialization), the Android consumer, the iOS stub, and a Wear forecast screen |

## The "share the right thing" promise

Every week of Crunch Droid adds one production-grade contract a senior reviewer actually checks. This week's:

> **The shared module must contain only what is genuinely platform-agnostic, and it must compile for every target it claims to support.** A `commonMain` that secretly depends on a JVM-only library (Retrofit, `java.time`, `java.util.UUID`) doesn't actually compile for iOS — it's "multiplatform" in name only, and the day someone tries the iOS build it collapses. Put only KMP-friendly code in `commonMain`, push platform specifics behind `expect`/`actual` or into platform source sets, and *prove* it by compiling the iOS target (even if you can't run it). A shared core that doesn't compile for its second platform is a single-platform module wearing a costume.

You will *prove* this in the mini-project and challenge: the `:shared-core` module's `commonMain` compiles for `androidTarget()` *and* an iOS target, with a `commonTest` that runs on both. If you accidentally reach for a JVM-only API, the iOS compile fails — and that failure is the feature, the compiler enforcing the discipline. The Android app consumes the shared core through a clean interface, and the same model renders on a Wear screen. Portability you can compile is portability; portability you only assert is a future bug.

## A note on what's not here

Week 19 is the *KMP-overview and Wear-introduction* week. It deliberately does **not** cover:

- **Deep Wear OS.** `ScalingLazyColumn` tuning, full tile and complication *authoring*, ongoing-activity wiring, and Wear navigation in depth are **Week 20** (multi-form-factor). This week is the *first* Wear screen and the *concept* of tiles/complications, not their full implementation.
- **Foldables and adaptive layouts.** Window size classes, `WindowInfoTracker`, and hinge-aware layout are **Week 20**. This week is two form factors (phone + wrist); Week 20 adds the rest.
- **A full iOS app.** You *compile* the iOS target and *stub* the Swift side; you do not build a SwiftUI app. The point is proving the shared core is portable, not shipping on the App Store. (Students on macOS may run the iOS simulator as a stretch; it isn't required.)
- **Compose Multiplatform as the UI.** We cover it as an *overview* and judge its readiness. The mini-project uses native UI per platform (Compose on Android), the recommended-today architecture; shared Compose UI is a forward-looking note, not this week's build.

The point of Week 19 is narrow and deep: one portable business core that compiles for two platforms, and one first screen on a second form factor — the architectural spine of a multi-form-factor system.

## Up next

Continue to **Week 20 — Multi-form-factor: foldables, Wear OS deep, TV and Automotive overview** once you have shipped this week's mini-project: a `:shared-core` that compiles for Android and iOS, consumed by an Android app, with a first Wear forecast screen. Week 20 goes deep on the form factors this week introduced: window size classes and adaptive layouts for foldables, `ScalingLazyColumn` and full tile/complication authoring for Wear, and a tour of TV and Automotive so you know what *not* to build for them in a 24-week course. The shared core you build this week is the foundation — Week 20's Wear companion to the weather app consumes the *same* `WeatherForecast` model and Ktor repository you just made portable. You finish this week with a core that travels and a foot on the wrist; Week 20 builds the rest of the multi-form-factor body around it.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
