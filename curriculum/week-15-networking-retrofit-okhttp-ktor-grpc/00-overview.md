# Week 15 — Networking: Retrofit, OkHttp, Ktor Client, gRPC

Welcome to Week 15 of **C21 · Crunch Droid**. Two weeks ago you built the Hilt graph; last week you filled the `:core-database` module with a real Room store. But that store has been empty, or seeded with fakes — the data hasn't come from anywhere. This week it gets a *source*. Your app starts talking to a backend, and by Friday it fetches real data over the wire, parses it into typed Kotlin, handles the failures that real networks throw at you, and caches the result into the Room database you built last week — the offline-first shape the capstone needs.

There is no single "Android networking library." There are three serious ones, each strongest in a different context, and a senior Android engineer in 2026 can reach for the right one and say why. **Retrofit** (over OkHttp) is the default for REST/JSON on Android — a typed interface where each method is an HTTP endpoint, with kotlinx-serialization turning JSON into data classes the compiler checks. **OkHttp** is the HTTP engine *underneath* Retrofit, and the layer where the production concerns live: interceptors for logging and auth, response caching, connection pooling, timeouts, and **certificate pinning**. **Ktor Client** is the Kotlin-Multiplatform-friendly choice — the same client code runs in `commonMain` and ships to iOS, which is exactly why the capstone's shared core uses it. And **gRPC** (`grpc-kotlin`) is the binary-contract option: a `.proto` schema generates typed client and server stubs, streaming is first-class, and the wire format is compact — the capstone syncs over gRPC. The week's frame is one sentence: **Retrofit for REST, Ktor for KMP, gRPC for binary contracts — and OkHttp under most of it.**

The mental shift this week is from "I `fetch` and hope" to "every network call is a typed operation that *will* fail, and I model the failure." A real network times out, returns a 500, drops mid-stream, serves stale data, and rotates its TLS certificate. Code that ignores those isn't networking code; it's a demo. So the spine of the week is a sealed **`NetworkResult<T>`** type — `Success`, `HttpError(code)`, `NetworkError`, `SerializationError` — that makes the failure modes part of the type, so the compiler forces the UI to handle them. You will write the retry that backs off correctly (not a tight loop that hammers a struggling server), the timeout that fails fast instead of hanging the UI, and the certificate pin that secures the connection *without* bricking the app at the next cert rotation — the single most common way pinning goes wrong in production.

We close the week by building a **weather client implemented twice** — once with Retrofit, once with Ktor — both returning the same sealed `NetworkResult`, both wired through the Week-13 Hilt graph and caching into the Week-14 Room store. A bonus path implements the same client over gRPC. By the end you can write networking that retries correctly, parses safely, fails into a type the UI must handle, and you can defend the choice of stack for a given context — the literal interview question for a senior Android role.

## Learning objectives

By the end of this week, you will be able to:

- **Build** a Retrofit service: a typed interface with `@GET`/`@POST`/`@Query`/`@Path`/`@Body`, kotlinx-serialization for JSON, and `suspend` functions returning typed results.
- **Configure** OkHttp for production: a logging interceptor (redacting secrets), an auth interceptor that adds and refreshes a token, an HTTP response cache, and sane timeout/connection-pool defaults.
- **Pin** a server's certificate with OkHttp's `CertificatePinner`, and design the pin set so a routine cert rotation does *not* brick the app (backup pins, the intermediate-CA pin, the kill-switch plan).
- **Model** every network outcome with a sealed `NetworkResult<T>` and a `safeApiCall` wrapper that maps HTTP errors, IO failures, and serialization failures into distinct typed cases the UI must handle.
- **Retry** correctly with exponential backoff and jitter, distinguish retryable (transient 5xx, timeout) from non-retryable (4xx) failures, and bound the retry so it fails instead of hanging forever.
- **Write** a Ktor Client with the same typed contract and `NetworkResult`, and explain why Ktor (pure Kotlin, no codegen, multiplatform) is the right call for the KMP shared core and Retrofit is the right call for an Android-only app.
- **Implement** a gRPC client on Android with `grpc-kotlin`: a `.proto` contract, generated stubs, a unary call, and a streaming call — and explain when a binary contract beats REST.
- **Recognise** the networking footguns — the unbounded retry that DDoSes your own backend, the brittle single-pin that bricks on rotation, the main-thread call, parsing the wrong shape, the leaked `OkHttpClient` per request — and the production fixes.

## Prerequisites

This week assumes you have completed **C21 weeks 1–14**, or have equivalent fluency. Specifically:

