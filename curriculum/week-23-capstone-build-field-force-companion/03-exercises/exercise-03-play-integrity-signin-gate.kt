// Exercise 3 — The Play Integrity sign-in gate: three outcomes, no brick, no fail-open
//
// Goal: Implement the attestation gate as a SEALED result with exactly three
//       outcomes — Attested, Failed(with a user message), and PlayServicesUnavailable —
//       store the token in a Keystore-backed store (faked for the JVM test), and prove
//       the gate NEVER fails open (an error is not success) and NEVER bricks (a
//       Play-Services-less device gets a documented fallback, not a hang).
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// This runs as a PLAIN JVM unit test. We fake the Play Integrity client and the
// Keystore store so the test pins the FAILURE-DESIGN contract that next week's
// chaos drill C exercises on a real Play-Services-less emulator. In :feature-auth
// you swap the fakes for the real IntegrityManager + a real EncryptedFile/Keystore
// store; the three-outcome contract this test enforces stays identical.
//
//   1. Put the sealed result + PlayIntegrityGate in :feature-auth (or a JVM module).
//   2. Put this test in the test source set and run it.
//
// ACCEPTANCE CRITERIA
//
//   [ ] attest() returns a SEALED result with three outcomes; the `when` is exhaustive.
//   [ ] On a token error, the result is Failed with a non-empty user message — NOT
//       Attested. (No fail-open.)
//   [ ] On no Play Services, the result is PlayServicesUnavailable with a fallback —
//       NOT a thrown exception, NOT a hang. (No brick.)
//   [ ] On success, the token is stored in the (faked) Keystore store.
//   [ ] A failed/unavailable attestation stores NOTHING.
//   [ ] Builds with 0 warnings; the test passes.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.fieldforce.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

// ----------------------------------------------------------------------------
// THE RESULT — a sealed interface so the UI MUST handle every outcome (exhaustive
// `when`). A boolean can't carry the user message or distinguish the no-Play-Services
// case, which is exactly why we don't use one (Lecture 2 §2).
// ----------------------------------------------------------------------------

@JvmInline
value class AttestationToken(val value: String)

enum class AttestationFailure { NetworkError, VerdictRejected, Timeout }

sealed interface AttestationResult {
    data class Attested(val token: AttestationToken) : AttestationResult
    data class Failed(val reason: AttestationFailure, val userMessage: String) : AttestationResult
    data object PlayServicesUnavailable : AttestationResult
}

// ----------------------------------------------------------------------------
// SEAMS — the real client wraps IntegrityManager; the real store is Keystore-backed.
// ----------------------------------------------------------------------------

interface IntegrityClient {
    fun isPlayServicesAvailable(): Boolean
    /** Throws on any attestation error; the gate translates the throw into Failed. */
    suspend fun requestToken(nonce: String): AttestationToken
}

interface TokenStore {                       // Keystore-backed in :feature-auth
    suspend fun put(token: AttestationToken)
    suspend fun get(): AttestationToken?
}

// Translate a transport/verdict error into a typed failure reason.
private fun Throwable.toAttestationFailure(): AttestationFailure = when (this) {
    is java.net.SocketTimeoutException -> AttestationFailure.Timeout
    is java.io.IOException -> AttestationFailure.NetworkError
    else -> AttestationFailure.VerdictRejected
}

// ----------------------------------------------------------------------------
// THE GATE — the failure design lives here. Lecture 2 §2.
// TODO 1: implement attest() so it:
//   (a) returns PlayServicesUnavailable (NOT a throw) when Play Services is absent,
//   (b) returns Attested + stores the token on success,
//   (c) returns Failed(reason, userMessage) on any error — NEVER Attested.
// ----------------------------------------------------------------------------

class PlayIntegrityGate(
    private val client: IntegrityClient,
    private val tokenStore: TokenStore,
) {
    suspend fun attest(nonce: String): AttestationResult {
        // TODO 1a: a Play-Services-less device cannot attest. Surface the limitation
        //          with a fallback — do NOT fail open, do NOT throw.
        if (!client.isPlayServicesAvailable()) {
            return AttestationResult.PlayServicesUnavailable
        }
        // TODO 1b/1c: request the token; success stores + Attested, error -> Failed.
        return runCatching { client.requestToken(nonce) }
            .fold(
                onSuccess = { token ->
                    tokenStore.put(token)
                    AttestationResult.Attested(token)
                },
                onFailure = { e ->
                    AttestationResult.Failed(
                        reason = e.toAttestationFailure(),
                        userMessage = "We couldn't verify this device. Check your connection " +
                            "and try again, or sign in on a managed device.",
                    )
                },
            )
    }
}

