# Week 09 — Layout, gestures, animation, accessibility

Welcome to Week 09 of **C21 · Crunch Droid**. For two weeks you learned the Compose runtime (composition, recomposition, the three phases) and its state model (snapshots, side effects, surviving rotation). This week is the *toolkit* — the part of Compose you actually touch every day to make a screen feel like a polished, native, accessible app instead of a wall of stacked `Text`s. You will write a custom `Layout` that measures and places children by your own rules, learn why the *order* of `Modifier`s in a chain changes what you see and what you can touch, detect drags and transforms with `pointerInput`, animate state changes with the full animation API family, and make every one of those interactions work for a user navigating with TalkBack — because an app that a blind user can't operate is broken, no matter how beautiful it looks to everyone else.

The mental shift this week is from "I arrange components" to "I control measurement, placement, gesture, motion, and semantics — and they're all one ordered pipeline." A `Modifier` chain is not a bag of independent flags; it's an ordered sequence where each modifier wraps the next, so `padding().background()` and `background().padding()` produce visibly different pixels *and* different touch targets *and* different accessibility bounds. A custom `Layout` is the same `measure → place` contract the built-in `Column` uses, exposed for you to implement. A gesture is `pointerInput` reading raw pointer events and turning them into drag deltas you feed into hoisted state (Week 8). An animation is a value that interpolates over time, read in the right phase (Week 7) so it's smooth. And accessibility is the `semantics` you attach so the same screen that renders a card a sighted user swipes also tells TalkBack "double-tap and hold, then swipe up to dismiss." These are not five separate topics; they're five faces of the same "I'm building a real interactive surface" skill, and this week wires them together.

The thing this week hammers on is that **modifier order matters more than people realize, and accessibility is not a feature you bolt on at the end.** We will walk a half-dozen `Modifier` chains where reordering changes paint, touch targets, and what TalkBack announces — `clickable` before `padding` makes the padding tappable; after `padding`, it isn't; `size` before `padding` versus after changes the final dimensions. And we'll build every interactive component with `semantics` from the first line, because retrofitting accessibility onto a gesture-driven custom component is ten times harder than designing it in. The skill this week earns is building custom layouts, writing accessible Compose UIs *by default*, and timing animations so they feel native instead of toy-like — and proving the accessibility with TalkBack actually turned on, not assumed.

We close the week by building a **swipe-to-dismiss card stack**: cards you drag horizontally with elastic resistance (the further you pull, the more it resists), that spring back if you don't pull far enough and animate off-screen if you do, with full TalkBack support (the card announces itself and offers a "dismiss" custom action so a TalkBack user can dismiss without performing a swipe gesture they can't see), backed by a written **WCAG contrast audit** of the card's colors. That combination — a custom gesture with physical-feeling resistance, a spring animation that doesn't feel cheap, and accessibility that makes the gesture operable without sight, plus a contrast audit proving the colors are legible — is the senior, ship-it-to-real-users instinct this week installs.

## Learning objectives

By the end of this week, you will be able to:

- **Write** a custom `Layout` composable: implement the `measure`/`place` contract, pass constraints to children, measure them, and position them by your own algorithm (a flow layout, a staggered grid, a custom arc).
- **Order** a `Modifier` chain deliberately, predicting how reordering `padding`, `size`, `background`, `clickable`, `border`, and `clip` changes paint, touch target, and accessibility bounds — and explain *why* the chain is applied outside-in.
- **Detect** gestures with `pointerInput`: `detectDragGestures`, `detectTapGestures`, `detectTransformGestures`, and the lower-level `awaitPointerEventScope` loop; feed deltas into hoisted state and respect touch slop and velocity.
- **Animate** with the right API for the job: `animate*AsState` for single-value state transitions, `updateTransition` for coordinated multi-property animations, `AnimatedVisibility` for enter/exit, `AnimatedContent` for swapping content, and `Animatable` for gesture-driven and interruptible animations — using springs and tweens with timing that feels native.
- **Make** a component accessible by default: attach `semantics`, set `contentDescription`/`stateDescription`, merge or clear semantics with `mergeDescendants`/`clearAndSetSemantics`, add `customActions` for gesture-only interactions, and verify with TalkBack.
- **Audit** color contrast against WCAG AA (4.5:1 for normal text, 3:1 for large text and UI components), compute a contrast ratio, and fix a failing pair — and respect large-text and font-scale settings so a screen survives a 200% font scale.
- **Combine** all of the above into one component: a gesture-driven, spring-animated, fully-accessible interactive surface, the way real production components are built.

## Prerequisites

This week assumes you have completed **C21 weeks 1–8**, or have equivalent fluency. Specifically:

