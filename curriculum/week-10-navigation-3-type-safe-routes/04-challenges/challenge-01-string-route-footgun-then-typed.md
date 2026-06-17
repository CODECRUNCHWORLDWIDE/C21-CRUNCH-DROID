# Challenge 1 — Plant a string-route footgun, then make it a compile error

**Time.** 60–120 minutes.
**Deliverable.** A short report (`SAFETY.md`) with the before/after, the failing test that proves the runtime crash, the refactored typed graph, and an explanation — committed to your Week 10 repo.

## The premise

Every Android engineer has, at least once, shipped the "argument read by the wrong key" bug. The route template says `{itemId}`, the read says `getInt("item_id")`, and they don't match — so the argument arrives as the default, or the cast throws, and the app crashes or silently shows the wrong thing. It works in the demo because you happen to type both keys the same that day. Then someone renames the argument in one place and not the other, and a user gets a crash report.

The skill this challenge builds is not "know string routes are risky." It is: **plant the footgun, write the test that proves it crashes at runtime, then refactor to typed routes so the same mistake becomes a compile error the test can no longer even express.** The grading is the difference in *where* the bug is caught — runtime vs. compile time — and your explanation of why that difference matters.

## What to build

### Step 1 — The string-route graph with a planted argument bug

Build a two-screen graph the old way, with a deliberate key mismatch. The route passes `itemId` but the detail reads `item_id`:

```kotlin
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

@Composable
fun BuggyApp(nav: NavHostController = rememberNavController()) {
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(onItem = { id -> nav.navigate("detail/$id") })
        }
        composable(
            route = "detail/{itemId}",                                  // declares "itemId"
            arguments = listOf(navArgument("itemId") { type = NavType.IntType })
        ) { entry ->
            // THE FOOTGUN: reads "item_id" — a different key. Returns null -> -1 fallback.
            val itemId = entry.arguments?.getInt("item_id") ?: -1
            DetailScreen(itemId = itemId)                               // shows -1, the wrong item
        }
    }
}
```

This *compiles*. It *runs*. It shows the wrong item, because `getInt("item_id")` finds nothing and falls back to `-1`. A subtler variant uses `getString` where an `Int` was stored and crashes with a `ClassCastException`. Pick one; the `-1` silent-wrong-answer version is the more insidious teaching case.

### Step 2 — The test that proves it's broken at runtime

Write a Compose UI test that navigates to the detail and asserts the *correct* item is shown. It will FAIL — that failure is your "before" evidence. The bug is only catchable at runtime, by a test, after the fact:

```kotlin
@Test
fun detail_showsTheItemThatWasTappedOn() {
    rule.setContent { BuggyApp() }
    rule.onNodeWithText("Open 42").performClick()
    rule.waitForIdle()
    // We tapped item 42; we expect "Detail: 42". The footgun shows "Detail: -1".
    rule.onNodeWithText("Detail: 42").assertIsDisplayed()   // FAILS on the buggy graph
}
```

Run it. Watch it fail with the wrong number. Screenshot or copy the failure. This is the cost of stringly-typed routing: the bug is invisible until a test (or a user) exercises that exact path at runtime.

### Step 3 — Refactor to typed routes

Now rebuild the graph with Navigation 3 and typed routes. The argument is a property; there is no key to mismatch:

```kotlin
sealed interface Route : NavKey
@Serializable data object Home : Route
@Serializable data class Detail(val itemId: Int) : Route

@Composable
fun SafeApp() {
    val backStack = rememberNavBackStack(Home)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Home> { HomeScreen(onItem = { id -> backStack.add(Detail(itemId = id)) }) }
            entry<Detail> { route -> DetailScreen(itemId = route.itemId) }   // no key, no mismatch
        }
    )
}
```

### Step 4 — Show the bug is now impossible to write

Here is the heart of the challenge. Try to reproduce the Step 1 mistake in the typed graph. You *cannot*:

```kotlin
entry<Detail> { route -> DetailScreen(itemId = route.item_id) }   // COMPILE ERROR: no such property
entry<Detail> { route -> DetailScreen(itemId = route.itemId) }    // the only thing that compiles is correct
```

There is no `"item_id"` to mistype because there is no string key at all — there is a property `itemId`, and the compiler knows its name and type. The Step 2 test now *passes*, but more importantly, the Step 2 *bug can no longer be written*. Run the same test against `SafeApp` and watch it go green.

### Step 5 (optional, for the stretch) — delete an argument and read the compiler's list

Add a required argument to `Detail` (say `val source: String`) and do **not** update the call sites. Build. The compiler lists every place that constructs a `Detail` and now needs the new argument — a complete, exhaustive, free refactor checklist. Do the equivalent in the string graph (rename `itemId` to `id` in the template) and note that the compiler says *nothing*; the breakage only surfaces at runtime. Record both experiences.

## Acceptance criteria

- [ ] `BuggyApp` exists with a real, planted key-mismatch (or wrong-type) argument bug, and it compiles and runs.
- [ ] A Compose UI test asserts the correct item is shown and **fails** on `BuggyApp` (the "before" evidence — keep the failure output).
- [ ] `SafeApp` is the Navigation 3 typed-route refactor; the **same** test **passes** on it.
- [ ] You demonstrated that the Step 1 mistake is a **compile error** in the typed graph (the `route.item_id` line that won't compile).
- [ ] `SAFETY.md` records: the buggy code, the failing-test output, the typed refactor, and a 3–5 sentence explanation of why "the compiler caught it" is strictly better than "a test caught it."
- [ ] (Stretch) The delete-an-argument experiment, with the compiler's call-site list for the typed graph and the silence of the string graph.
- [ ] Build with **0 warnings**.

## What "great" looks like

A weak submission says "typed routes are safer." A great submission says:

> The string-route graph passed `itemId` but read `getInt("item_id")`; the two keys never matched, so every detail showed `-1`. The bug compiled, ran, and demoed fine — it only surfaced when a UI test navigated to the detail and asserted the item number. Converting to a `@Serializable data class Detail(val itemId: Int)` deleted the key entirely: the detail reads `route.itemId`, a typed property, so the mismatch is now a compile error (`route.item_id` does not resolve). The win is *where* the failure lives: the string version needs a test that exercises the exact path at runtime to find the bug; the typed version makes the bug unrepresentable, so it cannot reach a test or a user. A test catches bugs you remembered to write; the compiler catches every instance of a class of bug, including the ones you didn't think to test.

Specific, reproduced, and honest about the trade (you write the routes; the compiler checks them). That's the senior-engineer answer.

## Where this reappears

The "make illegal states unrepresentable" instinct — moving a bug from runtime to compile time by choosing a better type — is exactly what Week 12 does when it models UI state as a sealed `UiState` (`Loading | Error | Success`) so that "rendering data while still loading" cannot be expressed. The footgun you fixed here is the same shape: a string where a type belonged. You'll meet it again every week of Phase 3.
