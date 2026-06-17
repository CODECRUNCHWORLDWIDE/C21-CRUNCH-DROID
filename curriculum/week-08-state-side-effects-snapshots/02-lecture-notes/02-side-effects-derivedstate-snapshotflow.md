# Lecture 2 — Side effects, `derivedStateOf`, and bridging to Flow with `snapshotFlow`

Lecture 1 gave you the state model: snapshot-backed cells, three retention boundaries, hoisting. This lecture is about the other half of a real screen — the **side effects**, the controlled escape hatches for doing imperative work (start a network call, register a listener, log to analytics, run an animation) from inside a declarative UI that re-runs your code constantly. The central problem is one sentence: **your composable body runs again on every recomposition, so you cannot just start a coroutine in it — it would start again, and again, and again.** The side-effect APIs exist to tie imperative work to precise points in the composition lifecycle so it runs the right number of times. The skill this week earns is naming the lifecycle hook each API is keyed to, so you reach for the right one the first time.

We take them in the order you reach for them, then `derivedStateOf` (the "computed state that notifies on result change"), then `snapshotFlow` (the bridge to Week 5's Flow operators), then the footguns.

---

## 1. The problem: a composable body is not a place to start work

This looks reasonable and is a serious bug:

```kotlin
@Composable
fun UserProfile(userId: String, repo: UserRepository) {
    var user by remember { mutableStateOf<User?>(null) }
    // BUG: this runs on EVERY recomposition, firing a new request each time.
    repo.fetchUser(userId) { user = it }     // <- fires repeatedly
    user?.let { ProfileCard(it) }
}
```

`fetchUser` runs every time `UserProfile` recomposes — which could be many times a second. You get a storm of requests, races, and wasted work. The composable body is for *describing UI*, not for *launching work*. To launch work, you need an API that says "run this once when I enter, and again only when this key changes." That's `LaunchedEffect`.

The whole side-effect family answers variations of "*when* should this imperative thing run, relative to the composition lifecycle?" Memorize the lifecycle hooks from lecture 1 — **enter**, **recompose**, **leave** — because each API keys to a combination of them.

---

## 2. `LaunchedEffect` — a coroutine keyed to enter + key change

`LaunchedEffect(key)` launches a coroutine when the composable **enters** the composition. The coroutine is **cancelled and relaunched** whenever `key` changes, and **cancelled** when the composable **leaves**. That cancellation-on-key-change *is* structured concurrency (Week 4) applied to the composition lifecycle.

```kotlin
@Composable
fun UserProfile(userId: String, repo: UserRepository) {
    var user by remember { mutableStateOf<User?>(null) }
    // Runs once on enter; cancels & relaunches if userId changes; cancels on leave.
    LaunchedEffect(userId) {
        user = repo.fetchUser(userId)        // a suspend call, run exactly once per userId
    }
    user?.let { ProfileCard(it) }
}
```

The keying rules, which are the whole API:

- **`LaunchedEffect(Unit)` or `LaunchedEffect(true)`** — run once on enter, never restart (until leave/re-enter). Use for a one-shot that should fire exactly once: a "screen viewed" analytics event, a one-time initial load. The footgun is using `Unit` when you *meant* to re-run on a parameter change.
- **`LaunchedEffect(userId)`** — restart whenever `userId` changes. This is the common case: the effect *depends on* `userId`, so when it changes you want to cancel the old work and start fresh. The cancellation is the feature: a stale in-flight fetch for the old `userId` is cancelled before the new one starts.
- **`LaunchedEffect(key1, key2)`** — restart if *either* changes.

The mental rule: **the keys are the effect's dependencies.** If the effect's body reads a value that, when it changes, should restart the effect, that value is a key. Forgetting a key means the effect uses a stale captured value; adding a key that changes too often means the effect thrashes (cancel/restart storm). Getting the keys right is getting the dependency list right — exactly like a dependency array, but enforced by cancellation.

---

## 3. `rememberCoroutineScope` — launching from *events*, not composition

`LaunchedEffect` launches from *composition* — it runs as part of composing. But a button's `onClick` is an *event*, fired outside composition, and you cannot call `LaunchedEffect` from a lambda (it's a composable). For event-driven coroutines you use `rememberCoroutineScope`:

