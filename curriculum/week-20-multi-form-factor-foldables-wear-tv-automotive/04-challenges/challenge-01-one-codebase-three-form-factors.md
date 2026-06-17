# Challenge 1 — One codebase, three form factors

**Time.** 90–150 minutes.
**Deliverable.** A multi-module project (`:domain`, `:app`, `:wear`) shipping one feature on phone, unfolded foldable, and Wear, plus a `FORM-FACTORS.md` decision log with screenshots of each surface and a written justification of each design choice. Committed to your Week 20 repo.

## The premise

Every junior multi-form-factor attempt looks the same: build the phone app, then "make it work" on the watch by shrinking the layout and the TV by stretching it. It compiles, it technically runs, and it is wrong on every screen — a watch app you have to dwell in, a foldable that ignores the hinge, a tablet that wastes half its width. The skill this challenge builds is the opposite instinct: **one shared domain layer, three deliberately *different* UIs, each shaped to its form factor's constraints — and the discipline to write down *why* each is shaped the way it is.** A port you can't justify is a port; a design you can defend is engineering.

You will ship one feature — a forecast browser — three ways, and the grading is the *appropriateness* of each surface plus your decision log, not the line count.

## What to build

A forecast browser over a small set of cities, each with an hourly forecast. The same data, three surfaces.

### Step 1 — The shared domain (`:domain`)

A pure-Kotlin module (no Android, no Compose) holding the model and a repository interface. Everything else consumes this; nothing duplicates it.

```kotlin
// :domain — pure Kotlin, shared by :app and :wear.
data class City(val id: String, val name: String)

data class HourForecast(val hour: Int, val tempC: Int, val condition: Condition)

enum class Condition { Clear, Cloud, Rain, Snow }

data class CityForecast(val city: City, val hours: List<HourForecast>) {
    val current: HourForecast get() = hours.first()
    val willRainSoon: Boolean get() = hours.take(3).any { it.condition == Condition.Rain }
}

interface ForecastRepository {
    suspend fun cities(): List<City>
    suspend fun forecast(cityId: String): CityForecast
}
```

A fake in-memory implementation is fine — the point is the *boundary*, not a real backend. (In the capstone this is the Week-19 KMP `:shared-core`; here a fake is acceptable.)

### Step 2 — Phone + foldable UI (`:app`)

An adaptive list-detail screen (lecture 1) that:

- Shows **one pane on COMPACT** (phone portrait), **two panes on MEDIUM/EXPANDED** (phone landscape, tablet, unfolded foldable), via `NavigableListDetailPaneScaffold`.
- In **tabletop posture** (`FoldingFeature` half-opened, horizontal), splits the *detail* pane around the hinge — e.g. the current conditions on top, the hourly list on the bottom.
- Reflows **live** when the window changes (drag the resizable emulator; pose the hinge).

```kotlin
@Composable
fun ForecastApp(activity: Activity, repo: ForecastRepository) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val fold = rememberFoldState(activity)   // your helper from lecture 1
    // ... NavigableListDetailPaneScaffold with a tabletop-aware detailPane ...
}
```

### Step 3 — Wear UI (`:wear`)

A genuinely Wear-native companion (lecture 2), consuming the *same* `:domain`:

- A **scaling list** (`TransformingLazyColumn`) of cities, in `AppScaffold`/`ScreenScaffold`, with **rotary input** and `SwipeDismissableNavHost` navigation to a per-city detail.
- A **tile** (`TileService` + ProtoLayout) showing the current temperature for the user's primary city, reading *cached* data, with a freshness interval and versioned resources.
- (Stretch) a **complication** supplying the current temperature as `SHORT_TEXT`, and/or an **ongoing activity** when `willRainSoon` is true.

None of these is a copy of the phone UI — they share the *domain*, not the *presentation*.

### Step 4 — The decision log (`FORM-FACTORS.md`)

