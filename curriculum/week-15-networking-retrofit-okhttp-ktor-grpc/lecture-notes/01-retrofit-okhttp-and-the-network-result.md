# Lecture 1 — Retrofit over OkHttp, and modelling every failure as a type

> "The happy path is a tutorial. The error paths are the job. Networking code that doesn't model failure isn't networking code — it's a demo waiting to crash on a train."

This is the lecture that decides whether your networking layer survives contact with a real network. The framing for the whole week is two sentences. First: **Retrofit is a typed front end over OkHttp** — Retrofit turns an interface into HTTP calls, OkHttp is the engine that actually makes them, and the production concerns (logging, auth, caching, pinning, timeouts) all live in OkHttp. Second: **every network call will fail, so the failure must be a value the type system forces you to handle**, not an exception you forgot to catch. Hold both, and the week's surprises — why a retry made things worse, why a 401 loops, why the UI hung, why parsing crashed — have a clear home. Lose them, and you ship a beautiful happy path that falls over the first time someone opens the app in a tunnel.

We build the model bottom-up: the engine (OkHttp and its interceptor chain), then the typed front end (Retrofit + kotlinx-serialization), then the failure model (`NetworkResult` + `safeApiCall`), then resilience (retry, backoff, timeout). By the end you should be able to draw the request's path from a Kotlin function call down to the socket and back, and point to where each concern lives.

---

## 1. The stack, drawn once

Here is the full stack under a Retrofit call, top to bottom:

```text
┌─────────────────────────────────────────────────────────────┐
│  Your code                                                   │
│    val result: NetworkResult<Forecast> = repo.forecast(city) │
├─────────────────────────────────────────────────────────────┤
│  safeApiCall + NetworkResult (your failure model)            │
│    maps HttpException / IOException / SerializationException  │
│    -> Success / HttpError / NetworkError / SerializationError │
├─────────────────────────────────────────────────────────────┤
│  Retrofit (typed front end)                                  │
│    @GET("forecast") suspend fun forecast(...): Forecast      │
│    kotlinx-serialization converter (JSON <-> data class)     │
├─────────────────────────────────────────────────────────────┤
│  OkHttp (the engine — where production lives)                │
│    interceptors: logging, auth, cache                        │
│    CertificatePinner, timeouts, connection pool, Cache       │
├─────────────────────────────────────────────────────────────┤
│  TCP/TLS sockets                                             │
│    the actual bytes on the wire                              │
└─────────────────────────────────────────────────────────────┘
```

You call a `suspend` function; Retrofit builds the HTTP request from the annotations; OkHttp runs it through the interceptor chain, over a pooled, TLS-pinned connection, with timeouts; the response comes back, the converter parses the JSON into your data class, and `safeApiCall` turns the whole thing — success or any flavour of failure — into a `NetworkResult` value. Every layer has one job. When something goes wrong, you ask which layer owns it.

---

## 2. OkHttp — the engine and the interceptor chain

OkHttp is the HTTP client doing the real work. The single most important concept is the **interceptor chain**: a stack of interceptors each request passes through on the way out and each response passes through on the way back. This is where you add cross-cutting behaviour — logging, auth headers, caching — without touching any individual call.

```kotlin
val logging = HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
    redactHeader("Authorization")   // never log the token, even in debug
}

class AuthInterceptor(private val tokenProvider: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer ${tokenProvider.current()}")
            .build()
        return chain.proceed(request)
    }
}

val client = OkHttpClient.Builder()
    .addInterceptor(AuthInterceptor(tokenProvider))   // application interceptor
    .addInterceptor(logging)                          // application interceptor (last = closest to your code)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)                // hard cap on the whole call
    .cache(Cache(File(context.cacheDir, "http"), maxSize = 10L * 1024 * 1024))
    .build()
```

Things to internalise:

- **Application vs. network interceptors.** `addInterceptor` adds an *application* interceptor — it runs once per call, sees the request your code made, and (importantly) does *not* run for a cache hit served without hitting the network. `addNetworkInterceptor` runs once per *network* request, sees redirects and retries, and always touches the wire. Auth and logging are usually application interceptors; cache-header rewriting is a network interceptor. Getting this wrong is why "my logging interceptor didn't fire" (a cache hit) or "my auth header was added twice" (on a redirect) happens.
- **The `OkHttpClient` is expensive and must be shared.** It owns a connection pool and dispatcher thread pools. Creating one per request defeats connection reuse and leaks threads. It is the canonical `@Singleton` in your Hilt graph (Week 13). Build it once, share it everywhere; use `client.newBuilder()` to derive a variant (e.g. the auth client from the public client) so they share the pool.
- **Timeouts are not optional.** `connectTimeout`, `readTimeout`, and a `callTimeout` (the hard cap on the entire call including retries and redirects) are what stop a call hanging forever on a dead network. A network call with no timeout is a UI hang waiting to happen.
- **The `Authenticator` for token refresh.** An `Interceptor` adds a token; an `Authenticator` reacts to a `401` by refreshing the token and retrying *once*. The distinction matters: refresh logic belongs in the `Authenticator` (which OkHttp calls on 401 with retry guarding), not in an interceptor that would loop.

---

## 3. Retrofit + kotlinx-serialization — the typed front end

Retrofit turns an interface into HTTP. Each method is an endpoint; the annotations describe the request; the converter parses the response into a typed object.

```kotlin
@Serializable
data class ForecastDto(
    val city: String,
    @SerialName("temp_c") val temperatureC: Double,
    val condition: String
)

interface WeatherApi {
    @GET("forecast")
    suspend fun forecast(
        @Query("city") city: String,
        @Query("days") days: Int = 1
    ): ForecastDto

    @POST("feedback")
    suspend fun submit(@Body feedback: FeedbackDto): Response<Unit>
}

val retrofit = Retrofit.Builder()
    .baseUrl("https://api.weather.example/")
    .client(okHttpClient)                                   // the shared @Singleton engine
    .addConverterFactory(
        Json { ignoreUnknownKeys = true }
            .asConverterFactory("application/json".toMediaType())
    )
    .build()

val api: WeatherApi = retrofit.create(WeatherApi::class.java)
```

The decisions to internalise:

- **kotlinx-serialization, not Gson/Moshi.** kotlinx-serialization is compile-time (it generates the serializer from `@Serializable`, no reflection), Kotlin-native (it understands `data class`, nullability, defaults, sealed classes), and multiplatform (the same DTOs work in the Ktor/KMP path). In 2026 it is the default. `ignoreUnknownKeys = true` is the one setting you almost always want — a server adding a field shouldn't crash your parse.
- **DTOs are not domain models.** `ForecastDto` mirrors the wire format (`temp_c`, server field names). Your *domain* `Forecast` is what the rest of the app uses. Map DTO → domain in the repository. This keeps the wire format's quirks out of your UI and lets the API change without rippling through the app. `@SerialName` bridges a wire name to a Kotlin name.
- **`suspend` functions, off the main thread.** A `suspend` Retrofit method runs the call on OkHttp's dispatcher and resumes you with the result — no manual threading, no callback. You call it from a coroutine on `Dispatchers.IO` (injected, Week 13). Retrofit also supports returning `Response<T>` (to inspect status/headers) or a `Flow` (for adapters); the `suspend fun ...: T` form is the common one.

---

## 4. `NetworkResult` — modelling every failure as a type

Here is the crux of the week. A Retrofit `suspend fun forecast(...): ForecastDto` can fail in *three distinct ways*, and if you don't model them, they leak out as exceptions that crash or hangs that never return:

1. **An HTTP error** — the server responded, but with `404`, `500`, `429`. Retrofit throws `HttpException`.
2. **A network error** — no response at all: timeout, no connectivity, connection reset. Throws `IOException`.
3. **A serialization error** — a response arrived but didn't match your DTO. Throws `SerializationException`.

Model all three (plus success) as a sealed type, so the compiler forces every caller to handle them:

```kotlin
sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class HttpError(val code: Int, val body: String?) : NetworkResult<Nothing>
    data class NetworkError(val cause: IOException) : NetworkResult<Nothing>
    data class SerializationError(val cause: Throwable) : NetworkResult<Nothing>
}

/** Wrap any Retrofit suspend call; map each failure flavour to a typed case. */
suspend fun <T> safeApiCall(block: suspend () -> T): NetworkResult<T> =
    try {
        NetworkResult.Success(block())
    } catch (e: HttpException) {
        NetworkResult.HttpError(e.code(), e.response()?.errorBody()?.string())
    } catch (e: SerializationException) {
        NetworkResult.SerializationError(e)
    } catch (e: IOException) {
        NetworkResult.NetworkError(e)            // timeout, no connectivity, reset
    }
```

