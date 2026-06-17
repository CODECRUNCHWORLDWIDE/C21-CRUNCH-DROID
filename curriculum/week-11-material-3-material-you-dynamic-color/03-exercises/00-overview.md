# Week 11 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Theme with color roles](./exercise-01-theme-with-color-roles.md)** — build a `MaterialTheme` with a custom `ColorScheme` generated from seed colors, then convert a screen full of hardcoded `Color(0xFF…)` values to semantic roles. The whole point of the week, in one exercise: stop painting colors, start assigning roles. (~45 min)
2. **[Exercise 2 — Dynamic color with a fallback](./exercise-02-dynamic-color-with-fallback.kt)** — write and test the `chooseColorScheme(darkTheme, dynamicColor)` selection logic that routes Android 12+ to the wallpaper palette and everything else to a hand-tuned fallback, across the full version/mode matrix. (~50 min)
3. **[Exercise 3 — Edge-to-edge and insets](./exercise-03-edge-to-edge-and-insets.kt)** — draw edge-to-edge and pad with the right window insets; a test that the content inset matches the system-bar inset, that scrollables use `contentPadding`, and that the IME inset is consumed. (~40 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Run the visual ones on **two emulators** — an Android 15 (API 35) image for dynamic color + edge-to-edge, and an Android 11 (API 30) image to exercise the fallback. The logic tests run on the JVM with Robolectric.
- The `.kt` exercises are written to drop into a `test` (Robolectric) or `androidTest` source set, as each file's header says.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A hardcoded `Color(0xFF…)` on a component is this week's equivalent of a suppressed warning — it's the bug, hidden.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-11` to compare.
