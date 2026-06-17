# Week 01 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 2. Answer key with explanations at the bottom — don't peek.

---

**Q1.** What is the K2 compiler, in 2026?

- A) A new runtime that interprets Kotlin bytecode faster than the JVM.
- B) A from-scratch rewrite of the Kotlin compiler's *frontend* (parsing, name resolution, type inference, smart-cast analysis), default since Kotlin 2.0, around a representation called FIR.
- C) A replacement for the JVM on Android.
- D) The Compose compiler.

---

**Q2.** You write a top-level function `fun slugify(s: String): String` in a file `Strings.kt`. What does it compile to on the JVM?

- A) A method on an interface `Strings`.
- B) A `public static final` method on a synthesized class `StringsKt`.
- C) An instance method requiring a `Strings()` object.
- D) Nothing — top-level functions are erased at compile time.

---

**Q3.** Which of these is the idiomatic, expression-style way to write a function that returns a grade?

- A) Declare `var grade`, assign it in an `if`/`else` chain, then `return grade`.
- B) `fun grade(s: Int): String = if (s >= 90) "A" else if (s >= 80) "B" else "C"`
- C) Use a `when` that returns `Unit` and prints the grade.
- D) Throw an exception for each grade boundary.

---

**Q4.** What is the difference between `==` and `===` in Kotlin?

- A) They are identical; `===` is just a typo-tolerant alias.
- B) `==` is referential identity; `===` is structural equality.
- C) `==` is structural equality (lowers to a null-safe `equals`); `===` is referential identity (same object).
- D) `==` only works on numbers; `===` only works on objects.

---

**Q5.** Given `val a: Int? = 127; val b: Int? = 127; val c: Int? = 128; val d: Int? = 128`, what are `a === b` and `c === d`?

- A) Both `true`.
- B) Both `false`.
- C) `a === b` is `true` (cached Integer), `c === d` is `false` (outside the −128..127 cache).
- D) `a === b` is `false`, `c === d` is `true`.

---

**Q6.** Why is using `===` to compare two numeric values a bug?

- A) `===` doesn't compile on numbers.
- B) Because boxed integers are object-identity-equal only inside the −128..127 cache, so `===` gives `true` for some values and `false` for others — use `==` for value comparison, always.
- C) `===` is slower than `==`.
- D) It isn't a bug; `===` is the correct value comparison.

---

**Q7.** In `fun lengthOrZero(x: Any): Int = if (x is String) x.length else 0`, why does `x.length` compile without a cast?

- A) `Any` has a `length` property.
- B) The compiler smart-casts `x` to `String` inside the `is String` branch.
- C) `length` is a Kotlin keyword.
- D) Kotlin ignores types at runtime.

---

**Q8.** A property `val value: Any? get() = compute()` (a custom getter) is checked with `if (box.value is String) { ... box.value.length ... }`. What happens?

- A) It smart-casts fine; custom getters are stable.
- B) It does **not** smart-cast — a custom getter could return a different value on the second read, so the compiler refuses; copy to a local `val` first.
- C) It crashes at runtime with a `ClassCastException`.
- D) It works but only under K2.

---

**Q9.** What is the difference between `val` and `const val`?

- A) None; `const` is decorative.
- B) `val` is a runtime read-only binding (compiles to a `final` field + getter); `const val` is a compile-time constant of a primitive/`String` type, *inlined* at each use site.
- C) `const val` is mutable.
- D) `val` can only hold numbers.

---

**Q10.** A `data class Point(val x: Int, val y: Int)` generates which members?

- A) Only a constructor.
- B) `getX`/`getY`, `component1`/`component2`, `copy` (+ `copy$default`), `toString`, `hashCode`, and `equals`.
- C) Just `toString`.
- D) Nothing extra; `data` is a comment.

---

**Q11.** A function `fun greet(name: String, greeting: String = "Hello")` with a default argument compiles to:

- A) One method; the JVM has native default arguments.
- B) Two methods: `greet(String, String)` and a synthetic `greet$default(String, String, int, Object)` that uses an `int` bitmask to fill in omitted arguments.
- C) Four overloads, one per argument combination.
- D) A method that reads the default from a config file at runtime.

---

**Q12.** Which is the idiomatic replacement for a manual loop that filters active users and uppercases their names into a new list?

