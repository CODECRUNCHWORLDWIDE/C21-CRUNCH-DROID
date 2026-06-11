# Week 20 — Exercises

Short, focused drills. Each one should take 30–55 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Window-size-class adaptive layout](exercise-01-window-size-class-adaptive-layout.md)** — drive a list-detail layout from `WindowSizeClass`, reflow it across the Resizable emulator from one pane to two, and observe a live fold with `WindowInfoTracker`. The whole point of lecture 1, in one screen you can *drag* to reflow. (~50 min)
2. **[Exercise 2 — Wear scaling list and navigation](exercise-02-wear-scaling-list-navigation.kt)** — build a Wear screen with `TransformingLazyColumn`, `SwipeDismissableNavHost`, and rotary scroll; make a list look right on a *round* screen and navigate with the system swipe-dismiss gesture. (~45 min)
3. **[Exercise 3 — A Wear tile with ProtoLayout](exercise-03-wear-tile-protolayout.kt)** — author a `TileService` that renders a glanceable card from *cached* data with the `protolayout-material3` builders, version its resources, and set a freshness interval. You'll feel why a tile is not a composition. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Run exercise 1 on the **Resizable (Experimental) emulator** and *drag it* between phone / unfolded / tablet — watch the layout reflow. Run exercises 2 and 3 on a **Wear OS emulator** (Wear OS Large Round, API 34+).
- The `.kt` exercises are written to drop into the `app` (exercise 1's harness) or a `:wear` module (exercises 2–3) of a Compose project. Each file's header says which module and which dependencies.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A Wear list built with phone `LazyColumn`, or a tile that does I/O on render, is a bug this week — the right-surface rule is the arbiter, not your intuition.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-20` to compare.
