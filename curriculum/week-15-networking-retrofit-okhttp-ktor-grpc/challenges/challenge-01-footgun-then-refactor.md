# Challenge 1 — Plant a retry-storm footgun, then refactor it (with numbers)

**Time.** 60–120 minutes.
**Deliverable.** A short report (`RESILIENCE.md`) with two request-count measurements, the refactored code, and a written certificate-pinning rotation analysis, committed to your Week 15 repo.

## The premise

Every senior engineer has, at least once, shipped a retry that made an outage worse. It works perfectly when the server is healthy. Then the server has a bad five minutes, every client's naive retry loop fires as fast as it can, and your *own retry traffic* becomes a DDoS that keeps the server down — a "retry storm." The skill this challenge builds is not "know retries need backoff" — it's **plant the storm, measure it, fix it, and prove the fix with a request count.** A resilience fix you can't quantify is a guess. Then you'll do the same reasoning for certificate pinning: trace the catastrophic failure, design the safe version.

## Part A — The retry storm

### Step 1 — Stand up a flaky server with `MockWebServer`

`MockWebServer` (OkHttp's test server) lets you script responses and **count requests**. Enqueue a run of 503s:

```kotlin
val server = MockWebServer()
repeat(1000) { server.enqueue(MockResponse().setResponseCode(503)) }  // always overloaded
server.start()
val baseUrl = server.url("/")
// After the test: server.requestCount tells you how many requests actually hit it.
```

### Step 2 — Plant the footgun (the WRONG retry)

Write the naive unbounded tight-loop retry and point it at the flaky server. Run it for a fixed wall-clock window (say 5 seconds) and record `server.requestCount`.

```kotlin
// THE BITE: retries instantly, forever, with no backoff, no bound, no jitter.
// Against a 503-ing server this fires as fast as the machine can make requests.
suspend fun fetchNaive(api: WeatherApi): String {
    while (true) {
        try {
            return api.forecast("lisbon")        // 503 -> exception -> immediate retry
        } catch (e: Exception) {
            // no delay, no bound — straight back to the top
        }
    }
}
```

Run it inside a `withTimeout(5_000)` so it stops, then read `server.requestCount`. On a typical machine this is **hundreds to thousands of requests in 5 seconds** — that's the storm. Record the number.

### Step 3 — Refactor to bounded backoff (the RIGHT retry)

Use the `retrying` + `isRetryable` logic from exercise 2: bounded attempts, exponential backoff, jitter, retryable-only. Re-run against a fresh flaky server and record the request count again.

```kotlin
suspend fun fetchResilient(api: WeatherApi): NetworkResult<String> =
    retrying(maxAttempts = 4, baseDelayMs = 500) {
        safeApiCall { api.forecast("lisbon") }
    }.first   // bounded: at most 4 requests, spaced out, then returns the 503 as HttpError
```

Now `server.requestCount` should be **exactly 4** (or however many `maxAttempts` allows), spread over several seconds, after which the operation *returns a handled failure* instead of hammering forever. Record the number and the elapsed time.

### Step 4 — Prove the same answer

Assert that against a server that recovers (enqueue two 503s then a 200), *both* versions eventually return the same successful body — a faster-failing-but-wrong fix is worthless. The resilient version must still succeed when the server recovers; it just doesn't storm when it doesn't.

## Part B — The certificate-pinning rotation analysis

No code required — this is the operational reasoning that separates a senior engineer from someone who copy-pasted a `CertificatePinner`. Write it in `RESILIENCE.md`.

### Step 5 — Trace the catastrophic failure

You ship this:

```kotlin
val pinner = CertificatePinner.Builder()
    .add("api.crunch.example", "sha256/SINGLE_LEAF_KEY_PIN=")   // ONE pin, the current leaf
    .build()
```

Answer, in writing: **the server rotates its TLS certificate to a new key (routine, happens annually). What fraction of installed app instances can make a successful request the moment rotation completes, and for how long?** Walk the blast radius: which users are affected (all of them, until they update), how long until they recover (until a store update propagates — days), and why this is worse than no pinning at all.

### Step 6 — Design the rotation-safe pin set

Design the configuration that makes the next rotation a non-event, and explain each choice:

```kotlin
val pinner = CertificatePinner.Builder()
    .add("api.crunch.example",
        "sha256/CURRENT_LEAF_KEY=",       // current
        "sha256/NEXT_LEAF_KEY=",          // backup: the key the server WILL rotate to
        "sha256/INTERMEDIATE_CA_KEY=")    // the more-stable intermediate
    .build()
// + a remote-config kill switch to disable pinning without a store release.
```

Explain: why the backup pin (matches after rotation, zero outage); why pinning the intermediate (survives a leaf rotation under the same CA); why the kill switch (recover from a pinning misconfig without a multi-day store release); and the operational cost you've signed up for (coordinating rotation with the backend team's calendar).

## Acceptance criteria

- [ ] `MockWebServer` seeded with 503s; the naive retry's `requestCount` measured over a fixed window and recorded.
- [ ] The resilient retry's `requestCount` measured and recorded — bounded to `maxAttempts`, spaced by backoff.
- [ ] An assertion that both versions return the same body when the server recovers (a faster wrong answer is worthless).
- [ ] `RESILIENCE.md` records: the two request counts, the speedup/reduction factor, and the machine you measured on.
- [ ] `RESILIENCE.md` contains the pinning rotation analysis: the catastrophic single-pin blast radius (with the "what fraction, for how long" answer) and the rotation-safe pin set with each choice justified.
- [ ] Build with **0 warnings**.

## What "great" looks like

A weak submission says "the bounded retry sent fewer requests." A great submission says:

> Against a `MockWebServer` returning only 503s, the naive unbounded retry fired **2,140 requests in a 5-second window** (≈428 req/s) — at scale, that retry traffic *is* the DDoS keeping the server down. The bounded version with `maxAttempts = 4` and exponential backoff (500/1000/2000 ms + jitter) fired exactly **4 requests over 4.1 seconds**, then returned the 503 as a handled `HttpError(503)` — a 535× reduction in request volume, and crucially it *stops*. Both versions return the same `"21.5°C Sunny"` body when the server recovers after two 503s. On pinning: a single-leaf `CertificatePinner` means **100% of installed instances fail every request the instant the server rotates its key, and stay broken until a store update propagates (2–5 days)** — strictly worse than no pinning. The rotation-safe set pins the current leaf, the pre-generated next leaf (so rotation day is a non-event), and the more-stable intermediate CA, plus a remote kill switch — accepting the cost of coordinating rotation with the backend team's calendar.

Quantified, explained, and operationally honest about the costs. That's the senior-engineer answer.

## Where this reappears

The bounded-backoff instinct is exactly what Week 16 (WorkManager) builds on — a periodic sync job uses `BackoffPolicy.EXPONENTIAL`, the same idea at a coarser timescale (minutes, not milliseconds). The pinning analysis returns in Week 22 (security), where pinning sits alongside Keystore and Play Integrity, and the capstone's chaos drills include a rotation-style scenario. The "measure the request storm, don't guess" discipline is the same one Week 18 applies to performance with a flame graph.
