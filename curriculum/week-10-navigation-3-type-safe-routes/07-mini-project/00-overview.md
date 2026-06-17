# Mini-Project — Catalog Companion: typed navigation, end to end

This week the app gets a skeleton. You will build **Catalog Companion**, a three-tab Compose application — Home, Catalog, Profile — wired entirely with Navigation 3. Every destination is a `@Serializable` type, every tab keeps its own back-stack history, an onboarding flow is a nested graph, deep links open inner screens from a `Uri`, predictive back animates every transition, and an end-to-end UI test drives the whole thing. There is not one string route in it.

This is a *foundational* project, not a throwaway. The graph you build here is the one Week 11 themes with Material 3 and Week 12 gives a real MVVM architecture. The screens are intentionally simple — an in-memory catalog, a static profile — because the point of the week is the *wiring between screens*, not what flows through them. Get the graph right and the next two weeks are skin and architecture on a solid skeleton.

---

## Where you're starting from

You have, from earlier weeks, as standalone screens:

- **Compose Pomodoro** (Week 7) — a self-contained timer composable.
- A **search-as-you-type** screen (Week 8) with debounced input that survives rotation.
- Comfort with `remember`/`rememberSaveable`, state hoisting, and `collectAsStateWithLifecycle` (Weeks 8–9).

You do not need to reuse those verbatim; Catalog Companion has its own simple screens. But the *instinct* they built — hoist state, keep effects in effects, let the parent own navigation — is exactly what makes this week's wiring clean.

## What you're building toward

By the end you have:

- A **`sealed interface Route : NavKey`** graph: `data object` roots for each tab, `data class` routes for screens that carry an id, a nested `Onboarding` sub-family.
- A **bottom bar** with three tabs, each holding its **own back stack** so tab history is independent and preserved across switches.
- A **nested onboarding flow** (welcome → permissions → done) that pops as a unit when finished.
- **Deep links** — a custom scheme (`catalog://item/42`) and an `https://` App Link — mapped through a pure `routeForUri` and seeded into a sensible back stack.
- **Predictive back** wired with `transitionSpec`/`popTransitionSpec`, verified on a gesture-navigation emulator.
- A **ViewModel scoped to a back-stack entry** for the detail screen, seeded by the route's id.
- **End-to-end Compose UI tests** that drive every transition and a deep link.

---

## Milestone 1 — Model the graph as types (≈ 1.5 h)

Define the whole navigation graph as a sealed family of `@Serializable` route types. This is the data model the rest of the app renders.

```kotlin
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Route : NavKey

// Tab roots — no arguments, so data object.
@Serializable data object HomeRoot : Route
@Serializable data object CatalogRoot : Route
@Serializable data object ProfileRoot : Route

// Screens that carry identity — data class, the argument is a property.
@Serializable data class ItemDetail(val itemId: Int) : Route
@Serializable data class UserProfile(val userId: String) : Route

// The onboarding nested graph — its own sealed sub-family so "exit the flow"
// is a one-line removeAll { it is Onboarding }.
sealed interface Onboarding : Route
@Serializable data object Welcome : Onboarding
@Serializable data object Permissions : Onboarding
@Serializable data object OnboardingDone : Onboarding
```

Decisions you must be able to defend in review:

- **Why `data object` for roots and `data class` for `ItemDetail`?** Roots have no identity beyond "the catalog root"; a detail's identity *is* its `itemId`. `ItemDetail(1)` and `ItemDetail(2)` must be distinct back-stack entries, which `data class` equality gives you for free.
- **Why is `Onboarding` a sub-sealed-interface and not just three loose routes?** So `removeAll { it is Onboarding }` pops the entire flow exhaustively, with the compiler guaranteeing the type test covers every onboarding screen. A nested graph is a sub-family.
- **Why nothing larger than an id/string in any route?** The back stack is serialized into saved instance state for process-death survival. Big or non-`@Serializable` properties break or bloat that. The route carries identity; the ViewModel carries data (Milestone 5).

## Milestone 2 — The tabbed shell with per-tab back stacks (≈ 2 h)

Build the `Scaffold` + `NavigationBar` shell, holding one back stack per tab and rendering the active one.

