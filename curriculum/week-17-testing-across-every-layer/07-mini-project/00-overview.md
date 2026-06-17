# Mini-Project — `:feature-checkout`, tested at every layer

This week you build a **`:feature-checkout` module** and test it at *every layer of the pyramid at once* — unit tests on the `ViewModel`, Robolectric tests on the Room DAO, Compose UI tests on the screen, Roborazzi screenshot tests on every Material 3 state, and one Espresso end-to-end smoke. By the end you have a module whose CI run is green, fast, and *legible*: a single red test points at a single broken layer, and you know which one before you open the file.

The point of the project is not "write a checkout feature." It's to build a feature whose test suite is **layered correctly** — so the ViewModel test, the DAO test, the UI test, the screenshot test, and the Espresso smoke each break *independently*. That property — "one red test, one broken layer" — is the senior instinct this week installs, and it's what makes a real test suite a tool instead of a chore.

This module sits on top of the architecture you already own: MVVM with UDF (Week 12), Hilt (Week 13), Room (Week 14). You're not redesigning any of it — you're proving it works, at every layer, deterministically.

---

## Where you're starting from

A multi-module Compose project (the shape from Weeks 13–16): an `:app` module, a Hilt graph, a Room database, and a Compose theme. If you don't have one, scaffold a minimal version — the testing is the point, not the app size. You'll add one new module: `:feature-checkout`.

## What you're building toward

By the end you have, in `:feature-checkout`:

- A `CheckoutViewModel` exposing `StateFlow<CheckoutUiState>` (Loading / Content / Empty / Error), driven by a `CartRepository`.
- A `CartRepository` backed by a Room `CartDao` (the source of truth) and an `OrderApi` (place-order).
- A `CheckoutScreen` composable rendering each `UiState`, with tagged, accessible nodes.
- **Small tier:** JUnit 5 + `runTest` + `MainDispatcherExtension` + Turbine + a `FakeCartRepository` testing the ViewModel's state machine; plus a pure unit test on the price/total math.
- **Medium tier:** Robolectric tests on the real `CartDao` against an in-memory Room DB; Compose UI tests on `CheckoutScreen` (JVM via Robolectric); Roborazzi screenshot goldens for Loading, Content, Empty, Error (and dark theme).
- **Large tier:** one `@HiltAndroidTest` Espresso smoke driving add-to-cart → checkout → confirmed, with a fake `OrderApi` swapped in via Hilt.
- A green, layered CI run, and a documented demonstration that each layer breaks independently.

---

## Milestone 1 — The module and its state (≈ 1 h)

Create `:feature-checkout`. Define the UI state as an immutable sealed type (Week 12 shape) and the domain pieces:

```kotlin
data class CartItem(val sku: String, val label: String, val priceCents: Int, val qty: Int)

sealed interface CheckoutUiState {
    data object Loading : CheckoutUiState
    data object Empty : CheckoutUiState
    data class Content(val items: List<CartItem>, val totalCents: Int) : CheckoutUiState
    data class Error(val message: String) : CheckoutUiState
}

interface CartRepository {
    fun observeCart(): kotlinx.coroutines.flow.Flow<List<CartItem>>
    suspend fun setQuantity(sku: String, qty: Int)
    suspend fun placeOrder(): OrderResult
}

sealed interface OrderResult {
    data class Success(val orderId: String) : OrderResult
    data class Failure(val message: String) : OrderResult
}
```

Decisions you must defend in review:

- **Why `Empty` as a distinct state, not `Content(emptyList())`?** Because the screen renders them differently (an empty-cart message vs. a list), and a distinct state makes both the screenshot matrix and the UI test explicit. A senior reviewer wants states the UI actually branches on to be distinct.
- **Why is `CartRepository` an interface?** So the small-tier ViewModel test can use a `FakeCartRepository` (no Room, no network) and the medium-tier DAO test exercises the *real* implementation. The interface is the seam the pyramid is built on.

