# Week 02 — Resources

Every primary resource on this page is **free**. The Kotlin documentation is free and open. The KEEP proposals are public on GitHub. The JSON grammar is a one-page free standard. A couple of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"Null safety."** The canonical Kotlin null-safety page — `?`, `?:`, `!!`, `?.let`, and the rationale. Read it before Monday's exercise:
  <https://kotlinlang.org/docs/null-safety.html>
- **"Sealed classes and interfaces."** The official sealed-type page — closed hierarchies, exhaustive `when`, the module/package rules:
  <https://kotlinlang.org/docs/sealed-classes.html>
- **"Data classes."** `componentN`, `copy`, the generated members, and `data class` vs `class`:
  <https://kotlinlang.org/docs/data-classes.html>
- **"Inline value classes."** `@JvmInline value class`, erasure, when boxing happens:
  <https://kotlinlang.org/docs/inline-classes.html>
- **"Java interop: null-safety and platform types."** The exact behaviour of `String!` and the annotations that fix it:
  <https://kotlinlang.org/docs/java-interop.html#null-safety-and-platform-types>

## The reference pages you'll open mid-task

- **"Control flow" — the `when` expression** (exhaustiveness, ranges, type checks): <https://kotlinlang.org/docs/control-flow.html#when-expressions-and-statements>
- **"Enum classes"** (abstract members, per-constant overrides, properties): <https://kotlinlang.org/docs/enum-classes.html>
- **`Result<T>` and `runCatching`** (the stdlib outcome type): <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-result/>
- **Scope functions** (`let`/`run`/`also`/`apply`/`with` — the null-handling workhorses): <https://kotlinlang.org/docs/scope-functions.html>
- **"Type checks and casts"** (`as?`, smart casts in `when` branches — Week 1, reused hard here): <https://kotlinlang.org/docs/typecasts.html>

## KEEP — the design rationale

- **"Inline classes" KEEP** — why value classes exist, the erasure model, the boxing rules:
  <https://github.com/Kotlin/KEEP/blob/master/proposals/inline-classes.md>
- **"Sealed interfaces and freedom for sealed classes" KEEP** — why `sealed interface` was added and the same-module rule loosened to same-package-and-module:
  <https://github.com/Kotlin/KEEP/blob/master/proposals/sealed-interface-freedom.md>
- **The KEEP repository root** (browse the `proposals/` folder for any feature you want the *why* of):
  <https://github.com/Kotlin/KEEP>

## "Make illegal states unrepresentable" — the canon

This week's thesis has a literature. None of it is Kotlin-specific, which is the point — it's a cross-language idea Kotlin happens to support well.

- **"Designing with types: Making illegal states unrepresentable" — Scott Wlaschin** (F#, but the idea is universal; the single best essay on the topic):
  <https://fsharpforfunandprofit.com/posts/designing-with-types-making-illegal-states-unrepresentable/>
- **"Parse, don't validate" — Alexis King.** The argument for pushing validation into the type at the boundary, so downstream code can't see invalid data. Maps directly onto this week's parser:
  <https://lexi-lambda.github.io/blog/2019/11/05/parse-don-t-validate/>
- **"Algebraic Data Types"** — any solid intro; the product (`data class`) vs sum (`sealed`) framing is the mental model. Wikipedia's "Algebraic data type" page is a fine, free reference.

## JSON (for the mini-project)

- **The JSON grammar (ECMA-404 / RFC 8259).** One page. Read the railroad diagrams; they *are* your parser's structure:
  <https://www.json.org/json-en.html>
- **RFC 8259** (the IETF JSON spec, the normative one for edge cases like number formats and string escapes):
  <https://www.rfc-editor.org/rfc/rfc8259>
- **kotlinx.serialization** — you will NOT use it this week (the point is to hand-roll the parser), but read how *its* `JsonElement` sealed hierarchy is shaped; it's the production version of what you build:
  <https://github.com/Kotlin/kotlinx.serialization>

## Community writing (current, opinionated, correct)

- **Jake Wharton — "Public API challenges in Kotlin"** and the inline-class posts; the bytecode-level take on value classes and nullability is exactly this week's altitude:
  <https://jakewharton.com/blog/>
- **Roman Elizarov — language-design talks.** The sealed-types and null-safety design decisions explained by a designer.
- **Kotlin Slack** (`kotlinlang.slack.com`) — `#language-evolution` and `#getting-started` for the edge-case questions (when does a value class box? why won't this `when` compile exhaustively?).
- **Kotlin YouTube — KotlinConf talks on domain modelling and type-driven design.**

## Open-source projects to read this week

You learn modelling from reading models. Pick one and study how they use sealed hierarchies and value classes:

- **`Kotlin/kotlinx.serialization`** — the `JsonElement` sealed hierarchy (`JsonObject`, `JsonArray`, `JsonPrimitive`, `JsonNull`) is the production shape of the mini-project's `JsonNode`. Read `JsonElement.kt`:
  <https://github.com/Kotlin/kotlinx.serialization/tree/master/formats/json>
- **`arrow-kt/arrow`** — the functional-Kotlin library; its `Either`, `Option`, and `Validated` are the algebraic-types toolkit taken to its conclusion. You won't use Arrow on the job day one, but reading `Either` clarifies the sum-type idea:
  <https://github.com/arrow-kt/arrow>
- **Any Now-in-Android `Result` type** — the `Result` sealed class in Google's reference app (`core/data`) is the idiomatic Android `Loading`/`Success`/`Error` you'll write for real in Phase 2:
  <https://github.com/android/nowinandroid>

## Tools you'll use this week

- **The same Gradle Kotlin DSL toolchain from Week 1** — JDK 21, Kotlin 2.2.x, JUnit 5. No new tools.
- **The IDE's "Show Kotlin Bytecode ▸ Decompile"** — essential for the inline-value-class section; it's the fastest way to *see* that `value class UserId(val raw: Long)` erases to `long` in a function signature but boxes when nullable.
- **`javap -c -p`** — the command-line confirmation of the same erasure, carried over from Week 1.

## Free books and long-form

- **"Kotlin in Action, 2nd edition" — free sample chapters.** The chapters on null safety and on classes/objects cover this week at the right depth; Manning posts the early ones free.
- **The Kotlin docs "Concepts" section** read end to end is, again, effectively a free book — the null-safety, sealed-class, data-class, and inline-class pages are this week's reading.

## Paid books (optional, clearly marked)

- **"Kotlin in Action, 2nd edition"** (paid). The definitive book; the null-safety and type-modelling chapters are the clearest in print.
- **"Effective Kotlin" — Marcin Moskała** (paid). The items on "specify your API stability," "use sealed classes to represent restricted hierarchies," and "consider value classes for primitives" are this week's lessons as rules.
- **"Domain Modeling Made Functional" — Scott Wlaschin** (paid, F#). Not Kotlin, but the best book-length treatment of "make illegal states unrepresentable"; everything transfers.

---

*If a link 404s, please open an issue so we can replace it.*
