# Lecture 1 — Custom layout, the modifier pipeline, and gestures

> "A `Modifier` chain is not a list of options. It is an ordered pipeline, applied outside-in, where every modifier wraps the rest — which is why reordering it changes what you see and what you can touch."

This is the lecture that turns "I stack components and hope" into "I control measurement, placement, and touch on purpose." The framing for the first half of the week is two facts, held together: **layout in Compose is a single-pass `measure → place` contract you can implement yourself, and the `Modifier` chain that decorates any composable is an ordered pipeline whose order is load-bearing.** Master both and you can build any layout and predict any modifier chain. Then we add gestures — `pointerInput` reading raw touches — because a custom interactive component needs all three.

We build bottom-up: the layout contract, then custom `Layout`, then the modifier pipeline (with a reorder gallery), then gesture detection.

---

## 1. The layout contract — measure, then place

Every layout in Compose — `Column`, `Row`, `Box`, and the custom one you're about to write — obeys one contract:

1. **Receive `Constraints` from your parent.** A `Constraints` is four numbers: `minWidth`, `maxWidth`, `minHeight`, `maxHeight`. Your parent is telling you "you must be at least this big and at most this big."
2. **Measure each child exactly once.** You call `measurable.measure(childConstraints)` on each child, passing constraints *you* decide, and get back a `Placeable` — a measured child that knows its own `width` and `height`. **A child may be measured only once per layout pass** — this is the rule that makes Compose layout single-pass and fast (no exponential re-measurement like the old `View` system's nested `onMeasure` calls).
3. **Decide your own size**, within the constraints your parent gave you, usually based on the children's measured sizes.
4. **Place each `Placeable`** at an `(x, y)` offset inside yourself, in the placement block.

That's it. `Column` measures its children, sums their heights, and places them stacked. `Box` measures its children and places them all at the same origin. A custom layout is you implementing steps 2–4 with your own algorithm.

Why is single-pass measurement such a big deal? Because the old `View` system allowed multi-pass layout — a `LinearLayout` with weights measures its children, then measures them *again* to distribute leftover space — and nested multi-pass layouts compound: two levels of `RelativeLayout` could measure a deep subtree an exponential number of times, which is a real cause of jank in legacy Android UIs. Compose's single-measure rule makes layout cost *linear* in the number of nodes: each child is measured once, full stop. The trade-off is that you occasionally need an explicit intrinsic-measurement query (§5b) or `SubcomposeLayout` (§5c) for the cases that genuinely need to look before they leap — but those are opt-in and bounded, not the silent exponential blowup the old system permitted. The performance you get from Compose layout is not magic; it's this one rule, enforced.

---

## 2. A custom `Layout` — a worked example

Here is a custom vertical layout that stacks children but adds increasing horizontal indentation per child — a "staircase." It shows every step of the contract:

```kotlin
@Composable
fun Staircase(
    modifier: Modifier = Modifier,
    stepIndent: Dp = 24.dp,
    content: @Composable () -> Unit
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        // STEP 2: measure each child once. We let each be as wide as it wants
        // (up to our max) and as tall as it wants.
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }

        val indentPx = stepIndent.roundToPx()
        // STEP 3: our width is the widest child plus the total indentation; our
        // height is the sum of child heights. Both clamped to the constraints.
        val width = (placeables.maxOfOrNull { it.width + indentPx * placeables.indexOf(it) } ?: 0)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeables.sumOf { it.height }
            .coerceIn(constraints.minHeight, constraints.maxHeight)

        // STEP 4: place each child stacked vertically, indented by its index.
        layout(width, height) {
            var y = 0
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(x = indentPx * index, y = y)   // placeRelative is RTL-aware
                y += placeable.height
            }
        }
    }
}
```

Three things to notice and be able to explain in a review:

- **`measure` is called once per child.** If you tried to measure a child twice you'd get a runtime crash; the single-measure rule is enforced. (When you genuinely need to measure, then compose based on the measurement, you reach for `SubcomposeLayout` — but it's heavier and you rarely need it. Reach for it for things like a `BoxWithConstraints` that composes different content based on available size.)
- **`placeRelative` vs `place`.** `placeRelative` mirrors automatically in right-to-left locales (so your staircase indents from the right in Arabic/Hebrew); `place` uses raw left-to-right coordinates. Default to `placeRelative` for layout that should respect reading direction.
- **You must respect the incoming `Constraints`.** Your final `layout(width, height)` size must be within `[minWidth, maxWidth] × [minHeight, maxHeight]`, or you've broken the contract with your parent. Always `coerceIn`.
- **The constraints you pass to children are your choice.** You don't have to forward the parent's constraints unchanged. The staircase loosens `minWidth`/`minHeight` to 0 so each child can be its natural size; a layout that wants children to fill a column would pass a fixed width. Deciding what constraints to hand each child is half the design of a custom layout — it's how you express "fill me," "wrap yourself," or "be exactly this big" to each one.