Then the repository returns the result, and the UI must handle every case — the compiler enforces it via the exhaustive `when`:

```kotlin
suspend fun forecast(city: String): NetworkResult<Forecast> =
    safeApiCall { api.forecast(city).toDomain() }   // DTO -> domain inside the wrapper

// In the ViewModel, mapping to UiState the screen renders:
val uiState = when (val result = repo.forecast(city)) {
    is NetworkResult.Success -> UiState.Ready(result.data)
    is NetworkResult.HttpError -> UiState.Error("Server error ${result.code}")
    is NetworkResult.NetworkError -> UiState.Error("No connection — check your network")
    is NetworkResult.SerializationError -> UiState.Error("Unexpected response")
}
```

This is the Week 2 algebraic-modeling skill applied to networking. The failure modes are now *cases in a type*, not exceptions you might forget. The exhaustive `when` means adding a new failure case is a compile error everywhere it isn't handled — exactly the safety property you want around something as failure-prone as the network. **A `NetworkResult` is the difference between an app that degrades gracefully and one that crashes in a tunnel.**

One important ordering detail in `safeApiCall`: catch `HttpException` and `SerializationException` *before* `IOException`, because some of those can subtype or wrap an `IOException` and you want the more specific case. Order your catches from most specific to most general.

### Why not just return `Result<T>` or throw?

Two tempting alternatives, and why the sealed type beats them. Kotlin's stdlib `Result<T>` is a `Success`/`Failure(Throwable)` pair — but its failure side is an *untyped* `Throwable`, so the caller is right back to `when (val e = result.exceptionOrNull()) { is HttpException -> ...; is IOException -> ... }`, doing the type-discrimination by hand with no exhaustiveness guarantee. A custom sealed `NetworkResult` makes the *meaningful* failure categories — HTTP vs. network vs. parse — first-class, named, and exhaustively checked. And just *throwing* pushes the failure handling to some `try`/`catch` far from the call, where it's easy to catch too broadly (`catch (e: Exception)` swallows everything including bugs) or forget entirely (an uncaught `IOException` on a coroutine crashes the app). The sealed result moves the handling *into the type signature*: a function that returns `NetworkResult<Forecast>` advertises, in its signature, that it can fail and how — and the compiler makes the caller deal with it. That visibility is the whole point.

### Mapping `NetworkResult` to `UiState`

The repository returns `NetworkResult`; the ViewModel maps it to a `UiState` the Compose layer renders. Keeping these two types distinct matters: `NetworkResult` is about *the network outcome*, `UiState` is about *what the screen shows* — and they are not one-to-one. A `NetworkError` when you have cached data is not an error screen; it's a quiet "showing last update" banner over the cached content. A `Success` with an empty list is an *empty state*, not a *ready* state. The ViewModel is where that translation lives:

```kotlin
sealed interface UiState {
    data object Loading : UiState
    data class Ready(val forecast: Forecast) : UiState
    data class Offline(val cached: Forecast) : UiState   // network failed, but we have cache
    data class Error(val message: String) : UiState
}
```

The point: don't leak `NetworkResult` into the UI layer raw. Translate it into screen states the designer reasoned about, where "no connection but we have yesterday's forecast" is a deliberate, friendly state — not a red error the user can do nothing about.

---

## 5. Retry, backoff, and timeout — resilience without self-harm

A transient failure (a 503, a momentary timeout) often succeeds on retry. But a *naive* retry is worse than no retry: a tight `while (failed) retry()` loop hammers a struggling server, turning a blip into an outage, and DDoSes your own backend. Three rules make retry safe.

### Rule 1 — only retry what's retryable

A `500`/`503`/timeout is transient; retrying may help. A `400`/`401`/`404` is a *client* error — the request is wrong, and retrying it unchanged will fail identically forever. Retrying a 4xx is pure waste (and a 401 retry-loop without refresh is a self-inflicted lockout).

```kotlin
fun NetworkResult<*>.isRetryable(): Boolean = when (this) {
    is NetworkResult.NetworkError -> true                       // timeout/reset: maybe transient
    is NetworkResult.HttpError -> code == 429 || code in 500..599 // throttle or server error
    else -> false                                                // 4xx, success, parse error: no
}
```

### Rule 2 — exponential backoff with jitter

