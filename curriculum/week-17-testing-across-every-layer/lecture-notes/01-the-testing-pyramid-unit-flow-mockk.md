# Lecture 1 — The testing pyramid, JUnit 5, Flow, and MockK

> "A test that can't tell you *which* layer broke is a smoke alarm that goes off for burnt toast and house fires alike. The skill is not writing tests. It's writing tests that fail precisely."

This is the lecture that turns "I should write tests" into an engineering discipline with a budget and a shape. We start with the pyramid — the model that tells you how many tests of each kind to write and why — then we build the small tier all the way up: JUnit 5 on Android, deterministic coroutine testing with `runTest` and a `TestDispatcher`, Flow assertions with Turbine, and collaborator isolation with MockK. By the end you can take a `ViewModel` driving a `StateFlow<UiState>` and write a test that drives it through every state, deterministically, in milliseconds, on the JVM.

The frame for the whole week is one sentence: **the kind of test you write is a deliberate trade of speed for fidelity, and a senior engineer makes that trade on purpose.** Lecture 2 climbs to the medium and large tiers — Robolectric, Compose UI test, screenshots, Espresso. This lecture owns the base of the pyramid, where most of your tests should live.

---

## 1. The pyramid: speed versus fidelity

Picture a pyramid. At the wide base: **small** tests — pure JVM, no Android framework, no device. They run in **milliseconds**, hundreds per second, and you write **many** of them. In the middle: **medium** tests — they need *some* of the Android framework (a `Context`, a Room database, a composition) but still run on the JVM via Robolectric or a host-side Compose runtime. They run in **seconds** and you write **fewer**. At the narrow tip: **large** tests — the whole app on a real device or emulator, real wiring, real lifecycle. They run in **tens of seconds**, they're flaky-prone, and you write **very few**.

```
        /\
       /  \      LARGE  — Espresso, full instrumentation, real device.
      /    \             Tens of seconds. Flaky-prone. Write VERY FEW.
     /------\
    /        \   MEDIUM — Robolectric, Compose UI test (JVM), screenshot.
   /          \          Seconds. Some framework. Write FEWER.
  /------------\
 /              \ SMALL — JUnit + Turbine + MockK. Pure JVM, no device.
/________________\        Milliseconds. Write MANY.
```

Why this shape and not, say, a rectangle (equal numbers of each)? **Cost compounds.** A small test costs you a millisecond to run and almost nothing to keep green. A large test costs you tens of seconds, a device, and a steady drip of flakiness from real timing. If you write your whole suite as large tests, your CI takes an hour, fails randomly, and the team learns to hit "re-run" instead of reading the failure. If you write it as a pyramid, the bulk of your confidence comes from the fast, deterministic base, and the few slow tests at the top only have to prove the *wiring* — that the pieces, each already unit-tested, connect.

The senior reframe: **a test's value is confidence per second.** A unit test on a `ViewModel`'s state machine gives you enormous confidence for almost no time. An Espresso test that re-verifies that same state machine through the UI gives you a little *additional* confidence (the wiring) for a lot of time. Spend where the ratio is best — the base — and reserve the top for the wiring you can't test any other way.

## 2. What *not* to test

Before we write a single test, the discipline of *not* writing one. A senior reviewer flags these as readily as a missing test:

- **Don't test the framework.** You don't write a test that asserts `LazyColumn` scrolls or `Room` persists a row at all — those are Google's tests. You test *your* SQL, *your* state logic, *your* mapping.
- **Don't test getters and trivial mappings you can read at a glance.** A test that asserts `user.name` returns the name you set is noise. Test behavior with branches, not field access.
- **Don't assert on private implementation.** Test the public contract — the state a `ViewModel` emits, the rows a DAO returns — not the private method it called along the way. Tests coupled to implementation break on every refactor and teach the team to delete tests.
- **Don't screenshot a screen whose layout is still changing.** A golden you have to re-record every commit is a maintenance tax, not a regression net. Screenshot the *stable* states.
- **Don't write a large test for something a small test covers.** If a unit test on the `ViewModel` proves the error state, don't *also* prove it through Espresso. Prove the *wiring* once at the top; prove the *logic* at the bottom.

"What not to test" is half the skill. The other half is the next five sections.

## 3. JUnit 5 on Android (and the JUnit 4 split)

