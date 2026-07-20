# Mini-Project — The weather client, implemented twice (Retrofit and Ktor)

This week the app gets a network source. You will build a **weather client implemented twice** — once with Retrofit, once with Ktor Client — both behind the *same* repository interface and both returning the *same* sealed `NetworkResult<T>`. You will wire both through the **Week-13 Hilt graph** (the `:core-network` module you stubbed), cache the result into the **Week-14 Room store**, and surface it as a `UiState` a Compose screen renders. A bonus path implements the same client over gRPC. By the end you can talk to a backend three ways and defend the choice for each context.

This is the *integration* project for Phase 3 — it ties together everything so far. The `NetworkResult` is the Week-2 algebraic-modeling skill; the `suspend`/`Flow` calls are Week 4–5; the Hilt-provided clients are Week 13; the Room cache is Week 14; and the offline-first network→Room→`Flow`→UI wiring is the foundation Week 16 makes fully offline-capable. Building the client *twice* over one interface is the point: you feel that the discipline is constant and only the engine changes — the exact insight that lets the capstone share its data layer with iOS via Ktor.

---

## Where you're starting from

Your Week 13 Hilt graph has a stubbed `:core-network` module; your Week 14 app has a real Room store. You need:

- The Retrofit, OkHttp, kotlinx-serialization, and Ktor dependencies.
- A free weather API (e.g. Open-Meteo, which needs no API key) or a small mock server.
- The Room database from Week 14 to cache into, and the Hilt graph from Week 13 to wire into.

If you don't have clean Week 13/14 checkpoints, build the minimal Hilt graph and Room store first; the networking work is the same either way.

## What you're building toward

By the end you have:

- A `WeatherRepository` interface returning `NetworkResult<Forecast>` (and a cached `Flow<Forecast?>` from Room).
- A **Retrofit** implementation (`RetrofitWeatherSource`) over a shared, Hilt-provided `@Singleton` OkHttpClient.
- A **Ktor** implementation (`KtorWeatherSource`) with the same contract and `NetworkResult`.
- One `@Qualifier`-selected source the repository uses (swap engines by flipping a binding) — proving they're interchangeable.
- A `safeApiCall`/`safeKtorCall` wrapper, bounded exponential-backoff retry, and timeouts on both.
- The forecast **cached into the Room store** as the single source of truth; the UI reads Room via a `Flow`, the network fills it.
- A passing **failure-handling test** with `MockWebServer`/`MockEngine`: a 500, a timeout, and a malformed body each produce the right `NetworkResult`.

---

## Milestone 1 — The domain model and the repository interface (≈ 1 h)

Define the *domain* model (what the app uses) separately from the wire DTOs (what each engine parses):

```kotlin
// Domain model — what the UI and the rest of the app see. Engine-agnostic.
data class Forecast(
    val city: String,
    val temperatureC: Double,
    val condition: String,
    val fetchedAt: Long
)

// The repository: one interface, swappable implementations.
interface WeatherRepository {
    suspend fun refresh(city: String): NetworkResult<Forecast>   // fetch + cache
    fun observe(city: String): Flow<Forecast?>                    // read from Room
}
```

The `NetworkResult<T>` sealed type is the one from lecture 1 / exercise 2 — `Success`, `HttpError`, `NetworkError`, `SerializationError`. Both engines return it; the UI handles every case.

## Milestone 2 — The Retrofit source (≈ 1.5 h)

Build the Retrofit implementation over a Hilt-provided OkHttp client. The DTO mirrors the wire format; the source maps DTO → domain.

```kotlin
@Serializable
data class ForecastDto(
    val city: String,
    @SerialName("temperature") val temperatureC: Double,
    @SerialName("weather") val condition: String
)

interface WeatherApi {
    @GET("forecast")
    suspend fun forecast(@Query("city") city: String): ForecastDto
}

class RetrofitWeatherSource @Inject constructor(
    private val api: WeatherApi,
    @Dispatcher(IO) private val io: CoroutineDispatcher    // injected, swappable in tests
) {
    suspend fun fetch(city: String): NetworkResult<Forecast> =
        withContext(io) {
            retrying(maxAttempts = 4) {
                safeApiCall {
                    api.forecast(city).let {
                        Forecast(it.city, it.temperatureC, it.condition, System.currentTimeMillis())
                    }
                }
            }.first
        }
}
```

Provide the `WeatherApi`, `OkHttpClient` (`@Singleton`), and `Retrofit` in the `:core-network` Hilt module — fill in the stub from Week 13. The client carries the logging interceptor (redacting secrets) and the timeouts from lecture 1.

## Milestone 3 — The Ktor source (≈ 1.5 h)

Build the Ktor implementation with the *same* contract and the *same* `NetworkResult`. Reuse the `ForecastDto` — kotlinx-serialization is shared.

```kotlin
class KtorWeatherSource @Inject constructor(
    private val client: HttpClient,                        // Hilt-provided, OkHttp engine
    @Dispatcher(IO) private val io: CoroutineDispatcher
) {
    suspend fun fetch(city: String): NetworkResult<Forecast> =
        withContext(io) {
            retrying(maxAttempts = 4) {
                safeKtorCall {
                    client.get("forecast") { parameter("city", city) }
                        .body<ForecastDto>()
                        .let { Forecast(it.city, it.temperatureC, it.condition, System.currentTimeMillis()) }
                }
            }.first
        }
}
```

Provide the Ktor `HttpClient` (with `ContentNegotiation`, `HttpRequestRetry`, `HttpTimeout`, and the OkHttp engine sharing your singleton) in a Hilt module. Notice how little differs from the Retrofit source — the DTO, the `NetworkResult`, the retry, the dispatcher injection are all identical. That sameness is the lesson.

