# Week 15 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 16. Answer key with explanations at the bottom — don't peek.

---

**Q1.** What is the relationship between Retrofit and OkHttp?

- A) They're competing libraries; you pick one.
- B) Retrofit is a typed front end that turns an interface into HTTP calls; OkHttp is the engine underneath that actually makes them and where production concerns (interceptors, caching, pinning, timeouts) live.
- C) OkHttp is built on Retrofit.
- D) They're unrelated; Retrofit uses Java's `HttpURLConnection`.

---

**Q2.** Why model network outcomes as a sealed `NetworkResult<T>` rather than throwing exceptions?

- A) It's faster.
- B) The failure modes become *cases in a type*, so an exhaustive `when` forces the UI to handle every one — a missing case is a compile error, not a crash in production.
- C) Exceptions don't work with coroutines.
- D) It avoids serialization.

---

**Q3.** A Retrofit `suspend fun forecast(...): ForecastDto` can fail in three distinct ways. Which set?

- A) Only network errors.
- B) HTTP error (server responded non-2xx), network error (no response — timeout/no connectivity), and serialization error (response didn't match the DTO).
- C) Only serialization errors.
- D) Compile errors, runtime errors, and logic errors.

---

**Q4.** In an exponential-backoff retry, which failures should you retry, and which must you not?

- A) Retry everything until it works.
- B) Retry transient failures (5xx, 429, timeout); never retry a 4xx — a client error replayed unchanged fails identically forever.
- C) Retry only 4xx.
- D) Never retry anything.

---

**Q5.** Why does an exponential backoff add random *jitter*?

- A) To make the code look sophisticated.
- B) Without jitter, many clients that failed at the same instant all retry at the same instant — a synchronized "thundering herd" that re-creates the overload. Jitter spreads the retries.
- C) Jitter makes individual requests faster.
- D) It's required by HTTP.

---

**Q6.** Why must the `OkHttpClient` be a shared `@Singleton` rather than constructed per request?

- A) It isn't; construct one per request for isolation.
- B) It owns a connection pool and thread pools; a per-request client defeats connection reuse and leaks threads.
- C) Singletons are always faster.
- D) Retrofit requires exactly one.

---

**Q7.** You pin a single leaf certificate's key. The server rotates to a new key. What happens?

- A) Nothing; pinning auto-updates.
- B) Every installed app instance fails every request the instant rotation completes, until users update — you've bricked the app for everyone.
- C) Only new installs are affected.
- D) The app falls back to no pinning automatically.

---

**Q8.** What makes certificate pinning rotation-safe?

- A) Pinning only the leaf.
- B) Pinning a backup (the next key the server will rotate to) and/or the more-stable intermediate CA, plus a remote kill switch to disable pinning without a store release.
- C) Never rotating the certificate.
- D) Pinning the root CA only.

---

**Q9.** What is the single decisive reason to choose Ktor Client over Retrofit?

- A) Ktor is always faster.
- B) Ktor compiles in `commonMain` — the same client code runs on Android and iOS in a Kotlin Multiplatform module; Retrofit is JVM/Android-only.
- C) Ktor doesn't need serialization.
- D) Retrofit is deprecated.

---

**Q10.** When is gRPC the right choice over REST?

- A) For a public API you don't control.
- B) A binary contract with a backend you control, heavy streaming (server-streaming `Flow`), or performance-critical compact payloads at scale.
- C) For a browser client.
- D) gRPC is always the right choice.

---

**Q11.** What's the difference between an OkHttp *application* interceptor and a *network* interceptor for your logging/auth?

- A) No difference.
- B) An application interceptor runs once per call and does *not* fire on a cache hit served without the network; a network interceptor runs per network request and sees redirects/retries. Auth/logging are usually application interceptors.
- C) Network interceptors are deprecated.
- D) Application interceptors can't modify the request.

---

**Q12.** Why set `ignoreUnknownKeys = true` on the kotlinx-serialization `Json`?