```kotlin
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider

enum class Tab(val label: String) { Home, Catalog, Profile }

@Composable
fun CatalogCompanionApp(deepLink: NavKey? = null) {
    var selectedTab by rememberSaveable { mutableStateOf(Tab.Home) }

    val homeStack = rememberNavBackStack(HomeRoot)
    val catalogStack = rememberNavBackStack(CatalogRoot)
    val profileStack = rememberNavBackStack(ProfileRoot)

    val activeStack = when (selectedTab) {
        Tab.Home -> homeStack
        Tab.Catalog -> catalogStack
        Tab.Profile -> profileStack
    }

    // A deep link seeds the catalog stack and switches to its tab (Milestone 4).
    LaunchedEffect(deepLink) {
        when (deepLink) {
            is ItemDetail -> { selectedTab = Tab.Catalog; if (catalogStack.lastOrNull() != deepLink) catalogStack.add(deepLink) }
            is UserProfile -> { selectedTab = Tab.Profile; if (profileStack.lastOrNull() != deepLink) profileStack.add(deepLink) }
            else -> Unit
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(Icons.Default.Star, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavDisplay(
            modifier = Modifier.padding(padding),
            backStack = activeStack,
            onBack = {
                if (activeStack.size > 1) activeStack.removeLastOrNull()
                else if (selectedTab != Tab.Home) selectedTab = Tab.Home
                // at Home root: don't consume back -> system exits the app
            },
            transitionSpec = { slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 } },
            popTransitionSpec = { slideInHorizontally { -it / 4 } togetherWith slideOutHorizontally { it } },
            entryProvider = entryProvider {
                entry<HomeRoot> { HomeScreen(onStartOnboarding = { homeStack.add(Welcome) }) }
                entry<CatalogRoot> { CatalogScreen(onItem = { id -> catalogStack.add(ItemDetail(id)) }) }
                entry<ItemDetail> { route -> DetailScreen(itemId = route.itemId, onBack = { catalogStack.removeLastOrNull() }) }
                entry<ProfileRoot> { ProfileScreen(onUser = { id -> profileStack.add(UserProfile(id)) }) }
                entry<UserProfile> { route -> UserProfileScreen(userId = route.userId) }
                // Onboarding entries — Milestone 3.
                entry<Welcome> { WelcomeScreen(onNext = { homeStack.add(Permissions) }) }
                entry<Permissions> { PermissionsScreen(onNext = { homeStack.add(OnboardingDone) }) }
                entry<OnboardingDone> { DoneScreen(onFinish = { homeStack.removeAll { it is Onboarding } }) }
            }
        )
    }
}
```

Decisions to defend:

- **Why one back stack per tab?** Independent history. Drill into a catalog item, switch to Profile, come back to Catalog — you are still on the item. A single shared stack breaks this and users notice.
- **Why is switching tabs `selectedTab = tab` and not a navigation?** Tabs are not on a back stack; you are choosing which stack `NavDisplay` renders. Each tab's history sits preserved while you are away from it.
- **Why the guarded `onBack`?** You own back behaviour. Within a tab, back pops; at a non-Home tab root, back returns to Home; at the Home root, back exits the app. Each branch is a deliberate decision, not a default.

## Milestone 3 — The nested onboarding graph (≈ 1.5 h)

Onboarding is a flow-within-the-Home-tab: welcome → permissions → done, entered from Home and popped as a unit when finished.

```kotlin
@Composable fun WelcomeScreen(onNext: () -> Unit) { /* … Button("Next", onNext) … */ }
@Composable fun PermissionsScreen(onNext: () -> Unit) { /* … Button("Grant", onNext) … */ }
@Composable fun DoneScreen(onFinish: () -> Unit) { /* … Button("Finish", onFinish) … */ }
```

The whole flow lives in `homeStack` as a contiguous run of `Onboarding` entries. Finishing it is one line — `homeStack.removeAll { it is Onboarding }` — which pops every onboarding screen regardless of how far the user got, exhaustively, because `Onboarding` is sealed. Back *within* onboarding (welcome ← permissions ← done) works for free via `NavDisplay`'s `onBack`. The exhaustiveness is the payoff: the day someone adds a fourth onboarding screen, `it is Onboarding` already covers it.

Decision to defend: **why pop the whole flow on finish instead of leaving it in history?** Because back from the post-onboarding screen should not walk the user *back into* the flow they just completed. Popping the run means onboarding happens once and disappears from history.

## Milestone 4 — Deep links (≈ 1.5 h)

Wire deep links: a custom scheme and an App Link, mapped through a pure function and seeded into the stack.

```kotlin
fun routeForUri(uri: Uri): NavKey? {
    val kind = when (uri.scheme) {
        "catalog" -> uri.host
        "https" -> uri.pathSegments.firstOrNull()
        else -> return null
    }
    return when (kind) {
        "item" -> uri.lastPathSegment?.toIntOrNull()?.let { ItemDetail(it) }
        "profile" -> uri.lastPathSegment?.takeIf { it.isNotBlank() }?.let { UserProfile(it) }
        else -> null
    }
}
```

Manifest `<intent-filter>`s for `catalog://item` and `https://catalog.crunch.dev` (see lecture 2, §3). Read the launching intent in `MainActivity`, map it, pass it as `deepLink` into the app (Milestone 2's `LaunchedEffect` seeds the right tab's stack). Test from the command line:

