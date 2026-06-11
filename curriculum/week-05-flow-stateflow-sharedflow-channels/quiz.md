# Week 05 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 06. Answer key with explanations at the bottom — don't peek.

---

**Q1.** What best describes a *cold* `Flow`?

- A) A flow that always has a current value, shared across all collectors.
- B) A suspending producer recipe that runs nothing until collected and re-runs from scratch for each collector.
- C) A flow that runs once eagerly and caches its result forever.
- D) A flow that can only emit a single value.

---

**Q2.** You `collect` the same cold `flow { val x = api.fetch(); emit(x) }` in two different places. How many times does `api.fetch()` run?

- A) Once — the result is cached.
- B) Twice — each collection re-runs the producer from the top.
- C) Zero — the flow is lazy and never runs.
- D) It depends on the dispatcher.

---

**Q3.** A search box should fire a request per settled query, and a new query must cancel the in-flight request for the previous one. Which flat-map operator?

- A) `flatMapConcat`
- B) `flatMapMerge`
- C) `flatMapLatest`
- D) `map`

---

**Q4.** With `flatMapMerge` instead of `flatMapLatest` on the search box, what bug ships?

- A) Nothing — they behave the same.
- B) Stale results: every query's request runs concurrently and they return out of order, so the screen can show results for a query the user already deleted.
- C) The search never returns any results.
- D) Each request runs twice.

---

**Q5.** A fast producer feeds a slow collector and you only care about the *latest* value; dropping intermediates is correct. Which operator?

- A) `buffer(Channel.UNLIMITED)`
- B) `conflate()`
- C) `flatMapConcat`
- D) `toList()`

---

**Q6.** Inside a `flow { }` builder you write `withContext(Dispatchers.IO) { emit(x) }`. What happens?

- A) It works fine and emits on IO.
- B) A runtime `IllegalStateException` — "Flow invariant is violated" — because you may not emit from a different context; use `flowOn` instead.
- C) A compile error.
- D) It silently drops the value.

---

**Q7.** What distinguishes a `StateFlow` from a `SharedFlow`?

- A) Nothing; they are aliases.
- B) A `StateFlow` always has a current `value`, conflates, and de-duplicates equal values (state); a `SharedFlow` is configurable (`replay`, buffer) and has no required current value (events/multicast).
- C) A `SharedFlow` is cold; a `StateFlow` is hot.
- D) A `StateFlow` cannot be collected by more than one collector.

---

**Q8.** You model a one-shot "show snackbar" event as a `StateFlow<Event?>`. What bug appears on screen rotation?

- A) The snackbar never shows.
- B) The snackbar fires again, because the new post-rotation collector receives the cached last event — events should be a `SharedFlow(replay = 0)`.
- C) The app crashes.
- D) The event is delivered to the wrong screen.

---

**Q9.** Why is `SharingStarted.WhileSubscribed(5000)` the Android default for `stateIn`/`shareIn`?

- A) 5000 is the maximum allowed value.
- B) It starts on the first subscriber and stops 5 seconds after the last one leaves, so a configuration change (old collector leaves, new one subscribes a moment later) does not tear down and re-run the upstream, but actually leaving the screen does stop it.
- C) It makes the flow cold again.
- D) It disables conflation.

---

**Q10.** You bridge a `LocationManager` (callback API) into a Flow with `callbackFlow` but forget `awaitClose`. What happens?

- A) Nothing; `awaitClose` is optional.
- B) The builder throws at runtime telling you `awaitClose` is required — because without unregistering the listener, you leak it (and the screen it references).
- C) The flow emits nothing.
- D) The flow completes immediately.

---

**Q11.** Why must a callback API be bridged with `callbackFlow`/`channelFlow` rather than a plain `flow { }`?

- A) `flow { }` can't emit more than once.
- B) The SDK invokes the listener on its own thread, outside the flow's coroutine; a plain `flow { }` forbids emitting from a different context, while `callbackFlow` is channel-backed so `trySend` from any thread is allowed.
- C) `flow { }` is cold and callbacks need hot.
- D) `callbackFlow` is faster.

