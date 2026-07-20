# Week 02 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 3. Answer key with explanations at the bottom — don't peek.

---

**Q1.** What is the difference between `String` and `String?` in Kotlin?

- A) None; the `?` is decorative.
- B) `String` can never be null (the compiler guarantees it); `String?` might be null, and the compiler forces you to handle the null before using it as a string.
- C) `String?` is a different class entirely with different methods.
- D) `String` is nullable; `String?` is non-null.

---

**Q2.** What is the result type of `name?.length` where `name: String?`?

- A) `Int` — always.
- B) `Int?` — because the safe call short-circuits to `null` if `name` is null.
- C) `String?`.
- D) It's a compile error.

---

**Q3.** What does `findUser(id) ?: return null` do?

- A) Always returns null.
- B) Calls `findUser`, and if it returns null, returns null from the enclosing function early; otherwise the result is the non-null user, smart-cast for the rest of the function.
- C) Throws if the user is null.
- D) Nothing; `?:` can't be followed by `return`.

---

**Q4.** What does `value!!` compile to, and when does it throw?

- A) A no-op; `!!` is erased.
- B) An `Intrinsics.checkNotNull` call that throws a `NullPointerException` if `value` is null.
- C) A safe call that returns null.
- D) A cast to `Any`.

---

**Q5.** You call an un-annotated Java method that returns `String`. What type does Kotlin assign, and what's the risk?

- A) `String?`; no risk.
- B) `String`; guaranteed non-null.
- C) A *platform type* (`String!`) the compiler lets you treat as either nullable or non-null — and if you treat it as non-null and it was actually null, you get a runtime NPE. Seal the leak by narrowing to an explicit type at the boundary.
- D) `Any`; you must cast.

---

**Q6.** Why can a `when` over a `sealed interface` omit the `else` branch?

- A) Sealed types don't support `else`.
- B) Because the hierarchy is closed and known entirely at compile time, so the compiler can verify every case is covered — and if you add a case later, every incomplete `when` becomes a compile error.
- C) `else` is optional in all `when`s.
- D) Because sealed types have a default case built in.

---

**Q7.** You add a fourth subtype to a sealed type. What happens to existing exhaustive `when`s that used no `else`?

- A) Nothing; they keep working and ignore the new case.
- B) They fail to compile until you add a branch for the new case — the compiler walks you to every site that needs updating.
- C) They silently route the new case to the first branch.
- D) They throw at runtime.

---

**Q8.** What is the difference between a product type and a sum type?

- A) They're the same.
- B) A product type (data class) bundles several fields together ("a *and* b *and* c"); a sum type (sealed) is exactly one of several cases ("a *or* b *or* c").
- C) A product type is for numbers; a sum type is for strings.
- D) A sum type can hold multiple values; a product type holds one.

---

**Q9.** Why is this model dangerous: `data class Payment(val status: String, val confirmationCode: String?, val failureReason: String?)`?

- A) It uses too many fields.
- B) It allows illegal states — e.g. a "pending" payment with both a confirmation code and a failure reason — to be constructed, because the nullable fields aren't tied to the status. A sealed sum where each case carries only its valid data fixes it.
- C) `String` is the wrong type for status.
- D) It's not dangerous; it's idiomatic.

---

**Q10.** `@JvmInline value class UserId(val raw: Long)` is passed to `fun load(id: UserId)`. What does the parameter look like in the decompiled bytecode (non-null, monomorphic position)?

- A) A `UserId` object (always boxed).
- B) A raw `long` — the value class erases to its underlying type in non-null, non-generic positions (zero allocation).
- C) An `Object`.
- D) An `int`.

---

**Q11.** In which situation must an inline value class **box** (allocate a real wrapper object)?

- A) Never; value classes never box.
- B) When it's nullable (`UserId?`), used as a generic type argument (`List<UserId>`), or assigned to an interface/supertype it implements.
- C) Only on Android.
- D) Whenever it has a method.

---

**Q12.** You need to model "parse an Int that might fail as not-a-number OR be negative, and force callers to handle each case." Which is the best fit?

- A) Return `Int?` (nullable).
- B) Return `Result<Int>` from `runCatching`.
- C) Return a typed sealed result (`Ok` / `NotANumber` / `Negative`) so callers `when` over the enumerated failures exhaustively.
- D) Throw an exception.

