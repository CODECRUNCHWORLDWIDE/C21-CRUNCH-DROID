# Exercise 1 — Pick the right test

**Goal.** Before you write any test code this week, build the instinct that picks the *right tier* the first time. Given eight concrete behaviors from a real Android app, you decide for each: which pyramid tier (small / medium / large), which tool, and *why* — the cost/confidence trade — plus whether there's something here you should *not* test at all. This is lecture 1's pyramid and "what not to test" sections, applied.

**Estimated time.** 40 minutes.

**Prerequisites.** Lectures 1 and 2 read. No tooling — this is a paper (or markdown) exercise. Write your answers in `notes/test-tier-decisions.md` in your Week 17 repo.

---

## The setup

You've joined a team shipping the checkout flow you'll build in this week's mini-project. The architecture is the one you know: a `CheckoutViewModel` exposing `StateFlow<CheckoutUiState>`, a `CartRepository` backed by a Room `CartDao` and an `OrderApi` (Retrofit), a `CheckoutScreen` composable, and Hilt wiring it all together. Eight behaviors need test coverage. For each, decide the tier, the tool(s), and the justification.

## The eight behaviors

For each behavior below, write down: **(a)** the tier (small/medium/large), **(b)** the tool(s), **(c)** one sentence on *why this tier and not a cheaper or more expensive one*.

1. **`PriceCalculator.total()` applies a 20% tax to a subtotal and rejects negative input.** Pure Kotlin, no Android, no coroutines.

2. **`CheckoutViewModel` emits `Loading` then `Content` when the cart loads successfully, and `Loading` then `Error` when the repository fails.** A `StateFlow<UiState>` driven by a suspend repository call.

3. **`CartDao.totalCents()` returns `SUM(price * qty)` correctly across multiple rows, including the empty-cart case (should be 0, not null).** Real SQL in a `@Query`.

4. **The "Place order" button is disabled when the cart is empty and enabled when it has items.** A composable that renders a `Button(enabled = ...)`.

5. **The error banner on `CheckoutScreen` renders with the right color, icon, and padding in both light and dark theme.** Pure visual correctness.

6. **Adding an item to the cart, opening checkout, and tapping "Place order" results in a visible "Order confirmed" screen — through the real Hilt graph and navigation.** The full wiring.

7. **`CartRepository` maps an `OrderApi` 500 response into `CheckoutUiState.Error` and a 200 into `Content`.** A repository translating network results, with a mocked/faked `OrderApi`.

8. **`Modifier.shimmerPlaceholder()` (a custom modifier) animates a gradient sweep at 60fps without recomposing.** A Compose performance property.

## The "what not to test" question

Two of the eight have a trap: a piece that would be wasteful or wrong to test, or a behavior better proven a different way than your first instinct. Identify at least one and explain. (Hint: re-read lecture 1 §2, and think hard about behavior 8 — is "doesn't recompose" an *assertion* you write, or a thing you measure with a different tool from a different week?)

## What a strong answer looks like

A weak answer says "behavior 3: medium, Robolectric." A strong answer says:

> **Behavior 3 (DAO SUM):** Medium tier, Robolectric + an in-memory Room database + `runTest`. Not small, because the behavior under test *is* the SQL — a unit test with a fake DAO would test my fake's arithmetic, not Room's `SUM`, missing exactly the null-vs-zero bug (`SUM` over zero rows returns `NULL` in SQLite unless you `COALESCE`). Not large, because no device quirk is involved; running it on an emulator would be ~40× slower for zero added fidelity. The empty-cart case is the whole reason this can't be a unit test.

That's the level: tier, tool, and a *reason that names the specific bug the cheaper tier would miss or the expensive tier wouldn't add coverage for.*

---

## Acceptance criteria

- [ ] `notes/test-tier-decisions.md` has all eight behaviors with tier, tool(s), and a one-sentence justification each.
- [ ] At least three justifications name a *specific* failure the wrong tier would miss or a specific cost it would add (not just "it's faster").
- [ ] You answered the "what not to test" question for at least one behavior, with reasoning.
- [ ] Behavior 8 is correctly identified as a *performance/recomposition* property measured with the Layout Inspector / Compiler report (Week 7) and macrobenchmark (Week 18), **not** an assertion in a JUnit test.

## What you just proved

You proved you can place a behavior on the pyramid *before* writing it — the senior instinct that keeps a suite fast and honest. The most common junior mistake isn't writing bad tests; it's writing the *right test at the wrong tier*: a logic check done through Espresso (slow, flaky) or a SQL check done with a fake (proves nothing). You also drew the line at the framework's edge: you don't test that Room persists or that Compose doesn't recompose by *asserting* it — you test your SQL and you *measure* recomposition. Tier selection is the whole game, and you just played all eight rounds.

---

## Reference answer sketch (read only after you've written yours)

- **1 — Small.** JUnit 5, pure JVM. Branches + the negative-input guard = a unit test's home turf. A `@ParameterizedTest` over tax cases.
- **2 — Small.** JUnit 5 + `runTest` + `MainDispatcherExtension` + Turbine + a fake repository. The state machine is logic; the virtual clock and fake make it deterministic and millisecond-fast.
- **3 — Medium.** Robolectric + in-memory Room + `runTest`. The behavior *is* the SQL; the empty-cart `SUM`-is-`NULL` bug only shows against real SQLite.
- **4 — Medium.** Compose UI test (`createComposeRule`, runnable on JVM via Robolectric). `assertIsEnabled`/`assertIsNotEnabled` given empty vs. populated state.
- **5 — Medium.** Roborazzi screenshot, one golden per theme. Visual correctness no assertion captures; *don't* assert color hex in a UI test — screenshot it.
- **6 — Large.** Espresso + `@HiltAndroidTest` with a fake `OrderApi`. The one wiring smoke; proves the graph + navigation connect.
- **7 — Small.** JUnit 5 + `runTest`, with a *fake* `OrderApi` (preferred) or a MockK mock. Repository mapping is logic; isolate the API.
- **8 — Not a JUnit test.** A *performance* property: measure with the Layout Inspector recomposition counts (Week 7) and macrobenchmark (Week 18). Asserting "didn't recompose" in a unit test is the trap — that's measured, not asserted.

## Hints (read only if stuck > 10 min)

- **Stuck between small and medium?** Ask: does the behavior under test *need* a real Android API (SQLite, `Context`, a composition)? If yes → medium. If it's pure Kotlin logic → small. The framework boundary is the line.
- **Stuck between medium and large?** Ask: is there a *real device quirk* involved (hardware, true multi-process, real GPU), or is it just "the framework on the JVM is enough"? Only genuine device-dependence justifies the large tier.
- **Tempted to write more than one Espresso test?** Re-read lecture 2 §4. The base proves the logic; the tip proves the wiring *once*.
