# Week 15 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — A Retrofit service with interceptors](exercise-01-retrofit-service-and-interceptors.md)** — build a typed Retrofit service over OkHttp with kotlinx-serialization, a logging interceptor, and an auth interceptor; fetch real data from a test API and parse it into a data class. The foundation of the week, in one exercise. (~40 min)
2. **[Exercise 2 — `NetworkResult` and exponential-backoff retry](exercise-02-network-result-and-retry.kt)** — implement the `NetworkResult` sealed type, a `safeApiCall` that maps each failure flavour, and a bounded exponential-backoff retry that distinguishes retryable from non-retryable failures. Tested with a fake API that fails on cue. (~50 min)
3. **[Exercise 3 — The same client in Ktor](exercise-03-ktor-client.kt)** — build a Ktor Client with `ContentNegotiation` and the retry/timeout plugins, returning the *same* `NetworkResult`, tested against Ktor's `MockEngine` (no real network). Feel that the discipline is identical and only the engine changes. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Exercise 1 runs on the emulator against a public test API (`httpbin.org` or a weather API). Exercises 2 and 3 run as JVM tests (`./gradlew test`) using a fake API / Ktor `MockEngine`, so they need no network and no emulator — the file headers say which.
- The `.kt` exercises drop into a `src/test/kotlin` source set; each file's header says how to run it.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A network call that can hang forever or retry forever is a bug this week — bound and time-out everything.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-15` to compare.
