# Exercise 1 — Window-size-class adaptive layout

**Goal.** Build a list-detail screen driven by `WindowSizeClass`, run it on the Resizable emulator, and *drag the emulator* between phone / unfolded / tablet to watch it reflow from one pane to two — live, with no restart. Then add a `WindowInfoTracker` observer and pose the hinge to see fold state change. This is lecture 1 made visible: if you can predict the pane count for a given window before you drag, you understand window size classes.

**Estimated time.** 50 minutes.

**Prerequisites.** Android Studio Ladybug+, the **Resizable (Experimental)** emulator (`Tools ▸ Device Manager ▸ Create Device ▸ Resizable (Experimental)`). Set up a Compose "Empty Activity" project (it wires the Compose BOM and the Compose Compiler plugin).

---

## Step 1 — Add the adaptive dependencies

In your `app/build.gradle.kts`, add the `material3-adaptive` artifacts and the window library:

```kotlin
dependencies {
    val adaptive = "androidx.compose.material3.adaptive"
    implementation("$adaptive:adaptive:1.0.0")
    implementation("$adaptive:adaptive-layout:1.0.0")
    implementation("$adaptive:adaptive-navigation:1.0.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.3.1")
    implementation("androidx.window:window:1.3.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
}
```

(Use the latest stable versions your BOM resolves; the exact numbers will have moved by the time you read this.)

## Step 2 — A tiny domain and a list

Keep the data trivial so the focus stays on adaptation:

```kotlin
data class City(val id: String, val name: String, val tempC: Int)

private val cities = listOf(
    City("ldn", "London", 14),
    City("nyc", "New York", 19),
    City("tok", "Tokyo", 23),
    City("syd", "Sydney", 26),
)

@Composable
fun CityList(onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier) {
        items(cities, key = { it.id }) { city ->
            ListItem(
                headlineContent = { Text(city.name) },
                supportingContent = { Text("${city.tempC}°C") },
                modifier = Modifier.clickable { onClick(city.id) }
            )
        }
    }
}

@Composable
fun CityDetail(cityId: String?, modifier: Modifier = Modifier) {
    val city = cities.firstOrNull { it.id == cityId }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            if (city == null) "Select a city" else "${city.name}\n${city.tempC}°C",
            textAlign = TextAlign.Center
        )
    }
}
```

## Step 3 — The adaptive list-detail scaffold

Use `NavigableListDetailPaneScaffold`. It reads the window size class itself and reflows; you do not write a `when (widthClass)`:

```kotlin
@Composable
fun AdaptiveCityScreen() {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                CityList(onClick = { id ->
                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id)
                })
            }
        },
        detailPane = {
            AnimatedPane {
                CityDetail(cityId = navigator.currentDestination?.contentKey)
            }
        }
    )
}
```

Set `setContent { MaterialTheme { AdaptiveCityScreen() } }` in your activity. Add the imports Android Studio offers.

## Step 4 — PREDICT, then drag

Before you run, write down (comment or paper) your prediction for each window:

- **Phone portrait (width ≈ 411dp → COMPACT)** — how many panes? What does tapping a city do? What does back do?
- **Unfolded / medium (width ≈ 700dp → MEDIUM)** — how many panes?
- **Tablet landscape (width ≈ 1280dp → EXPANDED)** — how many panes? What does tapping a city do now?

Recall lecture 1, §2–3: `COMPACT` shows one pane (tap pushes detail, back pops to list); `MEDIUM`/`EXPANDED` show two panes side by side (tap swaps the detail in place).

## Step 5 — Run and confirm

Run on the Resizable emulator. Use the emulator's display-mode control (the resizable AVD's toolbar, or `Settings`) to switch between **Phone**, **Unfolded**, and **Tablet**.

**Expected result:**

- **Phone:** one pane. The list fills the screen; tap a city → detail fills the screen; back → list.
- **Unfolded / Tablet:** two panes. List on the left, detail on the right; tap a city → the detail updates *in place* (no full-screen transition).
- **The reflow is live.** Tap a city on the phone to open its detail, then switch the emulator to Tablet *without restarting* — the layout reflows to two panes and your selection is preserved. That live reflow is `currentWindowAdaptiveInfo()` recomposing.