```bash
adb shell am start -W -a android.intent.action.VIEW -d "catalog://item/42" dev.crunch.catalog
adb shell am start -W -a android.intent.action.VIEW -d "https://catalog.crunch.dev/item/42" dev.crunch.catalog
```

Decision to defend: **why seed `[CatalogRoot, ItemDetail(42)]` and switch to the Catalog tab, rather than just show the detail?** So back from the deep-linked detail lands on the catalog root in the right tab — the history a user who tapped a link expects — instead of exiting the app from a blank stack.

## Milestone 5 — A ViewModel scoped to the detail entry (≈ 1 h)

Give the detail screen a `ViewModel` scoped to its back-stack entry, seeded by the route's id. Install the ViewModel entry decorator on `NavDisplay`:

```kotlin
class DetailViewModel(private val itemId: Int) : ViewModel() {
    val item: StateFlow<CatalogItem?> = MutableStateFlow(InMemoryCatalog.find(itemId)).asStateFlow()
}

entry<ItemDetail> { route ->
    val vm: DetailViewModel = viewModel { DetailViewModel(route.itemId) }
    val item by vm.item.collectAsStateWithLifecycle()
    DetailScreen(item = item, onBack = { catalogStack.removeLastOrNull() })
}
```

The ViewModel is created when you navigate into the detail, survives rotation, and is cleared when you pop the entry. The id rides in the route; the data lives in the ViewModel. This seam is exactly where Week 12's MVVM-with-UDF architecture plugs in.

## Milestone 6 — End-to-end UI tests (≈ 1 h)

Prove every transition with a Compose UI test, plus JVM tests for the back-stack logic and the deep-link parser (exercises 02 and 03 are the templates).

```kotlin
@Test
fun catalogDrillDown_back_keepsTabHistory() {
    rule.setContent { CatalogCompanionApp() }
    rule.onNodeWithText("Catalog").performClick(); rule.waitForIdle()
    rule.onNodeWithText("Item 42").performClick(); rule.waitForIdle()
    rule.onNodeWithText("Detail: 42").assertIsDisplayed()

    // Switch away and back — the drill-down is preserved.
    rule.onNodeWithText("Profile").performClick(); rule.waitForIdle()
    rule.onNodeWithText("Catalog").performClick(); rule.waitForIdle()
    rule.onNodeWithText("Detail: 42").assertIsDisplayed()   // still on the item
}
```

---

## Acceptance criteria

- [ ] Every destination is a `@Serializable` type implementing `sealed interface Route : NavKey`. **No string routes; no `Bundle` argument reads.** (`grep -rn 'navigate("' app/src` and `grep -rn 'arguments?.get' app/src` return nothing.)
- [ ] Three tabs, each with its **own back stack**; tab history is independent and preserved across switches.
- [ ] A nested **onboarding** sub-family that pops as a unit via `removeAll { it is Onboarding }`.
- [ ] Deep links via a pure `routeForUri` for both `catalog://item/42` and `https://…/item/42`, seeding a back stack so back lands on a root; verified with `adb shell am start`.
- [ ] **Predictive back** wired (`enableOnBackInvokedCallback`, `transitionSpec`/`popTransitionSpec`, `NavDisplay` owns the pop), seen on a gesture-navigation emulator.
- [ ] The detail screen uses a **ViewModel scoped to its entry**, seeded by `route.itemId`.
- [ ] End-to-end Compose UI tests cover tab switching, drill-down + back, preserved tab history, and a deep link; JVM tests cover the back-stack logic and `routeForUri` (incl. bad input).
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **Adaptive list-detail.** On a wide window (foldable/tablet), show catalog list and detail side by side using `NavDisplay`'s adaptive scene strategy; on a phone, keep the drill-down. (Form factors are Phase 4 — this is a taste.)
- **Deep link into onboarding.** `catalog://onboarding` seeds the Home tab into `Welcome`; confirm finishing it pops cleanly.
- **Back-stack logging decorator.** Add an `entryDecorator` (or a `LaunchedEffect` on the stack) that logs the back stack on every change; watch it during a deep link and a tab switch to *see* the history you built.
- **Restore across process death.** Enable "Don't keep activities" in developer options, background and return, and confirm the back stack — current tab, drill-down, everything — restores. That's the `@Serializable` routes earning their annotation.

## What this milestone earns you

You can now model a navigation graph as a data model and navigate it without a single string — the literal "skill earned" line for the week. More than that: you built the skeleton the next two weeks dress and architect. The clean tabbed-plus-nested-plus-deep-linked graph was *earned* by modelling routes as types up front; the deep links and predictive back are easy precisely because the back stack is yours. Week 11 makes it look shipped (Material 3, dynamic color, edge-to-edge); Week 12 makes it *architected* (MVVM, UDF, the Now-In-Android shape) — and the ViewModel-scoped-to-an-entry seam you built in Milestone 5 is exactly where that architecture lands.
