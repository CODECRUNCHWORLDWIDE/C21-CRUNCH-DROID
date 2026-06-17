# Challenge 1 — A shared core that compiles for two platforms (prove it)

**Time.** 60–120 minutes.
**Deliverable.** A `:shared-core` KMP module committed to your Week 19 repo, with the green output of `compileKotlinIosSimulatorArm64` *and* the Android compile, a `commonTest` that passes on both targets, and a short `SHARED.md` documenting what's in `commonMain` vs. platform source sets and *why*.

## The premise

The whole bet of KMP is that you share the business layer *and it actually runs on every platform*. The failure mode — the one this challenge inoculates you against — is a `commonMain` that secretly depends on a JVM-only API, compiles fine for Android, and collapses the day someone tries the iOS build. The skill is not "set up a KMP module"; it's **keep the common code honestly portable and prove it with the compiler.** A green iOS compile is the proof; everything else is aspiration.

You'll build a `:shared-core` for a weather domain — a typed model, a Ktor repository, an `expect`/`actual` seam — and prove it travels.

## What to build

### Step 1 — The KMP module skeleton

Create `:shared-core` with the multiplatform plugin, an `androidTarget()`, and an iOS target:

```kotlin
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}

kotlin {
    androidTarget()
    iosSimulatorArm64()       // (and iosArm64()/iosX64() for full coverage)

    sourceSets {
        commonMain.dependencies {
            implementation("io.ktor:ktor-client-core:3.0.1")
            implementation("io.ktor:ktor-client-content-negotiation:3.0.1")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
        }
        androidMain.dependencies { implementation("io.ktor:ktor-client-okhttp:3.0.1") }
        iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.0.1") }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            implementation("io.ktor:ktor-client-mock:3.0.1")     // a fake HTTP engine for tests
        }
    }
}
```

### Step 2 — The shared domain and repository (`commonMain`)

```kotlin
@Serializable
data class WeatherForecast(
    val location: String,
    val temperatureCelsius: Double,
    val condition: Condition
)
@Serializable enum class Condition { CLEAR, CLOUDY, RAIN, SNOW }

sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class Failure(val message: String) : NetworkResult<Nothing>
}

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
```

### Step 3 — An `expect`/`actual` seam

Add at least one genuine platform seam (a request id or the platform name):

```kotlin
// commonMain
expect fun platformName(): String
// androidMain
actual fun platformName(): String = "Android ${android.os.Build.VERSION.SDK_INT}"
// iosMain
actual fun platformName(): String = platform.UIKit.UIDevice.currentDevice.systemName()
```

Use it somewhere in the shared code (e.g. a `User-Agent`-style header), so the seam is exercised, not decorative.

### Step 4 — A `commonTest` that runs on both platforms

Test the repository against Ktor's `MockEngine` (a fake HTTP engine that's multiplatform), so the test runs on the JVM *and* iOS:

```kotlin
class WeatherRepositoryTest {
    @Test
    fun `parses a successful forecast`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"location":"Lisbon","temperatureCelsius":22.0,"condition":"CLEAR"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) { install(ContentNegotiation) { json() } }
        val repo = KtorWeatherRepository(client)

        val result = repo.forecast("Lisbon")
        assertTrue(result is NetworkResult.Success)
        assertEquals("Lisbon", (result as NetworkResult.Success).data.location)
        assertEquals(Condition.CLEAR, result.data.condition)
    }

    @Test
    fun `maps a server error to Failure`() = runTest {
        val mockEngine = MockEngine { respond("", HttpStatusCode.InternalServerError) }
        val client = HttpClient(mockEngine) { install(ContentNegotiation) { json() } }
        val result = KtorWeatherRepository(client).forecast("Lisbon")
        assertTrue(result is NetworkResult.Failure)
    }
}
```

### Step 5 — Prove portability

Run all three and capture the output for `SHARED.md`:

```bash
./gradlew :shared-core:compileKotlinIosSimulatorArm64   # the iOS COMPILE — the key proof
./gradlew :shared-core:compileDebugKotlinAndroid        # the Android compile
./gradlew :shared-core:iosSimulatorArm64Test            # commonTest on iOS (needs macOS) ...
./gradlew :shared-core:testDebugUnitTest                # ... and on the JVM
```

(On non-macOS, the iOS *compile* is your proof; the iOS *test run* needs macOS. Document which you ran.)

## Acceptance criteria

- [ ] `:shared-core` has `commonMain`, `androidMain`, `iosMain`, and `commonTest` source sets, with the multiplatform + serialization plugins.
- [ ] The domain model, `NetworkResult`, the `WeatherRepository` interface, and the `KtorWeatherRepository` live in `commonMain` and use only multiplatform libraries.
- [ ] At least one genuine `expect`/`actual` seam, exercised by the shared code (not decorative).
- [ ] `compileKotlinIosSimulatorArm64` succeeds — the iOS target compiles. (Capture the green output.)
- [ ] `commonTest` (Ktor `MockEngine` + `runTest`) passes on the JVM (and on iOS if you're on macOS), covering both the success and the error path.
- [ ] `SHARED.md` lists what's in `commonMain` vs. platform sets and *why*, and notes the green iOS compile.
- [ ] No JVM-only API (`java.*`, Retrofit, `java.time`) anywhere in `commonMain`. Build with **0 warnings**.

## What "great" looks like

A weak submission says "I made a KMP module." A great submission says:

> `:shared-core` puts `WeatherForecast`, `Condition`, `NetworkResult`, the `WeatherRepository` interface, and the `KtorWeatherRepository` in `commonMain` — all multiplatform (Ktor, kotlinx-serialization, kotlinx-coroutines, kotlinx-datetime), no `java.*`. The one platform seam is `platformName()`, declared `expect` in `commonMain` and implemented with `Build.VERSION.SDK_INT` on Android and `UIDevice.systemName()` on iOS; it feeds a `User-Agent` header, so the seam is exercised. `compileKotlinIosSimulatorArm64` succeeds (output attached), proving the common code is genuinely portable — when I briefly tried `java.util.UUID` for a request id, that compile failed, which is exactly the discipline working, so I moved it behind `expect`/`actual`. The `commonTest` drives the repository against Ktor's `MockEngine` and passes on both the JVM and (on my Mac) the iOS simulator, covering the 200-parse and 500-error paths. The Android app consumes the same `WeatherRepository` interface through a Hilt-provided `KtorWeatherRepository`.

Portable, proven by the iOS compile, the seam exercised, the test running on both targets — and honest about the moment the compiler caught a JVM-only slip. That's the senior KMP answer.

## Where this reappears

This `:shared-core` is, almost verbatim, a **capstone deliverable**: "`:shared-core` (KMP) — typed domain model, Ktor-based API surface, kotlinx-serialization wire format, kotlinx-coroutines flows, kotlinx-datetime" (Week 23). Building it now on a weather domain is rehearsal for the Field-Force Companion's shared core. The mini-project this week consumes this exact module from an Android app and a Wear screen; Week 20 builds the full Wear companion on it. And the "prove portability with the compiler" discipline is the KMP analogue of every "prove it, don't assert it" lesson the course has taught — Week 17's determinism, Week 18's distributions, now Week 19's green iOS build.
