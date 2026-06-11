// Exercise 3 — Compose UI test + Roborazzi screenshot, both on the JVM
//
// Goal: Drive a real composition with createComposeRule (find a node by test tag,
//       click it, assert state changed), then capture a screenshot golden per
//       Material 3 state with Roborazzi — all on the JVM via Robolectric, no
//       emulator. This is lecture 2, §2–3, made concrete: the two medium-tier
//       tools that prove a screen renders and reacts, and looks right.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
//   The composable (CheckoutRow / CheckoutScreen) goes in app/src/main. The UI test
//   and the screenshot test go in app/src/test (they run on the JVM under Robolectric).
//   Run the UI test:        ./gradlew :app:testDebugUnitTest
//   Record the goldens:     ./gradlew :app:recordRoborazziDebug
//   Verify on CI:           ./gradlew :app:verifyRoborazziDebug
//
// ACCEPTANCE CRITERIA
//
//   [ ] CheckoutRow has a Modifier.testTag and the +/- buttons have content
//       descriptions; the UI test finds them by tag/description, not brittle text.
//   [ ] The UI test clicks "+" and asserts the displayed quantity went from 1 to 2.
//   [ ] The screenshot test records ONE golden each for Content and Error states
//       (and a stretch dark-theme golden), captured via captureRoboImage.
//   [ ] Everything runs on the JVM (no connectedAndroidTest). Builds 0 warnings.
//
// Build deps:
//   testImplementation("androidx.compose.ui:ui-test-junit4")
//   debugImplementation("androidx.compose.ui:ui-test-manifest")
//   testImplementation("org.robolectric:robolectric:4.13")
//   testImplementation("io.github.takahirom.roborazzi:roborazzi:1.27.0")
//   testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.27.0")
//   plugins { id("io.github.takahirom.roborazzi") }
//   android { testOptions { unitTests { isIncludeAndroidResources = true } } }
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

// ============================================================================
// PRODUCTION COMPOSABLES  —  app/src/main/java/com/crunch/checkout/ui/
// ============================================================================

package com.crunch.checkout.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

data class RowItem(val sku: String, val label: String, val qty: Int)

/** One cart row: a label, a quantity, and +/- steppers. Tagged for stable test finding. */
@Composable
fun CheckoutRow(
    item: RowItem,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.testTag("row-${item.sku}").padding(8.dp)) {
        Text(item.label, modifier = Modifier.weight(1f))
        IconButton(onClick = { onDecrement(item.sku) }) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease ${item.label}")
        }
        Text("${item.qty}", modifier = Modifier.testTag("qty-${item.sku}"))
        IconButton(onClick = { onIncrement(item.sku) }) {
            Icon(Icons.Default.Add, contentDescription = "Increase ${item.label}")
        }
        Spacer(Modifier.weight(0.1f))
    }
}

// CheckoutScreen with the three Material 3 states you'll screenshot.
sealed interface ScreenState {
    data object Loading : ScreenState
    data class Content(val rows: List<RowItem>, val totalLabel: String) : ScreenState
    data class Error(val message: String) : ScreenState
}

@Composable
fun CheckoutScreen(state: ScreenState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        when (state) {
            ScreenState.Loading -> Text("Loading…")
            is ScreenState.Content -> {
                state.rows.forEach { CheckoutRow(it, onIncrement = {}, onDecrement = {}) }
                Text(state.totalLabel, modifier = Modifier.testTag("total"))
            }
            is ScreenState.Error -> Text(state.message, modifier = Modifier.testTag("error"))
        }
    }
}

// ============================================================================
// COMPOSE UI TEST  —  app/src/test/java/com/crunch/checkout/ui/
// ============================================================================
//
// Runs on the JVM via Robolectric (note @RunWith). Same API as on-device.

/*
package com.crunch.checkout.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CheckoutRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    // TODO 1: Drive the stepper.
    //   - setContent a CheckoutRow whose qty is hoisted into a remember { mutableIntStateOf(1) },
    //     with onIncrement = { qty++ } and onDecrement = { qty = (qty - 1).coerceAtLeast(0) }.
    //   - Assert the qty node ("qty-sku-1") reads "1".
    //   - onNodeWithContentDescription("Increase ...").performClick()
    //   - Assert the qty node now reads "2".
    @Test
    fun `tapping increment raises the quantity`() {
        // your code here
    }

    // TODO 2: Decrement never goes below zero.
    //   - Start qty at 0, tap decrement, assert it stays "0" (coerceAtLeast guard).
    @Test
    fun `decrement does not go below zero`() {
        // your code here
    }
}
*/

// ============================================================================
// ROBORAZZI SCREENSHOT TEST  —  app/src/test/java/com/crunch/checkout/ui/
// ============================================================================
//
// One golden per Material 3 state. Run recordRoborazziDebug to write the PNGs
// (commit them), verifyRoborazziDebug on CI to fail on a pixel diff.

/*
package com.crunch.checkout.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-mdpi")   // pin DPI/size for determinism
class CheckoutScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleRows = listOf(
        RowItem("sku-1", "Flat White", qty = 2),
        RowItem("sku-2", "Croissant", qty = 1)
    )

    // TODO 3: Content-state golden.
    //   - setContent { AppTheme { CheckoutScreen(ScreenState.Content(sampleRows, "Total: $9.50")) } }
    //   - composeRule.onRoot().captureRoboImage()
    @Test
    fun `content state golden`() {
        // your code here
    }

    // TODO 4: Error-state golden.
    //   - setContent the Error("Network unavailable") state, capture the root.
    @Test
    fun `error state golden`() {
        // your code here
    }

    // STRETCH TODO 5: a dark-theme content golden (wrap in AppTheme(darkTheme = true)).
}
*/

// ============================================================================
// WHAT EACH TIER PROVES (write before reading):
//
//   - The Compose UI test proves the screen RENDERS and REACTS: the right nodes are
//     present (found by tag/description, not brittle literal text) and a click changes
//     state. It does NOT prove the screen looks right — colors, spacing, theme.
//   - The screenshot test proves the screen LOOKS right: a pixel-faithful golden per
//     state catches the padding/color/font regression no assertion would. It does NOT
//     prove behavior — a frozen wrong-but-pretty screen passes a screenshot test.
//   You need both, at the medium tier, on the JVM. Neither needs a device anymore —
//   that's the pyramid line Compose moved.
// ============================================================================
// HINTS (read only if stuck > 15 min)
// ============================================================================
//
// - "onNodeWithText can't find my button." Icon buttons have no text — find them by
//   contentDescription (onNodeWithContentDescription) or a testTag. That's also why
//   the icons HAVE content descriptions: accessible == testable (lecture 2, §2).
//
// - "Screenshot test: no golden written." Run recordRoborazziDebug FIRST to create the
//   PNGs under src/test/.../roborazzi or build/outputs/roborazzi, commit them, THEN
//   verifyRoborazziDebug compares. The first verify with no golden fails by design.
//
// - "Golden diffs on a teammate's machine." You didn't pin the configuration. The
//   @Config qualifiers ("w400dp-h800dp-mdpi") fix size and DPI so the render is
//   identical across machines. An unpinned golden diffs forever.
//
// - "GraphicsMode error / blank image." Roborazzi needs GraphicsMode.Mode.NATIVE and
//   testOptions { unitTests { isIncludeAndroidResources = true } } in the module's
//   android block, plus the roborazzi-compose dependency.
//
// - "isIncludeAndroidResources missing." Without it, Robolectric can't load resources
//   and your theme/strings render empty. Add it to the module's android block.
// ============================================================================