- A) To skip validation entirely.
- B) So a server *adding* a new field doesn't crash the parse for every user on the next deploy — an additive server change shouldn't break clients.
- C) It makes parsing faster.
- D) It encrypts the body.

---

**Q13.** In the offline-first wiring, what is the single source of truth the UI reads, and what role does the network play?

- A) The UI reads the network directly; Room is a backup.
- B) The UI reads **Room** (via a `Flow`) so it always shows last-known data instantly; the network *fills* the cache. Cached data is never blank just because the network is down.
- C) The UI reads both and merges them by hand.
- D) There is no cache; every read hits the network.

---

## Answer key

**Q1 — B.** Retrofit is the typed front end (interface → HTTP); OkHttp is the engine where interceptors, caching, pinning, and timeouts live. The same OkHttp engine also backs Ktor's Android path. (Lecture 1, §1–3.)

**Q2 — B.** A sealed `NetworkResult` turns failures into typed cases an exhaustive `when` must handle — a missing case is a compile error. This is the Week-2 algebraic-modeling skill applied to the network's failure modes. (Lecture 1, §4.)

**Q3 — B.** HTTP error (`HttpException`, non-2xx), network error (`IOException`, no response), serialization error (`SerializationException`, wrong shape). `safeApiCall` maps each to a distinct case. (Lecture 1, §4.)

**Q4 — B.** Retry transient 5xx/429/timeout; never a 4xx — a client error is the request's fault and replaying it unchanged fails identically (a 401 retry-loop is a self-inflicted lockout). (Lecture 1, §5.)

**Q5 — B.** Jitter desynchronises clients so they don't all retry simultaneously and re-create the overload (the thundering herd). It's the difference between a recovering server and a self-sustained outage. (Lecture 1, §5; lecture 2, §5.)

**Q6 — B.** The `OkHttpClient` owns a connection pool and thread pools; a per-request client kills pooling and leaks threads. It's the canonical `@Singleton` (Week 13). (Lecture 1, §2; lecture 2, §5.)

**Q7 — B.** A single-leaf pin means 100% of installs fail every request the instant the server rotates, until a store update propagates (days). It's strictly worse than no pinning — the most common catastrophic pinning mistake. (Lecture 2, §1.)

**Q8 — B.** Backup pins (the next key, pre-pinned), pinning the more-stable intermediate CA, and a remote kill switch — plus coordinating rotation with the backend team. (Lecture 2, §1.)

**Q9 — B.** Ktor runs in `commonMain`, so the same client compiles for Android and iOS — the decisive reason for the KMP shared core. Everything else (DTOs, `NetworkResult`, retry) is identical to Retrofit. (Lecture 2, §2.)

**Q10 — B.** gRPC wins for a binary contract you control, heavy streaming, or compact performance-critical payloads. It loses for public APIs, browsers, and small apps where JSON's debuggability wins. (Lecture 2, §3–4.)

**Q11 — B.** Application interceptors run once per call and skip cache hits; network interceptors run per network request and see redirects/retries. Auth and logging are usually application interceptors — getting this wrong is why logging "didn't fire" on a cache hit. (Lecture 1, §2.)

**Q12 — B.** `ignoreUnknownKeys = true` lets a server *add* a field without crashing the parse — an additive server change shouldn't break clients. (Missing required fields still throw; model optional fields as nullable-with-default.) (Lecture 1, §3; lecture 2, §5.)

**Q13 — B.** The UI reads Room (the single source of truth) via a `Flow`, so it shows last-known data instantly; the network fills the cache. This is the offline-first shape that makes the app usable with no connection. (Lecture 1, §7 forward ref; mini-project Milestone 5.)

---

*Score 11+? On to Week 16. Below 9? Re-read both lecture notes and re-run exercises 2 and 3 — the `NetworkResult` failure model with bounded retry and the "same discipline, different engine" Ktor parallel are the two ideas this week is graded on.*
