// Exercise 2 — Test a StateFlow<UiState> ViewModel with Turbine + MockK
//
// Goal: Drive a CheckoutViewModel through its full state machine — Loading ->
//       Content on success, Loading -> Error on failure — deterministically, on
//       the JVM, in milliseconds. You swap Dispatchers.Main with a TestDispatcher,
//       assert every emission with Turbine (not just the last), use a hand-written
//       FAKE for the stateful repository, and a MockK mock only for an analytics
//       interaction verification. This is lecture 1, §4–7, made concrete.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
//   PRODUCTION code (CheckoutUiState, OrderRepository, Analytics, CheckoutViewModel)
//   goes in app/src/main. The FAKE, the MainDispatcherExtension, and the @Test class
//   go in app/src/test. Markers below say which source set each block belongs in.
//
//   Run: ./gradlew :app:testDebugUnitTest   (no emulator).
//
// ACCEPTANCE CRITERIA
//
//   [ ] The Loading -> Content test asserts the Loading emission AND the Content
//       emission (StateFlow replays current value first — don't be off by one).
//   [ ] The Loading -> Error test drives the failure path via the fake's knob.
//   [ ] Time is controlled by a TestDispatcher; there is NO Thread.sleep and NO
//       real delay anywhere.
//   [ ] One coVerify proves analytics.logCheckout(...) was called exactly once on
//       success and NOT called on failure.
//   [ ] The suite passes run 1000 times in a row (it's deterministic). Builds 0 warnings.
//
// Build deps (build.gradle.kts):
//   testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
//   testImplementation("app.cash.turbine:turbine:1.2.0")
//   testImplementation("io.mockk:mockk:1.13.13")
//   testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
//   plugins { id("de.mannodermaus.android-junit5") }
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

// ============================================================================
// PRODUCTION CODE  —  app/src/main/java/com/crunch/checkout/
// ============================================================================

package com.crunch.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CartItem(val sku: String, val priceCents: Int, val qty: Int = 1)

sealed interface CheckoutUiState {
    data object Loading : CheckoutUiState
    data class Content(val items: List<CartItem>, val totalCents: Int) : CheckoutUiState
    data class Error(val message: String) : CheckoutUiState
}

interface OrderRepository {
    // Returns the cart, or throws if the load fails.
    suspend fun loadCart(): List<CartItem>
}

interface Analytics {
    fun logCheckout(itemCount: Int)
}

class CheckoutViewModel(
    private val repository: OrderRepository,
    private val analytics: Analytics
) : ViewModel() {

    private val _uiState = MutableStateFlow<CheckoutUiState>(CheckoutUiState.Loading)
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { CheckoutUiState.Loading }
            val next = try {
                val items = repository.loadCart()
                analytics.logCheckout(items.size)          // side effect on SUCCESS only
                CheckoutUiState.Content(items, items.sumOf { it.priceCents * it.qty })
            } catch (e: Exception) {
                CheckoutUiState.Error(e.message ?: "Unknown error")
            }
            _uiState.update { next }
        }
    }
}

// ============================================================================
// TEST CODE  —  app/src/test/java/com/crunch/checkout/
// ============================================================================
//
// (In a real project these are separate files; kept together here for the drill.)

/*
package com.crunch.checkout

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// --- The MainDispatcherExtension: swap Dispatchers.Main for a TestDispatcher. ---
class MainDispatcherExtension(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) = Dispatchers.setMain(testDispatcher)
    override fun afterEach(context: ExtensionContext) = Dispatchers.resetMain()
}

// --- A FAKE repository: a real, simple, reusable implementation with test knobs. ---
class FakeOrderRepository : OrderRepository {
    var cart: List<CartItem> = emptyList()
    var failWith: Exception? = null              // set to drive the error path

    override suspend fun loadCart(): List<CartItem> {
        failWith?.let { throw it }
        return cart
    }
}

class CheckoutViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = FakeOrderRepository()
    private val analytics: Analytics = mockk(relaxed = true)   // mock: we only verify a call

    private fun viewModel() = CheckoutViewModel(repository, analytics)

    // TODO 1: Write the success test.
    //   - Set repository.cart to one CartItem("sku-1", priceCents = 500, qty = 2).
    //   - Build the viewModel(), then collect uiState with Turbine's test { }.
    //   - assertEquals(Loading, awaitItem())   // StateFlow replays current value first!
    //   - call viewModel.load(); advanceUntilIdle()
    //   - val content = awaitItem(); assert it's Content with the item and totalCents = 1000.
    //   - cancelAndIgnoreRemainingEvents()   // StateFlow never completes.
    //   - After the turbine block: coVerify(exactly = 1) { analytics.logCheckout(1) }
    @Test
    fun `success path emits Loading then Content and logs once`() = runTest {
        // your code here
    }

    // TODO 2: Write the failure test.
    //   - Set repository.failWith = RuntimeException("network unavailable").
    //   - Collect uiState; assert Loading first, then after load() + advanceUntilIdle(),
    //     awaitItem() is Error("network unavailable").
    //   - coVerify(exactly = 0) { analytics.logCheckout(any()) }  // not logged on failure.
    @Test
    fun `failure path emits Loading then Error and does not log`() = runTest {
        // your code here
    }
}
*/

// ============================================================================
// WHY THIS TEST IS GOOD (write your own answer before reading):
//
//   - DETERMINISTIC: the TestDispatcher gives a virtual clock; the fake has no real
//     I/O. Run it 1000 times, get 1000 identical results. No flakiness possible.
//   - PRECISE: it asserts EVERY emission (Loading AND the terminal state), so a bug
//     that skips Loading or emits a wrong intermediate state is caught — not just the
//     final value. (Off-by-one trap: StateFlow replays its current value, so the
//     first awaitItem() is Loading, the second is the change.)
//   - ISOLATED: a fake repository (stateful, reusable) and a mock analytics (we only
//     care that a call happened). The right double for each: fake for behavior,
//     mock for interaction. (Lecture 1, §6.)
//   - LAYERED: if this goes red, the bug is in the ViewModel's state logic. Not the
//     DAO, not the UI. One red test, one broken layer.
// ============================================================================
// HINTS (read only if stuck > 15 min)
// ============================================================================
//
// - "awaitItem() returned Content but I expected Loading." StateFlow replays its
//   CURRENT value to a new collector. The first awaitItem() inside test { } is the
//   current state (Loading). Call load() AFTER the first awaitItem(), then await again.
//
// - "Test hangs / awaitItem() never returns." You forgot advanceUntilIdle() after
//   load(). With StandardTestDispatcher the launched coroutine is queued, not run,
//   until you advance. (UnconfinedTestDispatcher runs eagerly — but the extension
//   above uses Unconfined for Main, so the launch runs; if you still hang, you're
//   awaiting more items than were emitted — use cancelAndIgnoreRemainingEvents.)
//
// - "Turbine complains about unconsumed events." A StateFlow never completes, so you
//   must cancelAndIgnoreRemainingEvents() (or cancel()) at the end of test { }, not
//   awaitComplete(). awaitComplete is only for terminating flows.
//
// - "coVerify fails: 0 calls." The relaxed mock returns Unit for logCheckout, so the
//   call goes through. If coVerify sees 0, your success path didn't reach the log line
//   — check load() completed (advanceUntilIdle) and the repository didn't throw.
//
// - "Why a fake for the repo but a mock for analytics?" The repo has behavior worth
//   encoding once (state, the fail knob) and many tests reuse it -> fake. Analytics is
//   fire-and-forget; we only assert the interaction (called/not called) -> mock. Using
//   the right double for each is the senior signal here.
// ============================================================================
