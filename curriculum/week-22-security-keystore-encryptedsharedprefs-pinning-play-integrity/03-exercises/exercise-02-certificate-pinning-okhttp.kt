// Exercise 2 — Certificate pinning in OkHttp: prove a MITM proxy is refused
//
// Goal: Pin a server's certificate with OkHttp's CertificatePinner, route the app
//       through a MITM proxy (mitmproxy) with the proxy's CA installed, and PROVE
//       the connection is REFUSED — where an UNPINNED client's "encrypted" traffic
//       was readable by the proxy. Then add a rotation-safe backup pin. This is
//       lecture 2's core claim made concrete: pinning narrows TLS trust from "any
//       CA" to "my key", and a forged cert (the proxy's) fails the pin.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// This drops into your app module (the Week-15 weather client is the natural host).
// You need mitmproxy (`brew install mitmproxy` / `pip install mitmproxy`) and an
// emulator routed through it. The proof is in the proxy window: before pinning, you
// SEE the requests; after pinning, the app throws SSLPeerUnverifiedException and the
// proxy sees a failed handshake.
//
//   1. Start mitmproxy; install its CA on the emulator (mitm.it after routing).
//   2. Run the UNPINNED client; confirm the proxy READS your traffic. (The before.)
//   3. Add the CertificatePinner (below); rerun; confirm the connection is REFUSED.
//   4. Add a backup pin and explain the rotation strategy.
//
// DEPENDENCIES: OkHttp 4.12+ (you already have it from Week 15).
//
// ACCEPTANCE CRITERIA
//
//   [ ] An UNPINNED client whose traffic the proxy can read (the demonstrated risk).
//   [ ] A pinned client (CertificatePinner) that THROWS / refuses to connect through
//       the proxy, because the proxy's forged cert doesn't match the pin.
//   [ ] A BACKUP pin is included, and you can explain the rotation-safe strategy.
//   [ ] You pin the RIGHT cert (intermediate or a stable key), not blindly the leaf.
//   [ ] Builds with 0 warnings.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.weather.security

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// ----------------------------------------------------------------------------
// STEP 1 — Compute the SPKI SHA-256 pin from the REAL server certificate.
// Run this in a terminal against your API host (NOT against the proxy!):
//
//   openssl s_client -connect api.open-meteo.com:443 -servername api.open-meteo.com \
//     </dev/null 2>/dev/null \
//     | openssl x509 -pubkey -noout \
//     | openssl pkey -pubin -outform der \
//     | openssl dgst -sha256 -binary | base64
//
// Better: do the same against the INTERMEDIATE cert in the chain (more rotation-safe).
// Put the resulting "sha256/..." values into the pinner below.
//
// You can ALSO let OkHttp tell you: connect once with a deliberately-wrong pin and
// OkHttp's SSLPeerUnverifiedException message PRINTS the actual pins it saw. Copy
// the right one from there (a common, pragmatic way to get the pin).
// ----------------------------------------------------------------------------

private const val API_HOST = "api.open-meteo.com"   // your weather API host

// ----------------------------------------------------------------------------
// BEFORE — an UNPINNED client. Through a MITM proxy with the proxy's CA installed,
// this client TRUSTS the proxy's forged certificate and the proxy reads everything.
// ----------------------------------------------------------------------------

fun unpinnedClient(): OkHttpClient =
    OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
// Run a request through this with mitmproxy intercepting -> the proxy SHOWS the
// request/response. That's the vulnerability: a user-installed CA (the proxy's)
// lets an attacker read your "encrypted" traffic.

// ----------------------------------------------------------------------------
// AFTER — a PINNED client. The CertificatePinner rejects ANY cert chain that
// doesn't match a pin, including the proxy's forged (but CA-valid) cert.
// ----------------------------------------------------------------------------

