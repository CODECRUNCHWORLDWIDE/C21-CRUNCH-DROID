# Week 20 — Multi-form-factor: foldables, Wear OS deep, TV and Automotive overview

Welcome to Week 20 of **C21 · Crunch Droid**. Last week (Week 19) you split a `:shared-core` module into Kotlin Multiplatform code and met Compose for Wear OS for the first time — tiles, complications, ongoing activities, as a guided tour. This week the tour ends and the engineering begins. Android is not one screen anymore. It is a phone, a foldable that is a phone *and* a tablet depending on the hinge, a watch with a 1.4-inch round display and a rotating crown, a television three metres away driven by a D-pad, and a car dashboard you are legally forbidden from making distracting. One Compose codebase has to render correctly on all of them, and "correctly" means something different on each.

The mental shift this week is from "design for a screen size" to **"design for a *window*, and let the window tell you what it is."** The phone-only instinct is to branch on `if (isTablet)` — a boolean you compute from screen width in pixels, which is wrong the moment a foldable unfolds mid-session, wrong on a tablet in split-screen, wrong in a free-form window on a Chromebook. The modern instinct is to read **window size classes** (`WindowSizeClass`, now first-class in the `material3-adaptive` libraries) and **fold state** (`WindowInfoTracker`, `FoldingFeature`) as *observable inputs to your composition*, and to let a single adaptive layout reflow itself. The same `NavigableListDetailPaneScaffold` shows a list on a compact phone, a list-detail split on an unfolded foldable or a tablet, and reacts live when the hinge opens. You write the layout once; the window class drives the rest.

The thing this week hammers on is that **Wear OS is not a small phone.** It is the form factor people most often get wrong, because the APIs *look* like phone Compose — it is still `@Composable`, still `Modifier`, still recomposition — but the design constraints invert. The list component is `TransformingLazyColumn` (the modern Wear 4/5 replacement for `ScalingLazyColumn`), not `LazyColumn`, because content scales and fades toward the curved edges of a round screen. Navigation is `SwipeDismissableNavHost`, because the system swipe-to-dismiss gesture owns the left edge. The screen is glanceable: a watch interaction is three seconds, not three minutes, so the real surface area is the **tile** (a glanceable card in the system carousel, built with the Tiles + ProtoLayout API, *not* general Compose) and the **complication** (a tiny data slot on a watch face). And the battery budget is brutal — an animation that is fine on a phone drains a watch. We go deep here because the syllabus says "Wear OS deep," because the capstone ships a real `:wear` module, and because this is the form factor that separates engineers who *ported* an app from engineers who *designed* for the wrist.

We close the week by building a **Wear OS companion to the Week-19 weather app** — a tile that shows the current forecast at a glance, a complication that drops the current temperature onto the user's watch face, and ongoing-activity support so an active rain alert appears in the system's ongoing-activity surfaces. Three Wear-specific surfaces, each built with the right (and *different*) API, each respecting the constraints of the wrist. TV and Automotive we treat as overview — you will learn what they are, the one or two APIs that matter (`tv-material` and the D-pad focus model for TV; the Car App Library templates and the distraction-optimization rules for Automotive), and — just as important — **what not to build** for them in a 24-week course. Knowing the boundary is itself a senior skill.

## Learning objectives

By the end of this week, you will be able to:

- **Read** window size classes (`WindowSizeClass.windowWidthSizeClass` / `windowHeightSizeClass` — `COMPACT`, `MEDIUM`, `EXPANDED`) as the input to an adaptive layout, and explain why a pixel-width branch is the wrong abstraction.
- **Build** an adaptive list-detail layout with `NavigableListDetailPaneScaffold` (or `ListDetailPaneScaffold`) that reflows from single-pane on a phone to two-pane on a tablet/unfolded foldable, with the back button restoring the list pane correctly.
- **Observe** fold state with `WindowInfoTracker.windowLayoutInfo` and a `FoldingFeature`, detect a hinge (`isSeparating`, `orientation`, `occlusionType`), and lay content out around it (tabletop posture, book posture).
- **Author** a Wear OS UI with the modern components: `TransformingLazyColumn` for the scaling list, `SwipeDismissableNavHost` for navigation, the Wear Material 3 (`androidx.wear.compose.material3`) component set, and rotary-input handling for the crown/bezel.
- **Build** a Wear **tile** with the Tiles + ProtoLayout API (`TileService`, `ProtoLayout`, resource versioning, freshness intervals) — and explain why a tile is *not* a `@Composable`.
- **Build** a Wear **complication** with `ComplicationDataSourceService`, supplying the right `ComplicationType` (SHORT_TEXT, RANGED_VALUE, MONOCHROMATIC_IMAGE) for each watch-face slot.
- **Surface** an **ongoing activity** with the `OngoingActivity` API so an active task (a rain alert) appears on the watch's ongoing-activity surfaces and stays in sync with its notification.
- **Describe** Android TV and Automotive at the level of "what they are, the key API, the distraction/focus rules, and what a small team should *not* attempt" — `tv-material`, the D-pad focus model, the Car App Library, and the driver-distraction guidelines.

## Prerequisites

This week assumes you have completed **C21 weeks 1–19**, or have equivalent fluency. Specifically:

- You can write idiomatic Jetpack Compose — composables, `Modifier` chains, state hoisting, the three phases, recomposition and stability — Weeks 7–9. Wear Compose *is* Compose; everything you know about recomposition and skippability transfers, and battery makes it matter more.
- You understand `StateFlow<UiState>`, the Now-In-Android MVVM-with-UDF pattern, and Hilt-wired ViewModels — Weeks 12–13. The Wear companion consumes the same domain layer; the tile and complication services read it too.
- You have a working KMP `:shared-core` and a Compose for Wear OS introduction from **Week 19**. This week's mini-project is the companion to that exact app — if you skipped Week 19's `WeatherForecast` model and Ktor repository, build a minimal stub first.
- You are fluent with coroutines, `Flow`, and `WorkManager` enough to know that a tile's data has to come from *somewhere* cheap and cached — Weeks 4–5, 16. A tile that does a network call on render is a bug.

**Toolchain.** Android Studio Ladybug (2024.2) or newer, JDK 17, Kotlin 2.0+ with the Compose Compiler plugin. Compose BOM 2024.10+ and the `material3-adaptive` artifacts (`androidx.compose.material3.adaptive:adaptive`, `:adaptive-layout`, `:adaptive-navigation`). For Wear: `androidx.wear.compose:compose-material3`, `:compose-foundation`, `:compose-navigation`, the Tiles libraries (`androidx.wear.tiles:tiles`, `androidx.wear.protolayout:protolayout`, `:protolayout-material3`), the Watch Face Complications data-source library, and `androidx.wear:wear-ongoing`. Target SDK 35 (Android 15), minSdk 26 for the Wear module (Wear OS minimum). You need a **Wear OS emulator** (round, e.g. Wear OS Large Round API 34) and a **resizable / foldable emulator** (the "Resizable (Experimental)" device, or a Pixel Fold image) — both are free.

## Topics covered