## Milestone 2 — The ViewModel and the small tier (≈ 2.5 h)

Write the `CheckoutViewModel`: observe the cart, map to `UiState`, expose place-order. Then test it at the small tier.

```kotlin
class CheckoutViewModel(private val repository: CartRepository) : ViewModel() {
    val uiState: StateFlow<CheckoutUiState> = repository.observeCart()
        .map { items ->
            if (items.isEmpty()) CheckoutUiState.Empty
            else CheckoutUiState.Content(items, items.sumOf { it.priceCents * it.qty })
        }
        .catch { emit(CheckoutUiState.Error(it.message ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckoutUiState.Loading)
    // ... placeOrder() launching into viewModelScope, exposing a one-shot event ...
}
```

The small-tier tests (JUnit 5, `test/`):

- **`CheckoutViewModelTest`** — with a `FakeCartRepository`, the `MainDispatcherExtension`, and Turbine: assert `Loading` → `Empty` for an empty cart, `Loading` → `Content` with the right total for a populated cart, and `Error` when the repository's flow throws. Assert *every* emission, control time with `advanceUntilIdle`.
- **`CheckoutMathTest`** — a pure unit test (no coroutines) on the total arithmetic and any discount/tax rule, with a `@ParameterizedTest` over cases.

Acceptance for this milestone: both test classes pass, deterministically, on the JVM in milliseconds. If you change a state-machine rule, *only* `CheckoutViewModelTest` goes red.

## Milestone 3 — The DAO and the Robolectric medium tier (≈ 2 h)

Write the real `CartDao` and the `RoomCartRepository`. Test the DAO with Robolectric against an in-memory database:

```kotlin
@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items ORDER BY label")
    fun observeAll(): Flow<List<CartItemEntity>>

    @Query("SELECT COALESCE(SUM(price_cents * qty), 0) FROM cart_items")
    fun totalCents(): Flow<Int>      // COALESCE so an empty cart is 0, not null

    @Upsert suspend fun upsert(item: CartItemEntity)
    @Query("DELETE FROM cart_items WHERE sku = :sku") suspend fun delete(sku: String)
}
```

The `CartDaoTest` (Robolectric, in-memory Room, `runTest`):

- Insert rows, assert `observeAll().first()` returns them ordered.
- Assert `totalCents()` sums `price * qty` across rows.
- **The empty-cart case:** assert `totalCents()` is `0`, not null — this is the bug the `COALESCE` fixes and the reason this is a *medium* test (real SQL), not a small one. If you drop the `COALESCE`, *only this test* goes red.

## Milestone 4 — The screen and the Compose UI + screenshot tiers (≈ 2.5 h)

Write `CheckoutScreen` rendering each state, with tagged, accessible nodes (test tags on rows, content descriptions on icon buttons). Then test it two ways at the medium tier:

- **`CheckoutScreenTest`** (Compose UI test, JVM via Robolectric): given `Content`, the rows and total render; tapping "+" calls the increment callback; given `Empty`, the empty message shows; given `Error`, the error node shows. Find nodes by tag/description, not brittle text.
- **`CheckoutScreenScreenshotTest`** (Roborazzi, JVM): one golden each for Loading, Content, Empty, Error, and a dark-theme Content. Pin the configuration (`qualifiers = "w400dp-h800dp-mdpi"`). `recordRoborazziDebug` to write, `verifyRoborazziDebug` to check.

If you change a color or padding, *only the screenshot test* goes red. If you break a click handler, *only the UI test* goes red. Two different regressions, two different red tests.

## Milestone 5 — The Espresso end-to-end smoke (≈ 1.5 h)

One `@HiltAndroidTest` test, in `androidTest/` (JUnit 4, runs on a device/emulator), driving the real journey:

```kotlin
@HiltAndroidTest
class CheckoutSmokeTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Before fun inject() = hiltRule.inject()

    @Test
    fun `add to cart, checkout, see confirmation`() {
        composeRule.onNodeWithText("Add Flat White").performClick()
        composeRule.onNodeWithContentDescription("Open cart").performClick()
        composeRule.onNodeWithText("Place order").performClick()
        composeRule.onNodeWithText("Order confirmed").assertExists()
    }
}
```

Swap a **fake `OrderApi`** into the real Hilt graph with `@TestInstallIn` (replacing the production network module) and a `HiltTestRunner`, so the smoke is deterministic — it drives the *real* ViewModel, navigation, and DAO, but against an in-memory API. This is the *one* large test: it proves the wiring, not the logic the base already owns.

## Milestone 6 — Prove the layers break independently (≈ 1 h)

This is the milestone that earns the week's promise. Demonstrate, with four deliberate one-line breakages (then revert each), that each layer fails in isolation:

1. **Break the total math** in the ViewModel's `map` → only `CheckoutViewModelTest` goes red.
2. **Drop the `COALESCE`** from the DAO query → only `CartDaoTest` (empty-cart case) goes red.
3. **Change the error banner's color** → only `CheckoutScreenScreenshotTest` goes red.
4. **Break the increment callback wiring** → only `CheckoutScreenTest` goes red.

Record each in your README: the breakage, which test(s) went red, which stayed green. That table *is* the proof your suite is layered correctly.

---

## Acceptance criteria

- [ ] `:feature-checkout` builds with the small + medium tiers running on the JVM (`testDebugUnitTest`) and the Espresso smoke on a device (`connectedDebugAndroidTest`).
- [ ] **Small:** `CheckoutViewModelTest` (Turbine + fake + `MainDispatcherExtension`) asserts every emission for Empty/Content/Error; `CheckoutMathTest` covers the arithmetic. Deterministic.
- [ ] **Medium:** `CartDaoTest` (Robolectric, in-memory Room) covers the `SUM`/`COALESCE` including empty-cart; `CheckoutScreenTest` (Compose UI on JVM) covers render + interaction.
- [ ] **Medium:** `CheckoutScreenScreenshotTest` (Roborazzi) has one golden each for Loading, Content, Empty, Error, and dark Content, configuration-pinned and committed.
- [ ] **Large:** one `@HiltAndroidTest` Espresso smoke with a fake `OrderApi` swapped via Hilt; it passes and proves the wiring.
- [ ] **The independence table** (Milestone 6) shows each of four breakages turning exactly one layer's test(s) red.
- [ ] The whole JVM suite is deterministic: no `Thread.sleep`, no real clock, no real network, fresh fakes per test. Test doubles live in `testFixtures`/`androidTest`, never in `main`.
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **`:core:testing` module.** Extract the `MainDispatcherExtension`, the fakes, and the sample `TestData` into a shared test-only module other features depend on (the Now-In-Android pattern). Reuse, not duplication.
- **A11y assertions.** Add Compose UI test assertions on the semantics: every icon button has a content description, the total has a meaningful one. Accessible == testable.
- **Idling for async.** If your place-order has a visible spinner, drive the Espresso smoke through it with an `IdlingResource` instead of any sleep — or make the fake complete synchronously and explain which you chose and why.
- **Screenshot CI gate.** Wire `verifyRoborazziDebug` into the module's `check` task so a golden diff fails the build (preview of Week 21's CI). A free visual-regression gate.

## What this milestone earns you

You can now take a real feature and test it at every layer of the pyramid — logic at the base, framework and UI in the middle, wiring at the tip — deterministically, mostly on the JVM, and *prove* the suite is layered so a single red test names a single broken layer. That is the literal "skill earned" line for the week: picking the right test type the first time, writing deterministic Flow tests, and catching visual regressions before users do. Week 18 swaps assertions for measurements — macrobenchmark, Baseline Profiles, R8 — but the discipline is the same one you just installed: control the inputs, measure precisely, and trust the number only when it's reproducible.
