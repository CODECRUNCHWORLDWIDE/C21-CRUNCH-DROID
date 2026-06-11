# Week 11 — Resources

Every primary resource on this page is **free**. Android's developer documentation and the Material 3 design site are free without any membership. Material Theme Builder is a free web tool. The samples are public on GitHub. A handful of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **Material 3 in Compose — the official guide.** `MaterialTheme`, `ColorScheme`, `Typography`, `Shapes`. Read this before you touch a color:
  <https://developer.android.com/develop/ui/compose/designsystems/material3>
- **"Material Design 3 — the color system."** Roles, tonal palettes, and what each role is for. The conceptual half of the week:
  <https://m3.material.io/styles/color/system/overview>
- **"Dynamic color in Compose."** `dynamicLightColorScheme` / `dynamicDarkColorScheme`, the SDK gate, and the fallback pattern:
  <https://developer.android.com/develop/ui/compose/designsystems/material3#dynamic_color_schemes>
- **"Display content edge-to-edge."** `enableEdgeToEdge()`, drawing behind the system bars, and the inset story:
  <https://developer.android.com/develop/ui/views/layout/edge-to-edge>
- **"Handle insets in Compose."** `WindowInsets`, `windowInsetsPadding`, `safeDrawing`, `ime`, and the double-padding traps:
  <https://developer.android.com/develop/ui/compose/layouts/insets>

## The APIs (reference, skim don't memorize)

- **`MaterialTheme`:** <https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#MaterialTheme(androidx.compose.material3.ColorScheme,androidx.compose.material3.Shapes,androidx.compose.material3.Typography,kotlin.Function0)>
- **`ColorScheme` / `lightColorScheme` / `darkColorScheme`:** <https://developer.android.com/reference/kotlin/androidx/compose/material3/ColorScheme>
- **`dynamicLightColorScheme` / `dynamicDarkColorScheme`:** <https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary>
- **`Typography`:** <https://developer.android.com/reference/kotlin/androidx/compose/material3/Typography>
- **`enableEdgeToEdge`:** <https://developer.android.com/reference/kotlin/androidx/activity/ComponentActivity#(androidx.activity.ComponentActivity).enableEdgeToEdge(androidx.activity.SystemBarStyle,androidx.activity.SystemBarStyle)>
- **`WindowInsets` and the inset modifiers:** <https://developer.android.com/reference/kotlin/androidx/compose/foundation/layout/WindowInsets>
- **`isSystemInDarkTheme`:** <https://developer.android.com/reference/kotlin/androidx/compose/foundation/package-summary#isSystemInDarkTheme()>

## Tools (free)

- **Material Theme Builder** — paste seed colors (or pick from an image) and export a complete `ColorScheme` (light + dark) as Compose Kotlin. This is how you generate the fallback palette:
  <https://material-foundation.github.io/material-theme-builder/>
- **Material 3 component reference** — every component with its anatomy and the roles it consumes:
  <https://m3.material.io/components>
- **WebAIM Contrast Checker** — paste two hex values, get the ratio and the WCAG pass/fail. Your contrast-audit ground truth:
  <https://webaim.org/resources/contrastchecker/>
- **Accessibility Scanner** (Android) — runs on a device and flags contrast and touch-target issues live:
  <https://support.google.com/accessibility/android/answer/6376570>

## Talks and sessions (free, watch in this order)

- **"Material You: dynamic color"** (Android Developers) — how the system extracts a palette and how apps consume it:
  <https://www.youtube.com/c/AndroidDevelopers>
- **"Building beautiful apps with Material 3 in Compose"** (Google I/O) — theming end to end:
  <https://io.google/>
- **"Going edge-to-edge"** (Android Developers) — the insets model, the common bugs, and the fixes:
  <https://www.youtube.com/c/AndroidDevelopers>
- **Chris Banes — "Edge-to-edge and insets" deep dive** — the clearest explanation of the inset hierarchy:
  <https://chrisbanes.me/>

## The color system (why roles matter)

A color in M3 is a role, not a hex. Internalising the seed → tonal-palette → role pipeline is the conceptual core of the week.

