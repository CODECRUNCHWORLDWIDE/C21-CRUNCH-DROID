// Exercise 2 — Drag with spring-back (pointerInput + Animatable)
//
// Goal: Detect a horizontal drag with pointerInput, follow the finger with
//       Animatable.snapTo, and on release either spring back to center or animate
//       off-screen to dismiss past a threshold. This is the mini-project's motion
//       core, isolated — the gesture-driven, interruptible animation pattern.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// This composable lives in your `app` module. Run it in the emulator and drag the
// card left/right. The proof is feel: a small drag springs back with a little
// bounce; a big drag flings the card off-screen. Grabbing the card mid-spring
// should interrupt the spring naturally (that's why we use Animatable, not
// animate*AsState).
//
//   1. Drop into app/src/main and show DraggableCard() in your Activity.
//   2. Drag short -> springs back. Drag past threshold -> dismisses.
//   3. Grab it mid-animation -> it follows your finger immediately.
//
// ACCEPTANCE CRITERIA
//
//   [ ] The card follows the finger 1:1 while dragging (snapTo, no lag).
//   [ ] Release under threshold -> springs back to 0 with a spring() spec.
//   [ ] Release over threshold -> animates off-screen and calls onDismiss.
//   [ ] Grabbing mid-animation interrupts it (Animatable cancellation).
//   [ ] The offset is READ in the layout phase (Modifier.offset { }), not in the
//       composable body. Builds with 0 warnings.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.scratch.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

@Composable
fun DraggableCard(onDismiss: () -> Unit = {}) {
    // Animatable holds the horizontal offset. It is interruptible: launching a new
    // snapTo/animateTo cancels any in-flight animation (structured concurrency).
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Past this many pixels of drag, release dismisses instead of springing back.
    val dismissThresholdPx = 400f
    val offscreenPx = 1400f

    Box(
        modifier = Modifier
            // READ the offset in the LAYOUT phase so dragging doesn't recompose (Week 7).
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .size(300.dp, 160.dp)
            .background(Color(0xFF3DDC84), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()                       // don't let a parent also handle it
                        scope.launch {
                            // snapTo follows the finger exactly — no animation, 1:1.
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (abs(offsetX.value) > dismissThresholdPx) {
                                // DISMISS: fling off-screen in the drag direction, then notify.
                                offsetX.animateTo(
                                    targetValue = sign(offsetX.value) * offscreenPx,
                                    animationSpec = tween(durationMillis = 250)
                                )
                                onDismiss()
                            } else {
                                // RETURN: spring back to center with a little bounce.
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        }
                    }
                )
            }
    ) {
        Text("Drag me", Modifier.size(120.dp))
    }
}

// ----------------------------------------------------------------------------
// WHY Animatable and not animateDpAsState (write it before reading):
//
//   The card must do THREE things a declarative animate*AsState can't:
//     1. Follow the finger exactly while dragging (snapTo — instant, no tween).
//     2. animateTo a target on release (spring back OR fling off).
//     3. Be INTERRUPTED: if the user grabs the card mid-spring, the new snapTo
//        cancels the running animateTo and the card follows the finger again.
//   Animatable is a suspend-based, cancellable animation primitive that gives all
//   three. animate*AsState only knows "move toward this target" and can't be
//   driven imperatively or snapped to a finger.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - snapTo vs animateTo: snapTo jumps instantly (use while the finger is down to
//   follow it); animateTo runs an animation over time (use on release).
//
// - Launch from `scope` (rememberCoroutineScope) because the drag callbacks are
//   EVENTS, not composition (Week 8). snapTo/animateTo are suspend functions.
//
// - The "interrupt mid-spring" behavior is automatic: starting a new snapTo on
//   the same Animatable cancels the in-flight animateTo. You get it for free by
//   always launching the drag's snapTo on the same Animatable.
//
// - If the card stutters while dragging, you're probably reading offsetX.value in
//   the composable body somewhere instead of only inside Modifier.offset { }.
//   The ONLY read should be in the offset lambda (the layout phase).
//
// - Tune `dampingRatio`: DampingRatioMediumBouncy overshoots a little (feels
//   alive); DampingRatioNoBouncy settles without overshoot. Try both; pick what
//   feels native. Never use a linear tween for the spring-back — it feels robotic.
//
// ----------------------------------------------------------------------------
