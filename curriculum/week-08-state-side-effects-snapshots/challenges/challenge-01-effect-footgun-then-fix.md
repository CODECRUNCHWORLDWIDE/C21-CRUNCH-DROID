# Challenge 1 — Plant the effect footguns, then fix them (with logcat)

**Time.** 60–120 minutes.
**Deliverable.** A short report (`EFFECTS.md`) with three before/after logcat excerpts (one per footgun) and the refactored code, committed to your Week 08 repo.

## The premise

Every Android engineer has, at least once, shipped a screen that fired a network request on every recomposition, or a `LaunchedEffect` that kept using a stale value because the key was wrong, or a sensor listener that was never unregistered and leaked. These bugs are invisible in a five-second demo and expensive in production — wasted requests, wrong data, growing memory. The skill this challenge builds is not "know the footguns exist" — it's **plant them, watch the lifecycle misbehave in logcat, diagnose each by its timing, and fix it with the correct API and key.** A fix you can't show in a log is a guess.

You will build a "live order detail" screen the wrong way — three effect bugs at once — observe each, then fix each and prove it with logcat.

## What to build

A screen that loads an order by id, subscribes to a live price feed, and auto-refreshes. Each piece logs when its effect runs, so the bugs are visible.

### Step 1 — Plant footgun A: work in the composable body

```kotlin
@Composable
fun OrderScreenBad(orderId: String, repo: OrderRepository) {
    var order by remember { mutableStateOf<Order?>(null) }

    // FOOTGUN A: a bare suspend launch in the body. Fires on EVERY recomposition.
    // (Pretend this is a callback-style fetch; the point is it's in the body.)
    repo.fetchOrderBlocking(orderId) { loaded ->
        Log.d("OrderScreen", "fetchOrder fired for $orderId")   // watch this spam logcat
        order = loaded
    }

    Text(order?.summary ?: "Loading…")
}
```

Run it, cause a few recompositions (type in a nearby field, toggle something). **Watch logcat: `fetchOrder fired` prints over and over** — once per recomposition. That's the request storm. Copy the logcat.

### Step 2 — Plant footgun B: the wrong key (stale capture)

```kotlin
@Composable
fun OrderTotalBad(orderId: String, repo: OrderRepository) {
    var total by remember { mutableStateOf(0.0) }

    // FOOTGUN B: keyed on Unit, but the body reads orderId. When orderId changes,
    // the effect does NOT restart, so `total` stays the FIRST order's total forever.
    LaunchedEffect(Unit) {
        Log.d("OrderScreen", "computing total for $orderId")    // prints only ONCE, with the first id
        total = repo.computeTotal(orderId)
    }

    Text("Total: $total")
}
```

Drive it with two different `orderId`s (a button that switches the id). **Watch logcat: `computing total` prints only once, with the first id**, and the total never updates when you switch orders. That's the stale capture. Copy the logcat.

### Step 3 — Plant footgun C: the leaked listener

```kotlin
@Composable
fun PriceTickerBad(feed: PriceFeed) {
    var price by remember { mutableStateOf(0.0) }

    // FOOTGUN C: register a listener in LaunchedEffect with no teardown. When the
    // composable leaves, the listener is still registered -> leak. Navigate away
    // and back a few times; listeners accumulate.
    LaunchedEffect(Unit) {
        val listener = PriceListener { p ->
            Log.d("OrderScreen", "price tick $p (one log line PER registered listener)")
            price = p
        }
        feed.addListener(listener)
        // no removeListener -> leaked
    }

    Text("Price: $price")
}
```

Navigate to this screen and back several times (or toggle it in/out of the composition). **Watch logcat: each price tick now prints multiple times** — one line per leaked listener still registered — proving they accumulated. Copy the logcat.

### Step 4 — Fix each footgun

