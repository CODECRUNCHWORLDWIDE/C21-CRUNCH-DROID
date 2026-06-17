// Exercise 2 — Equality (== vs ===) and smart casts, pinned down with tests
//
// Goal: Prove, with passing assertions, exactly how Kotlin's two equality
//       operators behave — including the Integer-cache 127/128 split — and walk
//       the precise boundaries where the compiler will and will not smart-cast.
//       You produce a test suite that turns lecture 2 from "claims" into "facts
//       my own code demonstrates."
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// This is a JUNIT 5 (Jupiter) test suite — the test framework that ships in the
// week's Gradle starter. Drop it into `src/test/kotlin/...` of a Kotlin/JVM
// Gradle project and run with `./gradlew test`. It is pure JVM: no Android, no
// emulator, no device.
//
//   1. Add this file under src/test/kotlin/com/crunch/lab/.
//   2. Run with `./gradlew test` (or the green run gutter in the IDE).
//   3. Read which assertions pass. Where a line is commented "// won't compile",
//      UNCOMMENT it and watch the compiler reject it — the rejection IS the
//      lesson. Re-comment it so the suite builds again.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] All (uncommented) tests pass.
//   [ ] You uncommented each "// won't compile" line at least once, read the
//       compiler error, and can explain it.
//   [ ] You can state, in one sentence each: why == on boxed 127 is ===-equal
//       but boxed 128 is not, and why a custom-getter property can't smart-cast.
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package com.crunch.lab

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

class EqualityAndSmartCastTests {

    // ------------------------------------------------------------------------
    // Part A — structural (==) vs referential (===) equality
    // ------------------------------------------------------------------------

    @Test
    fun `structural equality compares values, referential compares identity`() {
        val a = "hello"
        // A different String OBJECT with the same VALUE.
        val b = StringBuilder("hel").append("lo").toString()

        assertTrue(a == b)    // structural: lowers to a null-safe equals -> true
        assertFalse(a === b)  // referential: different objects -> false
    }

    @Test
    fun `== is null-safe and never throws`() {
        val a: String? = null
        val b: String? = null
        val c: String? = "x"

        assertTrue(a == b)    // null == null -> true (the lowering handles nulls)
        assertFalse(a == c)   // null == "x" -> false, no NPE
        assertFalse(c == a)   // "x" == null -> false, no NPE
    }

    // ------------------------------------------------------------------------
    // Part B — the Integer-cache footgun (the one that bites everyone once)
    // ------------------------------------------------------------------------

    @Test
    fun `boxed Int in cache range is reference-equal`() {
        // 127 is inside the JVM Integer cache (-128..127). Both boxes are the
        // SAME cached object, so even === is true. This is a trap, not a feature.
        val a: Int? = 127
        val b: Int? = 127

        assertTrue(a == b)    // structural: 127 == 127
        assertTrue(a === b)   // referential: same CACHED Integer object (surprise!)
    }

    @Test
    fun `boxed Int outside cache range is NOT reference-equal`() {
        // 128 is OUTSIDE the cache. Each box is a fresh Integer object, so ===
        // is false even though == is true. THIS is why === on values is a bug.
        val c: Int? = 128
        val d: Int? = 128

        assertTrue(c == d)    // structural: 128 == 128 -> true
        assertFalse(c === d)  // referential: two distinct Integer objects -> false
    }

    @Test
    fun `the lesson - always use == for value comparison`() {
        // Whatever the number, == gives the right answer. === does NOT, and the
        // cache boundary makes "=== happens to work" a time bomb.
        for (n in listOf(0, 1, 127, 128, 1000, -200)) {
            val x: Int? = n
            val y: Int? = n
            assertEquals(x, y)          // == path: always correct
            // Do NOT assert x === y here — it's true for some n and false for
            // others, which is precisely the point: never compare values with ===.
        }
    }

    // ------------------------------------------------------------------------
    // Part C — data-class equality (== works because equals is generated)
    // ------------------------------------------------------------------------

    @Test
    fun `data class gets structural equality for free`() {
        val p1 = Coord(48.85, 2.35)
        val p2 = Coord(48.85, 2.35)

        assertTrue(p1 == p2)    // generated equals compares lat and lon
        assertFalse(p1 === p2)  // still two distinct objects
        assertEquals(p1.hashCode(), p2.hashCode())  // generated hashCode agrees
    }

    @Test
    fun `a plain class without equals falls back to identity`() {
        val a = Plain(1)
        val b = Plain(1)
        // No equals override -> Object.equals -> reference identity -> not equal.
        assertFalse(a == b)
        assertTrue(a == a)
    }

    // ------------------------------------------------------------------------
    // Part D — smart casts: where they work
    // ------------------------------------------------------------------------

    @Test
    fun `smart cast after is-check narrows the type`() {
        assertEquals(5, lengthOrZero("hello"))
        assertEquals(0, lengthOrZero(42))
    }

