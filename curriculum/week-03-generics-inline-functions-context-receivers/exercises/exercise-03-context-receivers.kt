// Exercise 3 — Context receivers: from parameter-threading to ambient capabilities
//
// Goal: Take a function that drags a Logger and a Clock through its parameter list
//       on every call, refactor it to declare them as CONTEXT RECEIVERS, and call
//       it under nested `with`. Then prove the compile-time requirement by calling
//       it WITHOUT the context and reading the error.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// Pure Kotlin, JVM, no Android. Context receivers are EXPERIMENTAL in Kotlin 2.x and
// need a compiler flag. Add to build.gradle.kts:
//
//   kotlin {
//       compilerOptions {
//           freeCompilerArgs.add("-Xcontext-receivers")
//       }
//   }
//
// (Exact spelling evolves toward "context parameters" in newer Kotlin — pin to your
// version and read the current release notes. The CONCEPT below is stable.)
//
// ACCEPTANCE CRITERIA
//
//   [ ] recordEventCtx declares context(Logger, Clock) and uses log()/now() unqualified.
//   [ ] It is called successfully inside with(logger) { with(clock) { ... } }.
//   [ ] You uncommented the "no context" call, read the compile error, and can quote
//       what it says (the capability is missing) — then re-commented it.
//   [ ] The context-receiver version takes NO logger/clock parameters.
//   [ ] Builds with 0 warnings.
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package com.crunch.ktcontext

interface Logger { fun log(message: String) }
interface Clock { fun now(): Long }

// A test double for each capability.
val consoleLogger = object : Logger { override fun log(message: String) = println("LOG: $message") }
val fixedClock = object : Clock { override fun now(): Long = 1_700_000_000_000L }

// ----------------------------------------------------------------------------
// BEFORE — capabilities threaded as parameters. Notice every call must pass both,
// and every function that wants to CALL this must also hold both to forward them.
// ----------------------------------------------------------------------------

fun recordEventParams(logger: Logger, clock: Clock, name: String) {
    logger.log("event '$name' at ${clock.now()}")
}

// A caller that itself has to carry logger+clock just to forward them — the noise
// context receivers remove.
fun handleLoginParams(logger: Logger, clock: Clock, userId: String) {
    recordEventParams(logger, clock, "login:$userId")
    recordEventParams(logger, clock, "session-start:$userId")
}

// ----------------------------------------------------------------------------
// AFTER — capabilities as CONTEXT RECEIVERS. The signature is about WHAT the
// function does (name: String), not WHAT it needs (the capabilities live in the
// context block, checked by the compiler).
// ----------------------------------------------------------------------------

context(Logger, Clock)
fun recordEventCtx(name: String) {
    // TODO 1: log the same message as recordEventParams, but call log(...) and now()
    //   UNQUALIFIED — they resolve to the Logger and Clock context receivers.
    //   (No `logger.` / `clock.` prefix: the receivers are implicit.)
}

// A caller declared with the SAME context receivers forwards them IMPLICITLY —
// it does not re-pass logger/clock, it just calls recordEventCtx and the context
// propagates.
context(Logger, Clock)
fun handleLoginCtx(userId: String) {
    // TODO 2: call recordEventCtx twice ("login:$userId" and "session-start:$userId").
    //   No arguments for the capabilities — they're already in scope here.
}

// ----------------------------------------------------------------------------
// CALLING IT — you provide the receivers with nested `with`, which puts each in scope.
// ----------------------------------------------------------------------------

fun main() {
    // Parameter version: pass everything explicitly.
    recordEventParams(consoleLogger, fixedClock, "boot")
    handleLoginParams(consoleLogger, fixedClock, "u-42")

    // Context-receiver version: provide the capabilities once, call cleanly inside.
    with(consoleLogger) {
        with(fixedClock) {
            recordEventCtx("boot")          // both Logger and Clock are in scope here
            handleLoginCtx("u-42")          // context propagates into handleLoginCtx
        }
    }

    // TODO 3 (then re-comment): uncomment the next line. It MUST fail to compile,
    // because there is no Logger/Clock in scope here. Read the error, quote it in a
    // comment, then re-comment the line so the file builds.
    //
    // recordEventCtx("no-context")   // <- expected COMPILE ERROR: missing context receivers
}

// ----------------------------------------------------------------------------
// WHY context receivers beat the alternatives (write it before reading):
//
//   - vs parameters: recordEventCtx's signature is `(name: String)`, not
//     `(logger, clock, name)`. handleLoginCtx forwards the capabilities WITHOUT
//     re-passing them — they propagate through the context. Less noise, same safety.
//   - vs an extension receiver: you'd get only ONE receiver; context receivers
//     compose any number (here, two).
//   - vs a global singleton Logger: the capability is explicit in the type and
//     swappable per `with` scope — testable, no hidden global, compiler-checked.
//
//   The load-bearing property: recordEventCtx CANNOT be called without its context.
//   The TODO 3 error is the type system enforcing "this function needs a Logger and
//   a Clock" — a guarantee a parameter-less global could never give you.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - "Unresolved reference: log" inside recordEventCtx — you didn't add the
//   -Xcontext-receivers flag, OR you wrote `context(Logger, Clock)` on the wrong
//   line. The context block goes immediately ABOVE the `fun` line.
//
// - TODO 1: `log("event '$name' at ${now()}")`. Both `log` and `now` are members of
//   the in-scope context receivers; call them bare.
//
// - TODO 2: just `recordEventCtx("login:$userId")` etc. Because handleLoginCtx ALSO
//   declares context(Logger, Clock), the receivers are in scope and propagate.
//
// - The TODO 3 line compiles (no error)? You probably left a top-level `with` open,
//   or moved the call inside the with block. It must be OUTSIDE any with(logger)/
//   with(clock) scope to lack the context.
//
// - Newer Kotlin warns context receivers are deprecated in favor of context
//   parameters — that's expected; the syntax is evolving. Pin to a Kotlin version
//   where -Xcontext-receivers works, or adapt to `context(...)` parameter syntax if
//   your version ships it. The exercise's POINT is the ambient-capability concept.
//
// ----------------------------------------------------------------------------