Android's *instrumentation* tests (the ones that run on a device, in `androidTest`) still default to **JUnit 4** — the `AndroidJUnitRunner` is a JUnit 4 runner. But your *unit* tests (in `test`, on the JVM) can and should use **JUnit 5**, which is a cleaner, more expressive framework: lifecycle annotations that read better, first-class parameterized tests, nested test classes, and `assertThrows` that returns the exception.

You enable it with the `de.mannodermaus.android-junit5` Gradle plugin:

```kotlin
// app/build.gradle.kts (or feature module)
plugins {
    id("de.mannodermaus.android-junit5")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
}
```

A JUnit 5 test looks like this — note `@BeforeEach`/`@AfterEach` (not JUnit 4's `@Before`/`@After`), and `org.junit.jupiter.api` imports:

```kotlin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class PriceCalculatorTest {

    private lateinit var calculator: PriceCalculator

    @BeforeEach
    fun setUp() {
        calculator = PriceCalculator(taxRate = 0.20)
    }

    @Test
    fun `total applies tax to subtotal`() {
        val total = calculator.total(subtotalCents = 1000)
        assertEquals(1200, total)            // 1000 + 20% = 1200
    }

    @Test
    fun `negative subtotal is rejected`() {
        assertThrows<IllegalArgumentException> {
            calculator.total(subtotalCents = -1)
        }
    }
}
```

Two JUnit 5 features you'll reach for often:

**Parameterized tests** collapse a table of cases into one method:

```kotlin
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DiscountTest {
    @ParameterizedTest(name = "{0} cents at tier {1} -> {2} cents")
    @CsvSource(
        "1000, GOLD, 800",
        "1000, SILVER, 900",
        "1000, NONE, 1000"
    )
    fun `discount applies by tier`(subtotal: Int, tier: String, expected: Int) {
        assertEquals(expected, applyDiscount(subtotal, Tier.valueOf(tier)))
    }
}
```

**Nested classes** group related cases and let setup cascade:

```kotlin
import org.junit.jupiter.api.Nested

class CartTest {
    @Nested
    inner class WhenEmpty {
        @Test fun `total is zero`() { /* ... */ }
        @Test fun `cannot checkout`() { /* ... */ }
    }
    @Nested
    inner class WithItems {
        @Test fun `total sums line items`() { /* ... */ }
    }
}
```

The split to remember: **`test/` = JUnit 5, `androidTest/` = JUnit 4.** Your Espresso and on-device tests stay JUnit 4; your JVM unit tests get JUnit 5. Don't fight it.

## 4. Deterministic coroutines: `runTest` and `TestDispatcher`

Every modern Android `ViewModel` launches coroutines. A test of that `ViewModel` must not depend on *wall-clock* time — `delay(1000)` cannot actually wait a second, or your suite takes minutes and flakes. `kotlinx-coroutines-test` gives you a **virtual clock** you control.

`runTest` is the entry point. It runs your test body in a `TestScope` with a **`TestDispatcher`** whose clock you advance manually. A `delay(1000)` inside it completes *instantly* in virtual time:

```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TimedThingTest {
    @Test
    fun `delay completes in virtual time, instantly`() = runTest {
        var done = false
        // This 'delay' does NOT wait a real second — the test clock skips it.
        delay(1000)
        done = true
        assertEquals(true, done)
        // currentTime is the virtual clock; the test ran in ~0 real ms.
        assertEquals(1000, currentTime)
    }
}
```

Two dispatcher flavors, and the difference matters:

- **`StandardTestDispatcher`** (the default in `runTest`) queues coroutines and runs them only when you *advance* the clock — `advanceUntilIdle()` runs everything pending, `advanceTimeBy(ms)` runs up to a point. This gives you fine control over *intermediate* states, which is exactly what you need to assert "it went to Loading, then Content."
- **`UnconfinedTestDispatcher`** runs coroutines eagerly, as far as they can go, the moment they're launched. Convenient when you only care about the *final* state and don't want to call `advanceUntilIdle()`, but it hides intermediate states — so prefer Standard when you're asserting a sequence.

The other half: a `ViewModel` uses `viewModelScope`, which is bound to `Dispatchers.Main`. On the JVM there is no `Main` dispatcher, so you must **replace it** for tests. The idiomatic tool is a JUnit rule (Now-In-Android ships exactly this):

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

// JUnit 5 version: an Extension that swaps Dispatchers.Main for a TestDispatcher.
class MainDispatcherExtension(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        Dispatchers.setMain(testDispatcher)
    }
    override fun afterEach(context: ExtensionContext) {
        Dispatchers.resetMain()
    }
}
```

(In JUnit 4 instrumentation code, this is a `TestWatcher` rule instead — same idea, different base class.) Register it on your test class with `@JvmField @RegisterExtension val mainDispatcher = MainDispatcherExtension()` and every `viewModelScope.launch` now runs on your controlled dispatcher.

## 5. Flow assertions with Turbine

A `StateFlow<UiState>` emits a *sequence*. The naive test collects into a list and asserts on it after the fact — fragile, racy, and it can't express "wait for the next emission." **Turbine** turns a Flow into a suspendable, assertable channel:

```kotlin
import app.cash.turbine.test
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TurbineBasicsTest {
    @Test
    fun `flow emits 1, 2, 3 then completes`() = runTest {
        flowOf(1, 2, 3).test {
            assertEquals(1, awaitItem())
            assertEquals(2, awaitItem())
            assertEquals(3, awaitItem())
            awaitComplete()             // assert the flow finished
        }
    }
}
```

The Turbine vocabulary you'll use all week:

- **`awaitItem()`** — suspend until the next emission, return it. The workhorse.
- **`awaitComplete()`** — assert the flow completed (only for terminating flows; a `StateFlow` never completes, so you don't call this on one).
- **`awaitError()`** — assert the flow threw, return the throwable.
- **`expectNoEvents()`** — assert nothing is pending right now (used to prove a debounce hasn't fired yet).
- **`cancelAndIgnoreRemainingEvents()`** — for a hot flow like `StateFlow` that never completes; you assert the items you care about, then cancel. Turbine *requires* you consume or cancel all events, which is exactly the discipline that catches "I forgot to handle the error emission."
- **`turbineScope { }`** with `flowA.testIn(this)` — collect two flows at once when you need to assert on both.

A critical detail for `StateFlow`: it always replays its **current value** to a new collector. So the *first* `awaitItem()` is the current state, not the next change. A `StateFlow(UiState.Loading)` that then emits `Content` gives you `Loading` on the first `awaitItem()` and `Content` on the second. Get this wrong and your assertions are off by one.

## 6. MockK: isolating collaborators

To unit-test a `ViewModel`, you must isolate it from its real dependencies — you don't want a real network call or a real database in a millisecond test. **MockK** is the Kotlin-native mocking library; it speaks `suspend`, `object`, sealed classes, and Kotlin's nullability natively (Mockito, the Java library, fights all of these).

```kotlin
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

