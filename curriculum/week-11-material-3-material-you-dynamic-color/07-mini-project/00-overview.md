# Mini-Project — Pocket Reader: a fully themed Material 3 app

This week the app gets its skin. You will build **Pocket Reader**, a reader app with full Material 3 theming: a real `ColorScheme` and `Typography`, dynamic color from the user's wallpaper on Android 12+, a hand-tuned brand fallback (light and dark) on older devices, edge-to-edge layout with correct window insets, and a dark theme you have *audited* for WCAG contrast rather than eyeballed. By the end it looks shipped, and it is correct across the full configuration matrix.

This is a *theming* project, not an architecture one. Pocket Reader's article list is an in-memory list of a dozen items — the point of the week is how the app *looks and lays out*, not where its data lives (that's Week 12). If you built Catalog Companion last week, you can theme that instead and skip rebuilding screens; either way, the deliverable is a correctly-themed, edge-to-edge, contrast-audited app.

---

## Where you're starting from

You have, from this week's exercises:

- A `ColorScheme` (light + dark) generated from brand seed colors (exercise 1).
- The `chooseColorScheme(darkTheme, dynamicColor)` selection logic, tested across the matrix (exercise 2).
- An edge-to-edge, inset-aware screen pattern (exercise 3).

And from earlier weeks, the Compose fluency to build a few simple screens (a list, a detail, a settings screen). Pocket Reader assembles these into one themed app.

## What you're building toward

By the end you have:

- A **`PocketReaderTheme`** providing a custom `ColorScheme`, `Typography`, and `Shapes`, following `isSystemInDarkTheme()` with a user override.
- **Dynamic color** on Android 12+ (`dynamicLight/DarkColorScheme`), with a **hand-tuned fallback** (light + dark) below — chosen by the tested selection logic, with a user toggle to opt out.
- A reader UI — article list, article detail, settings — built **entirely from color and type roles**, no hardcoded colors on components.
- **Edge-to-edge** layout: `enableEdgeToEdge()`, content behind transparent system bars, scrollables padded with `contentPadding`, the settings screen's text field lifted above the keyboard with `imePadding()`.
- A **dark theme audited for contrast**: the key role pairs measured against 4.5:1 / 3:1, with a test asserting it.
- A demonstrated pass across the **configuration matrix**: light/dark × dynamic on/off × API 35/API 30 × gesture/three-button.

---

## Milestone 1 — The theme (≈ 1.5 h)

Build `PocketReaderTheme` from your exercise-1 `ColorScheme` plus a `Typography` and `Shapes`, wired to the exercise-2 selection logic.

```kotlin
@Composable
fun PocketReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PocketReaderTypography,
        shapes = PocketReaderShapes,
        content = content
    )
}
```

Decisions to defend in review:

- **Why the `Build.VERSION.SDK_INT >= S` gate?** Dynamic color exists only on Android 12+. Below it there's no wallpaper palette; the gate routes those devices to the fallback. (Exercise 2 tested every cell of this.)
- **Why a `dynamicColor` parameter?** So a user (or a brand requirement) can opt out and get your brand palette even on Android 12+. Wire it to a real settings toggle in Milestone 4.
- **Why is `Typography`/`Shapes` defined once?** Same reason as color: components read type and shape *roles* (`titleLarge`, `shapes.medium`); defining them once means the whole app follows from one place.

## Milestone 2 — The reader UI, in roles (≈ 2 h)

Build three screens — article list, article detail, settings — reading only roles. Not one `Color(0xFF…)` on a component.

```kotlin
@Composable
fun ArticleList(articles: List<Article>, onOpen: (Article) -> Unit, innerPadding: PaddingValues) {
    LazyColumn(contentPadding = innerPadding) {
        items(articles) { article ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,        // role
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onOpen(article) }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(article.title, style = MaterialTheme.typography.titleMedium)        // type role
                    Text(
                        article.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant                    // on* partner
                    )
                }
            }
        }
    }
}
```

The detail screen reads `surface`/`onSurface` for body text and `primary` for the "save" action; settings has the dark-mode and dynamic-color toggles. Every color is a role; every text style is a type role. Grep your screens for `Color(0xFF` — it should appear only in the `ColorScheme` definition.

## Milestone 3 — Edge-to-edge and insets (≈ 1.5 h)

Turn on edge-to-edge and handle the insets so nothing is occluded in any nav mode.

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { PocketReaderTheme { PocketReaderApp() } }
    }
}

