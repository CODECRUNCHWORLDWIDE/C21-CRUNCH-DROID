# Week 02 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 2 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets a JDK 21 toolchain, Kotlin 2.2.x, built with Gradle Kotlin DSL, tested with JUnit 5. Every problem must build with **0 warnings**.

---

## Problem 1 — De-`!!` a real function

**Problem statement.** Write (or find in your own code) a function with at least three `!!`s and a nested-nullable access, then refactor it to use only the `?` family (`?.`, `?:`, `?: return`, `as?`, `?.let`) with zero `!!`. Keep a before/after. Write a JUnit 5 test proving every null path is handled without crashing.

**Acceptance criteria.**

- A `before/` and `after/` (or a diff) showing the refactor; the after version has **zero `!!`**.
- A test exercising every null branch (each link in the chain being null) with no thrown exception.
- A one-line note per `!!` removed naming the operator that replaced it.
- 0 warnings. Committed.

**Hint.** A deep nullable chain (`a?.b?.c?.d`) plus a trailing `?: default` handles most of it. For "bail out," use `?: return`. For "do a block if present," `?.let`.

**Estimated time.** 40 minutes.

---

## Problem 2 — Seal a platform-type boundary

**Problem statement.** Write a small Java class (`src/main/java/...`) with an un-annotated method returning a `String` that is sometimes null. Call it from Kotlin two ways: the unsafe way (assign to `String`, watch it NPE on the null path) and the safe way (assign to `String?` at the boundary, handle the null). Then add a `@Nullable` (JSpecify or `org.jetbrains.annotations.Nullable`) annotation to the Java method and show that Kotlin now sees the return as `String?` automatically — the leak closes.

**Acceptance criteria.**

- A test demonstrating the unsafe version throws on the null path (use `assertThrows`) and the safe version returns a default.
- After adding `@Nullable`, the Kotlin call site treats the return as `String?` (the unsafe non-null assignment now produces a *compiler warning/error* or requires handling).
- `notes/platform-boundary.md` explains: platform types, why the annotation fixes it, and the boundary-narrowing discipline.
- 0 warnings (in the final, annotation-fixed state). Committed.

**Hint.** `org.jetbrains.annotations:annotations` or the JSpecify `org.jspecify:jspecify` dependency gives you the `@Nullable` annotation. Kotlin reads it and converts the platform type to a proper nullable type.

**Estimated time.** 45 minutes.

---

## Problem 3 — A traffic-light enum vs a sealed state

**Problem statement.** Model a traffic light two ways. First as an `enum class TrafficLight` with an abstract `fun next(): TrafficLight` overridden per constant (Red→Green, Green→Yellow, Yellow→Red). Then model a richer "intersection state" as a **sealed** type where the cases carry *different* data (e.g. `Normal(light: TrafficLight)`, `Flashing(color: String, sinceEpoch: Long)`, `OutOfService(reason: String)`) and write an exhaustive `when` over it. Document, in one paragraph, where the enum was sufficient and why the intersection state needed a sealed type.

**Acceptance criteria.**

- An `enum class TrafficLight` with an abstract `next()` overridden per constant; a test that `Red.next() == Green`, etc., cycles correctly.
- A `sealed interface IntersectionState` with at least three cases carrying different data; an exhaustive `when` consumer (no `else`).
- `notes/enum-vs-sealed.md` with the one-paragraph "where the line is" explanation.
- 0 warnings. Committed.

**Hint.** The enum works because every light is the same shape (no per-state payload differences). The intersection state needs sealed because `Flashing` and `OutOfService` carry *different* fields — an enum can't express that.

**Estimated time.** 45 minutes.

---

## Problem 4 — Value classes stop a unit bug

**Problem statement.** Write a function `fun trip(distanceMeters: Double, speedMetersPerSec: Double): Double` returning duration in seconds, then show how easy it is to call it wrong (passing km where meters were expected, or swapping the two `Double` args). Now redesign with inline value classes (`Meters`, `MetersPerSecond`, `Seconds`) so the swapped/wrong-unit call is a **compile error**. Decompile and confirm the value-class parameters erase to `double`.