---

**Q13.** In the JSON parser mini-project, why is `JsonNull` modelled as a `data object JsonNull : JsonNode` rather than using Kotlin's `null`?

- A) Because `null` isn't allowed in sealed types.
- B) Because a JSON null is a distinct, meaningful value in the tree ("the key exists and its value is JSON null"), which differs from "the key is absent" — using Kotlin `null` would conflate the two.
- C) Because `data object` is faster.
- D) There's no reason; either works identically.

---

## Answer key

**Q1 — B.** Nullability is part of the type. `String` is compiler-guaranteed non-null; `String?` forces you to handle the null. The most common Java crash becomes a Kotlin compile error. (Lecture 1, §1.)

**Q2 — B.** A safe call `?.` short-circuits to `null` if the receiver is null, so the result is nullable — `Int?`, not `Int`. The trailing `?: default` is how you'd remove the nullability. (Lecture 1, §2.)

**Q3 — B.** `?: return` is the Elvis-with-early-return idiom: if the left is null, exit the function early; otherwise the value is non-null and smart-cast for the rest of the body. (`return` has type `Nothing`, which is why it fits on the right of `?:`.) (Lecture 1, §2.)

**Q4 — B.** `!!` compiles to an `Intrinsics.checkNotNull` (or `checkNotNullExpressionValue`) call that throws a `NullPointerException` when the value is null. It's the explicit "crash if null" — a code smell in most places. (Lecture 1, §2–3.)

**Q5 — C.** An un-annotated Java return is a *platform type* (`String!`): the compiler lets you treat it as nullable or not, and trusts you. Treating it as non-null when it's actually null gives you the exact NPE Kotlin prevents elsewhere. The discipline is to narrow at the boundary (assign to explicit `String`/`String?`) or prefer annotated/JSpecify libraries. (Lecture 1, §4.)

**Q6 — B.** A sealed hierarchy is closed and fully known at compile time, so the compiler can check exhaustiveness without an `else` — and enforce it as you add cases. (Lecture 2, §2.)

**Q7 — B.** Every `else`-free exhaustive `when` over the type fails to compile until the new case is handled. That's the refactoring-assistant property — and exactly why you *don't* write `else` (which would silently swallow the new case). (Lecture 2, §2; exercise 2.)

**Q8 — B.** Product = data class = "fields *and* fields." Sum = sealed = "one of these cases." Composed, they're algebraic data types. (Lecture 2, §3–4.)

**Q9 — B.** The nullable fields aren't tied to the status, so illegal combinations (pending + confirmation + failure reason) construct fine. The fix is a sealed `Payment` where each case (`Pending`, `Completed(code)`, `Failed(reason)`) carries exactly its valid data, making the illegal states unrepresentable. (Lecture 2, §4; challenge.)

**Q10 — B.** In a non-null, monomorphic position the value class erases to its underlying type — a raw `long` — with zero allocation. (The function name is mangled for interop, but that's not a cost.) (Lecture 2, §5; exercise 3.)

**Q11 — B.** It boxes when nullable (`UserId?` can't be a `long`), as a generic argument (`List<UserId>` erases to objects), or when assigned to an interface/supertype. Monomorphic non-null positions erase. (Lecture 2, §5.)

**Q12 — C.** Multiple distinct, enumerable failure modes that callers must handle → a typed sealed result, so the consuming `when` is exhaustive. Nullable loses the reason; `Result<T>` gives an untyped `Throwable`; throwing skips compile-time handling. (Lecture 2, §6; exercise 3.)

**Q13 — B.** `JsonNull` is a real value in the tree (the JSON literal `null`), distinct from "key absent." A `data object` case models it precisely; Kotlin's `null` would conflate "value is JSON null" with "no value." (Mini-project, the algebraic model.)

---

*Score 11+? On to Week 3. Below 9? Re-read both lecture notes and re-run exercises 2 and 3 — the exhaustive-`when` "build breaks on a new case" property and the value-class erasure/outcome-choice are the two ideas this week is graded on, and they're load-bearing for the generics week and all of Phase 2's state modelling.*
