# Mini-Project — `kt-bus`: a typed event bus published to `mavenLocal`

This week you build **`kt-bus`** — a small, typed, in-process event bus library whose entire public API is the week's promise made real: `subscribe<UserLoggedIn> { event -> ... }` and `post(UserLoggedIn(id))`, with **no `Class` parameter, no caller cast, and no way to get a `ClassCastException` from the library's surface**. The type routing happens because `reified` captured the concrete event type at the call site; the single unavoidable internal cast is guarded by that reified type and provably safe.

The point of the project is not "build an event bus" — event buses are out of fashion for app architecture (you'll use `StateFlow`/`SharedFlow` from Week 05 for that, and we'll say so). The point is to build a **real Kotlin library**: a typed reified API that feels effortless to call, a Gradle subproject with `maven-publish`, published to your local Maven repository, and *consumed by a second module* — proving the API works across a module boundary the way a published artifact would. That round trip — author a library, publish it, consume it as a dependency — is the deliverable.

This is a *pure-Kotlin, JVM* project — no Android, no emulator. The Android-specific weeks start at Week 06. Everything here runs with `./gradlew` and a JDK.

---

## Where you're starting from

A single-module Gradle Kotlin DSL project from Week 01 (your `kt-stat` repo, or a fresh one). You'll add a *second* subproject, so make sure your `settings.gradle.kts` and `gradlew` work first.

If you're starting fresh: `gradle init` → "library" → Kotlin → Kotlin DSL, or copy your Week 01 project structure. You need a `settings.gradle.kts`, a root `build.gradle.kts`, and the Gradle wrapper.

## What you're building toward

By the end you have:

- A `:kt-bus` library subproject exposing `EventBus` with `inline fun <reified T> subscribe(...)` and `post(...)`.
- Zero `Class`/`Any`/cast leakage in the public API — the "no casts in the caller" promise, verified.
- A `Subscription` handle so subscribers can unsubscribe (no leaks).
- `:kt-bus` published to `mavenLocal` with `maven-publish`, with a real group/artifact/version coordinate.
- A *second* subproject, `:demo`, that depends on `kt-bus` (via `mavenLocal` or a project dependency) and uses it with full type safety.
- A test suite proving: events route to the right typed handlers, subtype routing behaves as you decided, unsubscribe works, and there is no cross-type leakage.
- A `README.md` for the library documenting the coordinate and a usage snippet, plus a one-paragraph "why you'd use `StateFlow` instead in a real app" honesty note.

---

## Milestone 1 — Design the typed API (≈ 1 h)

Decide the public surface *first*, because the whole project is judged on it. The contract: a caller never names a type twice, never passes a `Class`, never casts.

```kotlin
package com.crunch.ktbus

/** A handle returned by subscribe(); call cancel() to stop receiving events. */
fun interface Subscription {
    fun cancel()
}

class EventBus {
    // Keyed by the event's Class; values are type-erased handlers we cast safely on dispatch.
    @PublishedApi
    internal val handlers = mutableMapOf<Class<*>, MutableList<(Any) -> Unit>>()

    // reified T captures the event type at the call site -> no Class parameter, no caller cast.
    // noinline because the handler is STORED in the map (an inlined lambda has no object to store).
    inline fun <reified T : Any> subscribe(noinline handler: (T) -> Unit): Subscription {
        val type = T::class.java
        return register(type, handler)
    }

    // The non-inline core does the storage + the single guarded cast, kept out of the inline body
    // so the inline function stays small (inline functions should be thin).
    @PublishedApi
    internal fun <T : Any> register(type: Class<T>, handler: (T) -> Unit): Subscription {
        @Suppress("UNCHECKED_CAST")
        val erased = handler as (Any) -> Unit         // the ONE cast — safe: see Milestone 3
        val list = handlers.getOrPut(type) { mutableListOf() }
        list.add(erased)
        return Subscription { list.remove(erased) }
    }

    fun post(event: Any) {
        // dispatch to exact-type handlers (subtype policy decided in Milestone 4)
        handlers[event.javaClass]?.toList()?.forEach { it(event) }
    }
}
```

Decisions you must be able to defend in review:

- **Why `reified T` on `subscribe`?** So the caller writes `subscribe<UserLoggedIn> { }` and `handler`'s parameter is already typed `UserLoggedIn` — the type is captured at the call site, not passed as a `Class`. That is the entire "no casts in the caller" promise.
- **Why `noinline handler`?** Because the handler is *stored* in `handlers`. An inlined lambda is spliced into the call site and has no object to put in a list; `noinline` keeps it a real `Function` instance. (Lecture 2, footgun §4.)
- **Why split out the non-inline `register`?** Inline functions should be thin — the body is copied to every call site, so heavy logic in an `inline fun` bloats every caller. Keep `subscribe` to "capture the type and delegate"; put the storage in a normal function. (`@PublishedApi internal` lets the inline function call the internal one.)

