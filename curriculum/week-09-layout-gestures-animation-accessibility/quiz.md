# Week 09 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving on. Answer key with explanations at the bottom — don't peek.

---

**Q1.** In a custom `Layout`, how many times may you call `measure` on a given child per layout pass?

- A) As many times as you like.
- B) Exactly once — the single-measure rule that keeps Compose layout single-pass and fast.
- C) Twice: once for width, once for height.
- D) Zero; children measure themselves.

---

**Q2.** What does a custom `Layout`'s `MeasurePolicy` produce, and in what order?

- A) Pixels, then a tree.
- B) It measures each child into a `Placeable` (respecting incoming `Constraints`), decides its own size, then places each `Placeable` at an offset in the placement block.
- C) It draws each child directly.
- D) It returns an XML layout.

---

**Q3.** `Modifier.padding(16.dp).background(Blue)` vs `Modifier.background(Blue).padding(16.dp)` — what's the difference?

- A) No difference; modifiers are unordered.
- B) The first paints blue *inside* the padding (a blue box with a transparent margin); the second paints blue first, so the padding eats into the blue (a blue border around the content).
- C) The first crashes.
- D) Only the color differs.

---

**Q4.** `Modifier.clickable { }.padding(24.dp)` vs `Modifier.padding(24.dp).clickable { }` — which region is tappable?

- A) Both: the whole area.
- B) First: the padding is part of the touch target (clickable wraps the padding). Second: only the inner content is tappable; the padding is dead space.
- C) Neither is tappable.
- D) They're identical.

---

**Q5.** A drag detector's `onDrag` gives you `dragAmount`. What is it, and what do you do with it?

- A) The absolute finger position; set the offset to it.
- B) The *delta* since the last move; accumulate it into a hoisted state value, which you then read in `Modifier.offset { }` (layout phase).
- C) The velocity; ignore it.
- D) The total drag distance; reset state to it.

---

**Q6.** Inside a drag's `onDrag`, why call `change.consume()`?

- A) For performance.
- B) To tell the system you handled the event so an ancestor (e.g. a scrolling parent) doesn't also act on it.
- C) It's required syntax.
- D) To cancel the gesture.

---

**Q7.** Which animation API is correct for a gesture-driven, interruptible value (a card that follows a finger, then springs back, and can be grabbed mid-spring)?

- A) `animateDpAsState`.
- B) `Animatable` — imperative `snapTo`/`animateTo`, cancellable, so a new gesture interrupts the in-flight animation.
- C) `AnimatedVisibility`.
- D) `Crossfade`.

---

**Q8.** Why prefer a `spring()` over a `tween(300, LinearEasing)` for a drag release?

- A) Springs are faster to compute.
- B) Springs are physics-based and settle with momentum, feeling native/alive; a linear tween feels robotic and scheduled. Use springs for interaction.
- C) Tweens don't work with gestures.
- D) There's no difference.

---

**Q9.** What do accessibility services like TalkBack actually read?

- A) Your composable functions directly.
- B) The semantics tree — a parallel tree of meaning that built-in components populate and custom components must populate via `Modifier.semantics { }`.
- C) The pixel buffer.
- D) The XML layout.

---

**Q10.** Your card dismisses with a horizontal swipe. Why can't a TalkBack user dismiss it, and what's the fix?

- A) They can; swipes work the same.
- B) TalkBack intercepts swipe gestures for its own navigation, so the user can't perform your swipe; the fix is a `CustomAccessibilityAction("Dismiss")` exposed in semantics, which TalkBack offers in its actions menu.
- C) The fix is a bigger card.
- D) The fix is to remove the swipe.

---

**Q11.** What does `Modifier.semantics(mergeDescendants = true) { }` do, and when do you want it?

- A) It hides the element from TalkBack.
- B) It collapses an element and its children into one focusable TalkBack stop — wanted for a composite like a card so it reads as one unit, not three separate stops.
- C) It deletes the children.
- D) It's for performance only.

---

**Q12.** The WCAG AA contrast threshold for normal text is 4.5:1. `#AAAAAA` text on a white background is about 2.3:1. What does that mean, and how do you fix it?

