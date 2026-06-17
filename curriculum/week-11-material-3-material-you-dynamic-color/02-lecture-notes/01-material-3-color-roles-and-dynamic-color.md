# Lecture 1 — Material 3: color as a role, and dynamic color with a fallback

> "In Material 3 you never paint a button blue. You tell it it's `primary`, and the theme decides what `primary` means in this light, this dark, this wallpaper, this device."

This is the lecture that decides whether Material 3 theming feels like fighting a framework or like expressing intent. The framing for the whole week is one sentence: **a color in Material 3 is a semantic *role*, resolved by a `ColorScheme`, not a hardcoded hex value.** Hold that, and every surprise this week — why a component changes color when you flip dark mode without you touching it, why dynamic color "just works," why a hardcoded `Color(0xFF2196F3)` looks wrong in three of four configurations — has a one-idea explanation. Lose it, and you are scattering hex values and patching the ones that look wrong.

We build the mental model top-down: the role model (what a role is), then the theme (the boundary that resolves roles), then the palette (where role colors come from — seed → tonal palette → role), then dynamic color (the system filling the palette from the wallpaper), then the fallback (your palette when the system can't). By the end you should be able to point at any pixel of a Material app and name the role it's reading.

---

## 1. The problem hardcoded colors create

Here is the naive way to color a screen, and why it's a trap:

```kotlin
// THE TRAP — hardcoded colors. Looks fine in exactly one configuration.
Button(
    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),  // "blue"
    onClick = { /* … */ }
) {
    Text("Save", color = Color.White)
}
Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEEE))) { /* … */ }
```

This button is blue in light mode. In dark mode it is *still* that exact blue on a near-black surface — too saturated, glaring, wrong. With dynamic color on, the user's whole device is tinted green from their wallpaper, and this one button is stubbornly blue, breaking the coherence Material You promises. The `Color.White` text might pass contrast on this blue but fail on the next hardcoded color you pick. You will spend the rest of the project patching each hardcoded color in each configuration, and you will still miss some.

The fix is not "pick better hex values." It's to stop naming colors at all and start naming *roles*.

---

## 2. The color-role model

Material 3 defines a fixed set of **semantic roles**. You assign a role to a component by intent; the theme resolves the role to an actual color for the current configuration. The roles you'll use constantly:

| Role | What it's for |
|------|---------------|
| `primary` | The main brand/action color — primary buttons, active states, FAB |
| `onPrimary` | Content (text/icon) drawn *on* a `primary` surface — guaranteed to contrast |
| `primaryContainer` | A softer fill for prominent-but-not-loudest elements |
| `onPrimaryContainer` | Content on `primaryContainer` |
| `secondary` / `tertiary` | Supporting accent roles (and their `on*`/`*Container` partners) |
| `surface` | The default background of cards, sheets, app bars |
| `onSurface` | Primary text/icons on a surface |
| `surfaceVariant` / `onSurfaceVariant` | A subtly differentiated surface and its content (dividers, secondary text) |
| `background` / `onBackground` | The window background and content on it |
| `error` / `onError` / `errorContainer` / `onErrorContainer` | Error states, same pattern |
| `outline` / `outlineVariant` | Borders and dividers |

The pattern to internalise: **every base role has a matching `on*` role, and the `on*` color is guaranteed to contrast with its base.** `onPrimary` is legible on `primary`; `onSurface` is legible on `surface`. So you never compute "what text color goes on this background" — you read the `on*` partner of whatever base you used. That single rule is most of accessible color, for free.

Now the same button, the right way:

```kotlin
// THE FIX — roles. Correct in light, dark, dynamic, and fallback, with no changes.
Button(onClick = { /* … */ }) {              // Button defaults to container=primary, content=onPrimary
    Text("Save")                              // text uses onPrimary automatically — guaranteed contrast
}
Card { /* … */ }                             // Card defaults to container=surfaceVariant, content=onSurfaceVariant
```

`Button` already reads `primary`/`onPrimary` from the theme; `Card` reads `surface*` roles. You did not specify colors at all — the components ask the theme for their roles, and the theme answers correctly for the current configuration. When you *do* need to choose a role explicitly, you read it from the theme, never construct it:

```kotlin
Text("Subtitle", color = MaterialTheme.colorScheme.onSurfaceVariant)   // a role, from the theme
Surface(color = MaterialTheme.colorScheme.primaryContainer) { /* … */ }
Icon(Icons.Default.Warning, tint = MaterialTheme.colorScheme.error, contentDescription = null)
```

The discipline, stated as a code-review rule: **a literal `Color(0xFF…)` on a component is a smell.** The legitimate place for raw colors is *defining the `ColorScheme`* (and even there, you usually generate them). Everywhere else, read a role.

---

## 3. `MaterialTheme` — the boundary that resolves roles

`MaterialTheme` is the composable that makes a `ColorScheme`, a `Typography`, and a `Shapes` available to everything beneath it. It works through `CompositionLocal` (Week 8's mechanism): a value provided high in the tree and read anywhere below without threading it through every parameter.

```kotlin
@Composable
fun PocketReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = chooseColorScheme(darkTheme, dynamicColor)   // §4–6
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PocketReaderTypography,
        shapes = PocketReaderShapes,
        content = content
    )
}

// Anywhere beneath PocketReaderTheme { … }:
val primary = MaterialTheme.colorScheme.primary          // reads the provided ColorScheme
val titleStyle = MaterialTheme.typography.headlineMedium // reads the provided Typography
val cardShape = MaterialTheme.shapes.medium              // reads the provided Shapes
```

Three things to hold onto:

- **The theme is read, not passed.** `MaterialTheme.colorScheme` is a `CompositionLocal` read; you do not pass the scheme down through parameters. That's why a deeply nested `Text` can use `onSurface` without anyone wiring it there.
- **Swapping the `ColorScheme` re-themes the whole subtree.** Flip `darkTheme`, and `chooseColorScheme` returns a different `ColorScheme`, `MaterialTheme` provides it, and every component beneath recomposes with the new role colors — no component code changes. This is the whole payoff: the configuration lives in *one* place (the scheme), the UI reads roles, and the two are decoupled.
- **`Typography` and `Shapes` work identically.** A `Typography` maps roles like `headlineMedium`, `bodyLarge`, `labelSmall` to `TextStyle`s; components read the role. Define your type scale once; components ask for `titleLarge` and get your font.

### The `surfaceContainer` roles — the 2024 elevation system

There is one part of the role set worth calling out specifically, because it changed in Material 3's 2024 revision and trips up engineers who learned M3 earlier. The old model communicated elevation with a single `surface` plus a translucent "elevation overlay" tint. The current model replaces that with an explicit ladder of **surface container roles**, from lowest to highest:

```kotlin
MaterialTheme.colorScheme.surfaceContainerLowest    // the dimmest container (e.g. a recessed area)
MaterialTheme.colorScheme.surfaceContainerLow
MaterialTheme.colorScheme.surfaceContainer          // the default card/sheet container
MaterialTheme.colorScheme.surfaceContainerHigh
MaterialTheme.colorScheme.surfaceContainerHighest   // the most-raised container (e.g. a menu, a dialog)
```

Each step is a slightly different tone, so a card (`surfaceContainerLow`) sits visibly above the `background`, a bottom sheet (`surfaceContainer`) above that, and a menu (`surfaceContainerHigh`) above that — *without shadows*, which is critical in dark mode where a drop shadow on near-black is invisible. The rule: **express elevation by picking the right `surfaceContainer*` role, not by stacking shadows or hardcoding a lighter gray.** A raised element in dark mode is lighter than what's beneath it because its container role is a higher tone — that's the whole mechanism, and it's why a hardcoded dark background loses the elevation signal entirely.

In practice you mostly let components pick: a Material 3 `Card`, `ModalBottomSheet`, or `DropdownMenu` already reads the appropriate container role. When you build a custom raised surface, choose the container role by *how raised it is*, the same way you choose `primary` by *what it is*. It's the role discipline applied to elevation.

### A walk through the role set, by intent

To make "choose by intent" concrete, here's how you'd reach for each family on a real screen:

```kotlin
// The page itself.
Modifier.background(MaterialTheme.colorScheme.background)              // the window backdrop
Text("Heading", color = MaterialTheme.colorScheme.onBackground)        // text directly on the backdrop

// A card carrying content.
Card { /* defaults to surfaceVariant / onSurfaceVariant */ }
Text("Body", color = MaterialTheme.colorScheme.onSurface)             // primary text on a surface
Text("Caption", color = MaterialTheme.colorScheme.onSurfaceVariant)   // secondary/dimmer text

// The loud primary action.
Button(onClick = {}) { Text("Buy now") }                              // primary / onPrimary

// A prominent-but-not-loudest element.
Surface(color = MaterialTheme.colorScheme.primaryContainer) {
    Text("Featured", color = MaterialTheme.colorScheme.onPrimaryContainer)
}

// A second-tier accent (filters, chips).
AssistChip(onClick = {}, label = { Text("Sports") })                  // secondary roles

// A rare third accent (a highlight, a "new" badge).
Badge(containerColor = MaterialTheme.colorScheme.tertiary) { /* … */ }

// An error.
Text("Required", color = MaterialTheme.colorScheme.error)             // error / onError / errorContainer

// Structure.
HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)   // dividers
OutlinedTextField(/* uses outline for its border */)
```

The skill is not memorizing thirty names; it's asking, for any element, "what *is* this — a primary action? secondary text? a divider? an error?" — and reaching for the role whose *name describes that intent*. When you find yourself wanting a color the role set can't name, that's a signal to add a *named* theme extension (§9), not to drop a literal.

---

## 4. Where role colors come from: seed → tonal palette → role

A `ColorScheme` is ~30 colors (every role above). You do not pick 30 hex values by hand. You start from a small number of **seed colors** and derive the rest. The pipeline:

1. **Seed color.** One (or a few) source colors — your brand color, or for dynamic color, a color the system extracted from the wallpaper. Just `Color(0xFF6750A4)` (M3's default purple), say.
2. **Tonal palette.** From each seed, Material derives a **tonal palette**: the same hue at thirteen *tones* from 0 (black) to 100 (white) — tone 10, 20, 40, 80, 90, etc. The tones are perceptually even (computed in the HCT color space), so tone-40 is as "dark" as tone-40 of any other hue. A palette is one hue across the lightness range.
3. **Role assignment.** The standard roles map onto specific tones of specific palettes. In *light* theme, `primary` is the primary palette's tone-40, `onPrimary` is tone-100 (white), `primaryContainer` is tone-90, `onPrimaryContainer` is tone-10. In *dark* theme the tones flip: `primary` is tone-80, `onPrimary` is tone-20, and so on — which is why dark mode isn't "invert the colors," it's "re-pick tones for a dark surface."

You almost never compute this by hand. **Material Theme Builder** (resources page) takes your seed colors and exports a complete light + dark `ColorScheme` as Compose Kotlin:

```kotlin
// Generated by Material Theme Builder from a brand seed — this is your FALLBACK palette.
val LightColors = lightColorScheme(
    primary = Color(0xFF8C4A60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3A071D),
    secondary = Color(0xFF74565F),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF22191C),
    surfaceVariant = Color(0xFFF2DDE2),
    onSurfaceVariant = Color(0xFF514347),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF837377),
    // …the rest of the roles…
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
    outline = Color(0xFF9D8C90),
    // …the rest…
)
```

The thing to be able to explain in review: a **seed** is the source color, a **tonal palette** is that hue across all tones, and a **role** is a specific tone assigned a job. Light and dark differ because the *same roles* read *different tones* (40/80 for `primary`, 90/30 for containers) to stay legible on a light vs. dark surface.

---

## 5. Dynamic color — the system fills the palette from the wallpaper

Material You's dynamic color is exactly the §4 pipeline with one change: **the seed comes from the user's wallpaper, and the system does the extraction.** On Android 12+ the OS analyses the wallpaper, picks source colors, and exposes the derived tonal palettes to apps. Compose hands you a ready-made `ColorScheme` from them:

```kotlin
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext

@Composable
fun chooseColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme {
    val context = LocalContext.current
    return when {
        // Dynamic color is available only on Android 12 (API 31, "S") and up.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors       // hand-tuned fallback (§4), dark
        else -> LightColors           // hand-tuned fallback, light
    }
}
```

What to understand and be able to say out loud:

- **The extraction happens in the system, not your app.** `dynamicLightColorScheme(context)` reads palettes the OS already computed from the wallpaper. You don't sample the wallpaper yourself; you ask the framework for the result. (Sampling the wallpaper directly would need a permission and is the wrong approach.)
- **`Build.VERSION.SDK_INT >= S` is the gate.** Below API 31 there are no dynamic palettes; calling the dynamic functions there isn't available. The gate routes pre-12 devices to your fallback (§6).
- **Dynamic color reads roles too.** When dynamic is on, your components still read `primary`/`surface`/`onSurface` — the *values* behind those roles came from the wallpaper instead of your seed, but the component code is identical. That's the role model paying off: the same widget tree adapts to the wallpaper with zero changes.
- **Let the user opt out.** Some users (and some brand requirements) want your brand color regardless of wallpaper. The `dynamicColor: Boolean` parameter (often a user setting) lets them choose your fallback even on Android 12+. Wire it to a real toggle in a real app.

To *see* it: change the emulator's wallpaper, relaunch the app, and watch `primary`, the FAB, the selected tab — everything reading those roles — re-tint to match. That's Material You.

---

## 6. The fallback — your palette when the system can't

On Android 11 and below (and when the user opts out), there is no wallpaper-derived palette, so you supply your own — the hand-tuned `LightColors`/`DarkColors` from §4. The fallback is not an afterthought; for a large fraction of the install base it's the *only* theme they'll ever see, so treat it as a first-class deliverable.

Principles for a good fallback:

- **Generate it from real brand seeds, then audit it.** Use Material Theme Builder with your brand color so the role relationships are correct out of the box, then run the contrast audit (lecture 2, §4) on the key `on*`-on-base pairs and fix any that fall short. Generated is a starting point, not a guarantee.
- **Build both light and dark.** A fallback that only does light leaves dark-mode users on pre-12 devices with a broken theme. `lightColorScheme(...)` and `darkColorScheme(...)`, both tuned.
- **Don't try to imitate a specific wallpaper.** The fallback should look like *your brand*, coherent and intentional. Chasing "make it look like dynamic color would" is a losing game — dynamic color is per-user; your fallback is one fixed, well-made identity.
- **Test it on a real pre-12 emulator.** The fallback path only runs below API 31, so an API-30 emulator is the only way to actually exercise it. "It looks right with dynamic on" tells you nothing about the fallback.

The selection logic (§5's `chooseColorScheme`) is the seam: above 12 with dynamic on, you get the wallpaper palette; otherwise you get your fallback; in both cases the UI reads roles. Exercise 02 tests exactly this selection across the version/mode matrix.

---

## 7. Typography and shape, briefly

Color is the deep topic this week, but `MaterialTheme` carries two more role systems, and the same "role, not literal" discipline applies:

```kotlin
val PocketReaderTypography = Typography(
    headlineMedium = TextStyle(fontFamily = Brand, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = Brand, fontWeight = FontWeight.Medium, fontSize = 22.sp),
    bodyLarge = TextStyle(fontFamily = Brand, fontSize = 16.sp, lineHeight = 24.sp),
    labelSmall = TextStyle(fontFamily = Brand, fontSize = 11.sp)
    // …override only the roles you care about; the rest inherit M3 defaults…
)

// Read a type role, don't construct a TextStyle inline:
Text("Article title", style = MaterialTheme.typography.titleLarge)
```

Same for `Shapes` — `MaterialTheme.shapes.small/medium/large` map to corner treatments, and components (cards, buttons, sheets) read them. Define the scale once; components ask for the role. The payoff is identical to color: change the type scale or corner radius in one place, and the whole app follows.

---

## 8. What the role model asks of you — the sharp edges

The role model is a good abstraction, which means it leaks responsibility back to you in predictable places:

1. **You must choose roles by *intent*, not by *appearance*.** "I want this gray" is the wrong question; "is this a `surfaceVariant` or an `outline`?" is the right one. Picking a role because it happens to look right in light mode, when its *meaning* is wrong, breaks in dark or dynamic. Choose the role for what the element *is*.
2. **Generated palettes still need a contrast audit.** Material Theme Builder gives correct *relationships*, but a specific `onSurfaceVariant`-on-`surfaceVariant` pair can still land below 4.5:1 for body text. Generated is not audited (lecture 2, §4).
3. **Dynamic color is per-device and unpredictable.** You cannot screenshot "the dynamic theme" because it's different on every wallpaper. Test the *structure* (roles, contrast guarantees from `on*` partners) rather than specific colors, and trust the `on*`-on-base contrast that M3 maintains.
4. **The fallback is real surface area.** A large share of users are on it. "Looks great with dynamic on, on my Pixel" is not coverage. Test API 30.
5. **Don't fight the components.** A `Button` already reads `primary`/`onPrimary`. Overriding it with `buttonColors(...)` to a hardcoded color is how you reintroduce the §1 trap. Override only when you have a real reason, and override with *roles*.

None of these are reasons to avoid the role model — they're the things you keep in peripheral vision so that when something looks wrong in one configuration, you ask "which role, and does its `on*` partner contrast?" instead of patching a hex.

---

## 9. The decision table

| Situation | Reach for |
|-----------|-----------|
| New Compose app in 2026 | **Material 3** with `MaterialTheme` and roles |
| Brand-coherent device feel on Android 12+ | **Dynamic color** (`dynamicLight/DarkColorScheme`) |
| Android 11 and below, or user opted out | **Hand-tuned fallback** `lightColorScheme`/`darkColorScheme` from brand seeds |
| Generating a palette from a brand color | **Material Theme Builder**, then audit |
| Choosing a color for a specific element | A **role** from `MaterialTheme.colorScheme`, never a literal |
| A one-off accent the role set can't express | Add a **custom color** to your theme (an extension), don't scatter a literal |
| Matching an existing brand exactly, no wallpaper tint | **Fallback only** (`dynamicColor = false`), audited |

That second-to-last row matters: if you genuinely need a color outside the role set (a brand gradient, a category accent), add it as a *named extension* to your theme so it's still centralized and themeable — don't paint a literal on a component. The extension pattern, briefly:

```kotlin
// A named extension for a color the role set can't express — still centralized.
@Immutable
data class ExtendedColors(val premium: Color, val onPremium: Color)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(premium = Color.Unspecified, onPremium = Color.Unspecified)
}

// Provide it alongside MaterialTheme, varying by light/dark like any role:
@Composable
fun AppTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val extended = if (darkTheme) DarkExtended else LightExtended
    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(colorScheme = /* … */, content = content)
    }
}

// Read it like a role — centralized, themeable, dark-aware. NOT a scattered literal.
val gold = LocalExtendedColors.current.premium
```

This keeps the "one place owns the colors" property even for colors Material doesn't name: the extension varies by light/dark, lives next to the theme, and is read through a `CompositionLocal` just like `MaterialTheme.colorScheme`. A literal `Color(0xFFD4AF37)` sprinkled on a component has none of those properties — that's the difference between a themeable extension and a hardcoded smell.

---

## 10. Recap — the one-question habit

You will theme all week. The discipline that turns you from someone who *picks colors* into someone who *themes correctly* is to ask, on every colored element, one question: **what role is this, and is it reading the role or a literal?**

- Component looks wrong in dark mode → it's reading a hardcoded color, not a role; switch it to a role.
- Whole app coherent on the wallpaper → roles, fed by `dynamicLightColorScheme`. Free.
- Pre-12 device looks broken → no fallback, or an unaudited one; build and audit `LightColors`/`DarkColors`.
- Text hard to read on a fill → you didn't use the `on*` partner of the base role.
- "Make this gray" → wrong question; name the role.

Material 3 didn't give you a palette. It gave you a *vocabulary of roles* and a `ColorScheme` that resolves them per configuration. Dynamic color fills that vocabulary from the wallpaper; your fallback fills it from your brand; your components read it without knowing which. Learn the role model well enough to choose by intent, generate-then-audit your fallback, and gate dynamic color cleanly — and you have half this week's skill: theming a real app, not a tutorial app.

In lecture 2 we go to the *layout chrome* half: edge-to-edge drawing behind the system bars, window insets so nothing is occluded, the IME inset for keyboards, dark theme as a peer configuration, and the contrast audit that turns "looks fine" into a measured 4.5:1. Bring the role model with you — the contrast audit is the role model meeting WCAG.
