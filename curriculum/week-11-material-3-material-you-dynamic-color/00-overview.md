# Week 11 — Material 3, Material You, dynamic color, edge-to-edge

Welcome to Week 11 of **C21 · Crunch Droid**. Last week you built Catalog Companion's skeleton — a typed navigation graph with tabs, deep links, and predictive back — and you themed it with whatever Material 3 defaults the project template handed you. This week the app stops looking like a template and starts looking *shipped*. By Friday it has a real Material 3 `ColorScheme` and `Typography`, it pulls **dynamic color** from the user's wallpaper on Android 12+, it falls back to a hand-tuned brand palette on older devices, it draws **edge-to-edge** behind the status and navigation bars with correct window-inset padding, and it has a dark theme you have audited for contrast — not guessed at.

Material 3 (often "M3") is Google's current design system, and Material You is the personalization layer on top of it that landed with Android 12 in 2021. In 2026, M3 is the default for a new Compose app and Material You is what users expect: the system extracts a color palette from their wallpaper and apps tint themselves to match, so the whole device feels coherent and personal. The headline fact this week hammers on is that **a color in Material 3 is a *role*, not a hex value.** You do not paint a button "blue." You assign it `primary`, and a button rendered with `primary` is the right color in light theme, the right color in dark theme, the right color when dynamic color is on, and the right color on the user's specific wallpaper — all from one role. Theming an app in M3 is the discipline of expressing intent in roles (`primary`, `surface`, `onSurface`, `errorContainer`) and letting the `ColorScheme` resolve them, instead of hardcoding colors that look fine in one configuration and wrong in three others.

The mental shift this week is from "I set colors on components" to "**I define a `ColorScheme` of roles, and components read the role they need from the theme.**" A `MaterialTheme { }` is the boundary that makes a `ColorScheme`, a `Typography`, and a `Shapes` available to every composable beneath it via `MaterialTheme.colorScheme`, `MaterialTheme.typography`, `MaterialTheme.shapes`. Dynamic color is one decision *inside* that boundary: on Android 12+ you build the `ColorScheme` from `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)`, which read the system-extracted palette; below that, you build it from your own brand seed colors. The component code does not change — `Button`, `Card`, `TopAppBar` all read roles — only the `ColorScheme` you feed the theme changes. That separation is what lets one widget tree look right across light, dark, dynamic, and fallback without a single conditional in the UI.

We close the week by building **Pocket Reader**, a reader app with full Material 3 theming: dynamic color on Android 12+, a hand-tuned fallback `ColorScheme` (light and dark) built from brand seed colors for Android 11 and below, edge-to-edge layout with `enableEdgeToEdge()` and inset-aware padding so content never hides behind the system bars, and a dark theme you audit for WCAG contrast — measuring `onSurface`-on-`surface` and `onPrimary`-on-`primary` contrast ratios and fixing any that fall short. You will also take a component palette and audit it for contrast on purpose, because "the dark theme looks a bit washed out" is a real bug report, and the skill this week earns is shipping color that is *correct*, not just color that *renders*.

## Learning objectives

By the end of this week, you will be able to:

- **Explain** Material 3's color-role model — that a color is a semantic role (`primary`, `surface`, `onSurface`, `*Container`, `on*`) resolved by a `ColorScheme`, not a hardcoded hex — and predict which role a component reads and why that makes theming configuration-proof.
- **Build** a `MaterialTheme` with a custom `ColorScheme`, `Typography`, and `Shapes`, and reach the theme from any composable via `MaterialTheme.colorScheme` / `.typography` / `.shapes` rather than constructing colors inline.
- **Implement** dynamic color: `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` on Android 12+ (`Build.VERSION.SDK_INT >= S`), with a graceful, hand-tuned fallback `ColorScheme` built from brand seed colors below that version.
- **Generate** a tonal palette from seed colors and assign the standard roles, and explain the difference between a *seed* color, the *tonal palette* derived from it, and the *role* a component consumes.
- **Draw** edge-to-edge: call `enableEdgeToEdge()`, let content extend behind the system bars, and pad with `WindowInsets` (`systemBars`, `safeDrawing`, `ime`) via `Modifier.windowInsetsPadding` / `Scaffold`'s inset handling so nothing is occluded.
- **Apply** window insets correctly per surface — consume them once, avoid double padding, handle the IME (keyboard) inset for a text field — and recognise the "content under the nav bar" and "doubled top padding" bugs.
- **Audit** a theme for accessibility: compute contrast ratios for `on*`-on-base role pairs against the WCAG 1.4.3 thresholds (4.5:1 for body text, 3:1 for large text and UI), and fix a failing pair by adjusting the tonal value.
- **Theme** dark mode as a first-class configuration, not an afterthought — distinct `surface` elevation tints, sufficient contrast, dynamic-dark on 12+, hand-tuned-dark below — and verify it across the configuration matrix.

