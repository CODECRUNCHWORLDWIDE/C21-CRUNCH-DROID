# Week 03 — Resources

Every primary resource on this page is **free**. The Kotlin documentation and language specification are free and open. The KEEP proposals are public on GitHub. The conference talks are free on YouTube. A couple of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"Generics: in, out, where."** The canonical Kotlin generics page — declaration-site variance, type projections, star-projections, the `where` clause. Read it twice; the variance section is the one most people skim and then fumble in interviews:
  <https://kotlinlang.org/docs/generics.html>
- **"Inline functions."** The official page on `inline`, `reified`, `noinline`, `crossinline`, and inline properties. Short, dense, and load-bearing for the whole week:
  <https://kotlinlang.org/docs/inline-functions.html>
- **"Higher-order functions and lambdas."** Function types, lambda syntax, receiver function types (`A.() -> B`), and the cost model of a lambda before inlining:
  <https://kotlinlang.org/docs/lambdas.html>
- **"SAM conversions" and "Functional (SAM) interfaces."** When a lambda becomes a Java single-abstract-method instance, and when to define a Kotlin `fun interface`:
  <https://kotlinlang.org/docs/fun-interfaces.html>
- **The context receivers / context parameters KEEP proposal.** The actual design document for the Kotlin 2.x feature — read at least the motivation and the "why not just parameters" sections:
  <https://github.com/Kotlin/KEEP/blob/master/proposals/context-receivers.md>

## Erasure, reification, and the bytecode

You will not be able to *feel* erasure until you read the bytecode that proves it. Keep `javap` in your hand all week.

- **`javap -c -p` on a generic function** — disassemble a compiled `inline fun <reified T>` call site and a plain generic function and compare. The reified one has the concrete type baked in; the plain one does not. This is the exercise-2 proof.
- **The JVM generics / type-erasure background** (Java tutorial, applies verbatim to Kotlin since both target the same bytecode):
  <https://docs.oracle.com/javase/tutorial/java/generics/erasure.html>
- **"Reified type parameters"** section of the inline-functions page above — the single most important paragraph this week. Re-read it after you have seen the `javap` output; it will read differently.

## Context receivers — current, honest, evolving

Context receivers are an **experimental** Kotlin 2.x feature behind `-Xcontext-receivers`, and the design is actively evolving toward *context parameters*. Read with that in mind — the concept is stable, the exact syntax may shift.

- **The KEEP proposal** (linked above) is the primary source. The "Detailed design" section is the spec.
- **The Kotlin blog and "What's new in Kotlin 2.x"** release notes — search for the context-receivers/context-parameters status in the current release. Treat the most recent release note as authoritative over any blog post:
  <https://kotlinlang.org/docs/whatsnew-eap.html>
- **Jake Wharton and Kotlin team writing on context receivers** — the independent commentary on when context receivers beat parameters and where they bite. Search for the current-year talks; the API surface moved between previews, so prefer recent material.

## Talks (free, watch in this order)

- **"Exploring Kotlin's inline classes and inline functions"** / any recent KotlinConf inline-functions deep dive — the inlining transform shown at the bytecode level.
- **"The economics of generics"** style talks (variance explained with real Kotlin stdlib examples) — search the KotlinConf playlist for the current variance/generics session.
- **"Context receivers" KotlinConf session** — the Kotlin team's own walkthrough of the feature, its motivation, and the migration path to context parameters. The most current one supersedes older ones.

## Read it at the source — the Kotlin standard library

The stdlib is the best inline/reified/variance textbook you have, and it is open source. Read these:

- **`Standard.kt`** — `let`, `run`, `with`, `apply`, `also`, `takeIf`, `repeat`. Every one is `inline`; read why:
  <https://github.com/JetBrains/kotlin/blob/master/libraries/stdlib/src/kotlin/util/Standard.kt>
- **`Collections.kt` / `_Collections.kt`** — `map`, `filter`, `filterIsInstance` (a reified inline function!), `fold`. The reified `filterIsInstance<T>()` is exactly the shape exercise 2 asks you to build:
  <https://github.com/JetBrains/kotlin/tree/master/libraries/stdlib/src/kotlin/collections>
- **`TypeIntrinsics` and the reified-call sites** — how `reified` lowers. Deep, optional, but it makes erasure concrete.

## Community writing (current, opinionated, correct)

- **The Kotlin team's "Kotlin Foundation" blog and the official Kotlin blog** — the authoritative source for language-feature status, especially the context-receivers evolution:
  <https://blog.jetbrains.com/kotlin/>
- **Roman Elizarov's writing** (Kotlin libraries lead) — the deepest independent reasoning about the language's design choices, including why inlining and variance are shaped the way they are.
- **Jake Wharton's blog** — practical, bytecode-level Kotlin and Gradle posts; the `R8`/inlining interactions are gold for later weeks:
  <https://jakewharton.com/blog/>

## Open-source projects to read this week

You learn more from one hour reading a real library's generic API than three hours of tutorials. Pick one and read how it uses inline/reified and variance:

- **`kotlinx.serialization`** — reified `encodeToString<T>` / `decodeFromString<T>` and the variance on its serializer hierarchy. The cleanest production reified API you will read:
  <https://github.com/Kotlin/kotlinx.serialization>
- **`kotlinx.coroutines`** — `inline` and `crossinline` in the coroutine builders; you will return here in Week 04, but read `launch`/`async` signatures now:
  <https://github.com/Kotlin/kotlinx.coroutines>
- **`arrow-kt`** — the functional Kotlin library that pioneered ergonomic context-receiver and DSL patterns; read its `Raise` / context-based error handling for a real context-receiver design:
  <https://github.com/arrow-kt/arrow>

## Tools you'll use this week

- **JDK 17 (or 21)** with `javap` on your `PATH` — `javap -c -p -classpath app/build/classes/kotlin/main com.example.YourFileKt` disassembles your compiled code. This is how you *prove* inlining and reification, not just believe in them.
- **Kotlin 2.0+ / the K2 compiler** — confirm with `kotlinc -version` or your Gradle Kotlin plugin version. Context receivers need `-Xcontext-receivers` in `freeCompilerArgs`.
- **Gradle Kotlin DSL** — you add a second subproject and `maven-publish` to push `kt-bus` to `mavenLocal`. `./gradlew :kt-bus:publishToMavenLocal`.
- **`gradle.properties` / `build.gradle.kts`** — where the context-receivers compiler flag lives. The mini-project README shows the exact block.

## Free books (chapter-level, not whole books)

- **"Kotlin in Action, 2nd edition" (Manning) — the generics and higher-order-functions chapters.** The 2nd edition is current for Kotlin 1.9/2.0; the inline-functions and generics chapters are the best long-form treatment available. (Sample chapters are free on the Manning site; the full book is paid — marked below.)
- **The Kotlin language specification** — the formal spec sections on type parameters, variance, and inline functions. Dense, but the ground truth when a blog post and the docs disagree:
  <https://kotlinlang.org/spec/>

## Paid books (optional, clearly marked)

- **"Kotlin in Action, 2nd edition" — Isakova, Elizarov, Aigner, Jemerov (Manning)** (paid for the full book). The definitive book; the generics, inline, and DSL chapters map almost one-to-one onto this week.
- **"Effective Kotlin" — Marcin Moskała** (paid). Item-based best practices; the items on inline functions, reified, and generic variance are sharp and current.

---

*If a link 404s, please open an issue so we can replace it.*