- You understand the Compose runtime and the **three phases (Week 7)**: layout and draw are two of them, and modifier ordering and deferred reads are exactly the phase discipline from Week 7 applied to interaction. Animations read in the right phase (Week 7) are why they're smooth.
- You can manage state and side effects (**Week 8**): a drag gesture writes a hoisted offset state; a spring-back is an `Animatable` driven from a `LaunchedEffect`/`rememberCoroutineScope`. Gesture handling *is* state management plus a `pointerInput` source.
- You are fluent in **coroutines (Week 4)**: gesture loops and `Animatable.animateTo` are suspend functions; a fling is a coroutine; cancellation (a new touch interrupting a spring) is structured concurrency.
- You can read and write idiomatic **Kotlin 2.x** — lambdas, scope functions, `with`/`run` — Weeks 1–3. The `MeasureScope`/`PointerInputScope` receivers are scoped lambdas exactly like the DSLs you learned to read.

**Toolchain.** Android Studio Ladybug (2024.2)+, JDK 17, Kotlin 2.0+ with the Compose Compiler plugin, compileSdk 35 (Android 15), minSdk 24. The Compose BOM pins `androidx.compose.*`. You'll use `androidx.compose.animation`, `androidx.compose.foundation` (gestures), and `androidx.compose.ui` (layout, semantics). **TalkBack** is the key tool — enable it on the emulator (it ships in the system image; toggle via Settings ▸ Accessibility, or the accessibility shortcut) because the accessibility work must be *verified*, not assumed.

## Topics covered

- **Custom `Layout`.** The `measure`/`place` contract: a `MeasurePolicy` receives `Constraints`, measures each child into a `Placeable` (a child can be measured exactly once), computes the layout's own size, and places each `Placeable` at an offset in the placement block. Single-pass measurement and why Compose forbids multi-pass by default. `Layout` vs `SubcomposeLayout` (and when you actually need the latter).
- **`Constraints` and intrinsic measurements.** Min/max width and height bounds flowing down; `Modifier.requiredSize` vs `size`; intrinsic sizing (`IntrinsicSize.Min`/`Max`) for "size me to my tallest child" cases and their cost.
- **`Modifier` chains and ordering.** A chain is an ordered, outside-in pipeline. How order changes layout (`size` then `padding` vs `padding` then `size`), paint (`background` then `padding` vs reverse), touch (`clickable` before/after `padding`), and clipping (`clip` then `background` vs reverse). The mental model: each modifier wraps the rest of the chain.
- **`pointerInput` and gesture detectors.** `detectTapGestures` (tap, double-tap, long-press, press), `detectDragGestures` (and `detectDragGesturesAfterLongPress`), `detectTransformGestures` (pan/zoom/rotate), and the raw `awaitPointerEventScope` loop. Touch slop, pointer IDs, consuming events, and velocity tracking.
- **Animation APIs.** `animate*AsState` (Float, Dp, Color, Offset, …) for fire-and-forget single-value animation; `updateTransition` for coordinating several properties off one state; `AnimatedVisibility` for enter/exit transitions; `AnimatedContent` for content swaps with a `transitionSpec`; `Crossfade`; and `Animatable`/`Animation` for gesture-driven, interruptible, velocity-aware animation.
- **Animation specs and timing.** `tween` (duration + easing), `spring` (stiffness + damping, physics-based, the native-feeling default), `keyframes`, `repeatable`/`infiniteRepeatable`. Why springs feel better than fixed-duration tweens for interaction, and choosing easing that doesn't feel robotic.
- **Compose semantics.** The semantics tree (a parallel tree to the UI tree, the thing accessibility services read). `contentDescription`, `stateDescription`, `role`, `heading`, `liveRegion`. `Modifier.semantics { }`, `mergeDescendants`, `clearAndSetSemantics`, and `customActions` for exposing gesture-only interactions to TalkBack and Switch Access.
- **Accessibility compliance.** Minimum touch target (48dp), `contentDescription` for non-text controls, `stateDescription` for toggles, focus order, and `liveRegion` for dynamic announcements. Testing with TalkBack and the Accessibility Scanner.
- **WCAG contrast and large text.** The contrast-ratio formula, AA thresholds (4.5:1 normal, 3:1 large/UI), computing and fixing a failing pair, and respecting the system font scale (`sp` over `dp` for text, testing at 200%) so a large-text user isn't excluded.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                                | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|----------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | Custom `Layout`; constraints; the `measure`/`place` contract         |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | `Modifier` ordering; `pointerInput` and gesture detectors            |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Animation APIs; springs vs tweens; gesture-driven `Animatable`       |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | Semantics, TalkBack, custom actions; WCAG contrast; challenge        |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — swipe-to-dismiss card stack; elastic drag             |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work; spring-back, accessibility actions, audit    |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                           |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                      | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./00-overview.md) | This overview (you are here) |
| [resources.md](./01-resources.md) | The custom-layout and modifier docs, the gesture and animation guides, the accessibility docs, the WCAG contrast spec, and the canonical talks |
| [lecture-notes/01-custom-layout-modifiers-gestures.md](./02-lecture-notes/01-custom-layout-modifiers-gestures.md) | Custom `Layout` and the measure/place contract, the `Modifier` chain as an ordered pipeline (with the reorder gallery), and `pointerInput` gesture detection end to end |
| [lecture-notes/02-animation-semantics-accessibility.md](./02-lecture-notes/02-animation-semantics-accessibility.md) | The animation API family and spec/timing, Compose semantics and TalkBack, custom accessibility actions, and WCAG contrast + large-text compliance |
| [exercises/README.md](./03-exercises/00-overview.md) | Index of the three exercises |
| [exercises/exercise-01-modifier-order-and-custom-layout.md](./03-exercises/exercise-01-modifier-order-and-custom-layout.md) | Predict-then-confirm a modifier-reorder gallery, then write a custom flow `Layout` from the measure/place contract |
| [exercises/exercise-02-drag-with-spring-back.kt](./03-exercises/exercise-02-drag-with-spring-back.kt) | Detect a horizontal drag with `pointerInput`, feed it into an `Animatable`, and spring back on release |
| [exercises/exercise-03-semantics-and-contrast.kt](./03-exercises/exercise-03-semantics-and-contrast.kt) | Add semantics + a custom action to a gesture component, and write a WCAG contrast-ratio check that fails a bad color pair |
| [challenges/README.md](./04-challenges/00-overview.md) | Index of the challenge |
| [challenges/challenge-01-inaccessible-then-fixed.md](./04-challenges/challenge-01-inaccessible-then-fixed.md) | Take a gesture-only component that's invisible to TalkBack and fails contrast, audit it, fix it to full accessibility + AA contrast, and document the before/after with TalkBack and a contrast report |
| [quiz.md](./05-quiz.md) | 13 questions on custom layout, modifier ordering, gestures, the animation family, semantics, and WCAG contrast |
| [homework.md](./06-homework.md) | Six practice problems for the week |
| [mini-project/README.md](./07-mini-project/00-overview.md) | Full spec for the swipe-to-dismiss card stack: elastic drag, spring-back, TalkBack custom actions, WCAG contrast audit |

