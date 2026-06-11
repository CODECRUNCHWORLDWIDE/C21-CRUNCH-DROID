// Exercise 3 — Semantics + custom action, and a WCAG contrast check
//
// Goal: (A) Add semantics and a custom accessibility action to a gesture-only
//       component so TalkBack can operate it without the swipe. (B) Write a WCAG
//       contrast-ratio function and a unit test that FAILS a bad color pair and
//       PASSES a good one. The accessibility half of the week, made concrete.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// Part A (the composable) lives in app/src/main; verify it by turning ON TalkBack
// and operating the card with the screen reader. Part B (the contrast functions
// + test) is pure JVM — the test runs with `./gradlew :app:test`, no emulator.
//
//   1. Drop Part A into app/src/main; run with TalkBack on.
//   2. Drop Part B's functions into a shared file; move the @Test to app/src/test.
//   3. Verify: TalkBack announces the card and offers the "Dismiss" action;
//      the contrast test fails light-gray-on-white and passes near-black-on-white.
//
// ACCEPTANCE CRITERIA
//
//   [ ] The card has contentDescription + a CustomAccessibilityAction("Dismiss").
//   [ ] With TalkBack ON, you can dismiss the card via its action (no swipe).
//   [ ] mergeDescendants makes the card ONE TalkBack stop.
//   [ ] contrastRatio + passesAA are correct; the test fails a bad pair, passes good.
//   [ ] Builds with 0 warnings.
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package com.crunch.scratch.a11y

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow

// ----------------------------------------------------------------------------
// PART A — make a gesture-only card operable by TalkBack.
// ----------------------------------------------------------------------------

@Composable
fun AccessibleDismissCard(title: String, body: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .size(320.dp, 96.dp)
            .background(CardBackground, RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                // The VISUAL swipe-to-dismiss, for sighted users.
                detectHorizontalDragGestures(onDragEnd = { /* threshold check elided */ }) { change, _ ->
                    change.consume()
                }
            }
            // mergeDescendants = true: the title+body collapse into ONE TalkBack stop,
            // read as one element instead of two separate focus targets.
            .semantics(mergeDescendants = true) {
                contentDescription = "Notification: $title"
                // The custom action TalkBack exposes in its actions menu, so a screen-
                // reader user can dismiss WITHOUT performing the swipe they can't do.
                customActions = listOf(
                    CustomAccessibilityAction(label = "Dismiss notification") {
                        onDismiss()
                        true                     // return true: the action was handled
                    }
                )
            }
    ) {
        Column {
            Text(title, color = CardText, fontSize = 16.sp)    // sp honors font scale
            Text(body, color = CardText, fontSize = 14.sp)
        }
    }
}

// Card colors — Part B's test proves these pass AA.
val CardBackground = Color(0xFF1E1E2E)   // dark
val CardText = Color(0xFFE0E0F0)          // light

// ----------------------------------------------------------------------------
// PART B — the WCAG contrast math (pure, testable).
// ----------------------------------------------------------------------------

/** Relative luminance per WCAG: gamma-correct each channel, then weight by the
 *  human eye's sensitivity (green dominates). Range 0 (black) .. 1 (white). */
fun relativeLuminance(color: Color): Double {
    fun channel(c: Float): Double {
        val s = c.toDouble()
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/** Contrast ratio per WCAG: (lighter + 0.05) / (darker + 0.05). 1:1 .. 21:1. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val light = maxOf(la, lb)
    val dark = minOf(la, lb)
    return (light + 0.05) / (dark + 0.05)
}

/** AA: 4.5:1 for normal text, 3:1 for large text (>=18sp, or >=14sp bold) and UI. */
fun passesAA(foreground: Color, background: Color, largeText: Boolean = false): Boolean =
    contrastRatio(foreground, background) >= if (largeText) 3.0 else 4.5

// ----------------------------------------------------------------------------
// THE TEST — move to app/src/test/java/com/crunch/scratch/a11y/ContrastTest.kt
//
//   import androidx.compose.ui.graphics.Color
//   import kotlin.test.Test
//   import kotlin.test.assertFalse
//   import kotlin.test.assertTrue
//
//   class ContrastTest {
//
//       @Test fun `black on white is maximum contrast`() {
//           val ratio = contrastRatio(Color.Black, Color.White)
//           assertTrue(ratio > 20.9, "black/white should be ~21:1, was $ratio")
//       }
//
//       @Test fun `light gray on white FAILS AA for normal text`() {
//           // #AAAAAA on white is ~2.3:1 — a classic "elegant but illegible" fail.
//           val lightGray = Color(0xFFAAAAAA)
//           assertFalse(passesAA(lightGray, Color.White), "light gray on white must fail AA")
//       }
//
//       @Test fun `the card colors pass AA`() {
//           assertTrue(passesAA(CardText, CardBackground), "card text/bg must pass AA")
//       }
//
//       @Test fun `a mid-gray passes for LARGE text but not normal`() {
//           val gray = Color(0xFF767676)   // ~4.54:1 on white — the AA boundary-ish
//           assertTrue(passesAA(gray, Color.White, largeText = true))   // 3:1 bar -> pass
//           // normal-text 4.5 bar: depends on exact value; compute and assert your finding
//       }
//   }
//
// ----------------------------------------------------------------------------
// WHY the custom action matters (write it before reading):
//
//   TalkBack intercepts swipe gestures for its OWN navigation, so a TalkBack user
//   physically cannot perform your horizontal swipe-to-dismiss. If dismiss is
//   swipe-only, the card is impossible to dismiss for them — the component is
//   broken. A CustomAccessibilityAction registers "Dismiss" in TalkBack's actions
//   menu, giving the screen-reader user an equivalent path to the same operation.
//   EVERY gesture-only interaction needs an equivalent action.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - Turn on TalkBack: emulator Settings > Accessibility > TalkBack (or the
//   volume-key shortcut). Navigate to the card; you should hear the
//   contentDescription. Open the actions menu (swipe up-then-right, or the
//   3-finger menu) to find "Dismiss notification."
//
// - mergeDescendants = true is what makes the card ONE stop. Without it, TalkBack
//   stops on the title and body separately — noisy. With it, one announcement.
//
// - The CustomAccessibilityAction lambda MUST return Boolean (true = handled).
//   Forgetting the return value is a common compile error.
//
// - Color.red/green/blue are already 0..1 floats in Compose, so feed them straight
//   into the gamma function. (If you had 0..255 ints you'd divide by 255 first.)
//
// - Sanity-check your math: contrastRatio(Black, White) must be ~21.0. If you get
//   1.0 you swapped lighter/darker or forgot the +0.05 terms.
//
// ----------------------------------------------------------------------------
