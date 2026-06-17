# Week 05 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Cold is lazy and per-collector](./exercise-01-cold-is-lazy-and-per-collector.md)** — prove a cold `flow { }` runs *nothing* until collected and re-runs its producer *for each* collector, then convert it to a hot `StateFlow` and prove the difference. The cold/hot distinction, made concrete. (~40 min)
2. **[Exercise 2 — `flatMapLatest` search-as-you-type](./exercise-02-flatmaplatest-search.kt)** — build a debounced search that cancels the in-flight request for a stale query when a new one arrives, and use Turbine to prove the stale result *never appears*. (~50 min)
3. **[Exercise 3 — `callbackFlow` bridge without a leak](./exercise-03-callbackflow-bridge.kt)** — bridge a fake callback-based sensor into a Flow with `callbackFlow`, and prove with an assertion that `awaitClose` unregisters the listener when the collector is cancelled — no leak. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Run it as **plain JVM Kotlin** — a `runTest` + Turbine test source set. No Android, no emulator this week.
- The `.kt` exercises drop into `src/test/kotlin` with `kotlinx-coroutines-test` and `app.cash.turbine:turbine` on the test classpath. Each file's header says how to run it.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A flow test that `Thread.sleep`s is a bug this week — assert on emissions with Turbine and `runTest` virtual time.

## What you'll need on the classpath

```toml
# libs.versions.toml
[versions]
kotlin = "2.1.0"
coroutines = "1.9.0"
turbine = "1.2.0"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
```

```kotlin
// build.gradle.kts
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(kotlin("test"))
}
```

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-05` to compare.