**Acceptance criteria.**

- A `before` showing the all-`Double` version compiling a wrong call (e.g. swapped arguments).
- An `after` with value classes where the wrong call **does not compile** (the bad call commented out with the error pasted).
- `notes/units.md` quoting the decompiled signature showing the parameters erased to `double` (no boxing).
- A test on the correct path. 0 warnings. Committed.

**Hint.** `@JvmInline value class Meters(val value: Double)` etc. The function becomes `fun trip(distance: Meters, speed: MetersPerSecond): Seconds`. Decompile to see `double` parameters and a mangled name.

**Estimated time.** 45 minutes.

---

## Problem 5 — Three outcome models, one operation, measured trade-offs

**Problem statement.** Take "look up a config value by key and parse it as an Int" and implement it three ways: nullable (`Int?`), stdlib `Result<Int>`, and a typed sealed `ConfigResult` (`Found(Int)` / `Missing` / `NotAnInt(raw)`). Write a caller for each that handles every outcome. Then write `notes/outcome-tradeoffs.md` stating, for each, what information you have and don't have at the call site, and which you'd pick for (a) an internal helper where absence is fine, (b) a public API boundary where callers must distinguish failures.

**Acceptance criteria.**

- All three implementations plus a caller for each; the sealed caller uses an exhaustive `when` (no `else`).
- `notes/outcome-tradeoffs.md` with the per-model analysis and the two recommendations.
- Tests covering found / missing / not-an-int for the sealed version.
- 0 warnings. Committed.

**Hint.** Nullable conflates missing and not-an-int (both → null). `Result` gives you a `Throwable` but not a typed reason. The sealed version distinguishes all three and forces handling. The recommendation usually lands: nullable for (a), sealed for (b).

**Estimated time.** 50 minutes.

---

## Problem 6 — Extend the JSON parser with a typed accessor

**Problem statement.** Add a typed accessor to your mini-project parser: extension functions `JsonNode.path(vararg keys: String): JsonNode?` that walks nested objects, and `JsonNode.asStringOrNull(): String?` / `asDoubleOrNull(): Double?` / `asBoolOrNull(): Boolean?`. Make `tree.path("user", "address", "city")?.asStringOrNull()` work. Each accessor must `when` over the sealed `JsonNode` internally.

**Acceptance criteria.**

- `path` walks nested `JsonObject`s, returning null if any key is missing or a non-object is hit mid-path.
- The `as...OrNull` accessors return the value for the matching node type and null otherwise (each via a `when` over `JsonNode`).
- Tests: a deep path that resolves, a path that misses a key, a path that hits a non-object, and each typed accessor on the right and wrong node type.
- 0 warnings. Committed.

**Hint.** `path` is a fold over the keys: start at the node, and for each key, if the current node is a `JsonObject`, look up the key (else return null). `asStringOrNull` is `(this as? JsonNode.JsonString)?.value` — or a `when` returning the value for `JsonString` and null otherwise.

**Estimated time.** 45 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, code is idiomatic Kotlin, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (a leftover `!!` that should be `?:`, an `else` in a sealed `when`, a missing `data` on a value-bag class). |
| 3 | Works, but misses one criterion (the unsafe boundary not actually demonstrated, value class boxes where it should erase, the sealed caller not exhaustive). |
| 2 | Compiles and partially works; a core idea is wrong (illegal states still constructible, nullable used where the failures needed distinguishing). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for an `!!` used to silence the compiler where a `?` operator was the right tool; **−2** for a model that still lets an illegal state be constructed when the problem asked to prevent it; **−1** for an `else` branch smuggled into a `when` over a sealed type (defeating the exhaustiveness check); **−1** for letting a platform type (`String!`) float past the boundary instead of narrowing it.

**Target: 24/30.** Below that, the two ideas to revisit are the two the quiz grades on — the exhaustive-`when` discipline (problems 3, 5, 6) and making illegal states unrepresentable with sealed types and value classes (problems 4, 5) — so re-run exercises 2 and 3 before resubmitting.
