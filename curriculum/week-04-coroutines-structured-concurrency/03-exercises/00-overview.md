# Week 04 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Disassemble a suspend function](./exercise-01-disassemble-a-suspend-function.md)** — write a function with two suspend calls, compile it, run `javap -c -p`, and find the synthesised `Continuation` parameter, the state-machine label, and the suspend points. Make the CPS transform concrete. (~40 min)
2. **[Exercise 2 — Structured vs leaky](./exercise-02-structured-vs-leaky.kt)** — implement the same fan-out two ways: a leaky `GlobalScope` version and a structured `coroutineScope` version, then prove with `runTest` that cancelling the owner stops the structured one and *fails* to stop the leaky one. (~50 min)
3. **[Exercise 3 — Cooperative cancellation](./exercise-03-cooperative-cancellation.kt)** — take a non-cooperative CPU loop that ignores `cancel()`, make it cancellable three ways, and fix a `catch` that swallows `CancellationException`. Measure that the fixed version stops promptly. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Run it as **plain JVM Kotlin** — a Gradle application target, or a `runTest` suite using `kotlinx-coroutines-test`. No Android, no emulator this week.
- The `.kt` exercises are written to drop into a Kotlin/JVM test source set (`src/test/kotlin`) with `kotlinx-coroutines-test` on the test classpath. Each file's header says how to run it.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A coroutine that outlives its scope is a bug this week — `GlobalScope` is not a fix, it is the thing we are eliminating.

## What you'll need on the classpath

```toml
# libs.versions.toml
[versions]
kotlin = "2.1.0"
coroutines = "1.9.0"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
```

```kotlin
// build.gradle.kts
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(kotlin("test"))
}
```

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-04` to compare.
