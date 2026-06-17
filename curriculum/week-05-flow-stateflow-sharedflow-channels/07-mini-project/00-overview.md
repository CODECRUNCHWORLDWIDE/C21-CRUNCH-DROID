# Mini-Project — A reactive ticker: cold timestamps, hot deltas, threshold alerts

This week you build a **reactive ticker module** that exercises every Flow idea end to end: a **cold** `Flow<Long>` of timestamps, a **hot** `StateFlow<PriceDelta>` of computed price changes, and a **`SharedFlow<Alert>(replay = 0)`** that fires when a delta crosses a threshold — all tested deterministically with Turbine and `runTest` virtual time. It is small, but it makes you place the cold/hot line on purpose three times in one module and prove each choice with an assertion.

This is a *plain JVM Kotlin* library plus a Turbine test source set. No Android, no emulator. The point of the week is the cold/hot distinction and operator selection; you learn them cleanest without `viewModelScope` and `collectAsStateWithLifecycle` in the way. In Phase 2 this exact shape — a cold source, a hot `StateFlow` of derived state, a `SharedFlow` of events — becomes a `ViewModel`. Build the engine now; wrap it in architecture later.

---

## Where you're starting from

A fresh, self-contained module. You need a Gradle Kotlin/JVM project with:

```kotlin
// build.gradle.kts
plugins { kotlin("jvm") version "2.1.0" }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation(kotlin("test"))
}
kotlin { jvmToolchain(21) }
```

## What you're building toward

By the end you have:

- A **cold** `tickFlow(): Flow<Long>` of timestamps — lazy, per-collector, cancellable.
- A `PriceFeed` that turns a cold stream of prices into a **hot** `StateFlow<PriceDelta>` of computed deltas, shared across collectors with `WhileSubscribed`.
- A **`SharedFlow<Alert>(replay = 0)`** that fires exactly once per threshold crossing — an *event*, not state, so a late subscriber doesn't re-fire stale alerts.
- A Turbine test suite proving: the timestamp flow is cold, the delta `StateFlow` always has a value and de-duplicates, the alert fires once per crossing and not on re-subscription, and the whole thing is deterministic under virtual time.

The module is abstracted over an injected price source (a `Flow<Double>`) so tests feed deterministic prices and real use plugs in a live source. We keep it source-agnostic this week — wiring a WebSocket via `callbackFlow` is a stretch goal; real networking is Phase 3.

---

## Milestone 1 — The cold timestamp flow (≈ 1.5 h)

The simplest piece, and the one that anchors "cold." A flow that emits the current time every interval, forever, until cancelled.

```kotlin
package com.crunch.droid.ticker

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

// COLD: building this runs nothing. Each collector gets its own ticking loop,
// cancelled when its scope is cancelled (Week 4). `now` is injected for testability.
fun tickFlow(interval: Duration, now: () -> Long = System::currentTimeMillis): Flow<Long> = flow {
    while (true) {
        emit(now())
        delay(interval)
    }
}
```

Decisions you must be able to defend in review:

- **Why cold and not a hot `SharedFlow`?** The timestamp source has no shared state worth caching and each consumer wants its own cancellable loop. Cold is the right default — lazy, per-collector, free to not run when nobody collects. (If you later wanted *one* shared ticking clock for the whole app, you'd `shareIn` it — but that's a deliberate upgrade, not the default.)
- **Why inject `now`?** Determinism. A test passes a fake clock and asserts exact timestamps; real use takes the system clock. This is Week 3's testability seam again.

Prove it's cold with Turbine (you already drilled this in exercise 01): building `tickFlow` registers nothing, and two collectors each get their own sequence.

## Milestone 2 — The hot delta `StateFlow` (≈ 2.5 h)

The heart. Turn a cold `Flow<Double>` of prices into a hot `StateFlow<PriceDelta>` of *computed* changes, shared so every collector sees the same current delta.

