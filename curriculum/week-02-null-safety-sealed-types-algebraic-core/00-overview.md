# Week 02 — Null safety, sealed types, and the algebraic core

Welcome to Week 2 of **C21 · Crunch Droid**. Last week you met Kotlin as a JVM language — expressions, immutability, equality, smart casts, and the bytecode that proves the idioms are free. This week you turn that type system into a *modelling tool*. The thesis of the week is that **the best place to make illegal states unrepresentable is the type system**, and Kotlin gives you three instruments to do it: nullable types that force you to handle absence at compile time, sealed hierarchies that let the compiler enforce you handled every case, and inline value classes that give a domain type its own identity without paying for a wrapper object. Master these and a whole category of Android bugs — the `NullPointerException`, the unhandled state, the "I passed the wrong `String` to the wrong parameter" — stops being possible.

The mental shift this week is from "I handle errors and absence at runtime" to "I model errors and absence in the type, and the compiler handles them at compile time." A `User?` is a different type from a `User`, and the compiler will not let you call a method on the nullable one until you've dealt with the `null`. A `sealed interface NetworkResult` with `Success`, `Failure`, and `Loading` subtypes is a closed set the compiler knows completely, so a `when` over it can be *exhaustive* — and when you add a fourth case next month, every `when` that forgot to handle it becomes a compile error, not a production crash. A `data class` models a product type ("a `User` is an id *and* a name *and* an email"); a sealed type models a sum type ("a result is a success *or* a failure"); together they are **algebraic data types**, the foundation of correct domain modelling. And an inline value class (`@JvmInline value class UserId(val raw: Long)`) gives you a type-safe `UserId` that the compiler treats as distinct from a `PostId` but that erases to a plain `Long` at runtime — type safety with zero allocation.

The "still has `NullPointerException` somewhere" in the syllabus title is the honest part. Kotlin's null safety is a compile-time discipline, but Kotlin runs on a JVM full of Java that doesn't share it. Values crossing the Java boundary arrive as **platform types** (`String!`) the compiler can't check, and a `!!` operator exists precisely so you can assert non-null and crash if you're wrong. We teach where null safety holds (pure Kotlin), where it leaks (the Java interop boundary, platform types), and the discipline that keeps the leaks from reaching users. By Friday you will model outcomes three ways — nullable, sealed, and `Result<T>` — and be able to say out loud when each one earns its keep.

We close the week by building a **typed JSON parser**: a small recursive-descent parser that consumes a JSON dialect and returns a sealed `JsonNode` tree — `JsonObject`, `JsonArray`, `JsonString`, `JsonNumber`, `JsonBool`, `JsonNull` — with **no external libraries**. Parsing is the canonical place algebraic types shine: the result is a sum type (a node is *one of* six shapes), errors are modelled as a sealed `ParseResult` rather than thrown, and consuming the tree is an exhaustive `when` the compiler enforces. It is the same lesson the Swift track learns building a sealed parse tree, adapted to Kotlin's sealed interfaces and `when`.

## Learning objectives

By the end of this week, you will be able to:

- **Handle** nullability with the full `?` operator family — safe call (`?.`), Elvis (`?:`), safe cast (`as?`), and the `!!` assertion — and explain the exact runtime behaviour and bytecode of each.
- **Recognize** platform types (`String!`) at the Java interop boundary, understand why the compiler can't null-check them, and apply the discipline (annotations, explicit types, early validation) that contains the leak.
- **Model** a closed set of cases with `sealed class` / `sealed interface`, write an **exhaustive `when`** the compiler enforces (no `else`), and explain why adding a case turns every incomplete `when` into a compile error rather than a silent fallthrough.
- **Distinguish** product types (data classes — "a *and* b") from sum types (sealed hierarchies — "a *or* b"), and combine them into algebraic data types that make illegal states unrepresentable.
- **Use** data classes as a modelling tool: destructuring via `componentN`, non-destructive update via `copy`, and the equality/`hashCode` contract they generate.
- **Define** inline value classes (`@JvmInline value class`) for type-safe domain wrappers, explain when they erase to the underlying primitive and when they must box, and use them to stop "stringly-typed" and "longly-typed" bugs.
- **Choose** between nullable, sealed `Result`-style, and the stdlib `Result<T>` for modelling an outcome, and justify the choice for a given API.
- **Use** enum classes with abstract members and properties, and know the line where an enum stops being enough and a sealed hierarchy takes over.

## Prerequisites