- **"The color roles":** <https://m3.material.io/styles/color/roles>
- **"Tonal palettes and tones":** <https://m3.material.io/styles/color/system/how-the-system-works>
- **"Choosing a scheme — static vs. dynamic":** <https://m3.material.io/styles/color/choosing-a-scheme>
- **HCT color space (the math behind tonal palettes)** — optional, for the curious; explains why tones are perceptually even:
  <https://material.io/blog/science-of-color-design>

## Edge-to-edge and insets

- **"Inset types" reference** (`systemBars`, `safeDrawing`, `safeContent`, `ime`, `navigationBars`, `statusBars`):
  <https://developer.android.com/develop/ui/compose/layouts/insets#inset-types>
- **`Scaffold` and inset handling** — how `Scaffold` consumes insets and passes you a padded `innerPadding`:
  <https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#Scaffold(androidx.compose.ui.Modifier,kotlin.Function0,kotlin.Function0,kotlin.Function0,kotlin.Function0,androidx.compose.material3.FloatingActionButtonPosition,androidx.compose.ui.graphics.Color,androidx.compose.ui.graphics.Color,androidx.compose.foundation.layout.WindowInsets,kotlin.Function1)>
- **IME (keyboard) insets — `imePadding`, `Modifier.imeNestedScroll`:**
  <https://developer.android.com/develop/ui/compose/touch-input/keyboard-input/ime-animations>

## Accessibility and contrast

- **WCAG 1.4.3 Contrast (Minimum)** — the 4.5:1 / 3:1 thresholds:
  <https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html>
- **"Make apps more accessible" (Android):** <https://developer.android.com/guide/topics/ui/accessibility>
- **Compose semantics and accessibility** — touch targets, content descriptions, and large-text reflow:
  <https://developer.android.com/develop/ui/compose/accessibility>

## Community writing (current, opinionated, correct)

- **Now in Android** — Google's flagship sample; its theme module is a model of dynamic-color-with-fallback and a clean `ColorScheme`/`Typography` setup:
  <https://github.com/android/nowinandroid>
- **Android Developers Medium — the Material 3 and edge-to-edge series:**
  <https://medium.com/androiddevelopers>
- **Nick Butcher / Material team on dynamic color** — the people who built it, explaining the extraction pipeline:
  <https://medium.com/androiddevelopers>
- **Chris Banes — theming and insets articles:**
  <https://chrisbanes.me/>

## Open-source projects to read this week

You learn more from one hour reading a real theme module than from three hours of tutorials. Pick one and trace how it builds the `ColorScheme` and gates dynamic color:

- **`android/nowinandroid`** — read `core/designsystem` and the `NiaTheme` composable: dynamic color, fallback, dark theme, and a tasteful `ColorScheme` in one place:
  <https://github.com/android/nowinandroid>
- **`android/compose-samples`** (e.g. JetNews, Jetsnack) — each has a hand-built theme you can compare against dynamic color:
  <https://github.com/android/compose-samples>
- **`material-components/material-components-android-compose-theme-adapter`** history — how teams bridged old XML themes into Compose M3 (useful background even though new apps skip it):
  <https://github.com/material-components>

## Tools you'll use this week

- **Two emulators** — an Android 15 (API 35) image for dynamic color + edge-to-edge gesture nav, and an Android 11 (API 30) image to exercise the fallback palette. Create both in Device Manager.
- **The wallpaper picker** — change the emulator's wallpaper (Settings ▸ Wallpaper) and relaunch your app to watch dynamic color re-extract. The most satisfying demo of the week.
- **Layout Inspector** — confirm a composable's padding matches the system-bar inset and isn't doubled.
- **`adb shell cmd uimode night yes|no`** — flip dark mode from the command line to test both without touching the UI.

## Free resources (chapter-level, not whole books)

- **Android's "Material Design 3" pathway and the theming codelab** on `developer.android.com/courses` are effectively a free book; the codelab walks dynamic color and the fallback end to end.
- **The Material 3 site's per-component pages** double as a free reference for which role each component reads.

## Paid books (optional, clearly marked)

- **"Jetpack Compose by Tutorials" — Kodeco** (paid). Broad Compose; the theming and Material 3 chapters are a solid structured walk if you prefer a book to docs.
- **"Jetpack Compose Internals" — Jorge Castillo** (paid). Explains *why* `MaterialTheme` propagates via `CompositionLocal` and how recomposition interacts with theme changes.

---

*If a link 404s, please open an issue so we can replace it.*