## Step 6 — Observe a live fold

Add a fold observer and show the current posture on screen so you can watch it change. Add to your activity (you need the `Activity` for `WindowInfoTracker`):

```kotlin
@Composable
fun FoldBanner(activity: Activity) {
    val tracker = remember { WindowInfoTracker.getOrCreate(activity) }
    val info by remember(tracker) { tracker.windowLayoutInfo(activity) }
        .collectAsStateWithLifecycle(initialValue = null)

    val fold = info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()
    val posture = when {
        fold == null -> "no fold (flat single screen)"
        fold.state == FoldingFeature.State.HALF_OPENED &&
            fold.orientation == FoldingFeature.Orientation.HORIZONTAL -> "TABLETOP"
        fold.state == FoldingFeature.State.HALF_OPENED &&
            fold.orientation == FoldingFeature.Orientation.VERTICAL -> "BOOK"
        else -> "FLAT"
    }
    Text("Posture: $posture", modifier = Modifier.padding(8.dp))
}
```

Render `FoldBanner(this@MainActivity)` above the scaffold. Switch the emulator to a foldable/unfolded mode and use **Virtual sensors ▸ hinge angle** (or a Pixel Fold AVD) to pose the hinge to ~90°. Watch the banner flip to `TABLETOP`. The banner updating *without a restart* is `WindowInfoTracker`'s flow emitting and `collectAsStateWithLifecycle` recomposing.

---

## Acceptance criteria

- [ ] `AdaptiveCityScreen` shows one pane on COMPACT and two panes on MEDIUM/EXPANDED, using `NavigableListDetailPaneScaffold`.
- [ ] You wrote a prediction for all three windows **before** running.
- [ ] Tapping a city pushes a full-screen detail on phone and swaps the detail in place on tablet; back behaves correctly on phone (pops to list).
- [ ] The layout reflows **live** when you change the emulator's display mode without a restart.
- [ ] `FoldBanner` shows `TABLETOP` when you pose the hinge to half-opened horizontal, updating live.
- [ ] No `if (isTablet)` or raw pixel-width branch anywhere. Every decision is driven by the scaffold's size-class reading or `FoldingFeature`.
- [ ] Build with **0 warnings, 0 errors**.

## What you just proved

You proved lecture 1's core claim with your eyes: a single layout reflows across window sizes because it reads *available window space* (size class) reactively, not device identity. The list-detail scaffold did the pane reflow, the navigation, and the back behavior for you. And you watched fold state — a second, finer window input — update live as the hinge moved. Both signals are observable inputs to composition; neither is a one-time device check. That is the entire adaptive discipline.

---

## Hints (read only if stuck > 10 min)

- **The layout never shows two panes.** Your emulator window is never wide enough to cross into MEDIUM/EXPANDED. Use the resizable AVD's *Tablet* mode (or rotate to landscape), and confirm the reported width is ≥ 600dp.
- **Tapping a city does nothing on tablet.** You forgot to read `navigator.currentDestination?.contentKey` in the detail pane — the navigator holds the selection; the detail must read it.
- **Back exits the app on phone instead of returning to the list.** Use `NavigableListDetailPaneScaffold` (not the plain `ListDetailPaneScaffold`) — the navigable wrapper wires predictive back into the navigator.
- **`FoldBanner` never changes.** `collectAsStateWithLifecycle` only collects while the activity is at least STARTED — make sure the app is foregrounded, and pose the hinge via Virtual sensors while it's visible. Also confirm you passed the `Activity` (not the application `Context`) to `windowLayoutInfo`.
- **Selection lost when reflowing phone→tablet.** The navigator survives recomposition (`rememberListDetailPaneScaffoldNavigator`); if it resets, you probably recreated it inside a child scope instead of hoisting it once at the screen root.
