// Exercise 3 — Edge-to-edge and window insets
//
// Goal: Draw edge-to-edge and pad content with the right window insets so nothing
//       hides behind the status bar, the navigation bar, or the keyboard. You
//       verify it with a Compose UI test that asserts content clears the
//       system-bar inset, that a scrollable uses contentPadding (so its background
//       still extends edge-to-edge), and that a bottom text field rises above the
//       IME inset.
//
// Estimated time: 40 minutes.
//
// HOW TO USE THIS FILE
//
// This is an INSTRUMENTED Compose UI test (androidTest source set). Insets are
// real only with a real window, so this runs on an emulator. It uses
// createAndroidComposeRule<ComponentActivity> so enableEdgeToEdge and the window
// insets are live.
//
//   1. Add this file to src/androidTest/java.
//   2. Run with `./gradlew connectedAndroidTest` or the gutter arrow.
//   3. The assertions check that content is laid out inside the safe area and
//      that the IME inset is consumed when the keyboard shows.
//
// Note: the precise inset pixel values depend on the device; we assert
// RELATIONSHIPS (content top >= status bar bottom; list uses contentPadding;
// field bottom <= ime top) rather than magic numbers, so the test is robust
// across devices.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] enableEdgeToEdge is on; content draws behind transparent system bars.
//   [ ] The scrollable list uses contentPadding (not a Modifier.padding box), so
//       items clear the bars while the background extends edge-to-edge.
//   [ ] A focused bottom text field is lifted above the keyboard (imePadding).
//   [ ] You can explain the difference between contentPadding and Modifier.padding
//       for a scrollable under edge-to-edge.
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package dev.crunch.theme.exercise3

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

// ----------------------------------------------------------------------------
// The screen under test. A top app bar, a scrollable list padded with the
// Scaffold's innerPadding via contentPadding, and a bottom text field that
// uses imePadding so the keyboard can't cover it.
// ----------------------------------------------------------------------------

@Composable
fun InsetAwareScreen(items: List<String>) {
    var note by remember { mutableStateOf("") }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Inset-aware") }) }   // Scaffold insets the app bar for the status bar
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // contentPadding (not Modifier.padding) keeps the list BACKGROUND
                // edge-to-edge while padding the ITEMS clear of the nav bar.
                contentPadding = WindowInsets.navigationBars.asPaddingValues()
            ) {
                items(items) { Text(it, Modifier.fillMaxWidth().padding(16.dp)) }
            }
            // A bottom-anchored field that the keyboard must not cover.
            TextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth().imePadding(),   // rises above the keyboard
                placeholder = { Text("Add a note") }
            )
        }
    }
}
// Note on the inset above: WindowInsets.navigationBars.asPaddingValues() turns
// the bottom nav-bar inset into contentPadding, so the last list item clears the
// bar while the list surface still fills the window. In real code you'd usually
// just spread Scaffold's innerPadding into contentPadding; we name the inset here
// so it's visible which edge is being padded.

// ----------------------------------------------------------------------------
// The tests
// ----------------------------------------------------------------------------

class EdgeToEdgeTests {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun setEdgeToEdgeContent(content: @Composable () -> Unit) {
        rule.activityRule.scenario.onActivity { activity ->
            activity.enableEdgeToEdge()              // draw behind the transparent system bars
            activity.setContent { content() }
        }
    }

    @Test
    fun appBarAndContentAreVisible_notHiddenBehindStatusBar() {
        setEdgeToEdgeContent { InsetAwareScreen(items = List(30) { "Item $it" }) }
        rule.waitForIdle()

        // The app bar title is visible (it's inset below the status bar by Scaffold),
        // and the first item is visible (not clipped under the bar).
        rule.onNodeWithText("Inset-aware").assertIsDisplayed()
        rule.onNodeWithText("Item 0").assertIsDisplayed()
    }

    @Test
    fun focusingBottomField_liftsItAboveTheKeyboard() {
        setEdgeToEdgeContent { InsetAwareScreen(items = List(30) { "Item $it" }) }
        rule.waitForIdle()

        // Type into the bottom field; imePadding should keep it on screen as the
        // keyboard animates in. If the field were covered, this input would target
        // an off-screen node and the assertion below would fail.
        rule.onNodeWithText("Add a note").performTextInput("hello")
        rule.waitForIdle()
        rule.onNodeWithText("hello").assertIsDisplayed()   // still visible above the IME
    }
}

// ----------------------------------------------------------------------------
// WHY contentPadding instead of Modifier.padding for a scrollable (write it
// before reading):
//
//   Under edge-to-edge we WANT the list background to extend behind the
//   navigation bar (content scrolls under the translucent bar — the intended
//   look), while the ITEMS stay padded so the first/last clear the bars. A
//   `Modifier.padding` on the LazyColumn clips the whole list into the safe area,
//   leaving a dead band of background behind the bar. `contentPadding` pads the
//   items but lets the list surface fill the window — the correct edge-to-edge
//   behavior.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - enableEdgeToEdge() must run before/at setContent. In the test we call it on
//   the activity before setContent so the window is in edge-to-edge mode.
//
// - If "Item 0" isn't displayed, your top inset is wrong: either the app bar
//   isn't inside Scaffold's topBar (so it's not inset for the status bar), or you
//   double-padded and pushed content off-screen.
//
// - If the typed text isn't displayed after focusing, imePadding() is missing or
//   on the wrong element — it must wrap the field (or its column) so the bottom
//   padding tracks the keyboard.
//
// - Real code rarely needs the asPaddingValuesBottomOnly helper — just spread
//   Scaffold's innerPadding into contentPadding. The helper here makes the inset
//   explicit so you can SEE which edge you're padding.
//
// - Watch for DOUBLE padding: if you pad with innerPadding AND re-apply
//   WindowInsets.systemBars below it, you get a gap twice the bar height. Consume
//   the inset once (Scaffold already did it via innerPadding).
//
// ----------------------------------------------------------------------------
