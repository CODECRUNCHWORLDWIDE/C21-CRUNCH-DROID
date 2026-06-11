# Lecture 2 — Compose for Wear OS: the wrist is a different screen

> "A watch is not a small phone. It's a glanceable surface a few centimeters across, round, on a wrist that's moving, interacted with for two seconds at a time. Shrink your phone UI onto it and you've built something technically functional and genuinely unusable."

Lecture 1 made your business core portable. This lecture takes it to your second form factor — the wrist. Compose for Wear OS *is* Compose: everything you learned about composition, recomposition, state, and side effects (Weeks 7–12) carries over unchanged. What's different is the **component set** and the **design constraints**: a round screen, a glance-length interaction, a different list (`ScalingLazyColumn`), a built-in clock (`TimeText`), and whole surfaces that aren't activities at all (tiles, complications, ongoing activities). By the end you can build a first Wear screen that renders the shared `WeatherForecast` model, and you'll understand what to share, what to port, and what to rebuild for the wrist.

The frame for this lecture is one sentence: **the business core travels unchanged from lecture 1, but the UI is rebuilt for the surface — share the core, build the skin, and never shrink the phone screen onto the watch.** Week 20 goes deep on Wear; this lecture is the foundation and the first screen.

---

## 1. What makes the wrist different

Before any code, the constraints that drive every Wear design decision. A watch differs from a phone on five axes that matter:

- **Size.** The screen is roughly 1.2–1.4 inches. There's room for a few lines of text and one or two actions, not a dense feed. Information density that's fine on a phone is unreadable on a watch.

  A concrete way to feel this: a typical phone screen is ~6 inches diagonally with thousands of square millimeters of usable area; a watch is a fraction of that, and the *round* shape further reduces the usable rectangle. So a phone screen that comfortably holds twenty pieces of information holds maybe three on a watch, legibly. The constraint isn't "make everything smaller" — text below a legibility threshold is useless — it's "show fewer things." That subtraction is the whole game, and it's covered as its own discipline below.

- **Shape.** Most Wear devices are *round*. Content near the corners gets clipped by the bezel; text that's left-aligned to the edge runs off the curve. Layouts must respect the circle, which is why Wear has components built to curve and inset content automatically.
- **Glance length.** Users look at a watch for **one to three seconds**. The interaction model is "raise wrist, get the answer, drop wrist." A flow that takes ten taps belongs on the phone; the watch shows the *one* thing that matters now.
- **Input.** A tiny touch target, a rotating side button or bezel (the "rotary input"), and voice. No keyboard worth typing on. Interactions are taps, swipes, and rotary scrolls — not text entry.
- **Battery and always-on.** The watch has a tiny battery and an always-on ambient mode (a dimmed, low-power display when the wrist is down). Your UI has to behave in both interactive and ambient states.

Every Wear component exists to serve these constraints. When you reach for a Wear component instead of its phone equivalent, you're getting round-screen handling, glance-optimized sizing, and ambient-mode awareness baked in. That's why **you don't just reuse phone composables on Wear** — you use the Wear component set.

### Glanceability as a design discipline

The deepest of these constraints is the glance length, because it changes not just sizing but *what you show at all*. On a phone you can afford a screen with a header, a hero image, a five-row list, a footer, and three actions — the user has the time and the screen to take it in. On a watch, a screen with that much content is a failure even if every element is perfectly sized, because the user has *two seconds* and will not scroll a wall of information on their wrist. The discipline is **ruthless subtraction**: for any phone screen, ask "what is the *one* thing a user needs from this in two seconds?" and show that, with at most one secondary action. A weather phone screen shows hourly *and* daily forecasts, radar, and details; the watch screen shows the current temperature and condition, and maybe the next few hours — that's it. Everything else is "open the phone."

This is why "port the phone screen to Wear" produces bad watch apps even when the components are correct: porting preserves the phone's *information architecture*, and the phone's architecture assumes a phone's attention budget. The Wear redesign isn't a smaller layout — it's a *smaller answer*. Designing for the wrist is mostly deciding what to leave out, and the engineers who do it well think in glances, not screens.

## 1a. Wear theming

Wear has its own `MaterialTheme` (from `androidx.wear.compose.material3`), with a `ColorScheme` and `Typography` tuned for the watch — higher contrast (legibility on a small screen in sunlight) and a type scale sized for glances. You theme a Wear app much as you themed the phone in Week 11, but with the Wear theme classes:

```kotlin
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ColorScheme

@Composable
fun ForecastWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme(/* Wear-tuned colors */),
        content = content
    )
}
```

Two Wear-specific theming notes. First, **dark backgrounds are the default and the right default** — a watch with an OLED screen saves battery on black pixels, and most watch faces are dark, so a dark app feels native and lasts longer. Don't port a light-themed phone app to a bright-white watch screen. Second, **contrast matters more** — the user is glancing, often outdoors, often mid-motion. The Week 9 accessibility-and-contrast discipline isn't a nice-to-have on Wear; it's the difference between a usable and an illegible watch app.

## 2. The Wear Compose artifacts

Compose for Wear OS is a *separate* set of artifacts from phone Compose, under `androidx.wear.compose.*`:

```kotlin
// Wear app module build.gradle.kts
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.wear.compose:compose-material3:1.0.0")   // Wear Material 3
    implementation("androidx.wear.compose:compose-foundation:1.4.0")  // Wear foundation (ScalingLazyColumn etc.)
    implementation("androidx.wear.compose:compose-navigation:1.4.0")  // Wear navigation
    // plus the standard Compose runtime/ui from the BOM
}
```

Crucially: you use `androidx.wear.compose.material3.*` for components, **not** `androidx.compose.material3.*`. The Wear versions — `Button`, `Chip`, `ToggleChip`, `Card`, `Text` — are shaped and sized for the wrist. Mixing in phone Material components on Wear is a common beginner mistake that produces UI that's the wrong size and ignores the round screen. The runtime (`androidx.compose.runtime.*`) and the UI primitives (`androidx.compose.ui.*` — `Modifier`, `Box`, layout) are *shared* with phone Compose; it's the *Material component layer* that's Wear-specific.

## 3. The Wear `Scaffold` and `TimeText`

A Wear screen starts with the Wear **`Scaffold`** — not the phone one. It provides the slots a watch screen needs: the time at the top, a scroll position indicator, a page indicator, and the main content.

```kotlin
import androidx.wear.compose.material3.Scaffold
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.Text

@Composable
fun ForecastWearApp() {
    Scaffold(
        timeText = { TimeText() },          // the always-present clock at the top
    ) {
        // main content goes here
        Text("Loading…")
    }
}
```

`TimeText` is the canonical example of a Wear-specific component: it renders the current time, curved along the top of the round screen, and it's *expected* on essentially every Wear screen — users glance at a watch and expect to see the time. The Wear `Scaffold` gives it a dedicated slot. You almost always include it; leaving it off makes a Wear screen feel broken.

The `Scaffold` also wires up the **vignette** (a subtle darkening at the screen edges so scrolling content fades rather than hard-clipping at the bezel) and the **position indicator** (the scrollbar-like arc on the right that shows where you are in a scrolling list). These are round-screen affordances you'd have to build by hand on a phone; on Wear they come with the `Scaffold`.

## 4. `ScalingLazyColumn`: the list built for a circle

The single most important Wear-specific component is **`ScalingLazyColumn`** — the Wear analogue of `LazyColumn`. It's a lazy, scrolling list (same virtualization, same `items { }` API you know from Week 9), but with two behaviors built for the round screen:

- **Scaling.** Items near the *center* of the screen are rendered at full size; items toward the top and bottom edges *scale down and fade*. This focuses attention on the centered item — the one the user is looking at — and gracefully handles the fact that the round screen's usable area is widest in the middle.
- **Auto-centering.** The list centers its content, so the first and last items can scroll to the middle of the screen rather than being stuck at the very top/bottom edge where the bezel clips them.

```kotlin
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.Text

@Composable
fun ForecastList(forecasts: List<ForecastRow>) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        state = listState,
    ) {
        items(forecasts, key = { it.id }) { row ->     // keyed, just like phone LazyColumn (Week 7)
            Card(onClick = { /* open detail */ }) {
                Text(row.label)
                Text(row.temperature)
            }
        }
    }
}
```

Notice how much is *the same*: `items` with a `key` (the Week 7 stability lesson applies identically — keyed lists on Wear too), `rememberScalingLazyListState` mirroring `rememberLazyListState`, the lazy virtualization. The Wear-specific part is the *scaling and centering behavior*, which you get for free by using `ScalingLazyColumn` instead of `LazyColumn`. The discipline from earlier weeks — stable items, keyed lists, deferred reads for animation — all carries to the wrist unchanged; only the component changes.