Wait longer between each attempt (exponential), and add randomness (jitter) so a thousand clients that all failed at once don't all retry at the same instant (a "thundering herd" that re-creates the overload):

```kotlin
suspend fun <T> retrying(
    maxAttempts: Int = 4,
    baseDelayMs: Long = 500,
    block: suspend () -> NetworkResult<T>
): NetworkResult<T> {
    var attempt = 0
    while (true) {
        val result = block()
        attempt++
        if (!result.isRetryable() || attempt >= maxAttempts) return result
        // Exponential: 500, 1000, 2000... plus jitter up to 50% so clients desync.
        val backoff = baseDelayMs * (1L shl (attempt - 1))
        val jitter = Random.nextLong(backoff / 2)
        delay(backoff + jitter)
    }
}
```

### Rule 3 — bound it, and let it fail

`maxAttempts` is not optional. An unbounded retry hangs the operation forever and is the footgun that DDoSes your backend. After the bound, **return the failure** — a handled `NetworkError`/`HttpError` the UI shows — rather than retrying into eternity. Combined with the `callTimeout` on OkHttp (which caps the *whole* call), you have two independent ceilings: per-call timeout and total retry bound.

`withTimeout(...)` from coroutines is the third tool, for wrapping an operation that has no natural timeout. The discipline: **fail fast, retry smart, bound everything.** A network operation that can hang forever or retry forever is a bug, no matter how rare the trigger.

---

## 6. What the typed client still hides — the leaks to know about

Retrofit over OkHttp is a good abstraction, which means it leaks in predictable places:

1. **The actual HTTP.** You call a Kotlin function; you don't see the headers, the status, the cache decision. The fix is the logging interceptor (`BODY` in debug) and a proxy (Charles/mitmproxy) when you need the raw bytes. When a call behaves oddly, *look at the wire*.
2. **Caching is HTTP-header-driven.** OkHttp's response `Cache` honours `Cache-Control`/`ETag` from the *server*. If the server doesn't send cache headers, the cache does nothing — and you may need to rewrite headers with a network interceptor to cache anyway. The cache isn't magic; it obeys HTTP.
3. **Response bodies must be closed.** A `Response` body is a stream; not closing it leaks a connection. Retrofit closes it for you on the `suspend fun ...: T` path, but if you take a raw `ResponseBody`, `response.use { }` it.
4. **Serialization is strict by default.** Without `ignoreUnknownKeys`, an extra server field crashes the parse. With it, a *missing* required field still crashes — model optional fields as nullable with defaults.
5. **The connection pool and DNS are shared state.** A leaked per-request `OkHttpClient` means no pooling and a thread leak. Share the singleton.

None of these are reasons to avoid Retrofit. They are the puddles you learn to recognise when the abstraction leaks.

---

## 7. The decision so far

For an **Android-only REST/JSON** app, the stack is settled: **Retrofit + OkHttp + kotlinx-serialization**, with the failure modeled as a `NetworkResult` and resilience from bounded backoff. It is the default, it is what Now-In-Android uses, and it is what you reach for unless you have a specific reason not to. The two reasons you might reach past it — *multiplatform* (you want the client in `commonMain` for iOS too) and *binary contracts/streaming* (gRPC) — are lecture 2's subject. But the *shape* you learned here carries: a typed client, safe parsing, a sealed result, bounded retry. Ktor and gRPC change the engine, not the discipline.

---

## 8. Recap — the failure-first habit

You will write networking all week. The discipline that turns you from someone who writes happy paths into someone who ships production networking is the reflex to ask, on every call, "how does this fail, and where does the failure go?"

- The server is down → `HttpError(503)`, retried with backoff, then surfaced. Not a crash.
- The network is gone → `NetworkError`, failed fast by the timeout. Not a hang.
- The body is malformed → `SerializationError`. Not a crash.
- The request is wrong (400) → `HttpError(400)`, *not* retried. Not a loop.
- The token expired (401) → the `Authenticator` refreshes once. Not an infinite retry.

Retrofit gave you a typed client and OkHttp gave you a production engine. Neither gave you a failure model — that's the `NetworkResult` you write, and it is the difference between a demo and an app. In lecture 2 we add the two production hardening topics (certificate pinning and the rotation trap), the two alternative stacks (Ktor for KMP, gRPC for binary contracts), the decision table for choosing among the three, and the footguns measured. Bring this stack diagram; we are about to harden every layer of it.

---