// How the UI consumes it — exhaustive, the compiler enforces all three branches.
fun describeForUi(result: AttestationResult): String = when (result) {
    is AttestationResult.Attested -> "signed in"
    is AttestationResult.Failed -> "error: ${result.userMessage}"
    AttestationResult.PlayServicesUnavailable -> "fallback: use web sign-in or a managed device"
}

// ----------------------------------------------------------------------------
// FAKES
// ----------------------------------------------------------------------------

class FakeIntegrityClient(
    private val playServices: Boolean,
    private val tokenOrError: Result<AttestationToken>,
) : IntegrityClient {
    override fun isPlayServicesAvailable(): Boolean = playServices
    override suspend fun requestToken(nonce: String): AttestationToken =
        tokenOrError.getOrThrow()
}

class FakeTokenStore : TokenStore {
    private var stored: AttestationToken? = null
    override suspend fun put(token: AttestationToken) { stored = token }
    override suspend fun get(): AttestationToken? = stored
}

// ----------------------------------------------------------------------------
// THE TEST — pins the failure-design contract chaos drill C will exercise live.
// ----------------------------------------------------------------------------

class PlayIntegrityGateTest {

    @Test
    fun `success attests and stores the token`() = runTest {
        val store = FakeTokenStore()
        val gate = PlayIntegrityGate(
            client = FakeIntegrityClient(
                playServices = true,
                tokenOrError = Result.success(AttestationToken("verdict-ok")),
            ),
            tokenStore = store,
        )

        val result = gate.attest(nonce = "n1")

        val attested = assertIs<AttestationResult.Attested>(result)
        assertEquals("verdict-ok", attested.token.value)
        assertEquals(AttestationToken("verdict-ok"), store.get())   // stored in Keystore store
    }

    @Test
    fun `an error returns Failed with a message — it must NOT fail open`() = runTest {
        val store = FakeTokenStore()
        val gate = PlayIntegrityGate(
            client = FakeIntegrityClient(
                playServices = true,
                tokenOrError = Result.failure(java.io.IOException("no network")),
            ),
            tokenStore = store,
        )

        val result = gate.attest(nonce = "n1")

        val failed = assertIs<AttestationResult.Failed>(result)    // NOT Attested
        assertEquals(AttestationFailure.NetworkError, failed.reason)
        assertTrue(failed.userMessage.isNotBlank(), "the user needs a clear message")
        assertNull(store.get(), "a failed attestation must store nothing")
    }

    @Test
    fun `no Play Services returns a fallback — it must NOT brick`() = runTest {
        val store = FakeTokenStore()
        val gate = PlayIntegrityGate(
            client = FakeIntegrityClient(
                playServices = false,                              // the drill-C device
                tokenOrError = Result.success(AttestationToken("unused")),
            ),
            tokenStore = store,
        )

        // a brick would throw or hang; this returns a handled outcome instead.
        val result = gate.attest(nonce = "n1")

        assertEquals(AttestationResult.PlayServicesUnavailable, result)
        assertNull(store.get(), "no token without attestation")
        assertTrue(describeForUi(result).contains("fallback"))     // a documented path exists
    }
}

// ----------------------------------------------------------------------------
// WHY this design (write it before reading):
//
//   - Fail-open (treating an error as success) makes attestation a control that's
//     OFF whenever it's inconvenient — worse than no control, because it lies. The
//     "must NOT fail open" test pins that an error is Failed, never Attested.
//   - Hard-brick (throwing/hanging when Play Services is absent) takes down every
//     legitimate user on a de-Googled, enterprise, or emulator device. Chaos drill C
//     runs the app on exactly such a device next week; PlayServicesUnavailable +
//     a fallback is what passes it. The "must NOT brick" test pins that.
//   - The sealed result forces the UI's `when` to handle all three, so a new outcome
//     can't be silently dropped.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - "fail open" test fails (got Attested)? Your runCatching is catching the error
//   but the onFailure branch returns Attested or rethrows. onFailure must return
//   Failed(...). Never map an error to success.
//
// - "no brick" test throws? You called requestToken before checking
//   isPlayServicesAvailable(), so the fake's success token is irrelevant and some
//   other path threw — or you didn't early-return PlayServicesUnavailable. Check
//   Play Services FIRST, before any token request.
//
// - assertIs unresolved? It's in kotlin.test — import kotlin.test.assertIs.
//
// - In :feature-auth, the real isPlayServicesAvailable() is
//   GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
//   ConnectionResult.SUCCESS, and requestToken wraps StandardIntegrityManager.
//   The TokenStore is an EncryptedFile / Keystore-backed store (Week 22). The
//   contract above is what those real pieces must satisfy.
//
// ----------------------------------------------------------------------------
