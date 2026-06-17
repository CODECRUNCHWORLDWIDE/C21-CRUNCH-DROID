# Mini-Project — Swipe-to-dismiss card stack: elastic, springy, accessible

This week you build a **swipe-to-dismiss card stack** with every tool from both lectures at once: a custom gesture with **elastic resistance** (the further you pull, the more it resists), a **spring-back** animation when you don't pull far enough and a fling-off when you do, **full TalkBack support** (each card announces itself and offers a "Dismiss" custom action so a screen-reader user can dismiss without the swipe they can't perform), and a written **WCAG contrast audit** of the card's colors. It's a notification-stack UI — the kind every messaging and email app ships — built by hand so you understand every layer Material's `SwipeToDismissBox` (Week 11) hides.

The point of the project is to prove you can build a real interactive component that is *both* delightful and *operable by everyone*: a gesture with physical-feeling resistance, motion that springs like a real object, and accessibility that makes the gesture work without sight. That combination — elastic drag, native-feeling spring, TalkBack custom action, audited contrast — is the senior, ship-it-to-real-users instinct this week installs.

This is a *fresh* screen, not a continuation. You start from an Empty Activity Compose project. Navigation, Material 3, and architecture come later; this week is interaction and accessibility, alone, done right.

---

## Where you're starting from

An Empty Activity Compose project (or your Week 7/8 `Scratch` app). You need:

- The Compose BOM and the Compose Compiler plugin (template-wired).
- `androidx.compose.animation`, `androidx.compose.foundation` (gestures), `androidx.compose.ui` (layout, semantics) — all from the BOM.
- For the optional test: the contrast functions from exercise 3 + a JUnit test target.

## What you're building toward

By the end you have:

- A `CardStack` showing 3–5 notification cards, slightly offset/scaled behind the top one (a custom layout or offset modifiers).
- The top card draggable horizontally with **elastic resistance** (a non-linear transform of the drag delta).
- **Spring-back** if released under the dismiss threshold; **fling-off** and removal if released past it.
- **TalkBack**: each card is one stop, announces its notification, and offers a "Dismiss" custom action operable without the swipe.
- A **WCAG contrast audit**: the card text/background and any icon/background pairs computed and shown to pass AA, written into the README.
- A short clip showing the drag/resistance/spring, plus a TalkBack clip showing dismissal via the custom action.

---

## Milestone 1 — The card model and the stack layout (≈ 1 h)

Model the notifications immutably (Week 7 stability) and lay out the stack.

```kotlin
@Immutable
data class Notification(val id: String, val title: String, val body: String)

@Composable
fun CardStack(notifications: List<Notification>, onDismiss: (String) -> Unit) {
    // Show the top few cards; back cards are offset down and scaled slightly so
    // the stack reads as a stack. Only the TOP card is draggable.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        notifications.take(3).reversed().forEachIndexed { indexFromBack, notification ->
            val depth = notifications.take(3).size - 1 - indexFromBack   // 0 = top
            val isTop = depth == 0
            DismissibleCard(
                notification = notification,
                isInteractive = isTop,
                modifier = Modifier
                    .offset(y = (depth * 12).dp)                 // each back card lower
                    .scale(1f - depth * 0.04f),                  // and slightly smaller
                onDismiss = { onDismiss(notification.id) }
            )
        }
    }
}
```

Decisions you must defend in review:

- **Why only the top card is interactive?** A stack where every card responds to drag is confusing and an accessibility nightmare (which card does a swipe target?). The top card is the actionable one; back cards are visual context.
- **Why an immutable `Notification`?** Stability (Week 7) — the list and cards are skippable, so dismissing one doesn't needlessly recompose the rest.

## Milestone 2 — The elastic drag (≈ 1.5 h)

The drag isn't linear: as you pull the card further from center, each pixel of finger movement moves it *less* (rubber-band resistance). Use the raw pointer loop or a drag detector with a resistance transform.

```kotlin
/** Map a raw horizontal drag offset to a resisted visual offset. Near center it's
 *  1:1; far out it asymptotes, so the card feels like it's on a rubber band. */
fun elasticOffset(rawOffset: Float, maxStretch: Float = 600f): Float {
    val sign = sign(rawOffset)
    val magnitude = abs(rawOffset)
    // a smooth saturating curve: grows fast near 0, flattens toward maxStretch
    val resisted = maxStretch * (1f - exp(-magnitude / maxStretch))
    return sign * resisted
}
```

