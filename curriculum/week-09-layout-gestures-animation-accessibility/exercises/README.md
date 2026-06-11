# Week 09 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Modifier order and a custom layout](exercise-01-modifier-order-and-custom-layout.md)** — predict-then-confirm a modifier-reorder gallery (where reordering changes paint, size, and touch target), then write a custom flow `Layout` from the measure/place contract. The whole of lecture 1's first half, in one screen. (~45 min)
2. **[Exercise 2 — Drag with spring-back](exercise-02-drag-with-spring-back.kt)** — detect a horizontal drag with `pointerInput`, feed the deltas into an `Animatable`, and spring back on release (or dismiss past a threshold). The mini-project's motion core, isolated. (~50 min)
3. **[Exercise 3 — Semantics and contrast](exercise-03-semantics-and-contrast.kt)** — add `semantics` + a custom action to a gesture-only component so TalkBack can operate it, and write a WCAG contrast-ratio check that fails a bad color pair. The accessibility half of the week. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Run on the **Android emulator** (a Pixel 8 API 35 image is the reference). For exercise 3, **turn on TalkBack** (Settings ▸ Accessibility ▸ TalkBack) and actually operate the component with it.
- The `.kt` exercises drop into your `app` module; exercise 3's contrast check runs as a plain JVM unit test. Each file's header says which.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A gesture-only action with no accessibility equivalent is a bug this week — TalkBack is the arbiter, not "it looks fine."

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-09` to compare.
