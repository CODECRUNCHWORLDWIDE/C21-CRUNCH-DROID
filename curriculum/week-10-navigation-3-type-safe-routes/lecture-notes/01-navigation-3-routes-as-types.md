# Lecture 1 — Navigation 3: routes as types, and the back stack you own

> "Navigation 3 didn't add a feature. It moved a boundary: the back stack stopped belonging to the framework and started belonging to your app."

This is the lecture that decides whether Navigation 3 feels like a new gadget you have to learn or like the obvious shape navigation should always have had. The framing for the whole week is one sentence: **in Navigation 3, a route is a `@Serializable` Kotlin type, and the app owns the back stack as ordinary Compose state.** Hold that, and every surprise this week — why there is no `getString("id")`, why deep links are easy, why a tab switch keeps its history, why predictive back works — has a one-idea explanation. Lose it, and you are cargo-culting `entry<…>` blocks and hoping.

We will build the mental model bottom-up: the old model and the bug class it shipped, then routes as types, then the back stack you own, then the display that renders it, then typed arguments, then ViewModel scoping. By the end you should be able to draw the data flow on a whiteboard — *back stack (your state) → `NavDisplay` → `entryProvider` → the top entry's composable* — and point to which piece is responsible for any given behaviour.

---

## 1. The model we are leaving, and the bug class it shipped

For years, Compose navigation looked like this:

```kotlin
// THE OLD MODEL — Navigation-Compose with string routes. Do not write new code like this.
NavHost(navController, startDestination = "home") {
    composable("home") { HomeScreen(onItem = { id -> navController.navigate("detail/$id") }) }
    composable(
        route = "detail/{itemId}",
        arguments = listOf(navArgument("itemId") { type = NavType.IntType })
    ) { entry ->
        val itemId = entry.arguments?.getInt("itemId") ?: 0   // <- read back by string key, cast by hand
        DetailScreen(itemId)
    }
}
```

Look at every place a typo turns into a runtime crash:

- `navController.navigate("detail/$id")` — the string `"detail/…"` is not checked against the declared routes. Misspell it (`"detial/…"`), or change the route's spelling in one place and not the other, and you get a runtime "navigation destination not found" exception in front of a user.
- `"detail/{itemId}"` declares an argument named `itemId` as a *string* in a path template. The name `itemId` is spelled three times — in the route template, in `navArgument("itemId")`, and in `getInt("itemId")` — and nothing ties them together. Misspell one and the argument silently arrives null/default.
- `entry.arguments?.getInt("itemId")` — the argument is read out of an Android `Bundle` by string key and cast to `Int` by hand, with a `?: 0` fallback papering over the case where it isn't there. The compiler has no idea this `Int` is supposed to relate to the `Int` you passed.

This is a whole bug class: **stringly-typed routing.** It is the navigation equivalent of `dict["naem"]` returning `None` — the kind of bug that compiles fine, demos fine, and crashes the day someone refactors a route name or fat-fingers an argument key. Android shipped a half-step fix (type-safe routes *on the old `NavHost`*, with `@Serializable` route classes and `composable<Detail>`), and that was a real improvement — read it on the resources page. Navigation 3 finishes the job by also handing you the back stack.

---

## 2. What Navigation 3 actually is

Navigation 3 is built around a single inversion. In the old model, an opaque `NavController` owned a graph and a history, and you asked it to do things by name. In Nav3:

1. **You hold the back stack.** It is a `SnapshotStateList<NavKey>` — a regular, observable Compose list of route objects. You add to it to navigate forward; you remove from it to go back. You can log it, inspect it, save it, and test it, because it is your data.
2. **A route is a type.** Every destination is a `@Serializable` Kotlin type that implements the `NavKey` marker interface. No-argument screens are `data object`s; screens that carry data are `data class`es whose properties *are* the arguments.
3. **`NavDisplay` renders the top of the stack.** You give it your back stack and an `entryProvider` that maps each route *type* to a composable. It shows the composable for whatever is on top, and animates transitions when the top changes.

Here is the smallest complete Nav3 app — internalise this shape, because every exercise and the mini-project are elaborations of exactly it:

```kotlin
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

// 1. The graph, as types. A sealed interface makes it exhaustive and reviewable.
sealed interface Route : NavKey

@Serializable data object Home : Route
@Serializable data class ItemDetail(val itemId: Int) : Route

// 2. The app owns the back stack and renders it.
@Composable
fun CatalogApp() {
    val backStack = rememberNavBackStack(Home)   // starts with Home on the stack

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },   // back = pop the top
        entryProvider = entryProvider {
            entry<Home> {
                HomeScreen(onItem = { id -> backStack.add(ItemDetail(itemId = id)) })
            }
            entry<ItemDetail> { route ->
                DetailScreen(itemId = route.itemId)   // argument is just a property
            }
        }
    )
}
```

Read that against the old model and the wins line up one-for-one:

| Old model (string routes) | Navigation 3 (typed routes) |
|---|---|
| `navigate("detail/$id")` — unchecked string | `backStack.add(ItemDetail(id))` — typed, compiler-checked |
| `"detail/{itemId}"` template with a named arg | `data class ItemDetail(val itemId: Int)` — the arg is a property |
| `entry.arguments?.getInt("itemId")` — `Bundle` read + cast | `route.itemId` — a typed property access |
| `NavController` owns the history | `backStack` is your `SnapshotStateList` |
| "destination not found" crashes at runtime | a wrong route type is a *compile* error |

There is no string anywhere you could mistype into a crash. That is the headline, and it is real.

---

## 3. Routes as types — modelling the graph

The graph is a data model now, so model it like one. The conventions you will use all week:

```kotlin
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// The sealed root: every destination in the app is a Route. Sealed = exhaustive.
sealed interface Route : NavKey

// No-argument screen -> data object. One canonical instance, cheap, comparable.
@Serializable data object Home : Route
@Serializable data object Settings : Route

// Screen with arguments -> data class. The properties ARE the arguments.
@Serializable data class ItemDetail(val itemId: Int) : Route
@Serializable data class UserProfile(val userId: String, val tab: ProfileTab = ProfileTab.Posts) : Route

@Serializable enum class ProfileTab { Posts, About }
```

Four decisions worth being able to defend in a code review:

1. **Why `@Serializable`?** Because the back stack must survive **process death.** When Android kills your backgrounded app to reclaim memory and the user returns, the system restores your saved instance state — and Nav3 saves the back stack by serializing each route. A `data object Home` serializes to nothing; a `data class ItemDetail(itemId = 42)` serializes to its properties and comes back as exactly that instance. You do not write a serializer; `@Serializable` from `kotlinx.serialization` generates it, and the `kotlin("plugin.serialization")` Gradle plugin wires it in. (If you forget `@Serializable`, saving the back stack fails — that is the error to recognise.)

2. **Why `data object` for no-argument screens?** A `data object` is a singleton with a sensible `equals`/`hashCode`/`toString`. Two references to `Home` are equal, which is exactly what you want for a screen that has no identity beyond "the home screen." Using a `data class` with no properties would also work but is noisier; `object` would lack the nice `toString`. `data object` is the idiom.

3. **Why a `sealed interface` root?** Exhaustiveness. When you write a `when (route)` over `Route` — for analytics, for a custom transition decision, for a "which tab does this belong to" mapping — the compiler forces you to handle every destination, and *fails your build* the day someone adds a screen and forgets to handle it. A sealed graph is a graph you cannot under-handle. (It also makes the whole graph greppable in one place, which reviewers love.)

4. **Arguments vs. shared state.** An argument is part of a screen's *identity* — `ItemDetail(itemId = 42)` and `ItemDetail(itemId = 43)` are different entries, and the back stack treats them as distinct. So put in the route *only* what identifies the screen: an id, a small enum, a query string. Do **not** put a whole `Item` object, a list, or anything large or mutable in the route — that bloats the serialized state and couples your navigation to your data model. The screen takes the *id* from the route and loads the object from a repository or a ViewModel. "The route carries identity; the ViewModel carries data" is the line you will draw all week, and it is the line Week 12 builds its architecture on.

---

## 4. The back stack you own

The back stack is the heart of the inversion. In Nav3 it is a `SnapshotStateList<NavKey>` — Compose's observable list type. `rememberNavBackStack(Home)` gives you one seeded with `Home`, already `rememberSaveable` so it survives configuration change and process death:

