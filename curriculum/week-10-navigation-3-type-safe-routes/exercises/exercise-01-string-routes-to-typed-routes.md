# Exercise 1 — String routes to typed routes

**Goal.** Take a tiny, deliberately old-fashioned string-route `NavHost` graph and migrate it to Navigation 3. By the end there is no route string and no `Bundle` argument read anywhere — every destination is a `@Serializable` type and every argument is a property the compiler checks. This is the entire promise of the week distilled to one migration: if you can do this, you understand the model; everything else is elaboration.

**Estimated time.** 45 minutes.

**Prerequisites.** Android Studio Ladybug+, an Android 15 emulator (Android 7.0 / API 24 minimum works for the runtime; Nav3 artifacts as in the README). A fresh empty Compose Activity project named `RouteMigration`, Kotlin 2.0+ with the Compose Compiler plugin and `kotlin("plugin.serialization")`. The mini-project does the real, large migration; this is the warm-up on a three-screen graph.

---

## Step 1 — Start from the string-route graph (the *before*)

Create `BeforeNav.kt`. This is the old model — type it out so you feel exactly what you are deleting. It compiles and runs; it is also a runtime crash waiting for a typo.

```kotlin
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun BeforeApp() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onItem = { id -> nav.navigate("detail/$id") },          // <- unchecked string
                onSettings = { nav.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable(
            route = "detail/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.IntType })
        ) { entry ->
            val itemId = entry.arguments?.getInt("itemId") ?: 0          // <- Bundle read + cast + fallback
            DetailScreen(itemId = itemId, onBack = { nav.popBackStack() })
        }
    }
}
```

Count the footguns before you proceed: `"detail/$id"` and `"detail/{itemId}"` must agree by spelling; `"itemId"` is written three times with nothing tying the copies together; `getInt("itemId")` reads from a `Bundle` by key and falls back to `0` if it's missing. Each is a runtime bug the compiler cannot see.

## Step 2 — Define the graph as types

Create `Routes.kt`. The whole graph becomes a sealed family of `@Serializable` types.

```kotlin
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// The sealed root makes the graph exhaustive and reviewable in one place.
sealed interface Route : NavKey

@Serializable data object Home : Route
@Serializable data object Settings : Route
@Serializable data class Detail(val itemId: Int) : Route
```

Three things to be able to explain in review:

- `data object` for `Home`/`Settings` (no arguments), `data class` for `Detail` (carries `itemId`).
- `@Serializable` so the back stack survives process death — the back stack is saved by serializing each route.
- `sealed interface Route : NavKey` so every destination is a `Route` and a `when` over them is exhaustive.

## Step 3 — Render with `NavDisplay` + `entryProvider` (the *after*)

Replace `BeforeApp` with `AfterApp` in a new file `AfterNav.kt`. The shape is back-stack → `NavDisplay` → `entryProvider`.

```kotlin
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay

@Composable
fun AfterApp() {
    val backStack = rememberNavBackStack(Home)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Home> {
                HomeScreen(
                    onItem = { id -> backStack.add(Detail(itemId = id)) },   // typed; compiler-checked
                    onSettings = { backStack.add(Settings) }
                )
            }
            entry<Settings> {
                SettingsScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<Detail> { route ->
                DetailScreen(itemId = route.itemId, onBack = { backStack.removeLastOrNull() })  // property, no Bundle
            }
        }
    )
}
```

Line up the deletions against Step 1:

- `nav.navigate("detail/$id")` → `backStack.add(Detail(itemId = id))`. No string.
- `"detail/{itemId}"` template → `data class Detail(val itemId: Int)`. The arg is the property.
- `entry.arguments?.getInt("itemId") ?: 0` → `route.itemId`. No `Bundle`, no key, no fallback.

## Step 4 — The screens (unchanged by the migration)