Drive an `Animatable` with the *raw* accumulated drag, but *display* the elastic transform of it (read in the layout phase, Week 7):

```kotlin
val rawDrag = remember { Animatable(0f) }     // accumulates the actual finger movement
// in Modifier.offset { } (layout phase):
.offset { IntOffset(elasticOffset(rawDrag.value).roundToInt(), 0) }
```

So the card follows the finger with diminishing returns — pull it twice as far and it moves only a little further. That resistance is what makes a swipe feel *physical* rather than like sliding on ice. (Decide dismissal off the *raw* drag distance, not the resisted display offset, so the threshold is predictable.)

## Milestone 3 — Spring-back and fling-off (≈ 1 h)

On release, decide based on the raw drag distance, and animate with native-feeling specs (lecture 2, §2):

```kotlin
onDragEnd = {
    scope.launch {
        if (abs(rawDrag.value) > dismissThresholdPx) {
            // FLING off-screen in the drag direction, then remove.
            rawDrag.animateTo(
                targetValue = sign(rawDrag.value) * 2000f,
                animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
            )
            onDismiss()
        } else {
            // SPRING back to center with a little life.
            rawDrag.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }
}
```

Because it's an `Animatable`, grabbing the card mid-spring interrupts the animation and it follows the finger again (lecture 2, §1). Tune the spring's `dampingRatio` until the return feels alive but not silly — `MediumBouncy` overshoots a touch; `NoBouncy` settles cleanly. Never a `LinearEasing` tween for the spring-back; it reads as robotic.

## Milestone 4 — Accessibility: announce and act (≈ 1.5 h)

Make every card operable by TalkBack. This is the milestone the week's promise is built on.

```kotlin
Modifier
    .semantics(mergeDescendants = true) {                       // ONE TalkBack stop per card
        contentDescription = "Notification: ${notification.title}. ${notification.body}"
        if (isInteractive) {
            // The swipe-to-dismiss, made operable WITHOUT the swipe.
            customActions = listOf(
                CustomAccessibilityAction(label = "Dismiss") { onDismiss(); true }
            )
        }
    }
```

Then **verify with TalkBack actually on**:

1. Enable TalkBack (Settings ▸ Accessibility ▸ TalkBack).
2. Navigate to the top card — it announces the notification as one stop.
3. Open the actions menu (swipe up-then-right, or the menu gesture) — "Dismiss" is listed.
4. Invoke "Dismiss" — the card is removed, *without you performing the swipe*.

If "Dismiss" isn't in the actions menu, the `customActions` aren't attached to the focused node — check the `semantics` is on the same element TalkBack focuses (the merged card root). Also confirm back cards are *not* announced as interactive (only the top card has the action).

## Milestone 5 — The WCAG contrast audit (≈ 0.5 h)

Audit the card's colors against AA (lecture 2, §5). Using exercise 3's `contrastRatio`/`passesAA` (or WebAIM), compute the ratio for every text/background and icon/background pair, and write the results into your README as a table:

| Foreground | Background | Ratio | AA (4.5:1 / 3:1) |
|---|---|---|---|
| Card title `#…` | Card bg `#…` | x.x:1 | PASS/FAIL |
| Card body `#…` | Card bg `#…` | x.x:1 | PASS/FAIL |
| Dismiss icon `#…` | Card bg `#…` | x.x:1 | PASS (3:1 UI) |

If any pair fails, **fix it** — darken the foreground or lighten the background until it passes — and record the corrected colors. Text in `sp` (not `dp`) so it honors the font scale; test the stack at 200% font scale and confirm it doesn't clip.

## Milestone 6 — Prove it (≈ 0.5 h)

Record two short clips (or screenshot sequences) in your README:

1. **The motion**: drag a card partway (see the elastic resistance), release (see it spring back); drag it past threshold (see it fling off and the next card surface).
2. **The accessibility**: with TalkBack on, navigate to a card, open the actions menu, and dismiss via the "Dismiss" action — no swipe.

Plus the contrast table from Milestone 5. "It's smooth *and* a TalkBack user can dismiss it" is the deliverable.

---

## Acceptance criteria