## Prerequisites

This week assumes you have completed **C21 weeks 1–10**, or have equivalent fluency. Specifically:

- You can write composables, hoist state, and reason about recomposition — Weeks 7–9. Theming reads from a `CompositionLocal` (`MaterialTheme.colorScheme` is one); if how a `CompositionLocal` propagates down the tree is fuzzy, theming will feel like magic.
- You have **Catalog Companion** (Week 10) or an equivalent multi-screen Compose app with a `Scaffold`, a `NavigationBar`, and a `TopAppBar`. This week themes *that* app — the screens stay, the look changes — so a working multi-screen app is the canvas.
- You understand `Modifier` chains and ordering — Week 9. Inset padding is a `Modifier`, and *where* in the chain you apply it changes what gets padded; order is load-bearing for edge-to-edge.
- You can read `Build.VERSION.SDK_INT` checks and reason about API-level gating — Week 6. Dynamic color is `Build.VERSION_CODES.S` (API 31) and up; the fallback path is the interesting half.

**Toolchain.** Android Studio Ladybug (2024.2) or newer, AGP 8.5+, Kotlin 2.0+ with the Compose Compiler plugin, `compileSdk 35` targeting Android 15, `minSdk 24`. Material 3 via `androidx.compose.material3:material3` (and `material3-window-size-class` for later weeks). `androidx.activity:activity-compose` for `enableEdgeToEdge()`. You will test on **two** emulators: an Android 14/15 image (for dynamic color and edge-to-edge gesture nav) and an Android 11 / API 30 image (to exercise the fallback palette). Material Theme Builder (the web tool) generates a starting `ColorScheme` from seed colors — link on the resources page.

## Topics covered

- **The Material 3 color-role model.** Roles (`primary`, `secondary`, `tertiary`, `surface`, `surfaceVariant`, `background`, `error`, and every `on*` and `*Container`), what each is *for*, and why a component reads a role rather than a color.
- **`MaterialTheme` and the three `CompositionLocal`s.** `ColorScheme`, `Typography`, `Shapes`; reading them via `MaterialTheme.colorScheme` etc.; why constructing colors inline defeats theming.
- **Seed colors → tonal palettes → roles.** What a seed color is, how a tonal palette (tones 0–100) is derived, and how the standard roles map onto palette tones. Material Theme Builder as the generator.
- **Dynamic color (Material You).** `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)`; the `Build.VERSION.SDK_INT >= S` gate; what "extracted from the wallpaper" means and where the extraction happens (the system, not your app).
- **The fallback palette.** Building a hand-tuned light and dark `ColorScheme` from brand seed colors for pre-Android-12; why a good fallback matters and how to keep it close to the dynamic feel without copying it.
- **Edge-to-edge.** `enableEdgeToEdge()`; content drawing behind the status and navigation bars; the modern transparent-system-bars default; gesture-nav vs. three-button nav and what each looks like edge-to-edge.
- **Window insets.** `WindowInsets.systemBars`, `safeDrawing`, `safeContent`, `ime`; `Modifier.windowInsetsPadding`, `Scaffold`'s automatic inset consumption, `consumeWindowInsets`; the double-padding and content-occlusion bugs.
- **The IME inset.** Reacting to the keyboard with `WindowInsets.ime` / `imePadding()` so a focused text field rises above the keyboard instead of hiding behind it.
- **Dark theme.** Treating dark as a peer configuration: `isSystemInDarkTheme()`, distinct surface tints and elevation overlays, dynamic-dark vs. hand-tuned-dark, and verifying both.
- **Contrast auditing.** WCAG 1.4.3 thresholds (4.5:1 / 3:1), computing the contrast ratio for role pairs, and fixing a failing pair by moving a tonal value — measured, not eyeballed.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                              | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|--------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | The color-role model; `MaterialTheme`; seed → tonal palette → roles  |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | Dynamic color on 12+; the hand-tuned fallback palette               |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Edge-to-edge; window insets; the IME inset; double-padding bugs     |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | Dark theme as a peer config; contrast auditing; challenge           |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — Pocket Reader: full M3 theme, dynamic + fallback     |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work; edge-to-edge + dark + contrast audit         |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                         |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                    | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./00-overview.md) | This overview (you are here) |
| [resources.md](./01-resources.md) | The Material 3 docs, the color-system and theming guides, Material Theme Builder, the edge-to-edge and window-insets guides, and the WCAG contrast reference |
| [lecture-notes/01-material-3-color-roles-and-dynamic-color.md](./02-lecture-notes/01-material-3-color-roles-and-dynamic-color.md) | M3 end to end: the color-role model, `MaterialTheme`, seed → tonal palette → roles, dynamic color on 12+, and the hand-tuned fallback below |
| [lecture-notes/02-edge-to-edge-insets-dark-theme-contrast.md](./02-lecture-notes/02-edge-to-edge-insets-dark-theme-contrast.md) | Edge-to-edge with `enableEdgeToEdge()`, window insets and the IME inset, dark theme as a peer configuration, and contrast auditing against WCAG |
| [exercises/README.md](./03-exercises/00-overview.md) | Index of the three exercises |
| [exercises/exercise-01-theme-with-color-roles.md](./03-exercises/exercise-01-theme-with-color-roles.md) | Build a `MaterialTheme` with a custom `ColorScheme` from seed colors; convert hardcoded colors in a screen to roles |
| [exercises/exercise-02-dynamic-color-with-fallback.kt](./03-exercises/exercise-02-dynamic-color-with-fallback.kt) | Choose dynamic vs. fallback `ColorScheme` by SDK level and dark mode; a test of the selection logic across the version/mode matrix |
| [exercises/exercise-03-edge-to-edge-and-insets.kt](./03-exercises/exercise-03-edge-to-edge-and-insets.kt) | Draw edge-to-edge and pad with the right insets; a test that the content inset matches the system-bar inset and the IME inset is consumed |
| [challenges/README.md](./04-challenges/00-overview.md) | Index of the challenge |
| [challenges/challenge-01-contrast-audit-then-fix.md](./04-challenges/challenge-01-contrast-audit-then-fix.md) | Audit a theme's role pairs for WCAG contrast, find the failing pair, fix it by moving a tonal value, and document the before/after ratios |
| [quiz.md](./05-quiz.md) | 13 questions on color roles, `MaterialTheme`, dynamic color, the fallback, edge-to-edge, insets, dark theme, and contrast |
| [homework.md](./06-homework.md) | Six practice problems for the week |
| [mini-project/README.md](./07-mini-project/00-overview.md) | Full spec for "Pocket Reader": full M3 theme, dynamic color + fallback, edge-to-edge, audited dark theme |

