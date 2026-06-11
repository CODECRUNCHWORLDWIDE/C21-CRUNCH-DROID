// Exercise 3 — Defer a state read to the draw phase
//
// Goal: Take an animation that recomposes EVERY frame because it reads its value
//       during composition, then move the read into the DRAW phase so composition
//       never runs per frame. Prove it with a recomposition counter that freezes
//       on the fixed version while the animation keeps running.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// These composables live in your `app` module. Run them in the emulator. The
// proof is visual: the "bad" ring's recomposition border cycles ~60x/second; the
// "good" ring's border is frozen while the ring still sweeps. This is lecture 1,
// §6 (the three phases) made concrete.
//
//   1. Put both composables in app/src/main and call them from an Activity.
//   2. Wrap each in the recompositionCounter() modifier from exercise 1.
//   3. Run. Watch the bad one's border flicker and the good one's stay still.
//
// ACCEPTANCE CRITERIA
//
//   [ ] AnimatedRingBad reads the animating value in composition; its counter
//       border cycles every frame.
//   [ ] AnimatedRingGood reads the animating value in the DRAW phase (drawBehind);
//       its counter border is frozen while the ring animates smoothly.
//   [ ] Builds with 0 warnings.
//   [ ] You can explain which phases run per frame in each version.
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package com.crunch.scratch.ring

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// ----------------------------------------------------------------------------
// BAD — reads `progress.value` in the COMPOSABLE BODY (composition phase).
//        Every animation frame recomposes this whole function: composition +
//        layout + draw all run, 60 times a second, to move one arc.
// ----------------------------------------------------------------------------

@Composable
fun AnimatedRingBad(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ring")
    val progress: State<Float> = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    // THE FOOTGUN: progress.value is read HERE, in composition. Reading the
    // animating State in the composable body re-runs this composable every frame.
    val sweep = 360f * progress.value          // <- read in composition = recompose per frame

    Canvas(modifier = modifier.size(120.dp)) {
        drawArc(
            color = Color.Blue,
            startAngle = -90f,
            sweepAngle = sweep,                // value captured at composition time
            useCenter = false,
            style = Stroke(width = 12.dp.toPx())
        )
    }
}

// ----------------------------------------------------------------------------
// GOOD — reads `progress.value` INSIDE drawBehind (the DRAW phase). Composition
//        and layout never run per frame; only the draw phase re-executes. The
//        recomposition counter on this composable freezes after first composition.
// ----------------------------------------------------------------------------

@Composable
fun AnimatedRingGood(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "ring")
    val progress: State<Float> = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Box(
        modifier = modifier
            .size(120.dp)
            // THE FIX: drawBehind's lambda runs in the DRAW phase. Reading
            // progress.value HERE means only draw re-runs each frame — composition
            // and layout are skipped entirely.
            .drawBehind {
                drawArc(
                    color = Color.Blue,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,   // <- read in draw = no recomposition
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx()),
                    size = Size(size.minDimension, size.minDimension)
                )
            }
    )
}

// A tiny Box stand-in so the file is self-contained without extra imports above.
@Composable
private fun Box(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) { /* drawn entirely by the drawBehind modifier */ }
}

// ----------------------------------------------------------------------------
// HOW TO SEE IT
//
//   Call both with the exercise-1 counter:
//
//     Column {
//         AnimatedRingBad(Modifier.recompositionCounter())   // border flickers ~60x/s
//         AnimatedRingGood(Modifier.recompositionCounter())  // border frozen, ring still sweeps
//     }
//
//   Better still, open the Layout Inspector with "Show Recomposition Counts":
//   AnimatedRingBad's count climbs by ~60 per second; AnimatedRingGood's count
//   stays at 1 (its initial composition) forever while the arc animates.
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
// WHICH PHASES RUN PER FRAME (write it before reading):
//
//   AnimatedRingBad:  COMPOSITION (re-runs the body, reads progress) -> LAYOUT
//                     (Canvas re-measured) -> DRAW. All three, every frame.
//   AnimatedRingGood: only DRAW. progress.value is read inside drawBehind, so the
//                     snapshot system only invalidates the DRAW phase; composition
//                     and layout are never scheduled for the animation.
//
//   The cheapest phase is draw; the most expensive is composition. Read animating
//   values in draw (pure visuals) or layout (position/size), never composition.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - Both borders flicker? You wrapped the wrong thing, or you read progress.value
//   in the body of the "good" one too. The fix is that the ONLY place the good
//   version reads progress.value is inside the drawBehind lambda.
//
// - Lambda-vs-value is the whole trick. `Modifier.offset(x = v.dp)` reads in
//   composition; `Modifier.offset { IntOffset(v.roundToInt(), 0) }` reads in
//   layout; `drawBehind { ... v ... }` reads in draw. Same value, different phase,
//   wildly different cost.
//
// - `rememberInfiniteTransition` drives the value without a coroutine you manage;
//   it schedules the right phase invalidation based on WHERE you read the State.
//   You don't pick the phase by API — you pick it by WHERE you read.
//
// - If the Layout Inspector shows the good one recomposing too, check you didn't
//   accidentally pass `progress.value` (a Float) into the composable instead of
//   `progress` (a State<Float>). Passing the unwrapped value forces a read at the
//   call site, in composition.
//
// ----------------------------------------------------------------------------