```kotlin
val backStack = rememberNavBackStack(Home)
```

Everything you do to navigate is a list operation:

```kotlin
// Forward: push a route.
backStack.add(ItemDetail(itemId = 42))

// Back: pop the top.
backStack.removeLastOrNull()

// Pop to a specific screen (e.g. "back to Home"): drop everything above it.
val homeIndex = backStack.indexOfFirst { it is Home }
if (homeIndex != -1) backStack.subList(homeIndex + 1, backStack.size).clear()

// Replace the whole stack (e.g. after login, clear the auth flow):
backStack.clear()
backStack.add(Home)

// Inspect the current top — this is ordinary state you can read.
val current = backStack.lastOrNull()
```

Because the back stack is just observable Compose state, three things that were hard in the old model become trivial:

- **You can read where you are.** `backStack.lastOrNull()` is the current screen. Want to show a different top bar on `Home` than on `ItemDetail`? `when (backStack.lastOrNull())` and branch. No `currentBackStackEntryAsState()` ceremony.
- **You can construct any history you want.** Deep links (lecture 2) work by *building* the back stack — `listOf(Home, ItemDetail(42))` — so that back from the deep-linked detail lands on Home. The old model made you fight the controller for this; here you just assemble the list.
- **You can test it without a UI.** A test can hold a back stack, call the navigation lambdas, and assert on the list contents directly. Exercise 02 does exactly this.

One rule to internalise: **mutate the back stack from event callbacks, not from composition.** Adding to the back stack inside the body of a composable (rather than inside an `onClick` or an effect) re-navigates on every recomposition — an infinite loop. Navigation is an *event*; put the `backStack.add(…)` in the lambda you pass down (`onItem = { … }`), never in the composable body. This is the same "side effects go in effects, not in the body" discipline from Week 8, applied to navigation.

---

## 5. `NavDisplay` and the `entryProvider`

`NavDisplay` is the composable that turns your back stack into rendered screens. It reads the top of the stack, finds the matching entry, and shows it — re-rendering and animating whenever the top changes. You give it three things: the back stack, a back handler, and an `entryProvider`.

```kotlin
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider {
        entry<Home> {
            HomeScreen(
                onItem = { id -> backStack.add(ItemDetail(itemId = id)) },
                onSettings = { backStack.add(Settings) }
            )
        }
        entry<Settings> {
            SettingsScreen(onBack = { backStack.removeLastOrNull() })
        }
        entry<ItemDetail> { route ->          // the route instance is the lambda argument
            DetailScreen(itemId = route.itemId)
        }
    }
)
```

The `entryProvider { … }` DSL maps each route **type** to a composable via `entry<T> { … }`:

- `entry<Home> { … }` — no argument; the lambda takes no parameter (or ignores it).
- `entry<ItemDetail> { route -> … }` — the lambda receives the *typed route instance*, so `route.itemId` is a checked property access. This is where "no `Bundle`" cashes out: the argument arrives as a Kotlin value of the right type, because it never left Kotlin in the first place — Nav3 serialized and deserialized it for you across process death, but in-process it is just the object you added.

`NavDisplay` also owns transitions and predictive back (lecture 2). For now, know its three responsibilities: **render the top entry, animate when the top changes, and route the system back gesture to `onBack`.**

### `entryDecorators` — cross-cutting concerns

Real entries need more than a composable: a ViewModel scoped to the entry, saved state that survives the entry being on the back stack, lifecycle awareness. Nav3 handles these with **entry decorators** — wrappers applied around every entry. You usually pass the standard set:

```kotlin
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryDecorators = listOf(
        rememberSceneSetupNavEntryDecorator(),     // base setup
        rememberSavedStateNavEntryDecorator(),      // saved state per entry
        rememberViewModelStoreNavEntryDecorator()   // ViewModel scoped to the entry
    ),
    entryProvider = entryProvider { /* … */ }
)
```

The one that matters most for the weeks ahead is `rememberViewModelStoreNavEntryDecorator()`, from `lifecycle-viewmodel-navigation3`. It scopes a `ViewModel` to a **back-stack entry**, not to an `Activity`. That means a detail screen's ViewModel lives exactly as long as that detail entry is on the stack: created when you navigate in, cleared when you pop it off. We cover this properly in §7, but install the decorator now so `viewModel()` inside an entry does the right thing.

