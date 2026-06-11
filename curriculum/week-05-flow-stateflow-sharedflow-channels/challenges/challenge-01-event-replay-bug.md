# Challenge 1 — The event-replay bug (reproduce, explain, fix)

**Time.** 60–120 minutes.
**Deliverable.** A `BUG.md` write-up (symptom, root cause, fix, and the `Channel`-vs-`SharedFlow` trade-off) plus the fixed code and Turbine tests, committed to your Week 05 repo.

## The premise

This is the most-shipped Flow bug in Android: a one-shot event — "show a snackbar," "navigate to the receipt," "play a sound" — modelled as *state* instead of an *event*, so it re-fires when a new collector subscribes. The classic trigger is **screen rotation**: the old collector unsubscribes, a new one subscribes a moment later, and because the event was held as state, the new collector receives the cached last event and the snackbar fires again — for something that already happened.

The skill this challenge builds is not "know events aren't state" — it is **reproduce the double-fire deterministically, explain the root cause in one paragraph, fix it, and prove the fix fires exactly once across a simulated rotation.** A re-subscription is exactly what rotation does, and Turbine lets you simulate it without an emulator.

## Step 1 — Build the bug (events as `StateFlow`)

Model a checkout `ViewModel`-equivalent that emits a "show receipt" event the *wrong* way — as a `StateFlow<Event?>`:

```kotlin
package com.crunch.droid

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface CheckoutEvent {
    data object ShowReceipt : CheckoutEvent
}

// WRONG: a one-shot event held as STATE. The last event is cached and replayed
// to every new subscriber — including the post-rotation one.
class BuggyCheckout {
    private val _event = MutableStateFlow<CheckoutEvent?>(null)
    val event: StateFlow<CheckoutEvent?> = _event.asStateFlow()

    fun pay() {
        _event.value = CheckoutEvent.ShowReceipt   // fire the event (as state)
    }
}
```

## Step 2 — Reproduce the double-fire with a simulated rotation

A rotation is "collector A leaves, collector B subscribes." Write a Turbine test that collects once (the event fires), then collects *again* (simulating the new collector after rotation) and shows the event fires *again*:

```kotlin
@Test
fun stateFlowReplaysEventOnResubscription() = runTest {
    val vm = BuggyCheckout()
    vm.pay()                                   // user pays; event fires once

    // Collector A (before rotation) sees the event.
    vm.event.test {
        // StateFlow always replays its current value to a new subscriber.
        assertEquals(CheckoutEvent.ShowReceipt, awaitItem())
        cancelAndIgnoreRemainingEvents()
    }

    // ROTATION: a NEW collector subscribes. The bug: it ALSO gets ShowReceipt,
    // because StateFlow cached it. The snackbar fires a SECOND time.
    vm.event.test {
        assertEquals(CheckoutEvent.ShowReceipt, awaitItem())   // <-- the double-fire
        cancelAndIgnoreRemainingEvents()
    }
}
```

This test *passing* is the bug demonstrated: the event was delivered to two separate subscriptions. In a real app those two subscriptions are the same screen before and after rotation, and the user sees the receipt snackbar twice.

## Step 3 — Fix it with `SharedFlow(replay = 0)`

Re-model the event as a true one-shot with `SharedFlow(replay = 0)`:

```kotlin
class FixedCheckout {
    private val _events = MutableSharedFlow<CheckoutEvent>(
        replay = 0,                            // a new subscriber gets NO past events
        extraBufferCapacity = 1,               // tryEmit can buffer one if no collector yet
    )
    val events: SharedFlow<CheckoutEvent> = _events.asSharedFlow()

    fun pay() {
        _events.tryEmit(CheckoutEvent.ShowReceipt)
    }
}
```

Now write the test proving the event fires for the collector present *when it happened*, but a later subscriber (post-rotation) gets nothing:

```kotlin
@Test
fun sharedFlowFiresOnceNoReplayOnResubscription() = runTest {
    val vm = FixedCheckout()

    // Collector present when pay() happens sees the event exactly once.
    vm.events.test {
        vm.pay()
        assertEquals(CheckoutEvent.ShowReceipt, awaitItem())
        expectNoEvents()                       // and only once
        cancelAndIgnoreRemainingEvents()
    }

    // ROTATION: a NEW collector subscribes AFTER pay(). With replay = 0 it gets
    // NOTHING — no double snackbar.
    vm.events.test {
        expectNoEvents()                       // the fix: no replay to the late subscriber
        cancelAndIgnoreRemainingEvents()
    }
}
```

## Step 4 — Contrast a `Channel`

Implement the same event stream a third way, with a `Channel<CheckoutEvent>(Channel.BUFFERED)` consumed via `receiveAsFlow()`. Note in `BUG.md` the key behavioural difference: a `Channel` delivers each event to **exactly one** receiver (so if two collectors race, only one gets it), while `SharedFlow` *broadcasts* (every active collector gets every event). For a single-screen event stream both fix the replay bug; the choice is about delivery semantics, and you should be able to state which you'd pick and why.

## Step 5 — Write it up

`BUG.md`:

- **Symptom** — what the user sees (snackbar/navigation fires twice after rotating the screen).
- **Root cause** — one paragraph: a one-shot event modelled as `StateFlow` (or `SharedFlow` with `replay > 0`) caches its last value and replays it to every new subscriber; rotation creates a new subscriber, so the event re-fires.
- **The fix** — `SharedFlow(replay = 0)`, and which test proves it now fires once across a simulated rotation.
- **The `Channel` trade-off** — broadcast (SharedFlow) vs single-delivery (Channel), and which you'd choose for a single-screen event and why.

## Acceptance criteria

- [ ] `stateFlowReplaysEventOnResubscription` passes (the bug, demonstrated: the event reaches a second, later subscription).
- [ ] `sharedFlowFiresOnceNoReplayOnResubscription` passes (the fix: `expectNoEvents()` for the post-rotation subscriber).
- [ ] A `Channel`-based variant exists and its behaviour (single-delivery) is noted.
- [ ] `BUG.md` documents symptom + root cause + fix + the Channel/SharedFlow trade-off, in your own words.
- [ ] Every assertion is a Turbine assertion under `runTest` — **no `Thread.sleep`**.
- [ ] Build with **0 warnings**.

## What "great" looks like

A weak submission says "I used SharedFlow and it works." A great submission says:

> **Symptom:** rotating the checkout screen re-showed the "Receipt saved" snackbar for a payment that completed before the rotation. **Root cause:** the event was a `StateFlow<Event?>`, which by definition caches its current value and replays it to every new subscriber; a configuration change tears down the old collector and creates a new one within milliseconds, and that new collector received the cached `ShowReceipt` — so the snackbar fired again. **Fix:** the event is now a `MutableSharedFlow(replay = 0, extraBufferCapacity = 1)`; the Turbine test subscribes *after* `pay()` and asserts `expectNoEvents()`, proving no replay across the simulated rotation. **Trade-off:** I chose `SharedFlow` over a `Channel` because the screen may have more than one legitimate collector (e.g. an analytics observer alongside the UI) and `SharedFlow` broadcasts to all of them, whereas a `Channel` would deliver the event to only one — which would silently drop it for the other.

Reproduced, explained by mechanism, fixed, and honest about the alternative. That is the senior-engineer answer — and it is verbatim the career-pack "cold versus hot flows — when to pick which" drill.

## Where this reappears

This exact bug and fix recur in Phase 2 (every `ViewModel` with one-shot navigation/snackbar events) and Phase 4's chaos drills. The "events are a `SharedFlow(replay = 0)`, state is a `StateFlow`" rule is one you will apply on every screen you ever build.
