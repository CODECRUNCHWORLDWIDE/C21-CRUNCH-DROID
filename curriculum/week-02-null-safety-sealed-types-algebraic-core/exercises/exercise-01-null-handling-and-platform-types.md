# Exercise 1 — Null handling and platform types

**Goal.** Replace crash-prone, `!!`-laden null handling with the idiomatic `?` operator family, handle a platform-type boundary the way a senior engineer does, and read the bytecode each operator compiles to so you know — not guess — what `?.`, `?:`, and `!!` actually do. This is the null-safety discipline the whole week rests on.

**Estimated time.** 40 minutes.

**Prerequisites.** The Week 1 Gradle Kotlin DSL toolchain (JDK 21, Kotlin 2.2.x, JUnit 5) and your `javap` habit. No Android, no device — pure JVM.

---

## Step 1 — Refactor a `!!`-everywhere function

Drop this into `src/main/kotlin/com/crunch/nulls/Profile.kt`. It works on the happy path and crashes on everything else — the Javaism of null handling.

```kotlin
package com.crunch.nulls

data class Address(val city: String?, val zip: String?)
data class Person(val name: String?, val address: Address?)

// CRASH-PRONE: every !! is a NullPointerException waiting to happen.
fun cityUpperBad(person: Person?): String {
    return person!!.address!!.city!!.uppercase()
}
```

Rewrite it as `cityUpperGood(person: Person?): String` so that:

- A `null` person, a null address, or a null city yields the string `"UNKNOWN"` (no crash).
- A present city is returned uppercased.
- There is **not a single `!!`** in your version.

The idiomatic shape uses a safe-call chain and Elvis:

```kotlin
fun cityUpperGood(person: Person?): String =
    person?.address?.city?.uppercase() ?: "UNKNOWN"
```

Write a JUnit 5 test proving all four cases (fully present, null person, null address, null city) behave as specified.

## Step 2 — Early-return narrowing with `?: return`

Sometimes you want to bail out, not default. Write:

```kotlin
fun greet(name: String?): String {
    // TODO: if name is null, return "Hi, stranger". Otherwise greet by the
    //       (now non-null, smart-cast) name. Use `?: return`.
    val n = name ?: return "Hi, stranger"
    return "Hi, ${n.uppercase()}!"
}
```

Confirm with a test that `greet(null) == "Hi, stranger"` and `greet("ada") == "Hi, ADA!"`. Note that after `val n = name ?: return ...`, `n` is a non-null `String` for the rest of the function — the Elvis-return narrowed it.

## Step 3 — `as?` for safe casts

Write a function that extracts an `Int` from an `Any?` without ever throwing:

```kotlin
fun asIntOrZero(value: Any?): Int =
    // TODO: cast value to Int if possible, else 0. Use `as?` and Elvis.
    (value as? Int) ?: 0
```

Test it with `42` (→ 42), `"hello"` (→ 0), `null` (→ 0), and `3.14` (→ 0). Note that `as?` returns `null` on a type mismatch instead of throwing `ClassCastException` like the unsafe `as` would.

## Step 4 — Handle a platform-type boundary

Here is a tiny Java class simulating an un-annotated library. Put it in `src/main/java/com/crunch/nulls/LegacyApi.java`:

```java
package com.crunch.nulls;

public class LegacyApi {
    // No @Nullable annotation — Kotlin sees the return as a PLATFORM type String!
    public static String lookup(String key) {
        return key.equals("known") ? "value-for-known" : null;   // returns null for unknown keys!
    }
}
```

Now in Kotlin, call it *safely*. The platform type lets you pretend it's non-null — which crashes for unknown keys. Do it right instead:

```kotlin
package com.crunch.nulls

fun safeLookup(key: String): String {
    // LegacyApi.lookup returns a platform type String! — treat it as nullable
    // AT THE BOUNDARY by assigning to an explicit String?, then handle the null.
    val result: String? = LegacyApi.lookup(key)
    return result ?: "DEFAULT"
}
```

Write a test: `safeLookup("known") == "value-for-known"` and `safeLookup("unknown") == "DEFAULT"` — and crucially, the unknown case does **not** throw. Then, to *feel* the leak, temporarily write the unsafe version `val result: String = LegacyApi.lookup(key)` (explicit non-null), run `safeLookup("unknown")`, and watch it NPE *at the boundary line*. Note in `notes/platform-types.md` why the boundary crash (with a clear line) is still better than letting a `String!` float deeper — and re-fix it.

## Step 5 — Read the bytecode of the operators

Compile and disassemble `cityUpperGood` and `greet`:

```bash
./gradlew compileKotlin
javap -c -p build/classes/kotlin/main/com/crunch/nulls/ProfileKt.class
```

**Answer in `notes/platform-types.md`:**

- In `cityUpperGood`, find the null checks for the safe-call chain. What instruction implements `?.` (look for `ifnull`/`ifnonnull` branches)?
- Add a function with a `!!` (`fun forceCity(p: Person): String = p.address!!.city!!`) and disassemble it. What `Intrinsics.checkNotNull` (or `Intrinsics.checkNotNullExpressionValue`) call appears, and what does it do when the value is null? This is the literal bytecode of "crash if null."

---

## Acceptance criteria

- [ ] `cityUpperGood` has **zero `!!`**, returns `"UNKNOWN"` for any null link, uppercases a present city, and passes a four-case test.
- [ ] `greet` uses `?: return` to narrow; `asIntOrZero` uses `as?` + Elvis; both tested.
- [ ] `safeLookup` narrows the platform type at the boundary (explicit `String?`) and never NPEs on an unknown key; tested.
- [ ] `notes/platform-types.md` quotes the `?.` branch bytecode and the `!!` `Intrinsics.checkNotNull` call from your actual `javap`, and explains why narrowing at the boundary beats letting `String!` float.
- [ ] Build with **0 warnings, 0 errors**.

## What you just proved

You proved the null-safety model from lecture 1 end to end: the `?` family handles absence without crashing (`?.` chains, `?:` defaults, `?: return` narrows, `as?` casts safely), `!!` is the explicit "crash if null" you now avoid by reflex, and the platform-type leak from Java is sealed by narrowing at the boundary. And you saw the bytecode — `?.` is a branch, `!!` is an `Intrinsics.checkNotNull` — so none of it is magic.

---

## Hints (read only if stuck > 10 min)

- **The safe-call chain returns `String?`, but the function declares `String`.** That's what the trailing `?: "UNKNOWN"` is for — Elvis removes the nullability by supplying a non-null default. Without it, the types won't match and it won't compile.
- **`?: return` complains about `Nothing`.** That's correct and fine — `return` has type `Nothing`, which is a subtype of everything, so `name ?: return X` typechecks. The `val n` after it is non-null.
- **The Java file isn't found.** Put it under `src/main/java/...` (not `src/main/kotlin/...`) and ensure your `build.gradle.kts` doesn't disable Java compilation. The `kotlin("jvm")` plugin compiles `src/main/java` by default.
- **You don't see `Intrinsics.checkNotNull` for the `!!`.** Make sure you disassembled the function that actually contains the `!!`, with `-p` (to show synthetic/private members). The check is inserted right before the dereference.
