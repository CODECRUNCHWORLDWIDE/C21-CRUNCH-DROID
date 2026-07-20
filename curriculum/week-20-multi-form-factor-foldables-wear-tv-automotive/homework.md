# Week 20 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 20 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Android Studio Ladybug+, Kotlin 2.0+, the Compose Compiler plugin, compileSdk 35, minSdk 24 (26 for Wear). Every problem must build with **0 warnings**.

---

## Problem 1 — Drive a navigation surface from the window size class

**Problem statement.** Build a screen with a top-level navigation surface that adapts: a **bottom navigation bar** on COMPACT, a **navigation rail** on MEDIUM, and a **permanent navigation drawer** on EXPANDED. Drive the choice from `currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass`. Run on the Resizable emulator and confirm the navigation surface changes as you resize, live. Write into `notes/nav-adaptation.md` which surface appears at which class and why.

**Acceptance criteria.**

- Three navigation surfaces, one per width class, chosen reactively (no `isTablet`).
- The surface reflows live when you change the emulator's display mode.
- `notes/nav-adaptation.md` records the mapping and the reasoning.
- 0 warnings. Committed.

**Hint.** `NavigationSuiteScaffold` (from `material3-adaptive-navigation-suite`) does exactly this for you — but build it once by hand first with a `when (widthClass)` so you understand what the suite automates, then note that the suite is the production path.

**Estimated time.** 45 minutes.

---

## Problem 2 — A hinge-aware media layout

**Problem statement.** Build a "player" screen that, in tabletop posture, puts a content area in the top half and controls in the bottom half (split at the hinge), and in flat/non-fold mode uses a single overlaid layout. Observe fold state with `WindowInfoTracker`. Pose the hinge on the foldable emulator and confirm the layout reflows. Record the postures you tested in `notes/postures.md`.

**Acceptance criteria.**

- A tabletop branch (content top, controls bottom) and a flat branch (single surface), chosen from `FoldingFeature`.
- Reflows live when the hinge is posed to half-opened-horizontal.
- `notes/postures.md` records FLAT, TABLETOP (and BOOK if you handle it) and what each rendered.
- 0 warnings. Committed.

**Hint.** Use the `rememberFoldState` helper from lecture 1, §4. Tabletop = `HALF_OPENED` + `HORIZONTAL`. A `Column { Top(Modifier.weight(1f)); Bottom(Modifier.weight(1f)) }` is enough for the split; you don't need the exact hinge bounds for this problem.

**Estimated time.** 45 minutes.

---

## Problem 3 — Predict-then-confirm pane reflow

**Problem statement.** Take exercise 1's list-detail screen. *Before running*, write into `notes/pane-predictions.md` your prediction of (a) the pane count, (b) what tapping a list item does, and (c) what back does, for each of COMPACT, MEDIUM, EXPANDED. Then run it on the resizable emulator across all three and record any surprises.

**Acceptance criteria.**

- `notes/pane-predictions.md` has the prediction written before the run, plus the confirmed result.
- The three behaviors (pane count, tap, back) are correct per class.
- 0 warnings. Committed.

**Hint.** COMPACT = one pane (tap pushes detail, back pops to list); MEDIUM/EXPANDED = two panes (tap swaps detail in place, back is handled within the two-pane view). If a prediction is wrong, that's the learning — note why.

**Estimated time.** 40 minutes.

---

## Problem 4 — A Wear screen that respects the round constraints

**Problem statement.** Build a small Wear screen: a scaling list of five items in `AppScaffold`/`ScreenScaffold`, with rotary input and the swipe-dismiss nav host to a detail. Then deliberately build a *wrong* version using a phone `LazyColumn` and the phone `NavHost`. Screenshot both on the round emulator and record in `notes/wear-vs-phone.md` what looks/behaves wrong in the phone version (edge clipping, broken back swipe, dead crown).

**Acceptance criteria.**

- A correct Wear version (`TransformingLazyColumn`, `SwipeDismissableNavHost`, `rotaryScrollable`) and a wrong (phone-component) version.
- `notes/wear-vs-phone.md` names at least three concrete differences with screenshots.
- 0 warnings. Committed.

**Hint.** The three giveaways: items clipped hard against the curved edge (no scaling), left-edge swipe doesn't go back, and the crown/bezel doesn't scroll. Exercise 2 is your correct template.

**Estimated time.** 50 minutes.

---

## Problem 5 — A tile that reads cached data

**Problem statement.** Build a `TileService` that shows a single cached value (a step count, a temperature — your choice) with the `protolayout-material3` builders, a freshness interval, and matching resource versions. Then add a "fake sync" function that updates the cache and calls `TileService.getUpdater(...).requestUpdate(...)`. Confirm the tile re-renders after the fake sync. Record the flow in `notes/tile-refresh.md`.

**Acceptance criteria.**

- A tile rendering cached data via ProtoLayout, with a freshness interval and matching versions. No fetch in `onTileRequest`.
- A fake-sync path that updates the cache and requests a tile update; the tile re-renders.
- `notes/tile-refresh.md` describes the sync→cache→requestUpdate flow.
- 0 warnings. Committed.

**Hint.** Exercise 3 is your template. The whole point: data flows *into* the cache from your sync, and the tile reads the cache. If you call the network in `onTileRequest`, you've misunderstood the surface.

**Estimated time.** 45 minutes.

---

## Problem 6 — The "what not to build" memo

**Problem statement.** Pick a real app idea (yours, or a well-known app). Write `notes/form-factor-strategy.md`: a one-page memo deciding, for *that* app, which of the five form factors (phone, foldable/tablet, Wear, TV, Automotive) you would build, which you would defer, and why — naming the key API each would use and the specific constraint that drives the decision (glanceability for Wear, distraction rules for Automotive, the 10-foot focus model for TV).

**Acceptance criteria.**

- A memo that decides build-vs-defer for all five form factors *for a specific app*, with reasoning.
- Each decision names the relevant API (`material3-adaptive`, Wear Compose/Tiles, `tv-material`, the Car App Library) and the driving constraint.
- The memo is honest about cost — it does *not* propose building all five.
- Committed.

**Hint.** Lecture 2, §8 is the template. The senior move is justified restraint: "we build phone + adaptive foldable + Wear because [glanceable use case]; we defer TV (marginal channel) and Automotive (distraction/certification cost unjustified) and document the APIs a future implementation would use."

**Estimated time.** 35 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, code is idiomatic, the right surface/API is used for each form factor, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. a `when (widthClass)` left where the suite/scaffold was the point, a hinge layout that doesn't reflow live). |
| 3 | Works, but misses one criterion (e.g. prediction written *after* running, tile refreshes only on a full reinstall, Wear version missing rotary input). |
| 2 | Compiles and partially works; a core idea is wrong (an `if (isTablet)` branch, a phone `LazyColumn` on Wear, a fetch in `onTileRequest`). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for a device-identity branch (`isTablet`, raw pixel width) where a window size class was the point; **−2** for fetching data on render in a tile or complication; **−1** for a Wear surface built with phone components (`LazyColumn`, phone `NavHost`) where the Wear equivalent was required.

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — adaptive layouts driven by window signals (problems 1, 2, 3) and the right Wear surface with the right constraints (problems 4, 5) — so re-run exercises 02 and 03 before resubmitting.