```kotlin
@Composable
fun ShareButton(content: String, sharer: Sharer) {
    val scope = rememberCoroutineScope()       // a scope tied to this composable's lifetime
    Button(onClick = {
        scope.launch {                          // launch from the EVENT handler
            sharer.share(content)
        }
    }) {
        Text("Share")
    }
}
```

The scope is tied to the composable's place in the composition: when the composable leaves, the scope is cancelled, so in-flight work stops. The decision rule is crisp: **does the work start because the composable appeared (or a key changed)? → `LaunchedEffect`. Does it start because the user did something (a tap)? → `rememberCoroutineScope().launch`.** Using `LaunchedEffect` for a click handler is impossible (wrong call site); using `rememberCoroutineScope` for an on-appear load is a smell (you'd have to gate it with a flag, which is what `LaunchedEffect` does for free).

---

## 4. `DisposableEffect` — setup paired with teardown

Some side effects acquire a resource that must be *released*: a `BroadcastReceiver`, a sensor listener, a `LifecycleObserver`, a third-party SDK callback. `DisposableEffect(key)` runs setup on enter (and on key change), and its mandatory `onDispose { }` runs the teardown on leave (and before re-running on key change).

```kotlin
@Composable
fun rememberLifecycleEvent(lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current): Lifecycle.Event {
    var event by remember { mutableStateOf(Lifecycle.Event.ON_ANY) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> event = e }
        lifecycleOwner.lifecycle.addObserver(observer)          // setup on enter
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)   // teardown on leave / key change
        }
    }
    return event
}
```

The discipline: **anything you register, you must unregister, and `DisposableEffect`'s `onDispose` is where.** A `LaunchedEffect` that adds a listener and never removes it is a leak; the moment your cleanup is "remove the thing I added," you want `DisposableEffect`, because its symmetry is enforced — the compiler requires the `onDispose` block. The key follows the same dependency rule as `LaunchedEffect`: when the key changes, `onDispose` runs for the old, then setup runs for the new.

---

## 5. `produceState` — an async source into `State`

`produceState` is sugar that combines `remember { mutableStateOf(initial) }` with a `LaunchedEffect` that feeds it. Use it to turn a suspend function, a callback API, or a `Flow` into a `State<T>` with a clean loading/loaded shape:

```kotlin
@Composable
fun userState(userId: String, repo: UserRepository): State<UiResult<User>> =
    produceState<UiResult<User>>(initialValue = UiResult.Loading, userId) {
        // `value` is the MutableState; this block is a LaunchedEffect keyed on userId.
        value = try {
            UiResult.Success(repo.fetchUser(userId))
        } catch (e: IOException) {
            UiResult.Error(e)
        }
        // For a callback API you'd register here and awaitDispose { } to clean up.
    }
```

It cancels and restarts on key change just like `LaunchedEffect`, and for callback sources it offers `awaitDispose { }` for cleanup. Reach for `produceState` when the *output* is a single piece of observable state derived from an async source; reach for raw `LaunchedEffect` when you're driving several pieces of state or doing fire-and-forget work.

---

## 6. `derivedStateOf` — computed state that notifies on *result* change

This is the most misunderstood API of the week, so the rule first, then the why. `derivedStateOf { ... }` creates a `State` whose value is computed from other state reads, and which **only notifies its readers when the computed *result* changes** — even if the inputs it reads change far more often.

The canonical example: a "scroll to top" button that should appear only after the user scrolls past the first item.

```kotlin
@Composable
fun MessageList(messages: List<Message>) {
    val listState = rememberLazyListState()
    // listState.firstVisibleItemIndex changes on EVERY scroll pixel-ish.
    // But "should I show the button" only flips when it crosses 0.
    val showButton by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    Box {
        LazyColumn(state = listState) { items(messages) { MessageRow(it) } }
        if (showButton) {
            ScrollToTopButton()
        }
    }
}
```

Without `derivedStateOf`, reading `listState.firstVisibleItemIndex > 0` directly in the composable subscribes it to `firstVisibleItemIndex`, which changes constantly during a scroll — so the composable recomposes on every scroll frame, just to re-evaluate a boolean that mostly stays the same. With `derivedStateOf`, the composable subscribes to the *boolean result*, which only changes when the index crosses 0 — so it recomposes **twice** per scroll session (when the button appears, when it disappears), not hundreds of times.

The rule for when `derivedStateOf` earns its keep:

> Use `derivedStateOf` when a calculation reads **frequently-changing** state but produces a result that changes **rarely**, and that calculation drives recomposition or an expensive operation.