### Rotary input: the side button and bezel

One Wear input has no phone equivalent and deserves a note: **rotary input.** Wear devices have either a rotating side crown (like the Pixel Watch) or a rotating bezel, and users scroll long lists by turning it rather than dragging the tiny screen. `ScalingLazyColumn` supports rotary scrolling out of the box when you connect it, which you do by giving the scrollable a focus and the rotary modifier:

```kotlin
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.wear.compose.foundation.rotary.rotaryScrollable

@Composable
fun RotaryAwareList(forecasts: List<ForecastRow>) {
    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .rotaryScrollable(listState, focusRequester)   // wire the crown/bezel to this list
            .focusRequester(focusRequester)
            .focusable()
    ) {
        items(forecasts, key = { it.id }) { /* ... */ }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }   // grab rotary focus on entry
}
```

The lesson: scrolling on Wear isn't only touch. A list that only responds to drags but not the crown feels broken to a watch user who instinctively spins the bezel. Wiring rotary input is a small, expected piece of Wear polish — and it's a concrete example of "the wrist is a different surface," because there's simply no analogue on a phone.

### Ambient mode

The other no-phone-equivalent constraint: **ambient mode.** When the user drops their wrist, the watch dims to a low-power, low-color always-on display rather than turning off. A well-behaved Wear app simplifies its UI in ambient mode — fewer colors, no animations, the essential information only — and resumes the rich interactive UI when the wrist is raised. For an introductory screen you can lean on the system defaults, but know that ambient mode exists and that a production Wear app handles it deliberately (Week 20 goes deeper). The principle is the same battery-respect that governs everything on the wrist: do the minimum work the moment requires.

## 5. Wear Material components

The Wear Material 3 component set is shaped for glance-length interaction. The ones you'll use most:

- **`Button`** — a circular icon button, sized for a fingertip on a tiny screen. Often the primary action.
- **`Card`** — a tappable content container, the workhorse for list rows (as above).
- **`Chip`** — a wide, pill-shaped row with an optional icon, label, and secondary label. The most common Wear list item — it spans the screen width and is easy to tap.
- **`ToggleChip` / `SwitchButton`** — a chip with a toggle, for settings.
- **`Text`** — Wear's text, with default styles tuned for watch legibility (larger, higher-contrast than phone defaults).

```kotlin
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text

@Composable
fun RefreshButton(onRefresh: () -> Unit) {
    Button(onClick = onRefresh) {
        Icon(Icons.Default.Refresh, contentDescription = "Refresh forecast")
    }
}
```

