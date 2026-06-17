# Week 10 — Navigation 3 with type-safe routes

Welcome to Week 10 of **C21 · Crunch Droid**. For three weeks of Phase 2 your screens have been composables that you call directly — `HomeScreen()`, `DetailScreen(id)` — wired together by hand or stacked in a `when` on some `@Composable`-level state. That works for one screen and falls apart at three. This week you learn the modern Android answer for moving between screens: **Navigation 3**, the type-safe navigation library that replaced string routes with serializable Kotlin objects. By Friday your app has a Home / Catalog / Profile bottom bar, nested graphs, deep links that open the right screen from a URL, and predictive back working everywhere — and not one screen is addressed by a hand-spelled string.

Navigation 3 (often written "Nav3") is Google's 2024–2025 redesign of Jetpack Navigation, and in 2026 it is the default answer for navigation in a new Compose app. The headline change is the one this week hammers on: **a route is a type, not a string.** In the old Navigation-Compose world a destination was a string like `"detail/{itemId}"` and you reached it with `navController.navigate("detail/$itemId")` — a string-interpolation call the compiler could not check, with arguments pulled back out of a `Bundle` by string key and cast by hand. Every one of those steps was a runtime crash waiting for a typo. Navigation 3 makes each destination a `@Serializable` Kotlin type — a `data class` or `data object` — and you navigate by adding that *instance* to a back stack you own. The arguments are the type's properties. The compiler checks them. The "navigated to a route that doesn't exist" and "read argument under the wrong key" bug classes simply stop existing.

The mental shift this week is from "the NavController owns a graph and I ask it to go places by name" to "**I own a back stack of typed keys, and Navigation 3 renders the top one.**" In Nav3 the back stack is your state — a `SnapshotStateList` of route objects that you hold, mutate, and can `rememberSaveable`. You do not ask an opaque controller to push a string; you `backStack.add(ItemDetail(id = 42))` and the library finds the entry that knows how to render an `ItemDetail` and shows it. That inversion — the app owns the stack, the library renders it — is what makes deep links, conditional flows, and multi-pane adaptive layouts tractable instead of a fight with a framework that thinks it owns your history.

We close the week by building **Catalog Companion**, a three-tab app — Home, Catalog, Profile — with a typed back stack per tab, a nested graph for an onboarding flow, `https://` and custom-scheme deep links that resolve to typed routes, predictive back fully wired so the Android 14+ back gesture shows the live cross-screen animation, and end-to-end Compose UI tests that drive every transition. You will also migrate a small string-route graph to Nav3 on Tuesday and delete every `navController.navigate("…")` and every `arguments?.getString(…)` on the way — feeling, line by line, the bug class you are retiring.

## Learning objectives

By the end of this week, you will be able to:

- **Explain** what Navigation 3 changed and why — that a route is now a `@Serializable` type, that the app owns the back stack as Compose state, and that `NavDisplay` renders the top entry — and predict which old-Navigation bugs (bad route string, wrong argument key, lost type) the redesign eliminates.
- **Model** a navigation graph as a sealed hierarchy of `@Serializable` route types (`data object` for no-argument screens, `data class` for screens that carry arguments), and justify why the sealed root makes the graph exhaustive and reviewable.
- **Drive** navigation by mutating a back stack you own: `rememberNavBackStack`, `backStack.add(route)`, `backStack.removeLastOrNull()`, and reading the current top — and explain why this is `SnapshotStateList`-backed Compose state, not a hidden controller.
- **Render** a back stack with `NavDisplay` and an `entryProvider`, mapping each route type to its composable with `entry<T> { … }`, and pass typed arguments straight out of the route object with no `Bundle` round-trip.
- **Compose** nested graphs and per-tab back stacks for bottom-bar navigation, keeping each tab's history independent and surviving configuration change with `rememberSaveable`.
- **Wire** deep links — both `https://` App Links and custom-scheme links — that resolve an incoming `Uri` to a typed route and seed the back stack, without manifest `<deepLink>` string spelunking.
- **Enable** predictive back so the Android 14+ system back gesture animates the cross-screen transition live, and verify it with `NavDisplay`'s back handling and the right transition specs.
- **Test** every transition end-to-end with `createAndroidComposeRule`, asserting on screen content after each typed navigation and after a simulated deep link, and recognise the flakiness traps (no `waitForIdle`, asserting before recomposition settles).

## Prerequisites

This week assumes you have completed **C21 weeks 1–9**, or have equivalent fluency. Specifically:

