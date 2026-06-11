// Exercise 3 — A Wear tile with ProtoLayout: a serialized layout, not a composition
//
// Goal: Author a TileService that renders a glanceable forecast card from CACHED
//       data using the protolayout-material3 builders, version its resources, and
//       set a freshness interval. The lesson you must feel: a tile is NOT a
//       @Composable. There is no recomposition, no state, no side effects. You
//       build a serialized layout tree of data and the system renders it — in its
//       own process, on its own schedule, even when your app is not running. That
//       is why the data must be cheap and cached, never fetched on render.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// This service lives in your `:wear` module. Register it in the Wear manifest (see
// the manifest block at the bottom) and run on a Wear OS emulator. Long-press the
// watch face, add a tile, pick yours, and confirm it renders the cached forecast.
//
// DEPENDENCIES (in :wear/build.gradle.kts):
//
//   implementation("androidx.wear.tiles:tiles:1.4.0")
//   implementation("androidx.wear.protolayout:protolayout:1.2.0")
//   implementation("androidx.wear.protolayout:protolayout-material3:1.2.0")
//   implementation("com.google.guava:guava:33.0.0-android")   // ListenableFuture
//   (use the latest stable versions your project resolves)
//
// ACCEPTANCE CRITERIA
//
//   [ ] onTileRequest reads CACHED data only — no network, no blocking I/O on the
//       render path. (Here we read an in-memory cache; in the mini-project it's a
//       fast local store populated by the phone's WorkManager sync.)
//   [ ] The layout is built with protolayout-material3 (materialScope/primaryLayout),
//       so the tile looks like a system tile.
//   [ ] A freshness interval is set (the system is ASKED to refresh periodically).
//   [ ] Resources are versioned, and the tile's resourcesVersion matches the
//       Resources version.
//   [ ] You can push an immediate refresh with TileService.getUpdater(...).
//   [ ] Builds with 0 warnings.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.weather.wear.tile

import android.content.Context
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

// ----------------------------------------------------------------------------
// A trivially small "cache". In a real app this is a Room/DataStore read of the
// latest forecast the phone synced. The POINT is that it is FAST and LOCAL — the
// tile reads it synchronously and never blocks on a network call.
// ----------------------------------------------------------------------------

data class CachedForecast(val tempC: Int, val condition: String, val city: String)

object ForecastCache {
    // Seeded with a placeholder; the real app writes this from its sync job.
    @Volatile
    private var latest: CachedForecast = CachedForecast(tempC = 14, condition = "Cloud", city = "London")

    fun latest(context: Context): CachedForecast = latest   // synchronous, no I/O on render

    fun update(forecast: CachedForecast) {
        latest = forecast
    }
}

// Bump this whenever the IMAGE/resource set changes so the system re-requests
// resources. The Tile's resourcesVersion and the Resources' version must agree.
private const val RESOURCES_VERSION = "1"

// Ask the system to call onTileRequest again roughly every 15 minutes. This is a
// REQUEST — the system batches and may defer for battery.
private const val FRESHNESS_INTERVAL_MS = 15L * 60L * 1000L

// ----------------------------------------------------------------------------
// The TileService. Two requests: the tile (the layout) and the resources.
// ----------------------------------------------------------------------------

class ForecastTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        // Read CACHED data. Never fetch here — the tile may render with the app
        // dead and the network down.
        val forecast = ForecastCache.latest(this)

        // Build the layout with the Material 3 ProtoLayout builders so it looks
        // like a first-class system tile.
        val layout = materialScope(
            context = this,
            deviceConfiguration = requestParams.deviceConfiguration
        ) {
            forecastLayout(forecast)
        }

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)   // MUST equal the Tile's resourcesVersion
                // .addIdToImageMapping("ic_rain", imageResource(R.drawable.ic_rain)) // if used
                .build()
        )
}

// ----------------------------------------------------------------------------
// The layout. A MaterialScope extension that builds the glanceable card. Note:
// this returns a LayoutElement (serialized data), not a composition.
// ----------------------------------------------------------------------------

private fun MaterialScope.forecastLayout(forecast: CachedForecast) =
    primaryLayout(
        titleSlot = { text("${forecast.city}".layoutString) },
        mainSlot = { text("${forecast.tempC}°".layoutString) },
        bottomSlot = { text(forecast.condition.layoutString) }
    )

// ----------------------------------------------------------------------------
// PUSHING AN IMMEDIATE REFRESH. When your sync job writes fresh data, ask the
// system to re-render the tile now instead of waiting for the freshness interval:
//
//   fun onFreshForecast(context: Context, forecast: CachedForecast) {
//       ForecastCache.update(forecast)
//       TileService.getUpdater(context)
//           .requestUpdate(ForecastTileService::class.java)
//   }
//
// This is the clean pattern: the phone syncs -> writes the cache -> requests a
// tile update. The tile itself never fetches.
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
// MANIFEST registration (in :wear/src/main/AndroidManifest.xml, inside <application>):
//
//   <service
//       android:name=".tile.ForecastTileService"
//       android:exported="true"
//       android:label="Forecast"
//       android:permission="com.google.android.wearable.permission.BIND_TILE_PROVIDER">
//       <intent-filter>
//           <action android:name="androidx.wear.tiles.action.BIND_TILE_PROVIDER" />
//       </intent-filter>
//       <meta-data
//           android:name="androidx.wear.tiles.PREVIEW"
//           android:resource="@drawable/tile_preview" />
//   </service>
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
// WHY A TILE IS NOT A COMPOSITION (write it before reading):
//
//   The system renders tiles in ITS process, on ITS schedule, even when your app
//   is not running. It cannot run your live composition. So you hand it a
//   SERIALIZED layout tree (ProtoLayout) plus versioned resources, and it renders
//   that. Consequences:
//     - No state, no recomposition, no side effects. Render-once-per-request.
//     - Data must be cached and read synchronously — a fetch on render is a bug.
//     - Resources are versioned; bump the version when they change.
//     - The freshness interval REQUESTS a re-render; the system controls timing.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - Tile shows nothing / "couldn't load". Most often the resourcesVersion on the
//   Tile doesn't match the version on the Resources. They must be the same string.
//
// - Compile error on materialScope/primaryLayout. Add the protolayout-material3
//   dependency, and import from androidx.wear.protolayout.material3.
//
// - You're tempted to do a network call in onTileRequest. Don't. Return cached
//   data and request an update from your sync job when fresh data lands. The
//   ForecastCache.update + TileService.getUpdater pattern above is the right shape.
//
// - ListenableFuture unresolved. Add Guava (com.google.guava:guava:*-android).
//   onTileRequest/onTileResourcesRequest return ListenableFuture; for synchronous
//   work wrap with Futures.immediateFuture(...).
//
// - Freshness interval seems ignored. It's a request, not a guarantee — the system
//   batches it. To see an immediate refresh, call TileService.getUpdater(context)
//   .requestUpdate(ForecastTileService::class.java) after updating the cache.
//
// ----------------------------------------------------------------------------
