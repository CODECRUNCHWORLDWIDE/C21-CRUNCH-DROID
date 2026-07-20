# Week 15 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 15 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Kotlin 2.1+, Retrofit 2.11+, OkHttp 4.12+/5.x, Ktor 3.x, kotlinx-serialization 1.7+, JDK 17. Every problem must build with **0 warnings**.

---

## Problem 1 — Watch the wire with a logging interceptor

**Problem statement.** Using the exercise-1 client (or your mini-project), make a request and capture the full request/response from the `HttpLoggingInterceptor` at `BODY` level. Write your findings into `notes/wire.md`: the request line, the headers (with `Authorization` redacted), the response status, and the first lines of the body. Add one sentence: how do you confirm the auth header was added *and* redacted?

**Acceptance criteria.**

- `notes/wire.md` exists with the request line, redacted headers, status, and body excerpt — quoted from your actual logcat, not invented.
- The `Authorization` header appears as redacted (`██`), proving both that it was added and that `redactHeader` worked.
- Committed.

**Hint.** Set `HttpLoggingInterceptor.Level.BODY` and `redactHeader("Authorization")`. Filter logcat on `okhttp`. The redacted header is your evidence the interceptor chain ran in the right order.

**Estimated time.** 30 minutes.

---

## Problem 2 — Map every failure to a `NetworkResult` with `MockWebServer`

**Problem statement.** Using `MockWebServer`, write three tests that enqueue a 500, a malformed body, and a slow response (timeout), and assert your `safeApiCall` + Retrofit client produces `HttpError(500)`, `SerializationError`, and `NetworkError` respectively. Write a one-line note in `notes/failures.md` on the catch order in `safeApiCall` and why it matters.

**Acceptance criteria.**

- Three passing tests against `MockWebServer` covering the three failure flavours, each asserting the exact `NetworkResult` case.
- `notes/failures.md` explains the catch order (most specific first: HTTP and serialization before `IOException`).
- 0 warnings. Committed.

**Hint.** `server.enqueue(MockResponse().setResponseCode(500))`; `setBody("{not json}")`; `setBodyDelay(5, SECONDS)` with a short `readTimeout`. Point Retrofit's base URL at `server.url("/")`.

**Estimated time.** 50 minutes.

---

## Problem 3 — Measure a retry storm vs. bounded backoff

**Problem statement.** Against a `MockWebServer` returning only 503s, run the naive unbounded retry for a fixed 3-second window and record `server.requestCount`, then run the bounded exponential-backoff retry (`maxAttempts = 4`) and record the count again. Write both counts and the reduction factor into `notes/retry.md`.

**Acceptance criteria.**

- A test that measures both `requestCount`s against the same flaky server.
- `notes/retry.md` records the naive count, the bounded count (should equal `maxAttempts`), and the reduction factor, plus one sentence on why the naive version is dangerous at scale.
- 0 warnings. Committed.

**Hint.** Wrap the naive loop in `withTimeout(3_000)` so it stops. The bounded version uses the `retrying`/`isRetryable` from exercise 2. Use virtual time (`runTest`) for the bounded one so the backoff delays don't slow the test.

**Estimated time.** 45 minutes.

---

## Problem 4 — The same client in Ktor with `MockEngine`

**Problem statement.** Reimplement the exercise-2 weather call in Ktor Client and test it against `MockEngine`: a 200 → `Success(parsed)`, a 404 → `HttpError(404)`, a malformed body → `SerializationError`. Reuse the *same* `@Serializable` DTO and `NetworkResult` from the Retrofit path. Write one sentence in `notes/ktor.md` on what stayed the same and what changed.

**Acceptance criteria.**

- A Ktor client with `ContentNegotiation` and `expectSuccess = true`, returning the same `NetworkResult`, tested against `MockEngine`.
- The DTO and `NetworkResult` are *reused*, not rewritten.
- `notes/ktor.md` notes that the DTO/`NetworkResult`/safe-wrapper stayed the same and only the engine/API changed.
- 0 warnings. Committed.

**Hint.** `MockEngine { respond(content, status, headers) }`. `expectSuccess = true` is what makes Ktor throw on non-2xx so your `safeKtorCall` can map it. The reuse of the DTO is the point — if you're rewriting it, step back.

**Estimated time.** 45 minutes.

---

## Problem 5 — Write a rotation-safe pinning analysis

**Problem statement.** No code required. In `notes/pinning.md`, given a single-leaf `CertificatePinner`, trace the rotation failure: what fraction of installs fail, for how long, and why it's worse than no pinning. Then write the rotation-safe configuration (current pin + backup pin + intermediate + kill switch) and justify each element in one sentence.

**Acceptance criteria.**

- `notes/pinning.md` answers "what fraction of installs fail and for how long" for the single-leaf case (all of them, until a store update propagates — days).
- The rotation-safe config is written with each element justified (backup = zero-outage rotation; intermediate = survives leaf rotation; kill switch = recover without a store release).
- Committed.

**Hint.** The blast radius is the key insight: pinning is a *joint* client/server operational commitment. A single pin couples your app's availability to the server's cert lifecycle with no margin.

**Estimated time.** 35 minutes.

---

## Problem 6 — Wire the network into Room (offline-first)

**Problem statement.** Wire a `refresh()` that fetches a forecast and caches it into the Week-14 Room store, and an `observe()` that reads Room as a `Flow`. Confirm that with the network *off* (airplane mode / a `MockEngine` that throws `IOException`), the UI still shows the last cached forecast, and the `status` is a `NetworkError`. Write one sentence in `notes/offline.md` on why Room is the source of truth.

**Acceptance criteria.**

- A repository with `refresh()` (fetch + cache into Room) and `observe(): Flow` (read Room).
- A test or manual demo: network off → cached data still shows, `status` is `NetworkError`.
- `notes/offline.md` states that the UI reads Room (always has last-known data) and the network fills it.
- 0 warnings. Committed.

**Hint.** The UI's `forecast` `StateFlow` comes from `repo.observe()` (Room), never directly from the network. `refresh()` updates the cache as a side effect and surfaces the `NetworkResult` separately for error display. With the network off, `observe()` still emits the cached row.

**Estimated time.** 55 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, code is idiomatic Kotlin, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. a bare `catch(Exception)`, a missing timeout, a per-request client, Gson instead of kotlinx-serialization). |
| 3 | Works, but misses one criterion (e.g. retry not bounded, failure mapped to the wrong case, the DTO rewritten for Ktor instead of reused). |
| 2 | Compiles and partially works; a core idea is wrong (retrying a 4xx; an unbounded retry; a network call on the main thread; a single-leaf pin defended as safe). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for an unbounded retry or a retry that hammers a 4xx; **−2** for a network call on the main thread or a per-request `OkHttpClient`; **−1** for a leaked secret in logs (no `redactHeader`) or a strict parser with no `ignoreUnknownKeys`.

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — the `NetworkResult` failure model with bounded retry (problems 2, 3) and "same discipline, different engine" (problems 4, 6) — so re-run exercises 2 and 3 before resubmitting.
