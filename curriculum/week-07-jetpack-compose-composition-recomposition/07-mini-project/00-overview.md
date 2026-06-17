# Mini-Project — Pomodoro: a pure-Compose timer that recomposes the minimum

This week you build a **Pomodoro timer** entirely in Compose — no XML, no `View`, no Material 3 yet (that's Week 11), just `Box`, `Text`, `Canvas`, and the runtime you learned this week. The timer runs a 25-minute work interval and a 5-minute break, shows a **circular progress ring** that sweeps as time elapses, ticks an animated per-second pulse, and — the part that makes the lesson land — carries a **debug recomposition-counter overlay** so you can *watch* which parts of the screen recompose as the timer runs.

The point of the project is not "build a timer." It's to build a timer two ways: a naive version that recomposes the entire screen every second (you'll see the counters spin), and a deferred-read version that animates the ring at *zero* recomposition cost by reading the elapsed value in the draw phase. That before/after — "the whole screen recomposed every tick; now only draw runs" — is the senior instinct this week installs.

This is a *fresh* app, not a continuation (Compose is new this week). You start from an Empty Activity Compose project and build up. The navigation, persistence, and architecture come in later weeks; this week is the runtime, alone, doing its job well.

---

## Where you're starting from

An Empty Activity Compose project (the Android Studio template), which gives you:

- An `Activity` with `setContent { }`.
- The Compose BOM and the `org.jetbrains.kotlin.plugin.compose` plugin wired.
- A default theme you'll mostly ignore this week.

If you don't have one, create it: **File ▸ New ▸ New Project ▸ Empty Activity**, Kotlin, minSdk 24, package `com.crunch.pomodoro`.

## What you're building toward

By the end you have:

- A `PomodoroScreen` composable showing a circular ring, a `MM:SS` countdown, and start/pause/reset controls.
- A timer driven by a coroutine in the composition (`LaunchedEffect`-shaped, used as a black box this week — Week 8 opens it).
- The ring animating **in the draw phase** so its sweep costs zero recompositions.
- A debug-only **recomposition-counter overlay** on each region, proving the deferral works.
- A Compose Compiler report showing every composable on the hot path is `skippable`.
- A short clip or screenshot sequence in your README showing the counters: spinning on the naive version, frozen on the fixed one.

---

## Milestone 1 — Model the timer state (≈ 1 h)

Define the UI state as an **immutable** type (lecture 2: `val`s, stable). Keep the elapsed value separate from the structural state, because they invalidate different phases.

```kotlin
import androidx.compose.runtime.Immutable

enum class TimerPhase { Work, Break, Idle }

// Structural state: changes rarely (start, pause, phase switch). Read in composition.
@Immutable
data class TimerUiState(
    val phase: TimerPhase = TimerPhase.Idle,
    val isRunning: Boolean = false,
    val totalSeconds: Int = 25 * 60
)
```

Notice `elapsedSeconds` is **not** in this type. The countdown text needs it (composition), but the ring's sweep does not need to recompose — it needs the value in *draw*. We hold the fast-changing elapsed value in its own `State<Float>` and read it in the phase that needs it, so a tick that only advances the ring never recomposes the structural UI. This separation is the whole project.

Decisions you must be able to defend in review:

- **Why is `TimerUiState` `@Immutable` with all `val`s?** So every composable taking it is `skippable` (lecture 2). When the structural state hasn't changed, the runtime skips those composables even as the ring animates.
- **Why keep `elapsed` out of `TimerUiState`?** Because it changes ~60 times a second for a smooth ring. If it lived in the structural state, every tick would recompose everything that reads that state. We isolate the fast value and read it late (draw).

## Milestone 2 — Drive the clock (≈ 1.5 h)

Run a coroutine that advances elapsed time while running. Use `LaunchedEffect` as a black box this week (Week 8 explains it) — it launches a coroutine tied to the composition and cancels it when the key changes or the composable leaves.