---

## 6. Passing arguments without a `Bundle`

This is the crux of the type-safety story, so it gets its own section. In Nav3 an argument is a property of the route type. You pass it when you construct the route, and you read it off the route in the entry. There is no `Bundle`, no key, no cast:

```kotlin
@Serializable data class ItemDetail(val itemId: Int, val highlight: String? = null) : Route

// Pass: construct the route with the arguments. Compiler checks the types.
backStack.add(ItemDetail(itemId = 42, highlight = "price"))

// Read: the entry lambda receives the typed instance.
entry<ItemDetail> { route ->
    DetailScreen(itemId = route.itemId, highlight = route.highlight)
}
```

Compare the failure modes:

```kotlin
// OLD MODEL — every one of these is a runtime bug:
navController.navigate("detail/$id")                    // wrong route string -> crash at runtime
entry.arguments?.getInt("itemID")                       // wrong key (case!) -> null -> default -> silent bug
entry.arguments?.getString("itemId")                    // wrong type -> ClassCastException or null

// NAV3 — every one of these is a COMPILE error:
backStack.add(ItemDetial(itemId = 42))                  // typo in type name -> won't compile
backStack.add(ItemDetail(itemId = "42"))                // wrong arg type -> won't compile
entry<ItemDetail> { route -> route.itmeId }             // typo in property -> won't compile
```

The discipline that comes with this power: **keep route arguments small and identity-shaped.** Pass an `itemId: Int`, not the `Item`. Pass a `userId: String`, not the `User`. The reasons are three: (1) the route is serialized into saved instance state, so a big object bloats it and a non-`@Serializable` field breaks it; (2) the route is part of the back stack's identity and equality, so a mutable object makes equality nonsense; (3) coupling navigation to your data model means a change to `Item` ripples into your navigation graph. The screen receives the id and loads the data — from a ViewModel, which is the next section and the bridge to Week 12.

What may a route property be? Anything `@Serializable`: primitives, `String`, enums, `@Serializable` data classes, lists of those, nullables, and properties with defaults (`highlight: String? = null`). A default makes the argument optional at the call site — `ItemDetail(itemId = 42)` is valid — exactly the ergonomics you want.

---

## 7. ViewModel scoping in Nav3 — the bridge to architecture

A screen usually needs a `ViewModel`: somewhere to hold state that survives recomposition and configuration change, run coroutines, and load data. The question is *what does the ViewModel's lifetime track?* In the old `Activity` world a `ViewModel` was scoped to the `Activity` and lived as long as the screen's host. In Nav3, you scope it to the **back-stack entry**, so it lives exactly as long as that destination is on the stack.

With `rememberViewModelStoreNavEntryDecorator()` installed (§5), `viewModel()` inside an entry resolves to a ViewModel scoped to that entry:

```kotlin
class DetailViewModel(itemId: Int) : ViewModel() {
    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = DetailUiState.Loaded(repository.item(itemId))
        }
    }
}

entry<ItemDetail> { route ->
    // Scoped to THIS ItemDetail entry. Created when you navigate in,
    // cleared (onCleared called) when you pop this entry off the stack.
    val vm: DetailViewModel = viewModel { DetailViewModel(route.itemId) }
    val state by vm.state.collectAsStateWithLifecycle()
    DetailScreen(state = state)
}
```

Three consequences to hold onto:

- **The ViewModel survives recomposition and rotation**, because it is scoped to the entry, not to the composition. Rotate the device on the detail screen and the ViewModel — and its in-flight load — keeps going. This is the same survival guarantee you reached for `rememberSaveable` for in Week 8, but for *logic and async work*, not just a value.
- **The ViewModel is cleared exactly when its entry is popped.** Pop `ItemDetail` off the back stack and its `DetailViewModel.onCleared()` runs, cancelling `viewModelScope` and releasing resources. No leak, no manual teardown. The entry's lifetime *is* the ViewModel's lifetime.
- **The route's argument seeds the ViewModel.** `route.itemId` flows into `DetailViewModel(route.itemId)`. This is the clean version of "the route carries identity, the ViewModel carries data": the id rides in the route (small, serializable, identity-shaped), the data lives in the ViewModel (loaded from a repository, survives rotation), and the boundary between them is exactly the entry. Week 12 names this architecture (MVVM with unidirectional data flow, the Now-In-Android shape); this week you build the seam it plugs into.

