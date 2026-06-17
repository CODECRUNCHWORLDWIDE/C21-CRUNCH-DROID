# Week 01 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Read the bytecode](./exercise-01-read-the-bytecode.md)** — compile trivial Kotlin, run `javap -c -p`, and map four constructs (a top-level function, a `data class`, a default argument, a `const val`) to the bytecode they generate. The confidence skill of the week, in one exercise. (~40 min)
2. **[Exercise 2 — Equality and smart casts](./exercise-02-equality-and-smart-casts.kt)** — a test suite that pins down `==` vs `===`, reproduces the `Integer`-cache 127/128 split, and walks the exact boundaries where a smart cast is and isn't allowed. You produce passing assertions that *prove* the lecture. (~50 min)
3. **[Exercise 3 — Expressions over statements](./exercise-03-expressions-over-statements.kt)** — refactor statement-style "Java-in-Kotlin" into expression-style idiomatic Kotlin, with tests proving the behaviour is unchanged. Muscle memory for the central idiom. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Run them on a **plain JVM** — no Android, no emulator this week. The `.kt` exercises are written to run as a **Kotlin test suite** (JUnit 5, which ships in the starter) or as a `main` you call; each file's header says which. A Gradle Kotlin DSL starter is described in exercise 1 and the mini-project.
- See the output. Read the error if it didn't compile. A compiler error this week is usually a *lesson* — read what it says about smart casts or types.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A warning this week is a smell — Kotlin's warnings (unused `var`, redundant `else`, platform-type leak) are usually pointing at an idiom you missed.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-01` to compare.
