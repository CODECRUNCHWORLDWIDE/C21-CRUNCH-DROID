# Week 15 — Resources

Every primary resource on this page is **free**. The library documentation (Retrofit, OkHttp, Ktor, gRPC) is open. The Android developer documentation is free. The Now-In-Android sample is open source on GitHub under Apache-2.0. A handful of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **Retrofit documentation** — the typed service, converters, and call adapters. Read this before you write a service interface:
  <https://square.github.io/retrofit/>
- **OkHttp — Interceptors** — the application-vs-network interceptor chain, the single most important OkHttp concept this week:
  <https://square.github.io/okhttp/features/interceptors/>
- **OkHttp — HTTPS and `CertificatePinner`** — pinning and (critically) the rotation guidance:
  <https://square.github.io/okhttp/features/https/> and <https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/>
- **kotlinx.serialization guide** — `@Serializable`, the `Json` configuration, and the Retrofit/Ktor converters:
  <https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serialization-guide.md>
- **"Connecting to the network"** — Android's overview of HTTP on Android and the permission/threading model:
  <https://developer.android.com/training/basics/network-ops/connecting>

## Ktor Client

- **Ktor Client overview** — engines, plugins, the multiplatform story:
  <https://ktor.io/docs/client-create-new-application.html>
- **`ContentNegotiation` + kotlinx-serialization:** <https://ktor.io/docs/client-serialization.html>
- **`HttpRequestRetry` plugin** (Ktor's built-in backoff): <https://ktor.io/docs/client-retry.html>
- **`HttpTimeout` plugin:** <https://ktor.io/docs/client-timeout.html>
- **The OkHttp engine for Ktor on Android** (share the OkHttp engine you already configured): <https://ktor.io/docs/client-engines.html#okhttp>
- **`MockEngine` for testing** (no real network in tests): <https://ktor.io/docs/client-testing.html>

## gRPC on Android

- **gRPC Kotlin** — the `grpc-kotlin` quickstart and the coroutine stubs:
  <https://grpc.io/docs/languages/kotlin/>
- **gRPC Android basics tutorial:** <https://grpc.io/docs/platforms/android/kotlin/basics/>
- **Protocol Buffers language guide** (the `.proto` schema your stubs come from): <https://protobuf.dev/programming-guides/proto3/>
- **gRPC over TLS / channel security:** <https://grpc.io/docs/guides/auth/>

## The HTTP lineage (why this matters)

When networking behaves surprisingly, the explanation is often in the HTTP layer below your typed client.

- **MDN HTTP overview** — status codes, methods, caching headers; the substrate Retrofit and Ktor sit on:
  <https://developer.mozilla.org/en-US/docs/Web/HTTP>
- **HTTP caching (`Cache-Control`, `ETag`)** — what OkHttp's response `Cache` honours:
  <https://developer.mozilla.org/en-US/docs/Web/HTTP/Caching>
- **TLS and certificate chains** — leaf, intermediate, root; essential to understanding what you pin and why rotation breaks naive pinning:
  <https://developer.mozilla.org/en-US/docs/Web/Security/Transport_Layer_Security>

## Retry, backoff, and resilience (current, correct)

- **AWS Architecture Blog — "Exponential backoff and jitter"** — the canonical explanation of why jitter matters and how a naive retry storm forms:
  <https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/>
- **Google SRE book — "Handling Overload" / "Addressing Cascading Failures"** — why an unbounded retry DDoSes your own backend:
  <https://sre.google/sre-book/handling-overload/>

## Source to read this week (this is the assignment that teaches the most)

You learn more from one hour reading a production network layer than from three hours of tutorials. Read **Now-In-Android** — Google's reference app — specifically `core/network/`:

- **`android/nowinandroid`** — read `core/network/.../retrofit/`, the `@Serializable` DTOs, the OkHttp configuration, and how the network layer feeds the Room cache:
  <https://github.com/android/nowinandroid>
- **`square/okhttp` recipes** — the official recipes repo; the interceptor, caching, and pinning recipes are exemplary:
  <https://github.com/square/okhttp/tree/master/samples>
- **`android/architecture-samples`** — smaller examples if Now-In-Android is too much at once:
  <https://github.com/android/architecture-samples>

## Tools you'll use this week

- **A public test API** — `https://httpbin.org` (echoes requests, can return arbitrary status codes — perfect for testing retry against 500s), or a public weather API for the mini-project.
- **OkHttp's `MockWebServer`** — a real local HTTP server you control from a test: enqueue a 500, a timeout, a malformed body, and assert your client handles each. The single most useful test tool this week.
- **Ktor's `MockEngine`** — the same idea for Ktor Client tests, no real network.
- **`HttpLoggingInterceptor`** — set to `BODY` in debug to see every request/response (redact secrets in release).
- **`adb shell` + a proxy (mitmproxy/Charles)** — optional, to watch the actual bytes on the wire and confirm pinning blocks a MITM proxy.

## Community writing (current, opinionated, correct)

- **Jesse Wilson / Square engineering** — OkHttp's author on interceptors, pinning, and HTTP internals:
  <https://publicobject.com/> and <https://developer.squareup.com/blog/>
- **Chris Banes' blog** — practical Retrofit/Ktor and DI-of-networking notes:
  <https://chrisbanes.me/>
- **The Android Developers Medium publication** — current networking and serialization articles:
  <https://medium.com/androiddevelopers>

## Free books (chapter-level)

- **Android's "Guide to app architecture — the data layer"** is effectively a free book on where the network layer lives and how it feeds the repository/cache:
  <https://developer.android.com/topic/architecture/data-layer>

## Paid books (optional, clearly marked)

- **"OkHttp / Retrofit" chapters in various Android O'Reilly titles** (paid). The official docs above cover everything for free.
- **"gRPC: Up and Running"** — O'Reilly (paid). The clearest linear narrative on gRPC concepts if the streaming/contract model is new to you.

---

*If a link 404s, please open an issue so we can replace it.*
