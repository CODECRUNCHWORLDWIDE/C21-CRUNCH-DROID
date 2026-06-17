# Exercise 1 — Cold is lazy and per-collector

**Goal.** Take lecture 1's central claim — "a cold `Flow` is a recipe that runs nothing until collected and re-runs per collector" — and *prove it with counters*. Then convert the same source to a hot `StateFlow` and prove the opposite: one shared execution, a value available without collecting. After this exercise the cold/hot distinction is concrete, not a slogan.

**Estimated time.** 40 minutes.

**Prerequisites.** A Kotlin/JVM project with `kotlinx-coroutines-core`, `kotlinx-coroutines-test`, and `app.cash.turbine:turbine` on the test classpath. You have `runTest` from Week 4.

---

## Step 1 — A cold flow with a side-effect counter

In `src/test/kotlin/com/crunch/droid/ColdVsHotTest.kt`, write a cold flow whose producer increments a counter so you can *see* how many times it ran:

```kotlin
package com.crunch.droid

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class ColdVsHotTest {

    @Test
    fun coldFlowRunsNothingUntilCollected() = runTest {
        val producerRuns = AtomicInteger(0)
        val cold = flow {
            producerRuns.incrementAndGet()      // side effect: the producer ran
            emit(1); emit(2); emit(3)
        }

        // Building the flow must NOT run the producer.
        assertEquals(0, producerRuns.get(), "cold flow ran its producer before collection!")

        // Collect once -> producer runs once.
        val first = cold.toList()
        assertEquals(listOf(1, 2, 3), first)
        assertEquals(1, producerRuns.get())

        // Collect AGAIN -> producer runs AGAIN. Cold = per-collector re-execution.
        val second = cold.toList()
        assertEquals(listOf(1, 2, 3), second)
        assertEquals(2, producerRuns.get(), "cold flow should re-run per collector")
    }
}
```

Run it. The three assertions encode the three facts: zero runs before collection (lazy), one run after one collect, two runs after two collects (per-collector).

## Step 2 — Prove `take` cancels the upstream early

Add a test showing a terminal operator can stop the producer mid-stream — the cancellation from Week 4, applied to flows:

```kotlin
@Test
fun takeCancelsUpstreamEarly() = runTest {
    val emitted = AtomicInteger(0)
    val cold = flow {
        repeat(100) { emitted.incrementAndGet(); emit(it) }   // would emit 100...
    }

    val firstThree = cold.take(3).toList()                     // ...but we take 3

    assertEquals(listOf(0, 1, 2), firstThree)
    // The producer was cancelled after 3 emissions (give or take buffering); it did
    // NOT emit all 100. take() cancels the upstream once it has enough.
    assert(emitted.get() < 100) { "take(3) should cancel the producer well before 100" }
}
```

## Step 3 — Now make it hot and prove the difference

A `MutableStateFlow` is hot: one shared value, available without collecting, the same for every collector.

```kotlin
@Test
fun hotStateFlowHasAValueWithoutCollecting() = runTest {
    val hot = MutableStateFlow(0)

    // A StateFlow ALWAYS has a current value — readable synchronously, no collection.
    assertEquals(0, hot.value, "a StateFlow is born with a value")

    hot.value = 41
    hot.value = 42
    assertEquals(42, hot.value)                 // latest value, conflated

    // A new collector gets the CURRENT value first (42), not the history (no 0, no 41).
    hot.test {
        assertEquals(42, awaitItem())           // current value on subscription
        hot.value = 43
        assertEquals(43, awaitItem())           // then changes
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun stateFlowDeduplicatesEqualValues() = runTest {
    val hot = MutableStateFlow(0)
    hot.test {
        assertEquals(0, awaitItem())
        hot.value = 5
        assertEquals(5, awaitItem())
        hot.value = 5                           // SAME value -> NOT emitted (== de-dup)
        hot.value = 6
        assertEquals(6, awaitItem())            // the duplicate 5 never arrived
        cancelAndIgnoreRemainingEvents()
    }
}
```

## Step 4 — Write down what you proved

Create `notes/cold-vs-hot.md` and answer in your own words:

1. Why did `producerRuns` reach 2 after two collections of the cold flow, but a hot `StateFlow` would have run its source once?
2. Why does `hot.value` have a value while a cold flow has none until collected?
3. In the de-dup test, why did the second `5` never reach the collector?

---

## Acceptance criteria

- [ ] `coldFlowRunsNothingUntilCollected` passes: 0 runs before collect, 1 after one collect, 2 after two.
- [ ] `takeCancelsUpstreamEarly` passes: `take(3)` cancels the producer before all 100 emissions.
- [ ] `hotStateFlowHasAValueWithoutCollecting` passes: `.value` readable synchronously; a new collector gets the current value.
- [ ] `stateFlowDeduplicatesEqualValues` passes: an equal consecutive value is not emitted.
- [ ] `notes/cold-vs-hot.md` answers the three questions correctly, in your own words.
- [ ] Build with **0 warnings**. Committed.

## What you just proved

You proved lecture 1's and lecture 2's central distinction with counters and Turbine assertions, not slogans: a cold flow is lazy (nothing before collection) and per-collector (re-runs each time), while a hot `StateFlow` is a single shared live value that exists without collection and de-duplicates equal updates. Every Flow bug this week — the double API call, the missing "current value," the rotation replay — traces back to which of these two you have. You can now tell them apart by behaviour.

---

## Hints (read only if stuck > 10 min)

- **Turbine's `test { }` needs the flow.** `flow.test { awaitItem() ... }`. `awaitItem()` suspends for the next emission; `awaitComplete()` asserts the flow finished; `cancelAndIgnoreRemainingEvents()` stops collecting a flow that never completes (like a `StateFlow`).
- **A `StateFlow` never completes**, so always end its Turbine block with `cancelAndIgnoreRemainingEvents()` (or `cancel()`), or the test hangs waiting for more items.
- **`runTest` virtual time** means no real waiting — your `emit`s and `delay`s are instant. Don't add `Thread.sleep`.
- **The de-dup is by `==`.** `StateFlow` compares with `equals`. Two structurally-equal data classes count as the same value and the second is dropped. This matters for the mini-project's delta flow.
