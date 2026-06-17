# Week 11 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 11 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Android 15 (`compileSdk 35`), `minSdk 24`, Kotlin 2.0+ with the Compose Compiler plugin, Material 3. Every problem must build with **0 warnings**.

---

## Problem 1 — Hunt every hardcoded color

**Problem statement.** Take your mini-project (or exercise 1) and prove no component reads a literal color. Run the grep, paste the result into `notes/no-literals.md`, and for any hit, convert it to the role it was trying to be. Then write two sentences: which configuration each former literal would have broken in, and which role replaced it.

**Acceptance criteria.**

- `notes/no-literals.md` with the grep command and result.
- `grep -rn 'Color(0xFF' app/src/main` finds hits **only** in the `ColorScheme` definition file.
- Two sentences on what broke and what role fixed it.
- Committed.

**Hint.** The usual offenders: a `background(Color(...))`, a `Text(color = Color(...))`, a `ButtonDefaults.buttonColors(containerColor = Color(...))`. Each maps to a base role or its `on*` partner.

**Estimated time.** 25 minutes.

---

## Problem 2 — A typography scale, read as roles

**Problem statement.** Define a custom `Typography` overriding at least four type roles (`headlineMedium`, `titleLarge`, `bodyLarge`, `labelSmall`) with a real font family, wire it into your theme, and convert a screen's inline `fontSize`/`fontWeight` `TextStyle`s to `MaterialTheme.typography.*` roles. Confirm changing one role's size in the `Typography` updates every screen using it.

**Acceptance criteria.**

- A `Typography` with ≥4 overridden roles using a `FontFamily`.
- A screen reading `style = MaterialTheme.typography.<role>`, no inline `TextStyle(fontSize = …)`.
- A demonstrated single-point change (bump `titleLarge` size, see it everywhere).
- 0 warnings. Committed.

**Hint.** Override only the roles you care about; the rest inherit M3 defaults. `Text("…", style = MaterialTheme.typography.titleLarge)` reads the role. If a change in one place doesn't propagate, the screen is still using an inline style somewhere.

**Estimated time.** 40 minutes.

---

## Problem 3 — Test the scheme-selection matrix

**Problem statement.** Extend exercise 2's `schemeSource` (or your real `chooseColorScheme` decision) to also handle a third "high-contrast" mode the user can pick, and write tests covering the new cells: high-contrast overrides dynamic, in both light and dark, on both API levels. Keep the function pure and total.

**Acceptance criteria.**

- A `schemeSource` (or equivalent) with a `highContrast: Boolean` that, when on, returns a high-contrast fallback regardless of dynamic/API.
- Tests for the new cells plus the original eight (high-contrast off behaves as before).
- The function is pure (no `Build.VERSION.SDK_INT` read inside; api level passed in). 0 warnings. Committed.

**Hint.** High-contrast is a fallback variant: `HC_LIGHT`/`HC_DARK`. Add a branch *before* the dynamic check so it wins. The boundary tests from exercise 2 (`apiLevel == 31`, `== 30`) still matter — don't drop them.

**Estimated time.** 45 minutes.

---

## Problem 4 — Fix a real edge-to-edge occlusion bug

**Problem statement.** Build (or find in your app) a screen where content is occluded under the navigation bar edge-to-edge — e.g. a button or the last list item hidden behind the gesture pill or the three-button bar. Write down the symptom, fix it with the correct inset (`contentPadding` for a scrollable, `navigationBarsPadding()` for a fixed bottom element), and confirm it on **both** gesture and three-button nav.

**Acceptance criteria.**

- `notes/occlusion-fix.md` describing the symptom, the cause (content drawn behind the nav bar with no inset), and the fix.
- The fixed screen verified on gesture nav **and** three-button nav (note both).
- No double padding introduced by the fix.
- 0 warnings. Committed.

**Hint.** A fixed bottom button uses `Modifier.navigationBarsPadding()` (or `windowInsetsPadding(WindowInsets.navigationBars)`); a scrollable uses `contentPadding`. Switch nav modes in the emulator (Settings ▸ System ▸ Gestures) — three-button nav is the harsher test.

**Estimated time.** 45 minutes.

---

## Problem 5 — Audit two role pairs and fix one

**Problem statement.** Implement the WCAG `contrastRatio` (lecture 2, §5), audit two role pairs in your dark theme — pick `onSurfaceVariant`/`surfaceVariant` and one container pair — record both ratios in `notes/contrast.md`, and if either is below 4.5:1, fix it by moving a tone and record the new ratio.

**Acceptance criteria.**

- A correct `contrastRatio`/`relativeLuminance` implementation (the WCAG formula).
- `notes/contrast.md` with both pairs' before-ratios and, for any failure, the tone moved and the after-ratio.
- A test asserting the audited pairs now clear their threshold (4.5 body).
- 0 warnings. Committed.

**Hint.** The formula is `(lighter + 0.05)/(darker + 0.05)` on relative luminances. Cross-check your number against the WebAIM Contrast Checker — if they disagree, your luminance channel function is wrong (the 0.03928 sRGB linearization is the usual slip).

**Estimated time.** 50 minutes.

---

## Problem 6 — An animated dark-mode switch

**Problem statement.** Make the transition between light and dark mode animate rather than snap. When the user flips the dark-mode toggle, the key surface and primary colors should crossfade. Use `animateColorAsState` on the scheme colors (or a derived animated `ColorScheme`) so the change is smooth.

**Acceptance criteria.**

- Flipping dark mode produces a visible crossfade of at least the background/surface and primary colors, not an instant snap.
- The animation doesn't break contrast at the endpoints (it's the same audited schemes, animated between).
- The toggle state `rememberSaveable`s (survives rotation mid-animation).
- 0 warnings. Committed.

**Hint.** Build an animated `ColorScheme` by wrapping the chosen scheme's colors in `animateColorAsState` and passing the animated values to `MaterialTheme(colorScheme = animatedScheme)`. Animate the handful that matter (background, surface, surfaceVariant, primary) — animating all 30 is overkill. Don't animate *into* a contrast failure; the endpoints are your audited schemes.

**Estimated time.** 50 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, code is idiomatic Compose/Material 3, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. an inline `TextStyle` left in, animating all 30 colors, a redundant inset). |
| 3 | Works, but misses one criterion (e.g. a literal color left on a component, the contrast formula slightly off, occlusion fixed on only one nav mode). |
| 2 | Compiles and partially works; a core idea is wrong (hardcoded colors throughout, double padding, dynamic color not gated by SDK level). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for any literal `Color(0xFF…)` on a component where a role belonged; **−2** for content occluded by a system bar (no inset) or doubled inset padding; **−1** for dynamic color not gated by `Build.VERSION.SDK_INT >= S`.

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — roles-not-literals (problems 1, 2) and edge-to-edge insets (problems 4) — so re-run exercises 01 and 03 before resubmitting.
