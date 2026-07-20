# Exercise 1 — Modifier order, then a custom flow layout

**Goal.** First, predict-then-confirm a gallery of modifier chains where reordering changes paint, size, and touch target — so the "outside-in pipeline" model is yours, not memorized. Then write a custom flow `Layout` (wrap to the next line when a row fills) from the measure/place contract. This is lecture 1's first half, proven by your eyes and your hands.

**Estimated time.** 45 minutes.

**Prerequisites.** Android Studio Ladybug+, a Pixel 8 API 35 emulator. The `Scratch` Compose app from Week 7/8 works, or a fresh Empty Activity project.

---

## Part A — The modifier reorder gallery (predict, then run)

For each pair below, **write down your prediction** of how the two differ — *before* running — then render both side by side and confirm. Draw each chain as nested boxes (outer-to-inner) to reason about it (lecture 1, §3).

```kotlin
@Composable
fun Gallery() {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {

        // PAIR 1 — padding vs background. Which area is blue?
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.padding(16.dp).background(Color.Blue).size(60.dp))   // A
            Box(Modifier.background(Color.Blue).padding(16.dp).size(60.dp))   // B
        }

        // PAIR 2 — size vs padding. What is each one's TOTAL size?
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(80.dp).padding(10.dp).background(Color.Green))  // C
            Box(Modifier.padding(10.dp).size(80.dp).background(Color.Green))  // D
        }

        // PAIR 3 — clickable vs padding. Which region is tappable?
        var aCount by remember { mutableIntStateOf(0) }
        var bCount by remember { mutableIntStateOf(0) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier
                .clickable { aCount++ }.padding(24.dp)               // E: padding is tappable
                .background(Color.Magenta).size(40.dp))
            Box(Modifier
                .padding(24.dp).clickable { bCount++ }              // F: padding is NOT tappable
                .background(Color.Cyan).size(40.dp))
        }
        Text("E taps (incl. padding): $aCount    F taps (content only): $bCount")

        // PAIR 4 — clip vs background. Circle or rectangle?
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.clip(CircleShape).background(Color.Red).size(60.dp))   // G
            Box(Modifier.background(Color.Red).clip(CircleShape).size(60.dp))   // H
        }
    }
}
```

**Your predictions** (write before running):

- Pair 1: Which is a blue box with a transparent margin, which is blue all the way out?
- Pair 2: What is C's total size? D's total size?
- Pair 3: Tap the *padding* area (not the colored center) of E and F. Which counter increments?
- Pair 4: Which is a red circle, which is a red rectangle?

Run it, tap around, and confirm. The expected results (don't peek until you've predicted):

- **Pair 1:** A is a 60dp blue box with 16dp transparent margin (padding *then* background → blue inside the padding). B is blue out to the edges with the padding eating *into* the blue (background *then* padding).
- **Pair 2:** C is **80dp** total (size then padding → padding eats inward, content is 60dp). D is **100dp** total (padding then size → 10dp margin + 80dp box).
- **Pair 3:** Tapping the padding of **E increments** (clickable before padding → the padded region is in the touch target). Tapping the padding of **F does nothing** (padding before clickable → only the 40dp center is tappable). This is the touch-target lesson — and an accessibility one.
- **Pair 4:** G is a red **circle** (clip before background → background clipped to the circle). H is a red **rectangle** (background painted as a rect first; the later clip only affects subsequent drawing).

Write the one-sentence rule you derived in a comment: *each modifier wraps the rest of the chain; the chain applies outside-in.*

## Part B — A custom flow layout

Now build a `FlowLayout` that places children left-to-right, wrapping to a new line when the next child won't fit. Implement the measure/place contract from scratch (lecture 1, §1–2).

```kotlin
@Composable
fun FlowLayout(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 8.dp,
    verticalGap: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        val hGap = horizontalGap.roundToPx()
        val vGap = verticalGap.roundToPx()

        // STEP 2: measure each child once, loosely (it can be its natural size).
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }

        // STEP 3 + 4: walk children, wrapping rows. Track x within a row and the
        // running y. When a child won't fit in maxWidth, start a new row.
        val maxWidth = constraints.maxWidth
        // TODO 1: compute row breaks and total height, then place each child.
        //   - keep `x`, `y`, `rowHeight`
        //   - if x + child.width > maxWidth, wrap: x = 0, y += rowHeight + vGap, rowHeight = 0
        //   - record each child's placement position
        //   - total width = maxWidth (or the widest row), total height = final y + rowHeight

        // (Fill in the loop; the structure below is the placement block.)
        val positions = mutableListOf<IntOffset>()
        var x = 0
        var y = 0
        var rowHeight = 0
        for (placeable in placeables) {
            if (x > 0 && x + placeable.width > maxWidth) {   // wrap
                x = 0
                y += rowHeight + vGap
                rowHeight = 0
            }
            positions.add(IntOffset(x, y))
            x += placeable.width + hGap
            rowHeight = maxOf(rowHeight, placeable.height)
        }
        val totalHeight = (y + rowHeight).coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(width = maxWidth, height = totalHeight) {
            placeables.forEachIndexed { i, placeable ->
                placeable.placeRelative(positions[i])     // RTL-aware placement
            }
        }
    }
}

// Try it with chips:
@Composable
fun ChipDemo() {
    FlowLayout(Modifier.fillMaxWidth().padding(16.dp)) {
        listOf("kotlin", "compose", "coroutines", "flow", "snapshots", "layout",
               "gestures", "animation", "accessibility").forEach { tag ->
            Text(
                text = "#$tag",
                modifier = Modifier
                    .background(Color.LightGray, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
```

Run `ChipDemo`. The chips should flow across and wrap to new lines as the width fills. Resize (rotate, or split-screen) and confirm they re-wrap.

---

## Acceptance criteria

- [ ] You wrote predictions for all four modifier pairs **before** running, and confirmed each.
- [ ] You can state the outside-in rule in one sentence (each modifier wraps the rest of the chain).
- [ ] `FlowLayout` measures each child **once**, wraps rows at `maxWidth`, and respects the incoming constraints (`coerceIn` on height).
- [ ] `ChipDemo` flows and wraps; chips re-wrap when the width changes.
- [ ] `placeRelative` is used (RTL-aware), not `place`.
- [ ] Build with **0 warnings, 0 errors**.

## What you just proved

You proved lecture 1's two foundations: the **modifier chain is an ordered outside-in pipeline** (order changes paint, size, and — crucially — touch target), and a **custom `Layout` is the measure/place contract** you implement yourself (measure each child once, decide your size within constraints, place each child). Every built-in layout is this contract; now you can build any arrangement the built-ins don't offer.

---

## Hints (read only if stuck > 10 min)

- **`Layout` content lambda gives `(measurables, constraints)`.** `measurables` are un-measured children; call `.measure(constraints)` on each (once!) to get `Placeable`s. The trailing `layout(w, h) { ... }` is the placement block.
- **Measuring a child twice crashes.** Map over `measurables` once into `placeables` and reuse those — don't re-measure inside the placement block.
- **Flow doesn't wrap.** Check the wrap condition: wrap when `x > 0 && x + child.width > maxWidth`. The `x > 0` guard avoids wrapping before placing the first (possibly over-wide) child.
- **Pair 3's padding isn't tappable in E.** It should be — `clickable` before `padding` means the padded region is in the hit area. If both behave the same, you may have the same chain twice; double-check the order differs.