- You can write idiomatic Kotlin — sealed classes, data classes, generics, suspend functions — Weeks 1–3. The `NetworkResult<T>` sealed type and the typed Retrofit interface are exactly the algebraic-modeling skills from Week 2.
- You understand coroutines, structured concurrency, dispatchers, and `Flow` — Weeks 4–5. Every network call is a `suspend` function on `Dispatchers.IO`; retry and timeout are coroutine patterns; a streaming response is a `Flow`.
- You can model a Room schema and cache into it — Week 14. The offline-first pattern fetches over the wire and caches into Room as the single source of truth; you'll wire the network *into* last week's store.
- You can wire a Hilt graph with qualifiers — Week 13. You'll `@Provides` an `OkHttpClient`, a `Retrofit`, and a Ktor `HttpClient`, with an `@AuthClient`/`@PublicClient` qualifier split — the exact `:core-network` module you stubbed in Week 13.

**Toolchain.** Android Studio (2025.1 / Narwhal+), AGP 8.7+, Kotlin 2.1+, Retrofit 2.11+, OkHttp 4.12+/5.x, Ktor 3.x, kotlinx-serialization 1.7+, `grpc-kotlin` 1.4+ with protobuf. JDK 17. We use kotlinx-serialization throughout (not Moshi/Gson — kotlinx-serialization is the Kotlin-native, KMP-friendly default in 2026). Everything runs on the emulator against a public test API or a small local mock server.

## Topics covered

- **Retrofit + kotlinx-serialization.** The typed service interface, HTTP-verb annotations, query/path/body params, `suspend` functions, the `Json.asConverterFactory` converter, and `@Serializable` DTOs separated from domain models.
- **OkHttp the engine.** The `OkHttpClient` as the shared, `@Singleton` HTTP engine; the interceptor chain (application vs. network interceptors); `HttpLoggingInterceptor` with secret redaction; an auth interceptor and the `Authenticator` for 401-driven token refresh; the response `Cache`; timeouts and the connection pool.
- **Certificate pinning.** `CertificatePinner`, pinning the leaf vs. the intermediate CA, the rotation problem, backup pins, and the operational plan so a cert rotation is a non-event instead of an outage.
- **The `NetworkResult<T>` sealed type.** `Success`, `HttpError(code, body)`, `NetworkError(cause)`, `SerializationError(cause)`; a `safeApiCall { }` wrapper that catches `HttpException`, `IOException`, and `SerializationException` and maps them; surfacing the result into a `UiState` the Compose layer renders.
- **Retry, backoff, timeout.** Exponential backoff with jitter, the retryable/non-retryable distinction (don't retry a 400), a bounded retry count, per-call and per-connection timeouts, and `withTimeout`.
- **Ktor Client.** The `HttpClient` with the OkHttp (or CIO) engine, `ContentNegotiation` with kotlinx-serialization, typed `get`/`post` calls, the `HttpRequestRetry` and `HttpTimeout` plugins, and why the same code compiles in `commonMain`.
- **gRPC on Android.** The `.proto` service and message definitions, `protoc` + `grpc-kotlin` codegen, the `ManagedChannel`, a unary suspend call, a server-streaming `Flow` call, and TLS/pinning on a channel.
- **Choosing a stack.** The decision table — Retrofit (Android REST), Ktor (KMP), gRPC (binary contracts/streaming) — benchmarked against the same backend, with the trade-offs (codegen, payload size, multiplatform, ecosystem) made explicit.
- **Offline-first wiring.** Network → DTO → domain model → Room cache → `Flow` to the UI, with Room as the single source of truth and the network as the fill — the shape Week 16 makes fully offline-capable.
- **Footguns.** Unbounded retry, brittle single-pin, main-thread calls, parsing the wrong shape, a fresh `OkHttpClient` per request, and leaking response bodies (`response.use { }`).

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                                  | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|------------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | Retrofit + kotlinx-serialization; the OkHttp engine; interceptors       |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | `NetworkResult` sealed type; `safeApiCall`; retry/backoff/timeout       |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Certificate pinning; the rotation problem; footguns                     |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | Ktor Client; gRPC on Android; choosing a stack; challenge               |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — weather client twice (Retrofit + Ktor), shared result    |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work; cache into Room; bonus gRPC path                |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                            |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                        | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./00-overview.md) | This overview (you are here) |
| [resources.md](./01-resources.md) | The Retrofit, OkHttp, Ktor, and gRPC docs, the kotlinx-serialization guide, the Now-In-Android network layer, and the canonical writing on retry and pinning |
| [lecture-notes/01-retrofit-okhttp-and-the-network-result.md](./02-lecture-notes/01-retrofit-okhttp-and-the-network-result.md) | Retrofit end to end over OkHttp: the typed service, kotlinx-serialization, the interceptor chain, the `NetworkResult` sealed type and `safeApiCall`, retry/backoff/timeout, and where it leaks HTTP |
| [lecture-notes/02-pinning-ktor-grpc-and-choosing-a-stack.md](./02-lecture-notes/02-pinning-ktor-grpc-and-choosing-a-stack.md) | Certificate pinning and the rotation problem, the Ktor Client, gRPC on Android, the decision table for choosing a stack, and the networking footguns measured |
| [exercises/README.md](./03-exercises/00-overview.md) | Index of the three exercises |
| [exercises/exercise-01-retrofit-service-and-interceptors.md](./03-exercises/exercise-01-retrofit-service-and-interceptors.md) | Build a Retrofit service over OkHttp with kotlinx-serialization, a logging interceptor, and an auth interceptor; fetch real data and parse it |
| [exercises/exercise-02-network-result-and-retry.kt](./03-exercises/exercise-02-network-result-and-retry.kt) | Implement the `NetworkResult` sealed type, a `safeApiCall` mapper, and an exponential-backoff retry that distinguishes retryable from non-retryable failures; tested |
| [exercises/exercise-03-ktor-client.kt](./03-exercises/exercise-03-ktor-client.kt) | Build a Ktor Client with `ContentNegotiation` and the retry/timeout plugins, returning the same `NetworkResult`; tested against a mock engine |
| [challenges/README.md](./04-challenges/00-overview.md) | Index of the challenge |
| [challenges/challenge-01-footgun-then-refactor.md](./04-challenges/challenge-01-footgun-then-refactor.md) | Plant a naive unbounded-retry-against-flaky-server footgun (and a brittle single-pin), measure the request storm, refactor into bounded backoff with jitter and a rotation-safe pin set, and document the before/after |
| [quiz.md](./05-quiz.md) | 13 questions on Retrofit/OkHttp, interceptors, the `NetworkResult` type, retry/backoff, pinning/rotation, Ktor, gRPC, and stack choice |
| [homework.md](./06-homework.md) | Six practice problems for the week |
| [mini-project/README.md](./07-mini-project/00-overview.md) | Full spec for the weather client implemented twice (Retrofit + Ktor) over a shared `NetworkResult`, cached into Room, with a bonus gRPC path |

