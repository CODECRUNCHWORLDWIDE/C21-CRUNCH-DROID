// Exercise 3 — Inline value classes and modelling an outcome three ways
//
// Goal: (A) Stop a "longly-typed" bug with inline value classes and SEE that the
//       wrapper erases at runtime. (B) Model the same fallible operation three
//       ways — nullable, the stdlib Result<T>, and a typed sealed result — and
//       feel the trade-offs.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// This is a JUNIT 5 test suite. Drop it under src/test/kotlin/com/crunch/adt/
// in a Kotlin/JVM Gradle project and run with `./gradlew test`. Pure JVM.
//
//   1. Implement the TODO-marked functions.
//   2. Run the tests — green proves correctness.
//   3. Decompile (Tools > Kotlin > Show Kotlin Bytecode > Decompile, or javap)
//      the `chargeAmount` function and confirm the Money parameter erased to a
//      raw `long`. Write the finding in notes/value-class-erasure.md.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] All tests pass.
//   [ ] You confirmed (via decompile/javap) that a non-null Money parameter
//       erases to `long`, and you can name one situation where it would BOX.
//   [ ] You can state, in one sentence each, when to pick nullable vs Result<T>
//       vs a typed sealed result.
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package com.crunch.adt

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull

// ----------------------------------------------------------------------------
// Part A — inline value classes: distinct types that erase to primitives
// ----------------------------------------------------------------------------

@JvmInline value class UserId(val raw: Long)
@JvmInline value class AccountId(val raw: Long)
@JvmInline value class Money(val cents: Long) {
    // Value classes CAN have methods and computed properties.
    operator fun plus(other: Money) = Money(cents + other.cents)
    val dollars: Double get() = cents / 100.0
}

// A function that takes a Money. Decompile it: the parameter erases to `long`.
fun chargeAmount(account: AccountId, amount: Money): String =
    "Charging account ${account.raw} for ${amount.dollars} dollars"

class ValueClassTests {

    @Test
    fun `distinct value classes cannot be swapped`() {
        val user = UserId(42)
        val account = AccountId(42)
        // These are DIFFERENT types even though both wrap Long(42).
        // Uncomment to see the compile error — the whole point of value classes:
        // assertEquals(user, account)   // won't compile: UserId != AccountId

        assertEquals(42L, user.raw)
        assertEquals(42L, account.raw)
        // Same wrapper type + same value = equal (generated equals):
        assertEquals(UserId(42), UserId(42))
    }

    @Test
    fun `value classes carry behaviour and erase at runtime`() {
        val a = Money(150)   // $1.50
        val b = Money(250)   // $2.50
        assertEquals(Money(400), a + b)     // operator fun plus
        assertEquals(4.0, (a + b).dollars)  // computed property
        // The decompiled chargeAmount takes a `long` for `amount` — confirm it.
        assertTrue(chargeAmount(AccountId(7), Money(999)).contains("9.99"))
    }
}

// ----------------------------------------------------------------------------
// Part B — one fallible operation, modelled three ways.
//
// The operation: parse a non-negative integer "quantity" from a String.
//   - invalid  -> not a number
//   - negative -> a number but illegal
//   - ok       -> the value
// ----------------------------------------------------------------------------

// (1) NULLABLE: cheapest. null = "couldn't get a valid quantity", reason lost.
fun parseQtyNullable(raw: String): Int? =
    // TODO 1: return the parsed Int if it's a non-negative number, else null.
    //         Hint: raw.trim().toIntOrNull()?.takeIf { it >= 0 }
    TODO("implement parseQtyNullable")

// (2) stdlib Result<T>: success or the thrown exception. Reason = a Throwable.
fun parseQtyResult(raw: String): Result<Int> =
    // TODO 2: runCatching { } that parses and throws IllegalArgumentException
    //         on a negative value. Result captures whatever is thrown.
    TODO("implement parseQtyResult with runCatching")

// (3) TYPED SEALED RESULT: enumerated failure modes the compiler forces you to
//     handle. This is the richest model — and what the mini-project's parser uses.
sealed interface QtyResult {
    data class Ok(val value: Int) : QtyResult
    data object NotANumber : QtyResult
    data class Negative(val value: Int) : QtyResult
}

fun parseQtyTyped(raw: String): QtyResult {
    // TODO 3: return Ok(n) for a non-negative number, Negative(n) for a negative
    //         number, NotANumber when it isn't a number at all.
    val n = raw.trim().toIntOrNull() ?: return QtyResult.NotANumber
    return if (n < 0) QtyResult.Negative(n) else QtyResult.Ok(n)
}

class OutcomeModellingTests {

    @Test
    fun `nullable - present or null, reason lost`() {
        assertEquals(5, parseQtyNullable("5"))
        assertNull(parseQtyNullable("-3"))      // negative -> null (we don't know WHY)
        assertNull(parseQtyNullable("abc"))     // not a number -> null (also)
    }

    @Test
    fun `Result - success or captured exception`() {
        assertEquals(5, parseQtyResult("5").getOrNull())
        assertTrue(parseQtyResult("abc").isFailure)
        assertTrue(parseQtyResult("-3").isFailure)
        // fold lets you handle both arms functionally:
        val label = parseQtyResult("-3").fold(
            onSuccess = { "ok $it" },
            onFailure = { "bad: ${it.message}" },
        )
        assertTrue(label.startsWith("bad:"))
    }

    @Test
    fun `typed sealed result - every failure mode is enumerated and handled`() {
        // The caller `when`s exhaustively over the typed failures — no `else`.
        fun toLabel(r: QtyResult): String = when (r) {
            is QtyResult.Ok       -> "ok ${r.value}"
            QtyResult.NotANumber  -> "not a number"
            is QtyResult.Negative -> "negative ${r.value}"
        }
        assertEquals("ok 5", toLabel(parseQtyTyped("5")))
        assertEquals("not a number", toLabel(parseQtyTyped("abc")))
        assertEquals("negative -3", toLabel(parseQtyTyped("-3")))
    }
}

// ----------------------------------------------------------------------------
// THE THREE-WAY CHOICE (write it in your own words before reading):
//
//   - Nullable (T?): cheapest; use when absence is the only outcome and the
//     REASON doesn't matter. Here it conflates "not a number" and "negative".
//   - Result<T>: success or a Throwable; use to wrap throwing code functionally.
//     The failure is any exception — not enumerated.
//   - Typed sealed result: each failure mode is a named case the compiler forces
//     you to handle in an exhaustive `when`. Richest; use when the failures are
//     knowable and worth distinguishing. This is what the parser returns.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - parseQtyNullable: `raw.trim().toIntOrNull()?.takeIf { it >= 0 }`. takeIf
//   returns the receiver if the predicate holds, else null — perfect here.
//
// - parseQtyResult: `runCatching { val n = raw.trim().toInt(); require(n >= 0)
//   { "negative" }; n }`. `toInt()` throws on non-numbers; `require` throws
//   IllegalArgumentException on a negative. runCatching captures both.
//
// - Decompiling chargeAmount: look at the generated signature. You'll see the
//   `amount` parameter is a `long`, and the method name is mangled (e.g.
//   `chargeAmount-...`) to avoid JVM clashes. The mangling is interop hygiene,
//   not a cost. A Money? parameter, or List<Money>, would BOX instead.
//
// - If `assertEquals(user, account)` compiles for you, you typed both as the
//   same value class. They must be UserId and AccountId — distinct declarations.
//
// ----------------------------------------------------------------------------