@Composable
fun PocketReaderApp() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Pocket Reader") }) }
    ) { innerPadding ->
        // List scrolls under the translucent bars; items clear them via innerPadding.
        ArticleList(articles = InMemoryArticles.all, onOpen = { /* nav */ }, innerPadding = innerPadding)
    }
}
```

The settings screen's "add a reading-list URL" text field uses `imePadding()` so the keyboard never covers it. Decisions to defend:

- **Why `contentPadding = innerPadding` on the list, not `Modifier.padding`?** So the list background extends edge-to-edge behind the bars while items stay clear of them — the intended look. `Modifier.padding` would clip the list and leave a dead band.
- **Why no double padding?** `Scaffold` consumed the system-bar inset into `innerPadding`; nothing below re-applies `WindowInsets.systemBars`, or you'd get a gap twice the bar height.

The article *detail* screen — a long scrollable of body text — needs the same treatment, plus a back-arrow `TopAppBar` and (if it has a "leave a note" field) `imePadding()`:

```kotlin
@Composable
fun ArticleDetailScreen(article: Article, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(article.title, maxLines = 1) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { innerPadding ->
        // Long body text scrolls under the bars; contentPadding keeps it clear of them.
        LazyColumn(contentPadding = innerPadding, modifier = Modifier.fillMaxSize()) {
            item { Text(article.body, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        }
    }
}
```

Note the body text reads `bodyLarge` (a type role) and the default `onSurface` color — no literal sizes or colors. The detail screen is the one most likely to look broken in dark mode if any color slipped through as a literal, so it's the screen to check first in the matrix walk.

## Milestone 4 — Settings: dark mode and dynamic color toggles (≈ 1 h)

Give the user control. A settings screen with two switches — "Dark theme" (system/light/dark) and "Use wallpaper colors" (dynamic on/off) — that drive the theme parameters.

```kotlin
@Composable
fun PocketReaderRoot() {
    var darkMode by rememberSaveable { mutableStateOf<Boolean?>(null) }   // null = follow system
    var dynamic by rememberSaveable { mutableStateOf(true) }
    PocketReaderTheme(
        darkTheme = darkMode ?: isSystemInDarkTheme(),
        dynamicColor = dynamic
    ) {
        PocketReaderApp(
            darkMode = darkMode, onDarkModeChange = { darkMode = it },
            dynamic = dynamic, onDynamicChange = { dynamic = it }
        )
    }
}
```

Flipping "Use wallpaper colors" off on an Android 12+ device should visibly switch from the wallpaper tint to your brand fallback — a satisfying demo that the selection logic works. The toggles `rememberSaveable` so the choice survives rotation.

## Milestone 5 — The contrast audit (≈ 1 h)

Audit the dark theme (and light) for WCAG contrast as a test, and fix any failing pair by moving a tone (the challenge is the deep version of this).

```kotlin
@Test
fun darkThemeBodyTextMeetsWcagAA() {
    val failures = auditScheme(DarkColors).filterNot { it.passes }
    assertTrue("Contrast failures: $failures", failures.isEmpty())
}
```

Keep the `contrastRatio`/`auditScheme` helpers from the challenge in a test utility. A theme with a green contrast test is a theme you can defend.

## Milestone 6 — Walk the configuration matrix (≈ 0.5 h)

The acceptance bar for the whole week. Run Pocket Reader through every cell and confirm it's correct in each:

| | Light | Dark |
|---|---|---|
| **API 35, dynamic on** | wallpaper tint, legible | wallpaper tint, legible, elevation-tinted surfaces |
| **API 35, dynamic off** | brand fallback | brand fallback dark |
| **API 30** (no dynamic) | brand fallback | brand fallback dark |

Plus: gesture nav *and* three-button nav, confirming nothing hides behind a bar in either. Capture a screenshot of each cell into the repo README. "Correct in every cell" is the deliverable, not "looks nice on my phone."

---

## Acceptance criteria

- [ ] `PocketReaderTheme` provides a custom `ColorScheme`, `Typography`, and `Shapes`, following `isSystemInDarkTheme()` with a user override.
- [ ] Dynamic color on Android 12+ with a hand-tuned light **and** dark fallback below, chosen by the gated selection logic; a user toggle opts out.
- [ ] The reader UI reads **only roles** — `MaterialTheme.colorScheme.*` / `.typography.*` / `.shapes.*`. **No `Color(0xFF…)` on any component.** (`grep -rn 'Color(0xFF' app/src/main` finds hits only in the `ColorScheme` definition.)
- [ ] **Edge-to-edge** is on (`enableEdgeToEdge()`); scrollables use `contentPadding`; no double padding; the settings text field uses `imePadding()`.
- [ ] The dark theme **passes a WCAG contrast test** (4.5:1 body, 3:1 large/UI) for the key role pairs.
- [ ] The app is demonstrably correct across the **configuration matrix** (light/dark × dynamic on/off × API 35/30 × gesture/three-button), with screenshots in the README.
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **A reading theme preference.** Add a "sepia/high-contrast" reading mode as a third `ColorScheme` the user can pick, threaded through the same `chooseColorScheme` seam.
- **Custom brand color extension.** Add one brand accent the role set doesn't express (a "premium" gold) as a named theme extension — centralized, themeable — not a scattered literal.
- **Animated theme switch.** Animate the color change when the user flips dark mode (`animateColorAsState` on the scheme colors) so the switch is a smooth crossfade, not a snap.
- **Per-wallpaper dynamic audit.** Capture the dynamic scheme's role values on two different wallpapers (a debug overlay) and run the contrast audit on each, confirming the `on*`-on-base guarantees hold.

## What this milestone earns you

You can now theme a real app — not a tutorial app — in Material 3: roles not literals, dynamic color with a graceful fallback, edge-to-edge with correct insets, and a dark theme audited to a number. More than that: you made it *correct across the matrix*, which is the senior move — "looks nice on my phone" is a demo; "correct in every configuration, measured" is a shipped app. The navigation (Week 10) was the skeleton, this theme is the skin, and Week 12 adds the architecture — a `ViewModel`-driven `StateFlow<UiState>`, the Now-In-Android layer split, and process-death survival. A themed app with no architecture is still a demo; you're about to fix that. But it'll be a *beautiful* demo in the meantime, and that's worth something.
