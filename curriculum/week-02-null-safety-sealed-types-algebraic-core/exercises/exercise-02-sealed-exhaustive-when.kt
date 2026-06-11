// Exercise 2 — Sealed types and the exhaustive `when` the compiler enforces
//
// Goal: Model a closed domain as a sealed hierarchy, consume it with else-free
//       exhaustive `when`s, and PROVE the week's central property: adding a new
//       case turns every incomplete `when` into a compile error, so the compiler
//       walks you to every place that needs updating. This is "the compiler is
//       your first reviewer" made concrete.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// This is a JUNIT 5 test suite. Drop it under src/test/kotlin/com/crunch/sealed/
// in a Kotlin/JVM Gradle project and run with `./gradlew test`. Pure JVM.
//
//   1. Implement the two TODO-marked consumers (else-free exhaustive `when`).
//   2. Run the tests — green proves your consumers handle every case.
//   3. Do the "BREAK THE BUILD" experiment at the bottom: add a 4th case and
//      watch every `when` fail to compile until you handle it. That failure IS
//      the lesson.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] All tests pass.
//   [ ] Your two consumers are exhaustive WITH NO `else` branch.
//   [ ] You did the break-the-build experiment and can describe what the
//       compiler told you and at how many sites.
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package com.crunch.sealed

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

// ----------------------------------------------------------------------------
// The domain: the result of an HTTP-ish network call. A closed set of cases,
// each carrying exactly the data valid for it.
// ----------------------------------------------------------------------------

sealed interface NetworkResult {
    data class Success(val body: String) : NetworkResult
    data class Failure(val code: Int, val message: String) : NetworkResult
    data object Loading : NetworkResult
}

// ----------------------------------------------------------------------------
// Consumer 1 — describe a result as a human string. EXHAUSTIVE, no `else`.
// ----------------------------------------------------------------------------

fun describe(result: NetworkResult): String =
    // TODO 1: an exhaustive `when (result)` with one branch per case and NO else.
    //         In `is Success`, result is smart-cast — use result.body.
    //         In `is Failure`, use result.code and result.message.
    TODO("implement describe with an exhaustive when (no else)")

// ----------------------------------------------------------------------------
// Consumer 2 — map a result to an HTTP-style status int: Success->200,
// Failure->its code, Loading->0. EXHAUSTIVE, no `else`.
// ----------------------------------------------------------------------------

fun statusCode(result: NetworkResult): Int =
    // TODO 2: an exhaustive `when (result)` returning the int for each case.
    TODO("implement statusCode with an exhaustive when (no else)")

// ----------------------------------------------------------------------------
// A second sealed hierarchy: a sum type that is ALSO an algebraic model. A user
// session is exactly one of these. Note Anonymous carries no data (data object).
// ----------------------------------------------------------------------------

sealed interface Session {
    data object Anonymous : Session
    data class LoggedIn(val userName: String, val isAdmin: Boolean) : Session
    data class Expired(val lastSeenEpoch: Long) : Session
}

// Consumer: can this session perform an admin action? Exhaustive, no else.
fun canAdminister(session: Session): Boolean = when (session) {
    Session.Anonymous     -> false
    is Session.LoggedIn   -> session.isAdmin     // smart-cast to LoggedIn
    is Session.Expired    -> false
}

// ----------------------------------------------------------------------------
// Tests
// ----------------------------------------------------------------------------

class SealedExhaustiveWhenTests {

    @Test
    fun `describe covers every NetworkResult case`() {
        assertEquals("Got 5 bytes", describe(NetworkResult.Success("hello")))
        assertEquals("Error 404: Not Found", describe(NetworkResult.Failure(404, "Not Found")))
        assertEquals("Loading…", describe(NetworkResult.Loading))
    }

    @Test
    fun `statusCode maps each case to an int`() {
        assertEquals(200, statusCode(NetworkResult.Success("x")))
        assertEquals(503, statusCode(NetworkResult.Failure(503, "Unavailable")))
        assertEquals(0, statusCode(NetworkResult.Loading))
    }

    @Test
    fun `canAdminister respects the session sum type`() {
        assertEquals(false, canAdminister(Session.Anonymous))
        assertEquals(true, canAdminister(Session.LoggedIn("root", isAdmin = true)))
        assertEquals(false, canAdminister(Session.LoggedIn("ada", isAdmin = false)))
        assertEquals(false, canAdminister(Session.Expired(0L)))
    }

    @Test
    fun `data object equality and identity`() {
        // A data object is a singleton: there is exactly one Loading.
        assertEquals(NetworkResult.Loading, NetworkResult.Loading)
        // Two equal data classes are structurally equal (Week 1).
        assertEquals(NetworkResult.Success("a"), NetworkResult.Success("a"))
    }
}

// ----------------------------------------------------------------------------
// THE BREAK-THE-BUILD EXPERIMENT (do this, then undo it):
//
//   1. Add a fourth case to NetworkResult:
//          data class Cancelled(val reason: String) : NetworkResult
//   2. Recompile (`./gradlew test`). BOTH `describe` and `statusCode` now fail:
//          "'when' expression must be exhaustive, add necessary 'Cancelled'
//           branch or 'else' branch instead."
//   3. The compiler just listed EVERY consumer that forgot the new case — for
//      free, at compile time. Add the Cancelled branches, recompile, green.
//   4. Now imagine you'd written `else -> ...` instead: adding Cancelled would
//      have SILENTLY fallen into else, shipping a bug with no error. THAT is why
//      sealed + exhaustive-when + no-else is the most valuable correctness
//      property in the language for state modelling.
//   5. Undo the Cancelled case so the file matches the acceptance criteria.
//
// Write 2-3 sentences in notes/exhaustiveness.md about what the compiler told
// you and why the no-else discipline matters.
//
// ----------------------------------------------------------------------------
// WHY (write it before reading):
//
//   A sealed hierarchy is a CLOSED set the compiler knows entirely, so it can
//   verify a `when` covers every case without an `else`. Drop the `else` and the
//   compiler enforces completeness AND walks you to every incomplete site when
//   you add a case — turning a whole class of "forgot to handle the new state"
//   production bugs into compile errors.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - describe: `when (result) { is NetworkResult.Success -> "Got ${result.body.length}
//   bytes"; is NetworkResult.Failure -> "Error ${result.code}: ${result.message}";
//   NetworkResult.Loading -> "Loading…" }`. Note the data OBJECT case matches by
//   value (no `is`), the data CLASS cases match with `is` and smart-cast.
//
// - statusCode: same shape, returning 200 / result.code / 0.
//
// - If the compiler demands an `else` even though you covered all cases, you
//   probably used `when` as a STATEMENT, not an expression. Assign the result
//   (`= when ...`) or return it so it's an expression — only then is
//   exhaustiveness checked (and only then can you omit `else`).
//
// - "Got 5 bytes" expects result.body.length, not the body itself. Read the test.
//
// ----------------------------------------------------------------------------