---

**Q12.** What is the key behavioural difference between a `Channel` and a `SharedFlow` for delivering values to multiple consumers?

- A) They are identical.
- B) A `SharedFlow` broadcasts — every active collector gets every value; a `Channel` distributes — each value goes to exactly one receiver.
- C) A `Channel` broadcasts; a `SharedFlow` distributes.
- D) A `Channel` can only have one sender.

---

**Q13.** In a flow test, instead of `Thread.sleep(200); assertEquals(expected, lastSeen)`, what is the correct deterministic approach?

- A) Increase the sleep to 500ms.
- B) Use Turbine's `test { }` with `awaitItem()`/`awaitComplete()`/`expectNoEvents()` under `runTest` virtual time, asserting on the exact emission sequence with no real waiting.
- C) Run the test 100 times and take the majority result.
- D) Use `runBlocking` with a real delay.

---

## Answer key

**Q1 — B.** A cold flow is a lazy producer recipe: nothing runs until a terminal operator collects it, and it re-runs from scratch per collector. (Lecture 1, §1.)

**Q2 — B.** Cold = per-collector re-execution. Two collections run the producer twice, so `api.fetch()` runs twice. The fix for "run once, share" is `shareIn`/`stateIn`. (Lecture 1, §1; lecture 2, §6 footgun 1; exercise 01.)

**Q3 — C.** `flatMapLatest` cancels the inner flow for the previous value when a new one arrives — exactly latest-wins search semantics. (Lecture 1, §3; exercise 02.)

**Q4 — B.** `flatMapMerge` runs every query's request concurrently and merges results as they arrive, out of order — so a slow stale request can land last and show results for a deleted query. (Lecture 1, §3; exercise 02.)

**Q5 — B.** `conflate()` keeps only the latest value and drops intermediates the collector missed — correct when stale values are useless (UI state). `buffer(UNLIMITED)` would accumulate without bound. (Lecture 1, §4.)

**Q6 — B.** Emitting from a `withContext`-switched block breaks context preservation; you get a runtime "Flow invariant is violated." Use `flowOn` to change the upstream dispatcher declaratively. (Lecture 1, §5.)

**Q7 — B.** `StateFlow` always has a value, conflates, and de-duplicates equal values — the state primitive. `SharedFlow` is configurable (`replay`, buffer) with no required current value — the event/multicast primitive. (Lecture 2, §1–2.)

**Q8 — B.** A one-shot event as `StateFlow` (or `SharedFlow` with `replay > 0`) caches and replays its last value; the post-rotation collector re-fires the snackbar. Events must be `SharedFlow(replay = 0)`. (Lecture 2, §2, §6 footgun 2; challenge.)

**Q9 — B.** `WhileSubscribed(5000)` starts on the first subscriber and stops 5s after the last leaves, so rotation doesn't re-run the upstream while actually leaving the screen does stop it. (Lecture 2, §3.)

**Q10 — B.** `awaitClose` is mandatory in `callbackFlow`; the builder throws at runtime if you omit it, because a callback bridge without unregistration leaks the listener and the screen it references. (Lecture 2, §5; exercise 03.)

**Q11 — B.** The SDK calls the listener on its own thread; a plain `flow { }` requires context preservation (no cross-context emit), whereas `callbackFlow` is channel-backed and allows `trySend` from any thread. (Lecture 2, §5.)

**Q12 — B.** `SharedFlow` broadcasts (every collector gets every value); a `Channel` distributes (each value to exactly one receiver). Pick by whether you want broadcast or single-delivery. (Lecture 2, §4; challenge step 4.)

**Q13 — B.** Turbine `test { }` with `awaitItem`/`awaitComplete`/`expectNoEvents` under `runTest` virtual time gives deterministic emission-by-emission assertions with no real waiting — the week's "assert on emissions, never sleep" promise. (All exercises; mini-project.)

---

*Score 11+? On to Week 06. Below 9? Re-read both lecture notes and re-run exercises 01 and 02 — the cold-vs-hot distinction and the `flatMapLatest` cancel-previous semantic are the two ideas this week is graded on.*