The accessibility lesson from Week 9 and Week 17 is *more* important on Wear, not less: the screen is tiny, voice (with the watch's assistant) and TalkBack are heavily used, and content descriptions on icon buttons are non-negotiable. A Wear UI built with proper semantics is one a user can drive by voice when their hands are full — which on a watch is often.

### Wear navigation and the swipe-to-dismiss gesture

Wear has its own navigation library (`androidx.wear.compose.navigation`) with a `SwipeDismissableNavHost` — because the *back gesture on Wear is a swipe from the left edge*, not a system back button. A Wear screen that doesn't honor swipe-to-dismiss feels trapped to a watch user. The Wear nav host wires this for you:

```kotlin
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

@Composable
fun ForecastWearNav() {
    val navController = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(navController = navController, startDestination = "list") {
        composable("list") {
            ForecastListScreen(onOpen = { navController.navigate("detail/$it") })
        }
        composable("detail/{hour}") { /* detail screen; swipe-left to go back */ }
    }
}
```

The concept maps cleanly onto Navigation from Week 10 — a nav controller, a host, typed-ish routes — but the *host* is Wear-specific because the *gesture* is. This is the recurring shape of the whole lecture: the architecture (navigation, state, lists) is the architecture you know; the *surface-specific component* is what changes. Use the Wear nav host on Wear, and the left-swipe back gesture works the way watch users expect.

## 6. What Wear is *not*: tiles, complications, ongoing activities

Here's the conceptual leap that separates "I ported a screen to Wear" from "I understand Wear": **a Wear app's most-used surfaces are not activities, and not even Compose screens.** Your main Wear activity (the `ScalingLazyColumn` app above) is one surface, but it's the one users open *least*. The glanceable surfaces — the ones a user actually sees dozens of times a day — are separate:

### Tiles

A **tile** is a glanceable, swipeable surface the user reaches by swiping from the watch face — no app launch, no activity. It shows a snapshot of information (today's forecast, your next calendar event, your step count) and maybe one or two tap targets. It is **not** an activity and **not** built with Compose UI the same way (tiles use a separate, more constrained layout API — `androidx.wear.tiles` / the `ProtoLayout` system — because they must render quickly and cheaply, even when your app process isn't running). A tile is "the answer, without opening the app." For a weather app, the tile shows the current forecast at a glance; tapping it might open the full app.

### Complications

A **complication** is data your app provides *to a watch face* — the little temperature reading or step count embedded in the corner of the user's chosen watch face. Your app doesn't draw it; it provides the *data* (a value, a short text, an icon, a range) via a `ComplicationDataSourceService`, and the watch face renders it in its own style. A weather complication feeds the current temperature to whatever watch face the user runs. It's the most glanceable surface of all — the user sees it every time they check the time.

### Ongoing activities

An **ongoing activity** is a persistent surface for an *active, ongoing task* — a running workout, a playing media session, an active timer. It surfaces on the watch face and in the system UI so the user can get back to the in-progress task quickly. For the capstone's field-force app, an ongoing activity surfaces an *active dispatch* so the worker can return to it instantly.

The takeaway for *this* week: **know these exist and what each is for.** Tiles, complications, and ongoing activities are separate surfaces with their own APIs, their own constraints (a tile must render without your app running; a complication is data, not UI), and their own authoring models. You will *build* them in Week 20; this week you build the main Compose screen and understand that the wrist's real estate is mostly these glanceable surfaces, not the app screen. Designing a Wear experience means asking "what's the tile? what's the complication?" *first* — the full app screen is the least-used part.

### Why tiles use a different API (and why that's not an oversight)

A reasonable question: if Wear is Compose, why isn't a tile just a composable? The answer is a constraint that shapes the whole surface: **a tile must render even when your app's process is not running.** The system draws tiles as the user swipes, instantly, dozens of times a day, and it cannot afford to cold-start your whole app to do it. So a tile is *declarative data* — a `ProtoLayout` describing what to draw — that your `TileService` produces and the system renders in its own process. There's no live composition, no recomposition, no coroutine running your business logic at draw time; there's a layout description the system caches and paints. (Glance, the Jetpack library, lets you *author* tiles with a Compose-like DSL that compiles down to `ProtoLayout`, which softens the API difference — but the underlying constraint, "renders without your process," remains.)

This is the same kind of reasoning you met with the Baseline Profile and App Startup in Week 18 — the system is fiercely protective of the resources and the latency of a glanceable surface, so it constrains *how* you can build for it. Understanding *why* the tile API is different makes it feel less like an arbitrary second way to build UI and more like a direct consequence of "this must be instant and cheap." Week 20 has you author one; this week, the point is to understand that the wrist's most-used surfaces earn their separate APIs by being even more performance-constrained than the app screen.

## 7. What to share, what to port, what to rebuild

Put lectures 1 and 2 together into the decision framework for a multi-form-factor system:

- **Share the business core (KMP / a shared Android module).** The `WeatherForecast` model, the Ktor repository, the business rules — identical on phone and watch. The Wear app depends on the *same* shared core the phone app does. This is the lecture-1 win: write the forecast logic once.
- **Port the UI *concepts*, rebuilt in Wear idioms.** The phone shows a forecast list; the watch shows a forecast list too — but with `ScalingLazyColumn` not `LazyColumn`, `Chip` rows not phone list items, a glance-length subset of the data not the full detail. You port the *idea* (show the forecast), not the *code* (the phone screen). Shrinking the phone composable onto the watch is the anti-pattern; rebuilding it in Wear components is the craft.
- **Rebuild the glanceable surfaces fresh.** A tile is not a screen — it's a separate `ProtoLayout` surface with its own API. A complication is not UI — it's a data service. These have no phone equivalent to port; you build them new, for the watch's unique surfaces. (Week 20.)

The unifying principle, true across iOS, the phone, and the wrist: **the core travels, the skin is built for the surface.** A `WeatherForecast` is a `WeatherForecast` everywhere; how it's *shown* is the platform's job, rebuilt for each surface's constraints. That's the same "share the business layer, not the UI" rule from lecture 1, now spanning not just two operating systems but multiple form factors within Android itself.

## 7a. Standalone vs. paired, and the data layer

One architectural decision shapes a Wear app: is it **standalone** (it fetches its own data over the watch's own network/LTE/Wi-Fi) or **paired** (it gets data from the phone app over Bluetooth)? Modern Wear OS favors standalone apps — the watch has its own connectivity, and a standalone app works even when the phone is off or absent. For a standalone weather watch app, the Wear app holds the *same shared core* (the Ktor repository) and fetches the forecast itself. This is exactly why making the core portable in lecture 1 pays off: the Wear app isn't a thin remote control for the phone; it's a full client that reuses the shared business layer.

When you *do* need phone↔watch communication (the phone has data the watch can't easily fetch, or vice versa), the **Wearable Data Layer API** (`MessageClient`, `DataClient`, `CapabilityClient`) is the channel — messages and synced data items over the Bluetooth/Wi-Fi bridge. For this week's standalone design you won't need it, but know it exists for the cases where the watch genuinely depends on the phone. The senior instinct: prefer standalone (reuse the shared core on the watch) and reach for the data layer only when a piece of data truly lives on one device. A watch that can't show the weather because the phone is in another room is a worse watch.

## 7b. Testing and previewing a Wear screen

The testing discipline from Week 17 carries to Wear with one addition: **Wear-specific previews and screenshot configs.** Compose previews work on Wear with a Wear device spec, so you can iterate on the round-screen layout without an emulator:

```kotlin
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices

@WearPreviewDevices       // renders the preview on representative round + square Wear specs
@Composable
fun ForecastWearScreenPreview() {
    ForecastWearScreen(state = WearForecastUiState.Content(sampleLocation, sampleHours))
}
```

`@WearPreviewDevices` renders your composable on representative Wear device shapes (round and square, different sizes), so you catch the corner-clipping and round-screen issues *in the preview*, before the emulator. And the screenshot-testing tier from Week 17 (Roborazzi/Paparazzi) works on Wear too — a golden per Wear device spec catches round-screen regressions. The Compose UI test (`createComposeRule`, find by semantics) is *identical* on Wear; you drive a `Chip` by its content description exactly as you drove a phone button. Everything you learned about testing Compose applies; you just add Wear-shaped previews and goldens.

## 8. A first Wear screen, end to end

Bringing it together — a Wear screen rendering the shared forecast model:

```kotlin
@Composable
fun ForecastWearScreen(state: WearForecastUiState) {
    Scaffold(timeText = { TimeText() }) {
        when (state) {
            WearForecastUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            is WearForecastUiState.Content -> {
                val listState = rememberScalingLazyListState()
                ScalingLazyColumn(state = listState) {
                    item { Text(state.location, style = MaterialTheme.typography.titleMedium) }
                    items(state.hours, key = { it.hour }) { hour ->
                        Chip(
                            onClick = { },
                            label = { Text(hour.label) },
                            secondaryLabel = { Text(hour.temperature) }
                        )
                    }
                }
            }
            is WearForecastUiState.Error ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message)
                }
        }
    }
}
```

This is the *same* state-driven, `UiState`-sealed, render-by-state pattern from Week 12 — the architecture is identical to the phone. The only differences are the components (Wear `Scaffold`, `ScalingLazyColumn`, `Chip`, `TimeText`) and the *amount* of data shown (a glance-length subset). The `WearForecastUiState` is mapped from the *same* `WeatherForecast` the shared core provides, by a Wear `ViewModel` that consumes the *same* repository the phone uses. One core, two surfaces, native UI on each.

Trace the data flow once, because it's the whole architecture in miniature: the shared `KtorWeatherRepository` (lecture 1, `commonMain`) fetches a `WeatherForecast`; the Wear `ViewModel` (Wear-platform code) collects that into a `WearForecastUiState`, mapping the full forecast down to a glance-length `Content(location, hours)` at the boundary; `ForecastWearScreen` renders that state with Wear components. The phone app does the *exact same thing* with a phone `ViewModel` and phone components, mapping the same `WeatherForecast` to a richer `ForecastUiState` with more detail. Two view models, two UIs, one repository, one domain model. If the forecast API adds a field or fixes a parsing bug, you change it once in the shared core and both surfaces get it. *That* is the payoff of the whole week: the wrist and the phone (and, stubbed, the iPhone) are three skins over one core, and the core is the part you only write once.

The mapping-at-the-boundary detail is load-bearing and worth restating: the Wear `ViewModel` doesn't pass the raw `WeatherForecast` to the screen — it maps it to a *Wear-shaped* UI state that already dropped the data the watch won't show. The screen receives exactly what it renders, nothing more. This is the Week 12 domain-to-UI mapping doing double duty: it keeps the shared core UI-agnostic *and* it's where each surface decides how much of the shared data its glance budget can afford. The phone keeps it all; the watch keeps the essentials; the core stays the same.

## 8a. A Wear build checklist

Before you ship a Wear screen, the senior reviewer's checklist — most of it is "did you use the Wear thing, not the phone thing":

- **Wear components, not phone Material.** `androidx.wear.compose.material3.*`, not `androidx.compose.material3.*`. Mixing produces wrong-sized, round-screen-unaware UI.
- **A `TimeText` in the `Scaffold`.** Users expect the time on a watch screen; its absence reads as broken.
- **`ScalingLazyColumn` for scrolling lists**, with keyed `items` (the Week 7 stability lesson) and rotary input wired.
- **Swipe-to-dismiss via the Wear nav host.** The left-swipe back gesture must work.
- **A glance-length subset of the data.** Not the ported phone screen — the *one thing* the user needs in two seconds.
- **Dark theme, high contrast.** OLED battery and outdoor legibility both demand it.
- **Content descriptions on every icon.** Voice and TalkBack are heavily used on the wrist.
- **Wear-shaped previews and goldens.** `@WearPreviewDevices` catches round-screen issues before the emulator.

Run this checklist and your first Wear screen is genuinely a *watch* app, not a phone app squeezed onto a small display. The difference is mostly small, deliberate choices — each one is "use the surface's component and respect the surface's constraint."

## 9. Recap

The wrist is a different screen, and you now know how to render to it:

1. **A watch is not a small phone.** Round, tiny, glance-length, rotary-input, battery-and-ambient-constrained. Every Wear component exists to serve those constraints — so you use the Wear component set, not phone Material.
2. **Wear Compose is its own artifacts.** `androidx.wear.compose.*` — Wear `Scaffold`, `TimeText`, `ScalingLazyColumn`, Wear Material `Chip`/`Card`/`Button`. The runtime and UI primitives are shared; the Material layer is Wear-specific.
3. **`ScalingLazyColumn` is the round-screen list.** Items scale and fade toward the edges, content auto-centers — but the `items`/`key`/lazy-state discipline is identical to phone `LazyColumn`.
4. **Tiles, complications, and ongoing activities are separate surfaces.** A tile is a glanceable non-activity surface; a complication is data your app feeds a watch face; an ongoing activity surfaces an active task. They're the *most-used* Wear surfaces, and they're not Compose screens. (Built in Week 20.)
5. **Share the core, build the skin for the surface.** The same `WeatherForecast` and repository power the phone and the watch; the Wear UI is rebuilt in Wear idioms, not shrunk from the phone. The lecture-1 rule, now spanning form factors.
6. **Glanceability is subtraction.** A watch screen shows the *one* thing the user needs in two seconds, not a smaller copy of the phone's information architecture. The redesign is a smaller answer, not a smaller layout.
7. **The everyday-Wear surfaces are tiles and complications, not the app screen** — and they have their own APIs because they must render instantly and cheaply, even when your app isn't running. Design for the glance first.

The single sentence to carry out of both lectures: **the core travels, the skin is built for the surface.** A `WeatherForecast` is the same on an iPhone, an Android phone, and a watch; what differs is how each surface shows it and how much of it the surface's attention budget can afford. That is the entire architecture of a multi-form-factor product, and you now have both halves — the traveling core and the surface-built skin.

You now have a portable business core (lecture 1) and a first screen on a second form factor (lecture 2) — the architectural spine of a multi-form-factor system. The exercises drill the share/don't-share decision, an `expect`/`actual` pair, and a Wear screen; the challenge proves the shared core compiles for two platforms; the mini-project ships the whole thing. Next week, Week 20, goes deep on the form factors — adaptive layouts for foldables, full tile and complication authoring for Wear, and a tour of TV and Automotive. Build the core to travel; build each skin for its surface.