interface OrderRepository {
    suspend fun placeOrder(cartId: String): OrderResult
}

class CheckoutServiceTest {
    private val repository: OrderRepository = mockk()

    @Test
    fun `placeOrder returns the repository result`() = runTest {
        // coEvery for a SUSPEND function; every for non-suspend.
        coEvery { repository.placeOrder("cart-1") } returns OrderResult.Success("order-9")

        val service = CheckoutService(repository)
        val result = service.checkout("cart-1")

        assertEquals(OrderResult.Success("order-9"), result)
        coVerify(exactly = 1) { repository.placeOrder("cart-1") }   // it was called once
    }
}
```

The MockK vocabulary:

- **`mockk<T>()`** — a strict mock; every call you didn't stub throws, which catches "the code called something I didn't expect."
- **`mockk<T>(relaxed = true)`** — a relaxed mock; unstubbed calls return sensible defaults (0, false, empty). Use it when the boilerplate of stubbing every method isn't load-bearing.
- **`every { } returns` / `coEvery { } returns`** — stub a return; the `co` prefix is for suspend functions. `returnsMany(...)` for a sequence; `throws` to stub an exception.
- **`verify { } / coVerify { }`** — assert a call happened. `exactly = n`, `atLeast`, `wasNot Called`.
- **`slot<T>()` + `capture(slot)`** — capture the argument a method was called with, to assert on it.
- **`mockkStatic("...")` / `mockkObject(...)`** — mock top-level/static functions and `object`s; use sparingly — needing this often is a design smell.

### Fakes vs. mocks — the senior decision

Here is a judgment call the Now-In-Android team made deliberately, and you should understand it: **prefer a hand-written fake over a mock for your own repositories and data sources.** A *mock* stubs each call inline in each test (`coEvery { repo.x() } returns y`); a *fake* is a real, simple implementation you write once (an in-memory list backing a `Flow`) and reuse everywhere.

```kotlin
// A FAKE repository — a real, simple, reusable implementation.
class FakeOrderRepository : OrderRepository {
    private val orders = MutableStateFlow<List<Order>>(emptyList())
    var nextResult: OrderResult = OrderResult.Success("order-1")   // test knob