- [ ] A `CardStack` shows multiple cards with the top one interactive; back cards are visual context only.
- [ ] The drag has **elastic resistance** (non-linear transform), with the offset read in the **layout phase** (`Modifier.offset { }`).
- [ ] Release under threshold **springs back** (a `spring()` spec, not linear); release over threshold **flings off** and removes the card.
- [ ] Grabbing a card mid-animation **interrupts** it (proving `Animatable`, not `animate*AsState`).
- [ ] Each card is **one merged TalkBack stop**, announces its notification, and the top card offers a **"Dismiss" custom action** operable without the swipe.
- [ ] You **verified with TalkBack on** that a card can be dismissed via the action.
- [ ] A **WCAG contrast table** in the README shows every relevant pair passing AA (with fixes if any failed); text is sized in `sp`.
- [ ] Two clips/screenshot sequences (motion + accessibility) in the README.
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **Velocity-aware dismiss.** Track the drag velocity and dismiss on a *fast flick* even if the distance is under threshold (a quick flick should dismiss). Use the velocity from the gesture and an `Animatable` `animateTo` with the initial velocity.
- **Custom stack layout.** Replace the offset/scale modifiers with a real custom `Layout` (exercise 1's contract) that measures and places the stacked cards, so the stacking is a layout decision, not a per-card modifier.
- **Undo with `liveRegion`.** After a dismiss, show an "Undo" snackbar and announce "Notification dismissed" via a `liveRegion` so TalkBack confirms it without navigation.
- **Reduce-motion respect.** Check the system "remove animations" setting and fall back to an instant dismiss (no spring/fling) when the user has reduced motion enabled — accessibility includes vestibular sensitivity.

## Common pitfalls (and how to spot them)

These are the failures a reviewer sees most often on this project; catch them before you submit.

- **The card janks while dragging.** You're reading the offset in the composable body instead of only inside `Modifier.offset { }`. The offset's *only* read site should be the layout-phase lambda; anywhere else recomposes the card every frame (Week 7). Watch the recomposition counter from Week 7 — if it climbs while dragging, you have a leaked read.
- **The drag scrolls the parent instead of moving the card.** You forgot `change.consume()` in the drag callback. Consume the event so an ancestor (a scrollable column) doesn't also act on it.
- **The spring-back feels robotic.** You used a `tween` with `LinearEasing` instead of a `spring`. Interaction wants a spring; linear easing reads as a slideshow. Tune `dampingRatio` until the return feels alive.
- **Grabbing the card mid-animation does nothing (or stutters).** You used `animate*AsState` instead of `Animatable`. Only `Animatable` lets a new `snapTo` interrupt an in-flight `animateTo`. If the card can't be grabbed mid-spring, you're using the wrong animation primitive.
- **TalkBack can't dismiss the card.** You have the swipe but no `CustomAccessibilityAction`. The swipe is intercepted by TalkBack; the custom action is the *only* way a screen-reader user can dismiss. This is a hard fail — the week's promise is "operable without sight." Verify with TalkBack actually on.
- **TalkBack reads the card as three separate stops.** You didn't set `mergeDescendants = true`. Merge so the card is one announcement, not one stop per child.
- **Back cards respond to the gesture or announce as interactive.** Only the top card should be draggable and have the dismiss action; gate both on `isInteractive`/`isTop`. A stack where every card reacts is confusing and an accessibility problem.
- **Contrast fails but you shipped it anyway.** Run the audit (Milestone 5). If any pair is under AA, fix the color — don't note the failure and move on. Text in `dp` instead of `sp` is the same class of bug: it ignores the user's font scale.
- **Dismissal threshold uses the resisted display offset.** Decide dismiss-or-return off the *raw* drag distance, not the elastic-transformed display value, or the threshold becomes unpredictable (the resistance curve means a large raw drag maps to a small display offset).

Each of these maps to a specific lecture section cited above. The project is a composition of well-understood pieces; when one misbehaves, the trace from lecture 1, §5d (or the accessibility checks in lecture 2) points straight at it.

## What this milestone earns you

You can now build a custom interactive component that's *smooth* (elastic resistance, native-feeling springs, interruptible gesture-driven animation) *and* operable by everyone (merged semantics, a custom action for the gesture, AA-audited contrast, font-scale-respecting text). That is the literal "skills earned" for the week: building custom layouts, writing accessible Compose UIs by default, and animation timing that doesn't feel toy-like. You built by hand what Material 3's `SwipeToDismissBox` (Week 11) gives you — which means when you reach for the Material component next, you'll know exactly what it's doing for you, and exactly when to drop back to the hand-built version because you need behavior it doesn't offer. That's the difference between using a framework and understanding it.
