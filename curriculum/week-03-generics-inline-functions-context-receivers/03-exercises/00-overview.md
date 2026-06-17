# Week 03 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Variance and projections](./exercise-01-variance-and-projections.md)** — annotate a generic container and a set of functions with `in`/`out` so a fixed list of assignments type-checks, then *explain each annotation out loud*. The whole point of lecture 1 in one file: variance you can say, not just paste. (~40 min)
2. **[Exercise 2 — Inline and reified functions](./exercise-02-inline-reified-functions.kt)** — write `inline fun <reified T>` helpers (a typed `filterIsInstance`, a reified shape check, a reified enum lookup), then disassemble a call site with `javap` and *find the concrete type baked into the bytecode*. You produce the proof, not just the code. (~50 min)
3. **[Exercise 3 — Context receivers](./exercise-03-context-receivers.kt)** — take a function that threads a `Logger` and a `Clock` through its parameter list, refactor it to declare them as context receivers, and call it under nested `with`. Prove the compile-time requirement by trying to call it *without* the context and reading the error. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Run on the **JVM** — these are pure-Kotlin, no Android. A Gradle `:app` (JVM) module or a `main()` plus a JUnit test is enough. Exercise 2 wants you to run `javap`, so build to class files first (`./gradlew compileKotlin` or build in the IDE).
- Context receivers (exercise 3) need `freeCompilerArgs += "-Xcontext-receivers"` in your `build.gradle.kts`. The file's header shows the block.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** (an `UNCHECKED_CAST` you deliberately `@Suppress` and can *justify* is the one exception — see exercise 2) and pass its stated acceptance criteria. An unchecked cast you can't justify is a bug this week.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-03` to compare.