```kotlin
@Composable
fun rememberTimerController(state: TimerUiState): State<Float> {
    // progress in [0f, 1f]; updated by a coroutine while running. Held as State so
    // reads of it can be deferred to draw.
    val progress = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.isRunning, state.totalSeconds) {
        if (!state.isRunning) return@LaunchedEffect
        val start = System.nanoTime()
        val totalNanos = state.totalSeconds.toLong() * 1_000_000_000L
        while (true) {
            withFrameNanos { frameTime ->          // tick once per frame, in sync with draw
                val elapsed = frameTime - start
                progress.floatValue = (elapsed.toFloat() / totalNanos).coerceIn(0f, 1f)
            }
            if (progress.floatValue >= 1f) break
        }
    }
    return progress
}
```

`withFrameNanos` ties the update to the frame clock — you advance the value exactly when Compose is about to draw a frame, which is the natural cadence for an animation. (You could also use `rememberInfiniteTransition`/`animateFloatAsState` from exercise 3; both are fine. The point is the value lives in a `State` you read in draw.)

## Milestone 3 — Draw the ring (read elapsed in DRAW) (≈ 1.5 h)

The ring is a `Canvas` (or a `drawBehind` modifier) that reads `progress.value` *inside the draw lambda*, so advancing it never recomposes:

```kotlin
@Composable
fun ProgressRing(progress: State<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(240.dp)) {
        val stroke = 16.dp.toPx()
        val inset = stroke / 2
        // Track (full circle, faint).
        drawArc(
            color = Color.LightGray,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke)
        )
        // Progress arc: reads progress.value HERE, in the draw phase. The arc
        // re-draws every frame; composition and layout never re-run for it.
        drawArc(
            color = Color(0xFF3DDC84),                 // Android green
            startAngle = -90f,
            sweepAngle = 360f * progress.value,        // <- read in DRAW = zero recomposition
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}
```

This is the crux. `progress.value` is read only inside the `Canvas` draw scope, so the snapshot system invalidates only the **draw** phase when it changes. The `ProgressRing` composable itself does not recompose as the ring sweeps. Confirm it with the counter overlay in Milestone 5.

## Milestone 4 — The countdown text and controls (≈ 1 h)

The `MM:SS` text *does* need to recompose — but only when the displayed second changes, not every frame. Derive the integer seconds and let the text recompose once a second:

```kotlin
@Composable
fun CountdownText(progress: State<Float>, totalSeconds: Int, modifier: Modifier = Modifier) {
    // remaining whole seconds, derived. Recomposes only when the INT changes
    // (once a second), not on every fractional progress tick.
    val remaining by remember(totalSeconds) {
        derivedStateOf {
            (totalSeconds * (1f - progress.value)).toInt().coerceAtLeast(0)
        }
    }
    val mm = remaining / 60
    val ss = remaining % 60
    Text(
        text = "%02d:%02d".format(mm, ss),
        fontSize = 48.sp,
        modifier = modifier
    )
}
```

`derivedStateOf` (a black box this week; Week 8 explains it) is the right tool: it reads `progress.value` but only notifies its readers when the *derived integer* changes, so the text recomposes once a second instead of every frame. The ring (Milestone 3) animates in draw at 60fps; the text recomposes 1x/second; the structural UI recomposes ~never. Three different cadences, each in the right phase.

Add the controls (start/pause/reset) as plain `Button`s that flip `isRunning` / reset `progress`. These mutate the structural state and recompose only the buttons.

## Milestone 5 — The recomposition-counter overlay (≈ 1 h)

Reuse the `recompositionCounter()` modifier from exercise 1 (the color-cycling border). Put it on each region in **debug builds only**:

```kotlin
@Composable
fun PomodoroScreen(state: TimerUiState) {
    val progress = rememberTimerController(state)
    val debug = BuildConfig.DEBUG     // overlay only in debug builds

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            ProgressRing(progress, Modifier.then(if (debug) Modifier.recompositionCounter() else Modifier))
            CountdownText(progress, state.totalSeconds,
                Modifier.then(if (debug) Modifier.recompositionCounter() else Modifier))
        }
        Spacer(Modifier.height(24.dp))
        TimerControls(state, Modifier.then(if (debug) Modifier.recompositionCounter() else Modifier))
    }
}
```

Run it. **Watch the borders:**

- The **ring's** border is frozen — it never recomposes; only its draw phase runs as the arc sweeps.
- The **countdown text's** border cycles once per second — it recomposes when the displayed second changes.
- The **controls'** border cycles only when you tap start/pause/reset.

If the ring's border cycles every frame, you read `progress.value` somewhere in composition instead of only in the `Canvas` draw scope — go find the leaked read.

## Milestone 6 — The naive version, then the report (≈ 0.5 h)

To *feel* the win, write the naive ring once: read `progress.value` in the composable body and pass the sweep angle in as a parameter (exercise 3's `AnimatedRingBad`). Run it with the overlay — the ring's border now cycles every frame, and the Layout Inspector shows its recomposition count climbing ~60/s. Then delete it and keep the deferred version. Finally, turn on the Compose Compiler report (`reportsDestination`) and confirm `PomodoroScreen`, `ProgressRing`, `CountdownText`, and `TimerControls` are all `restartable skippable` and `TimerUiState` is `stable`. Record the report excerpt and the before/after counter behaviour in your README.

---

## Acceptance criteria

- [ ] `TimerUiState` is `@Immutable` with `val`s only; the report marks it `stable`.
- [ ] The fast-changing `progress` value lives in its own `State<Float>`, separate from the structural state.
- [ ] `ProgressRing` reads `progress.value` **only inside the draw scope** (`Canvas`/`drawBehind`); its recomposition count stays at 1 while the ring animates.
- [ ] `CountdownText` recomposes **once per second** (via `derivedStateOf`), not every frame.
- [ ] Start/pause/reset controls work and recompose only on tap.
- [ ] A debug-only recomposition-counter overlay is present and demonstrates: ring frozen, text 1x/s, controls on-tap.
- [ ] The Compose Compiler report shows every hot-path composable `skippable`.
- [ ] A short clip or screenshot sequence in the README shows the counter behaviour (naive: ring border spins; fixed: ring border frozen).
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **Work/break cycling.** After the work interval completes, auto-switch to a 5-minute break with a different ring color, then back. Keep the phase switch in the structural state so it recomposes once, not per frame.
- **Animated tick pulse.** Add a subtle scale pulse on the countdown text each second using `animateFloatAsState` — and read the scale in a `graphicsLayer { }` lambda (layout/draw phase) so the pulse doesn't recompose the text. Prove it with the counter.
- **Session count.** Persist nothing yet (that's Phase III), but hold a `remember`ed count of completed pomodoros in the structural state and show it; confirm it recomposes only when a session completes.
- **Read the report into CI.** Add a Gradle task or a tiny check that fails the build if a known-hot composable loses `skippable` (tivi does exactly this — see resources). A regression guard for free.

## What this milestone earns you

You can now reason about the Compose runtime well enough to build a screen with three different update cadences — a 60fps draw-only animation, a 1Hz text recomposition, and an on-demand structural recomposition — each placed in the correct phase, and *prove* it with the Compiler report and a recomposition counter. That is the literal "skill earned" line for the week: reading the Compose Compiler report, diagnosing unnecessary recomposition, and writing stable parameters by intent. Week 8 opens the box this project treated as opaque — `remember`, `mutableStateOf`, `LaunchedEffect`, `derivedStateOf`, `snapshotFlow` — and you'll be glad you can already say which scope recomposes and which phase a read belongs in before you learn *why* each side-effect API is keyed to its lifecycle hook.
