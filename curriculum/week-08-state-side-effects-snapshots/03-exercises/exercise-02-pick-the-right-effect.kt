// Exercise 2 — Pick the right side-effect API for six scenarios
//
// Goal: For each of six scenarios, choose and implement the correct side-effect
//       API, and fix one composable that fires a coroutine on every recomposition.
//       The skill is reaching for the right tool the FIRST time, by naming the
//       lifecycle hook it keys to (lecture 2, decision table §9).
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// These composables live in your `app` module. Most you confirm by running and
// watching logcat (each logs when its effect runs). The point is the CHOICE and
// the KEY, not heavy UI. Fill in the bodies marked "// TODO 1:" etc.
//
//   1. Drop into app/src/main.
//   2. Implement each TODO with the correct API.
//   3. Run, interact, and confirm the logcat lines fire the RIGHT number of times.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Each scenario uses the correct API for its lifecycle hook.
//   [ ] Scenario 1's request fires ONCE per userId, not per recomposition.
//   [ ] The listener in scenario 3 is removed on leave (no leak).
//   [ ] Builds with 0 warnings (including no "coroutine in composition" smell).
//   [ ] You wrote, in each comment, WHY that API (which hook it keys to).
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.scratch.effects

import android.util.Log
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

private const val TAG = "Effects"

// Fakes so the file compiles standalone.
class User(val id: String, val name: String)
interface UserRepository { suspend fun fetchUser(id: String): User }
fun interface PriceListener { fun onPrice(price: Double) }
interface PriceFeed {
    fun addListener(l: PriceListener)
    fun removeListener(l: PriceListener)
}

// ----------------------------------------------------------------------------
// SCENARIO 1 — Load a user when the screen appears, reload when userId changes.
// FIX THE BUG: the original fires fetchUser on EVERY recomposition.
// ----------------------------------------------------------------------------

@Composable
fun ProfileScreen(userId: String, repo: UserRepository) {
    var user by remember { mutableStateOf<User?>(null) }

    // TODO 1: load the user with the correct API, keyed so it reloads ONLY when
    //         userId changes (NOT on every recomposition). Why this API?
    //         -> ____________________________________________

    Text(user?.name ?: "Loading…")
}

// ----------------------------------------------------------------------------
// SCENARIO 2 — Run a share action when the user taps a button (an EVENT).
// ----------------------------------------------------------------------------

@Composable
fun ShareButton(content: String, share: suspend (String) -> Unit) {
    // TODO 2: get a scope and launch the share from the button's onClick.
    //         Why not LaunchedEffect here?
    //         -> ____________________________________________
    val scope = rememberCoroutineScope()
    Button(onClick = {
        // TODO 2 (cont.): launch share(content) on `scope`
    }) {
        Text("Share")
    }
}

// ----------------------------------------------------------------------------
// SCENARIO 3 — Subscribe to a price feed while on screen; UNSUBSCRIBE on leave.
// ----------------------------------------------------------------------------

@Composable
fun PriceTicker(feed: PriceFeed) {
    var price by remember { mutableStateOf(0.0) }

    // TODO 3: register a PriceListener on enter and REMOVE it on leave (no leak).
    //         Why this API (which gives you a teardown hook)?
    //         -> ____________________________________________

    Text("Price: $price")
}

// ----------------------------------------------------------------------------
// SCENARIO 4 — Turn a suspend fetch into a single State<Result> with Loading.
// ----------------------------------------------------------------------------

sealed interface UiResult<out T> {
    data object Loading : UiResult<Nothing>
    data class Success<T>(val value: T) : UiResult<T>
    data class Error(val message: String) : UiResult<Nothing>
}

@Composable
fun userResult(userId: String, repo: UserRepository): State<UiResult<User>> {
    // TODO 4: produce a State<UiResult<User>> from the suspend fetch, starting at
    //         Loading and keyed on userId. Which API combines remember+LaunchedEffect?
    //         -> ____________________________________________
    return produceState<UiResult<User>>(initialValue = UiResult.Loading, userId) {
        value = try {
            UiResult.Success(repo.fetchUser(userId))
        } catch (e: Exception) {
            UiResult.Error(e.message ?: "error")
        }
    }
}

// ----------------------------------------------------------------------------
// SCENARIO 5 — Show a "scroll to top" flag that only flips when the first visible
// index crosses 0, NOT on every scroll pixel.
// ----------------------------------------------------------------------------

@Composable
fun scrollToTopVisible(firstVisibleIndexProvider: () -> Int): Boolean {
    // TODO 5: derive a boolean that only NOTIFIES readers when the result flips,
    //         even though firstVisibleIndex changes constantly. Which API?
    //         -> ____________________________________________
    val visible by remember {
        derivedStateOf { firstVisibleIndexProvider() > 0 }
    }
    return visible
}

// ----------------------------------------------------------------------------
// SCENARIO 6 — A 5-second auto-dismiss timer that runs ONCE but calls the LATEST
// onDismiss lambda when it fires (without restarting on recomposition).
// ----------------------------------------------------------------------------

@Composable
fun AutoDismiss(onDismiss: () -> Unit) {
    // TODO 6: run ONE timer (keyed on Unit) but call the freshest onDismiss.
    //         Which helper keeps the latest value without being a restart key?
    //         -> ____________________________________________
    // hint: rememberUpdatedState
}

// ----------------------------------------------------------------------------
// THE ANSWERS (each TODO's API + the hook it keys to). Try first, then check:
//
//   1. LaunchedEffect(userId) — enter + key change. Fires once per userId, cancels
//      the prior fetch when userId changes. The bug was a bare call in the body,
//      which fires every recomposition.
//   2. rememberCoroutineScope().launch — the work starts from an EVENT (the tap),
//      not from composition; LaunchedEffect can't be called from a lambda.
//   3. DisposableEffect(feed) — enter/leave with onDispose teardown; you MUST
//      removeListener or you leak it past the composable's life.
//   4. produceState(Loading, userId) — remember + LaunchedEffect combined into a
//      single State<T>, keyed on userId.
//   5. derivedStateOf — recomputes on input change but only notifies on RESULT
//      change, so the reader recomposes when the boolean flips, not per scroll.
//   6. rememberUpdatedState(onDismiss) + LaunchedEffect(Unit) — one timer, latest
//      lambda. Keying on onDismiss would restart the timer every recomposition.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - Scenario 1's fix: move the fetch INTO LaunchedEffect(userId) { ... }. A bare
//   `repo.fetchUser(...)` in the body is the §1 footgun — it runs every recompose.
//
// - Scenario 3: DisposableEffect's lambda MUST end in onDispose { ... }; the
//   compiler enforces it. Register in the body, remove in onDispose.
//
// - Scenario 6: `val current by rememberUpdatedState(onDismiss)` then
//   `LaunchedEffect(Unit) { delay(5000); current() }`. Log to confirm the timer
//   does NOT restart when the parent recomposes with a new lambda.
//
// - "Coroutine in composition" warning/smell: you launched outside an effect.
//   Every coroutine in this file belongs in LaunchedEffect, produceState, or a
//   rememberCoroutineScope().launch inside an event handler.
//
// ----------------------------------------------------------------------------