- A) `for (u in users) { if (u.active) names.add(u.name.uppercase()) }`
- B) `users.filter { it.active }.map { it.name.uppercase() }`
- C) A `while` loop with an index.
- D) A recursive function.

---

**Q13.** In the `kt-stat` mini-project's `build.gradle.kts`, why is `mainClass` set to `"com.crunch.ktstat.MainKt"` and not `"com.crunch.ktstat.Main"`?

- A) Gradle requires a `Kt` suffix on all classes.
- B) Because `main()` is a top-level function in `Main.kt`, and top-level functions compile to `static` methods on a `FileNameKt` class — here, `MainKt`.
- C) `Main` is a reserved word.
- D) It's arbitrary; both work.

---

## Answer key

**Q1 — B.** K2 is the rewritten *frontend* (analysis: parsing, resolution, inference, smart casts), default since Kotlin 2.0, built around FIR. It is not a runtime and not the Compose compiler (though the Compose compiler is a *plugin* that runs in the frontend K2 provides). (Lecture 1, §2.)

**Q2 — B.** A top-level function becomes a `public static final` method on a synthesized `FileNameKt` class — `StringsKt` here. That's why it's the idiomatic replacement for a Java utility class, and why Java callers write `StringsKt.slugify(...)`. (Lecture 1, §3.)

**Q3 — B.** The single-expression body with `if` as an *expression* is the idiom — no throwaway `var`, no separate `return`. A is the Javaism this week trains you out of. (Lecture 1, §4; exercise 3.)

**Q4 — C.** `==` is structural (lowers to a null-safe `equals`); `===` is referential identity. This is the reverse of Java intuition, where `==` on objects is reference identity. (Lecture 2, §1.)

**Q5 — C.** Boxed `127` comes from the shared Integer cache (−128..127), so `a === b` is `true`; `128` is outside the cache, so `c === d` is two distinct objects → `false`. (Lecture 2, §1; exercise 2.)

**Q6 — B.** The cache boundary makes `===` on values `true` for some numbers and `false` for others — a time bomb. `==` is always correct for value comparison. (Lecture 2, §1.)

**Q7 — B.** Inside `is String`, the compiler smart-casts `x` to `String`, so `x.length` is valid with no explicit cast. The `checkcast` is still in the bytecode; you just didn't type it. (Lecture 2, §2.)

**Q8 — B.** A custom getter isn't provably stable (it could return a different value each call), so the compiler refuses to smart-cast. The fix is `val captured = box.value; if (captured is String) ...`. (Lecture 2, §2; exercise 2, Part E.)

**Q9 — B.** `val` is a runtime read-only (a `final` field + getter); `const val` is a compile-time constant, inlined at every use site (`javap` shows the literal, no field read). `const val` is restricted to primitives/`String` with compile-time-constant initializers. (Lecture 1, §6.)

**Q10 — B.** The `data` modifier generates the property getters, `componentN` (for destructuring), `copy` (+ `copy$default`), `toString`, `hashCode`, and `equals`. That's the "idiomatic costs nothing" proof from lecture 1, §7. (Exercise 1, Step 4.)

**Q11 — B.** Two methods: the real one and a synthetic `$default` carrying an `int` bitmask of which arguments were omitted. This is how Kotlin implements defaults on a JVM with no native support, and why `@JvmOverloads` exists for Java interop. (Lecture 1, §7.)

**Q12 — B.** `filter { }.map { }` is the collection-operator pipeline — a description of *what*, not *how*. A is the manual-loop Javaism. (One honest caveat from the challenge: the pipeline allocates an intermediate list the loop didn't — negligible at app scale, fixable with sequences in Week 3.) (Lecture 2, §3.)

**Q13 — B.** `main()` is a top-level function in `Main.kt`, so it compiles to a `static` method on `MainKt` (lecture 1, §3). The application plugin needs the *class* that holds `main`, which is `MainKt`, not `Main`. (Mini-project Milestone 1.)

---

*Score 11+? On to Week 2. Below 9? Re-read both lecture notes and re-run exercises 1 and 2 — the bytecode-reading habit and the equality/smart-cast boundaries are the two ideas this week is graded on, and they're load-bearing for the sealed-types and generics weeks ahead.*
