# Week 01 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 1 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets a JDK 21 toolchain, Kotlin 2.2.x, built with Gradle Kotlin DSL. Every problem must build with **0 warnings**.

---

## Problem 1 — Map three more constructs to bytecode

**Problem statement.** Extend exercise 1's `Demo.kt` with three new constructs and disassemble each: (a) an **extension function** `fun String.shout(): String = uppercase() + "!"`, (b) a **lambda** stored in a `val` (`val double: (Int) -> Int = { it * 2 }`), and (c) an **`object` declaration** (a singleton) `object Config { val name = "kt-stat" }`. Write your findings into `notes/bytecode-extra.md`.

**Acceptance criteria.**

- `notes/bytecode-extra.md` records, with quoted `javap` output: that `shout` is a `static` method taking a `String` receiver as its first parameter; that the lambda became a class implementing a `Function1` interface (or an `invokedynamic` with a synthetic method, depending on the SAM lowering); and that `Config` has a `static final INSTANCE` field (the singleton).
- The quoted bytecode is from your actual `javap`, not invented.
- Committed.

**Hint.** `javap -c -p ...Kt.class` for the extension and lambda; `javap -p Config.class` to see the `INSTANCE` field. The extension's signature reveals the receiver-as-first-arg trick — `shout(String)`, not an instance method on `String`.

**Estimated time.** 40 minutes.

---

## Problem 2 — Reproduce and document the Integer-cache split

**Problem statement.** Write a small `main` (or test) that loops over a list of integers spanning the cache boundary — say `listOf(126, 127, 128, 129, -128, -129)` — boxes each into two separate `Int?` values, and prints, for each, both `==` and `===`. Then write `notes/integer-cache.md` explaining the pattern you observed and stating the cache range.

**Acceptance criteria.**

- Output shows `===` is `true` for values in −128..127 and `false` outside it, while `==` is `true` for all of them.
- `notes/integer-cache.md` states the −128..127 cache range, names `Integer.valueOf`'s cache as the cause, and states the one-line rule ("compare values with `==`, never `===`").
- Committed.

**Hint.** You must use `Int?` (nullable) so the values are *boxed* to `Integer`; a primitive `Int` isn't an object and `===` is meaningless on it. Build the two values so the compiler can't fold them to the same constant — e.g. read one from a list and box the other separately.

**Estimated time.** 35 minutes.

---

## Problem 3 — The val-capture smart-cast fix, three ways

**Problem statement.** Write three functions that each take a parameter the compiler will *not* smart-cast directly, and fix each with a `val` capture: (a) a `var` property of an object, (b) a property with a custom getter, and (c) a function parameter of type `Any?` that you narrow after a null check. For each, include a comment showing the version that *doesn't* compile (commented out) and the `val`-capture version that does.

**Acceptance criteria.**

- Three functions, each with the blocked version commented out (with the exact compiler error text as a comment) and the working `val`-capture version below it.
- A test or `main` that calls all three and prints the expected results.
- 0 warnings. Committed.

**Hint.** Reuse the `Box` (custom getter) and `Holder` (mutable property) shapes from exercise 2, Part E. The compiler error you want to quote is "Smart cast to 'X' is impossible, because ... is a ... that has open or custom accessors" (or "is a mutable property ...").

**Estimated time.** 45 minutes.

---

## Problem 4 — Refactor a Javaism file end to end

**Problem statement.** Take any Java file you have lying around (or write a deliberately Java-style 40-line Kotlin file: a utility object, a manual loop, a `var`-in-`if` chain, a hand-written value class) and refactor it to idiomatic Kotlin. Keep a before/after diff. Write a one-line note per change naming the idiom applied.

**Acceptance criteria.**

- A `before/` and `after/` (or a Git diff) showing the refactor.
- A `notes/refactor-log.md` with one line per change: the Javaism → the idiom (expression body, `when`, data class, extension, `filter`/`map`, string template, scope function).
- Behaviour is unchanged — a quick test or `main` produces identical output before and after.
- 0 warnings. Committed.

**Hint.** This is the challenge's pattern at smaller scale. The fastest wins are: utility `object` → top-level/extension functions; manual loop → `filter`/`map`; `var`-in-`if` → `when`/`if` expression; hand-written value class → `data class`.

**Estimated time.** 50 minutes.

---

## Problem 5 — Add a `--top N` flag to `kt-stat`

**Problem statement.** Extend the mini-project so `kt-stat <dir> --top 3` shows only the top N languages by code lines (the totals row still reflects *all* languages, not just the shown ones). Parse the flag with a small hand-rolled argument loop — no library.

**Acceptance criteria.**

- `--top N` limits the displayed rows to N but the `TOTAL` row sums every language.
- Missing or invalid N (`--top abc`, `--top` with no value) prints a clear error to `stderr` and exits non-zero (or falls back to showing all, documented either way).
- A test on `buildReport` + a "take top N rows" helper proves the totals stay complete.
- 0 warnings. Committed.

**Hint.** Compute the full `Report` first (so `totals` is correct), then `report.rows.take(n)` for display only. Parse the flag with `args.indexOf("--top")` and read the next element, guarding the bounds. `toIntOrNull()` is your friend for the invalid-N case.

**Estimated time.** 45 minutes.

---

## Problem 6 — Prove a data class's equality is what tests rely on

**Problem statement.** Write a JUnit 5 test that puts `LanguageStats` (or any `data class` from the mini-project) instances into a `Set` and a `Map` key, and asserts that two structurally-equal instances collapse to one set element / address the same map entry. Then make a *non-data* copy of the class (no `equals`/`hashCode`), repeat, and assert the opposite (two "equal" instances are two distinct set elements). Document what this proves about `==`, `hashCode`, and why `data class` matters for collections.

**Acceptance criteria.**

- A passing test where two equal `data class` instances → set size 1, and addressing a `Map` with a fresh-but-equal key returns the stored value.
- A passing test where two equal *non-data* instances → set size 2 (identity-based), proving the difference.
- `notes/data-class-collections.md` explains: `==`/`hashCode` are generated for data classes, `HashSet`/`HashMap` rely on them, and a non-data class falls back to identity — which is the #1 cause of "my map key doesn't work."
- 0 warnings. Committed.

**Hint.** `setOf(a, b).size` where `a == b` is `1` for a data class, `2` for a non-data class. For the map, `map[freshEqualKey]` returns the value only when `equals`/`hashCode` are structural. This is the practical payoff of lecture 2, §1's "data class gets equality for free."

**Estimated time.** 45 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, code is idiomatic Kotlin, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (a leftover `var` that should be `val`, concatenation where a template fits, a manual loop where an operator was cleaner). |
| 3 | Works, but misses one criterion (bytecode quoted but mis-explained, the `--top` totals row wrongly limited, the smart-cast "fix" still doesn't compile). |
| 2 | Compiles and partially works; a core idea is wrong (`===` used for value comparison, a non-data class where a `data class` was the point). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for using `===` to compare values anywhere; **−2** for a hand-written `equals`/`hashCode` where a `data class` was the right tool; **−1** for any throwaway `var` that should be a `val`; **−1** for string concatenation where a template belongs.

**Target: 24/30.** Below that, the two ideas to revisit are the two the quiz grades on — reading bytecode to *verify* what an idiom compiled to (problems 1, 6) and the equality/smart-cast boundaries (problems 2, 3) — so re-run exercises 1 and 2 before resubmitting.
