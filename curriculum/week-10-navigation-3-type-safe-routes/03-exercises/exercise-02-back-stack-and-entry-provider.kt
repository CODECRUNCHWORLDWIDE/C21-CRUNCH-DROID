// Exercise 2 — The app-owned back stack and the entry provider
//
// Goal: Prove that Navigation 3 navigation is just state mutation you can test.
//       You drive an app-owned back stack and an entryProvider through a Compose
//       UI test, asserting the rendered screen after each typed navigation and
//       after a back. No NavController, no string routes — the back stack is a
//       SnapshotStateList you own, and the test reads it like any other state.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// This is an INSTRUMENTED Compose UI test (androidTest source set). It uses
// createComposeRule from androidx.compose.ui.test.junit4 and the Compose test
// finders/actions. Drop it into src/androidTest/java of a project that has the
// Navigation 3 artifacts. It builds its own back stack and entryProvider, so it
// needs no app code beyond the screens it declares inline.
//
//   1. Add this file to src/androidTest.
//   2. Run with the green gutter arrow, or `./gradlew connectedAndroidTest`.
//   3. Read the assertions: each typed navigation shows the right screen; back
//      pops to the previous one; the back stack contents match expectations.
//
// If you prefer Robolectric (JVM, no emulator), the same code runs in the `test`
// source set with @RunWith(RobolectricTestRunner::class) and createComposeRule()
// — Robolectric provides the Android runtime on the JVM.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] All tests pass: forward navigation shows the destination, back returns
//       to the source, and the back stack list matches after each step.
//   [ ] You can explain, in one sentence, why navigation logic here is testable
//       WITHOUT a NavController.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package dev.crunch.nav3.exercise2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

// ----------------------------------------------------------------------------
// The graph under test — a sealed family of @Serializable routes.
// ----------------------------------------------------------------------------

sealed interface Route : NavKey

@Serializable data object Home : Route
@Serializable data class Detail(val itemId: Int) : Route
@Serializable data object Settings : Route

// ----------------------------------------------------------------------------
// The app under test. We hoist the back stack so the test can both DRIVE it
// (via the UI) and READ it (asserting its contents). In a real app the back
// stack is created with rememberNavBackStack inside the composable; here we
// pass it in so the test owns it directly.
// ----------------------------------------------------------------------------

@Composable
fun TestApp(backStack: SnapshotStateList<NavKey>) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Home> {
                Column(Modifier.fillMaxSize()) {
                    Text("HOME")
                    Button(onClick = { backStack.add(Detail(itemId = 42)) }) { Text("Open 42") }
                    Button(onClick = { backStack.add(Settings) }) { Text("Go settings") }
                }
            }
            entry<Detail> { route ->
                Column(Modifier.fillMaxSize()) {
                    Text("DETAIL ${route.itemId}")
                    Button(onClick = { backStack.removeLastOrNull() }) { Text("Back") }
                }
            }
            entry<Settings> {
                Column(Modifier.fillMaxSize()) {
                    Text("SETTINGS")
                    Button(onClick = { backStack.removeLastOrNull() }) { Text("Back") }
                }
            }
        }
    )
}

// ----------------------------------------------------------------------------
// The tests
// ----------------------------------------------------------------------------

class BackStackTests {

    @get:Rule val rule = createComposeRule()

    @Test
    fun startsOnHome() {
        val backStack = mutableStateListOf<NavKey>(Home)
        rule.setContent { TestApp(remember { backStack }) }

        rule.onNodeWithText("HOME").assertIsDisplayed()
        assertEquals(listOf<NavKey>(Home), backStack.toList())
    }

    @Test
    fun openingItem_pushesDetail_andRendersIt() {
        val backStack = mutableStateListOf<NavKey>(Home)
        rule.setContent { TestApp(remember { backStack }) }

        rule.onNodeWithText("Open 42").performClick()
        rule.waitForIdle()

        // The rendered screen is the new top...
        rule.onNodeWithText("DETAIL 42").assertIsDisplayed()
        // ...and the back stack is exactly [Home, Detail(42)].
        assertEquals(listOf<NavKey>(Home, Detail(42)), backStack.toList())
    }

    @Test
    fun back_popsToTheSourceScreen() {
        val backStack = mutableStateListOf<NavKey>(Home)
        rule.setContent { TestApp(remember { backStack }) }

        rule.onNodeWithText("Open 42").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Back").performClick()
        rule.waitForIdle()

        // We're back on Home; Detail is gone from both the screen and the stack.
        rule.onNodeWithText("HOME").assertIsDisplayed()
        rule.onNodeWithText("DETAIL 42").assertDoesNotExist()
        assertEquals(listOf<NavKey>(Home), backStack.toList())
    }

    @Test
    fun navigatingToSettings_thenBack_keepsHistoryCorrect() {
        val backStack = mutableStateListOf<NavKey>(Home)
        rule.setContent { TestApp(remember { backStack }) }

        rule.onNodeWithText("Go settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("SETTINGS").assertIsDisplayed()
        assertEquals(listOf<NavKey>(Home, Settings), backStack.toList())

        rule.onNodeWithText("Back").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("HOME").assertIsDisplayed()
        assertEquals(listOf<NavKey>(Home), backStack.toList())
    }

    @Test
    fun typedArguments_arriveTyped() {
        // Two distinct Detail entries are distinct back-stack entries because the
        // argument is part of the route's identity (data class equality).
        val a = Detail(itemId = 1)
        val b = Detail(itemId = 2)
        assertEquals(Detail(1), a)        // value equality on the argument
        assertEquals(false, a == b)       // different argument -> different route
    }
}

// ----------------------------------------------------------------------------
// WHY this is testable without a NavController (write it before reading):
//
//   The back stack is a SnapshotStateList<NavKey> the app owns — plain Compose
//   state. Navigating forward is `add`, back is `removeLastOrNull`; the test can
//   both drive those (by clicking the buttons that call them) and READ the list
//   directly to assert the resulting history. There is no opaque controller to
//   mock: the navigation state IS the data structure, so the test inspects it
//   like any other piece of state.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - assertDoesNotExist() vs assertIsNotDisplayed(): during a transition both
//   screens can briefly exist. Assert on the DESTINATION being displayed and the
//   SOURCE not existing, AFTER rule.waitForIdle(), so a half-animated frame can't
//   flake the test.
//
// - If a forward-navigation assertion flakes, you almost certainly forgot
//   rule.waitForIdle() after performClick(). The add() triggers recomposition and
//   a transition; assert after it settles.
//
// - `remember { backStack }` keeps the SAME list instance across recompositions
//   so the test and the UI share it. Without remember, a recomposition could hand
//   the composable a fresh list and your assertions would read a different object.
//
// - On Robolectric: annotate the class @RunWith(RobolectricTestRunner::class),
//   put the file in src/test (not androidTest), and add a @Config(sdk = [34]) if
//   the default SDK level isn't installed. The Compose test APIs are identical.
//
// - The data-class equality test (typedArguments_arriveTyped) is the whole reason
//   Detail(1) and Detail(2) are different entries: route identity = data class
//   equality. This is what lets the back stack tell two details apart.
//
// ----------------------------------------------------------------------------