    override suspend fun placeOrder(cartId: String): OrderResult {
        if (nextResult is OrderResult.Success) {
            orders.update { it + Order(cartId) }
        }
        return nextResult
    }
    fun observeOrders(): Flow<List<Order>> = orders
}
```

When to pick which:

- **Fakes** for *your* components with non-trivial behavior — repositories, data sources, anything stateful. The fake encodes the contract once; tests stay readable ("set `nextResult`, call, assert"); and the fake catches contract violations a per-test mock can't. Now-In-Android uses fakes almost everywhere.
- **Mocks** for *interaction* verification (did we call `analytics.log(...)` exactly once?) and for collaborators where you only care about one stubbed call. A mock is faster to write for a one-off; a fake is better when many tests share the collaborator.

The smell: if your test is ten lines of `coEvery` setup before one line of assertion, you wanted a fake.

## 7. Putting it together: a `ViewModel` test

Here is the whole small tier in one test — a `CheckoutViewModel` driving a `StateFlow<CheckoutUiState>`, tested with the `MainDispatcherExtension`, a fake repository, and Turbine:

```kotlin
sealed interface CheckoutUiState {
    data object Loading : CheckoutUiState
    data class Content(val items: List<CartItem>, val totalCents: Int) : CheckoutUiState
    data class Error(val message: String) : CheckoutUiState
}

class CheckoutViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = FakeOrderRepository()

    @Test
    fun `loads cart then emits Content`() = runTest {
        repository.cart = listOf(CartItem("sku-1", priceCents = 500))
        val viewModel = CheckoutViewModel(repository)

        viewModel.uiState.test {
            // StateFlow replays current value first.
            assertEquals(CheckoutUiState.Loading, awaitItem())
            viewModel.load()
            advanceUntilIdle()                       // run the launched coroutine
            val content = awaitItem()
            assertEquals(
                CheckoutUiState.Content(
                    items = listOf(CartItem("sku-1", 500)),
                    totalCents = 500
                ),
                content
            )
            cancelAndIgnoreRemainingEvents()         // StateFlow never completes
        }
    }

    @Test
    fun `repository failure emits Error`() = runTest {
        repository.failNext = true
        val viewModel = CheckoutViewModel(repository)

        viewModel.uiState.test {
            assertEquals(CheckoutUiState.Loading, awaitItem())
            viewModel.load()
            advanceUntilIdle()
            val state = awaitItem()
            assertEquals(CheckoutUiState.Error("network unavailable"), state)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

Read what this test *is*: deterministic (virtual clock, no real time), isolated (a fake repository, no real network), precise (it asserts *every* state, not just the last), and fast (milliseconds). If it goes red, the bug is in the `ViewModel`'s state logic — not the DAO, not the UI, not the wiring. That precision is the whole point.

## 8. Recap

The base of the pyramid is where most of your confidence comes from, and it's the cheapest to keep green. Five tools build it:

1. **The pyramid is a budget.** Many small, fewer medium, very few large — because cost compounds and the base gives the best confidence-per-second. And half the skill is knowing what *not* to test.
2. **JUnit 5 for JVM tests, JUnit 4 for instrumentation.** Parameterized tests, nested classes, `assertThrows`. Live with the split.
3. **`runTest` + `TestDispatcher` give you a virtual clock.** `delay` is instant; `advanceUntilIdle` runs pending work; the `MainDispatcherExtension` swaps `Dispatchers.Main`. Deterministic time is non-negotiable.
4. **Turbine asserts on Flow sequences.** `awaitItem` per emission; remember `StateFlow` replays its current value first; consume or cancel every event.
5. **MockK isolates collaborators — but prefer fakes for stateful components.** `coEvery`/`coVerify` for suspend; a hand-written fake when many tests share a collaborator.

Lecture 2 climbs the pyramid: Robolectric to run the Android framework on the JVM (and test a real DAO), Compose UI test to drive a screen, Roborazzi to catch pixel regressions, and Espresso plus Hilt for one honest end-to-end smoke. The exercises drill the ViewModel test and a tier-selection decision; the challenge hands you a flaky suite to fix; the mini-project tests `:feature-checkout` at every layer at once. Build the base solid, then climb.
