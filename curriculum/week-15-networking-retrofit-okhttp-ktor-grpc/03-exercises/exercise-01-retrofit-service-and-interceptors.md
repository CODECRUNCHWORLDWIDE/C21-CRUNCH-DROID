# Exercise 1 — A Retrofit service with interceptors

**Goal.** Stand up the smallest possible real networking layer: a typed Retrofit service over a shared OkHttp client with kotlinx-serialization, a logging interceptor (with the auth header redacted), and an auth interceptor that adds a bearer token. Then fetch real data from a test API and parse it into a data class. This is the foundation of the week — if you can do this, you can talk to any REST backend; everything else is hardening.

**Estimated time.** 40 minutes.

**Prerequisites.** Android Studio (2025.1+), an emulator with network access, a project with the Retrofit, OkHttp, and kotlinx-serialization dependencies and the serialization plugin applied. The `INTERNET` permission in the manifest.

---

## Step 1 — Add the dependencies and the serialization plugin

In your module's `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.serialization)   // the kotlinx-serialization compiler plugin
}

dependencies {
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
}
```

And add `<uses-permission android:name="android.permission.INTERNET" />` to the manifest. Sync.

## Step 2 — Define the `@Serializable` DTO

We'll hit `httpbin.org`, which echoes back what you send. Model the slice you care about:

```kotlin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EchoResponse(
    val url: String,
    @SerialName("headers") val headers: Map<String, String>,
    @SerialName("args") val args: Map<String, String> = emptyMap()
)
```

Two things to notice: it's a `data class` annotated `@Serializable` (kotlinx-serialization generates the parser at compile time, no reflection), and `args` has a default so a missing field doesn't crash the parse.

## Step 3 — Define the typed Retrofit service

```kotlin
import retrofit2.http.GET
import retrofit2.http.Query

interface HttpBinApi {
    @GET("get")
    suspend fun echo(@Query("city") city: String): EchoResponse
}
```

Each method is an endpoint. `@GET("get")` is the path appended to the base URL; `@Query` adds a query parameter; `suspend` runs it off the main thread and resumes with the parsed result.

## Step 4 — Build the shared OkHttp client with interceptors

```kotlin
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

class AuthInterceptor(private val token: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer ${token()}")
            .build()
        return chain.proceed(request)
    }
}

fun buildClient(): OkHttpClient {
    val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY   // debug only; NONE/BASIC in release
        redactHeader("Authorization")               // NEVER log the token
    }
    return OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor { "demo-token-123" })
        .addInterceptor(logging)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)          // hard cap so a call can't hang forever
        .build()
}
```

The order matters: the auth interceptor runs first (adds the header), then logging logs the request *with* the header present but redacted. Reverse them and logging wouldn't see the header at all.

## Step 5 — Build Retrofit and the API

```kotlin
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

fun buildApi(): HttpBinApi {
    val json = Json { ignoreUnknownKeys = true }   // a new server field won't crash the parse
    return Retrofit.Builder()
        .baseUrl("https://httpbin.org/")
        .client(buildClient())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(HttpBinApi::class.java)
}
```

In a real app this all lives in a Hilt `@Provides` (the `:core-network` module from Week 13), with the client a `@Singleton`. For this exercise a factory function is fine — but know that `buildClient()` must be called *once* and shared, not per request.

## Step 6 — Call it and see the data

```kotlin
@Composable
fun EchoScreen() {
    var result by remember { mutableStateOf("Loading…") }
    LaunchedEffect(Unit) {
        result = try {
            val response = buildApi().echo(city = "lisbon")   // suspend; off the main thread
            "URL: ${response.url}\nargs: ${response.args}"
        } catch (e: Exception) {
            "Failed: ${e.message}"                            // exercise 2 replaces this with NetworkResult
        }
    }
    Text(result)
}
```

Run it. You should see the echoed URL and `args: {city=lisbon}`, proving the request went out, the query param was added, and the JSON parsed into your DTO. Open logcat: the `HttpLoggingInterceptor` printed the full request and response, with the `Authorization` header shown as `██` (redacted).

## Step 7 — Confirm the interceptors fired

In logcat (filter on `okhttp`), confirm:

- The request line `GET https://httpbin.org/get?city=lisbon`.
- The `Authorization` header present but **redacted** (you set `redactHeader`).
- The response body echoing your request.

If you don't see the `Authorization` header at all, the auth interceptor isn't installed or runs after logging. If you see the *actual* token, you forgot `redactHeader` — a security bug.

---

## Acceptance criteria

- [ ] A `@Serializable` DTO with at least one defaulted field, parsed by kotlinx-serialization.
- [ ] A typed `@GET` Retrofit service with a `suspend` method and a `@Query` param.
- [ ] A single shared `OkHttpClient` with an auth interceptor, a logging interceptor (`redactHeader("Authorization")`), and connect/read/call timeouts.
- [ ] `ignoreUnknownKeys = true` on the `Json`.
- [ ] Build with **0 warnings, 0 errors**; the app fetches and displays the parsed echo.
- [ ] In logcat, the request fired, the `Authorization` header is present but **redacted**, and the body parsed.

## What you just proved

You proved the layered stack from lecture 1 works: **Retrofit** turned your interface into an HTTP call, **kotlinx-serialization** parsed the response into a typed object, and **OkHttp**'s interceptor chain added auth and logged the exchange (without leaking the token). You also set the timeouts that stop a call hanging forever. Exercise 2 replaces the bare `try`/`catch` with a `NetworkResult` so every failure is a typed case the UI must handle.

---

## Hints (read only if stuck > 10 min)

- **`NetworkOnMainThreadException`.** You called the API outside a coroutine. The `suspend` method must run in a coroutine scope — `LaunchedEffect` provides one; in a ViewModel use `viewModelScope.launch`.
- **`SerializationException: Field 'x' is required`.** A field your DTO declares non-null is missing from the response. Make optional fields nullable with a default, and keep `ignoreUnknownKeys = true` for *extra* fields.
- **The auth header doesn't appear in logs.** The auth interceptor is added after the logging interceptor, or not added at all. Add auth *before* logging so logging sees the modified request.
- **The token appears in plaintext in logs.** You forgot `redactHeader("Authorization")`. Add it — a leaked token in logs is a real security incident.
- **`Unable to resolve host`.** The emulator has no network, or you forgot the `INTERNET` permission. Check connectivity and the manifest.
