# Week 09 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 09 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Android Studio Ladybug+, Kotlin 2.0+, the Compose Compiler plugin, compileSdk 35, minSdk 24. Every problem must build with **0 warnings**.

---

## Problem 1 — A custom badge layout

**Problem statement.** Write a custom `Layout` called `BadgedBox` that places a main content composable and positions a small "badge" composable at its top-right corner, overlapping the edge by half the badge's size (like an unread-count badge on an icon). Implement the measure/place contract; the badge must be measured once and placed at `(content.width - badge.width/2, -badge.height/2)`.

**Acceptance criteria.**

- A custom `Layout` measuring both children once, sizing itself to the content, and placing the badge overlapping the top-right corner.
- A demo: an icon with a "3" badge clipped to a circle at its corner.
- Respects incoming constraints. 0 warnings. Committed.

**Hint.** Measure content first, then the badge. Your layout's size is the content's size (the badge overhangs). Place content at `(0,0)`, badge at the computed corner offset. Use `placeRelative`.

**Estimated time.** 45 minutes.

---

## Problem 2 — The modifier-order quiz, written

**Problem statement.** Without running anything, predict in `notes/modifier-order.md` the result of six modifier chains (provided below), then render them to check. Chains: (a) `size(100).border(2.dp,Black).padding(10)`; (b) `padding(10).size(100).border(...)`; (c) `clip(CircleShape).clickable{}.background(Blue)`; (d) `background(Blue).clip(CircleShape).clickable{}`; (e) `clickable{}.size(48.dp)`; (f) `size(48.dp).clickable{}`. For each, state the visual result, the total size, and (for c–f) the tappable region and shape.

**Acceptance criteria.**

- `notes/modifier-order.md` has predictions written *before* rendering, plus confirmed results.
- Correctly explains where the border/clip/touch-target lands for each.
- Committed.

**Hint.** Draw each as nested boxes outer-to-inner. `clip` only affects drawing *after* it in the chain; `clickable` includes everything *after* it in its touch target.

**Estimated time.** 35 minutes.

---

## Problem 3 — A pinch-to-zoom image

**Problem statement.** Build an image (or colored box) that supports pinch-to-zoom and pan with `detectTransformGestures`. Track `scale`, `offset` (and optionally `rotation`) in hoisted state, apply them via `graphicsLayer { }` (so the transform reads in the draw phase), and clamp scale to `[1f, 4f]`.

**Acceptance criteria.**

- `detectTransformGestures` updates `scale`/`offset`; the image zooms and pans.
- The transform is applied via `graphicsLayer { }` (draw phase), not by recomposing.
- Scale is clamped; a double-tap resets to 1f (use `detectTapGestures(onDoubleTap = ...)` in a separate `pointerInput`).
- 0 warnings. Committed.

**Hint.** `detectTransformGestures { centroid, pan, zoom, rotation -> scale = (scale*zoom).coerceIn(1f,4f); offset += pan }`. Apply with `graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; ... }`.

**Estimated time.** 50 minutes.

---

## Problem 4 — `animate*AsState` vs `Animatable`

**Problem statement.** Build a toggle that, when on, animates a box's size and color. Implement it two ways: (1) with `animateDpAsState` + `animateColorAsState` (declarative); (2) with `Animatable`s driven from a `LaunchedEffect`. Write `notes/animation-apis.md` explaining which is simpler here (the declarative one) and *when* you'd need the `Animatable` version (gesture-driven/interruptible).

**Acceptance criteria.**

- Both versions animate identically on toggle.
- `notes/animation-apis.md` correctly argues the declarative version is right for fire-and-forget state transitions, and `Animatable` is for interruptible/gesture-driven motion.
- 0 warnings. Committed.

**Hint.** For most "state changed, smoothly move there" cases, `animate*AsState` is the right tool — fewer moving parts. Reserve `Animatable` for when you need `snapTo`, velocity, or interruption. This problem is about *recognizing* which case you're in.

**Estimated time.** 40 minutes.

---

## Problem 5 — A WCAG contrast linter

**Problem statement.** Write a small function `auditPalette(pairs: List<Pair<Color, Color>>): List<AuditResult>` that runs every (foreground, background) pair through the contrast ratio and reports pass/fail at AA (4.5:1 normal). Feed it your app's actual color pairs. Write a JUnit test that asserts a deliberately-bad pair fails and your fixed palette passes. Record the audit table in `notes/contrast-audit.md`.

**Acceptance criteria.**

- `auditPalette` computes ratios and AA pass/fail per pair (reuse exercise 3's math).
- A test fails a bad pair (e.g. `#999999` on white) and passes your corrected palette.
- `notes/contrast-audit.md` has the table (pair, ratio, pass/fail).
- 0 warnings. Committed.

**Hint.** Reuse `contrastRatio`/`passesAA` from exercise 3. `data class AuditResult(val fg: Color, val bg: Color, val ratio: Double, val passes: Boolean)`. Sanity-check: black/white must be ~21:1.

**Estimated time.** 40 minutes.

---

## Problem 6 — A fully accessible custom toggle

**Problem statement.** Build a custom toggle switch (a draggable thumb in a track, *not* the Material `Switch`) that's fully accessible: it has `role = Role.Switch`, a `stateDescription` of "On"/"Off", a `toggleable`/`onClick` action so TalkBack can flip it without the drag, a ≥48dp touch target, and AA-passing colors. Verify with TalkBack on.

**Acceptance criteria.**

- A custom-drawn toggle (track + thumb) that flips on tap/drag.
- `semantics`: `role = Role.Switch`, `stateDescription`, and a toggle action operable by TalkBack (no drag required).
- ≥48dp touch target; AA-passing track/thumb colors.
- Verified by operating it with TalkBack on (note the announcement in `notes/toggle-a11y.md`).
- 0 warnings. Committed.

**Hint.** `Modifier.semantics { role = Role.Switch; stateDescription = if (on) "On" else "Off"; toggleableState = ... }` plus an `onClick`/`toggleable` so TalkBack double-tap flips it. The drag is the sighted affordance; the toggle action is the screen-reader one — both must work, like the card's swipe + custom action.

**Estimated time.** 50 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, code is idiomatic Compose, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. a transform read in composition instead of `graphicsLayer`, a slightly-off contrast computation). |
| 3 | Works, but misses one criterion (e.g. the custom toggle works by drag but has no TalkBack action, a child measured twice). |
| 2 | Compiles and partially works; a core idea is wrong (a gesture-only control with no accessibility equivalent; a `LinearEasing` spring-back). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for a gesture-only interaction with no equivalent accessibility action (it excludes screen-reader users); **−2** for a text/UI color pair that fails WCAG AA where the problem asked you to meet it; **−1** for reading an animating value in composition where a layout/draw-phase read (`offset { }`, `graphicsLayer { }`) was the point.

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — gesture-driven animation with `Animatable` (problems 3, 4) and accessibility-by-default with semantics and contrast (problems 5, 6) — so re-run exercises 02 and 03 before resubmitting.