## Milestone 4 — One repository, swappable engines (≈ 1 h)

The repository depends on *one* source, selected by a qualifier, so you can flip engines by changing a single binding:

```kotlin
@Qualifier annotation class RetrofitEngine
@Qualifier annotation class KtorEngine

interface WeatherSource { suspend fun fetch(city: String): NetworkResult<Forecast> }
// (Make RetrofitWeatherSource and KtorWeatherSource implement WeatherSource.)

class DefaultWeatherRepository @Inject constructor(
    @RetrofitEngine private val source: WeatherSource,    // flip to @KtorEngine to swap
    private val dao: ForecastDao                           // the Room store from Week 14
) : WeatherRepository {

    override suspend fun refresh(city: String): NetworkResult<Forecast> {
        val result = source.fetch(city)
        if (result is NetworkResult.Success) {
            dao.upsert(result.data.toEntity())                // cache into Room
        }
        return result                                          // also return for one-shot callers
    }

    override fun observe(city: String): Flow<Forecast?> =
        dao.observe(city).map { it?.toDomain() }              // Room is the source of truth
}
```

Flipping `@RetrofitEngine` to `@KtorEngine` on one constructor parameter swaps the entire networking engine with zero other changes. That's the payoff of building to the interface.

## Milestone 5 — The offline-first wiring (≈ 1 h)

Wire the UI to read **Room**, not the network, with the network as the fill. This is the offline-first shape:

```kotlin
@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val repo: WeatherRepository
) : ViewModel() {
    private val city = MutableStateFlow("Lisbon")

    // The UI observes the CACHE (Room) — always shows the last-known data instantly.
    val forecast: StateFlow<Forecast?> = city
        .flatMapLatest { repo.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _status = MutableStateFlow<NetworkResult<Forecast>?>(null)
    val status: StateFlow<NetworkResult<Forecast>?> = _status   // for showing errors/loading

    fun refresh() = viewModelScope.launch { _status.value = repo.refresh(city.value) }
}
```

The screen shows the cached forecast immediately (from Room) and triggers a `refresh()` that fills the cache; the `status` carries the `NetworkResult` so the UI can show "offline — showing last update" on a `NetworkError`. Cached data is never blank just because the network is down — that's offline-first.

## Milestone 6 — Test the failure paths (≈ 0.5 h)

The acceptance bar. Point the client at a hostile server and confirm it degrades into a handled state every time:

1. **A 500** (`MockWebServer.enqueue(MockResponse().setResponseCode(500))` / Ktor `MockEngine`) → after bounded retry, `NetworkResult.HttpError(500)`. The cached forecast still shows.
2. **A timeout** (a `MockResponse` with a long `bodyDelay`, or a `SocketTimeoutException`) → `NetworkResult.NetworkError`, failed fast by the timeout. Not a hang.
3. **A malformed body** (`MockResponse().setBody("{not json}")`) → `NetworkResult.SerializationError`. Not a crash.

Write these as tests for *both* the Retrofit and the Ktor source — same assertions, two engines. "It worked when the network was perfect" is not the test.

## Bonus path — gRPC (≈ +2 h, stretch)

Define the weather service in a `.proto`, generate the `grpc-kotlin` stubs, and implement a third `GrpcWeatherSource` returning the same `NetworkResult` (mapping `StatusException`). Add a server-streaming `streamUpdates` that returns a `Flow<Forecast>`. Write one sentence in the README on when you'd choose gRPC for this client (a backend you control, streaming updates, compact payloads at scale) and when you wouldn't (a public REST weather API you don't control).

---

## Acceptance criteria

- [ ] A `WeatherRepository` interface returning `NetworkResult<Forecast>` and a cached `Flow<Forecast?>` from Room; domain model separate from wire DTOs.
- [ ] A **Retrofit** source and a **Ktor** source, both implementing one `WeatherSource` interface, both returning the same `NetworkResult`, both with bounded retry and timeouts.
- [ ] Both clients, the OkHttpClient (`@Singleton`), and the Ktor `HttpClient` are provided through the **Week-13 Hilt graph** (the `:core-network` module filled in), with the engine selected by a `@Qualifier`.
- [ ] The forecast **caches into the Week-14 Room store**; the UI reads Room via a `Flow` (offline-first), the network fills it.
- [ ] **Failure-path tests** for both engines: a 500, a timeout, and a malformed body each produce the right `NetworkResult` (via `MockWebServer`/`MockEngine`).
- [ ] Logging redacts secrets; `ignoreUnknownKeys = true`; no per-request client.
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **The gRPC bonus path** (above).
- **HTTP caching.** Add an OkHttp response `Cache` and confirm a repeated request is served from cache (watch the `X-Cache` / the logging interceptor's cache-hit indication).
- **Certificate pinning (rotation-safe).** Pin the API's cert with a backup pin and a kill-switch flag (lecture 2 / the challenge). Confirm a MITM proxy is blocked.
- **The challenge's retry storm.** Carry `RESILIENCE.md` over — measure the naive vs. bounded retry against a `MockWebServer` of 503s.
- **`RemoteMediator` preview.** Note in the README where Paging 3's `RemoteMediator` would slot in to page network results into Room — the bridge to Week 16's sync.

## What this milestone earns you

You can now write networking that retries correctly, parses safely, fails into a type the UI must handle, and choose between three solid stacks and defend it — the literal "skills earned" lines for the week. More than that: you built the same client twice over one interface and felt that the discipline is constant, which is the exact insight that lets the capstone share its data layer across platforms with Ktor. The network→Room→`Flow`→UI offline-first wiring you built here is the foundation Week 16 turns into a scheduled, constraint-aware sync engine. You'll be glad the failure handling is solid before you put it on a background schedule.