This week assumes you have completed **C21 Week 1**, or have equivalent fluency. Specifically:

- You write idiomatic Kotlin — expressions over statements, `val`-first, single-expression bodies — and you can read the bytecode a construct compiles to (Week 1). The inline-value-class section is half a bytecode lesson; you need last week's `javap` habit.
- You understand **smart casts** (Week 1, lecture 2) cold. An exhaustive `when` over a sealed type smart-casts the subject in every branch; if smart casts are fuzzy, sealed `when` will feel like magic instead of mechanism.
- You know structural vs referential equality (Week 1) — data-class `equals` and inline-value-class identity both build on it.

No Android, no coroutines, no Compose this week — still pure JVM. The JSON parser runs on the plain JVM exactly like `kt-stat` did.

**Toolchain.** The same as Week 1: a **JDK 21**, **Kotlin 2.2.x** via the Gradle Kotlin DSL plugin, **JUnit 5** for tests, and either Android Studio or IntelliJ IDEA Community Edition. The `@JvmInline` value-class section benefits from the IDE's "Show Kotlin Bytecode ▸ Decompile" to see the erasure; have it open. No paid software, no device.

## Topics covered

- **Nullable types and the `?` family.** `T?` as a distinct type; safe call `?.`; the Elvis operator `?:` for defaults and early returns (`?: return`); safe cast `as?`; the `!!` not-null assertion and exactly when it throws; `?.let { }` for "do this if non-null." What each lowers to in bytecode (a null check, an `Intrinsics.checkNotNull`, etc.).
- **Platform types and the Java boundary.** Why a value from Java is `String!` (neither `String` nor `String?` from the compiler's view), why the compiler trusts you on it, the `NullPointerException` it can still produce, and the discipline: nullability annotations (`@Nullable`/`@NonNull`/JSpecify), explicit Kotlin types at the boundary, and validating-then-narrowing on entry.
- **Sealed classes and sealed interfaces.** A closed hierarchy known entirely at compile time; the difference between `sealed class` (single inheritance, can hold state) and `sealed interface` (multiple inheritance, often cleaner); why sealed types must be in the same module/package and what that buys the compiler.
- **Exhaustive `when`.** `when` over a sealed type (or enum) as an expression requires every case; no `else` needed; adding a subtype breaks every incomplete `when` at compile time. The single most valuable correctness property in the language for state modelling.
- **Data classes as product types.** `componentN` and destructuring (`val (id, name) = user`); `copy` for non-destructive update; the generated `equals`/`hashCode`/`toString`; `data class` vs plain `class` (identity vs value).
- **Algebraic data types.** Product types (data classes: "fields *and* fields") and sum types (sealed: "case *or* case) combined; modelling a domain so illegal states can't be constructed; the parser's `JsonNode` as the canonical example.
- **Inline value classes.** `@JvmInline value class Money(val cents: Long)`; type safety without an allocation; when they erase to the underlying type and when they box (nullable, generic, interface contexts); the "stringly-typed/longly-typed" bug class they prevent.
- **`Result<T>` and outcome modelling.** The stdlib `Result<T>` (`runCatching`, `getOrElse`, `map`/`fold`); a hand-rolled sealed `Result`/`Either` for richer error types; the three-way choice — nullable vs sealed vs `Result<T>` — and the criteria for each.
- **Enum classes with abstract members.** Enums with properties and per-constant overrides of abstract functions; when an enum (fixed, no payload) suffices and when a sealed hierarchy (cases carry different data) is required.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                              | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|-------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | Reading group; nullable types, the `?` family, platform types     |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | Sealed classes/interfaces; exhaustive `when`; data classes        |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Algebraic types; inline value classes; the challenge              |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | `Result<T>` vs sealed vs nullable; enums with members; challenge   |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — JSON lexer + sealed `JsonNode` tree                  |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project — recursive-descent parser, exhaustive consumer       |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                         |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                   | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **35.5h**   |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./00-overview.md) | This overview (you are here) |
| [resources.md](./01-resources.md) | The Kotlin null-safety, sealed-type, and inline-class docs, the relevant KEEPs, the "make illegal states unrepresentable" canon, and JSON-parsing references |
| [lecture-notes/01-null-safety-and-the-platform-type-boundary.md](./02-lecture-notes/01-null-safety-and-the-platform-type-boundary.md) | The `?` operator family end to end, what each lowers to, platform types and the Java boundary, and the discipline that keeps null safety from leaking |
| [lecture-notes/02-sealed-types-data-classes-and-algebraic-modelling.md](./02-lecture-notes/02-sealed-types-data-classes-and-algebraic-modelling.md) | Sealed classes/interfaces, exhaustive `when`, data classes as products, inline value classes, `Result<T>`, and how they compose into algebraic data types |
| [exercises/README.md](./03-exercises/00-overview.md) | Index of the three exercises |
| [exercises/exercise-01-null-handling-and-platform-types.md](./03-exercises/exercise-01-null-handling-and-platform-types.md) | Replace `!!` and crashes with the `?` family, handle a platform-type boundary safely, and read the bytecode of each operator |
| [exercises/exercise-02-sealed-exhaustive-when.kt](./03-exercises/exercise-02-sealed-exhaustive-when.kt) | Model a closed domain with a sealed hierarchy, write exhaustive `when`s, and prove that adding a case breaks the build until handled |
| [exercises/exercise-03-inline-value-classes-and-result.kt](./03-exercises/exercise-03-inline-value-classes-and-result.kt) | Stop a stringly-typed bug with inline value classes, see the erasure, and model an outcome three ways (nullable, sealed, `Result<T>`) |
| [challenges/README.md](./04-challenges/00-overview.md) | Index of the challenge |
| [challenges/challenge-01-model-the-illegal-away.md](./04-challenges/challenge-01-model-the-illegal-away.md) | Take a primitive-obsessed, nullable-everywhere domain model, redesign it with sealed types and inline classes so illegal states can't be constructed, and prove the bad states no longer compile |
| [quiz.md](./05-quiz.md) | 13 questions on nullability, platform types, sealed `when`, data classes, inline classes, and `Result` |
| [homework.md](./06-homework.md) | Six practice problems for the week |
| [mini-project/README.md](./07-mini-project/00-overview.md) | Full spec for the typed JSON parser: a sealed `JsonNode` tree, a recursive-descent parser, a sealed `ParseResult`, and an exhaustive consumer — no libraries |

## The "illegal states don't compile" promise

Week 1 gave you "idiomatic Kotlin that reads like Kotlin." Week 2 raises the bar a senior reviewer holds for your *domain models*:

> **An illegal state should fail to compile, not fail at runtime.** If your model lets someone construct a `Payment` that is both `pending` and has a `confirmationCode`, or a `User` whose `email` is a nullable `String?` that half your code forgets to check, you pushed the bug from compile time to production. Model the states as a sealed hierarchy where each case carries exactly the data it needs, wrap your IDs in inline value classes so they can't be swapped, and make the `null` either impossible or impossible-to-ignore. The compiler is your first code reviewer; give it enough types to do the job.

You will *prove* this in the challenge: you take a model where illegal states are constructible, redesign it, and demonstrate that the previously-buggy call sites now **fail to compile**. A bug the compiler catches is a bug no user ever sees.

## A note on what's not here

Week 2 is the *algebraic core* week. It deliberately does **not** cover:

- **Coroutines, `suspend`, async error handling.** Structured concurrency and its cancellation/exception model are Week 4. The `Result<T>` here is synchronous; the parser is synchronous.
- **Generics in depth, variance, reified types.** Week 3. We use `List<T>` and `Result<T>` as users, and we wave at variance where `JsonNode` subtyping touches it, but the rigorous treatment is next week.
- **Sealed types driving Compose UI state.** That payoff lands in Phase 2 (Week 12's `UiState`). This week builds the modelling skill; Compose collects the dividend later.
- **kotlinx.serialization.** The mini-project parses JSON *by hand*, on purpose, so the algebraic modelling is the lesson. The real serialization library shows up in Phase 3's networking week.

The point of Week 2 is narrow and deep: model absence with nullable types, model alternatives with sealed types, model values with data and inline classes, and let the compiler enforce that you handled every case.

## Up next

Continue to **Week 03 — Generics, inline functions, context receivers** once you have shipped the JSON parser and can write an exhaustive `when` without reaching for `else`. Week 3 takes the types you modelled this week and makes them *generic*: variance (why `List<Dog>` is a `List<Animal>` but `MutableList<Dog>` is not), reified type parameters (how `inline fun <reified T>` keeps the type at runtime), inline functions (and the bytecode you'll finally read in anger), and context receivers. The parser you wrote this week becomes a natural example — a generic `Parser<T>` and inline combinators — so the algebraic modelling here is the substrate for the abstraction there. Earn the exhaustive `when` this week; you'll generalize it next.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
