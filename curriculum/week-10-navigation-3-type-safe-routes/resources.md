# Week 10 — Resources

Every primary resource on this page is **free**. Android's developer documentation is free without any membership. The AndroidX source is public on GitHub and `cs.android.com`. The Google I/O and "Android Developers" sessions are free on YouTube. A handful of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **Navigation 3 — the official guide.** The library overview, the back-stack model, `NavDisplay`, and `entryProvider`. Read this before you write a route:
  <https://developer.android.com/guide/navigation/navigation-3>
- **"Navigation 3 — basics."** The minimal app: a back stack, a `NavDisplay`, an `entryProvider`. This is the shape of every exercise:
  <https://developer.android.com/guide/navigation/navigation-3/basics>
- **"Define routes and destinations."** Routes as `@Serializable` types, `NavKey`, and the `entry<T>` DSL:
  <https://developer.android.com/guide/navigation/navigation-3/define-routes>
- **"Deep links in Compose navigation."** Mapping an incoming `Uri` to a destination; the manifest `<intent-filter>` and App Links story carries straight over:
  <https://developer.android.com/guide/navigation/design/deep-link>
- **"Add support for predictive back."** The system back gesture, `enableOnBackInvokedCallback`, and how a navigation library participates:
  <https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture>

## The artifacts and APIs (reference, skim don't memorize)

- **`androidx.navigation3` API reference:** <https://developer.android.com/reference/kotlin/androidx/navigation3/runtime/package-summary>
- **`NavDisplay`:** <https://developer.android.com/reference/kotlin/androidx/navigation3/ui/NavDisplay>
- **`NavBackStack` / `rememberNavBackStack`:** <https://developer.android.com/reference/kotlin/androidx/navigation3/runtime/package-summary>
- **`entryProvider` / `entry` DSL:** <https://developer.android.com/reference/kotlin/androidx/navigation3/runtime/package-summary>
- **`NavKey`** — the marker interface a route type implements: same reference page.
- **`lifecycle-viewmodel-navigation3`** — ViewModel scoped to a back-stack entry:
  <https://developer.android.com/jetpack/androidx/releases/lifecycle>
- **`kotlinx.serialization` — `@Serializable`:** <https://github.com/Kotlin/kotlinx.serialization>
- **`onBackPressedDispatcher` / `OnBackPressedCallback`** (predictive back under the hood):
  <https://developer.android.com/reference/androidx/activity/OnBackPressedCallback>

## Talks and sessions (free, watch in this order)

- **"Building UI with the Navigation 3 library"** (Android Developers) — the introduction; the back-stack model, `NavDisplay`, typed routes:
  <https://www.youtube.com/c/AndroidDevelopers>
- **"What's new in Jetpack"** (Google I/O 2025) — where Nav3 is positioned relative to the old Navigation-Compose:
  <https://io.google/2025/>
- **"Mastering navigation in Compose"** (Android Developers) — the older Navigation-Compose model; useful as the *before* picture you are migrating away from:
  <https://www.youtube.com/c/AndroidDevelopers>
- **"Predictive back: a deep dive"** (Android Developers) — the gesture, the animation contract, and how to test it:
  <https://www.youtube.com/c/AndroidDevelopers>

## The old model (why this matters)

Navigation 3 replaced Navigation-Compose's string routes. You will not write the old model this week, but you should be able to *read* it — most existing apps and most Stack Overflow answers are still in it, and Tuesday's exercise migrates one.

- **Navigation-Compose (the predecessor):** <https://developer.android.com/develop/ui/compose/navigation>
- **Type-safe navigation in Navigation-Compose** (the half-step between string routes and Nav3, `@Serializable` routes on the *old* `NavHost`):
  <https://developer.android.com/guide/navigation/design/type-safety>
- **`NavHost` / `composable` / `navArgument`** — the string-route APIs you are retiring:
  <https://developer.android.com/reference/kotlin/androidx/navigation/compose/package-summary>

Reading the old model makes the new one legible: every Nav3 concept is a direct answer to a specific pain in `NavHost` + `composable("route/{arg}")`.

## Deep links and App Links

