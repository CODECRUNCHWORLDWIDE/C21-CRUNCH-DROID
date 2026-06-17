# Week 09 — Resources

Every primary resource on this page is **free**. Android's developer documentation is free. The WCAG spec is a free W3C standard. The AndroidX source is public on Android Code Search. The talks are free on YouTube. A couple of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"Custom layouts in Compose."** The `Layout` composable, the measure/place contract, `MeasurePolicy`, and `Constraints`. Read before you write your first custom layout:
  <https://developer.android.com/develop/ui/compose/layouts/custom>
- **"Compose modifiers — order matters."** The modifier-ordering reference; the chain as an ordered pipeline. This is the single most important page for Tuesday:
  <https://developer.android.com/develop/ui/compose/modifiers>
- **"Gestures in Compose."** `pointerInput`, `detectTapGestures`/`detectDragGestures`/`detectTransformGestures`, touch slop, and the raw pointer loop:
  <https://developer.android.com/develop/ui/compose/touch-input/pointer-input>
- **"Animations in Compose — overview."** The decision guide that maps your situation to the right animation API:
  <https://developer.android.com/develop/ui/compose/animation/introduction>
- **"Accessibility in Compose."** Semantics, `contentDescription`, `stateDescription`, custom actions, merge/clear semantics, touch targets — the heart of Thursday:
  <https://developer.android.com/develop/ui/compose/accessibility>

## Layout and modifiers (deeper)

- **"Constraints and modifier order"** — how constraints flow and how `size`/`requiredSize`/`padding` interact:
  <https://developer.android.com/develop/ui/compose/layouts/constraints-modifiers>
- **"Intrinsic measurements"** — `IntrinsicSize.Min`/`Max` and their cost:
  <https://developer.android.com/develop/ui/compose/layouts/intrinsic-measurements>
- **`SubcomposeLayout`** — when single-pass layout isn't enough (measure-then-compose):
  <https://developer.android.com/reference/kotlin/androidx/compose/ui/layout/SubcomposeLayout>

## Animation (the full family)

- **"Value-based animations"** — `animate*AsState`, `Animatable`, `updateTransition`:
  <https://developer.android.com/develop/ui/compose/animation/value-based>
- **"Animated visibility and content"** — `AnimatedVisibility`, `AnimatedContent`, `Crossfade`:
  <https://developer.android.com/develop/ui/compose/animation/composables-modifiers>
- **"Customize animations"** — `tween`, `spring`, `keyframes`, easing, `AnimationSpec`:
  <https://developer.android.com/develop/ui/compose/animation/customize>
- **"Gesture + animation"** — combining `pointerInput` with `Animatable` for interruptible, velocity-aware motion (the mini-project pattern):
  <https://developer.android.com/develop/ui/compose/touch-input/pointer-input/scroll#animate-scrolling>

## Accessibility and WCAG (the must-verify part)

- **"Build accessible apps"** (Android, framework-wide) — touch targets, focus, labels:
  <https://developer.android.com/guide/topics/ui/accessibility>
- **WCAG 2.2 — Contrast (Minimum) 1.4.3** (the AA contrast success criterion, the actual standard):
  <https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html>
- **WCAG — Non-text Contrast 1.4.11** (3:1 for UI components and graphical objects):
  <https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html>
- **The contrast-ratio formula** (relative luminance + the (L1+0.05)/(L2+0.05) ratio) — used in exercise 3:
  <https://www.w3.org/WAI/GL/wiki/Relative_luminance>
- **WebAIM Contrast Checker** — paste two hex colors, get the ratio and AA/AAA pass/fail:
  <https://webaim.org/resources/contrastchecker/>
- **Accessibility Scanner** (Play Store app) and **Espresso accessibility checks** — automated audits:
  <https://support.google.com/accessibility/android/answer/6376570>

## Talks (free, watch in this order)

- **"Compose layouts and modifiers"** (Google I/O / ADS) — the measure/place model and the modifier pipeline in motion:
  <https://www.youtube.com/watch?v=zMKMwh9gZuI>
- **"Animation in Jetpack Compose"** — the API family and when to use each:
  <https://www.youtube.com/watch?v=Z_T2Wku0g3o>
- **"Making apps accessible with Compose"** — semantics, TalkBack, custom actions, demoed:
  <https://www.youtube.com/watch?v=BgQg5Bp4Sb0>
- **"Gesture handling in Compose"** — `pointerInput` and the gesture detectors, including drag + transform.

## The source, when you want the contract

- **`androidx.compose.ui.layout`** — `Layout`, `MeasurePolicy`, `Placeable`, `Constraints`:
  <https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/layout/>
- **`androidx.compose.ui.semantics`** — `SemanticsProperties`, `SemanticsActions`, the semantics DSL:
  <https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/semantics/>
- **`androidx.compose.foundation.gestures`** — `detectDragGestures`, `detectTransformGestures`, `draggable`:
  <https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/gestures/>

## Community writing (current, opinionated, correct)

- **Chris Banes' blog** — practical Compose layout, gesture, and accessibility from a former Android team engineer:
  <https://chrisbanes.me/>
- **Halil Ozercan / "Compose internals" community** — custom-layout and pointer-input deep dives.
- **Android Developers Medium** — the official accessibility and animation series:
  <https://medium.com/androiddevelopers>
- **Tal Kol / "Building a custom gesture in Compose"** and similar community write-ups on `awaitPointerEventScope`.

## Open-source projects to read this week

- **`android/compose-samples`** — Jetsnack's custom layouts and the gesture/animation patterns in Crane and Owl:
  <https://github.com/android/compose-samples>
- **`google/accessibility-test-framework`** — the engine behind the Accessibility Scanner; instructive for what "accessible" is checked against:
  <https://github.com/google/Accessibility-Test-Framework-for-Android>
- **`android/nowinandroid`** — read its `semantics` usage and how it labels interactive elements:
  <https://github.com/android/nowinandroid>

## Tools you'll use this week

- **TalkBack** — Android's screen reader, in every system image. Enable: Settings ▸ Accessibility ▸ TalkBack, or the volume-key shortcut. **You must turn this on** to verify the accessibility work — it's the test.
- **Accessibility Scanner** (Play Store) — scans a screen and flags small touch targets, low contrast, and missing labels.
- **Layout Inspector** — shows the semantics tree alongside the UI tree (`View ▸ Tool Windows ▸ Layout Inspector`), so you can see what TalkBack will read.
- **WebAIM Contrast Checker / the formula in exercise 3** — compute contrast ratios for the WCAG audit.

## Free books (chapter-level, not whole books)

- **Android's "Compose: layouts," "Compose: animation," and "Compose: accessibility" codelabs** — guided, free, hands-on:
  <https://developer.android.com/courses/jetpack-compose/course>
- **"Jetpack Compose internals" (Jorge Castillo)** — the free early chapters cover the layout phase and the measure/place machinery at the runtime level:
  <https://jorgecastillo.dev/book/>

## Paid books (optional, clearly marked)

- **"Jetpack Compose internals" — Jorge Castillo** (paid for the full book). The definitive treatment of the layout phase and custom layout internals.
- **"Programming Android with Kotlin" — O'Reilly** (paid). Solid Compose UI chapters covering gestures and animation.

---

*If a link 404s, please open an issue so we can replace it.*
