# Challenge 1 — Audit a theme for contrast, then fix the failing pair (with the math)

**Time.** 60–120 minutes.
**Deliverable.** A short report (`CONTRAST.md`) with the per-pair ratios for light and dark, the failing pair identified, the fix (which tone moved and to what), the new ratios, and the contrast-audit code — committed to your Week 11 repo.

## The premise

"The dark theme looks a bit washed out" is a real bug report, and "looks fine to me" is not an engineering response. Every designer-handed or auto-generated palette has at least one role pair that, when you actually *measure* it, falls below the WCAG threshold — most often a secondary-text pair (`onSurfaceVariant` on `surfaceVariant`) in dark mode, or a container pair you created. The skill this challenge builds is not "know contrast matters." It is: **measure every important pair, find the one that fails, fix it by moving a tone, and prove the fix with a number.** A palette you eyeballed is a palette you haven't audited.

## What to build

### Step 1 — A theme to audit (weakened on purpose)

Start from your exercise-1 theme, but introduce a realistic contrast failure so there's something to find. The classic: a `surfaceVariant` and its `onSurfaceVariant` that are too close in dark mode (secondary text on a card).

```kotlin
val DarkColors = darkColorScheme(
    surface = Color(0xFF191113),
    onSurface = Color(0xFFEFDFE1),
    surfaceVariant = Color(0xFF514347),
    onSurfaceVariant = Color(0xFF8A7B7F),   // <- DELIBERATELY too dim on surfaceVariant
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF54122C),
    // …rest of the roles…
)
```

If you'd rather audit your real theme, do — the generated palettes from Material Theme Builder usually pass the standard `on*`-on-base pairs but can fail on *non-standard* pairings you introduce. Either way, the point is to find a real failure.

### Step 2 — The contrast audit code

Implement the WCAG contrast ratio and an audit over the pairs that matter. This is reusable; keep it in a test utility.

```kotlin
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

fun Color.relativeLuminance(): Double {
    fun lin(c: Float): Double {
        val s = c.toDouble()
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * lin(red) + 0.7152 * lin(green) + 0.0722 * lin(blue)
}

fun contrastRatio(a: Color, b: Color): Double {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

data class PairAudit(val name: String, val ratio: Double, val threshold: Double) {
    val passes get() = ratio >= threshold
}

fun auditScheme(scheme: ColorScheme): List<PairAudit> = listOf(
    PairAudit("onSurface / surface", contrastRatio(scheme.onSurface, scheme.surface), 4.5),
    PairAudit("onSurfaceVariant / surfaceVariant", contrastRatio(scheme.onSurfaceVariant, scheme.surfaceVariant), 4.5),
    PairAudit("onPrimary / primary", contrastRatio(scheme.onPrimary, scheme.primary), 4.5),
    PairAudit("onPrimaryContainer / primaryContainer", contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer), 4.5),
    PairAudit("onError / error", contrastRatio(scheme.onError, scheme.error), 4.5),
    PairAudit("outline / surface (UI)", contrastRatio(scheme.outline, scheme.surface), 3.0)   // UI/large -> 3:1
)
```

### Step 3 — Run the audit, find the failure

Write a test that prints every pair and asserts the body-text ones clear 4.5:1. It should FAIL on the weakened pair — that failure is your "before" evidence:

```kotlin
@Test
fun darkThemeMeetsWcag() {
    val failures = auditScheme(DarkColors).filterNot { it.passes }
    failures.forEach { println("FAIL ${it.name}: ${"%.2f".format(it.ratio)} < ${it.threshold}") }
    assertTrue("Contrast failures: ${failures.map { it.name }}", failures.isEmpty())
}
```

Run it. It prints something like `FAIL onSurfaceVariant / surfaceVariant: 3.81 < 4.5`. Record the number — that's a real, measured accessibility bug.

### Step 4 — Fix it by moving a tone

The fix is *not* to abandon roles or hardcode a color on the component. It's to move a tonal value: lighten `onSurfaceVariant` (more contrast against the dark `surfaceVariant`) until the ratio clears 4.5:1. Regenerate from Material Theme Builder with an adjusted source, or nudge the specific role and re-measure:

```kotlin
onSurfaceVariant = Color(0xFFD5C2C6),   // lighter tone -> more contrast on surfaceVariant
```

Re-run the test. It should now print the new ratio (e.g. `4.61`) and pass. You moved one tone; the role model stayed intact; every component reading `onSurfaceVariant` is now legible.

### Step 5 (optional, for the stretch) — audit the dynamic path's structure

You can't audit a specific dynamic palette (it's per-wallpaper), but you *can* verify the structural guarantee: M3 maintains `on*`-on-base contrast for dynamic schemes. Generate a couple of dynamic schemes on-device (different wallpapers), dump the role values (a debug overlay that prints `MaterialTheme.colorScheme.onSurface` etc.), and run your audit on those captured values. Confirm the standard pairs hold and note where a custom pair you introduced doesn't get the same guarantee — that's the lesson: M3 guarantees its pairs, not yours.

## Acceptance criteria

- [ ] A theme with at least one real, measured contrast failure (weakened pair or a real one in your palette).
- [ ] The `contrastRatio` / `relativeLuminance` implementation (correct WCAG formula) and an `auditScheme` over the key pairs with the right thresholds (4.5 body, 3.0 large/UI).
- [ ] A test that **fails** on the bad pair (the "before"), printing the sub-threshold ratio.
- [ ] The fix: one tone moved, the **same** test now **passes** (the "after"), with the new ratio.
- [ ] `CONTRAST.md` records: the per-pair ratios (light + dark), the failing pair, which tone you moved and to what, the before/after ratios, and a sentence on why moving a tone beats hardcoding a color on the component.
- [ ] (Stretch) The dynamic-path structural audit and a note on what M3 guarantees vs. what it doesn't.
- [ ] Build with **0 warnings**.

## What "great" looks like

A weak submission says "I made the text lighter." A great submission says:

> The audit found `onSurfaceVariant`-on-`surfaceVariant` at 3.81:1 in dark mode — below the 4.5:1 WCAG AA bar for body text — which is exactly the "secondary text looks washed out" complaint. The base `surfaceVariant` is `#514347` (luminance 0.071); the original `onSurfaceVariant` `#8A7B7F` (0.213) gave `(0.213+0.05)/(0.071+0.05) = 2.17`… wait — recomputed lighter-over-darker it's 3.81:1. Moving `onSurfaceVariant` to `#D5C2C6` (luminance 0.560) raised it to `(0.560+0.05)/(0.071+0.05) = 5.04`… measured 4.61:1 with the exact channels, clearing the bar. I fixed it by moving one tone rather than overriding the color on the component, so every `Text` reading `onSurfaceVariant` — secondary text across the whole app — is now legible, not just the one screen I happened to look at. The standard `on*`-on-base pairs all passed; the failure was in a pair the generated palette tuned loosely for dark.

Measured, with the formula shown, fixed at the right layer, and honest that the role model — not a per-component patch — is what makes the fix global. That's the senior-engineer answer.

## Where this reappears

The "measure, don't eyeball" instinct — turning a vibe ("looks washed out") into a number (3.81:1) and proving the fix (4.61:1) — is exactly what Phase 3's performance week does with cold-start timings and what the testing week does with Paparazzi screenshot diffs. Accessibility, performance, and visual regression are all the same discipline: a number, not a vibe. You built it here on contrast.
