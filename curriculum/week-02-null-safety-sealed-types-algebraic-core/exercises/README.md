# Week 02 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Null handling and platform types](exercise-01-null-handling-and-platform-types.md)** — replace `!!` and crash-prone code with the `?` family (`?.`, `?:`, `as?`, `?: return`), handle a platform-type boundary safely, and read the bytecode each operator compiles to. The null-safety discipline of the week, in one exercise. (~40 min)
2. **[Exercise 2 — Sealed types and exhaustive `when`](exercise-02-sealed-exhaustive-when.kt)** — model a closed domain as a sealed hierarchy, write `else`-free exhaustive `when`s, and *prove* that adding a case breaks the build until you handle it everywhere. The compiler-as-reviewer lesson, made concrete. (~50 min)
3. **[Exercise 3 — Inline value classes and `Result`](exercise-03-inline-value-classes-and-result.kt)** — stop a stringly/longly-typed bug with inline value classes, see the erasure in the decompiled bytecode, and model one outcome three ways (nullable, sealed, `Result<T>`). (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Run them on a **plain JVM** — no Android this week. The `.kt` exercises run as a **JUnit 5 test suite** in a Kotlin/JVM Gradle project (the same toolchain as Week 1); each file's header says how.
- Where an exercise has a `// won't compile` line, **uncomment it and read the error** — the compiler error is the lesson (especially the exhaustiveness errors in exercise 2). Re-comment so the suite builds.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A `!!` left where a `?.` belongs, or an `else` smuggled into a sealed `when`, is a smell this week grades on.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-02` to compare.
