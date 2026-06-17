// Exercise 3 — Expressions over statements: refactor Java-in-Kotlin to idiom
//
// Goal: Take a set of statement-style functions (the kind a Java developer
//       writes on day one of Kotlin) and refactor each into expression style,
//       proving with tests that behaviour is unchanged. The point is muscle
//       memory for the week's central idiom: reach for if/when/try as
//       expressions, kill the throwaway var, drop the redundant return.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// This is a JUNIT 5 test suite. Each "...Statement" function is the BEFORE (it
// works, but it's Java-in-Kotlin). You write the matching "...Expression"
// function as the AFTER. The tests run BOTH against the same inputs and assert
// they agree — so your refactor is correct only when every test passes.
//
//   1. Add under src/test/kotlin/com/crunch/lab/.
//   2. Implement each TODO-marked "...Expression" function in expression style.
//   3. Run `./gradlew test`. Green means your idiom matches the original
//      behaviour exactly.
//
// The "...Expression" stubs contain a single `// TODO N:` marker each. Replace
// the TODO body with the idiomatic expression-style implementation. Do NOT
// change the "...Statement" originals or the tests.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] All tests pass (each Expression function matches its Statement twin).
//   [ ] Every Expression function uses a single expression body (fun f() = ...)
//       or an expression (if/when/try) as its result — NO throwaway `var`, no
//       intermediate mutable accumulation where an operator pipeline fits.
//   [ ] You can point at each refactor and name the idiom you applied.
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package com.crunch.lab

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class ExpressionsOverStatementsTests {

    @Test
    fun `grade - expression matches statement`() {
        for (score in listOf(-5, 0, 59, 60, 69, 70, 79, 80, 89, 90, 100, 150)) {
            assertEquals(gradeStatement(score), gradeExpression(score))
        }
    }

    @Test
    fun `classifyHttp - expression matches statement`() {
        for (code in listOf(100, 200, 204, 301, 400, 404, 418, 500, 503, 600)) {
            assertEquals(classifyHttpStatement(code), classifyHttpExpression(code))
        }
    }

    @Test
    fun `parsePortOrDefault - expression matches statement`() {
        for (raw in listOf("8080", "443", "", "not-a-number", "  90 ", "-1")) {
            assertEquals(parsePortStatement(raw), parsePortExpression(raw))
        }
    }

    @Test
    fun `activeNamesUpper - expression matches statement`() {
        val users = listOf(
            User("ada", active = true),
            User("Linus", active = false),
            User("grace", active = true),
            User("dennis", active = false),
        )
        assertEquals(activeNamesUpperStatement(users), activeNamesUpperExpression(users))
    }

    @Test
    fun `firstEvenOrNull - expression matches statement`() {
        assertEquals(firstEvenStatement(listOf(1, 3, 4, 6)), firstEvenExpression(listOf(1, 3, 4, 6)))
        assertEquals(firstEvenStatement(listOf(1, 3, 5)), firstEvenExpression(listOf(1, 3, 5)))
        assertEquals(firstEvenStatement(emptyList()), firstEvenExpression(emptyList()))
    }
}

// ----------------------------------------------------------------------------
// BEFORE — statement style (Java-in-Kotlin). Do NOT modify these.
// ----------------------------------------------------------------------------

fun gradeStatement(score: Int): String {
    var grade: String
    if (score >= 90) {
        grade = "A"
    } else if (score >= 80) {
        grade = "B"
    } else if (score >= 70) {
        grade = "C"
    } else if (score >= 60) {
        grade = "D"
    } else {
        grade = "F"
    }
    return grade
}

fun classifyHttpStatement(code: Int): String {
    var result: String
    if (code in 100..199) {
        result = "informational"
    } else if (code in 200..299) {
        result = "success"
    } else if (code in 300..399) {
        result = "redirect"
    } else if (code in 400..499) {
        result = "client error"
    } else if (code in 500..599) {
        result = "server error"
    } else {
        result = "unknown"
    }
    return result
}

fun parsePortStatement(raw: String): Int {
    var port: Int
    try {
        port = raw.trim().toInt()
    } catch (e: NumberFormatException) {
        port = 8080
    }
    return port
}

fun activeNamesUpperStatement(users: List<User>): List<String> {
    val names = mutableListOf<String>()
    for (user in users) {
        if (user.active) {
            names.add(user.name.uppercase())
        }
    }
    return names
}

fun firstEvenStatement(numbers: List<Int>): Int? {
    for (n in numbers) {
        if (n % 2 == 0) {
            return n
        }
    }
    return null
}

// ----------------------------------------------------------------------------
// AFTER — expression style. Implement each (replace the TODO body).
// ----------------------------------------------------------------------------

fun gradeExpression(score: Int): String =
    // TODO 1: an if/else-if chain as an EXPRESSION (no var, no return).
    //         Hint: the last `else` makes the if exhaustive.
    TODO("implement gradeExpression in expression style")

fun classifyHttpExpression(code: Int): String =
    // TODO 2: a `when` over ranges as an expression: when (code) { in 100..199 -> ... }
    TODO("implement classifyHttpExpression with a when over ranges")

fun parsePortExpression(raw: String): Int =
    // TODO 3: a `try`/`catch` as an EXPRESSION assigned to the function result.
    TODO("implement parsePortExpression with try as an expression")

fun activeNamesUpperExpression(users: List<User>): List<String> =
    // TODO 4: a filter/map pipeline — no mutable list, no manual loop.
    TODO("implement activeNamesUpperExpression with filter + map")

fun firstEvenExpression(numbers: List<Int>): Int? =
    // TODO 5: a single collection operator that returns the first match or null.
    TODO("implement firstEvenExpression with firstOrNull")

// ----------------------------------------------------------------------------
// Supporting type
// ----------------------------------------------------------------------------

data class User(val name: String, val active: Boolean)

// ----------------------------------------------------------------------------
// THE IDIOMS YOU APPLIED (name them after you finish):
//
//   1. gradeExpression       — if/else-if as an expression (single-expression body)
//   2. classifyHttpExpression — when over ranges as an expression
//   3. parsePortExpression   — try/catch as an expression
//   4. activeNamesUpper...   — filter + map pipeline instead of loop + mutableList
//   5. firstEvenExpression   — firstOrNull { } instead of loop + early return
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - gradeExpression: `if (score >= 90) "A" else if (score >= 80) "B" else ...`
//   The whole if-chain is the value after the `=`. No braces needed.
//
// - classifyHttpExpression: `when (code) { in 100..199 -> "informational"; ...;
//   else -> "unknown" }`. `in` does a range membership check inside `when`.
//
// - parsePortExpression: `try { raw.trim().toInt() } catch (e: NumberFormatException)
//   { 8080 }` — the try-block's value (or the catch-block's) becomes the result.
//
// - activeNamesUpperExpression: `users.filter { it.active }.map { it.name.uppercase() }`.
//   Note the order matters only for clarity here; both give the same list.
//
// - firstEvenExpression: `numbers.firstOrNull { it % 2 == 0 }`. Returns the first
//   element matching the predicate, or null if none — exactly the loop's behaviour.
//
// - If a test fails, your expression and the statement original disagree on an
//   edge (a boundary like 90, or the empty list). Re-read the original's branch
//   order; expression refactors must preserve it exactly.
//
// ----------------------------------------------------------------------------