---

## 8. What Navigation 3 still asks of you — the sharp edges

Nav3 is a good library, which means it leaks responsibility back to you in predictable places. Senior engineers know where:

1. **You own back behaviour.** `onBack = { backStack.removeLastOrNull() }` is the default, but *you* wrote it. If you want "back from a deep-linked screen goes Home" or "back on the root tab exits the app," you write that logic against the back stack. The library renders; you decide history. This is freedom and responsibility in equal measure.
2. **Mutate from events, never from composition.** §4's rule, restated because it is the single most common Nav3 bug: a `backStack.add(…)` in a composable body loops. Navigation is an event; it goes in a callback or an effect.
3. **Keep routes serializable and small.** A non-`@Serializable` property, or a giant object in a route, breaks save/restore or bloats it. The compiler catches the missing annotation at the serialization site; the bloat is a judgement call you must make.
4. **Per-tab back stacks are your design.** Nav3 does not impose a tab model; bottom-bar navigation with independent per-tab history is something you *compose* out of multiple back stacks (lecture 2). The library gives you the primitive (a back stack); the pattern is yours to assemble.
5. **The library is young.** Navigation 3 stabilised over 2024–2025 and API names may still shift at the edges. Pin your versions, read the release notes (resources page), and prefer the official `nav3-recipes` over older blog posts. The *concepts* in this lecture are stable; a method name might move.

None of these are reasons to reach back for string routes. They are the things you keep in peripheral vision so that when you hit one, you recognise it as "Nav3 handing me a decision" rather than "Nav3 being broken."

---

## 9. The decision table

When is Nav3 the right tool, and what do you reach for at the edges? Memorise the shape:

| Situation | Reach for |
|-----------|-----------|
| New multi-screen Compose app in 2026 | **Navigation 3** — the default |
| You want compiler-checked routes and arguments | **Navigation 3** (or type-safe Navigation-Compose on an existing app) |
| Bottom-bar app with independent per-tab history | **Nav3** with one back stack per tab (lecture 2) |
| Deep links into typed destinations | **Nav3** — build the back stack from the `Uri` (lecture 2) |
| Existing large app on string routes, can't rewrite | **Type-safe Navigation-Compose** as a half-step, migrate to Nav3 incrementally |
| A single screen, no navigation | **Neither** — just call the composable; navigation is overhead you don't need yet |
| Adaptive multi-pane (list-detail on a foldable) | **Nav3** with an adaptive `NavDisplay` scene strategy (Phase 4 covers form factors) |

That second-to-last row matters: do not stand up a navigation library for a one-screen app. Navigation earns its keep at the *second* screen. Compose Pomodoro from Week 7 did not need it; Catalog Companion this week does.

---

## 10. Recap — the inversion, in one breath

You will write Nav3 all week. The discipline that turns you from someone who *uses* it into someone who can *reason about* it is to keep the inversion in mind: **the app owns the back stack; the library renders the top of it.**

- Navigate forward → `backStack.add(SomeRoute(args))`.
- Navigate back → `backStack.removeLastOrNull()` (which `onBack` wires to the system gesture).
- Render → `NavDisplay` shows the `entry<T>` matching the top route, passing the typed instance.
- Argument → a property of the route type, checked by the compiler, never a `Bundle` key.
- ViewModel → scoped to the back-stack entry; lives as long as the entry, seeded by the route's id.

Navigation 3 did not invent a clever new controller. It deleted the controller and gave you the state. The string-route bug class — wrong route, wrong argument key, lost type — goes away not because the library checks your strings harder, but because there are no strings. Learn the inversion well enough to build a history by hand, model your graph as a sealed set of `@Serializable` types, and you have this week's core skill: **a navigation graph as a data model.**

In lecture 2 we use that primitive to build the patterns a real app needs: per-tab back stacks for a bottom bar, nested graphs for a flow-within-a-flow, deep links that seed the stack from a `Uri`, predictive back with transition specs so leaving a screen feels native, and the tests that prove every transition. Bring this `back stack → NavDisplay → entryProvider` picture with you; we are about to compose it into something real.
