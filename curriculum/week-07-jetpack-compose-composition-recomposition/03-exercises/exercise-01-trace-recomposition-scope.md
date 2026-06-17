# Exercise 1 — Trace a recomposition scope

**Goal.** Stand up the smallest real Compose screen, drop a recomposition counter on each region, *predict* which regions recompose when you tap a button that changes one piece of state, then run it and confirm your prediction. This is lecture 1's recomposition-scope rule made visible — if you can predict the counters before you run, you understand recomposition.

**Estimated time.** 40 minutes.

**Prerequisites.** Android Studio Ladybug+, a Pixel 8 API 35 emulator (any API 24+ emulator works). The Pomodoro mini-project is *not* required — we build a throwaway `Scratch` app so the focus stays on recomposition scope. Set up a new Compose project (the "Empty Activity" template, which wires the Compose BOM and the Compose Compiler plugin for you).

---

## Step 1 — Scaffold a fresh Compose app

In Android Studio: **File ▸ New ▸ New Project ▸ Empty Activity.** Name it `Scratch`, package `com.crunch.scratch`, language **Kotlin**, minSdk **24**. The template wires the Compose BOM and the `org.jetbrains.kotlin.plugin.compose` plugin. Run it once in the emulator and confirm you see "Hello Android!" before touching anything.

## Step 2 — Add the recomposition counter helper

Create `RecompositionCounter.kt`. This modifier tints a border that cycles color on every recomposition of the scope it's attached to, so recomposition becomes *visible*:

```kotlin
package com.crunch.scratch

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A simple mutable box that is NOT Compose state, so mutating it does not itself
 *  trigger recomposition — we only want to OBSERVE recomposition, not cause it. */
private class Ref(var value: Int)

/** Cycles a border color every time the modified scope recomposes. Debug helper only. */
fun Modifier.recompositionCounter(): Modifier = composed {
    val ref = remember { Ref(0) }
    ref.value++                                   // runs once per recomposition of this scope
    val palette = remember {
        listOf(Color.Red, Color.Green, Color.Blue, Color.Magenta, Color(0xFFFFA000))
    }
    this.border(width = 3.dp, color = palette[ref.value % palette.size])
}
```

## Step 3 — Build a screen with three regions

Replace the body of `MainActivity`'s `setContent { }` with a call to `TraceScreen()`, and add:

```kotlin
@Composable
fun TraceScreen() {
    var count by remember { mutableStateOf(0) }

    Column(modifier = Modifier.padding(16.dp)) {
        // Region A: reads `count`. Prediction: ?
        Text(
            text = "Count: $count",
            modifier = Modifier.recompositionCounter().padding(8.dp)
        )

        // Region B: reads NOTHING that changes. Prediction: ?
        StaticHeader(modifier = Modifier.recompositionCounter().padding(8.dp))

        // Region C: the button. Its body reads nothing; its onClick is an event. Prediction: ?
        Button(
            onClick = { count++ },
            modifier = Modifier.recompositionCounter().padding(8.dp)
        ) {
            Text("Increment")
        }
    }
}

@Composable
fun StaticHeader(modifier: Modifier = Modifier) {
    Text(text = "I never change", modifier = modifier)
}
```

Add the imports Android Studio offers (`androidx.compose.foundation.layout.*`, `androidx.compose.material3.*`, `androidx.compose.runtime.*`, `androidx.compose.ui.Modifier`, `androidx.compose.ui.unit.dp`).

## Step 4 — PREDICT before you run

Write your prediction down (in a comment, or on paper) **before** running. For each region, when you tap "Increment":

- **Region A (`Text("Count: $count")`)** — recomposes? Why?
- **Region B (`StaticHeader`)** — recomposes? Why?
- **Region C (the `Button`)** — recomposes? Why?

Recall the rule from lecture 1, §4: a state change recomposes the *nearest enclosing restartable scope that read the state.* Who read `count`?

## Step 5 — Run and confirm

Run the app. Tap "Increment" several times and watch the borders.

**Expected result:**

- **Region A's border cycles color** on every tap — it read `count`, so its scope recomposes.
- **Region B's border does NOT change** — `StaticHeader` read nothing that changed and is skippable, so it is skipped.
- **Region C's border does NOT change** — the `Button`'s body read nothing; `onClick` is an event, not a read.

If Region B's border *does* cycle, something is forcing it to recompose — most likely you read `count` higher up and passed it down. The whole `Column` is inside `TraceScreen`, which reads `count` via `Text`... so why doesn't the *whole* `Column` re-run? Because the runtime restarts the smallest scope that read the state, and `Text` is the reader. That's the lesson.

## Step 6 — Break it on purpose, then understand

Now move the read *up*: change `Text("Count: $count")` to read through a parameter from a non-skippable wrapper. Add an unstable parameter to `StaticHeader`:

```kotlin
@Composable
fun StaticHeader(items: List<String>, modifier: Modifier = Modifier) {   // List = unstable param
    Text(text = "Items: ${items.size}", modifier = modifier)
}
```

and call it `StaticHeader(items = listOf("a", "b"), ...)`. Run again. Now **Region B's border cycles too**, even though `items` is logically constant — because `List` is unstable, `StaticHeader` is not skippable, so the runtime can't prove its input is unchanged and re-invokes it on every recomposition of the parent. That's footgun 1 from lecture 2, seen live. Fix it by making the parameter an `ImmutableList` (next exercise) and watch Region B go still again.

---

## Acceptance criteria

- [ ] A `Scratch` Compose app with `TraceScreen` and the `recompositionCounter()` modifier.
- [ ] You wrote down a prediction for all three regions **before** running.
- [ ] You ran it and confirmed: A recomposes, B and C do not.
- [ ] You added the unstable `List` parameter to `StaticHeader`, saw B start recomposing, and can explain *why* in one sentence (unstable param → not skippable → can't skip).
- [ ] Build with **0 warnings, 0 errors**.

## What you just proved

You proved lecture 1's recomposition-scope rule with your eyes: a state change recomposes the smallest scope that *read* the state, and skippable composables that didn't read it are skipped. And you proved lecture 2's stability rule in reverse: making a parameter unstable (`List`) flips a function out of skippable, so it recomposes for nothing. Every recomposition bug this week is one of these two facts.

---

## Hints (read only if stuck > 10 min)

- **All three borders cycle, even before the `List` change.** You probably attached `recompositionCounter()` to the `Column` instead of each child, or you read `count` in a spot that's inside everyone's scope. Make sure `Text`, `StaticHeader`, and `Button` each have their *own* counter and the `count` read lives in `Text`.
- **No border shows at all.** `composed { }` needs the modifier actually applied — check you wrote `Modifier.recompositionCounter().padding(8.dp)` and not just `Modifier.padding(8.dp)`.
- **Region B doesn't recompose even with the `List` parameter.** With strong skipping, the compiler may still skip if it can prove the *same list instance* is passed. Construct a fresh `listOf(...)` inside the call (a new instance each parent recomposition) to force the issue, or check the report says `unstable items`.
- **Border flickers once on first launch only.** That's the initial composition — every counter ticks from 0 to 1 once. Tap the button to see *recomposition*, not first composition.
