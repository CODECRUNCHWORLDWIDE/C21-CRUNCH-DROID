// Exercise 3 — The FCM token-rotation chaos drill (drill B): no message dropped
//
// Goal: Drive the FCM token rotation deterministically: onNewToken fires, the app
//       re-registers the new token with the backend through a RETRYABLE path, and a
//       message sent during the re-registration window is NOT silently dropped. Prove
//       (a) a re-registration that fails on a flaky network RETRIES (doesn't drop the
//       token), and (b) once re-registered, the backend sends to the NEW token, so no
//       message is lost.
//
// Estimated time: 55 minutes.
//
// HOW TO USE THIS FILE
//
// This runs as a PLAIN JVM unit test (no emulator, no Firebase). It models the
// device's onNewToken callback, a flaky re-registration path, the backend's token
// record, and the message pipeline. The LIVE drill in the mini-project rotates a real
// token (FirebaseMessaging.deleteToken()) and sends a real push during the window;
// this test pins the CONTRACT — a failed registration must retry, not drop — that the
// live drill checks against.
//
// ACCEPTANCE CRITERIA
//
//   [ ] onNewToken enqueues a re-registration through a RETRYABLE path (not a single
//       fire-and-forget call).
//   [ ] A re-registration that fails the first time RETRIES and eventually lands.
//   [ ] After re-registration, the backend sends to the NEW token; messages to the
//       OLD token after rotation are NOT delivered (and were re-targeted, not dropped).
//   [ ] A message sent DURING the window is delivered after re-registration (queued/
//       retried), never silently dropped.
//   [ ] Builds with 0 warnings; the tests pass.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.fieldforce.chaos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

// ----------------------------------------------------------------------------
// THE BACKEND — holds the device's current token and a queue of messages it could
// not deliver yet (because re-registration hadn't landed). Delivering drains the
// queue to whatever token is currently registered.
// ----------------------------------------------------------------------------

class Backend {
    var registeredToken: String? = null
        private set
    private val pending = ArrayDeque<String>()        // messages awaiting a valid token
    val delivered = mutableListOf<Pair<String, String>>()   // (token, message)

    fun registerToken(token: String) {
        registeredToken = token
        flush()                                       // a newly-registered token drains the queue
    }

    /** Send a message. If we have a token, deliver to it; if not (mid-rotation),
     *  QUEUE it — never drop it. Queuing is what makes "no message dropped" true. */
    fun send(message: String) {
        val token = registeredToken
        if (token == null) pending += message else delivered += token to message
    }

    private fun flush() {
        val token = registeredToken ?: return
        while (pending.isNotEmpty()) delivered += token to pending.removeFirst()
    }
}

// ----------------------------------------------------------------------------
// THE RETRYABLE RE-REGISTRATION PATH — models routing onNewToken through the same
// durable outbox/WorkManager path as data writes (Lecture 2 §2). A naive single call
// that drops on failure is the bug; this retries with bounded attempts.
// ----------------------------------------------------------------------------

class Registrar(
    private val backend: Backend,
    private val maxAttempts: Int = 5,
) {
    // a flaky transport: fails the first `failTimes` attempts, then succeeds.
    var failTimes = 0
    var attemptsMade = 0
        private set

    /** Enqueue a re-registration. Returns true if it eventually landed within the
     *  attempt budget. The retry loop is the contract: a failure must NOT drop the
     *  token. */
    fun reRegister(token: String): Boolean {
        repeat(maxAttempts) { i ->
            attemptsMade = i + 1
            val failsThisTime = i < failTimes
            if (!failsThisTime) {
                backend.registerToken(token)          // landed
                return true
            }
            // else: transient failure — loop and retry (WorkManager backoff in prod).
        }
        return false                                  // exhausted the budget (poison)
    }
}

// ----------------------------------------------------------------------------
// THE DEVICE — its onNewToken callback routes to the retryable registrar. The naive
// version (commented) is the footgun this drill exists to catch.
// ----------------------------------------------------------------------------

class Device(private val registrar: Registrar) {
    var currentToken: String = "token-old"
        private set

    /** FCM calls this when the token rotates. The CORRECT impl routes to the
     *  retryable registrar so a network blip during rotation doesn't drop the token.
     *
     *  THE FOOTGUN (do NOT do this):
     *    fun onNewToken(token: String) { backend.registerOnce(token) }  // drops on failure
     */
    fun onNewToken(token: String): Boolean {
        currentToken = token
        return registrar.reRegister(token)            // retryable path
    }
}

// ----------------------------------------------------------------------------
// THE DRILL TESTS
// ----------------------------------------------------------------------------