## The "it handles the failure" promise

Week 14 gave you "survives a cold launch." Week 15 adds the networking contract a senior reviewer checks first:

> **Every network outcome the user can hit must be a case the code handles, not an exception that crashes or a hang that never returns.** A timeout fails fast into a typed `NetworkError`, not a frozen UI. A 500 retries with backoff, then surfaces as a `HttpError` the screen can show. A 400 does *not* retry. A malformed body is a `SerializationError`, not a crash. If a flaky network can crash your app or hang it forever, the networking layer is broken, no matter how clean the happy path looks.

You will *prove* this by pointing the client at a server that times out, returns 500s, and serves malformed JSON, and confirming the app degrades into a handled state every time — "it worked when the network was perfect" is not the test.

## A note on what's not here

Week 15 is the *networking* week. It deliberately does **not** cover:

- **Offline sync and conflict resolution.** This week fetches and caches; the *scheduled, constraint-aware, exponential-backoff, conflict-resolving* sync engine is Week 16 (WorkManager) and the capstone. We wire network→Room as a forward reference and stop at "it caches."
- **WebSockets and real-time push.** FCM, WebSockets, and SSE are real-time topics; the capstone touches FCM. This week is request/response (plus gRPC streaming as the one streaming exception).
- **Backend implementation.** You consume an API; building the typed backend that serves it is C22 (Mesh) and out of scope. We use a public test API or a tiny mock server.

The point of Week 15 is narrow and deep: one typed client, the serialization that parses it safely, the sealed result that models every failure, the retry and pin that make it production-grade, and the three stacks you can choose between and defend.

## Up next

Continue to **Week 16 — Background work: WorkManager, foreground services, exact alarms** once you have shipped this week's mini-project and proven the client degrades gracefully under a hostile network. Week 16 takes the network→Room fetch you built this week and makes it a *scheduled, offline-first sync engine*: a periodic WorkManager job with exponential backoff (the same backoff you wrote this week, now at the job level), network and battery constraints, and a foreground-promotion path. Every week left in Phase 3, and the whole capstone, reads and writes over the network layer you build now. Earn it this week — production apps are mostly networking, error handling, and caching, and this is where you learn all three at once.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