## The "operable without sight" promise

Every week of Crunch Droid adds one production-grade contract a senior reviewer actually checks. This week's:

> **Every interaction must be operable without sight, and every color pair must pass WCAG AA.** Turn on TalkBack and operate your screen end to end with your eyes closed — every control announces what it is and what it does, every gesture-only action (swipe to dismiss) has an equivalent custom action a TalkBack user can invoke, and every text/background pair meets 4.5:1 (3:1 for large text and UI components). If a sighted-only user can do something a TalkBack user can't, the component is broken.

You will *prove* this by enabling TalkBack on the emulator and operating the card stack with the screen reader on — dismissing a card via its custom action, not the swipe — and by running a contrast check (the Accessibility Scanner, or your own ratio computation) over the card's colors. "It looks accessible" is not the test; turn on TalkBack and try to use it, and compute the actual contrast ratio. The difference between a gesture that only works by sight and one with a custom action is exactly this promise.

## A note on what's not here

Week 09 is the *interaction and accessibility toolkit* week. It deliberately does **not** cover:

- **Material 3 components and theming.** We build with `Box`, `Layout`, `Canvas`, and raw colors so you see the mechanics. Material 3's `SwipeToDismissBox`, `ModalBottomSheet`, dynamic color, and the Material theming system are **Week 11** — and Material's components bake in much of this week's accessibility, which is exactly why you learn the mechanics *first*, so you know what they're doing for you.
- **Navigation.** Moving between screens, predictive back, and type-safe routes are **Week 10**. This week's card stack is one screen.
- **Architecture.** Gesture and animation state lives in `remember`/`Animatable` in the composable, as Week 8 taught. Hoisting it into a `ViewModel` is **Week 12**.
- **Advanced graphics.** `RenderEffect`, shaders (AGSL), and complex `Canvas` drawing beyond the card are out of scope; we draw only what the card needs.

The point of Week 09 is narrow and deep: one measure/place contract, one ordered modifier pipeline, the gesture and animation APIs that turn touches into motion, and the semantics and contrast discipline that makes all of it usable by everyone.

## Up next

This is the last of your three authored weeks in the sequence handed to you, but in the full track it continues to **Week 10 — Navigation 3 with type-safe routes**, where the card stack becomes one destination in a multi-screen app, and **Week 11 — Material 3**, where you'll meet the framework's own `SwipeToDismissBox` and appreciate exactly how much accessibility and animation it handles for you — because you built it by hand first. Everything downstream assumes you can build a custom interactive component that's smooth *and* accessible. Earn that here.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