This is the whole of custom layout. A flow layout (wrap to the next line when the row is full), a staggered grid, a circular arrangement, a chip group — all are this contract with a different placement algorithm. The exercise has you build a flow layout from scratch.

One more decision the contract surfaces: **what to do when children don't fit.** Your parent's `maxWidth` might be smaller than the sum of your children's natural widths. You choose the policy: wrap (the flow layout), clip (place children that overflow off the visible area), scale, or scroll. The built-in `Row` simply lets children overflow if they're too wide (and you'd wrap them in `horizontalScroll` to make it scrollable); `FlowRow` wraps; a custom layout does whatever its algorithm says. There's no "right" answer the framework imposes — the measure/place contract hands you the constraints and your children's sizes, and *you* decide what to do when they conflict. That freedom is the point of writing a custom layout in the first place: when no built-in arrangement matches your design, you implement the exact policy you need, and it's never more than measure-once, decide-size, place.

---

## 3. The `Modifier` chain — an ordered, outside-in pipeline

Now the half of this lecture that bites everyone at least once. A `Modifier` chain like `Modifier.padding(8.dp).background(Color.Blue)` is **not** a set of independent properties. It is an **ordered pipeline**, and each modifier *wraps* the ones after it. The chain is applied **outside-in**: the first modifier is the outermost layer, the last is the innermost, closest to the content.

The cleanest way to internalize it: read the chain as nested boxes, outer to inner.

```kotlin
Modifier
    .padding(16.dp)        // outermost: reserves 16dp around everything inside
    .background(Blue)      // inside the padding: paints blue in the remaining area
    .padding(8.dp)         // inside the blue: 8dp of blue-free space
    .size(40.dp)           // innermost: the content area is 40dp
```

So the same two modifiers in different order produce visibly different results:

```kotlin
// A: padding THEN background -> blue is painted INSIDE the padding (padding is transparent)
Box(Modifier.padding(16.dp).background(Blue).size(40.dp))

// B: background THEN padding -> blue is painted FIRST (around everything), padding eats INTO it
Box(Modifier.background(Blue).padding(16.dp).size(40.dp))
```

In A, you get a 40dp blue box with 16dp of transparent margin around it. In B, you get a blue area with a 40dp content box and 16dp of blue showing as a border. Same modifiers, different pixels — because order decides what wraps what.

### The reorder gallery — six chains where order changes everything

**Padding vs size.** `size(100.dp).padding(10.dp)` → outer 100dp, content 80dp (padding eats inward). `padding(10.dp).size(100.dp)` → 10dp margin, then a 100dp box → 120dp total. The first constrains then pads; the second pads then sizes.

**Background vs padding** (above): which area gets painted.

**Clip vs background.** `clip(CircleShape).background(Blue)` → the background is clipped to a circle (you get a blue circle). `background(Blue).clip(CircleShape)` → the background is painted as a rectangle first, then the clip applies to *later* drawing only — you get a blue rectangle. Clip before the thing you want clipped.

**Clickable vs padding** — the touch-target one. `clickable { }.padding(16.dp)` → the clickable area *includes* the 16dp padding (the whole padded region is tappable). `padding(16.dp).clickable { }` → only the inner content is tappable; the padding is dead space. For a comfortable touch target you almost always want `clickable` *before* `padding` (or use `padding` outside and a min-size inside) so the tap area is generous. This is an accessibility issue, not just ergonomics — a too-small touch target fails the 48dp minimum (lecture 2).

**Border vs padding.** `border(2.dp, Black).padding(8.dp)` → border on the outside, 8dp inside it. `padding(8.dp).border(2.dp, Black)` → 8dp margin, then a border drawn around the inner content. Where do you want the line?

**Size vs weight (in a Row/Column).** `Modifier.weight(1f)` participates in the parent's space distribution; combining it with `size` or `padding` and reordering changes whether the weight applies to the padded or unpadded extent.

The mental model that resolves all six: **each modifier wraps the rest of the chain. The first modifier sees the full constraints from the parent and decides what to pass inward; the last modifier is closest to the content.** When a chain surprises you, draw it as nested boxes outer-to-inner and the surprise evaporates. The exercise makes you predict a gallery before running it.

There's a corollary that catches even experienced engineers: **a modifier you pass *into* a component as a parameter is applied at a specific point, usually the outermost, and you can't reorder the component's internal chain.** When you write `MyButton(modifier = Modifier.padding(8.dp))`, that padding wraps whatever `MyButton` does internally — it's the *outer* layer. If `MyButton` internally does `Modifier.background(...).clickable(...)`, your external padding sits *outside* the clickable, so (per the gallery) the padding is *not* part of the tappable area. This is why well-designed components apply the incoming `modifier` parameter at the right spot (usually first, on the root) and document it — and why, when a passed-in `padding` "isn't tappable," the answer is that it landed outside the component's own `clickable`. Designing a reusable component means deciding where the caller's `modifier` plugs into your internal chain.

### Modifiers and the three phases (Week 7 callback)

Modifiers participate in the layout and draw phases. `padding`, `size`, `offset` affect layout; `background`, `border`, `clip`, `drawBehind` affect draw; `clickable`, `pointerInput` affect input *and* add semantics (lecture 2). The lambda forms (`offset { }`, `drawBehind { }`) defer their state reads to layout/draw — exactly the phase-deferral from Week 7. A draggable card reads its offset in `offset { }` (layout) so dragging doesn't recompose. The modifier chain is where Week 7's phase discipline meets this week's interaction work.

---

### A note on the `layout` modifier — the single-child shortcut

You don't always need a full `Layout` composable. For tweaking *one* element's measurement — shifting it, padding it asymmetrically, sizing it relative to its content — there's `Modifier.layout { measurable, constraints -> ... }`, a per-element version of the same contract:

```kotlin
// A custom modifier that adds padding to the top equal to the element's baseline.
fun Modifier.firstBaselineToTop(fromTop: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)             // measure once (same rule)
    val baseline = placeable[FirstBaseline]                     // the text's baseline
    val placeableY = fromTop.roundToPx() - baseline
    val height = placeable.height + placeableY
    layout(placeable.width, height) {
        placeable.placeRelative(0, placeableY)                  // place at the computed offset
    }
}
```

It's the same measure-once-then-place contract, scoped to a single element via a modifier instead of a whole layout composable. Use `Modifier.layout { }` for one-element tweaks (aligning a baseline, a custom offset that depends on the element's own size); use the `Layout` composable when you're arranging *multiple* children by a custom algorithm. Both are the same contract at different granularities — and knowing they're the same contract is the insight.