```kotlin
// FIX A: tie the load to LaunchedEffect(orderId) — once per orderId, cancels on leave.
@Composable
fun OrderScreen(orderId: String, repo: OrderRepository) {
    var order by remember { mutableStateOf<Order?>(null) }
    LaunchedEffect(orderId) {
        Log.d("OrderScreen", "fetchOrder fired for $orderId")   // now: once per orderId
        order = repo.fetchOrder(orderId)                        // suspend version
    }
    Text(order?.summary ?: "Loading…")
}

// FIX B: key on orderId so the effect restarts when it changes.
@Composable
fun OrderTotal(orderId: String, repo: OrderRepository) {
    var total by remember { mutableStateOf(0.0) }
    LaunchedEffect(orderId) {                                    // orderId is the dependency
        Log.d("OrderScreen", "computing total for $orderId")    // now: once per orderId
        total = repo.computeTotal(orderId)
    }
    Text("Total: $total")
}

// FIX C: DisposableEffect with onDispose removes the listener on leave/key change.
@Composable
fun PriceTicker(feed: PriceFeed) {
    var price by remember { mutableStateOf(0.0) }
    DisposableEffect(feed) {
        val listener = PriceListener { p ->
            Log.d("OrderScreen", "price tick $p")
            price = p
        }
        feed.addListener(listener)
        onDispose { feed.removeListener(listener) }             // teardown -> no leak
    }
    Text("Price: $price")
}
```

### Step 5 — Re-run and prove each fix

- **Fix A**: cause recompositions; `fetchOrder fired` now prints only when `orderId` changes, not per recomposition.
- **Fix B**: switch orders; `computing total` now prints for *each* order, and the total updates.
- **Fix C**: navigate away/back several times; each price tick now prints **once**, proving listeners are removed on leave.

Copy each "after" logcat.

### Step 6 (optional, stretch) — `rememberUpdatedState`

Add a 10-second auto-refresh that should run *once* but call the *latest* `onRefresh` lambda. Implement it naively (key on `onRefresh`) and watch the timer restart on every recomposition in logcat; then fix it with `rememberUpdatedState(onRefresh)` + `LaunchedEffect(Unit)` and confirm the timer runs once while still calling the fresh lambda. This is footgun 3 from lecture 2, §10.

## Acceptance criteria

- [ ] All three bad versions reproduce their bug, visible in logcat (request storm, stale capture, leaked listener).
- [ ] All three fixes use the correct API and key (`LaunchedEffect(orderId)`, `LaunchedEffect(orderId)`, `DisposableEffect(feed)` + `onDispose`).
- [ ] Behavior parity: the fixed screen does everything the bad one was supposed to, correctly.
- [ ] `EFFECTS.md` records: before/after logcat for each footgun, and a one-sentence diagnosis of each (which lifecycle hook was wrong).
- [ ] A 3–5 sentence explanation, in your own words, of why each fix works (which hook the right API keys to).
- [ ] Build with **0 warnings**.

## What "great" looks like

A weak submission says "I used `LaunchedEffect` and `DisposableEffect`." A great submission says:

> The original `OrderScreenBad` fired `fetchOrder` on every recomposition because the call lived in the composable body; logcat showed 14 fetches for a single screen view. Moving it into `LaunchedEffect(orderId)` keyed on `orderId` reduced that to exactly one fetch per distinct order, because `LaunchedEffect` runs on enter and only restarts when its key changes. `OrderTotalBad` keyed on `Unit` captured the first `orderId` and never updated — logcat showed `computing total for order-1` once even after switching to `order-2`; keying on `orderId` made it restart and recompute. `PriceTickerBad` leaked a listener per visit because nothing unregistered it — after four navigations, each price tick logged four times; `DisposableEffect(feed)` with `onDispose { feed.removeListener(...) }` brought it back to exactly one, because `onDispose` runs on leave. The stretch auto-refresh restarted every recomposition when keyed on the lambda; `rememberUpdatedState` plus `LaunchedEffect(Unit)` gave one timer that still calls the latest callback.

Quantified, explained, and tied to the lifecycle hook each bug violated. That's the senior-engineer answer.

## Where this reappears

The "tie work to the lifecycle, get the key right, always pair register with unregister" discipline is exactly what Phase III's testing week (where you assert effects fire the right number of times) and performance week (where a leaked listener shows up as memory growth in a macrobenchmark) build on. The cancellation-on-key-change you relied on here is structured concurrency (Week 4) applied to the composition lifecycle — the same instinct, a different scope.
