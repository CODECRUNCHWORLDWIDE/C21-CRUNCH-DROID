# Week 20 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 21. Answer key with explanations at the bottom — don't peek.

---

**Q1.** Why is `if (resources.configuration.smallestScreenWidthDp >= 600)` the wrong way to decide a two-pane layout?

- A) It's slower than a window size class.
- B) It reads the *device*, not the available *window* (wrong in split-screen, never updates when a foldable unfolds mid-session, and encodes a guess about intent).
- C) `smallestScreenWidthDp` doesn't exist anymore.
- D) Two-pane layouts are always wrong on phones.

---

**Q2.** What are the three window *width* size classes and their breakpoints?

- A) SMALL `<400dp`, MEDIUM `400–800dp`, LARGE `≥800dp`.
- B) COMPACT `<600dp`, MEDIUM `600–840dp`, EXPANDED `≥840dp`.
- C) PHONE, TABLET, DESKTOP.
- D) PORTRAIT, LANDSCAPE, SQUARE.

---

**Q3.** You read the window size class with `currentWindowAdaptiveInfo()`. What makes the layout reflow *live* when a foldable unfolds?

- A) A `BroadcastReceiver` you register.
- B) `currentWindowAdaptiveInfo()` is a composable that reads the current window metrics and *recomposes* when they change, so the branch re-evaluates.
- C) You must restart the activity.
- D) Nothing; you have to poll.

---

**Q4.** What does `NavigableListDetailPaneScaffold` give you that a hand-rolled `when (widthClass) { ... }` does not?

- A) Faster rendering.
- B) The pane reflow *plus* the per-form-factor navigation and the correct predictive-back behavior (compact: detail→list; expanded: in-place).
- C) Dynamic color.
- D) Nothing; it's the same.

---

**Q5.** A device is half-opened with a *horizontal* hinge, sitting like a tiny laptop. Which posture is this, and how do you detect it?

- A) Book posture; `orientation == VERTICAL`.
- B) Tabletop posture; `FoldingFeature.state == HALF_OPENED && orientation == HORIZONTAL`.
- C) Flat posture; `state == FLAT`.
- D) You can't detect posture from `FoldingFeature`.

---

**Q6.** Why collect `WindowInfoTracker.windowLayoutInfo(...)` with `collectAsStateWithLifecycle` rather than `collectAsState`?

- A) It's required to compile.
- B) Lifecycle-aware collection stops tracking window changes while the app is backgrounded, saving battery and avoiding work for a UI nobody is looking at.
- C) `collectAsState` doesn't exist for `Flow`.
- D) It makes the flow hot.

---

**Q7.** On Wear OS, why use `TransformingLazyColumn` (or `ScalingLazyColumn`) instead of the phone's `LazyColumn`?

- A) It's faster.
- B) The round screen curves away at top and bottom; the scaling list shrinks and fades items toward the edges so they look intentional instead of clipped. A plain `LazyColumn` renders hard against the curve and looks broken.
- C) `LazyColumn` doesn't compile on Wear.
- D) It's required for rotary input.

---

**Q8.** Why does Wear OS use `SwipeDismissableNavHost` instead of the phone `NavHost`?

- A) Wear can't use the phone navigation library.
- B) The system owns the left-edge swipe as the back gesture; the Wear host cooperates with it, whereas a phone host would fight it.
- C) It supports deep links and the phone one doesn't.
- D) No reason; they're interchangeable.

---

**Q9.** Why is a Wear **tile** *not* a `@Composable`?

- A) Tiles don't support Kotlin.
- B) The system renders tiles in its own process, on its own schedule, even when your app isn't running — so it can't run a live composition. You hand it a *serialized ProtoLayout* tree plus versioned resources, and it renders that.
- C) Tiles are written in XML.
- D) Tiles only show images.

---

**Q10.** What is the cardinal rule for data in a tile's `onTileRequest` (and a complication's `onComplicationRequest`)?

- A) Always fetch the freshest data over the network.
- B) Read *cached, local* data synchronously — never fetch on render, because the surface may render with the app dead and the network down. Push refreshes when fresh data lands.
- C) Block until the network responds.
- D) Return random placeholder data.

---

**Q11.** A watch-face slot accepts `SHORT_TEXT`. Your `ComplicationDataSourceService` supplies a `RANGED_VALUE`. What happens?