## The "correct across the configuration matrix" promise

Week 7 gave you "renders exactly once." Week 10 gave you "no string routes." Week 11 adds the theming contract a senior reviewer actually checks:

> **The app looks right across the full configuration matrix, and the colors are roles, not hardcoded hex.** Light and dark, dynamic color on and off, Android 12+ and Android 11, gesture nav and three-button nav — the app is legible, the contrast passes WCAG, and nothing hides behind a system bar in any of them. If a reviewer can find a `Color(0xFF…)` painted directly on a component, or a screen that's unreadable in dark mode, or content occluded by the nav bar edge-to-edge, the theming is not done — no matter how nice the light-mode-dynamic-on screenshot looks.

You will *prove* this by running Pocket Reader through the matrix — two emulators (API 30 and API 35), light and dark, dynamic on and off — and by computing contrast ratios for the key role pairs rather than eyeballing them. "It looked fine on my phone" is not the test; the test is that it is correct in every cell of the matrix, measured.

## A note on what's not here

Week 11 is the *theming and layout-chrome* week. It deliberately does **not** cover:

- **Architecture.** Pocket Reader's article list is in-memory; we do not yet draw the data/domain/UI boundary or model `UiState` — that is Week 12 (MVVM, UDF, Now-In-Android). This week the point is how it *looks*, not where the data *lives*.
- **Custom layouts and gestures.** We use Material 3 components (`Scaffold`, `TopAppBar`, `Card`, `NavigationBar`) as-is; building a custom `Layout` or a gesture-driven component was Week 9. Edge-to-edge here is about insets on standard components, not bespoke layout.
- **Adaptive multi-pane and form factors.** Window size classes, foldables, and Wear are Phase 4. This week is a single phone (and tablet) layout themed correctly, not an adaptive one.

The point of Week 11 is narrow and deep: one `ColorScheme` of roles, the theme that resolves them, dynamic color with a graceful fallback, edge-to-edge with correct insets, and a dark theme audited for contrast.

## Up next

Continue to **Week 12 — MVVM, UDF, the Now-In-Android pattern** once you have shipped Pocket Reader and proven it across the configuration matrix. Week 12 takes the navigated, themed app you now have and gives it a real architecture: a `ViewModel`-driven `StateFlow<UiState>`, unidirectional data flow, a data/domain/UI layer split modelled on Now-In-Android, and survival of process death via `SavedStateHandle`. The navigation (Week 10) is the skeleton, the theme (this week) is the skin, and the architecture (next week) is the nervous system. Every one of those weeks assumes the previous one is solid — a beautifully themed app with no architecture is a demo, and an architected app with hardcoded colors looks unfinished. Earn the theme this week.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
