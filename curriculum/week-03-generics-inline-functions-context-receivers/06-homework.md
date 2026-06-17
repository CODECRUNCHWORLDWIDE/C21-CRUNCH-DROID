# Week 03 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 03 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets JDK 17+, Kotlin 2.0+ with the K2 compiler, pure JVM (no Android). Context-receiver problems need `freeCompilerArgs += "-Xcontext-receivers"`. Every problem must build with **0 warnings** (a single guarded, justified `UNCHECKED_CAST` is the only exception, where noted).

---

## Problem 1 — Prove erasure with `javap`

**Problem statement.** Write a generic function `fun <T> wrap(value: T): List<T> = listOf(value)` and a `@JvmStatic`-free file with two overloads that *would* clash after erasure (`fun describe(x: List<String>)` and `fun describe(x: List<Int>)`). Compile, observe the clash error, fix it with `@JvmName`, then run `javap -c` and record the erased JVM signatures into `notes/erasure.md`.

**Acceptance criteria.**

- `notes/erasure.md` shows the platform-clash error message (quoted) and the `@JvmName` fix.
- The disassembled signatures of both `describe` functions are pasted, showing they are `(List)` after erasure.
- Committed.

**Hint.** The clash error is "Platform declaration clash: The following declarations have the same JVM signature." `@JvmName("describeInts")` on one overload disambiguates the bytecode name. `javap -c -p YourFileKt`.

**Estimated time.** 30 minutes.

---

## Problem 2 — Annotate variance until it compiles

**Problem statement.** You're given three invariant interfaces — a `Source<T>` (produces only), a `Sink<T>` (consumes only), and a `Pipe<T>` (both) — and a `main()` with assignments, some of which should compile and some of which should stay errors. Add `in`/`out` so exactly the right ones compile, then write a one-sentence out-loud justification per interface into `notes/variance.md`.

**Acceptance criteria.**

- `Source` is `out`, `Sink` is `in`, `Pipe` is invariant.
- The "should compile" assignments compile; the "should stay errors" ones still fail.
- Three justifications in your own words in `notes/variance.md`.
- 0 warnings. Committed.

**Hint.** This is exercise 1 with different names. `out` for producers (covariant), `in` for consumers (contravariant), invariant for both. If `Source` rejects `out`, you left a `T` in a parameter position somewhere.

**Estimated time.** 40 minutes.

---

## Problem 3 — A reified JSON-ish decoder

**Problem statement.** Write `inline fun <reified T> decode(raw: Any?): T?` that returns `raw` cast to `T` if `raw is T`, else `null` — with no `Class` parameter and no caller cast. Then write `decodeList<T>(raws: List<Any?>): List<T>` that keeps only the elements that are `T`. Test with `String`, `Int`, and a nullable `String?` to show nullability flows through.

**Acceptance criteria.**

- `decode<String>("hi")` returns `"hi"`; `decode<Int>("hi")` returns `null`; `decode<String?>(null)` returns `null`-as-`String?` (not an error).
- `decodeList<String>(listOf("a", 1, "b"))` returns `["a", "b"]`.
- A note in `notes/reified-decode.md` explains why neither function needs a `Class` argument.
- 0 warnings (the `is T` smart-cast means you need no explicit cast). Committed.

**Hint.** `return if (raw is T) raw else null` — `raw is T` smart-casts `raw` to `T` in the branch. `reified` makes `is T` a real, checkable test at each call site.

**Estimated time.** 40 minutes.

---

## Problem 4 — `crossinline` and the non-local-return rule

**Problem statement.** Write `inline fun retry(times: Int, block: () -> Unit)` that runs `block` up to `times`, allowing a non-local `return` from `block` to exit the caller (demonstrate it). Then write `inline fun onEach(items: List<Int>, crossinline action: (Int) -> Unit)` that wraps `action` in a `Runnable` and runs each on the runnable — and show that a non-local `return` from `action` is a **compile error**, with a comment explaining why `crossinline` forbids it here.

**Acceptance criteria.**

- `retry`'s `block` can non-local-return and exit the enclosing function (a small demo proves it).
- `onEach`'s `action` is `crossinline`; attempting a non-local return in it fails to compile (kept commented with the error quoted).
- `notes/crossinline.md` explains the difference in one paragraph.
- 0 warnings. Committed.

**Hint.** In `retry`, the lambda is invoked directly, so a non-local return is fine. In `onEach`, the lambda is invoked from *inside a `Runnable`* — a different execution context — so returning from the enclosing function would be unsound; `crossinline` is what makes the compiler reject it.

**Estimated time.** 45 minutes.

---

## Problem 5 — `fun interface` vs a function type

**Problem statement.** Define the same callback two ways: a plain function type `typealias Transform = (String) -> String` and a `fun interface Transformer { fun transform(s: String): String }`. Write an API that takes each, call both with a lambda, and write into `notes/sam.md`: when does the `fun interface` read better, and what's the one thing you *can't* do passing a function-typed *value* (not literal) where a `fun interface` is expected?

**Acceptance criteria.**

- Both forms defined and called with a lambda literal.
- `notes/sam.md` answers both questions: (a) `fun interface` wins when you want a named type or Java interop or extra members; (b) a function-typed *value* doesn't auto-convert to a `fun interface` — you must wrap it `Transformer(theValue)`.
- 0 warnings. Committed.

**Hint.** A lambda *literal* SAM-converts to a `fun interface`; a `(String) -> String` *variable* does not — `apiTakingTransformer(myFunctionValue)` fails, `apiTakingTransformer(Transformer(myFunctionValue))` works. Lecture 2, §5.

**Estimated time.** 40 minutes.

---

## Problem 6 — Refactor parameter-threading to context receivers

**Problem statement.** Take a small "service" with three functions that each take a `Logger` and a `Db` parameter and pass them down to each other. Refactor all three to `context(Logger, Db)` so the capabilities propagate implicitly, call the top one inside `with(logger) { with(db) { ... } }`, and demonstrate (then re-comment) that calling it outside the context is a compile error. Note in `notes/context.md` the lines of parameter noise you removed.

**Acceptance criteria.**

- Three functions converted to `context(Logger, Db)`; none take `logger`/`db` parameters anymore.
- The top function is called successfully under nested `with`, and the out-of-context call is a documented compile error.
- `notes/context.md` records the before/after signatures and the noise removed.
- 0 warnings. Committed. (Needs `-Xcontext-receivers`.)

**Hint.** This is exercise 3 extended to three functions. The win: a function that calls another `context(Logger, Db)` function doesn't re-pass the capabilities — they propagate. Pin to a Kotlin version where `-Xcontext-receivers` works; if your version ships context *parameters*, adapt the syntax and note the difference.

**Estimated time.** 45 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, code is idiomatic Kotlin, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. an explicit cast left where a smart-cast was the point, a `Class` parameter left where `reified` was the point). |
| 3 | Works, but misses one criterion (e.g. variance compiles but the out-loud justification is missing or wrong; `crossinline` used but the reason not explained). |
| 2 | Compiles and partially works; a core idea is wrong (claims `reified` works without `inline`; marks a producing-and-consuming type covariant). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for an unchecked cast you cannot justify (the whole week is about *not* leaking casts); **−2** for claiming a `var`-bearing or both-producing-and-consuming type can be variant; **−1** for a `Class<T>` parameter on an API where `reified` was the intended design.

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — reified substitution (problems 1, 3, 4) and variance/context capabilities (problems 2, 6) — so re-run exercises 02 and 03 before resubmitting.