- You can read and write idiomatic Kotlin 2.x — `data class`, `sealed interface`, `object`/`data object`, generics, lambdas with receivers — Weeks 1–3. The `sealed interface` + `data object`/`data class` shape is load-bearing this week: a Nav3 route graph *is* a sealed hierarchy, and exhaustiveness is what makes it safe.
- You understand `kotlinx.serialization` and the `@Serializable` annotation — touched in Weeks 2 and 5. Nav3 routes are `@Serializable` so they can be saved and restored across process death; you do not need to write a custom serializer, but you must know why the annotation is there.
- You can write a composable, hoist state, and reason about recomposition and `remember` / `rememberSaveable` — Weeks 7–9. The back stack is `rememberSaveable` Compose state; if state ownership is fuzzy, navigation state will be too.
- You have **Compose Pomodoro** (Week 7) and the **search-as-you-type** screen (Week 8) in Git as standalone screens. This week stitches that kind of screen into a real multi-screen app; the screens barely change, the wiring between them is the whole point.

**Toolchain.** Android Studio Ladybug (2024.2) or newer with AGP 8.5+, Kotlin 2.0+ with the Compose Compiler Gradle plugin, `compileSdk 35` targeting Android 15, `minSdk 24`. Navigation 3 artifacts: `androidx.navigation3:navigation3-runtime` and `navigation3-ui`, plus `androidx.lifecycle:lifecycle-viewmodel-navigation3` for ViewModel-scoped entries, and `org.jetbrains.kotlinx:kotlinx-serialization-json` with the `kotlin("plugin.serialization")` Gradle plugin. Predictive back is on by default in your app's manifest (`android:enableOnBackInvokedCallback="true"`). Everything runs in the emulator — no physical device required, though predictive back feels best on a device with gesture navigation enabled.

## Topics covered

- **What Navigation 3 is, and what it replaced.** The string-route Navigation-Compose model (`NavHost`, `composable("route")`, `navArgument`) as historical context; the Nav3 model (app-owned back stack, `NavDisplay`, `entryProvider`); why the rewrite happened and what it buys you.
- **Routes as types.** `@Serializable data object` for argument-free screens, `@Serializable data class` for screens with arguments, a `sealed interface` root for the whole graph; why serializable, and how that powers save/restore across process death.
- **The back stack you own.** `rememberNavBackStack` / a `SnapshotStateList<NavKey>`; `add`, `removeLastOrNull`, `removeAll`, reading the top; the back stack as ordinary Compose state you can inspect, log, and test.
- **`NavDisplay` and `entryProvider`.** Rendering the top of the stack; `entry<Home> { … }`, `entry<ItemDetail> { detail -> … }`; pulling arguments straight off the typed route; `entryDecorators` for cross-cutting concerns (ViewModel scoping, saved state).
- **Arguments without `Bundle`s.** Passing data as route-type properties; why there is no `getString("id")`; the difference between an argument (part of identity, in the route) and shared state (in a ViewModel or repository).
- **Nested graphs and bottom-bar navigation.** A back stack per tab; switching tabs without losing each tab's history; a top-level `Scaffold` with a `NavigationBar`; nested onboarding flow as its own sub-stack.
- **Deep links.** Manifest `<intent-filter>` for `https://` App Links and a custom scheme; mapping an incoming `Uri` to a typed route; seeding the back stack so back from a deep-linked screen lands somewhere sensible; verifying with `adb shell am start`.
- **Predictive back.** The Android 13/14 predictive back gesture; how `NavDisplay` participates; transition specs (`NavDisplay`'s `transitionSpec`/`popTransitionSpec`) so the gesture animates the outgoing and incoming screens together; testing it.
- **ViewModel scoping in Nav3.** `lifecycle-viewmodel-navigation3`: a ViewModel scoped to a back-stack entry that survives recomposition and is cleared when the entry is popped; why this matters for the MVVM week that follows.
- **Testing navigation.** `createAndroidComposeRule`, asserting screen content after each typed navigation, driving a deep link in test, `waitForIdle`, and the flakiness traps.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                            | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | Why Nav3; routes as `@Serializable` types; the app-owned back stack |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | `NavDisplay` + `entryProvider`; migrate a string graph to Nav3   |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Nested graphs; per-tab back stacks; bottom-bar navigation        |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | Deep links; predictive back; navigation testing; challenge       |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — Catalog Companion: three tabs, nested graph        |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work; deep links + predictive back + UI tests   |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                       |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                  | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./00-overview.md) | This overview (you are here) |
| [resources.md](./01-resources.md) | The Navigation 3 docs and release notes, the AndroidX `navigation3` source, the deep-link and predictive-back guides, and the canonical samples |
| [lecture-notes/01-navigation-3-routes-as-types.md](./02-lecture-notes/01-navigation-3-routes-as-types.md) | Nav3 end to end: what it replaced, routes as `@Serializable` types, the app-owned back stack, `NavDisplay`/`entryProvider`, typed arguments, and ViewModel scoping |
| [lecture-notes/02-nested-graphs-deep-links-predictive-back.md](./02-lecture-notes/02-nested-graphs-deep-links-predictive-back.md) | Per-tab back stacks and bottom-bar navigation, nested graphs, deep links from `Uri` to typed route, predictive back with transition specs, and testing every transition |
| [exercises/README.md](./03-exercises/00-overview.md) | Index of the three exercises |
| [exercises/exercise-01-string-routes-to-typed-routes.md](./03-exercises/exercise-01-string-routes-to-typed-routes.md) | Migrate a string-route graph to Nav3: define `@Serializable` routes, render with `NavDisplay`, delete every route string and `Bundle` read |
| [exercises/exercise-02-back-stack-and-entry-provider.kt](./03-exercises/exercise-02-back-stack-and-entry-provider.kt) | Drive an app-owned back stack and an `entryProvider` in a Robolectric/Compose test; assert the rendered screen after each typed navigation |
| [exercises/exercise-03-deep-link-to-typed-route.kt](./03-exercises/exercise-03-deep-link-to-typed-route.kt) | Resolve an incoming `Uri` to a typed route and seed the back stack; test that a deep link lands on the right screen with the right arguments |
| [challenges/README.md](./04-challenges/00-overview.md) | Index of the challenge |
| [challenges/challenge-01-string-route-footgun-then-typed.md](./04-challenges/challenge-01-string-route-footgun-then-typed.md) | Plant a string-route argument bug that crashes at runtime, then refactor to typed routes so the compiler catches it — document the before/after |
| [quiz.md](./05-quiz.md) | 13 questions on routes-as-types, the back stack, `NavDisplay`, nested graphs, deep links, and predictive back |
| [homework.md](./06-homework.md) | Six practice problems for the week |
| [mini-project/README.md](./07-mini-project/00-overview.md) | Full spec for "Catalog Companion": three tabs, nested graph, deep links, predictive back, end-to-end UI tests |