This is the graded artifact. For each surface, write *why it is shaped the way it is*. A strong log has entries like:

- **Phone/foldable:** "Used `NavigableListDetailPaneScaffold` rather than a manual `when (widthClass)` because the scaffold handles the predictive-back behavior (compact: detail→list; expanded: in-place) that I would otherwise get wrong. In tabletop posture I split the detail around the hinge because the half-opened device naturally presents a top region and a bottom region."
- **Wear:** "Used `TransformingLazyColumn` not `LazyColumn` because the round screen curves away at top/bottom; the scaling list makes the edges look intentional. Added `rotaryScrollable` because the crown is the watch's primary scroll input. The tile reads a cached forecast — never a network call — because the system renders it with the app possibly dead and the network down."
- **What I did NOT build, and why:** "No TV or Automotive target. Weather has a plausible TV use case (a morning dashboard) but it is not this product's primary channel, and Automotive's distraction rules and certification are a serious investment unjustified here. Both are documented as deferred backlog items."

## Acceptance criteria

- [ ] A `:domain` module with no Android/Compose dependency, consumed by both `:app` and `:wear`. The model is defined once.
- [ ] `:app` reflows from one pane (COMPACT) to two (MEDIUM/EXPANDED) via the adaptive scaffold, *live*, with correct back behavior on each.
- [ ] `:app` detects tabletop posture and reflows the detail around the hinge.
- [ ] `:wear` uses `TransformingLazyColumn`, `SwipeDismissableNavHost`, rotary input, and `AppScaffold`/`ScreenScaffold` — no phone components.
- [ ] `:wear` ships a tile that reads cached data, versions resources, and sets a freshness interval. (Complication and/or ongoing activity for stretch credit.)
- [ ] No surface is a port of another: they share `:domain`, not presentation code. No `if (isTablet)`, no phone `LazyColumn` on Wear, no fetch-on-render in the tile.
- [ ] `FORM-FACTORS.md` justifies each surface's design and documents what you did *not* build (TV/Automotive) and why.
- [ ] Screenshots: phone (one pane), tablet/unfolded (two panes), tabletop (hinge split), Wear list, Wear tile.
- [ ] Both modules build with **0 warnings**.

## What "great" looks like

A weak submission says "it runs on phone, foldable, and watch." A great submission says:

> One `:domain` module defines `CityForecast` and `ForecastRepository`; `:app` and `:wear` both consume it and share zero presentation code. On the Resizable emulator, `:app` shows a single pane below 600dp and a list-detail split above it, reflowing live as I drag the window — the `NavigableListDetailPaneScaffold` handles the predictive-back difference between the two so I never wrote a `when (widthClass)`. Posing the hinge to half-opened-horizontal flips the detail pane into a top/bottom split around the hinge bounds. On the Wear emulator, the companion uses `TransformingLazyColumn` so the city list scales toward the curved edges, the crown scrolls it via `rotaryScrollable`, and the left-edge swipe dismisses via `SwipeDismissableNavHost`. The tile reads a cached forecast synchronously and sets a 15-minute freshness interval; when the (fake) sync writes new data it calls `getUpdater(...).requestUpdate(...)`, so the tile never fetches on render. I deliberately did not build TV or Automotive: weather has a marginal TV case and no Automotive case for this product, and both are documented as deferred with the specific APIs (`tv-material`, the Car App Library) a future implementation would use.

Shared domain, three appropriate surfaces, and an honest account of the boundary. That's the senior multi-form-factor answer.

## Where this reappears

This is the exact shape of the capstone's `:shared-core` (KMP) + `:app` (phone/foldable) + `:wear` (Wear) split — one domain, multiple form-factor-appropriate UIs. The "right surface per form factor" judgment you exercise here is graded directly there. And Week 21 picks up where this leaves off: a CI/CD pipeline that builds, tests, and *ships* both the `:app` AAB and the `:wear` APK on every tag, so multi-form-factor stops being a manual build dance.