## 9. Appendix — the interceptor chain, walked once in detail

Because the interceptor chain is the single most misunderstood piece of OkHttp, it is worth walking the full path of one request through it, slowly. Picture three application interceptors installed in this order: auth, then logging, then a retry-policy interceptor. When your code makes a call, the request travels *down* the chain — auth first, then logging, then retry — and then out to the network; the response travels *back up* — retry first, then logging, then auth — before reaching your code. Each interceptor is wrapped around the ones below it, like nested function calls. The `chain.proceed(request)` call inside an interceptor is the moment it hands control to the *next* interceptor down; everything before `proceed` runs on the way out, everything after `proceed` runs on the way back.

This ordering has concrete consequences you will hit. Because the auth interceptor is outermost, it adds the `Authorization` header *before* logging runs, so logging sees and can log (redacted) the header — which is what you want. If you reversed them, logging would run first and never see the header. Because the logging interceptor wraps the network call, the time it measures (if you time around `proceed`) includes everything below it, including any retry. And because an *application* interceptor is outside the retry-and-redirect machinery, it runs exactly once per logical call even if OkHttp internally retries the request three times against different IP addresses — whereas a *network* interceptor, installed inside that machinery, runs once per actual network attempt. This is precisely why "log every network attempt including retries" wants a network interceptor, while "add a header once per call" wants an application interceptor. The two interceptor types are not a redundant pair; they are two different positions in the chain with two different visibility scopes, and choosing the wrong one is a real, recurring bug.

There is one more subtlety worth committing to memory: a cached response served entirely from OkHttp's `Cache`, with no network access at all, **skips the network interceptors entirely** — there was no network attempt, so the network-interceptor position never executed. The application interceptors still run (the call was still made). So if your logging is a network interceptor and a request is served from cache, you will see *nothing* in the log, and conclude wrongly that the call didn't happen. It happened; it just never touched the wire. Knowing this saves you an afternoon of confused debugging the first time you add a response cache.

## 10. Appendix — separating DTOs from domain models, and why it pays

It is tempting, especially early, to let your `@Serializable` wire type *be* your domain type — one `Forecast` class that both parses the JSON and flows through the whole app. It works until it doesn't, and the place it stops working is always the same: the server changes. A wire type is owned by the backend's contract; it carries the backend's field names (`temp_c`, `wx_code`), the backend's nullability decisions, the backend's representation of dates (epoch seconds? ISO strings? both, on different endpoints?). A *domain* type is owned by your app; it carries the names and shapes your UI and business logic want. Fusing them means every backend quirk leaks straight into your Compose code, and every backend change ripples through the entire app instead of stopping at one mapping function.

The discipline is to keep two types and a mapping between them: `ForecastDto` (wire-shaped, `@Serializable`, lives in the network layer) and `Forecast` (domain-shaped, plain data class, used everywhere else), with a `ForecastDto.toDomain()` extension in the repository. The mapping is the seam.

When the backend renames `temp_c` to `temperature_celsius`, you change one `@SerialName` and the mapping, and nothing else in the app moves. When the backend adds a field you don't care about, `ignoreUnknownKeys` swallows it and your domain type never grows. When you later add a *second* data source (a cache, a different API, a gRPC backend), they all map into the same domain `Forecast`, and the rest of the app cannot tell which source it came from.

That last property is exactly what makes the offline-first pattern and the multi-engine mini-project possible: the UI consumes domain types, and the question of which engine or cache produced them is an implementation detail hidden behind the mapping. The few extra lines of a `toDomain()` function are among the highest-leverage code in the whole networking layer.

## 11. Appendix — where networking work runs, and why it is never the main thread

A final foundational point, because it underlies every code example in this lecture: **network work runs on a background dispatcher, never the main thread.** Modern Android enforces this with a `NetworkOnMainThreadException` for synchronous calls, but the deeper reason is the user experience. The main thread is the UI thread; it is the thread that draws every frame and handles every touch. A network call can take anywhere from milliseconds to tens of seconds, and any time the main thread is blocked waiting for the network, the UI is frozen — no scrolling, no taps, no animation — and if it freezes for a few seconds, Android shows the "Application Not Responding" dialog and the user force-quits.

A compact reference for the threading rules every network call must obey:

- **Never call the network on the main thread.** Modern Android throws `NetworkOnMainThreadException`; even if it didn't, a blocked UI thread freezes scrolling and taps.
- **Use `suspend` functions**, which suspend the coroutine instead of blocking the thread, and run the I/O on a background dispatcher regardless.
- **Launch from a background dispatcher** — inject `@Dispatcher(IO)` (Week 13) so a test can swap a test dispatcher in.
- **Wrap in `withContext(io)`** when you need to be explicit about where suspend work runs.
- **Never `runBlocking` on the main thread** to "just get the value" — that reintroduces exactly the block you were avoiding.
- **Structured concurrency** (Week 4) ties in-flight requests to a scope, so cancelling the screen cancels the request.

The whole reason Retrofit's `suspend` functions are pleasant is that they solve this for free. A `suspend fun forecast(...)` does not block the thread that calls it; it suspends the coroutine, lets the thread go do other work, and resumes the coroutine when the response arrives — and Retrofit runs the actual I/O on OkHttp's own background dispatcher regardless. So the rule in practice is: call your `suspend` network functions from a coroutine launched on `Dispatchers.IO` (which you inject behind a `@Dispatcher(IO)` qualifier, per Week 13, so a test can swap it for a test dispatcher), wrap them in `withContext(io)` if you need to be explicit, and never, ever use `runBlocking` on the main thread to "just get the value." The combination — a `suspend` API, a background dispatcher, and structured concurrency from Week 4 — is what keeps a network-heavy screen responsive while requests are in flight. When you review networking code, "what thread does this run on?" should have an obvious answer (a background dispatcher) for every single call; if it doesn't, that is the bug.

## 12. Appendix — a worked end-to-end trace

To consolidate everything, here is one request traced from the UI down and back, naming the layer responsible at each step. It is worth reading slowly once; every concept in the lecture appears in it.

1. **The screen** calls `viewModel.refresh()`, which launches a coroutine on `viewModelScope`.
2. **The ViewModel** calls `repository.forecast(city)` — a `suspend` function — inside that coroutine.
3. **The repository** calls `safeApiCall { api.forecast(city).toDomain() }`, wrapping the call in the failure mapper.
4. **Retrofit** builds an HTTP `GET /forecast?city=...` from the interface annotations and hands it to OkHttp.
5. **OkHttp** runs the request through the interceptor chain: the auth interceptor adds the `Authorization` header, the logging interceptor logs it (redacted).
6. **OkHttp** opens (or reuses, from the pool) a TLS connection — verifying the pinned certificate if pinning is on — and sends the bytes, subject to the connect/read/call timeouts.
7. **The server** responds (or times out, or errors).
8. **On the way back up**, the response passes through the interceptors again, OkHttp may serve from cache or apply retries, and Retrofit's kotlinx-serialization converter parses the JSON into `ForecastDto`.
9. **The repository** maps `ForecastDto.toDomain()` and `safeApiCall` wraps the whole thing in `NetworkResult.Success` — or, if any step threw, `HttpError` / `NetworkError` / `SerializationError`.
10. **The ViewModel** maps that `NetworkResult` to a `UiState`, and **the screen** recomposes to render it.

Every failure mode you learned has a home in that trace: a timeout fails at step 6 into a `NetworkError`; a 500 fails at step 7 and surfaces at step 9 as an `HttpError` (after the bounded retry between steps 6–8); a wrong-shaped body fails the parse at step 8 into a `SerializationError`. The point of holding the whole trace in your head is that when something goes wrong, you can locate *which step* owns it — and that is the difference between debugging networking and guessing at it.

The "which layer owns it" reflex, as a quick diagnostic table:

- **No response, the call hung** → step 6, OkHttp/sockets; check timeouts, fail into `NetworkError`.
- **A 4xx/5xx came back** → step 7, the server; map to `HttpError`, retry only if 5xx/429.
- **Parse blew up** → step 8, the converter; check the DTO shape and `ignoreUnknownKeys`, fail into `SerializationError`.
- **The header was missing or doubled** → step 5, the interceptor order.
- **The cache served stale or nothing** → step 6/8, OkHttp's response `Cache` and the server's cache headers.
- **The UI froze** → the call ran on the main thread; move it to `Dispatchers.IO`.
- **The token loops on 401** → the refresh is in an interceptor, not an `Authenticator` with a retry guard.

Run that table against any networking bug and it points you at the responsible layer in seconds, which is the entire payoff of understanding the stack as distinct layers rather than one opaque "the network" box.