- **Window size classes.** `WindowSizeClass` from `androidx.compose.material3.windowsizeclass` / the adaptive libraries: width and height buckets (`COMPACT < 600dp`, `MEDIUM 600–840dp`, `EXPANDED ≥ 840dp`), `currentWindowAdaptiveInfo()`, and why these are *breakpoints based on available window space*, not device identity.
- **Adaptive layouts.** The `material3-adaptive` scaffolds: `NavigableListDetailPaneScaffold`, `ListDetailPaneScaffold`, `SupportingPaneScaffold`, the three-pane model, `PaneAdaptedValue`, and how the scaffold reflows panes by window class automatically.
- **Foldable APIs.** `WindowInfoTracker.getOrCreate(context).windowLayoutInfo(activity)`, `WindowLayoutInfo.displayFeatures`, `FoldingFeature` — `state` (FLAT / HALF_OPENED), `orientation` (HORIZONTAL / VERTICAL), `isSeparating`, `occlusionType`. Tabletop and book postures, and laying out around the hinge `bounds`.
- **Compose for Wear OS — deep.** `androidx.wear.compose.material3`: `AppScaffold`, `ScreenScaffold`, `TransformingLazyColumn` (the Wear 4/5 scaling list; `ScalingLazyColumn` as the older equivalent you will still see), `Button`, `Chip`/`Card`, `TimeText`, `PositionIndicator`, edge-curved layout, and rotary input via `Modifier.rotaryScrollable`.
- **Wear navigation.** `SwipeDismissableNavHost` and `rememberSwipeDismissableNavController` — why phone `NavHost` is wrong on Wear (the system owns the swipe-back edge).
- **Tiles.** `TileService`, the Tiles + ProtoLayout model (`onTileRequest`, `onTileResourcesRequest`), `protolayout-material3` primitives, `freshnessIntervalMillis`, resource versioning, and why a tile is a *serialized layout the system renders*, not a live composition.
- **Complications.** `ComplicationDataSourceService`, `ComplicationType` (SHORT_TEXT, LONG_TEXT, RANGED_VALUE, MONOCHROMATIC_IMAGE, SMALL_IMAGE), `getPreviewData`, `onComplicationRequest`, and `ComplicationDataSourceUpdateRequester` to push refreshes.
- **Ongoing activities.** The `OngoingActivity` API layered on a notification, `OngoingActivityStatus`, the ongoing-activity surfaces (watch face, recents), and keeping the notification and ongoing activity consistent.
- **Android TV (overview).** `androidx.tv:tv-material`, the D-pad / focus model (`Modifier.focusable`, focus restoration, `TvLazyColumn`/`TvLazyRow` heritage now folded into Compose foundation lazy lists with focus), the 10-foot UI, and overscan-safe layouts.
- **Android Automotive (overview).** The Car App Library (`androidx.car.app`), template-based UIs (`ListTemplate`, `PaneTemplate`, `NavigationTemplate`), driver-distraction guidelines, and why you *cannot* ship arbitrary Compose to a moving car.
- **What not to build.** The senior judgment call: for a course-sized team, which form factors earn a full implementation (phone, foldable, Wear) and which earn an overview and a deferred backlog item (TV, Automotive).

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                                | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|----------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | Window size classes; adaptive scaffolds; the foldable APIs           |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | Fold state, postures; reflowing list-detail; predictive back on panes |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Wear OS deep — scaling lists, navigation, rotary; the design inversion |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | Tiles, complications, ongoing activities; TV + Automotive overview; challenge |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — Wear weather companion: scaffold, tile                |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work — complication + ongoing activity; measure power |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                           |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                      | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./README.md) | This overview (you are here) |
| [resources.md](./resources.md) | The adaptive-layout guides, the foldable codelab, the Wear OS Compose docs, the Tiles and Complications guides, the TV and Automotive docs, and the canonical talks |
| [lecture-notes/01-adaptive-layouts-window-size-classes-foldables.md](./lecture-notes/01-adaptive-layouts-window-size-classes-foldables.md) | Window size classes, the `material3-adaptive` scaffolds, fold state with `WindowInfoTracker`, postures, and one adaptive layout that reflows across phone, tablet, and foldable |
| [lecture-notes/02-wear-os-deep-tiles-complications-tv-automotive.md](./lecture-notes/02-wear-os-deep-tiles-complications-tv-automotive.md) | Wear OS deep — the design inversion, `TransformingLazyColumn`, navigation, rotary, tiles, complications, ongoing activities — then TV and Automotive as overview and the "what not to build" judgment |
| [exercises/README.md](./exercises/README.md) | Index of the three exercises |
| [exercises/exercise-01-window-size-class-adaptive-layout.md](./exercises/exercise-01-window-size-class-adaptive-layout.md) | Drive a layout from `WindowSizeClass`, reflow it across the resizable emulator, and observe a live fold with `WindowInfoTracker` |
| [exercises/exercise-02-wear-scaling-list-navigation.kt](./exercises/exercise-02-wear-scaling-list-navigation.kt) | Build a Wear screen with `TransformingLazyColumn`, `SwipeDismissableNavHost`, and rotary scroll; respect the round-screen constraints |
| [exercises/exercise-03-wear-tile-protolayout.kt](./exercises/exercise-03-wear-tile-protolayout.kt) | Author a Wear tile with `TileService` + ProtoLayout, version its resources, and set a freshness interval |
| [challenges/README.md](./challenges/README.md) | Index of the challenge |
| [challenges/challenge-01-one-codebase-three-form-factors.md](./challenges/challenge-01-one-codebase-three-form-factors.md) | Take one feature and ship it correctly on phone, unfolded foldable, and Wear — one shared domain layer, three form-factor-appropriate UIs — and document each design decision |
| [quiz.md](./quiz.md) | 13 questions on window size classes, fold state, Wear components, tiles, complications, ongoing activities, and the TV/Automotive boundary |
| [homework.md](./homework.md) | Six practice problems for the week |
| [mini-project/README.md](./mini-project/README.md) | Full spec for the Wear OS weather companion: an adaptive tile, a complication, and ongoing-activity rain alerts |