And when *not* to: if the result changes about as often as the inputs (e.g. `fullName = "$first $last"` where both change together), `derivedStateOf` adds overhead for no benefit — just compute it inline. The wrong instinct is "wrap every computed value in `derivedStateOf`"; the right instinct is "wrap a value whose inputs churn but whose result is stable." It's a filter on notification frequency, not a general memoizer.

---

## 7. `SideEffect` — publishing to non-Compose code

`SideEffect { }` runs after **every successful recomposition**. Use it to push current Compose state out to an object that isn't Compose-aware — an analytics SDK, a third-party controller that needs the latest value:

```kotlin
@Composable
fun rememberAnalytics(user: User, analytics: Analytics) {
    // Update the analytics user properties on every successful recomposition.
    SideEffect {
        analytics.setUserProperty("plan", user.plan)
    }
}
```

It's the narrowest tool: no coroutine, no cleanup, just "after this composition committed, run this." If you need a coroutine, use `LaunchedEffect`; if you need cleanup, use `DisposableEffect`; `SideEffect` is for the fire-after-commit publish-to-the-outside-world case.

---

## 8. `snapshotFlow` — bridging snapshot state to Kotlin `Flow`

`snapshotFlow { }` converts reads of snapshot state into a cold `Flow` that emits when the read values change. This is the bridge between this week's UI state and Week 5's entire Flow operator toolbox. It's what lets you `debounce` a text field, `distinctUntilChanged` a selection, and `flatMapLatest` a query into a cancellable search.

```kotlin
@Composable
fun SearchScreen(repo: SearchRepository) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Hit>>(emptyList()) }

    LaunchedEffect(Unit) {
        snapshotFlow { query }                  // a Flow<String> of query values
            .debounce(300)                      // wait 300ms after the last keystroke (Week 5)
            .distinctUntilChanged()             // ignore re-emits of the same query
            .filter { it.length >= 2 }          // don't search 1-char queries
            .flatMapLatest { q ->               // CANCEL the prior search when a new query arrives
                flow { emit(repo.search(q)) }
            }
            .collect { results = it }           // push results back into snapshot state
    }

    SearchUi(query = query, onQueryChange = { query = it }, results = results)
}
```

Read the pipeline as a sentence: *take the stream of query values, wait for the user to pause typing, drop duplicate queries, ignore too-short ones, run the search — cancelling any prior in-flight search when a new query arrives — and write each result set back into state.* Every operator is one you learned in Week 5; `snapshotFlow` is the only new piece, and all it does is turn `query` (snapshot state) into a `Flow<String>`.

Two things to know:

- **`snapshotFlow` is cold and runs in a coroutine** — you collect it inside a `LaunchedEffect` (so it's tied to the composition lifecycle and cancelled on leave). It conflates: if the value changes faster than the collector consumes, you get the latest, not every intermediate.
- **It emits only on *change*** (using `equals`), like `distinctUntilChanged` for free on the read values. You still often add explicit `distinctUntilChanged` after operators that could re-introduce duplicates.

`snapshotFlow` is why the mini-project can build a production-grade debounced search with no `ViewModel` and no `LiveData` — just snapshot state, the bridge, and Week 5's operators. It's the single most powerful line in this week.

---

## 8b. When effects run, and on which dispatcher

Two practical facts that save you from subtle bugs.

**Effects run after composition commits, not during it.** When a composable composes, Compose records the effects you declared (`LaunchedEffect`, `DisposableEffect`, etc.) but does *not* run them mid-composition. It runs them *after* the composition successfully commits to the tree. This is why you can't observe an effect's result during the same composition that declared it, and why effects are safe places to do work that shouldn't happen if composition is abandoned (Compose can compose speculatively and throw the result away — effects only fire for committed compositions). The order among sibling effects follows declaration order, but don't build logic that depends on fine-grained ordering between unrelated effects; if two effects must coordinate, that coupling is a smell.

**`LaunchedEffect` runs on the composition's dispatcher — usually the main thread.** The coroutine a `LaunchedEffect` launches starts on `AndroidUiDispatcher.Main` by default, which means its body runs on the main thread until it suspends or you switch context. That's correct for touching Compose state (state writes should happen where the composition lives), but it means CPU-heavy or blocking work inside a `LaunchedEffect` will jank the UI unless you move it off-main:

```kotlin
LaunchedEffect(query) {
    // runs on Main: fine for the orchestration and the state write
    val result = withContext(Dispatchers.IO) {   // move blocking/heavy work off-main (Week 4)
        repo.search(query)                        // network / disk / CPU work here
    }
    results = result                              // back on Main: safe state write
}
```

This is Week 4's dispatcher discipline applied to effects: orchestrate on Main, push the heavy lifting to `IO`/`Default` with `withContext`, and come back to Main to write state. The cancellation story is unchanged — when the key changes or the composable leaves, the whole coroutine (including the `withContext` block) is cancelled, because structured concurrency propagates cancellation through `withContext`.

## 8c. `rememberSaveable` and effects — the restart subtlety

One interaction worth flagging because it bites people: after a configuration change, `rememberSaveable` restores your state, but `LaunchedEffect` keyed on that restored value will *re-run* on the recreated composition. Usually that's fine and even desirable (re-derive UI state from the restored query, as the mini-project does). But if the effect has a *user-visible side effect* — showing a snackbar, navigating, playing a sound — re-running it on every rotation is a bug: the snackbar pops again, you navigate again. For those one-shot, user-visible effects, gate them so they fire once regardless of restoration:

```kotlin
@Composable
fun ResultScreen(showSuccess: Boolean) {
    // survive rotation, but only show the snackbar ONCE, not again on every rotation
    var alreadyShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showSuccess) {
        if (showSuccess && !alreadyShown) {
            snackbar.show("Saved!")
            alreadyShown = true        // saved across rotation -> won't re-show
        }
    }
}
```

The pattern — a `rememberSaveable` "already handled" flag — is how you make a one-shot user-visible effect idempotent across configuration changes. Re-deriving data (re-running a search) is fine to repeat; re-firing a snackbar or a navigation is not. Know which kind of effect you have.

## 9. The decision table — stop the blur

Six APIs is enough to blur together. Here is the map from "I need to…" to the right tool:

| I need to… | Use | Keyed to |
|------------|-----|----------|
| Run a coroutine when the screen appears / a key changes | `LaunchedEffect(key)` | enter + key change; cancel on leave |
| Launch a coroutine from a click / event handler | `rememberCoroutineScope().launch` | the event; scope cancelled on leave |
| Register a listener/observer that must be removed | `DisposableEffect(key)` + `onDispose` | enter/leave (+ key change), with teardown |
| Turn a suspend/callback/Flow source into one `State<T>` | `produceState(initial, key)` | enter + key change |
| Compute a value that reads churny state but rarely changes | `derivedStateOf { }` | recomputes on input change, notifies on result change |
| Publish current state to non-Compose code after commit | `SideEffect { }` | every successful recomposition |
| Treat snapshot state as a `Flow` (debounce, flatMapLatest…) | `snapshotFlow { }` (collected in a `LaunchedEffect`) | the snapshot reads inside it |

If you can fill this table from memory, you've got the week. The exercises drill exactly these choices.

---

## 10. The footguns — measured against the lifecycle

### Footgun 1 — work in the composable body

Covered in §1: starting a coroutine or a request directly in the body fires it every recomposition. Always wrap launch-work in `LaunchedEffect` (on-appear) or `rememberCoroutineScope` (event). If you ever write `repo.fetch(...)` as a bare statement in a composable, stop.

### Footgun 2 — the wrong key (stale capture, or thrash)

```kotlin
// STALE: keyed on Unit, but the body reads userId. When userId changes, the effect
// does NOT restart, so it keeps fetching the OLD user forever.
LaunchedEffect(Unit) { user = repo.fetchUser(userId) }   // wrong: userId should be a key

// THRASH: keyed on something that changes every recomposition (a new lambda/object),
// so the effect cancels and restarts constantly and never completes.
LaunchedEffect(onResult) { /* ... */ }                   // wrong if onResult is a fresh lambda each time
```

Fix the first by keying on `userId`. Fix the second by keying on stable values (or `rememberUpdatedState` — see below). The key list is the dependency list; get it exactly right.

### Footgun 3 — capturing a value that should stay fresh without restarting

Sometimes an effect should run once (key on `Unit`) but use the *latest* value of something that changes, without restarting. Keying on that value would restart it; not keying captures the stale first value. `rememberUpdatedState` is the escape hatch:

```kotlin
@Composable
fun AutoDismiss(onTimeout: () -> Unit) {
    // We want ONE 5s timer, but to call the LATEST onTimeout when it fires.
    val currentOnTimeout by rememberUpdatedState(onTimeout)
    LaunchedEffect(Unit) {              // keyed on Unit -> the timer is NOT restarted on recomposition
        delay(5000)
        currentOnTimeout()             // calls the latest lambda, not the one captured at launch
    }
}
```

`rememberUpdatedState` keeps a `State` pointing at the latest value, so a long-lived effect reads the current one without using it as a restart key. The "I want a single effect that uses fresh values" case.

### Footgun 4 — a leaked listener (no `onDispose`)

Registering a listener in `LaunchedEffect` without removing it leaks it past the composable's life. Use `DisposableEffect` so the `onDispose` teardown is mandatory and symmetric. (§4.)

### Footgun 5 — `derivedStateOf` where it doesn't belong

Wrapping a value whose result changes as often as its inputs adds overhead and obscures intent. `derivedStateOf` is for churny-input / stable-output only. (§6.)

---

## 10b. A second look at `snapshotFlow` — what it actually subscribes to

`snapshotFlow { ... }` deserves a closer look because it's the week's most powerful and most subtly-behaved API. The block you pass is evaluated, its snapshot-state reads are *tracked* (the same read-tracking from lecture 1), and the resulting value is emitted. Whenever any tracked state changes, the block re-runs and — if the new value differs from the last (`equals`) — emits again. So:

```kotlin
snapshotFlow { listState.firstVisibleItemIndex }   // emits the index, on each change
snapshotFlow { query to onlyUnread }               // emits a Pair; re-emits when EITHER changes
snapshotFlow { derived.value }                      // you can read derivedStateOf inside it
```

Three properties to internalize:

- **It reads inside a snapshot observer**, so only *snapshot state* reads are tracked. Reading a plain `var` inside the block does nothing reactive — the flow won't re-emit when that `var` changes, because there's no tracked read. If your `snapshotFlow` "isn't emitting," check that what you read is actually `MutableState`, not a plain field.
- **It conflates and dedupes.** Like `distinctUntilChanged` on the produced value, it skips emissions where the value didn't change, and if the producer outruns the collector, the collector sees the latest value, not every intermediate. For a fast-changing source feeding a slow consumer (the search case), that's exactly what you want.
- **It's cold and lifecycle-bound when collected in a `LaunchedEffect`.** The collection starts when the effect enters and is cancelled when it leaves or its key changes. So the flow's lifetime is the composable's lifetime — no manual teardown.

The reason `snapshotFlow` is so useful is that it lets you apply *time-based* and *combinatorial* operators to UI state, which the snapshot system alone can't express. The snapshot system says "this changed, recompose"; it has no notion of "wait 300ms," "only the latest of overlapping operations," or "combine these two streams." `snapshotFlow` lifts UI state into Flow, where Week 5's entire operator vocabulary — `debounce`, `sample`, `flatMapLatest`, `combine`, `buffer` — becomes available. It is the seam between the declarative-state world and the reactive-stream world, and most non-trivial input handling lives at that seam.

## 11. Recap

Lecture 1 gave you the state model; this lecture gave you the controlled ways to *do imperative work* from a declarative UI. Three habits carry it:

1. **Never start work in the composable body.** It runs every recomposition. Tie work to the lifecycle: `LaunchedEffect` for on-appear/key-change, `rememberCoroutineScope` for events, `DisposableEffect` for register/unregister, `produceState` for async-to-state.
2. **The keys are the dependencies.** A `LaunchedEffect`/`produceState`/`DisposableEffect` key list is the set of values that, when they change, should cancel-and-restart the effect. Too few keys → stale captures; too many or churny → thrash. `rememberUpdatedState` is the "single effect, fresh value" escape hatch.
3. **`derivedStateOf` filters notifications; `snapshotFlow` bridges to Flow.** Use `derivedStateOf` for churny-input/stable-output values; use `snapshotFlow` to turn snapshot state into a `Flow` and bring Week 5's `debounce`/`flatMapLatest` to bear on UI state.

You now have both halves of a real screen: snapshot state that survives the lifecycle, and side effects keyed to that lifecycle. The exercises make you pick the right effect for six scenarios and fix the body-launch bug; the challenge plants the wrong-key and leaked-listener footguns and makes you fix them; the mini-project builds a debounced, cancellation-correct, rotation-proof search with `snapshotFlow` and no `ViewModel`. Go make your effects fire exactly the number of times they should.