Create `Screens.kt`. Note these are *identical* whether the app is string-routed or typed — the migration touches the wiring, not the screens. That is the point: navigation is a layer you can swap.

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HomeScreen(onItem: (Int) -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Home", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = { onItem(42) }) { Text("Open item 42") }
        Button(onClick = { onItem(7) }) { Text("Open item 7") }
        Button(onClick = onSettings) { Text("Settings") }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun DetailScreen(itemId: Int, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Detail: $itemId", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onBack) { Text("Back") }
    }
}
```

## Step 5 — Run, navigate, and feel the type safety

Set `AfterApp()` as your activity content, run on the emulator, and walk every path: Home → item 42 → back, Home → item 7 → back, Home → Settings → back. It behaves identically to the string-route version — that is expected. The difference is invisible at runtime and total at compile time.

## Step 6 — Prove the compiler catches what the strings didn't

Now break each call site the way a string typo would, and watch the *compiler* — not a user's crash report — stop you:

```kotlin
backStack.add(Detial(itemId = 42))   // typo in type name -> RED, won't compile
backStack.add(Detail(itemId = "42")) // wrong arg type -> RED, won't compile
entry<Detail> { route -> route.itmeId } // typo in property -> RED, won't compile
```

In the string-route version, each equivalent typo (`"detial/$id"`, a string where an int was meant, `getInt("itmeId")`) compiled fine and crashed or silently defaulted at runtime. Delete a property from `Detail` (say add a required `val source: String`) and watch the compiler list *every* call site you must update. That list is the bug class you just retired.

---

## Acceptance criteria

- [ ] A `sealed interface Route : NavKey` with `Home`/`Settings` as `@Serializable data object` and `Detail` as `@Serializable data class Detail(val itemId: Int)`.
- [ ] `AfterApp` renders the graph with `rememberNavBackStack`, `NavDisplay`, and an `entryProvider` mapping each route type via `entry<T>`.
- [ ] **Zero** `navigate("…")` calls and **zero** `arguments?.get…` reads remain. (`grep -rn 'navigate("' src/` and `grep -rn 'arguments?.get' src/` return nothing.)
- [ ] The `Detail` argument is read as `route.itemId`, a typed property — not from a `Bundle`.
- [ ] Build with **0 warnings, 0 errors**.
- [ ] You broke a call site three ways (typo'd type, wrong arg type, typo'd property) and confirmed each is a *compile* error, then reverted.

## What you just proved

You proved the core claim of the week with your own hands: the string-route bug class — wrong route, wrong argument key, lost type — does not go away because the library checks strings harder, but because **there are no strings.** You migrated a working graph with the screens untouched, which is the senior move: navigation is a layer, and a well-modelled one swaps cleanly. Every exercise and the mini-project build on this `back stack → NavDisplay → entryProvider` skeleton.

---

## Hints (read only if stuck > 10 min)

- **`rememberNavBackStack(Home)` won't resolve.** Confirm the `androidx.navigation3:navigation3-runtime` and `navigation3-ui` dependencies are in your version catalog and the Gradle sync succeeded. The API names track the library version; if one moved, check the release notes on the resources page.
- **`@Serializable` is unresolved.** Add the `kotlin("plugin.serialization")` Gradle plugin and the `kotlinx-serialization-json` dependency. The annotation comes from `kotlinx.serialization`, not from Nav3.
- **`entry<Detail> { route -> … }` says `route` is `Any`.** Make sure `Detail` implements `Route` (and thus `NavKey`) and that you wrote `entry<Detail>` with the concrete type — the lambda's parameter type is inferred from the `entry<T>` type argument.
- **Back from a screen does nothing.** `onBack = { backStack.removeLastOrNull() }` must be on `NavDisplay`, and your in-screen "Back" button should call the same pop. If both are missing, there is nothing to pop the stack.
- **Compiler complains the `when`/graph isn't exhaustive somewhere.** That's the sealed interface doing its job — it's a feature. Handle the new route or you can't build, which is exactly the safety you wanted.