## 4. Gestures — `pointerInput` and the detectors

A gesture is the system turning a stream of raw pointer events (down, move, up) into a meaningful interaction (tap, drag, pinch). In Compose, you tap into that stream with `Modifier.pointerInput(key) { }`, whose lambda is a `PointerInputScope` — a coroutine scope (Week 4) where you await and process pointer events.

You rarely write the raw loop; Compose ships high-level **detectors**:

```kotlin
// Taps, double-taps, long-presses, and press/release.
Modifier.pointerInput(Unit) {
    detectTapGestures(
        onTap = { offset -> /* single tap at `offset` */ },
        onDoubleTap = { /* ... */ },
        onLongPress = { /* ... */ },
        onPress = { /* suspend; can awaitRelease() */ }
    )
}

// Drags: onDragStart, per-move delta, onDragEnd/onDragCancel.
Modifier.pointerInput(Unit) {
    detectDragGestures(
        onDragStart = { /* ... */ },
        onDragEnd = { /* settle / fling */ },
        onDrag = { change, dragAmount ->
            change.consume()                  // consume so parents don't also handle it
            offsetX += dragAmount.x           // feed the delta into hoisted state (Week 8)
        }
    )
}

// Pan + zoom + rotate together (a transformable image).
Modifier.pointerInput(Unit) {
    detectTransformGestures { centroid, pan, zoom, rotation ->
        scale *= zoom
        offset += pan
        angle += rotation
    }
}
```

Four facts that separate working gesture code from flaky gesture code:

- **Feed deltas into hoisted state.** A drag detector gives you a *delta* per move (`dragAmount`), not an absolute position. You accumulate it into a state value (`offsetX += dragAmount.x`) that you then read in `Modifier.offset { }` (layout phase). The gesture is a source; the state is the truth; the offset modifier is the read. That's the Week 8 pattern with a `pointerInput` source.
- **Consume events you handle.** `change.consume()` tells the system "I handled this; don't let an ancestor (a scrolling parent) also act on it." Forgetting to consume is why a drag inside a scrollable sometimes scrolls the parent instead of dragging your element.
- **Respect touch slop.** The system requires a small minimum movement (touch slop) before a drag "starts," so a tap with a tiny finger wobble isn't misread as a drag. The detectors handle slop for you; the raw loop (`awaitTouchSlopOrCancellation`) lets you control it.
- **Key your `pointerInput` correctly.** `pointerInput(key)` restarts the gesture coroutine when the key changes — like `LaunchedEffect`'s key (Week 8). Key on the values the gesture logic depends on. Keying on `Unit` is right when the logic is constant; keying on a changing value restarts the detector.

### Higher-level gesture modifiers — `draggable`, `scrollable`, `anchoredDraggable`

`pointerInput` is the foundation, but for common patterns Compose ships higher-level *modifiers* that wrap it and handle the bookkeeping (velocity, slop, fling) for you:

- **`Modifier.draggable(state, orientation)`** — single-axis drag with a `DraggableState` you update; handles the gesture loop so you just consume deltas. Good for a one-axis slider or a single-direction swipe where you don't need the raw loop.
- **`Modifier.scrollable(state, orientation)`** — turns drag into scroll, with fling. The primitive under `verticalScroll`/`horizontalScroll`.
- **`Modifier.anchoredDraggable(state, ...)`** — the gold standard for swipe-to-dismiss and bottom-sheets: you define *anchors* (snap points), and the modifier handles dragging between them, the fling-to-nearest-anchor physics, and the settling animation. It's what Material's `SwipeToDismissBox` is built on.

The progression to internalize: **reach for the highest-level tool that fits.** A standard swipe-between-snap-points wants `anchoredDraggable` (it gives you anchors and settling for free). A custom one-axis drag wants `draggable`. Only when you need behavior none of those express — the elastic-resistance curve in the mini-project, multi-touch choreography — do you drop to `detectDragGestures`, and only when *that* doesn't fit do you drop to the raw `awaitPointerEventScope` loop. Most production code lives at the `draggable`/`anchoredDraggable` level; this week teaches the lower levels so you understand what they're doing and can drop down when you must. Don't hand-roll a swipe-to-dismiss with the raw loop when `anchoredDraggable` exists — but *do* know the raw loop, for the day a designer asks for something the modifiers can't do.

### The raw loop, when you need it

For a custom gesture the detectors don't cover (the elastic-resistance drag in the mini-project, which needs to transform the delta non-linearly), you drop to `awaitPointerEventScope`:

```kotlin
Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown()                 // wait for a finger
            do {
                val event = awaitPointerEvent()          // each move
                val drag = event.changes.first()
                val delta = drag.positionChange()
                // apply ELASTIC resistance: the further out, the less each px moves it
                offsetX += delta.x * resistance(offsetX)
                drag.consume()
            } while (event.changes.any { it.pressed })   // until the finger lifts
            // finger up -> spring back or dismiss (lecture 2's Animatable)
        }
    }
}
```

This is the lowest level — you own the down/move/up loop. You reach for it when you need behavior the detectors don't express (resistance curves, multi-touch choreography). Most days the detectors are enough; know the raw loop exists for the day they aren't.

A few raw-loop details that matter when you do use it: `awaitFirstDown()` suspends until a finger touches; `awaitPointerEvent()` returns the next event with all current pointer `changes`; `change.positionChange()` gives the delta since the last event; and `change.consume()` (or `consumeAllChanges()` on older APIs) marks it handled. To track velocity for a fling, accumulate position/time samples in a `VelocityTracker` and call `calculateVelocity()` on release. Multi-touch means iterating `event.changes` (each pointer has a stable `id`) rather than assuming one finger. None of this is hard, but it's bookkeeping the high-level detectors do for you — which is exactly why you prefer them until you can't. The raw loop is the floor; you stand on it only when the building blocks above don't reach.

---

## 5. Putting it together — a draggable, custom-laid-out component

A real interactive component composes all three: a custom layout positions it, a modifier chain styles and sizes it (in the right order), and a gesture drives a state that an offset modifier reads. Here's the skeleton the mini-project fleshes out:

```kotlin
@Composable
fun DraggableCard(onDismiss: () -> Unit) {
    var offsetX by remember { mutableFloatStateOf(0f) }     // hoisted gesture state (Week 8)
    Box(
        Modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }  // READ offset in LAYOUT phase (Week 7)
            .size(300.dp, 180.dp)                            // size before background (order!)
            .background(CardColor, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        offsetX += drag.x                    // feed delta into state
                    },
                    onDragEnd = {
                        if (abs(offsetX) > dismissThreshold) onDismiss()
                        else offsetX = 0f                    // (lecture 2: animate this back)
                    }
                )
            }
    ) {
        // card content
    }
}
```

Notice every thread of this lecture: the offset is *read in layout* (Week 7 phase discipline) so dragging doesn't recompose; the modifier *order* puts `size` before `background`; the gesture *consumes* events and feeds *deltas* into *hoisted* state (Week 8); and `onDragEnd` decides dismiss-or-return. What's missing is the *animation* of the spring-back and the *accessibility* so a TalkBack user can dismiss without the swipe — both are lecture 2.

---

## 5b. `Constraints` and intrinsic measurement — sizing that flows both ways

A custom layout's behavior is governed by `Constraints`, and two patterns are worth a closer look because they trip people up.

**Constraints flow down; sizes flow up.** A parent hands a child `Constraints` (min/max width/height); the child measures itself within them and reports a size back up. `Modifier.size(100.dp)` sets *both* min and max to 100dp (a fixed size). `Modifier.fillMaxWidth()` sets min width to the parent's max (fill it). `Modifier.wrapContentSize()` loosens the min to 0 (be as small as your content). The difference between `size` and `requiredSize` is which wins a conflict: `size` *respects* a parent's tighter constraint (if the parent says max 50dp, `size(100.dp)` yields 50dp), while `requiredSize` *overrides* it (forcing 100dp even if it overflows). When a child "won't get as big as I asked," it's usually a parent constraint capping it — and `requiredSize` is the (occasionally correct, often dangerous) override.

**Intrinsic measurements** answer "how big would you be if I asked?" *before* the real measure pass. The classic use: a `Row` of two items where you want both as tall as the taller one's natural height. `Modifier.height(IntrinsicSize.Min)` asks each child for its minimum intrinsic height and sizes the row to the max of those:

```kotlin
Row(Modifier.height(IntrinsicSize.Min)) {
    Text("Left", Modifier.fillMaxHeight())          // stretches to the row's height
    VerticalDivider()                                // a divider that needs to match height
    Text("A much longer right side that wraps to multiple lines", Modifier.fillMaxHeight())
}
```

Intrinsic measurement runs an extra query pass, so it has a cost — don't reach for it reflexively. But for "size me to my tallest/widest sibling" cases it's the clean tool, and a custom layout can implement `minIntrinsicWidth`/`maxIntrinsicHeight` to participate. Know it exists; reach for it when wrap-to-tallest is exactly the requirement.

## 5c. `Layout` vs `SubcomposeLayout` — when one pass isn't enough

The single-measure rule (§1) means you cannot measure a child, look at its size, and *then* decide what content to compose — composition happens before layout. Most of the time that's fine. But sometimes you genuinely need "measure available space, *then* compose content that fits it" — a chart that renders a different number of labels based on width, a `BoxWithConstraints` that swaps layouts at a breakpoint. That's what `SubcomposeLayout` is for: it lets you compose *during* the measure pass, in response to constraints.

```kotlin
// BoxWithConstraints is SubcomposeLayout in disguise: compose based on available size.
BoxWithConstraints {
    if (maxWidth < 400.dp) {
        CompactLayout()        // composed only when narrow
    } else {
        WideLayout()           // composed only when wide
    }
}
```

The trade-off: `SubcomposeLayout` is *heavier* — composing during layout defeats some of Compose's optimizations and can hurt performance if overused (don't wrap a `LazyColumn`'s every item in one). The guidance: reach for plain `Layout` by default; use `SubcomposeLayout` (or `BoxWithConstraints`) only when you truly must compose based on measured size, and keep it shallow. Knowing the distinction — and that `BoxWithConstraints` isn't free — is a senior detail.

## 5d. A worked trace — one drag, through every layer

Walk a single drag gesture through the whole stack, so the threads connect. The user puts a finger on the card and drags 80px right.