## Milestone 2 — Make `post` route correctly (≈ 1 h)

`post(event)` must invoke exactly the handlers registered for the event's runtime type. The `toList()` copy before iterating matters — a handler that unsubscribes (or subscribes) during dispatch would otherwise mutate the list you're iterating (a `ConcurrentModificationException`). Defend that copy in review.

Write the first tests now, red-green:

```kotlin
import org.junit.Test
import kotlin.test.assertEquals

data class UserLoggedIn(val userId: String)
data class MessageReceived(val from: String, val text: String)

class EventBusTest {
    @Test fun `routes an event only to its typed subscribers`() {
        val bus = EventBus()
        val logins = mutableListOf<String>()
        val messages = mutableListOf<String>()

        bus.subscribe<UserLoggedIn> { logins += it.userId }       // it: UserLoggedIn, no cast
        bus.subscribe<MessageReceived> { messages += it.text }    // it: MessageReceived, no cast

        bus.post(UserLoggedIn("u1"))
        bus.post(MessageReceived("u2", "hi"))
        bus.post(UserLoggedIn("u3"))

        assertEquals(listOf("u1", "u3"), logins)                  // login handler saw only logins
        assertEquals(listOf("hi"), messages)                      // message handler saw only messages
    }
}
```

The acceptance gate: in `subscribe<UserLoggedIn> { ... }`, `it` is statically `UserLoggedIn` — your IDE autocompletes `.userId`, and there is **no cast in your handler**. That's the promise, demonstrated.

## Milestone 3 — Justify the single cast (≈ 0.5 h)

There is exactly one `@Suppress("UNCHECKED_CAST")` in the whole library (in `register`). Write a comment proving it's safe, because "I suppressed the warning" is not a justification:

> The cast `handler as (Any) -> Unit` is safe because the handler is *only ever invoked* by `post` with an event whose `javaClass` equals the `type` key the handler was filed under (`handlers[event.javaClass]`). A handler registered under `UserLoggedIn::class.java` can only be reached by posting a `UserLoggedIn`, so when it runs, its `Any` argument is in fact a `UserLoggedIn` — the cast inside the handler's own typed body never fails. The `reified T` at the call site is what tied the handler to its key; that's why this internal cast is sound and the public API can never throw a `ClassCastException` at the caller.

This is the heart of the week: you cast *once*, internally, and you can *prove* it's safe from the reified key. Add a test that posts every event type and confirms no handler is ever invoked with the wrong type (a handler that fails an `is` check would throw — assert none does).

## Milestone 4 — Decide and implement a subtype policy (≈ 1.5 h)

A real bus has a design decision: if `AdminLoggedIn : UserLoggedIn` is posted, should `subscribe<UserLoggedIn>` handlers fire? There's no universal right answer — *make the call and defend it.* Two reasonable policies:

- **Exact-type only** (simplest, what Milestone 1 does): `subscribe<UserLoggedIn>` fires only for `UserLoggedIn`, not subtypes. Predictable, no surprises, fast (`handlers[event.javaClass]`).
- **Assignable** (more flexible): `subscribe<UserLoggedIn>` also fires for `AdminLoggedIn`. Requires checking `key.isAssignableFrom(event.javaClass)` across all keys on each `post` — more work, and now an event can hit multiple handler sets.

Implement *one* deliberately and test the boundary:

```kotlin
// If you choose ASSIGNABLE, post() becomes:
fun post(event: Any) {
    handlers.forEach { (type, list) ->
        if (type.isAssignableFrom(event.javaClass)) {
            list.toList().forEach { it(event) }
        }
    }
}
```

Test that an `AdminLoggedIn` reaches (assignable) or does not reach (exact) `UserLoggedIn` subscribers, matching your chosen policy. Document the choice and the trade-off in the library README. The point is that you *noticed the decision* and can defend it — that's the senior signal, not which policy you picked.

## Milestone 5 — Unsubscribe and no leaks (≈ 1 h)

`subscribe` returns a `Subscription`; `cancel()` removes the handler. Test it:

```kotlin
@Test fun `cancel stops further delivery`() {
    val bus = EventBus()
    val seen = mutableListOf<String>()
    val sub = bus.subscribe<UserLoggedIn> { seen += it.userId }

    bus.post(UserLoggedIn("a"))
    sub.cancel()
    bus.post(UserLoggedIn("b"))        // not delivered — already cancelled

    assertEquals(listOf("a"), seen)
}
```

Also confirm cancelling twice is harmless and that cancelling during dispatch (a handler that cancels itself) doesn't crash — that's why Milestone 2's `toList()` copy exists. A bus that leaks subscriptions is a memory leak in a real app; the `Subscription` handle is how the caller controls lifetime.

## Milestone 6 — Publish to `mavenLocal` and consume it (≈ 1.5 h)

Make `:kt-bus` a publishable artifact. In `kt-bus/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "com.crunch"
version = "0.1.0"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            // coordinate becomes com.crunch:kt-bus:0.1.0
        }
    }
}
```

Publish: `./gradlew :kt-bus:publishToMavenLocal`. Confirm the artifact lands under `~/.m2/repository/com/crunch/kt-bus/0.1.0/`.

Now create a second subproject `:demo` that consumes it. In `demo/build.gradle.kts`, add `mavenLocal()` to repositories and `implementation("com.crunch:kt-bus:0.1.0")`. Write a `main()` that subscribes and posts — and confirm it type-checks across the module boundary with no casts, exactly as if `kt-bus` were a third-party library off Maven Central. (A `project(":kt-bus")` dependency also works and is faster for iteration; do the `mavenLocal` round trip at least once to prove publishing works.)

```kotlin
// demo/src/main/kotlin/Main.kt
import com.crunch.ktbus.EventBus

fun main() {
    val bus = EventBus()
    bus.subscribe<String> { println("got: $it") }   // it: String, across a published-module boundary
    bus.post("hello from a consumer")                 // got: hello from a consumer
}
```

---

## Acceptance criteria

- [ ] `subscribe<T>` and `post` are the public API; **no `Class` parameter, no caller cast** anywhere a consumer writes.
- [ ] Exactly one `@Suppress("UNCHECKED_CAST")` in the library, with a written safety justification tied to the reified key.
- [ ] `subscribe` uses `reified` (capture the type) and `noinline` (store the handler); the inline body is thin and delegates to a non-inline `register`.
- [ ] A subtype policy (exact or assignable) is chosen, implemented, tested at the boundary, and documented with its trade-off.
- [ ] `subscribe` returns a `Subscription`; `cancel()` stops delivery; cancelling during dispatch doesn't crash (the `toList()` copy).
- [ ] `:kt-bus` publishes to `mavenLocal` (`com.crunch:kt-bus:0.1.0` under `~/.m2`).
- [ ] A `:demo` module consumes it (via `mavenLocal` at least once) and uses `subscribe<T>` with full type safety and no casts.
- [ ] Test suite is green: routing, subtype policy boundary, unsubscribe, no cross-type leakage.
- [ ] Build with **0 warnings, 0 errors** (the one guarded cast aside).

## Stretch goals

- **A reified `subscribeOnce<T>`** that auto-cancels after the first matching event — built by capturing the `Subscription` and cancelling from inside the handler. Watch the `toList()` copy save you here.
- **Sticky events.** A `postSticky(event)` that replays the last value of that type to *new* subscribers — `subscribe<T>` immediately fires with the last sticky `T` if one exists. (This is `StateFlow`'s `replay = 1` behaviour by hand — note that in the README, it's the bridge to Week 05.)
- **Thread safety.** Make `subscribe`/`post`/`cancel` safe under concurrent access with a lock or a concurrent map, and write a test that hammers it from multiple threads. (Real coordination is coroutines/Flow in Week 04–05; this is the manual version.)
- **A `fun interface` handler variant.** Offer `subscribe(handler: EventHandler<T>)` using a `fun interface EventHandler<T> { fun handle(event: T) }`, and discuss when a named `fun interface` reads better than a bare `(T) -> Unit` in a public API (lecture 2, §5).

## What this milestone earns you

You can now design and ship a *typed Kotlin library*: a reified API that captures the type at the call site so callers never name it twice or cast, a single internal cast you can prove safe from the reified key, an inline/noinline split that keeps the inline surface thin, and a real `maven-publish` round trip consumed across a module boundary. That is the literal "skill earned" line for the week — reading variance annotations and explaining them out loud, writing inline DSL-style APIs, and publishing a Kotlin library. Week 04 takes the higher-order-function and `crossinline` machinery you used here and shows why a *coroutine* builder is shaped exactly the same way — and why the `crossinline` rule you applied to a stored handler is the same rule that governs a `launch { }` block.
