# Lecture 2 — Animation, semantics, and accessibility

Lecture 1 gave you layout, the modifier pipeline, and gestures — the mechanics of an interactive component. This lecture is the half that makes it *ship-quality*: **animation** so motion feels native instead of abrupt, and **accessibility** so the component is operable by everyone, including users who never see it. These are not polish you add at the end. A spring-back that snaps instantly feels broken; a swipe-to-dismiss with no TalkBack action is *unusable* for a blind user. The skill this week earns is building motion that feels physical and accessibility that's designed in from the first line — and proving the accessibility with TalkBack actually on.

We take animation first (you need it to finish the card's spring-back), then semantics and TalkBack, then WCAG contrast and large-text compliance.

---

## 1. The animation API family — pick the right tool

Compose has several animation APIs, and reaching for the wrong one is the most common animation smell. The decision is about *what* you're animating:

| You're animating… | Use | Why |
|--------------------|-----|-----|
| One value to a target, fire-and-forget | `animate*AsState` | Simplest; declarative; recomposes the read |
| A gesture-driven / interruptible value | `Animatable` | Imperative `animateTo`/`snapTo`; cancellable; velocity-aware |
| Several properties off one state change | `updateTransition` | Coordinates many child animations from one transition |
| A composable entering/leaving | `AnimatedVisibility` | Enter/exit transitions (fade, slide, expand) |
| Swapping between content for a state | `AnimatedContent` | Animates out the old, in the new, with a `transitionSpec` |
| A simple crossfade between two | `Crossfade` | The narrow case of `AnimatedContent` |

### `animate*AsState` — the declarative single value

```kotlin
// When `expanded` flips, the height animates to the new target automatically.
val height by animateDpAsState(
    targetValue = if (expanded) 200.dp else 60.dp,
    animationSpec = spring(),
    label = "height"
)
Box(Modifier.height(height))
```

You give it a target; it animates the current value toward it whenever the target changes. There are typed variants — `animateFloatAsState`, `animateDpAsState`, `animateColorAsState`, `animateOffsetAsState`, etc. It's the right tool for "a state changed, smoothly move to the new value." It reads the animating value in composition by default, so for 60fps animations prefer reading the result in a layout/draw lambda (Week 7) where it matters.

### `Animatable` — the imperative, interruptible one

For gesture-driven animation (the card's spring-back), `animate*AsState` is wrong — you need imperative control: snap to the drag position while the finger is down, then `animateTo` zero (spring back) or off-screen (dismiss) on release, and *interrupt* that animation if the user grabs the card again mid-spring. That's `Animatable`:

```kotlin
val offsetX = remember { Animatable(0f) }
val scope = rememberCoroutineScope()

// while dragging: snapTo follows the finger exactly (no animation lag)
scope.launch { offsetX.snapTo(offsetX.value + dragDelta) }

// on release: spring back to 0, OR animate off-screen to dismiss
scope.launch {
    if (abs(offsetX.value) > threshold) {
        offsetX.animateTo(
            targetValue = sign(offsetX.value) * screenWidth,
            animationSpec = tween(250)
        )
        onDismiss()
    } else {
        offsetX.animateTo(0f, animationSpec = spring(dampingRatio = 0.6f))   // springy return
    }
}
```

`Animatable` is a suspend-based animation primitive: `snapTo` jumps instantly (for following a finger), `animateTo` runs an animation you can `await` or cancel. If a new gesture starts while `animateTo` is running, launching a new `snapTo`/`animateTo` *cancels* the in-flight one (structured concurrency, Week 4) — so the card grabs naturally mid-flight. This interruptibility is exactly why gestures use `Animatable` and not `animate*AsState`.

### `AnimatedVisibility` and `AnimatedContent`

```kotlin
AnimatedVisibility(visible = showBanner, enter = slideInVertically() + fadeIn(), exit = fadeOut()) {
    Banner()
}

AnimatedContent(targetState = page, transitionSpec = {
    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
}) { current ->
    PageContent(current)
}
```

`AnimatedVisibility` animates a composable in and out of existence; `AnimatedContent` animates the *transition between two states'* content. Both take enter/exit transitions you compose with `+`.

---

## 2. Specs and timing — why springs feel native

The *spec* decides how a value gets from A to B over time. Two you'll use constantly:

- **`tween(durationMillis, easing)`** — a fixed-duration interpolation. `tween(300, easing = FastOutSlowInEasing)`. Predictable, good for non-interactive transitions (a banner sliding in). The footgun is *linear* easing (`LinearEasing`), which feels robotic — real motion accelerates and decelerates. Default to `FastOutSlowInEasing` (Material's standard curve) for tweens.
- **`spring(dampingRatio, stiffness)`** — physics-based, no fixed duration; it settles like a real spring. `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)`. Springs feel native because real objects have momentum and settle — a spring that slightly overshoots and settles feels *alive* where a tween feels *scheduled*. For anything the user interacts with directly (drag release, toggles, expand/collapse), prefer a spring.

The rule that makes your animations stop feeling like a tutorial: **use springs for interaction, tweens with non-linear easing for choreographed transitions, and never `LinearEasing` for anything a human watches.** A dismiss that springs back with a touch of bounce reads as physical; the same dismiss with a 300ms linear tween reads as a slideshow. Timing is the difference between "native app" and "web page in a frame."

`keyframes { }` lets you specify intermediate values at specific times (for multi-stage motion), and `infiniteRepeatable` loops (the Week 7 ring). But `spring` and `tween` cover ninety percent of real work.

---

## 2b. `updateTransition` — coordinating several animations off one state

When *one* state change should drive *several* coordinated animations — a card that, on selection, grows, changes color, and elevates all together — running three separate `animate*AsState` calls works but they're independent and can drift. `updateTransition` ties them to a single `Transition` driven by one state, so they start together, share timing, and stay in sync:

```kotlin
val transition = updateTransition(targetState = selected, label = "card")
val elevation by transition.animateDp(label = "elevation") { isSelected ->
    if (isSelected) 12.dp else 2.dp
}
val color by transition.animateColor(label = "color") { isSelected ->
    if (isSelected) SelectedColor else UnselectedColor
}
val scale by transition.animateFloat(label = "scale") { isSelected ->
    if (isSelected) 1.05f else 1f
}
```

All three derive from the same `transition`, so they animate as one coordinated change. You can give each a different `transitionSpec` (the color snappier, the scale springier) while keeping them anchored to the same state flip. `updateTransition` also exposes the *current* and *target* states and whether it's `isRunning`, which is handy for sequencing. The rule: **one value animating off a state → `animate*AsState`; several coordinated values off one state → `updateTransition`.** It's the difference between three animations that happen to fire together and one transition that is genuinely coordinated.

## 3. The semantics tree — what accessibility services read

Compose maintains a **semantics tree** parallel to the UI tree. It's a tree of nodes describing *meaning*, not appearance: "this is a button labeled 'Dismiss'," "this is a heading," "this toggle is checked." Accessibility services — TalkBack (screen reader), Switch Access, the Accessibility Scanner — read the **semantics tree**, not your composables. So accessibility is the discipline of making sure the semantics tree says the right thing.

Most built-in components populate semantics for you: `Button` says it's a button, `Text` exposes its text, `Switch` exposes its checked state. The problem is **custom components** — a `Box` you made tappable with `pointerInput`, a custom-drawn `Canvas` control. To Compose, a `Box` is just a box; it has no idea it's a "dismiss button." You must tell it, via `Modifier.semantics { }`:

```kotlin
Box(
    Modifier
        .pointerInput(Unit) { detectTapGestures { onClick() } }
        .semantics {
            contentDescription = "Delete photo"   // what TalkBack announces
            role = Role.Button                      // it's a button, not just text
            onClick(label = "Delete") { onClick(); true }  // expose the action to a11y services
        }
) { Icon(Icons.Default.Delete, contentDescription = null) }   // null: the parent describes it
```

The key semantics properties:

- **`contentDescription`** — the spoken label for a non-text element (an icon, an image, a custom control). Text gets its label for free; icons and images need one (or explicitly `null` if decorative and described by a parent).
- **`stateDescription`** — for stateful controls: "checked"/"unchecked", "expanded"/"collapsed". TalkBack reads it after the label.
- **`role`** — `Button`, `Checkbox`, `Switch`, `Image`, etc. Tells the service how to describe and interact.
- **`heading()`** — marks a heading so TalkBack users can jump between sections.
- **`liveRegion`** — announces dynamic changes (a "Saved" toast, a result count updating) without the user navigating to it.

### Merging and clearing semantics

Two tools control how the tree is shaped:

- **`Modifier.semantics(mergeDescendants = true) { }`** — collapses a node and its children into one focusable element. A card with a title and subtitle should be *one* TalkBack stop that reads "Title, Subtitle," not three separate stops. Merge descendants so the whole card is one semantic unit.
- **`Modifier.clearAndSetSemantics { }`** — replaces a subtree's semantics entirely. Use it when the auto-generated semantics are wrong or noisy and you want to state exactly what the element means, discarding the children's contributions.

The discipline: **a custom interactive element needs `contentDescription` + `role` + the action exposed; a composite element (a card) should `mergeDescendants` so it's one stop; and decorative elements should have `contentDescription = null` so they're skipped.** Get those three habits and most of accessibility is done.

---

## 4. Custom actions — making a gesture operable without the gesture

Here's the hard part, and the one this week's promise is built on. Your card dismisses with a *swipe*. A TalkBack user navigates by tapping and swiping in TalkBack's own gesture language — they cannot perform your custom horizontal-swipe-to-dismiss, because TalkBack intercepts gestures for its own navigation. If dismiss only works by swipe, **a blind user cannot dismiss the card at all.** The component is broken for them.

The fix is a **custom accessibility action** — an action you register in semantics that TalkBack exposes in its actions menu (the user swipes up-then-right, or uses the actions menu, to invoke it):

```kotlin
Box(
    Modifier
        .pointerInput(Unit) { /* the visual swipe-to-dismiss for sighted users */ }
        .semantics {
            // Expose dismiss as a NAMED custom action TalkBack can invoke without the swipe.
            customActions = listOf(
                CustomAccessibilityAction(label = "Dismiss card") {
                    onDismiss(); true
                }
            )
            contentDescription = "Notification: $title. Double-tap and hold for actions."
        }
) { /* card content */ }
```

Now a TalkBack user lands on the card, hears it described, opens the actions menu, and chooses "Dismiss card" — performing the same operation the swipe does, without the swipe. **Every gesture-only interaction needs an equivalent custom action.** This is the single most important accessibility lesson of the week: a beautiful gesture that's the *only* way to do something excludes everyone who can't perform it. The custom action is how you include them.

The principle generalizes: swipe-to-delete needs a "Delete" custom action; drag-to-reorder needs "Move up"/"Move down" actions; pinch-to-zoom needs zoom controls or actions. If sighted users get a gesture, screen-reader users get an action that does the same thing.

---

## 5. WCAG contrast — the part you can compute

Accessibility isn't only screen readers; it's also *legibility*. Low-contrast text — light gray on white, "elegant" but unreadable — fails users with low vision, and everyone in sunlight. The **Web Content Accessibility Guidelines (WCAG)** give a concrete, computable bar.

**The contrast ratio** between two colors is `(L1 + 0.05) / (L2 + 0.05)`, where `L1` is the relative luminance of the lighter color and `L2` of the darker, and relative luminance is a weighted, gamma-corrected sum of the RGB channels. It ranges from 1:1 (identical colors) to 21:1 (black on white).

**The AA thresholds** (the standard most teams target):

- **4.5:1** for normal text.
- **3:1** for large text (≥ 18pt, or ≥ 14pt bold) and for UI components / graphical objects (a button's border, an icon).

You can compute it:

```kotlin
fun relativeLuminance(color: Color): Double {
    fun channel(c: Float): Double {
        val s = c.toDouble()
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val (light, dark) = if (la > lb) la to lb else lb to la
    return (light + 0.05) / (dark + 0.05)
}

fun passesAA(foreground: Color, background: Color, largeText: Boolean = false): Boolean {
    val ratio = contrastRatio(foreground, background)
    return ratio >= if (largeText) 3.0 else 4.5
}
```

The discipline: **audit every text/background pair and every UI-component/background pair against AA, compute the ratio, and fix failures by darkening the foreground or lightening the background until it passes.** The exercise has you write this check and fail a bad pair on purpose; the mini-project has you audit the card's real colors and document the ratios.

### Large text and font scale

The other half of legibility is respecting the **system font scale.** A user can set their font scale to 200% (Settings ▸ Display ▸ Font size). If you size text in `dp`, it *ignores* that setting and stays tiny — excluding low-vision users. Size text in **`sp`** (scale-independent pixels), which honors the font scale, and *test your layout at 200%* so it doesn't clip or overlap. A screen that breaks at 200% font scale fails a large, real population. The rule: **`sp` for text, `dp` for everything else, and test at the largest font scale.**

---

## 5b. Touch targets, focus order, and reduce-motion

Three more accessibility facts round out the picture beyond labels and contrast.

**Minimum touch target: 48dp.** Every interactive element should have a touch target of at least 48×48dp, regardless of how small the *visual* is. A 24dp icon button is fine *visually*, but its tap area must be padded to 48dp — otherwise users with motor impairments (and everyone with large fingers) miss it. `IconButton` and the Material components handle this for you; a bare `Modifier.clickable` on a small element does not. Wrap small controls in `Modifier.size(48.dp)` or `Modifier.minimumInteractiveComponentSize()` so the target meets the bar even when the glyph is small. The Accessibility Scanner flags sub-48dp targets automatically.

**Focus order.** TalkBack traverses elements in a default order (roughly top-to-bottom, start-to-end). Usually that's right, but for a visually-reordered layout (a label that appears after its field but should be read before it), you can override with `Modifier.semantics { traversalIndex = ... }` or group with `isTraversalGroup`. Most screens need no intervention; know the tool exists for the screens that do, and *test the order with TalkBack* rather than assuming it.

**Reduce motion.** Some users experience nausea or discomfort from animation (vestibular disorders). Android exposes a "remove animations" setting, and a considerate app honors it by falling back to instant transitions for large motion. You can read the animation scale and gate big animations:

```kotlin
// If the user has reduced animations, skip the fling/spring and dismiss instantly.
val reduceMotion = Settings.Global.getFloat(
    context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
) == 0f

if (reduceMotion) {
    offsetX.snapTo(targetOffscreen); onDismiss()    // instant, no animation
} else {
    offsetX.animateTo(targetOffscreen, tween(220)); onDismiss()
}
```

You don't have to remove *all* motion — a subtle fade is usually fine — but large, sweeping movement (a card flying across the screen) should degrade to something gentle when the user asked for less. Accessibility is not only "can a blind user operate it"; it's also "does it not make a motion-sensitive user ill." Both are part of the contract.

## 6. Bringing it home — the accessible, animated card

The mini-project's card composes everything from both lectures. Here's the accessibility-and-animation layer over lecture 1's gesture skeleton:

```kotlin
@Composable
fun DismissibleCard(notification: Notification, onDismiss: () -> Unit) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }   // read in layout (Week 7)
            .size(320.dp, 96.dp)
            .background(CardBg, RoundedCornerShape(12.dp))          // CardBg passes AA vs CardText
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + drag.x) }  // follow finger
                    },
                    onDragEnd = {
                        scope.launch {
                            if (abs(offsetX.value) > 200f) {
                                offsetX.animateTo(sign(offsetX.value) * 1200f, tween(250))
                                onDismiss()
                            } else {
                                offsetX.animateTo(0f, spring(dampingRatio = 0.6f))  // springy return
                            }
                        }
                    }
                )
            }
            .semantics(mergeDescendants = true) {                   // ONE TalkBack stop
                contentDescription = "Notification: ${notification.title}"
                customActions = listOf(                             // dismiss without the swipe
                    CustomAccessibilityAction("Dismiss") { onDismiss(); true }
                )
            }
    ) {
        Text(notification.title, color = CardText, fontSize = 16.sp)   // sp honors font scale
    }
}
```

Every thread of the week is here: layout-phase offset read (Week 7), gesture deltas into an `Animatable` (lecture 1 + this lecture's interruptible animation), spring-back vs dismiss with native-feeling specs, merged semantics so the card is one stop, a custom action so a TalkBack user can dismiss, AA-passing colors, and `sp` text. That's a production component.

---

## 6b. Testing accessibility — the Compose UI test angle

You don't only verify accessibility by hand with TalkBack (though you must do that too). Compose UI tests can *assert* semantics, which means accessibility becomes a regression-guarded property rather than a one-time check. Using `createComposeRule` (Phase III covers it fully), you can assert that an element has the right content description, role, and actions:

```kotlin
@Test fun dismissCard_hasDismissAction() {
    composeTestRule.setContent { DismissibleCard(notification, onDismiss = {}) }

    composeTestRule
        .onNodeWithContentDescription("Notification: Build passed")
        .assert(hasContentDescription("Notification: Build passed"))
        .assert(hasAnyDescendant(hasText("Build passed")).not())   // merged into one node

    // the custom action exists and is invokable
    composeTestRule
        .onNode(hasContentDescription("Notification: Build passed"))
        .assertHasClickAction()           // or assert a custom action by label
}
```

The point isn't the exact API (that's Week 17's testing week); it's the *principle*: because semantics are queryable, a UI test can fail the build if someone removes a `contentDescription` or a custom action. Accessibility you assert in CI doesn't silently regress. The same semantics tree that TalkBack reads is the tree your tests query — which is one more reason the semantics tree is the heart of the accessibility story. Design semantics in, verify by hand with TalkBack, and lock it with a test, and your component stays accessible as it evolves.

There's also the **Accessibility Scanner** (a Play Store app) and the **Espresso Accessibility checks**, which run a battery of automated audits — small touch targets, missing labels, low contrast — over a live screen and report violations. Run the Scanner over your screen as a fast first pass; it catches the mechanical failures (the 24dp target, the unlabeled icon) so your manual TalkBack pass can focus on the harder question of whether the screen is actually *operable* end to end.

## 7. Recap

Lecture 1 gave you the interactive mechanics; this lecture made them ship-quality. Three habits carry it:

1. **Pick the right animation API and a native-feeling spec.** `Animatable` for gestures (interruptible), `animate*AsState` for fire-and-forget, `AnimatedVisibility`/`AnimatedContent` for enter/swap. Springs for interaction, tweens-with-easing for choreography, never `LinearEasing`.
2. **Design accessibility in, not on.** Custom components need `contentDescription` + `role` + the action exposed; composites `mergeDescendants` into one stop; and *every gesture-only interaction needs an equivalent custom action* so it's operable without sight. Verify with TalkBack actually on.
3. **Make it legible.** Audit contrast against WCAG AA (4.5:1 normal, 3:1 large/UI) with the computable ratio, and size text in `sp` so it honors the font scale — testing at 200%.

One closing principle to carry past this week: **accessibility is a design constraint, not a QA pass.** The cheapest time to make a component accessible is while you're building it — a `contentDescription` here, a custom action there, AA colors chosen up front. The most expensive time is after it ships, when you're retrofitting semantics onto a gesture system that assumed sight. Teams that treat accessibility as "we'll add it before launch" never quite do; teams that build it in by reflex ship it for free. This week's promise — operable without sight, AA contrast — is not a checklist you run at the end. It's a way of building every interactive component from the first line. Make it a habit now, while the components are small, and it stays a habit when they're not.

You now have the whole interaction-and-accessibility toolkit: custom layout, the modifier pipeline, gestures, the animation family, semantics, and contrast. The exercises drill a drag-with-spring-back and a semantics-plus-contrast check; the challenge takes a beautiful-but-inaccessible component and makes it operable by everyone; the mini-project builds the swipe-to-dismiss card stack with elastic resistance, spring-back, TalkBack custom actions, and a written WCAG audit. And remember where this sits in the arc: Weeks 7, 8, and 9 together are the Compose *runtime* and *toolkit* — the recomposition model, the state-and-effect machinery, and the layout-gesture-animation-accessibility surface. Everything downstream (navigation, Material 3, architecture, the production stack) sits on top of these three weeks. The component you can build now — a custom-laid-out, gesture-driven, springy, accessible interactive surface backed by snapshot state that survives the lifecycle — is the unit every Android screen is made of. You don't yet have a `ViewModel`, a navigation graph, or a theme; you have the thing those organize. That's the right order: understand the unit, then learn to compose units into screens, screens into apps, and apps into shipping products.

Go build something that's smooth *and* usable by everyone — because the second half is not optional.
