# Exercise 1 — Theme with color roles

**Goal.** Build a real `MaterialTheme` with a custom `ColorScheme`, then take a screen riddled with hardcoded `Color(0xFF…)` values and convert every one to a semantic role. By the end the screen looks identical in light mode and — without you touching it again — *correct* in dark mode and under dynamic color, because it reads roles instead of literals. This is the entire premise of the week distilled to one conversion: stop painting colors, start assigning roles.

**Estimated time.** 45 minutes.

**Prerequisites.** Android Studio Ladybug+, an Android 15 emulator and (ideally) an Android 11 emulator. A Compose project with `androidx.compose.material3:material3`. Material Theme Builder open in a browser tab (resources page) to generate the palette.

---

## Step 1 — Generate a `ColorScheme` from seed colors

Go to Material Theme Builder, set a primary seed color (pick your brand color — `#8C4A60` is a fine warm rose to follow along), and export the Compose `Color.kt` / `Theme.kt`. You'll get a `lightColorScheme(...)` and `darkColorScheme(...)` filled with role values. Paste them into `ui/theme/Color.kt`:

```kotlin
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val LightColors = lightColorScheme(
    primary = Color(0xFF8C4A60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3A071D),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF22191C),
    surfaceVariant = Color(0xFFF2DDE2),
    onSurfaceVariant = Color(0xFF514347),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF837377)
    // …Theme Builder fills the rest of the roles; keep them all…
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF54122C),
    primaryContainer = Color(0xFF6F2942),
    onPrimaryContainer = Color(0xFFFFD9E2),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFEFDFE1),
    surfaceVariant = Color(0xFF514347),
    onSurfaceVariant = Color(0xFFD5C2C6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF9D8C90)
    // …keep all the generated roles…
)
```

Note the only place raw `Color(0xFF…)` literals are legitimate: *defining the scheme.* Everywhere else they're a smell.

## Step 2 — Wire the theme

Create `ui/theme/Theme.kt`:

```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun ReaderTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
        // (we add dynamic color in exercise 2; for now, fallback only)
    )
}
```

## Step 3 — The screen, the WRONG way (the *before*)

Type out this article-card screen with hardcoded colors. It compiles, runs, and looks fine in light mode — and breaks in dark mode and under dynamic color.

```kotlin
@Composable
fun ArticleCardBefore() {
    Column(
        Modifier.fillMaxSize().background(Color(0xFFFFF8F8)).padding(16.dp),   // hardcoded surface
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(color = Color(0xFFF2DDE2), shape = RoundedCornerShape(12.dp)) {  // hardcoded surfaceVariant
            Column(Modifier.padding(16.dp)) {
                Text("Kotlin 2.0 is here", color = Color(0xFF22191C), fontSize = 20.sp)  // hardcoded onSurface
                Text("A look at the K2 compiler", color = Color(0xFF514347))             // hardcoded onSurfaceVariant
            }
        }
        Button(
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8C4A60)),    // hardcoded primary
            onClick = {}
        ) {
            Text("Read", color = Color(0xFFFFFFFF))                                       // hardcoded onPrimary
        }
    }
}
```

Run it in **dark mode** (`adb shell cmd uimode night yes`). It's broken: a light surface on a dark device, near-black text invisible on it, the button stubbornly the same. Every hardcoded color is wrong in the configuration you didn't design for.

## Step 4 — The screen, the RIGHT way (the *after*)

Rewrite it reading roles. Each hardcoded color becomes the role it was *trying* to be:

```kotlin
@Composable
fun ArticleCardAfter() {
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)           // role, not literal
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Kotlin 2.0 is here", style = MaterialTheme.typography.titleLarge)   // type role too
                Text(
                    "A look at the K2 compiler",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant                    // the on* partner
                )
            }
        }
        Button(onClick = {}) {        // Button defaults to primary / onPrimary — no colors needed at all
            Text("Read")              // text uses onPrimary automatically
        }
    }
}
```

Run it again in dark mode. It's *correct* — dark surface, legible text, a button tinted for the dark scheme — and you changed nothing between the runs except the device's dark setting. That's the role model: the configuration lives in the `ColorScheme`, the UI reads roles, and the two are decoupled.

## Step 5 — Preview both, side by side

Use a `@Preview` for each mode so you see both without flipping the device:

```kotlin
@Preview(name = "Light")
@Composable fun PreviewLight() { ReaderTheme(darkTheme = false) { ArticleCardAfter() } }

@Preview(name = "Dark")
@Composable fun PreviewDark() { ReaderTheme(darkTheme = true) { ArticleCardAfter() } }
```

The `After` previews both read correctly. Add previews of `Before` and watch the dark one break — keep them as the contrast.

---

## Acceptance criteria

- [ ] A `ColorScheme` (light + dark) generated from seed colors, with the full set of roles.
- [ ] A `ReaderTheme` composable providing it, following `isSystemInDarkTheme()`.
- [ ] `ArticleCardAfter` reads **only roles** — `MaterialTheme.colorScheme.*` and `MaterialTheme.typography.*` — with **zero** `Color(0xFF…)` literals in the screen body. (`grep -n 'Color(0xFF' ArticleCard*.kt` finds hits only in `ArticleCardBefore`.)
- [ ] The button specifies **no** colors (it reads `primary`/`onPrimary` by default).
- [ ] The `After` screen renders correctly in both light and dark previews; you confirmed the `Before` screen breaks in dark.
- [ ] Build with **0 warnings, 0 errors**.

## What you just proved

You proved the core claim of the week: a color is a role, not a hex. The `Before` screen needed a different set of literals for every configuration and broke in the ones you didn't hand-tune; the `After` screen expresses *intent* (this is a `surface`, this text is `onSurfaceVariant`, this button is `primary`) and the theme resolves it correctly for light, dark, and — once exercise 2 adds it — dynamic color, with no UI changes. Every screen in the mini-project is built this way.

---

## Hints (read only if stuck > 10 min)

- **The button still looks wrong after "fixing" it.** You probably left `ButtonDefaults.buttonColors(...)` in. Delete the `colors` argument entirely — `Button` already reads `primary`/`onPrimary`. Overriding it reintroduces the hardcoded-color trap.
- **`MaterialTheme.colorScheme` is the default scheme, not mine.** Your screen must be *inside* `ReaderTheme { … }` (or a `@Preview` that wraps it). `MaterialTheme.colorScheme` reads the nearest provided scheme; outside your theme it falls back to the M3 default.
- **Dark mode won't flip.** `adb shell cmd uimode night yes` / `no`, or toggle it in the emulator's quick settings. `isSystemInDarkTheme()` reads that system setting.
- **Text contrast looks off on a custom surface.** You used a base role but the *wrong* `on*` (e.g. `onSurface` text on a `surfaceVariant` fill). Match the `on*` to its base: `onSurfaceVariant` on `surfaceVariant`. That pairing is what guarantees contrast.
- **Which role for "secondary gray text"?** `onSurfaceVariant` on a `surface`/`surfaceVariant`. Don't reach for a literal gray — name the role.
