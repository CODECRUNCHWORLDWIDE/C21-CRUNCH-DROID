// Exercise 3 — A Play Integrity verdict that fails CLOSED and gracefully
//
// Goal: Request a Play Integrity token with a server-issued nonce, model the result
//       as a sealed type, and handle the failure cases (no Play Services, emulator,
//       network error) by failing CLOSED and GRACEFULLY — deny the gated action with
//       a clear user message and a documented fallback, NEVER silently allow
//       (fail-open) and NEVER crash. This is lecture 2's attestation flow and is
//       VERBATIM the capstone's chaos drill #3.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// Drops into your app module. Requesting a real token needs the app to be known to
// the Play Console (an internal-track build from Week 21) and a Google Cloud project.
// The KEY LEARNING here works WITHOUT that: run on an emulator WITHOUT Google Play
// Services and confirm your code fails CLOSED (denies, with a message + fallback),
// not open and not with a crash.
//
//   1. Add the Play Integrity dependency (below).
//   2. Wire the request flow: nonce -> requestIntegrityToken -> sealed result.
//   3. Handle Unavailable by failing closed gracefully (deny + message + fallback).
//   4. Run on an emulator WITHOUT Play Services -> confirm graceful denial.
//
// DEPENDENCIES (app/build.gradle.kts):
//   implementation("com.google.android.play:integrity:1.4.0")
//   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1") // .await()
//
// ACCEPTANCE CRITERIA
//
//   [ ] The token is requested with a NONCE (server-issued, single-use) for anti-replay.
//   [ ] The result is a sealed type; success carries the OPAQUE token to send to the
//       backend (NOT decoded on the client).
//   [ ] The Unavailable / failure case fails CLOSED gracefully: deny + clear message
//       + documented fallback + logged reason. No fail-open, no crash, no silent lockout.
//   [ ] You ran it on an emulator without Play Services and saw the graceful denial.
//   [ ] You can explain why the verdict MUST be decided on the backend, not the client.
//   [ ] Builds with 0 warnings.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.auth.integrity

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.tasks.await

// ----------------------------------------------------------------------------
// The result model. Success carries the OPAQUE token (for the backend). Failure
// is explicit so the UI can fail closed with a message — not a thrown exception
// that crashes, and not a silent `true`.
// ----------------------------------------------------------------------------

sealed interface AttestationResult {
    /** An opaque integrity token. Send to the backend to DECODE + DECIDE. Do NOT
     *  decode it on the client — a client-side verdict is worthless (see bottom). */
    data class Token(val value: String) : AttestationResult

    /** Integrity could not be confirmed: no Play Services, an emulator, a network
     *  error. The UI must fail CLOSED gracefully on this. */
    data class Unavailable(val reason: String) : AttestationResult
}

// ----------------------------------------------------------------------------
// The request. The NONCE comes from your backend (a fresh, single-use value) so
// the decoded token can be checked against it server-side (anti-replay).
// ----------------------------------------------------------------------------

suspend fun requestIntegrityToken(context: Context, serverNonce: String): AttestationResult =
    try {
        val manager = IntegrityManagerFactory.create(context)
        val response = manager.requestIntegrityToken(
            IntegrityTokenRequest.builder()
                .setNonce(serverNonce)        // binds the token to this request
                .build()
        ).await()                             // suspend over the Play Task
        AttestationResult.Token(response.token())   // opaque; backend decodes it
    } catch (e: Exception) {
        // No Play Services (AOSP / bare emulator), network failure, etc. FAIL CLOSED.
        AttestationResult.Unavailable(e.message ?: "integrity unavailable")
    }

// ----------------------------------------------------------------------------
// The sign-in gate. THIS is the fail-closed-gracefully behavior the capstone grades.
// ----------------------------------------------------------------------------

sealed interface SignInGate {
    /** Proceed: hand the opaque token to the backend, which decodes + DECIDES. */
    data class Proceed(val integrityToken: String) : SignInGate

