// Exercise 3 — A Compose for Wear OS forecast screen
//
// Goal: Build a first Wear screen that renders the shared WeatherForecast model on
//       the wrist — the Wear Scaffold with TimeText, a ScalingLazyColumn of forecast
//       rows as Chips, and Wear Material components. You use the Wear component set
//       (NOT phone Material), respect the round screen, and show a glance-length
//       subset. This is lecture 2, §3–5 and §8, made concrete.
//
// Estimated time: 45 minutes. Needs a Wear OS emulator (a round Wear API 34 image).
//
// HOW TO USE THIS FILE
//
//   This goes in a Wear app module (a :wear module, or a Wear-targeted app). Run it
//   on a round Wear emulator. The proof is visual: TimeText curves along the top, the
//   list scales/centers items, and Chips render full-width.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Imports come from androidx.wear.compose.* (NOT androidx.compose.material3.*).
//   [ ] A Wear Scaffold with TimeText() in the timeText slot.
//   [ ] A ScalingLazyColumn of forecast hours, rendered as Chips, with keyed items.
//   [ ] The screen renders each UiState (Loading / Content / Error) — render-by-state.
//   [ ] Runs on a round Wear emulator; TimeText is visible at the top. 0 warnings.
//
// Build deps (wear module build.gradle.kts):
//   implementation(platform("androidx.compose:compose-bom:2024.10.00"))
//   implementation("androidx.wear.compose:compose-material3:1.0.0")
//   implementation("androidx.wear.compose:compose-foundation:1.4.0")
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package com.crunch.weather.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
// Wear-specific imports — NOTE the androidx.wear.compose package:
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Scaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText

// The Wear UI state — mapped from the SHARED WeatherForecast at the ViewModel boundary,
// already trimmed to a glance-length subset (lecture 2, §8).
data class HourRow(val hour: Int, val label: String, val temperature: String)

sealed interface WearForecastUiState {
    data object Loading : WearForecastUiState
    data class Content(val location: String, val hours: List<HourRow>) : WearForecastUiState
    data class Error(val message: String) : WearForecastUiState
}

// TODO 1: Build the Wear screen.
//   - Wrap everything in a Wear Scaffold with timeText = { TimeText() }.
//   - when (state) { Loading -> a centered CircularProgressIndicator;
//                    Content -> a ScalingLazyColumn (see TODO 2);
//                    Error   -> a centered Text(state.message) }
@Composable
fun ForecastWearScreen(state: WearForecastUiState) {
    Scaffold(
        timeText = { TimeText() }
    ) {
        when (state) {
            WearForecastUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is WearForecastUiState.Content -> {
                ForecastList(state)
            }
            is WearForecastUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message)
                }
            }
        }
    }
}

// TODO 2: Build the ScalingLazyColumn.
//   - val listState = rememberScalingLazyListState()
//   - ScalingLazyColumn(state = listState) {
//        item { Text(state.location) }                 // a header item
//        items(state.hours, key = { it.hour }) { row -> // KEYED items (Week 7 lesson)
//            Card(onClick = { }) {
//                Text(row.label); Text(row.temperature)
//            }
//        }
//     }
@Composable
private fun ForecastList(state: WearForecastUiState.Content) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(state = listState) {
        item { Text(state.location) }
        items(state.hours, key = { it.hour }) { row ->
            Card(onClick = { /* open detail in a real app */ }) {
                Text(row.label)
                Text(row.temperature)
            }
        }
    }
}

// A preview helper (lecture 2, §7b) — render on a round Wear spec without an emulator.
// Uncomment if you have the wear tooling-preview dependency:
//
// @androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
// @Composable
// fun ForecastWearScreenPreview() {
//     ForecastWearScreen(
//         WearForecastUiState.Content(
//             location = "Lisbon",
//             hours = listOf(
//                 HourRow(9, "09:00", "18°C"),
//                 HourRow(12, "12:00", "22°C"),
//                 HourRow(15, "15:00", "24°C")
//             )
//         )
//     )
// }

// ============================================================================
// WHY THE WEAR COMPONENTS, NOT PHONE MATERIAL (write before reading):
//
//   The Wear Scaffold, TimeText, ScalingLazyColumn, and Card/Chip are built for the
//   round, tiny, glanceable screen: TimeText curves along the top, ScalingLazyColumn
//   scales/fades items toward the edges and auto-centers, and the components are sized
//   for a fingertip on 1.3 inches. Phone Material components ignore all of that — they
//   render the wrong size and clip on the round bezel. Same Compose runtime and state
//   model (this is literally Week 12's render-by-state); different component layer.
//   (Lecture 2, §2-4.)
// ============================================================================
// HINTS (read only if stuck > 15 min)
// ============================================================================
//
// - "Cannot resolve Scaffold/TimeText/ScalingLazyColumn." You imported the PHONE
//   versions. Use androidx.wear.compose.material3.* and androidx.wear.compose.foundation.lazy.*.
//   This is the #1 Wear mistake (lecture 2, §2).
//
// - "TimeText doesn't show." It goes in the Scaffold's timeText slot, not the content.
//   Scaffold(timeText = { TimeText() }) { /* content */ }.
//
// - "List items don't scale/center." That's ScalingLazyColumn's job and it's automatic
//   — if it's not happening, you used a plain LazyColumn. Use ScalingLazyColumn with a
//   rememberScalingLazyListState().
//
// - "Content clips at the corners." That's the round screen. ScalingLazyColumn +
//   the Scaffold's vignette handle most of it; don't fight it with a phone layout.
//
// - "Should I show all 24 hours?" No — glance length (lecture 2, §1a). Show a few
//   hours, the essentials. The watch is the one-thing-in-two-seconds surface.
// ============================================================================
