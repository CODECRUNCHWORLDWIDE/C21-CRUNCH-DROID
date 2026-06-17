# Lecture 2 — Robolectric, Compose UI test, screenshots, and Espresso

> "The base of the pyramid proves your logic. The middle proves your framework code without a device. The tip proves the wiring on the real thing. Climb deliberately — every rung up costs you seconds and steals determinism."

Lecture 1 owned the small tier: JUnit 5, deterministic coroutines, Turbine, MockK. This lecture climbs. We run the **Android framework on the JVM** with Robolectric (and test a real Room DAO), we drive a **composable** with `createComposeRule()`, we catch **pixel regressions** with Roborazzi, and we write **one honest end-to-end** Espresso test wired through Hilt. By the end you can place any behavior on the right rung and know what each rung costs.

The through-line: **fidelity rises and speed falls as you climb, so climb only as far as the confidence requires.** A DAO's SQL needs the real SQLite — Robolectric, medium tier. A screen's rendering needs a composition — Compose UI test, medium tier. The end-to-end wiring needs the real app — Espresso, large tier, and you write *one*.

---

## 1. Robolectric: the Android framework on the JVM

Some of your code touches the Android framework — a class that needs a `Context`, a Room DAO that needs SQLite, a `SharedPreferences` reader. You *could* test it on a device (instrumentation), but that's slow and flaky for code whose logic doesn't actually depend on a real device. **Robolectric** runs a reimplementation of the Android framework *on the JVM*, so these tests run in seconds in your `test/` source set — no emulator.

Robolectric works by providing **shadow** objects: JVM stand-ins for Android classes (`ShadowLog`, `ShadowSystemClock`, a SQLite that runs on the host). Your code calls the real Android API; Robolectric intercepts it and routes to the shadow.

```kotlin
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.jupiter.api.Test                    // JUnit 5 + the Robolectric extension
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// JUnit 4 form (most common; Robolectric's JUnit 5 support exists but is younger):
@org.junit.runner.RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContextThingTest {
    @org.junit.Test
    fun `reads a string resource`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val label = context.getString(R.string.checkout_title)
        org.junit.Assert.assertEquals("Checkout", label)
    }
}
```

Note: Robolectric is most mature on **JUnit 4**, so Robolectric tests often live in JUnit 4 even when the rest of your unit tests are JUnit 5. The `@Config(sdk = [34])` pins which Android API level the shadows emulate.

### Testing a Room DAO with Robolectric

This is the canonical medium test. Build a real Room database **in memory** (it lives only for the test, never touches disk), exercise the DAO, assert on the rows:

```kotlin
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class CartDaoTest {

    private lateinit var db: CheckoutDatabase
    private lateinit var dao: CartDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, CheckoutDatabase::class.java)
            .allowMainThreadQueries()        // fine in a test; never in production
            .build()
        dao = db.cartDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `insert then query returns the row`() = runTest {
        dao.insert(CartItemEntity(sku = "sku-1", priceCents = 500, qty = 2))
        val items = dao.observeAll().first()         // first emission of the Flow
        assertEquals(1, items.size)
        assertEquals("sku-1", items[0].sku)
        assertEquals(2, items[0].qty)
    }

    @Test
    fun `total sums price times quantity`() = runTest {
        dao.insert(CartItemEntity("a", priceCents = 500, qty = 2))   // 1000
        dao.insert(CartItemEntity("b", priceCents = 300, qty = 1))   // 300
        assertEquals(1300, dao.totalCents().first())                 // your @Query SUM(...)
    }
}
```

This test runs your **actual SQL** — the `@Query("SELECT SUM(...)")` aggregate, the relations, the type converters — against a real SQLite, in seconds, with no device. If the SQL is wrong, *this* test goes red and nothing else does. That's the precision the week is about.

### When Robolectric, when a real device?

Robolectric is a *reimplementation* — high-fidelity but not perfect. Use it for framework code whose behavior doesn't depend on a real device's quirks: DAOs, `Context` resource reads, parsing, formatting, a `WorkManager` test via `WorkManagerTestInitHelper`. Use a **real instrumentation test** when the behavior is genuinely device-dependent: hardware sensors, real GPU rendering, true multi-process behavior, or the final end-to-end wiring smoke. The honest rule: Robolectric for "does my code use the framework correctly," a device for "does it actually work on the thing."

