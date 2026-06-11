// Exercise 2 — The NetworkResult sealed type + an exponential-backoff retry
//
// Goal: Model every network outcome as a typed case (Success / HttpError /
//       NetworkError / SerializationError), write a safeApiCall wrapper that maps
//       each exception flavour, and write a BOUNDED exponential-backoff retry that
//       retries ONLY retryable failures (5xx/timeout) and never a 4xx. Prove it
//       with a fake API that fails on cue, asserting on both the result type and
//       the number of attempts.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// This runs as a plain JVM test (`./gradlew test`) — no emulator, no network. A
// FakeApi lets each test script the sequence of outcomes (e.g. "fail twice with
// 503, then succeed") so the retry behaviour is deterministic. We use
// kotlinx-coroutines-test so delays are virtual (the test doesn't actually wait
// seconds for the backoff).
//
//   1. Add this file to src/test/kotlin.
//   2. Run with `./gradlew test` or the green arrow.
//   3. The assertions enforce: correct result type, correct attempt count, and
//      that a 4xx is NOT retried.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] All tests pass.
//   [ ] You can explain, in one sentence each: why a 4xx must not be retried, and
//       why jitter matters for the backoff.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.net.week15

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ----------------------------------------------------------------------------
// The failure model: every outcome is a typed case the caller MUST handle.
// ----------------------------------------------------------------------------

sealed interface NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>
    data class HttpError(val code: Int, val body: String? = null) : NetworkResult<Nothing>
    data class NetworkError(val cause: IOException) : NetworkResult<Nothing>
    data class SerializationError(val cause: Throwable) : NetworkResult<Nothing>
}

// Stand-in exceptions (in a real app these are retrofit2.HttpException, etc.).
class FakeHttpException(val code: Int) : Exception("HTTP $code")
class FakeSerializationException(message: String) : Exception(message)

// ----------------------------------------------------------------------------
// safeApiCall: run a suspend call, map each failure flavour to a typed case.
// Catch order: most specific first (HTTP, serialization) before IOException.
// ----------------------------------------------------------------------------

suspend fun <T> safeApiCall(block: suspend () -> T): NetworkResult<T> =
    try {
        NetworkResult.Success(block())
    } catch (e: FakeHttpException) {
        NetworkResult.HttpError(e.code)
    } catch (e: FakeSerializationException) {
        NetworkResult.SerializationError(e)
    } catch (e: IOException) {
        NetworkResult.NetworkError(e)
    }

// ----------------------------------------------------------------------------
// The retryable/non-retryable distinction: retry transient server errors and
// network blips, NEVER a client (4xx) error or a parse error.
// ----------------------------------------------------------------------------

fun NetworkResult<*>.isRetryable(): Boolean = when (this) {
    is NetworkResult.NetworkError -> true
    is NetworkResult.HttpError -> code == 429 || code in 500..599
    else -> false      // Success, 4xx, SerializationError: don't retry
}

// ----------------------------------------------------------------------------
// Bounded exponential backoff with jitter. Returns after maxAttempts.
// ----------------------------------------------------------------------------

suspend fun <T> retrying(
    maxAttempts: Int = 4,
    baseDelayMs: Long = 500,
    block: suspend () -> NetworkResult<T>
): Pair<NetworkResult<T>, Int> {        // returns (result, attemptCount) for testing
    var attempt = 0
    while (true) {
        val result = block()
        attempt++
        if (!result.isRetryable() || attempt >= maxAttempts) return result to attempt
        val backoff = baseDelayMs * (1L shl (attempt - 1))     // 500, 1000, 2000...
        val jitter = Random.nextLong(backoff / 2)               // desync clients
        delay(backoff + jitter)
    }
}

// ----------------------------------------------------------------------------
// A fake API that plays back a scripted sequence of outcomes, counting calls.
// ----------------------------------------------------------------------------

class FakeApi(private val outcomes: List<() -> String>) {
    var callCount = 0; private set
    suspend fun fetch(): String {
        val index = callCount.coerceAtMost(outcomes.lastIndex)
        callCount++
        return outcomes[index]()       // each entry either returns a value or throws
    }
}