fun pinnedClient(): OkHttpClient {
    val pinner = CertificatePinner.Builder()
        .add(
            API_HOST,
            // The CURRENT certificate's (or intermediate's) SPKI SHA-256.
            "sha256/REPLACE_WITH_REAL_PIN_AAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            // A BACKUP pin you control (e.g. the key of the NEXT cert you'll rotate
            // to, generated in advance). Ship this BEFORE rotating, so the new cert
            // already matches a pin in installed apps — this prevents the bricking.
            "sha256/REPLACE_WITH_BACKUP_PIN_BBBBBBBBBBBBBBBBBBBBBBBBB="
        )
        .build()

    return OkHttpClient.Builder()
        .certificatePinner(pinner)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
}
// Run the SAME request through this with mitmproxy intercepting -> OkHttp throws
// javax.net.ssl.SSLPeerUnverifiedException: "Certificate pinning failure!" and the
// connection is REFUSED. The proxy CANNOT read what the app won't send. That refusal
// is the protection, demonstrated.

// ----------------------------------------------------------------------------
// A tiny runner you can call from a coroutine/test to see both behaviors:
// ----------------------------------------------------------------------------

fun fetchForecast(client: OkHttpClient): String {
    val request = Request.Builder()
        .url("https://$API_HOST/v1/forecast?latitude=51.5&longitude=-0.12&current=temperature_2m")
        .build()
    client.newCall(request).execute().use { response ->
        return response.body?.string() ?: "no body"
    }
    // With unpinnedClient() + proxy: succeeds, proxy reads it.
    // With pinnedClient()  + proxy: throws SSLPeerUnverifiedException (pinning failure).
    // With pinnedClient()  + NO proxy: succeeds normally (real cert matches the pin).
}

// ----------------------------------------------------------------------------
// THE ROTATION STRATEGY (write it before reading — it's the part that bricks apps):
//
//   - Pin the INTERMEDIATE cert, not the leaf. Leaves rotate often (yearly+);
//     intermediates rarely. A reissued leaf under the same intermediate still passes.
//   - ALWAYS include a BACKUP pin (a key you control / the next cert's key). Ship it
//     BEFORE rotating the server cert, so when you switch, installed apps already
//     trust the new key. This is the single most important practice.
//   - Ship the new pin, wait for adoption, THEN rotate the server cert. Never rotate
//     the cert first — that bricks every installed app with the old pin, and they
//     can't even reach you to be told to update.
//   - Keep a remote-config KILL SWITCH to disable pinning if a rotation goes wrong.
//   - If you can't commit to this discipline, DON'T pin — a missing pin is a smaller
//     risk than a self-inflicted multi-day outage.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - The pinned client connects fine even through the proxy. Either the proxy isn't
//   actually intercepting (check mitmproxy shows the flow), or your pin matches the
//   proxy's CA by accident (it won't). More likely: you didn't route the emulator
//   through the proxy. Set the emulator's proxy to your host:8080 and install
//   mitmproxy's CA via http://mitm.it on the device.
//
// - SSLPeerUnverifiedException even WITHOUT a proxy. Your pin is wrong. Use the
//   "connect with a bogus pin and read the real pins from the exception message"
//   trick, then paste the correct sha256/... value.
//
// - Cleartext/HTTP error in debug. Your network security config may block cleartext;
//   that's fine — you're using HTTPS. If you need the proxy's CA trusted in DEBUG,
//   put <certificates src="user"/> in <debug-overrides> (lecture 2, §4), NEVER in
//   the base/release config.
//
// - You only have one real key to pin. That's okay for the exercise; use a real
//   second key (or a second host's key) as the backup to PRACTICE the two-pin setup.
//   In production the backup is the pre-generated key of your next certificate.
//
// - Don't pin the PROXY's cert by mistake. Compute the pin against the REAL API host
//   (openssl s_client to api host), with the proxy OFF, so you pin your server, not
//   the attacker's.
//
// ----------------------------------------------------------------------------
