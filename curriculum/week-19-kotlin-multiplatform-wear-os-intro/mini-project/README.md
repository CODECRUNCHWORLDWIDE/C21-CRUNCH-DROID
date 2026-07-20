# Mini-Project — `:shared-core` KMP + Android consumer + a Wear forecast screen

This week you build a Kotlin Multiplatform **`:shared-core` module** that exposes a typed `WeatherForecast` model, a Ktor-backed repository, and kotlinx-serialization wire format — consumed by an **Android app**, *stubbed for iOS*, and rendered on a **Compose for Wear OS** screen. By the end you have a business core that compiles for two platforms, an Android app that consumes it cleanly, and the same forecast model glanceable on the wrist.

The point of the project is not "build a weather app." It's to build a **portable core** and prove it travels: the `:shared-core` compiles for Android *and* iOS, the Android app and the Wear app both consume the *same* repository and domain model, and the iOS target compiles green even though you don't ship a Swift app. That portability — "one core, three skins: phone, watch, and (stubbed) iPhone" — is the senior instinct this week installs, and it's the architectural spine of the capstone's `:shared-core` and `:wear` modules.

This is the architecture from lectures 1 and 2 made real: share the business layer (KMP), build a native skin per surface (Compose on Android, Wear Compose on the watch), prove portability with the compiler.

---

## Where you're starting from

Android Studio with the Kotlin Multiplatform plugin, and a multi-module project shape from earlier weeks. You'll add:

- A `:shared-core` KMP module (`commonMain`, `androidMain`, `iosMain`, `commonTest`).
- An `:androidApp` (or your existing app) that consumes `:shared-core`.
- A `:wear` module (a Wear-targeted app) that consumes the *same* `:shared-core`.

## What you're building toward

By the end you have:

- A `:shared-core` with a `@Serializable WeatherForecast`, a `WeatherRepository` interface, a `KtorWeatherRepository`, and at least one `expect`/`actual` seam — all in `commonMain`, compiling for Android and iOS.
- A `commonTest` (Ktor `MockEngine` + `runTest`) that passes on both targets, covering the success and error paths.
- An Android app consuming `:shared-core` through a `ViewModel` that maps the shared `WeatherForecast` to a phone UI state.
- An iOS *stub*: the iOS target compiles (the framework builds); you don't write SwiftUI, but the green compile proves the core is portable.
- A Wear app consuming the *same* `:shared-core`, with a `ForecastWearScreen` (Wear `Scaffold`, `TimeText`, `ScalingLazyColumn`, `Chip`) rendering a glance-length forecast.
- A `README.md` documenting what's shared vs. platform and the green iOS compile.

---

## Milestone 1 — The `:shared-core` module and domain (≈ 1.5 h)

Create `:shared-core` with the multiplatform + serialization plugins, an `androidTarget()`, and an iOS target. Define the domain in `commonMain`:

```kotlin
@Serializable
data class WeatherForecast(
    val location: String,
    val temperatureCelsius: Double,
    val condition: Condition,
    val hours: List<HourlyForecast>
)
@Serializable data class HourlyForecast(val hour: Int, val temperatureCelsius: Double)
@Serializable enum class Condition { CLEAR, CLOUDY, RAIN, SNOW }

sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Failure(val message: String) : NetworkResult<Nothing>
}
```

Decisions you must defend in review:

- **Why is `WeatherForecast` in `commonMain` and not the Android app?** Because it's pure data, identical on every platform, and *both* the Android app and the Wear app (and a future iOS app) need it. Defining it once in the shared core is the whole point — fix a parsing bug once, every consumer benefits.
- **Why `@Serializable` and not Gson annotations?** kotlinx-serialization is multiplatform; Gson is JVM-only and would break the iOS compile. The library choice *is* the portability constraint (lecture 1, §4).

## Milestone 2 — The Ktor repository (≈ 1.5 h)

Write the `WeatherRepository` interface and the `KtorWeatherRepository` in `commonMain`, with the platform HTTP engine injected:

```kotlin
interface WeatherRepository {
    suspend fun forecast(location: String): NetworkResult<WeatherForecast>
}

class KtorWeatherRepository(private val client: HttpClient) : WeatherRepository {
    override suspend fun forecast(location: String): NetworkResult<WeatherForecast> =
        try {
            NetworkResult.Success(
                client.get("https://api.example.com/forecast") { parameter("q", location) }.body()
            )
        } catch (e: Exception) {
            NetworkResult.Failure(e.message ?: "Network error")
        }
}

// commonMain factory; each platform provides its engine.
fun weatherHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) { json() }
}
```

`androidMain` supplies the OkHttp engine, `iosMain` the Darwin engine. Add a `commonTest` using Ktor's `MockEngine` that passes on both targets (the success-parse and the error-mapping paths). If you reach for a JVM-only API by mistake, the iOS compile fails — that's the discipline.

## Milestone 3 — The `expect`/`actual` seam (≈ 0.5 h)

Add one genuine platform seam and exercise it. A `User-Agent` header is a clean example:

```kotlin
// commonMain
expect fun platformName(): String
// androidMain — actual fun platformName() = "Android ${Build.VERSION.SDK_INT}"
// iosMain    — actual fun platformName() = UIDevice.currentDevice.systemName()
```

Use `platformName()` in the client config (a header), so the seam is load-bearing, not decorative. This proves you understand `expect`/`actual` is for *real* platform differences used by the shared code.

## Milestone 4 — The Android consumer (≈ 2 h)

The Android app depends on `:shared-core` and consumes the repository through a `ViewModel`, mapping the shared model to a phone UI state at the boundary:

```kotlin
class ForecastViewModel(private val repository: WeatherRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<ForecastUiState>(ForecastUiState.Loading)
    val uiState: StateFlow<ForecastUiState> = _uiState.asStateFlow()

    fun load(location: String) = viewModelScope.launch {
        _uiState.value = when (val result = repository.forecast(location)) {
            is NetworkResult.Success -> ForecastUiState.Content(result.data.toPhoneUi())  // map at boundary
            is NetworkResult.Failure -> ForecastUiState.Error(result.message)
        }
    }
}
```

Wire the repository via Hilt (provide `KtorWeatherRepository(weatherHttpClient(OkHttp.create()))`), render a Compose screen showing the forecast. The Android UI maps the *full* `WeatherForecast` to a rich phone state — all the detail.

## Milestone 5 — The iOS stub + the Wear screen (≈ 2.5 h)

Two surfaces from the one core:

**iOS stub.** You don't write SwiftUI, but prove the core is portable: run `./gradlew :shared-core:compileKotlinIosSimulatorArm64` and confirm it's green. If it fails, you have a JVM-only dependency in `commonMain` — find and fix it (the compile error names it). The green iOS compile is the milestone's deliverable, not a running iOS app. (Stretch, macOS only: run the iOS unit test or stand up a minimal SwiftUI view consuming the framework.)

**Wear screen.** The Wear app depends on the *same* `:shared-core`. A Wear `ViewModel` consumes the *same* `WeatherRepository`, mapping the forecast to a *glance-length* Wear state (location + a few hours, not the full detail). The screen uses Wear components:

```kotlin
@Composable
fun ForecastWearScreen(state: WearForecastUiState) {
    Scaffold(timeText = { TimeText() }) {
        when (state) {
            WearForecastUiState.Loading -> /* centered CircularProgressIndicator */
            is WearForecastUiState.Content -> {
                val listState = rememberScalingLazyListState()
                ScalingLazyColumn(state = listState) {
                    item { Text(state.location) }
                    items(state.hours, key = { it.hour }) { h ->
                        Chip(onClick = {}, label = { Text(h.label) }, secondaryLabel = { Text(h.temp) })
                    }
                }
            }
            is WearForecastUiState.Error -> /* centered Text(message) */
        }
    }
}
```

The Wear UI maps the *same* `WeatherForecast` to a *smaller* state than the phone — the glance-length subset. Same core, two surfaces, native UI on each.

## Milestone 6 — Document the architecture (≈ 1 h)

Write the module `README.md`:

- A diagram (Mermaid or ASCII) of `:shared-core` ← `:androidApp`, `:wear`, and (stubbed) iOS.
- A table of what's in `commonMain` vs. each platform set, and *why* (the share/don't-share decisions).
- The green `compileKotlinIosSimulatorArm64` output, proving portability.
- One sentence on the boundary discipline: the shared core knows nothing about the UI; each surface maps the shared model to its own state (the phone keeps all the detail, the watch keeps the glance-length essentials).

---

## Acceptance criteria

- [ ] `:shared-core` has `commonMain`/`androidMain`/`iosMain`/`commonTest`, with the multiplatform + serialization plugins; the domain model, `NetworkResult`, `WeatherRepository`, and `KtorWeatherRepository` are in `commonMain`, using only multiplatform libraries.
- [ ] `compileKotlinIosSimulatorArm64` succeeds (output captured in the README) — the iOS target compiles. No `java.*`/Retrofit/`java.time` in `commonMain`.
- [ ] A `commonTest` (Ktor `MockEngine` + `runTest`) passes, covering the success-parse and error-mapping paths.
- [ ] At least one `expect`/`actual` seam, exercised by the shared code.
- [ ] The Android app consumes `:shared-core` through a `ViewModel` that maps the shared model to a phone UI state at the boundary; the screen renders the forecast.
- [ ] The Wear app consumes the *same* `:shared-core`, with a `ForecastWearScreen` (Wear `Scaffold` + `TimeText` + `ScalingLazyColumn` + `Chip`) showing a glance-length subset; runs on a round Wear emulator.
- [ ] The README documents what's shared vs. platform, the green iOS compile, and the boundary discipline.
- [ ] Build with **0 warnings, 0 errors** (Android + iOS compile + Wear).

## Stretch goals

- **A `commonTest` that runs on iOS too.** On macOS, run `iosSimulatorArm64Test` and confirm the shared test passes on iOS as well as the JVM — the strongest portability proof.
- **A real SwiftUI stub.** On macOS, stand up a minimal SwiftUI view that imports the shared framework and calls `repository.forecast(...)` — proving the core is consumable from Swift, not just compilable.
- **A Wear tile (preview of Week 20).** Sketch a tile showing the current forecast (you'll build it properly next week). Even a static `ProtoLayout` tile reinforces that the tile is a separate, glanceable surface.
- **Shared `commonTest` for a business rule.** Add `shouldShowRainAlert(forecast)` to `commonMain` and a `commonTest` proving it — a pure shared business rule, tested once, running on both platforms.

## What this milestone earns you

You can now build a portable Kotlin Multiplatform core — a typed domain model, a Ktor repository, serialization, an `expect`/`actual` seam — that *provably* compiles for two platforms, consumed by an Android app and a Wear screen through one shared interface. That is the literal "skill earned" line for the week: KMP module setup, picking the right code to share, and Compose for Wear OS basics. It's also the architectural spine of two capstone deliverables — the `:shared-core` and the `:wear` module — so building it now on a weather domain is direct rehearsal for the Field-Force Companion. Week 20 goes deep on the form factors: adaptive layouts for foldables, full tile and complication authoring for Wear, building the full Wear companion on this exact shared core. You finish this week with a core that travels and a foot on the wrist; Week 20 builds the rest of the multi-form-factor body around it.
