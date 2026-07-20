# Mini-Project — Wear OS weather companion: tile, complication, ongoing activity

This week you build a **Wear OS companion** to the Week-19 weather app — a genuinely wrist-native experience surfaced three ways, each on its own correct API: a **tile** that shows the current forecast at a glance, a **complication** that drops the temperature onto the user's watch face, and an **ongoing activity** that appears when a rain alert is active. The companion consumes the same `WeatherForecast` domain model the Week-19 KMP `:shared-core` exposes; it shares the *domain*, not the presentation.

The point of the project is not "make a small weather app." It is to build the three Wear surfaces *correctly* — a tile that is a serialized ProtoLayout reading cached data (not a composition doing a fetch), a complication that supplies the right `ComplicationType` for the slot, and an ongoing activity bound to a notification for a genuinely ongoing task — and to *feel* why each is a different API. That "tile ≠ composition ≠ complication ≠ ongoing activity" instinct is the senior Wear skill this week installs, and it is exactly what the capstone's `:wear` module is graded on.

This is a `:wear` module added to your project (or a fresh Wear app if you skipped Week 19's app — build a minimal `WeatherForecast` stub first). The phone-side sync, persistence, and architecture come from earlier weeks; here you consume a cached forecast and present it on the watch.

---

## Where you're starting from

- The Week-19 `:shared-core` `WeatherForecast` model (or a minimal stub: `data class WeatherForecast(val city: String, val tempC: Int, val condition: String, val rainSoon: Boolean)`).
- A way to read the *latest cached* forecast on the watch — a local `ForecastCache` (Room/DataStore in the real app; an in-memory object is acceptable for this mini-project, seeded by a fake refresh).
- A Wear OS module: **New ▸ Module ▸ Wear OS**, minSdk 26, the Wear Compose, Tiles, ProtoLayout, Complications, and Ongoing libraries (see the README toolchain note).

## What you're building toward

By the end you have:

- A Wear **app** screen (scaling list of the next hours, navigated with the swipe-dismiss host) — the "open the app" surface.
- A **tile** (`ForecastTileService`) showing the current temperature and condition, glanceable from the tile carousel, reading cached data with a freshness interval and versioned resources.
- A **complication** (`TemperatureComplicationService`) supplying the current temperature as `SHORT_TEXT` to a watch-face slot.
- An **ongoing activity** that appears when `rainSoon` is true and clears when it is false, bound to a notification, with a live status.
- A short clip or screenshot sequence in your README showing all three surfaces.

---

## Milestone 1 — The cache and the domain boundary (≈ 1 h)

The three surfaces all read *one* cached forecast. Define the boundary so none of them fetches:

```kotlin
// The model shared with the phone (from :shared-core, or this stub).
data class WeatherForecast(
    val city: String,
    val tempC: Int,
    val condition: String,
    val rainSoon: Boolean
)

// A fast, synchronous local read. In the real app this is a Room/DataStore query
// populated by the phone's WorkManager sync (Week 16). Here, in-memory is fine.
object ForecastCache {
    @Volatile private var latest: WeatherForecast =
        WeatherForecast(city = "London", tempC = 14, condition = "Cloud", rainSoon = false)

    fun latest(): WeatherForecast = latest

    /** Called by the (fake) sync. Updates the cache and refreshes the surfaces. */
    fun update(context: Context, forecast: WeatherForecast) {
        latest = forecast
        ForecastSurfaces.refreshAll(context, forecast)   // tile + complication + ongoing
    }
}
```

Decisions you must be able to defend in review:

- **Why does every surface read a cache, never the network?** Because tiles and complications are rendered by the *system*, possibly while your app is dead and the network is down. A fetch on render renders stale or blank. The cache is the contract: the phone syncs and writes it; the surfaces read it.
- **Why centralize the refresh in `ForecastSurfaces.refreshAll`?** Because when fresh data lands, *all three* surfaces must update together — a tile showing 14° while the complication shows 19° is a bug. One refresh entry point keeps them consistent.

## Milestone 2 — The Wear app screen (≈ 1.5 h)

The "open the app" surface: a scaling list of the next hours, navigable with the swipe-dismiss host (exercise 2 is your template).

```kotlin
@Composable
fun WeatherWearApp(forecast: WeatherForecast, hours: List<HourForecast>) {
    val navController = rememberSwipeDismissableNavController()
    AppScaffold {
        SwipeDismissableNavHost(navController, startDestination = "today") {
            composable("today") {
                val listState = rememberTransformingLazyColumnState()
                ScreenScaffold(scrollState = listState) {
                    TransformingLazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().rotaryScrollable(
                            RotaryScrollableDefaults.behavior(scrollableState = listState),
                            focusRequester = rememberActiveFocusRequester()
                        )
                    ) {
                        item { ListHeader { Text("${forecast.city}  ${forecast.tempC}°") } }
                        items(hours, key = { it.hour }) { h ->
                            Button(onClick = { navController.navigate("hour/${h.hour}") }) {
                                Text("%02d:00  %d°  %s".format(h.hour, h.tempC, h.condition))
                            }
                        }
                    }
                }
            }
            composable("hour/{hour}") { entry ->
                HourDetailScreen(hours.firstOrNull { it.hour == entry.arguments?.getString("hour")?.toIntOrNull() })
            }
        }
    }
}
```

The app screen *is* Compose, so recomposition and stability (Weeks 7–8) apply — and on a watch they matter for battery. Keep the list rows skippable (immutable `HourForecast`, keyed list).

## Milestone 3 — The tile (≈ 1.5 h)

The glanceable surface. A `TileService` that renders the current forecast from the cache with the `protolayout-material3` builders (exercise 3 is your template):

```kotlin
private const val RESOURCES_VERSION = "1"
private const val FRESHNESS_MS = 15L * 60L * 1000L

class ForecastTileService : TileService() {
    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val f = ForecastCache.latest()                 // cached read — NO fetch
        val layout = materialScope(this, requestParams.deviceConfiguration) {
            primaryLayout(
                titleSlot = { text(f.city.layoutString) },
                mainSlot = { text("${f.tempC}°".layoutString) },
                bottomSlot = { text(f.condition.layoutString) }
            )
        }
        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(FRESHNESS_MS)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
                .build()
        )
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        )
}
```

Register it in the Wear manifest with the tile-provider intent filter and the `BIND_TILE_PROVIDER` permission (exercise 3's manifest block). The refresh on fresh data:

```kotlin
// In ForecastSurfaces.refreshAll:
TileService.getUpdater(context).requestUpdate(ForecastTileService::class.java)
```

The tile is a *serialized layout the system renders*. There is no state, no recomposition. If you find yourself reaching for `remember` or a coroutine in the tile, you have the wrong mental model — go back to lecture 2, §3.

## Milestone 4 — The complication (≈ 1 h)

A single datum on the watch face. A `ComplicationDataSourceService` supplying the current temperature as `SHORT_TEXT`:

```kotlin
class TemperatureComplicationService : ComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) shortText("21°", "Temp") else null

    override fun onComplicationRequest(request: ComplicationRequest, listener: ComplicationRequestListener) {
        if (request.complicationType != ComplicationType.SHORT_TEXT) { listener.onComplicationData(null); return }
        listener.onComplicationData(shortText("${ForecastCache.latest().tempC}°", "Temp"))   // cached
    }

    private fun shortText(text: String, desc: String) = ShortTextComplicationData.Builder(
        text = PlainComplicationText.Builder(text).build(),
        contentDescription = PlainComplicationText.Builder(desc).build()
    ).build()
}
```

Declare `SUPPORTED_TYPES=SHORT_TEXT` and the complication intent filter in the manifest. Push refreshes from `ForecastSurfaces.refreshAll` with `ComplicationDataSourceUpdateRequester`. The discipline is the same as the tile: read the cache, never fetch, and supply the *exact* `ComplicationType` the slot wants — a mismatched type renders nothing.

## Milestone 5 — The ongoing activity (≈ 1 h)

A live rain alert. When `rainSoon` becomes true, post an ongoing notification with an attached `OngoingActivity`; when it becomes false, cancel it.

```kotlin
fun syncRainAlert(context: Context, forecast: WeatherForecast, channelId: String) {
    val nm = NotificationManagerCompat.from(context)
    if (!forecast.rainSoon) { nm.cancel(RAIN_ID); return }

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_rain)
        .setContentTitle("Rain incoming")
        .setContentText("Rain expected soon in ${forecast.city}")
        .setOngoing(true)
        .setContentIntent(openAppPendingIntent(context))

    val ongoing = OngoingActivity.Builder(context, RAIN_ID, builder)
        .setStaticIcon(R.drawable.ic_rain)
        .setTouchIntent(openAppPendingIntent(context))
        .setStatus(Status.Builder().addTemplate("Rain soon in ${forecast.city}").build())
        .build()

    ongoing.apply(context)                       // decorate the surfaces
    nm.notify(RAIN_ID, builder.build())
}
```

Wire `syncRainAlert` into `ForecastSurfaces.refreshAll` so the alert appears and clears with the data. Use it *only* for the genuinely-ongoing rain window — an ongoing activity that lingers after the rain passed is the watch equivalent of a stuck notification.

## Milestone 6 — Consistency and the demo (≈ 0.5 h)

Drive a fake refresh (a button in the app, or a debug menu) that calls `ForecastCache.update(context, newForecast)` and confirm **all three surfaces move together**: the tile re-renders, the complication updates, and the rain alert appears or clears. Capture the demo: the tile in the carousel, the complication on a watch face, the rain alert on the ongoing-activity surface. Record it in your README.

---

## Acceptance criteria

- [ ] One `ForecastCache` (or local store) is the single source the tile, complication, and ongoing activity all read. None of the three fetches on render.
- [ ] The Wear **app** uses `TransformingLazyColumn`, `SwipeDismissableNavHost`, rotary input, and `AppScaffold`/`ScreenScaffold` — no phone components.
- [ ] The **tile** is a `TileService` rendering cached data via `protolayout-material3`, with a freshness interval and matching resource versions; refreshed via `getUpdater(...).requestUpdate(...)`.
- [ ] The **complication** supplies `SHORT_TEXT` (preview + live), declared in the manifest, refreshed via `ComplicationDataSourceUpdateRequester`.
- [ ] The **ongoing activity** appears when `rainSoon` is true and is cancelled when false, bound to a notification with a live status.
- [ ] A fake refresh updates **all three surfaces consistently** (no surface shows stale data while another is fresh).
- [ ] A short clip or screenshot sequence in the README shows the tile, the complication, and the rain alert.
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **RANGED_VALUE complication.** Add a second complication that renders the chance-of-rain as a `RANGED_VALUE` arc (value/min/max), so a watch face that prefers an arc slot can use it. Prove you supply the right type per slot.
- **Tile with a refresh button.** Add a tappable element to the tile that opens the app or requests a fresh sync (a `Clickable` ProtoLayout modifier with a `LoadAction`/`launchAction`). Keep the data read cached.
- **Battery-aware refresh.** Lengthen the freshness interval and rely on push refreshes from the phone sync instead of frequent polling. Note in the README why a shorter interval costs battery on the watch.
- **Phone↔Wear data layer.** If you want the real thing, wire the Wearable Data Layer (MessageClient/DataClient) so the phone pushes the forecast to the watch's cache instead of a fake. This is the capstone-grade path.

## What this milestone earns you

You can now build the three Wear surfaces an engineer actually ships — the app, the tile, and the complication — each on its correct API, plus an ongoing activity for live tasks, all over one cached domain boundary, and you can explain *why* a tile is not a composition and *why* a complication needs the right `ComplicationType`. That is the literal "skill earned" line for the week: Wear OS tile and complication authoring, and knowing what *not* to build for TV and Automotive. The capstone's `:wear` module is this mini-project at production scale — a tile, a complication, and an ongoing activity for active dispatches — and Week 21 next teaches you to *release* it: a signed Wear APK shipped alongside the phone AAB by CI on every tag.