- A) It passes; gray is fine.
- B) It fails AA (illegible for low vision / sunlight); fix by darkening the foreground (or lightening the background) until the computed ratio reaches 4.5:1.
- C) Contrast doesn't apply to text.
- D) Add a border.

---

**Q13.** Why size text in `sp` rather than `dp`?

- A) `sp` is shorter to type.
- B) `sp` honors the system font scale, so a user who set 200% font size sees larger text; `dp` ignores it, excluding low-vision users. (And test the layout at 200% so it doesn't clip.)
- C) `dp` doesn't work for text.
- D) No difference.

---

## Answer key

**Q1 — B.** A child may be measured exactly once per layout pass; this single-measure rule is what makes Compose layout single-pass and avoids the old `View` system's exponential nested re-measurement. (When you must measure-then-compose, use `SubcomposeLayout`.) (Lecture 1, §1–2.)

**Q2 — B.** The `MeasurePolicy` measures each child into a `Placeable` (with constraints you choose, within the parent's), decides its own size within the incoming `Constraints`, then places each `Placeable` in the placement block. Measure → size → place. (Lecture 1, §1–2.)

**Q3 — B.** The chain is outside-in: `padding` then `background` paints blue inside the padding (transparent margin); `background` then `padding` paints blue first, so padding eats into the blue. Each modifier wraps the rest. (Lecture 1, §3.)

**Q4 — B.** `clickable` before `padding` includes the padding in the touch target; `padding` before `clickable` leaves only the inner content tappable. For comfortable, accessible touch targets you usually want `clickable` first. (Lecture 1, §3.)

**Q5 — B.** `dragAmount` is the delta since the last move. You accumulate it into hoisted state (`offsetX += dragAmount.x`) and read that in `Modifier.offset { }` (layout phase) so dragging doesn't recompose. Gesture is a source; state is the truth. (Lecture 1, §4.)

**Q6 — B.** `change.consume()` marks the event handled so an ancestor (a scrollable parent) doesn't also act on it. Forgetting it is why a drag sometimes scrolls the parent instead. (Lecture 1, §4.)

**Q7 — B.** `Animatable` is the imperative, interruptible primitive: `snapTo` follows the finger, `animateTo` springs back or flings, and a new gesture cancels the in-flight animation. `animate*AsState` can't be driven imperatively or interrupted. (Lecture 2, §1.)

**Q8 — B.** Springs are physics-based and settle with momentum, feeling native; a linear tween feels robotic. Use springs for interaction, tweens-with-non-linear-easing for choreography, never `LinearEasing` for anything a human watches. (Lecture 2, §2.)

**Q9 — B.** Accessibility services read the *semantics tree*, a parallel tree of meaning. Built-ins populate it; custom components (a `Box` made tappable, a `Canvas` control) must populate it via `Modifier.semantics { }`. (Lecture 2, §3.)

**Q10 — B.** TalkBack intercepts swipes for its own navigation, so the user can't perform your custom swipe; a `CustomAccessibilityAction` registers an equivalent action TalkBack exposes in its menu. Every gesture-only interaction needs an equivalent action. (Lecture 2, §4.)

**Q11 — B.** `mergeDescendants = true` collapses an element and its children into one focusable stop — right for a card so TalkBack reads it as one unit rather than stopping on each child separately. (Lecture 2, §3.)

**Q12 — B.** ~2.3:1 fails the 4.5:1 AA bar for normal text — illegible for low-vision users and in sunlight. Fix by darkening the foreground or lightening the background until the computed ratio hits 4.5:1. (Lecture 2, §5.)

**Q13 — B.** `sp` honors the system font scale (so a 200% setting enlarges text); `dp` ignores it, excluding low-vision users. Size text in `sp` and test the layout at 200% so it doesn't clip. (Lecture 2, §5.)

---

*Score 11+? You've finished the Compose-runtime arc (Weeks 7–9). Below 9? Re-read both lecture notes and re-run exercises 2 and 3 — the gesture-driven `Animatable` and the semantics-plus-contrast accessibility work are the two ideas this week is graded on.*