## 2. Compose UI testing with `createComposeRule()`

A Compose UI test drives a real composition and asserts on what it renders — without a `View` hierarchy, by querying the **semantics tree** Compose maintains for accessibility and testing. The same tree TalkBack reads is the one your test queries; this is why accessible composables are testable composables.

```kotlin
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class CheckoutButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping checkout invokes the callback`() {
        var clicked = false
        composeRule.setContent {
            CheckoutButton(enabled = true, onClick = { clicked = true })
        }

        composeRule.onNodeWithText("Place order").assertIsDisplayed()
        composeRule.onNodeWithText("Place order").performClick()

        assert(clicked)
    }
}
```

The Compose UI test vocabulary:

- **Finders.** `onNodeWithText("...")`, `onNodeWithContentDescription("...")` (for icons/images), `onNodeWithTag("...")` (a `Modifier.testTag("...")` you add for stable, text-independent selection). `onAllNodes...` for collections.
- **Actions.** `performClick()`, `performTextInput("...")`, `performScrollTo()`, `performTouchInput { swipeUp() }`.
- **Assertions.** `assertIsDisplayed()`, `assertExists()`, `assertTextEquals("...")`, `assertIsEnabled()`/`assertIsNotEnabled()`, `assertIsOn()` for toggles.
- **`mainClock`.** `composeRule.mainClock.autoAdvance = false` then `advanceTimeBy(ms)` — control animation and `LaunchedEffect` timing deterministically, the Compose analogue of the `TestDispatcher`.

### `createComposeRule()` vs `createAndroidComposeRule()`

- **`createComposeRule()`** — hosts the composition without a specific Activity. Use it for testing composables in isolation (a button, a row, a screen given fake state). This is the one you reach for most.
- **`createAndroidComposeRule<MyActivity>()`** — launches a real Activity and tests its content. Use it when the screen needs the Activity (a `ViewModel` from `hiltViewModel()`, an intent extra). Heavier; reach for it only when isolation isn't enough.

### Compose UI tests on the JVM (the line-mover)

Historically a Compose UI test was a *large* (on-device) test. Now you can run the **same test** on the JVM via Robolectric by putting it in `test/` with the right setup (`@RunWith(RobolectricTestRunner::class)` and the Compose test dependencies). A test that took an emulator and tens of seconds now runs in seconds on CI with no device. This is the Compose-blurs-the-pyramid point from lecture 1: a whole tier of tests moved down a rung. Now-In-Android runs its Compose screenshot and UI tests on the JVM for exactly this reason.

## 3. Screenshot testing: Roborazzi and Paparazzi

Assertions catch *logic* regressions. They do not catch the day someone changes a padding, a color, or a font weight and the screen looks wrong but still "works." A **screenshot test** renders a composable to a PNG, compares it to a committed **golden** image, and fails CI on a pixel diff. Both leading tools run on the **JVM, no device** — fast and deterministic.

**Roborazzi** (the modern, actively-favored option; integrates with Robolectric and Compose):

```kotlin
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp-mdpi")
class CheckoutScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `checkout content state`() {
        composeRule.setContent {
            AppTheme {
                CheckoutScreen(state = CheckoutUiState.Content(sampleItems, totalCents = 1300))
            }
        }
        composeRule.onRoot().captureRoboImage()      // writes/compares the golden PNG
    }

    @Test
    fun `checkout error state`() {
        composeRule.setContent {
            AppTheme { CheckoutScreen(state = CheckoutUiState.Error("Network unavailable")) }
        }
        composeRule.onRoot().captureRoboImage()
    }
}
```

You run `./gradlew recordRoborazziDebug` once to write the goldens (and commit the PNGs), then `verifyRoborazziDebug` on CI to fail on any diff.

**Paparazzi** (Cash App's alternative; a `@get:Rule` that renders without Robolectric):

```kotlin
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class CheckoutPaparazziTest {
    @get:Rule val paparazzi = Paparazzi()

    @Test fun `content state`() {
        paparazzi.snapshot {
            AppTheme { CheckoutScreen(state = CheckoutUiState.Content(sampleItems, 1300)) }
        }
    }
}
```

### The state-matrix discipline, and the maintenance trap

The senior move is **one golden per meaningful state**: loading, content, empty, error, and — if relevant — dark theme and a large-font configuration. That matrix is exactly the set of states an assertion can't fully cover ("does the error banner look right?") and where visual regressions hide.

The trap: a screenshot test for a screen whose design is still churning means re-recording goldens every commit, which trains the team to run `record` blindly — defeating the point. **Screenshot the stable states.** And keep goldens small and configuration-pinned (`qualifiers = "w400dp-h800dp-mdpi"`) so they're deterministic across machines; a golden recorded on one DPI and verified on another diffs forever.

## 4. Espresso and the end-to-end smoke (one, not fifty)

At the tip of the pyramid: the real app, on a device, with real wiring. **Espresso** is the instrumentation UI framework — `onView`/`withId`/`perform`/`check` for `View`s, with Compose interop for Compose screens. You write **one** end-to-end smoke that drives a real user journey (cart → checkout → confirmed), and you write it not to re-test the logic (the base did that) but to prove the **wiring**: that Hilt assembled the graph, that navigation connects the screens, that the real ViewModel/repository/DAO actually talk.

```kotlin
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class CheckoutEndToEndTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before fun inject() = hiltRule.inject()

    @Test
    fun `add to cart, checkout, see confirmation`() {
        composeRule.onNodeWithText("Add to cart").performClick()
        composeRule.onNodeWithText("Cart").performClick()
        composeRule.onNodeWithText("Place order").performClick()
        composeRule.onNodeWithText("Order confirmed").assertExists()
    }
}
```

### Injecting fakes through Hilt

An end-to-end test must not hit a real backend (flaky, slow, and you'd need a live server). You swap the real network binding for a fake using Hilt's test API: `@TestInstallIn` (or `@UninstallModules` + a test module) replaces the production module, and a custom test runner uses `HiltTestApplication`:

```kotlin
// A custom runner so instrumentation uses HiltTestApplication.
class HiltTestRunner : androidx.test.runner.AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: android.content.Context?) =
        super.newApplication(cl, dagger.hilt.android.testing.HiltTestApplication::class.java.name, context)
}
// build.gradle.kts: testInstrumentationRunner = "com.crunch.checkout.HiltTestRunner"
```

```kotlin
// Replace the production network module with a fake for tests.
@dagger.hilt.testing.TestInstallIn(
    components = [dagger.hilt.components.SingletonComponent::class],
    replaces = [NetworkModule::class]
)
@dagger.Module
object FakeNetworkModule {
    @dagger.Provides
    fun provideOrderApi(): OrderApi = FakeOrderApi()   // deterministic, in-memory
}
```

Now the end-to-end test drives the **real** ViewModel, navigation, and DAO, but against a **fake** network — so it's deterministic and fast enough to keep, while still proving the wiring no unit test can. That's why you write *one*: it's expensive, it's the slowest and flakiest tier, and its job (the wiring) only needs to be proven once. Fifty Espresso tests is a CI tax that re-proves logic the base already owns.

### IdlingResource and the async problem

Espresso auto-waits for the main thread and (with the Compose rule) for the composition to be idle, but it doesn't know about *your* async work (a background sync). When you need it to wait, register an `IdlingResource` that reports busy/idle, or — better — design the test so the fake completes synchronously. Never `Thread.sleep`; a sleep is a flake waiting for a slow CI machine.

```kotlin
// An IdlingResource backed by a counter your code increments around async work.
object NetworkIdlingResource : androidx.test.espresso.IdlingResource {
    private val inFlight = java.util.concurrent.atomic.AtomicInteger(0)
    private var callback: androidx.test.espresso.IdlingResource.ResourceCallback? = null