class FcmTokenRotationDrillTest {

    @Test
    fun `happy path — rotation re-registers and the backend uses the new token`() {
        val backend = Backend().apply { registerToken("token-old") }
        val registrar = Registrar(backend)
        val device = Device(registrar)

        val landed = device.onNewToken("token-new")

        assertTrue(landed)
        assertEquals("token-new", backend.registeredToken)
        backend.send("dispatch d1 updated")
        // delivered to the NEW token, not the old.
        assertEquals(listOf("token-new" to "dispatch d1 updated"), backend.delivered)
    }

    @Test
    fun `a re-registration that fails first RETRIES — the token is not dropped`() {
        val backend = Backend().apply { registerToken("token-old") }
        val registrar = Registrar(backend).apply { failTimes = 3 }   // flaky network
        val device = Device(registrar)

        val landed = device.onNewToken("token-new")

        assertTrue(landed, "a transient failure must retry, not drop the token")
        assertEquals(4, registrar.attemptsMade)        // 3 failures + 1 success
        assertEquals("token-new", backend.registeredToken)
    }

    @Test
    fun `a message sent DURING the window is delivered, never silently dropped`() {
        val backend = Backend()                        // no token yet — mid-rotation
        val registrar = Registrar(backend).apply { failTimes = 2 }
        val device = Device(registrar)

        // a push arrives WHILE we have no registered token (the rotation window).
        backend.send("urgent: dispatch d9 reassigned")
        assertTrue(backend.delivered.isEmpty(), "no token yet — queued, not dropped")

        // re-registration eventually lands (after retries) and the queue flushes.
        val landed = device.onNewToken("token-new")
        assertTrue(landed)
        assertEquals(
            listOf("token-new" to "urgent: dispatch d9 reassigned"),
            backend.delivered,
            "the windowed message is delivered to the new token after re-registration",
        )
    }

    @Test
    fun `the naive single-attempt path WOULD drop the token (the footgun, shown)`() {
        // Demonstrate the bug the retry fixes: with a budget of 1 and a failure,
        // the token never lands and EVERY later message goes to the old (now dead)
        // token or is dropped. This is what NOT routing through a retryable path costs.
        val backend = Backend().apply { registerToken("token-old") }
        val registrar = Registrar(backend, maxAttempts = 1).apply { failTimes = 1 }
        val device = Device(registrar)

        val landed = device.onNewToken("token-new")

        assertFalse(landed, "single attempt + a failure = token dropped (the footgun)")
        assertEquals("token-old", backend.registeredToken)   // still the dead token
        // the postmortem's root cause: re-registration had no enforced retry.
    }
}

// ----------------------------------------------------------------------------
// WHAT THE DRILL PROVES (and the postmortem documents):
//
//   - THE FOOTGUN (Lecture 2 §2): the naive onNewToken makes a single non-retryable
//     call. If it fails (and rotation often coincides with flaky connectivity), the
//     backend never learns the new token and EVERY subsequent push is dropped until
//     the next rotation — a silent, total push outage. The last test shows it.
//   - THE FIX: route re-registration through the same durable, retryable path as data
//     writes (the outbox + WorkManager backoff). A transient failure retries; the
//     token lands. The second test pins that.
//   - NO MESSAGE DROPPED: a message sent during the window is QUEUED (not dropped) and
//     flushed to the new token once re-registration lands. The third test pins that.
//   - THE POSTMORTEM SURPRISE: the valuable finding is usually "we had no DETECTION —
//     we'd have only learned push was dead from a user complaint." Action item: a
//     re-registration success metric / a synthetic push prober.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - The retry test fails (landed == false)? Your reRegister returns on the first
//   failure instead of looping. The repeat(maxAttempts) loop must CONTINUE on a
//   transient failure and only return true on success (or false after exhausting).
//
// - The windowed-message test drops the message? Backend.send must QUEUE when
//   registeredToken is null, and registerToken must flush the queue. Dropping a
//   message when there's no token is exactly the bug the drill catches.
//
// - "Why model a queue on the backend?" Because a real push backend either queues
//   undeliverable messages briefly or the SENDER retries. The contract is "no SILENT
//   drop" — a bounded queue/retry satisfies it; a fire-and-forget that vanishes does
//   not.
//
// - Real capstone: onNewToken is FirebaseMessagingService.onNewToken; the retryable
//   path is a WorkManager OneTimeWorkRequest with BackoffPolicy.EXPONENTIAL that
//   posts the token to your gRPC backend. The contract above is what that must satisfy.
//
// ----------------------------------------------------------------------------