- **"Create deep links to app content":** <https://developer.android.com/training/app-links/deep-linking>
- **"Verify Android App Links":** <https://developer.android.com/training/app-links/verify-android-applinks>
- **`adb shell am start`** for testing a deep link from the command line:
  <https://developer.android.com/tools/adb#am>
- **App Links Assistant** in Android Studio — generates the `<intent-filter>` and the `assetlinks.json` for you.

## Predictive back

- **Predictive back design guidance:** <https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back>
- **"Add predictive back animations":** <https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture#add-animations>
- **Compose `BackHandler` and `PredictiveBackHandler`:** <https://developer.android.com/reference/kotlin/androidx/activity/compose/package-summary>

## Testing navigation

- **"Test your Compose layout"** — `createComposeRule` / `createAndroidComposeRule`, finders, assertions, and `waitForIdle`:
  <https://developer.android.com/develop/ui/compose/testing>
- **Robolectric** — JVM-side Android tests, the fast way to test a back stack without an emulator:
  <https://robolectric.org/>
- **Compose testing cheat sheet** (a one-page finder/action/matcher reference):
  <https://developer.android.com/develop/ui/compose/testing/testing-cheatsheet>

## Community writing (current, opinionated, correct)

- **Now in Android (the app and the series).** The Google sample that demonstrates production navigation patterns; the navigation module is exemplary even as it tracks the API:
  <https://github.com/android/nowinandroid>
- **Chris Banes — Compose and navigation deep dives.** Among the clearest writing on Compose internals and adaptive navigation:
  <https://chrisbanes.me/>
- **Android Developers Medium — the Navigation 3 series.** The team's own articles tracking the API as it stabilises:
  <https://medium.com/androiddevelopers>
- **Zsmb (Márton Braun) — Kotlin and Compose articles.** Sharp, correct writing on Kotlin idioms that show up in route modelling:
  <https://zsmb.co/>

## Open-source projects to read this week

You learn more from one hour reading a real navigation graph than from three hours of tutorials. Pick one and trace how it declares routes and renders the stack:

- **`android/nowinandroid`** — Google's flagship modern-Android sample; read the `:feature-*` modules and how the app wires their navigation:
  <https://github.com/android/nowinandroid>
- **`android/nav3-recipes`** — Google's official Navigation 3 recipe repo: bottom bars, nested graphs, adaptive layouts, deep links, each as a small runnable sample:
  <https://github.com/android/nav3-recipes>
- **`androidx/androidx`** (the `navigation3` directory) — the library source itself; reading `NavDisplay` and the `entryProvider` builder demystifies the whole thing:
  <https://cs.android.com/androidx/platform/frameworks/support>

## Tools you'll use this week

- **Android Studio Ladybug+** — installed from the JetBrains/Google download page. `Help ▸ About` to confirm AGP and Kotlin versions.
- **`adb`** — the Android Debug Bridge. You will use `adb shell am start -W -a android.intent.action.VIEW -d "catalog://item/42" <pkg>` to fire a deep link, and `adb shell am start -W -d "https://catalog.crunch.dev/item/42"` to test an App Link.
- **Layout Inspector / Compose recomposition counts** — to confirm a tab switch does not re-create the whole tree.
- **The emulator's gesture-navigation setting** — turn on gesture navigation (Settings ▸ System ▸ Gestures) so the predictive-back animation is visible; on three-button navigation the gesture preview does not show.

## Free resources (chapter-level, not whole books)

- **Android's "Jetpack Compose" pathway and the Navigation codelabs** on `developer.android.com/courses` are effectively a free book; the navigation codelab tracks Nav3 as it stabilises.
- **The `nav3-recipes` README** doubles as a free, runnable cookbook for every pattern this week needs.

## Paid books (optional, clearly marked)

- **"Jetpack Compose Internals" — Jorge Castillo** (paid). Not navigation-specific, but the recomposition and state chapters explain *why* an app-owned back stack made of `SnapshotStateList` behaves the way it does.
- **"Programming Android with Kotlin" — O'Reilly** (paid). Broader Android; the navigation chapter is dated to the old model but the lifecycle and back-stack reasoning still holds.

---

*If a link 404s, please open an issue so we can replace it. Navigation 3 is a young, fast-moving library; if an API name has shifted since this was written, check the release notes linked above and send a PR.*
