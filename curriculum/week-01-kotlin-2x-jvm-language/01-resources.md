# Week 01 — Resources

Every primary resource on this page is **free**. The Kotlin language documentation is free and open. The KEEP proposals and the compiler source are public on GitHub. The Gradle documentation is free. A couple of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **Kotlin language documentation — "Get started" and "Basics."** The official tour; read the "Basic syntax," "Idioms," and "Coding conventions" pages end to end before Wednesday:
  <https://kotlinlang.org/docs/home.html>
- **"Kotlin coding conventions."** The official style guide — the source of truth for "idiomatic." Read it once this week and re-read it whenever a reviewer flags style:
  <https://kotlinlang.org/docs/coding-conventions.html>
- **"Idioms."** A dense page of the canonical Kotlin idioms — expressions, `when`, destructuring, scope functions. This is your "stop writing Java" checklist:
  <https://kotlinlang.org/docs/idioms.html>
- **"Equality."** The official explanation of structural (`==`) vs referential (`===`) equality — short, load-bearing for lecture 2:
  <https://kotlinlang.org/docs/equality.html>
- **"Type checks and casts."** Smart casts, `is`/`!is`, `as`/`as?`, and the exact conditions under which a smart cast applies:
  <https://kotlinlang.org/docs/typecasts.html>

## Kotlin 2.x and the K2 compiler

The frontend rewrite is the headline of Kotlin 2.0. You will not write K2 code, but you should be able to explain what it is.

- **"Kotlin 2.0 Released" (JetBrains blog).** The announcement of the K2 compiler as default; the performance numbers and the rationale:
  <https://blog.jetbrains.com/kotlin/2024/05/kotlin-2-0-0-released/>
- **"K2 Compiler Performance Benchmarks and How to Measure Them on Your Projects":**
  <https://blog.jetbrains.com/kotlin/2024/04/k2-compiler-performance-benchmarks-and-how-to-measure-them-on-your-projects/>
- **Kotlin language releases and "What's new" pages** (2.0, 2.1, 2.2) — the per-release feature list, including the smart-cast improvements and the context-parameters preview:
  <https://kotlinlang.org/docs/whatsnew20.html> · <https://kotlinlang.org/docs/whatsnew21.html> · <https://kotlinlang.org/docs/whatsnew22.html>