## The "no string routes" promise

Week 7 gave you "renders exactly once." Week 8 gave you "survives rotation." Week 10 adds the navigation contract a senior reviewer actually checks:

> **No destination is addressed by a hand-spelled string, and no argument is read out of a `Bundle` by key.** Every screen you can navigate to is a `@Serializable` type; every argument is a property of that type, checked by the compiler. If a reviewer can find a `navigate("…")` call or an `arguments?.getString("…")` read, the navigation layer is not done — no matter how clean the screens look.

You will *prove* this by grepping your own mini-project for the string-route patterns and finding none, and by deleting an argument from a route type and watching the compiler — not a user's crash report — tell you every call site that needs updating. "It worked when I typed the route right" is not the test; the test is that you *cannot* type the route wrong.

## A note on what's not here

Week 10 is the *navigation* week. It deliberately does **not** cover:

- **Material 3 theming.** Catalog Companion uses default Material 3 components so the focus stays on the graph, not the palette. Dynamic color, edge-to-edge insets, and a tuned `ColorScheme` are next week (Week 11). We use a `NavigationBar` and a `Scaffold` plain this week and theme them properly later.
- **ViewModel architecture.** We *scope* a ViewModel to a back-stack entry (because Nav3 makes that clean) but we do not yet draw the data/domain/UI layer boundary or model `UiState` as a sealed type — that is Week 12's MVVM-with-UDF and the Now-In-Android pattern. This week the ViewModel is a thin holder; next-but-one week it earns its architecture.
- **Real data and networking.** Catalog Companion's catalog is an in-memory list. Wiring a repository, Room, and a network layer behind it is Phase 3. This week the point is the *graph*, not what flows through it.

The point of Week 10 is narrow and deep: one navigation graph modelled as types, the back stack you own, the display that renders it, the deep links that seed it, and the predictive-back gesture that makes leaving a screen feel native.

## Up next

Continue to **Week 11 — Material 3, Material You, dynamic color, edge-to-edge** once you have shipped Catalog Companion and proven every transition with a UI test. Week 11 takes the multi-screen app you just wired and makes it *look* shipped: a real Material 3 `ColorScheme`, dynamic color from the user's wallpaper on Android 12+, a hand-tuned fallback palette below that, edge-to-edge layout with proper window insets, and an audited dark theme. The navigation you built this week is the skeleton; next week is the skin. Then Week 12 puts a real MVVM-with-UDF architecture around it — and the ViewModel-scoped-to-a-back-stack-entry pattern you met this week is exactly where that architecture plugs in. Every one of those weeks assumes you can model a graph as types and navigate it without strings. Earn that this week.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