```kotlin
package com.crunch.droid.ticker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

// The derived state: the latest price and how it moved from the previous one.
data class PriceDelta(
    val price: Double,
    val previous: Double,
    val change: Double,            // price - previous
) {
    val percent: Double get() = if (previous == 0.0) 0.0 else change / previous * 100.0
}

class PriceFeed(
    prices: Flow<Double>,          // a COLD source of prices (injected; test feeds it)
    scope: CoroutineScope,         // the lifecycle that owns the shared state
) {
    // runningFold turns a stream of prices into a stream of deltas (each delta needs
    // the previous price — scan/runningFold is the operator for "carry state forward").
    val delta: StateFlow<PriceDelta> = prices
        .runningFold(PriceDelta(0.0, 0.0, 0.0)) { prev, price ->
            PriceDelta(price = price, previous = prev.price, change = price - prev.price)
        }
        .distinctUntilChanged()                    // don't emit an identical delta twice
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),  // the Android default
            initialValue = PriceDelta(0.0, 0.0, 0.0),         // a value before the first price
        )
}
```

The week-defining moves:

1. **`runningFold`** carries state forward: each `PriceDelta` is computed from the *previous* price, so a flow of bare prices becomes a flow of deltas. This is the `scan`/`runningReduce` family from lecture 1, §2 doing real work.
2. **`stateIn(... WhileSubscribed(5000), initialValue = ...)`** converts the cold fold into a hot `StateFlow`: it runs the upstream *once* (not per collector), caches the latest delta for new subscribers, and — with `WhileSubscribed(5000)` — survives a configuration change but stops when nobody watches. This is lecture 2, §3, and `WhileSubscribed(5000)` is the number you memorise.
3. **`distinctUntilChanged`** + `StateFlow`'s own de-dup means a repeated price doesn't churn the state — exercise 01's de-dup property, applied.

Prove with Turbine: `delta.value` is readable synchronously (it's a `StateFlow`), the first collector gets the current delta then changes, and feeding the same price twice doesn't emit twice.

## Milestone 3 — The threshold-alert `SharedFlow` (≈ 2 h)

The event stream. When a delta's magnitude crosses a threshold, fire an `Alert` — *once*. This must be a `SharedFlow(replay = 0)`, because re-firing a stale alert to a late subscriber (lecture 2's replay bug) would be wrong.

```kotlin
package com.crunch.droid.ticker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

sealed interface Alert {
    val delta: PriceDelta
    data class SpikeUp(override val delta: PriceDelta) : Alert
    data class SpikeDown(override val delta: PriceDelta) : Alert
}

class AlertEngine(
    feed: PriceFeed,
    private val thresholdPercent: Double,
    scope: CoroutineScope,
) {
    // replay = 0: an alert is a ONE-SHOT event. A late subscriber must NOT re-fire
    // an old spike. extraBufferCapacity = 8 so tryEmit doesn't drop under bursts.
    private val _alerts = MutableSharedFlow<Alert>(replay = 0, extraBufferCapacity = 8)
    val alerts: SharedFlow<Alert> = _alerts.asSharedFlow()

    init {
        // Watch the hot delta state; emit an alert ONLY when a crossing happens.
        feed.delta
            .onEach { delta ->
                when {
                    delta.percent >= thresholdPercent  -> _alerts.tryEmit(Alert.SpikeUp(delta))
                    delta.percent <= -thresholdPercent -> _alerts.tryEmit(Alert.SpikeDown(delta))
                    else -> Unit
                }
            }
            .launchIn(scope)   // owned by the scope; cancelled when the scope is
    }
}
```

The decisions to defend:

- **Why `SharedFlow(replay = 0)` and not `StateFlow`?** An alert is an *event* ("a spike just happened"), not *state* ("the current spike"). If it were a `StateFlow`, a collector subscribing after a spike would immediately receive the stale spike and (in a UI) re-fire the notification — the exact replay bug from the challenge. `replay = 0` means events fire once, for whoever is listening when they happen.
- **Why `launchIn(scope)`?** It collects `feed.delta` inside the owned scope, so the alert-watching coroutine is cancelled with the scope (Week 4's structured concurrency). No leak.

Prove with Turbine: an alert fires when the percent crosses the threshold, does *not* fire when it doesn't, fires only once per crossing, and — the key test — a collector subscribing *after* a spike receives nothing (no replay).

## Milestone 4 — Tie it together and test the whole pipeline (≈ 1.5 h)

Wire a cold price source → `PriceFeed` (hot delta state) → `AlertEngine` (event alerts), and test the end-to-end behaviour under virtual time:

```kotlin
@Test
fun spikeFiresAnAlertExactlyOnce() = runTest {
    val prices = MutableSharedFlow<Double>(extraBufferCapacity = 16)
    val feed = PriceFeed(prices, backgroundScope)               // runTest's owned scope
    val engine = AlertEngine(feed, thresholdPercent = 5.0, scope = backgroundScope)

    engine.alerts.test {
        prices.emit(100.0)        // baseline, no previous -> no alert
        prices.emit(106.0)        // +6% -> crosses +5% -> SpikeUp
        val alert = awaitItem()
        assertTrue(alert is Alert.SpikeUp)
        assertEquals(106.0, alert.delta.price)

        prices.emit(107.0)        // +~0.9% -> no crossing -> no alert
        expectNoEvents()
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun lateSubscriberDoesNotGetReplayedAlert() = runTest {
    val prices = MutableSharedFlow<Double>(extraBufferCapacity = 16)
    val feed = PriceFeed(prices, backgroundScope)
    val engine = AlertEngine(feed, thresholdPercent = 5.0, scope = backgroundScope)

    // A spike happens with NO alert collector present.
    prices.emit(100.0)
    prices.emit(110.0)            // +10% spike, fired into the void (replay = 0)
    runCurrent()

    // A collector subscribes AFTER the spike -> gets nothing. The replay-0 fix.
    engine.alerts.test {
        expectNoEvents()
        cancelAndIgnoreRemainingEvents()
    }
}
```

Note `backgroundScope` — `runTest` provides it as a scope that is automatically cancelled at the end of the test, which is exactly the owned scope a `stateIn`/`launchIn` needs. Using it keeps the test leak-free and deterministic.

---

## Acceptance criteria

- [ ] `tickFlow` is a **cold** `Flow<Long>` (lazy, per-collector), with an injected clock for testability.
- [ ] `PriceFeed.delta` is a **hot** `StateFlow<PriceDelta>` built with `runningFold` + `stateIn(WhileSubscribed(5000), initialValue = ...)`, shared across collectors and de-duplicated.
- [ ] `AlertEngine.alerts` is a **`SharedFlow<Alert>(replay = 0)`** — a one-shot event stream that does not replay to late subscribers.
- [ ] The alert watcher is collected with `launchIn(scope)` (owned, cancellable — no leak).
- [ ] A Turbine suite proves: the timestamp flow is cold, the delta `StateFlow` has a current value and de-duplicates, an alert fires exactly once per crossing, and a late subscriber gets no replay.
- [ ] Every assertion is a Turbine assertion under `runTest` virtual time — **no `Thread.sleep`**.
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **A real `callbackFlow` price source.** Replace the injected `Flow<Double>` with a `callbackFlow` bridging a fake "exchange listener," unregistering in `awaitClose` (exercise 03). Feel the cold callback bridge feeding a hot `StateFlow`.
- **`conflate` under a fast source.** Drive prices faster than the delta consumer and add `conflate()` so the consumer only ever sees the latest delta; prove with Turbine that intermediate deltas are dropped, not buffered unboundedly (lecture 1, §4).
- **Alert de-bounce.** Add `debounce` or a cooldown so repeated crossings within N seconds fire one alert, not a storm — and Turbine-test the cooldown deterministically with virtual time.
- **`flatMapLatest` on the source.** If a "switch symbol" command arrives, `flatMapLatest` to the new symbol's price flow, cancelling the old symbol's subscription (exercise 02). This is the exact shape of a real trading-app symbol switcher.

## What this milestone earns you

You can now place the cold/hot line on purpose: a cold source for lazy per-collector data, a hot `StateFlow` for shared derived state, a `SharedFlow(replay = 0)` for one-shot events — and you can prove each choice with a deterministic Turbine assertion instead of hoping. That is the literal "skills earned" line for the week: operator selection without guessing, bridging into Flow, and asserting on emissions deterministically. Week 6 closes the foundations phase by putting your Kotlin-coroutines-Flow fluency onto a real Android build; Phase 2 then wraps this exact ticker shape — cold source, hot `StateFlow<UiState>`, `SharedFlow` events — in a `ViewModel` and a Compose screen. You'll be glad the streaming engine is solid before the architecture goes on top.