- A) The watch face auto-converts it.
- B) The slot shows nothing — you must supply the `ComplicationType` the slot expects.
- C) It crashes the watch face.
- D) It shows the ranged value as text.

---

**Q12.** When should you use an `OngoingActivity`?

- A) For any notification.
- B) For a genuinely *ongoing* task (a workout, a timer, an active rain alert) — it surfaces the live task on the watch face and recents, bound to a notification, and should be cancelled when the task ends.
- C) To replace the app launcher.
- D) For one-shot alerts that don't persist.

---

**Q13.** For a course-sized team, which form-factor allocation is the *senior* call?

- A) Build full, separate apps for phone, tablet, foldable, Wear, TV, and Automotive.
- B) Phone always; one *adaptive* layout for phone/tablet/foldable; Wear if the product has a glanceable use case; TV and Automotive as overview knowledge and deferred backlog items unless the product specifically targets them.
- C) Only build for phones; everything else is a waste.
- D) Build TV first because the screen is biggest.

---

## Answer key

**Q1 — B.** A device check reads the wrong thing: it's wrong in split-screen (app gets half the display but device width is unchanged), it never updates when a foldable unfolds mid-session, and "tablet means two panes" smuggles a design intent into a device fact. Read available *window* space, reactively. (Lecture 1, §1.)

**Q2 — B.** `COMPACT < 600dp`, `MEDIUM 600–840dp`, `EXPANDED ≥ 840dp`. Coarse by design — three layouts covering bands of devices, not pixel-tuning per device. (Lecture 1, §2.)

**Q3 — B.** `currentWindowAdaptiveInfo()` is a composable that reads current window metrics and recomposes on change, so your `when` re-evaluates and the UI reflows live — the declarative model applied to window space. (Lecture 1, §2.)

**Q4 — B.** The navigable scaffold reflows the panes *and* does the per-form-factor navigation *and* wires predictive back correctly (compact pops detail→list; expanded swaps in place). The back behavior is the most-botched part of a hand-rolled adaptive layout. (Lecture 1, §3.)

**Q5 — B.** Tabletop: half-opened with a horizontal hinge. Detect it via `state == HALF_OPENED && orientation == HORIZONTAL`. (Book is half-opened + vertical.) (Lecture 1, §4.)

**Q6 — B.** Lifecycle-aware collection stops while backgrounded — you're not tracking window changes for a UI nobody sees, which matters for battery. (Lecture 1, §4.)

**Q7 — B.** The round screen curves away at the top and bottom; the scaling list fades/shrinks items toward the edges so the curve looks intentional. A plain `LazyColumn` renders items hard against the curve and looks broken. (Lecture 2, §1–2.)

**Q8 — B.** The system owns the left-edge swipe as back; `SwipeDismissableNavHost` cooperates with that gesture. A phone `NavHost` fights it. (Lecture 2, §2.)

**Q9 — B.** The system renders tiles in its process, on its schedule, with your app possibly dead — it can't run a live composition. You provide a serialized ProtoLayout plus versioned resources. No state, no recomposition. (Lecture 2, §3.)

**Q10 — B.** Read cached, local data synchronously; never fetch on render (the surface may render offline with the app dead). Populate the cache from your sync and push refreshes (`getUpdater`/`ComplicationDataSourceUpdateRequester`). (Lecture 2, §3–4.)

**Q11 — B.** A slot renders only the `ComplicationType` it declares it accepts; supply the wrong type and the slot shows nothing. You declare supported types and provide the matching one. (Lecture 2, §4.)

**Q12 — B.** For genuinely ongoing tasks, surfaced on the watch face/recents via a notification, with a live status — and cancelled when the task ends. A lingering ongoing activity is a stuck-notification bug. (Lecture 2, §5.)

**Q13 — B.** Phone always; one adaptive layout for the big-glass trio; Wear for the right product; TV/Automotive as overview + deferred backlog unless specifically targeted. Breadth without judgment is waste. (Lecture 2, §8.)

---

*Score 11+? On to Week 21. Below 9? Re-read both lecture notes and re-run exercises 2 and 3 — the Wear scaling-list/navigation components and the tile-as-serialized-layout idea are the two ideas this week is graded on.*
