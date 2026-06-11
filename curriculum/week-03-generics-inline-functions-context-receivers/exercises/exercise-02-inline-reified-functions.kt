// Exercise 2 — Inline and reified functions: write them, then PROVE the substitution
//
// Goal: Write three `inline fun <reified T>` helpers, then disassemble a call site
//       with `javap` and find the CONCRETE type baked into the bytecode. The proof
//       is the point: reification is a call-site substitution, and javap shows it.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// Pure Kotlin, JVM, no Android. Put this in src/main/kotlin (or a JVM :app module),
// add the JUnit test at the bottom to src/test, compile, and run `javap` on the
// compiled call-site class.
//
//   1. Implement the three reified functions where marked.
//   2. Run the @Test block to confirm behaviour.
//   3. Compile, then:
//        javap -c -p -classpath build/classes/kotlin/main YourFileKt
//      Find the `instanceof` / class-literal that proves T was substituted.
//
// ACCEPTANCE CRITERIA
//
//   [ ] reifiedFilter, isJsonShape, enumOrNull all implemented and pass the test.
//   [ ] You ran javap on a call site and located the concrete type (e.g.
//       `instanceof java/lang/String`) baked in — paste it in a comment.
//   [ ] You can explain, in one sentence, why each function REQUIRES `inline`.
//   [ ] Builds with 0 warnings (the one guarded UNCHECKED_CAST below is justified).
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.ktgenerics

// ----------------------------------------------------------------------------
// PART A — a reified filterIsInstance.
//
// The stdlib's filterIsInstance<T>() is itself a reified inline function. Build
// your own so you understand the shape: keep only the elements that ARE a T,
// returned as a List<T> with no caller cast.
// ----------------------------------------------------------------------------

inline fun <reified T> List<*>.keepInstances(): List<T> {
    val result = mutableListOf<T>()
    for (element in this) {
        // TODO 1: if `element` is a T, add it to `result`.
        //   Because T is reified, `element is T` is a REAL, checkable type test here.
        //   (Hint: a smart-cast makes the add type-check without an explicit cast.)
    }
    return result
}

// ----------------------------------------------------------------------------
// PART B — a reified "shape check" against a JSON-ish value.
//
// Given a parsed value of type Any? (imagine it came from a JSON parser as a
// String, Long, Boolean, List<*>, or Map<*,*>), check whether it matches the
// expected type T WITHOUT the caller passing a Class. This is the pattern every
// reified deserializer uses.
// ----------------------------------------------------------------------------

inline fun <reified T> matchesShape(value: Any?): Boolean {
    // TODO 2: return whether `value` is a T. With reified T this is just `value is T`.
    //   Note: `null is String` is false, `null is String?` is true — the nullability
    //   of T at the call site decides. That's reification carrying the FULL type.
    return false // replace with the real check
}

// ----------------------------------------------------------------------------
// PART C — a reified enum lookup with no Class<E> parameter.
//
// enumValues<E>() is a reified intrinsic. Use it to look up an enum constant by
// name, returning null instead of throwing when the name is unknown. The caller
// writes enumOrNull<Direction>("NORTH") — no Class, no try/catch at the call site.
// ----------------------------------------------------------------------------

inline fun <reified E : Enum<E>> enumOrNull(name: String): E? {
    // TODO 3: use enumValues<E>() (works because E is reified) and find the constant
    //   whose `.name` equals `name`, or null if none matches.
    return null // replace with the real lookup
}

enum class Direction { NORTH, SOUTH, EAST, WEST }

// ----------------------------------------------------------------------------
// A CALL SITE to disassemble. After implementing the above, compile and run
// javap on this file's *Kt class. In `proveSubstitution`, the keepInstances<String>
// call inlines, and the `element is String` becomes a literal `instanceof
// java/lang/String` in the bytecode — that is the substitution, visible.
// ----------------------------------------------------------------------------

fun proveSubstitution(): List<String> {
    val mixed: List<Any?> = listOf("alpha", 1, "beta", null, 2.0, "gamma")
    return mixed.keepInstances<String>()   // javap this call site -> instanceof java/lang/String
}

fun main() {
    println(proveSubstitution())                       // [alpha, beta, gamma]
    println(matchesShape<String>("hi"))                // true
    println(matchesShape<Long>("hi"))                  // false
    println(enumOrNull<Direction>("NORTH"))            // NORTH
    println(enumOrNull<Direction>("UPWARD"))           // null
}

// ----------------------------------------------------------------------------
// THE TEST — move into src/test/kotlin/com/crunch/ktgenerics/ReifiedTest.kt:
//
//   import org.junit.Test
//   import kotlin.test.assertEquals
//   import kotlin.test.assertTrue
//   import kotlin.test.assertFalse
//
//   class ReifiedTest {
//       @Test fun `keepInstances filters by reified type`() {
//           val mixed = listOf("a", 1, "b", 2L, "c")
//           assertEquals(listOf("a", "b", "c"), mixed.keepInstances<String>())
//           assertEquals(listOf(1), mixed.keepInstances<Int>())
//       }
//       @Test fun `matchesShape respects the reified type and its nullability`() {
//           assertTrue(matchesShape<String>("hi"))
//           assertFalse(matchesShape<Long>("hi"))
//           assertFalse(matchesShape<String>(null))      // null is String -> false
//           assertTrue(matchesShape<String?>(null))      // null is String? -> true
//       }
//       @Test fun `enumOrNull resolves known and rejects unknown`() {
//           assertEquals(Direction.EAST, enumOrNull<Direction>("EAST"))
//           assertEquals(null, enumOrNull<Direction>("nope"))
//       }
//   }
//
// ----------------------------------------------------------------------------
// WHY each function REQUIRES inline (write it before reading):
//
//   - keepInstances: `element is T` is only checkable if T is reified; reified
//     only works because the function is inlined and T is substituted at the call
//     site. Remove `inline` and `element is T` stops compiling.
//   - matchesShape: same — `value is T` needs the concrete type at the call site.
//   - enumOrNull: `enumValues<E>()` is itself reified; it needs E's concrete class,
//     which only exists at an inlined call site.
//
//   The single guarded cast you might need: in `keepInstances`, after `element is T`
//   smart-casts `element` to T, no explicit cast is required — if you wrote one,
//   it's because the smart-cast didn't apply (e.g. `element` was a `var`). Prefer
//   the smart-cast; it's checked, not unchecked.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - TODO 1: `if (element is T) result.add(element)` — the `is T` smart-casts
//   `element` to T inside the branch, so `result.add(element)` type-checks with no
//   cast. This is the cleanest reified filter.
//
// - TODO 2: `return value is T`. Try it with String vs String? to feel how the
//   call-site nullability flows into the check.
//
// - TODO 3: `return enumValues<E>().firstOrNull { it.name == name }`. enumValues<E>()
//   compiles only because E is reified — proof that reification reaches stdlib
//   intrinsics too.
//
// - javap shows a generic check instead of `instanceof java/lang/String`? You ran
//   javap on the function DEFINITION class, not the CALL SITE. The substitution
//   happens where you CALL keepInstances<String>() — disassemble proveSubstitution.
//
// - "Cannot check for instance of erased type: T" — you forgot `inline` or
//   `reified`. Both are required; the error names exactly that.
//
// ----------------------------------------------------------------------------