    /** Deny, but GRACEFULLY: a clear user message and a documented fallback path,
     *  plus the reason logged for production monitoring. Never silent, never a crash. */
    data class Blocked(val userMessage: String, val fallback: Fallback, val loggedReason: String) : SignInGate
}

enum class Fallback { WebSignIn, ContactSupport, ReducedFunctionality }

suspend fun gateSignIn(context: Context, serverNonce: String): SignInGate =
    when (val result = requestIntegrityToken(context, serverNonce)) {
        is AttestationResult.Token ->
            // The backend still makes the final call; we just forward the token.
            SignInGate.Proceed(result.value)

        is AttestationResult.Unavailable ->
            // FAIL CLOSED + GRACEFULLY: deny, explain, offer a path, log it.
            SignInGate.Blocked(
                userMessage = "We couldn't verify your device. Sign-in requires Google " +
                    "Play Services. You can sign in on the web instead.",
                fallback = Fallback.WebSignIn,
                loggedReason = result.reason
            )
    }

// ----------------------------------------------------------------------------
// What the BACKEND does (pseudocode — it's the backend's job, shown for the contract):
//
//   POST /auth/sign-in { integrityToken }
//   server:
//     verdict = playIntegrityApi.decode(integrityToken)        // decode SERVER-SIDE
//     require(verdict.nonce == issuedNonce)                    // anti-replay
//     val ok = verdict.appRecognitionVerdict == PLAY_RECOGNIZED &&
//              verdict.deviceRecognitionVerdict.contains(MEETS_DEVICE_INTEGRITY)
//     if (ok) issueSession() else denyWithReason(verdict)
//
// The verdict is ONE signal the backend weighs with its other fraud checks — not a
// gate the client enforces.
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
// WHY THE VERDICT MUST BE DECIDED ON THE BACKEND (write it before reading):
//
//   The integrity token is OPAQUE and meant to be decoded server-side. If you decode
//   it on the client and trust the result, an attacker who controls the client just
//   forges a passing verdict — the whole point of attestation is to move the trust
//   decision OFF the device the attacker controls and ONTO a server they don't. A
//   client-side integrity boolean is worthless. The nonce ties the token to one
//   server-issued challenge so a captured token can't be replayed.
// ----------------------------------------------------------------------------
// WHY FAIL CLOSED, NOT OPEN (the four behaviors):
//
//   - Fail OPEN (allow on unavailable): WRONG. An attacker disables Play Services to
//     bypass attestation entirely. Defeats the control.
//   - CRASH on the no-Play-Services path: WRONG. User-hostile, no reason given.
//   - Fail closed SILENTLY (deny, no message): WRONG. User locked out, baffled.
//   - Fail closed GRACEFULLY (deny + message + fallback + log): RIGHT. The capstone
//     chaos drill #3 requires exactly this.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - .await() unresolved. Add kotlinx-coroutines-play-services; it provides the
//   Task<T>.await() bridge so you can suspend over Play's Task API.
//
// - On an emulator the request just hangs or errors. Expected without Play Services —
//   that's the case you're handling. Confirm your catch maps it to Unavailable and
//   the gate returns Blocked (graceful denial), NOT a crash and NOT Proceed.
//
// - Tempted to "just allow it" when integrity is unavailable to unblock testing.
//   That's fail-open — the exact bug this exercise exists to prevent. Keep it closed;
//   use a debug flag that's OFF in release if you must unblock local dev, and document it.
//
// - Tempted to decode the token on the client to "check the verdict". Don't — it's
//   opaque by design and a client verdict is forgeable. Forward it to the backend.
//
// - Real token requests fail with an API error. Play Integrity needs the app known to
//   the Play Console (internal-track build, Week 21) and a Cloud project with the API
//   enabled. The FAIL-CLOSED behavior, though, you can test fully without any of that
//   by running where Play Services is absent.
//
// ----------------------------------------------------------------------------