## The "right surface, right constraints" promise

Every week of Crunch Droid adds one production-grade contract a senior reviewer actually checks. This week's:

> **A multi-form-factor feature must use the *right* surface and respect the *right* constraints for each form factor — never a phone layout ported sideways.** A Wear screen uses a scaling list and the swipe-dismiss nav host because the round screen and the system swipe gesture demand it. A tile is a serialized ProtoLayout with a freshness interval, not a live composition doing a network call. A foldable layout reads fold state and reflows around the hinge, not a `width > 600` branch. If your watch app is your phone app shrunk, or your tile fetches on render, the review fails no matter how clean the code looks.

You will *prove* this in the mini-project: the same `WeatherForecast` domain model from Week 19, surfaced three ways on Wear — a tile (glanceable, cached, versioned resources), a complication (a single datum in the right `ComplicationType`), and an ongoing activity (a live rain alert synced to a notification) — each built with its own correct API, none of them a phone screen in disguise.

## A note on what's not here

Week 20 is the *form-factor breadth* week. It deliberately does **not** cover:

- **CI/CD and release for multiple targets.** Building, signing, and shipping a `:wear` APK alongside the phone AAB through GitHub Actions and the Play Console is **Week 21**. This week you build the Wear surfaces; next week you release them.
- **Security on the watch.** Keystore-backed token storage that the Wear app shares with the phone, and Play Integrity on a Wear context, is **Week 22**.
- **Deep custom layout and gesture work.** Custom `Layout`, advanced `pointerInput`, and the full animation toolkit were **Week 09**. This week reuses those skills on new form factors; it does not re-teach them.
- **Phone networking and persistence internals.** The tile and complication read a cached `WeatherForecast`; *how* that cache is populated (Room, DataStore, WorkManager sync) was Weeks 14–16. Here it is a dependency you consume.

The point of Week 20 is breadth with judgment: one adaptive layout that reflows everywhere, a genuinely Wear-native companion (not a port), and a clear-eyed overview of TV and Automotive that includes the discipline to *not* over-invest in them.

## Up next

Continue to **Week 21 — CI/CD: GitHub Actions, fastlane, Play Console API** once you have shipped this week's Wear companion and proven each surface uses the right API for its constraints. Week 21 takes everything you have built — the phone app, the foldable-adaptive UI, and now a `:wear` module — and wires the release pipeline that builds, tests, screenshots, and ships all of it: a signed AAB for the phone, a signed APK for the watch, Paparazzi screenshots, and a fastlane upload to the Play internal track on every tag. Multi-form-factor is only half the story; releasing multiple form factors without a manual checklist is the other half, and that is next week.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