// ----------------------------------------------------------------------------
// Tests
// ----------------------------------------------------------------------------

class NetworkResultAndRetryTest {

    @Test
    fun success_maps_to_Success_and_does_not_retry() = runTest {
        val api = FakeApi(listOf({ "ok" }))
        val (result, attempts) = retrying { safeApiCall { api.fetch() } }
        assertEquals(NetworkResult.Success("ok"), result)
        assertEquals(1, attempts)        // success on the first try; no retry
    }

    @Test
    fun transient_503_then_success_retries_then_succeeds() = runTest {
        val api = FakeApi(listOf(
            { throw FakeHttpException(503) },     // attempt 1: retryable
            { throw FakeHttpException(503) },     // attempt 2: retryable
            { "recovered" }                        // attempt 3: success
        ))
        val (result, attempts) = retrying(maxAttempts = 4) { safeApiCall { api.fetch() } }
        assertEquals(NetworkResult.Success("recovered"), result)
        assertEquals(3, attempts)        // two failures, then success
    }

    @Test
    fun client_400_is_NOT_retried() = runTest {
        val api = FakeApi(listOf({ throw FakeHttpException(400) }))
        val (result, attempts) = retrying(maxAttempts = 4) { safeApiCall { api.fetch() } }
        assertTrue(result is NetworkResult.HttpError && result.code == 400)
        assertEquals(1, attempts)        // a 400 is the client's fault; retrying is pure waste
    }

    @Test
    fun persistent_500_exhausts_the_bound_then_fails() = runTest {
        val api = FakeApi(listOf({ throw FakeHttpException(500) }))   // always 500
        val (result, attempts) = retrying(maxAttempts = 4) { safeApiCall { api.fetch() } }
        assertTrue(result is NetworkResult.HttpError && result.code == 500)
        assertEquals(4, attempts)        // bounded: stops after maxAttempts, returns the failure
    }

    @Test
    fun network_io_error_is_retried() = runTest {
        val api = FakeApi(listOf(
            { throw IOException("timeout") },
            { "ok-after-blip" }
        ))
        val (result, attempts) = retrying { safeApiCall { api.fetch() } }
        assertEquals(NetworkResult.Success("ok-after-blip"), result)
        assertEquals(2, attempts)
    }

    @Test
    fun malformed_body_is_SerializationError_and_not_retried() = runTest {
        val api = FakeApi(listOf({ throw FakeSerializationException("bad json") }))
        val (result, attempts) = retrying { safeApiCall { api.fetch() } }
        assertTrue(result is NetworkResult.SerializationError)
        assertEquals(1, attempts)        // a parse error won't fix itself on retry
    }
}

// ----------------------------------------------------------------------------
// WHY a 4xx must not be retried, and WHY jitter matters (write before reading):
//
//   A 4xx is a CLIENT error — the request itself is wrong (bad params, missing
//   auth, not found). Replaying the identical request will fail identically
//   forever; retrying it is pure waste and, for 401, an infinite lockout loop.
//   Only transient SERVER/network failures (5xx, 429, timeout) can succeed on
//   retry.
//
//   Jitter (randomness in the backoff) matters because without it, a thousand
//   clients that all failed at the same instant all retry at the same instant —
//   a synchronized "thundering herd" that re-creates the overload it was waiting
//   out. Random jitter spreads the retries so the server isn't hit by a wall.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - runTest gives you a virtual clock: delay() returns immediately in the test,
//   so the exponential backoff doesn't make the test actually wait seconds. This
//   is why you can test a 4-attempt backoff in milliseconds.
//
// - The catch order in safeApiCall matters: put FakeHttpException and
//   FakeSerializationException BEFORE IOException, or a more general catch could
//   swallow the specific one. Most specific first.
//
// - isRetryable returns false for Success, every 4xx, and SerializationError.
//   It's the single decision point — if a test retries something it shouldn't,
//   the bug is here.
//
// - Returning (result, attemptCount) from retrying is a test affordance. In
//   production you'd return just the result; the count is so the tests can assert
//   "tried exactly N times".
//
// ----------------------------------------------------------------------------
