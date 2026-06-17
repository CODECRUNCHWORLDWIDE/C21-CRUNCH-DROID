// Exercise 3 — callbackFlow bridge without a leak: prove awaitClose unregisters
//
// Goal: Bridge a fake callback-based "sensor" into a Flow with callbackFlow, and
//       prove with an assertion that when the collector is cancelled, awaitClose
//       runs and UNREGISTERS the listener — so no listener leaks. The missing
//       awaitClose is the classic Android leak; here you make the fix testable.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// A Turbine + kotlinx-coroutines-test suite. Drop into src/test/kotlin with
// kotlinx-coroutines-test and app.cash.turbine:turbine on the test classpath.
//
//   1. Add to your test target.
//   2. Run with `./gradlew test`.
//   3. Read the assertions: emissions arrive while collecting; after cancel, the
//      fake sensor has ZERO registered listeners.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] `bridgeEmitsCallbackValues` passes — callbacks become flow emissions.
//   [ ] `awaitCloseUnregistersListener` passes — after the collector is cancelled,
//       the sensor has 0 listeners (no leak).
//   [ ] `missingAwaitCloseWouldLeak` documents (in a comment) why omitting
//       awaitClose leaks.
//   [ ] You can explain why callbackFlow (not flow {}) is required to bridge a
//       callback API.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.droid

import app.cash.turbine.test
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ----------------------------------------------------------------------------
// A fake callback-based "sensor" — stands in for LocationManager, a Broadcast
// Receiver, or any third-party SDK that takes a listener. It tracks how many
// listeners are currently registered so a test can prove unregistration.
// ----------------------------------------------------------------------------

fun interface SensorListener {
    fun onReading(value: Int)
}

class FakeSensor {
    private val listeners = mutableListOf<SensorListener>()

    val listenerCount: Int get() = listeners.size

    fun register(listener: SensorListener) {
        listeners.add(listener)
    }

    fun unregister(listener: SensorListener) {
        listeners.remove(listener)
    }

    // Drive a reading to every registered listener (the SDK calling you back).
    fun push(value: Int) {
        listeners.toList().forEach { it.onReading(value) }
    }
}

// ----------------------------------------------------------------------------
// The bridge: turn the callback sensor into a Flow<Int>. This is lecture 2, §5
// verbatim — register in the body, trySend each callback, UNREGISTER in awaitClose.
// ----------------------------------------------------------------------------

fun FakeSensor.readings(): Flow<Int> = callbackFlow {
    // 1. The listener: each callback becomes a flow emission via trySend.
    val listener = SensorListener { value -> trySend(value) }

    // 2. Register it with the callback API.
    register(listener)

    // 3. CRITICAL: awaitClose runs when the flow is cancelled (collector left).
    //    Unregister here, or the sensor keeps a reference to a dead listener.
    awaitClose {
        unregister(listener)
    }
}

// ----------------------------------------------------------------------------
// Tests
// ----------------------------------------------------------------------------

class CallbackFlowBridgeTests {

    @Test
    fun bridgeEmitsCallbackValues() = runTest {
        val sensor = FakeSensor()

        sensor.readings().test {
            // Once collection starts, the listener is registered and pushes flow through.
            sensor.push(10)
            assertEquals(10, awaitItem())
            sensor.push(20)
            assertEquals(20, awaitItem())
            sensor.push(30)
            assertEquals(30, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun awaitCloseUnregistersListener() = runTest {
        val sensor = FakeSensor()
        assertEquals(0, sensor.listenerCount, "no listener before collection (cold)")

        sensor.readings().test {
            // While collecting, exactly one listener is registered.
            sensor.push(1)
            assertEquals(1, awaitItem())
            assertEquals(1, sensor.listenerCount, "one listener while collecting")
            cancelAndIgnoreRemainingEvents()
        }

        // After the collector is cancelled, awaitClose ran and removed the listener.
        // THIS is the proof there is no leak: the sensor holds nobody.
        assertEquals(0, sensor.listenerCount, "awaitClose must unregister the listener — else it leaks!")
    }

    @Test
    fun coldUntilCollected() = runTest {
        val sensor = FakeSensor()
        // callbackFlow is cold: building readings() registers NOTHING.
        val flow = sensor.readings()
        assertEquals(0, sensor.listenerCount, "callbackFlow must not register before collection")

        // Pushing before anyone collects reaches no one.
        sensor.push(99)
        assertEquals(0, sensor.listenerCount)

        flow.test {
            sensor.push(7)
            assertEquals(7, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, sensor.listenerCount)
    }
}

// ----------------------------------------------------------------------------
// WHY omitting awaitClose leaks (and why callbackFlow not flow{}):
//
//   If `readings()` registered the listener but never unregistered it, then after
//   the collector's scope is cancelled the FakeSensor would still hold the listener
//   in its `listeners` list — and on Android, that listener closes over your screen/
//   ViewModel, so the SDK keeps your screen alive forever: a memory leak. awaitClose
//   is the one line that runs on cancellation to undo the registration.
//
//   callbackFlow (not a plain flow {}) is required because the SDK calls the listener
//   on ITS OWN thread, outside the flow's coroutine. A plain flow {} forbids emitting
//   from a different context (context preservation). callbackFlow is backed by a
//   channel, so trySend from any thread is allowed — that's exactly what a callback
//   bridge needs.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - `awaitClose { }` is MANDATORY in callbackFlow — the builder throws at runtime if
//   you forget it, because a callback bridge without cleanup is always a leak. Put
//   the unregister there.
//
// - `trySend` is the non-suspending send used inside a synchronous callback. It
//   returns a ChannelResult you can ignore here; if the buffer is full it would drop,
//   which for a sensor stream is usually fine (latest-ish wins).
//
// - The leak proof is `listenerCount == 0` AFTER the Turbine block ends. Turbine's
//   `cancelAndIgnoreRemainingEvents()` cancels the collector, which cancels the
//   callbackFlow, which runs awaitClose. Assert listenerCount OUTSIDE the test block.
//
// - To FEEL the leak, temporarily delete the `unregister(listener)` line and re-run
//   `awaitCloseUnregistersListener`: it fails with listenerCount == 1. Seeing that
//   failure is the lesson. Put the line back.
//
// ----------------------------------------------------------------------------
