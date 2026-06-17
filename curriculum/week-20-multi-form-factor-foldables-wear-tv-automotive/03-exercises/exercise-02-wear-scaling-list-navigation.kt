// Exercise 2 — A Wear scaling list with swipe-dismiss navigation and rotary input
//
// Goal: Build a Wear OS screen that looks RIGHT on a round display: a scaling list
//       (TransformingLazyColumn) that fades content toward the curved edges, a
//       SwipeDismissableNavHost so the system's left-edge back gesture works, and
//       rotary input so the crown/bezel scrolls the list. This is lecture 2's
//       "Wear is not a small phone" made concrete — every component here differs
//       from its phone equivalent for a reason.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// These composables live in a `:wear` module (a Wear OS app module). Run on a
// Wear OS emulator (Wear OS Large Round, API 34+). The proof is visual: the list
// items scale/fade near the top and bottom edges, swiping from the left edge goes
// back, and turning the crown scrolls.
//
//   1. Create a :wear module (New ▸ Module ▸ Wear OS) or an Empty Wear app.
//   2. Add the Wear Compose deps (below) and drop these composables into src/main.
//   3. setContent { ForecastWearApp(sampleForecasts) } in the Wear Activity.
//   4. Run on the round emulator. Scroll with the mouse AND with the rotary input
//      (the emulator's side control / the crown overlay).
//
// DEPENDENCIES (in :wear/build.gradle.kts):
//
//   implementation("androidx.wear.compose:compose-material3:1.0.0")
//   implementation("androidx.wear.compose:compose-foundation:1.4.0")
//   implementation("androidx.wear.compose:compose-navigation:1.4.0")
//   implementation(platform("androidx.compose:compose-bom:2024.10.00"))
//   (use the latest stable versions your project resolves)
//
// ACCEPTANCE CRITERIA
//
//   [ ] The list uses TransformingLazyColumn (NOT a phone LazyColumn); items scale
//       and fade toward the curved top/bottom edges.
//   [ ] Navigation uses SwipeDismissableNavHost; a left-edge swipe goes back.
//   [ ] TimeText is visible (provided by AppScaffold) and a scroll position
//       indicator appears on the right edge while scrolling.
//   [ ] Rotary input scrolls the list (the crown/bezel works, not just touch).
//   [ ] Builds with 0 warnings.
//   [ ] You can explain why each component differs from its phone equivalent.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.weather.wear

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

// ----------------------------------------------------------------------------
// Trivial domain — keep it tiny so the focus stays on the Wear components.
// ----------------------------------------------------------------------------

data class HourForecast(val hour: Int, val tempC: Int, val condition: String)

val sampleForecasts: List<HourForecast> = (0..11).map { i ->
    HourForecast(hour = (9 + i) % 24, tempC = 12 + (i % 5), condition = if (i % 3 == 0) "Rain" else "Cloud")
}

// ----------------------------------------------------------------------------
// The app: an AppScaffold (app-level chrome: TimeText) wrapping a
// SwipeDismissableNavHost (the Wear back gesture). Two screens: list and detail.
// ----------------------------------------------------------------------------

@Composable
fun ForecastWearApp(forecasts: List<HourForecast>) {
    val navController = rememberSwipeDismissableNavController()

    AppScaffold {
        // SwipeDismissableNavHost cooperates with the system left-edge swipe-to-dismiss.
        // A phone NavHost would fight that gesture — that's why Wear has its own host.
        SwipeDismissableNavHost(navController, startDestination = "list") {
            composable("list") {
                ForecastListScreen(
                    forecasts = forecasts,
                    onOpen = { hour -> navController.navigate("detail/$hour") }
                )
            }
            composable("detail/{hour}") { backStackEntry ->
                val hour = backStackEntry.arguments?.getString("hour")?.toIntOrNull()
                val forecast = forecasts.firstOrNull { it.hour == hour }
                ForecastDetailScreen(forecast)
            }
        }
    }
}

// ----------------------------------------------------------------------------
// The list screen: TransformingLazyColumn (the scaling list) + rotary input.
// ----------------------------------------------------------------------------

@Composable
fun ForecastListScreen(forecasts: List<HourForecast>, onOpen: (Int) -> Unit) {
    val listState = rememberTransformingLazyColumnState()

    // ScreenScaffold owns the per-screen scroll position indicator (the curved
    // scrollbar on the right edge), driven by the list state.
    ScreenScaffold(scrollState = listState) {
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // Rotary (crown/bezel) drives the same list state as touch. Without
                // this the crown does nothing — a common Wear port bug.
                .rotaryScrollable(
                    RotaryScrollableDefaults.behavior(scrollableState = listState),
                    focusRequester = rememberActiveFocusRequester()
                )
        ) {
            item { ListHeader { Text("Today") } }   // header scales as it nears the edge
            items(forecasts, key = { it.hour }) { f ->
                Button(onClick = { onOpen(f.hour) }) {
                    Text("%02d:00  %d°  %s".format(f.hour, f.tempC, f.condition))
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// The detail screen: reached by tap, left by the system swipe-dismiss gesture.
// ----------------------------------------------------------------------------

@Composable
fun ForecastDetailScreen(forecast: HourForecast?) {
    ScreenScaffold {
        TransformingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(if (forecast == null) "Unknown" else "%02d:00".format(forecast.hour)) } }
            item { Text(if (forecast == null) "—" else "${forecast.tempC}°C") }
            item { Text(forecast?.condition ?: "—") }
            item { Text("Swipe from the left edge to go back.") }
        }
    }
}

// ----------------------------------------------------------------------------
// WHY each component differs from its phone equivalent (write it before reading):
//
//   - TransformingLazyColumn vs LazyColumn: the round screen curves away at top
//     and bottom; the transforming list scales/fades items toward the edges so
//     they look intentional instead of clipped. A phone LazyColumn renders items
//     hard against the curve and looks broken.
//   - SwipeDismissableNavHost vs NavHost: the system owns the left-edge swipe as
//     back. The Wear host cooperates with that gesture; a phone host fights it.
//   - AppScaffold/ScreenScaffold vs phone Scaffold: AppScaffold provides TimeText
//     (watch users expect the time always visible) and ScreenScaffold provides the
//     curved scroll position indicator shaped to the bezel.
//   - rotaryScrollable: a watch's primary scroll input is the crown/bezel, not
//     touch. You must opt the scrollable into rotary input and give it focus.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - Items don't scale near the edges. You used a plain LazyColumn from
//   androidx.compose.foundation. Import TransformingLazyColumn from
//   androidx.wear.compose.foundation.lazy — the scaling behavior is built in.
//
// - The crown does nothing. rotaryScrollable needs an ACTIVE focus requester;
//   rememberActiveFocusRequester() ties focus to this scrollable. Also make sure
//   the same `listState` is passed to both TransformingLazyColumn and the rotary
//   behavior — the crown drives that exact state.
//
// - No TimeText / no scroll indicator. Those come from AppScaffold and
//   ScreenScaffold respectively. If you skipped the scaffolds you lose the chrome
//   the watch UI is expected to have.
//
// - Swiping left does nothing. Confirm you used SwipeDismissableNavHost (not the
//   phone NavHost) and rememberSwipeDismissableNavController. The gesture is the
//   host's job.
//
// - Compile error on `items`. Import androidx.wear.compose.foundation.lazy.items
//   (the Wear overload), not the foundation one.
//
// ----------------------------------------------------------------------------
