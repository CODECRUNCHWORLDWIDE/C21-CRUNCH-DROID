# Week 01 — Kotlin 2.x as a first-class JVM language

Welcome to Week 1 of **C21 · Crunch Droid**. Before we touch Compose, before we touch the Android runtime, before we wire a single dependency with Hilt, we are going to spend a week on the language — because the single biggest predictor of whether you write good Android code is whether you write good Kotlin, and the biggest predictor of *that* is whether you understand what Kotlin actually is. Kotlin is not "Java with less ceremony." It is a separate language with its own type system, its own idioms, and its own compiler that happens to emit JVM bytecode (and, on Android, gets fed through R8 and dexed into the ART format). This week you learn to write Kotlin that reads like Kotlin and not like translated Java, and you learn to *verify* what the compiler did by reading the bytecode it produced.

In 2026 the language you are learning is **Kotlin 2.x**, compiled by the **K2 compiler** — the rewritten frontend that JetBrains shipped as the default in Kotlin 2.0 and has hardened across the 2.1 and 2.2 releases. K2 is not a feature you use directly; it is the machinery that type-checks your code, resolves your overloads, and runs the Compose compiler plugin. But its existence changes how you reason about a few things — smart-cast precision, error messages, and the stability guarantees behind features like context parameters — so we walk it in conceptual terms early. You will not write a single line of "K2 code." You will, however, leave this week able to explain to a staff engineer what the frontend does and why the rewrite mattered.

The mental shift this week is from "I write statements that do things" to "I write expressions that *are* things." In Kotlin an `if` is an expression that returns a value. A `when` is an expression. A `try` is an expression. Most function bodies are a single expression after the `=`. Immutability is the default you reach for: `val` first, `var` only when you can justify the mutation out loud. Equality has two operators and you must know which is which (`==` is structural, calls `equals`; `===` is referential, compares identity). And the compiler tracks types more precisely than Java's ever did — once you check `x is String`, `x` *is* a `String` for the rest of that scope, with no cast, because of smart casts. These are not stylistic preferences. They are the substrate every later week is built on.

We close the week by building **`kt-stat`**, a command-line tool that walks a directory, counts source lines per language, and prints a colorized report — built with **Gradle Kotlin DSL** and packaged as a runnable fat JAR. It is deliberately not an Android app. The point is to meet Kotlin and Gradle on their own terms, on the JVM, where you can run `javap` on your own classes and *see* the bytecode, before the Android toolchain adds its own layers. By Friday you will have written idiomatic Kotlin, read the bytecode it compiled to, and stood up a real Gradle Kotlin DSL build from an empty directory — the toolchain you will use every single week for the next twenty-three.

## Learning objectives

By the end of this week, you will be able to:

- **Explain** what the K2 compiler frontend does (parsing, name resolution, type inference, smart-cast analysis, and the plugin pipeline that the Compose compiler hooks into) and why the 2.0 rewrite mattered for Android developers.
- **Write** idiomatic Kotlin that prefers expressions over statements, `val` over `var`, and top-level/extension functions over utility classes — and recognize "Java written in Kotlin syntax" when you see it in a review.
- **Distinguish** structural equality (`==`, lowering to `equals`) from referential equality (`===`), predict the result of each on boxed and unboxed values, and explain the `Int`/`Integer` caching footgun.
- **Use** smart casts confidently — know when the compiler can narrow a type after an `is`/`!= null` check and when it *cannot* (mutable `var`, custom getters, cross-module `open` properties).
- **Read** JVM bytecode with `javap -c` for a handful of trivial functions, and map a Kotlin construct (a top-level function, a `data class`, a default argument) to the bytecode it generates.
- **Stand up** a Gradle Kotlin DSL project from scratch: `build.gradle.kts`, `settings.gradle.kts`, the Kotlin JVM plugin, a `version catalog`, an application entry point, and a fat-JAR packaging task.
- **Reason about** type inference: where Kotlin infers and where you must annotate, why explicit return types on public API are a discipline, and how inference interacts with generics (previewing Week 3).

## Prerequisites

This week assumes **C1 (Crunch Convos)** or equivalent fluency in a typed, object-oriented language. Specifically:

- You know what a class, an interface, a method, and a field are, and you have written generics in *some* language (Java, C#, TypeScript, Swift). We will lean on that intuition; Week 3 makes it rigorous for Kotlin.
- You can read a stack trace and a compiler error without panicking, and you have used a command line to compile and run a program.
- You have **never needed Android** for any of this, and you don't need it this week either. Everything in Week 1 runs on a plain JVM.

A prior JVM background (from **C9 · Crunch Sharp** or any Java work) helps but is not required. If you come from Python or JavaScript, the typed-and-compiled model is the new thing; we go slowly on inference and equality, the two places dynamic-language developers stumble.

**Toolchain.** A **JDK 21** (any distribution — Temurin, Azul Zulu, Amazon Corretto, the JetBrains Runtime; all free and open source). **Kotlin 2.2.x** via the Gradle plugin (you do not install the compiler separately; Gradle fetches it). **Android Studio** (the 2025.x "Narwhal"-line release or newer) *or* **IntelliJ IDEA Community Edition** (free) — either works for this week, since we are pure JVM. **Gradle 8.x** via the wrapper that ships in the starter. The `javap` tool comes with the JDK. No paid software, no Android device, no emulator this week.

## Topics covered

- **Kotlin 2.x and K2.** What the K2 frontend is (a from-scratch rewrite of the compiler's analysis phase), what it replaced, why it was a multi-year effort, and what it buys you: faster compilation, more precise smart casts, unified analysis for IDE and compiler, and a stable plugin API that the Compose compiler (now versioned with Kotlin) depends on.
- **Top-level declarations.** Functions and properties that live directly in a file with no enclosing class — what they compile to (a `FileNameKt` class with `static` members), why they replace Java's `Utils` classes, and the package/file relationship.
- **Expressions over statements.** `if`, `when`, `try`, and `return`-as-expression. Single-expression function bodies (`fun f() = ...`). Why "everything is an expression" changes how you structure code.
- **Type inference.** Where Kotlin infers (`val x = 5`), where it cannot (recursive functions, public API by convention), how inference flows through generics, and the discipline of explicit return types on a library's public surface.
- **`val` vs `var` and immutability defaults.** Why `val` is the default reach, what `val` does and does not guarantee (the binding is immutable; the referenced object may not be), and the difference between a `val` and a `const val`.
- **Pragmatic equality.** `==` (structural, lowers to `a?.equals(b) ?: (b === null)`) vs `===` (referential identity). The `Integer` cache footgun (`==` on boxed `Int` in the −128..127 range vs outside it). Why `==` on `String` does the right thing in Kotlin where `==` on `String` is a bug in Java.
- **Smart casts.** How the compiler narrows a type after `is` / `!is` / `!= null`, the data-flow analysis behind it, and the precise conditions under which a smart cast is *not* allowed (a `var` that could change between check and use, a property with a custom getter, an `open` `val` across a module boundary).
- **Reading bytecode.** Compiling a `.kt` file, locating the `.class` output, and running `javap -c` to read the bytecode. Mapping a top-level function to a `static` method, a default argument to a synthetic `$default` method, and a `data class` to its generated `equals`/`hashCode`/`toString`/`componentN`/`copy`.
- **Gradle Kotlin DSL from day one.** `settings.gradle.kts`, `build.gradle.kts`, the `kotlin("jvm")` plugin, the `application` plugin, a `libs.versions.toml` version catalog, dependency declarations, and a fat-JAR task. Why the Kotlin DSL (over Groovy) gives you autocompletion and type-checking on your build script.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                            | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|-----------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | Reading group; K2 and Kotlin 2.x; top-level fns, expressions    |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | `val`/`var`, immutability, inference; equality `==` vs `===`     |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Smart casts; reading bytecode with `javap`; the challenge       |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | Gradle Kotlin DSL, version catalogs, fat JAR; challenge wrap     |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — `kt-stat` scaffold, file walk, line counting     |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work; colorized report; fat-JAR packaging      |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                       |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                 | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **35.5h**   |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./README.md) | This overview (you are here) |
| [resources.md](./resources.md) | The Kotlin language docs, the K2 blog posts, the KEEP proposals, the Gradle Kotlin DSL guide, and the canonical "idiomatic Kotlin" references |
| [lecture-notes/01-kotlin-2x-and-the-k2-compiler.md](./lecture-notes/01-kotlin-2x-and-the-k2-compiler.md) | Kotlin 2.x end to end: what K2 is, top-level functions and what they compile to, expressions over statements, type inference, `val`/`var`, and reading bytecode with `javap` |
| [lecture-notes/02-equality-smart-casts-and-idiomatic-kotlin.md](./lecture-notes/02-equality-smart-casts-and-idiomatic-kotlin.md) | Structural vs referential equality and the boxing footgun, smart casts and when they fail, and the idioms that separate Kotlin from translated Java — measured against bytecode |
| [exercises/README.md](./exercises/README.md) | Index of the three exercises |
| [exercises/exercise-01-read-the-bytecode.md](./exercises/exercise-01-read-the-bytecode.md) | Compile trivial Kotlin, run `javap -c`, and map four constructs to the bytecode they generate |
| [exercises/exercise-02-equality-and-smart-casts.kt](./exercises/exercise-02-equality-and-smart-casts.kt) | A test suite that pins down `==` vs `===`, the `Integer` cache, and the exact boundaries of smart casts |
| [exercises/exercise-03-expressions-over-statements.kt](./exercises/exercise-03-expressions-over-statements.kt) | Refactor statement-style "Java-in-Kotlin" into expression-style idiomatic Kotlin, with tests proving behaviour is unchanged |
| [challenges/README.md](./challenges/README.md) | Index of the challenge |
| [challenges/challenge-01-javaism-to-idiom.md](./challenges/challenge-01-javaism-to-idiom.md) | Take a deliberately Java-flavoured Kotlin file, rewrite it idiomatically, and justify each change by reading the before/after bytecode |
| [quiz.md](./quiz.md) | 13 questions on K2, expressions, inference, equality, smart casts, bytecode, and Gradle |
| [homework.md](./homework.md) | Six practice problems for the week |
| [mini-project/README.md](./mini-project/README.md) | Full spec for `kt-stat`: a Gradle Kotlin DSL CLI that walks a directory, counts source lines per language, and prints a colorized report as a fat JAR |

## The "reads like Kotlin" promise

Every later week of this track assumes you can write idiomatic Kotlin without thinking about it. The bar a senior reviewer holds, starting this week:

> **Your Kotlin should not be translatable back to Java line-for-line.** If a reviewer can mechanically map every line of your Kotlin to the Java statement it came from — a `Utils` class of static methods, an `if/else` assigning to a pre-declared `var`, a manual `equals`/`hashCode`, a null check followed by a cast — you wrote Java in Kotlin syntax. Idiomatic Kotlin reaches for expressions, immutability, top-level and extension functions, data classes, and smart casts, and the bytecode proves they cost nothing.

You will *prove* the "costs nothing" half by reading bytecode this week: a `data class` you wrote in five lines generates the same `equals`/`hashCode`/`toString` you would have hand-written in fifty, and `javap` shows it. Idiomatic is not slower. It is the same machine code with less of your time spent typing it.

## A note on what's not here

Week 1 is the *language foundations* week. It deliberately does **not** cover:

- **Coroutines and `suspend`.** Structured concurrency is Week 4. We will write zero asynchronous code this week; `kt-stat` is synchronous on purpose.
- **The Android runtime, ART, or the Activity lifecycle.** That is Week 6. This week is pure JVM so that bytecode reading is clean and the Android toolchain's extra layers (R8, dexing, the manifest) don't muddy the picture.
- **Jetpack Compose.** Phase 2. The Compose compiler plugin rides on K2, which is why we introduce K2 now — but you write no `@Composable` this week.
- **Null safety in depth, sealed types, generics.** Weeks 2 and 3. We mention `?` and `is` where equality and smart casts require them, but the full algebraic-types and generics treatment is next week and the week after.

The point of Week 1 is narrow and deep: the language as a JVM citizen, the idioms that make it Kotlin and not Java, the bytecode that proves the idioms are free, and the build tool that compiles all of it.

## Up next

Continue to **Week 02 — Null safety, sealed types, and the algebraic core** once you have shipped `kt-stat` and can read your own bytecode. Week 2 takes the type system you met this week and turns it into a modelling tool: nullable types and the `?` operator family, sealed classes and interfaces, data classes and component functions, inline value classes, and the exhaustive `when` the compiler enforces. Everything in Week 2 assumes you are fluent in expressions, `val`, and smart casts — because a sealed `when` *is* an expression, and an exhaustive `when` over a sealed type is smart-casting in every branch. Earn that fluency this week.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