    override fun getName() = "NetworkIdlingResource"
    override fun isIdleNow() = inFlight.get() == 0
    override fun registerIdleTransitionCallback(cb: androidx.test.espresso.IdlingResource.ResourceCallback?) {
        callback = cb
    }
    fun increment() { inFlight.incrementAndGet() }
    fun decrement() {
        if (inFlight.decrementAndGet() == 0) callback?.onTransitionToIdle()
    }
}
```

But notice the cost: an `IdlingResource` couples your *production* code to your test infrastructure (the counter lives in `main`). The cleaner answer for a deterministic smoke is to make the swapped-in fake complete synchronously (it returns immediately from the fake API), so there's no async to wait on at all. Reach for `IdlingResource` only when the async is genuinely unavoidable in the journey under test — and never, ever reach for `Thread.sleep`.

### Espresso-Compose interop

Many real apps are mixed — some `View` screens, some Compose. Espresso's `onView(withId(...))` drives the `View` parts; the Compose test rule's `onNodeWithText(...)` drives the Compose parts; and they cooperate in one test because the `createAndroidComposeRule` synchronizes with both the main thread and the composition. The smoke test above is pure Compose, but the moment your journey crosses into a legacy `View` screen, you mix the two finders in the same test body — Espresso for the `View` hop, the Compose rule for the Compose hop. Knowing both is the price of testing a real, half-migrated app.

## 5. Test structure: fixtures, fakes, and the test-only module

A layered suite shares a lot — the `MainDispatcherExtension`, the `FakeOrderRepository`, a `TestData` object of sample carts. Duplicating these across modules rots fast. Two patterns keep it clean:

**The `testFixtures` source set** (Gradle) lets a module publish test-only code that *other modules' tests* can depend on:

```kotlin
// core/data/build.gradle.kts
android { testFixtures { enable = true } }
// put FakeOrderRepository in core/data/src/testFixtures/
// feature/checkout/build.gradle.kts
dependencies { testImplementation(testFixtures(project(":core:data"))) }
```

**The `:core:testing` module pattern** (Now-In-Android) — a dedicated module holding the `MainDispatcherRule`, the fakes, and the test data, that every feature module's tests depend on. Either way, the rule is: **test doubles never live in `main`.** A `FakeRepository` in your production source set ships to users and is a code smell a reviewer will flag instantly.

### Flaky-test hygiene (the determinism checklist)

A flaky test is worse than no test — it teaches the team to ignore red. The checklist a senior reviewer applies:

- **No real clock.** `TestDispatcher` for coroutines, `mainClock` for Compose. Never `Thread.sleep`, never `System.currentTimeMillis()` in an assertion.
- **No real network or disk.** Fakes and in-memory databases. A test that hits the internet fails when the internet does.
- **No shared mutable state between tests.** Fresh fakes per test (build them in `@BeforeEach`); a static counter shared across tests fails when tests run in a different order.
- **No order dependence.** Each test sets up its own world and tears it down. If test B only passes after test A ran, both are broken.
- **No reliance on emission timing.** Turbine's `awaitItem` and the virtual clock, not "collect for 100ms and hope."

## 6. Recap

The middle and tip of the pyramid prove what the base can't — framework correctness and wiring — at a rising cost you pay only when the confidence requires it:

1. **Robolectric runs the framework on the JVM.** Test a real DAO against an in-memory Room database in seconds, no device. Use a real device only for genuinely device-dependent behavior.
2. **Compose UI test drives the semantics tree.** `createComposeRule`, finders, actions, assertions, `mainClock` for timing — and it runs on the JVM now, moving a whole tier down a rung.
3. **Screenshot tests catch pixel regressions.** Roborazzi (or Paparazzi), one golden per meaningful state, on the JVM. Screenshot the *stable* states or pay a re-record tax.
4. **One Espresso end-to-end smoke proves the wiring.** `@HiltAndroidTest` swaps a fake network into the real graph; you write *one* because the tip is expensive and the base already owns the logic.
5. **Structure keeps it sane.** `testFixtures` / a `:core:testing` module for shared fakes; test doubles never in `main`; and a determinism checklist that keeps the suite from flaking.

You now have the whole pyramid: logic at the base, framework in the middle, wiring at the tip, each test failing precisely on its own layer. The exercises make you pick the tier for eight behaviors and write a Compose UI plus screenshot test; the challenge hands you a flaky suite to autopsy; the mini-project tests `:feature-checkout` at *every* layer at once and proves each layer breaks independently. Make every failure legible.