- **The Kotlin compiler source** (read, don't memorize) — the `compiler/` tree, where the K2 frontend (`fir`, the "Frontend Intermediate Representation") lives:
  <https://github.com/JetBrains/kotlin>

## The reference pages you'll actually open mid-task

- **Functions** (top-level, default arguments, single-expression bodies): <https://kotlinlang.org/docs/functions.html>
- **Properties** (`val`/`var`, `const`, custom getters, backing fields): <https://kotlinlang.org/docs/properties.html>
- **Control flow** (`if`/`when`/`for` as expressions): <https://kotlinlang.org/docs/control-flow.html>
- **Basic types** (`Int`, boxing, the `Integer` cache reality): <https://kotlinlang.org/docs/basic-types.html>
- **Packages and imports** (the file-to-class mapping for top-level declarations): <https://kotlinlang.org/docs/packages.html>
- **Scope functions** (`let`/`run`/`with`/`apply`/`also` — previewed here, hammered in Week 2): <https://kotlinlang.org/docs/scope-functions.html>

## Gradle Kotlin DSL

You stand up your first real build this week and use it every week after.

- **"Gradle Kotlin DSL Primer."** The official guide to writing `build.gradle.kts` — why it's type-checked, how it differs from Groovy:
  <https://docs.gradle.org/current/userguide/kotlin_dsl.html>
- **"Building Kotlin JVM applications."** The `kotlin("jvm")` and `application` plugins, the entry point, running with `./gradlew run`:
  <https://docs.gradle.org/current/userguide/building_kotlin_projects.html>
- **"Sharing dependency versions between projects" / version catalogs.** The `libs.versions.toml` format you'll use in every module from Week 6 on:
  <https://docs.gradle.org/current/userguide/version_catalogs.html>
- **Kotlin Gradle plugin docs** (the `kotlin("jvm")` plugin, JVM target, toolchains):
  <https://kotlinlang.org/docs/gradle.html>

## Reading bytecode (the confidence skill)

- **`javap`** ships with your JDK. `javap -c -p ClassNameKt.class` disassembles the bytecode. No install needed; it's in `$JAVA_HOME/bin`.
- **IntelliJ / Android Studio: "Kotlin Bytecode" tool window.** Menu: **Tools ▸ Kotlin ▸ Show Kotlin Bytecode**, then **Decompile** to see the equivalent Java. The fastest way to answer "what did `@Model`… er, `data class` compile to?" without leaving the IDE.
- **The JVM Specification, Chapter 6 — "The Java Virtual Machine Instruction Set."** Reference, not cover-to-cover; look up an opcode (`invokestatic`, `getstatic`, `if_acmpeq`) when `javap` shows you one you don't know:
  <https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-6.html>

## KEEP — where the language is designed in the open

KEEP (Kotlin Evolution and Enhancement Process) is the public design repo. Reading a KEEP is the antidote to cargo-culting a feature — you see *why* it exists.

- **The KEEP repository:** <https://github.com/Kotlin/KEEP>
- **"Context parameters" (formerly context receivers)** — the proposal behind a Week 3 topic; skim it now to know the lineage:
  <https://github.com/Kotlin/KEEP/blob/master/proposals/context-parameters.md>
- **"Data classes" and "Inline classes" KEEPs** — the design rationale for two features you'll use in Week 2.

## Community writing (current, opinionated, correct)

- **Kotlin Slack** (`kotlinlang.slack.com`, free invite from kotlinlang.org) — the `#language-evolution`, `#getting-started`, and `#gradle` channels are where JetBrains engineers answer questions directly.
- **Roman Elizarov's talks and writing** — the structured-concurrency and language-design perspective from one of Kotlin's lead designers; the "Kotlin Coroutines" content is Week 4, but his language talks are gold this week.
- **Jake Wharton's blog** — deep, bytecode-level Kotlin/Android writing; the posts on `inline`, `R8`, and "Kotlin's hidden costs" are exactly the altitude this week aims for:
  <https://jakewharton.com/blog/>
- **Kotlin YouTube channel** — the "Kotlin in Depth" and KotlinConf talks; watch a K2 deep-dive talk if you want the compiler internals beyond the blog posts:
  <https://www.youtube.com/@Kotlin>

## Open-source projects to read this week

You learn more from one hour reading a real, well-written Kotlin codebase than from three hours of syntax tours. Pick one and read how they structure top-level functions, extensions, and their Gradle build:

- **`Kotlin/kotlinx.coroutines`** — the standard for idiomatic Kotlin library code; even before you know coroutines, the *style* (expression bodies, extension functions, `internal` visibility discipline) is exemplary:
  <https://github.com/Kotlin/kotlinx.coroutines>
- **`square/okio`** — small, focused, beautifully idiomatic Kotlin; a great first real codebase to read end to end:
  <https://github.com/square/okio>
- **`gradle/gradle`** sample Kotlin-DSL builds, and the `build.gradle.kts` of any of the above — read a real version catalog and a real fat-JAR/shadow setup.

## Tools you'll use this week

- **A JDK 21** — `java -version` to confirm. Temurin from <https://adoptium.net/> is the no-friction free choice on every OS.
- **`javap`** — already in your JDK's `bin`. `javap -c -p` is the invocation you'll memorize.
- **Gradle wrapper** — you never install Gradle globally; the `./gradlew` script in the starter downloads the right version. `./gradlew --version` confirms.
- **IntelliJ IDEA Community Edition** (free) or **Android Studio** — either runs Kotlin/JVM. The "Show Kotlin Bytecode" window is in both.

## Free books and long-form

- **"Kotlin in Action, 2nd edition" — sample chapters.** Manning posts the first chapters free; chapters 1–4 cover exactly this week's material at the right depth. (The full book is paid; the free chapters are enough for Week 1.)
- **The official Kotlin docs are, effectively, a free book** — the "Concepts" section read end to end is a complete language reference.

## Paid books (optional, clearly marked)

- **"Kotlin in Action, 2nd edition" — Sebastian Aigner, Roman Elizarov, Svetlana Isakova, Dmitry Jemerov** (paid). The definitive Kotlin book, updated for Kotlin 2.x and K2; the bytecode and idiom chapters are the clearest in print.
- **"Effective Kotlin" — Marcin Moskała** (paid). The "Effective"-series treatment for Kotlin; the items on `val`-first, expression bodies, and equality are precisely this week's lessons distilled into rules.

---

*If a link 404s, please open an issue so we can replace it.*