    @Test
    fun `smart cast after null check makes a value non-null`() {
        assertEquals("ADA", shout("ada"))
        assertEquals("(none)", shout(null))
    }

    @Test
    fun `smart cast inside when over types`() {
        assertEquals("int 6", classify(5))
        assertEquals("string 'HI'", classify("hi"))
        assertEquals("other", classify(3.14))
    }

    // ------------------------------------------------------------------------
    // Part E — smart casts: where they DON'T work
    //
    // Each blocked case below has a "// won't compile" line. Uncomment it, read
    // the compiler error ("Smart cast to 'String' is impossible, because ..."),
    // then re-comment it and use the val-capture fix that DOES compile.
    // ------------------------------------------------------------------------

    @Test
    fun `a custom-getter property cannot smart-cast, but a val capture can`() {
        val box = Box()

        // BLOCKED: box.value has a custom getter; two reads could differ, so the
        // compiler refuses to assume the second read is still a String.
        // if (box.value is String) {
        //     val n = box.value.length   // won't compile: smart cast impossible
        // }

        // FIX: copy to a local val (stable), then check and use that.
        val captured = box.value
        val n = if (captured is String) captured.length else -1
        assertEquals(5, n)   // Box.value returns "fixed" (length 5)
    }

    @Test
    fun `a mutable var that escapes cannot be assumed stable`() {
        // For a purely local var with no reassignment, K2 will usually still
        // smart-cast. The blocked case is a property someone else could change.
        // Holder.shared is a mutable, externally-visible var:
        Holder.shared = "hello"

        // BLOCKED: Holder.shared is a mutable property of another object; it
        // could change between the check and the use.
        // if (Holder.shared is String) {
        //     val n = Holder.shared.length   // won't compile: smart cast impossible
        // }

        // FIX: capture to a local val first.
        val captured = Holder.shared
        val n = if (captured is String) captured.length else -1
        assertEquals(5, n)
    }

    @Test
    fun `as-question-mark is the safe explicit cast`() {
        val any: Any = "world"
        val s: String? = any as? String     // safe: null if not a String
        val nope: Int? = any as? Int         // not an Int -> null, no crash
        assertEquals("world", s)
        assertEquals(null, nope)
        assertEquals("world", (any as? String) ?: "default")  // cast-or-default idiom
    }
}

// ----------------------------------------------------------------------------
// Supporting declarations
// ----------------------------------------------------------------------------

data class Coord(val lat: Double, val lon: Double)

class Plain(val n: Int)   // intentionally NOT a data class: identity equality

// Smart-cast after an is-check.
fun lengthOrZero(x: Any): Int =
    if (x is String) x.length else 0

// Smart-cast after a null check / early return.
fun shout(name: String?): String {
    name ?: return "(none)"        // after this, name is non-null
    return name.uppercase()
}

// Smart-cast inside a when over types.
fun classify(x: Any): String = when (x) {
    is Int -> "int ${x + 1}"
    is String -> "string '${x.uppercase()}'"
    else -> "other"
}

// A property with a custom getter: NOT smart-castable.
class Box {
    val value: Any?
        get() = "fixed"            // custom getter -> compiler can't assume stability
}

// A mutable, externally-visible property: NOT smart-castable.
object Holder {
    var shared: Any? = null
}

// ----------------------------------------------------------------------------
// WHY (write it in your own words before reading):
//
//   == lowers to a null-safe equals call (structural). === is a raw JVM
//   reference comparison (identity). Boxed Ints in -128..127 are shared from the
//   Integer cache, so two boxed 127s are the SAME object (=== true) while two
//   boxed 128s are DIFFERENT objects (=== false) — which is exactly why you must
//   never compare values with ===.
//
//   Smart casts require PROVABLE stability between the check and the use. A local
//   val is stable. A custom-getter property is not (the getter runs each read). A
//   mutable property another object exposes is not (it could change). The
//   universal fix is "copy to a local val and check that."
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - If `a == b` on the two Strings is unexpectedly === too, your `b` wasn't a
//   distinct object. Build it at runtime (StringBuilder) so the compiler can't
//   intern it to the same literal.
//
// - The 127/128 split depends on boxing. `val a: Int = 127` is a primitive int
//   and === isn't even meaningful. You MUST use `Int?` (nullable) so the value
//   is boxed to Integer and identity comparison applies.
//
// - "Smart cast to 'String' is impossible, because 'value' is a property that
//   has open or custom accessors" — that's the exact error for the Box case.
//   Reading it is the assignment; the val-capture is the fix.
//
// - Running `./gradlew test` but seeing "no tests found"? Confirm the JUnit 5
//   (Jupiter) dependency and `tasks.test { useJUnitPlatform() }` are in your
//   build.gradle.kts. The mini-project README shows the full test config.
//
// ----------------------------------------------------------------------------
