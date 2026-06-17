# Week 13 — Resources

Every primary resource on this page is **free**. The Android developer documentation is free. Dagger's site (`dagger.dev`) is open documentation. The Now-In-Android sample is open source on GitHub under Apache-2.0. A handful of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"Dependency injection with Hilt"** — Android's canonical Hilt guide. Read this before you write `@HiltAndroidApp`:
  <https://developer.android.com/training/dependency-injection/hilt-android>
- **"Hilt and Dagger annotations cheat sheet"** — the single most useful one-page reference for which annotation does what; keep it open all week:
  <https://developer.android.com/training/dependency-injection/hilt-cheatsheet>
- **"Hilt — component hierarchy and lifetimes"** — the component tree and the scope-to-component pairing table; central to lecture 1, §5:
  <https://developer.android.com/training/dependency-injection/hilt-android#component-lifetimes>
- **"Hilt in multi-module apps"** — where bindings live in a layered project; central to the mini-project:
  <https://developer.android.com/training/dependency-injection/hilt-multi-module>
- **"Manual dependency injection"** — the `ServiceLocator`/by-hand pattern Hilt replaces; read it so you understand what the framework buys you (and feel the boilerplate in the challenge):
  <https://developer.android.com/training/dependency-injection/manual>

## Dagger itself (the layer beneath)

Hilt is Dagger underneath. When Hilt errors read like Dagger errors, these pages explain them.

- **Dagger user's guide:** <https://dagger.dev/dev-guide/>
- **`@Component`, `@Subcomponent`, and the component graph:** <https://dagger.dev/dev-guide/subcomponents>
- **`@Module` / `@Provides` / `@Binds`:** <https://dagger.dev/dev-guide/>
- **Scopes in Dagger** (what `@Singleton` actually means to the processor): <https://dagger.dev/dev-guide/#singletons-and-scoped-bindings>
- **Assisted injection:** <https://dagger.dev/dev-guide/assisted-injection>
- **Multibindings** (`@IntoSet`, `@IntoMap` — useful when a feature contributes initializers): <https://dagger.dev/dev-guide/multibindings>

## The annotations (reference, skim don't memorize)

- **`@HiltAndroidApp`:** <https://dagger.dev/api/latest/dagger/hilt/android/HiltAndroidApp.html>
- **`@AndroidEntryPoint`:** <https://dagger.dev/api/latest/dagger/hilt/android/AndroidEntryPoint.html>
- **`@HiltViewModel`:** <https://dagger.dev/api/latest/dagger/hilt/android/lifecycle/HiltViewModel.html>
- **`@InstallIn` and the predefined components:** <https://dagger.dev/api/latest/dagger/hilt/InstallIn.html>
- **`@Module`:** <https://dagger.dev/api/latest/dagger/Module.html>
- **`@Provides` / `@Binds`:** <https://dagger.dev/api/latest/dagger/Provides.html> and <https://dagger.dev/api/latest/dagger/Binds.html>
- **`@Qualifier` and `@Named`:** <https://docs.oracle.com/javaee/7/api/javax/inject/Qualifier.html>
- **`@AssistedInject` / `@Assisted` / `@AssistedFactory`:** <https://dagger.dev/api/latest/dagger/assisted/AssistedInject.html>
- **`@EntryPoint` and `EntryPointAccessors`:** <https://dagger.dev/api/latest/dagger/hilt/EntryPoint.html>

## Build setup

- **Hilt Gradle setup (KSP):** <https://developer.android.com/training/dependency-injection/hilt-android#setup>
- **KSP (Kotlin Symbol Processing) — the 2026 annotation-processing backend:** <https://kotlinlang.org/docs/ksp-overview.html>
- **`hilt-navigation-compose`** (injecting a `@HiltViewModel` into a Compose destination): <https://developer.android.com/jetpack/androidx/releases/hilt>

## Source to read this week (this is the assignment that teaches the most)

You learn more from one hour reading a production multi-module Hilt graph than from three hours of tutorials. Read **Now-In-Android** — Google's reference app — specifically how it lays out DI:

- **`android/nowinandroid`** — the canonical modern Android sample; read `core/network/.../di/`, `core/data/.../di/`, and how feature modules consume them:
  <https://github.com/android/nowinandroid>
- **Now-In-Android architecture guide** — the data/domain/UI layering the DI graph follows:
  <https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md>
- **`android/architecture-samples`** — smaller, single-module-ish Hilt examples if Now-In-Android is too much at once:
  <https://github.com/android/architecture-samples>

## Reading the generated code

The skill that turns Hilt from magic into a tool is reading what KSP/Dagger generated.

- After a build, the generated sources live under `app/build/generated/ksp/debug/kotlin/` (KSP) or `.../kapt/` (kapt). Open `Hilt_<YourActivity>.java`, `<YourClass>_Factory.java`, and the `Dagger*_HiltComponents` aggregation.
- **"Hilt under the hood"** (the Android team's explainer talks; search the Android Developers YouTube channel for "Hilt under the hood") — walks the generated component tree.
- **`./gradlew :app:dependencies`** and **`:app:kspDebugKotlin --info`** — see exactly which processor ran and what it produced.

## Community writing (current, opinionated, correct)

- **Manuel Vivo — "Hilt" article series.** Manuel was on the Android DI team; his writing on scopes and the component hierarchy is the clearest outside the docs:
  <https://medium.com/@marxallski> and the Android Developers Medium publication: <https://medium.com/androiddevelopers>
- **Zac Sweers — "An introduction to Dagger 2" / Metro** (the kotlin-inject-adjacent compile-time DI work). Reading a Dagger alternative sharpens your sense of what Hilt's opinions cost and buy:
  <https://www.zacsweers.dev/>
- **Chris Banes' blog** — practical multi-module and Compose-DI notes:
  <https://chrisbanes.me/>

## Tools you'll use this week

- **Android Studio's "Gradle" tool window** — run `kspDebugKotlin` directly and watch the processor output.
- **The build error pane** — `MissingBinding`, `DuplicateBindings`, and scope-mismatch errors are your primary teacher this week. Read them as sentences.
- **`./gradlew :app:assembleDebug --rerun-tasks`** — force a clean annotation-processing pass when you want to re-read the generated graph.

## Free books (chapter-level)

- **Android's "Guide to app architecture"** (the data/domain/UI layering) is effectively a free book and the backbone of the multi-module graph you build:
  <https://developer.android.com/topic/architecture>

## Paid books (optional, clearly marked)

- **"Dependency Injection in Android with Dagger 2 and Hilt"** — various authors (paid). Useful if you want a single linear narrative; the docs above cover everything for free.
- **"Hands-On Android" / "Programming Android with Kotlin"** — O'Reilly (paid). General Android with solid DI chapters; not required.

---

*If a link 404s, please open an issue so we can replace it.*