1. **`pointerInput`'s detector fires.** `detectDragGestures`'s `onDrag` is called with a `dragAmount` of roughly `(8, 0)` per move event (deltas, not absolute). The first few pixels are eaten by *touch slop* before the drag "starts," so a tap-with-wobble isn't misread.
2. **The delta is consumed and accumulated.** `change.consume()` tells the system "handled — don't let the parent scroll." `offsetX += dragAmount.x` accumulates the delta into hoisted state (Week 8). After ten move events, `offsetX ≈ 80`.
3. **The offset is read in the layout phase.** `Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }` reads `offsetX` *inside the lambda*, which runs in the layout phase (Week 7). So advancing `offsetX` invalidates only layout + draw — the card's composable does *not* recompose as it drags. The card glides; the composition stays still.
4. **The modifier chain places and paints.** The chain (`offset → size → background → pointerInput`) runs outside-in: the offset shifts the whole element, `size` fixes its bounds, `background` paints inside, and `pointerInput` keeps reading touches. Order matters — `offset` first means the *whole* styled card moves together.
5. **On release, the gesture ends.** `onDragEnd` checks `abs(offsetX)` against the threshold and either dismisses or (lecture 2) springs back — an animation that, again, is read in the layout phase so it's smooth.

Every layer of this lecture is in that one drag: the gesture *source*, the *consume* that prevents the parent stealing it, the *delta* accumulated into *hoisted state*, the *layout-phase read* that keeps it from recomposing, and the *ordered modifier chain* that places and paints. When a real drag misbehaves, walk these five steps and the broken one is obvious — a stolen gesture (missing consume), a janky drag (offset read in composition), a wrong touch area (modifier order). The trace is the debugger.

## 6. Recap — the contract, the pipeline, the source

You will build interactive UI all week. The reflexes that turn you from someone who *uses* layouts and modifiers into someone who can *build* them:

- A surprising layout → which step of measure/place is doing it? Did I respect the incoming constraints, and did I measure each child exactly once?
- A surprising visual or touch target → draw the modifier chain as nested boxes, outer-to-inner. Whatever wraps whatever explains it.
- A drag that scrolls the parent → I forgot `change.consume()`.
- A janky drag → I'm reading the offset in composition; defer it to `Modifier.offset { }` (layout).
- A gesture that uses stale state → my `pointerInput` key is wrong; key it on the values the logic depends on.
- A passed-in `modifier`'s padding isn't tappable → it landed outside the component's internal `clickable`; that's where the caller's modifier plugged into the chain.
- A custom layout crashed on "measured twice" → I measured a child in the placement block instead of reusing the `Placeable` from the measure step.
- A layout won't grow to wrap its tallest child → I need an intrinsic measurement (`IntrinsicSize.Min`) or a custom intrinsic implementation, not a fixed size.

One more habit worth naming before we move on: **build interactive components stateless and hoisted, exactly as Week 8 taught.** A `DraggableCard` should take its offset state (or an `onDismiss` event) from its caller, not own business logic internally. The gesture *source* (`pointerInput`) and the *visual read* (`offset { }`) live in the component; the *meaning* of a dismiss (remove from a list, mark read, undo) lives in the caller via a lambda. This keeps the component reusable (it works in a stack, a list, a detail view) and testable (you can drive it with any state and assert the events). The same UDF discipline from Week 8 — state down, events up — applies to gesture components, and it's what lets the mini-project's card drop into a stack without rewriting it.

A meta-point worth holding: these three mechanisms — measure/place, the modifier pipeline, pointer input — are *uniform*. There isn't a special system for `Column` and a different one for your custom layout; `Column` *is* a `Layout` with a stacking `MeasurePolicy`. There isn't a magic touch system for `Button` and a manual one for your card; `Button`'s `onClick` is a `pointerInput` detector with semantics attached. The built-in components are not a privileged layer above you — they're the same primitives you just learned, composed and named. That uniformity is what makes Compose learnable: master the primitive once, and every component (built-in or yours) is an instance of it. When you read the source of `LazyColumn` or `Slider`, you'll recognize the measure/place contract, the modifier chain, and the gesture loop doing exactly what you did by hand. There's no second, hidden API — just these, all the way down.

Layout is the single-pass measure/place contract; the modifier chain is an ordered outside-in pipeline; gestures are `pointerInput` turning raw touches into deltas you feed into hoisted state. In lecture 2 we add the two things that make this production-grade: *animation* (so the spring-back and dismiss feel native, not abrupt) and *accessibility* (so the gesture is operable without sight). Bring the draggable-card skeleton with you; we're about to make it spring, and make it speak.
