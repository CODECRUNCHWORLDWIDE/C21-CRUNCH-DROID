# Week 08 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 09. Answer key with explanations at the bottom — don't peek.

---

**Q1.** What is a `MutableState<T>` created by `mutableStateOf`?

- A) A plain Kotlin variable with a fancy name.
- B) A snapshot-backed cell whose reads are tracked and whose writes notify the scopes that read it.
- C) A `StateFlow`.
- D) A field stored in a database.

---

**Q2.** Why does `var count by remember { mutableStateOf(0) }` need both `remember` and `mutableStateOf`?

- A) They're redundant; either alone works.
- B) `mutableStateOf` makes the value observable (reads subscribe, writes notify); `remember` keeps the same `MutableState` object across recompositions so changes are visible.
- C) `remember` makes it observable; `mutableStateOf` keeps it.
- D) Both are required by syntax but do the same thing.

---

**Q3.** You type into a field whose state is held in `remember { mutableStateOf("") }`, then rotate the device. What happens?

- A) The text survives; `remember` persists across configuration changes.
- B) The text is lost; `remember` survives recomposition but not a configuration change. Use `rememberSaveable`.
- C) The app crashes.
- D) The text survives only if the keyboard is open.

---

**Q4.** What can `rememberSaveable` store without a custom `Saver`?

- A) Anything, including arbitrary classes.
- B) Primitives, `String`, `Parcelable`/`Serializable`, and arrays/lists of those — the saved-instance-state bundle's supported types.
- C) Only `Int`.
- D) Only Compose `State` objects.

---

**Q5.** What does state hoisting mean, and what's the data-flow contract?

- A) Moving state into a `ViewModel`, always.
- B) Lifting state out of a composable to make it stateless: state flows *down* as parameters, events flow *up* as lambdas (the `value` / `onValueChange` contract) — unidirectional data flow.
- C) Caching state on disk.
- D) Sharing state between two apps.

---

**Q6.** A network call placed directly in a composable's body (not in any effect). What happens?

- A) It runs once, correctly.
- B) It runs on every recomposition, firing repeated requests — a footgun. Wrap launch-work in `LaunchedEffect` (on-appear) or `rememberCoroutineScope` (event).
- C) It never runs.
- D) It runs on a background thread automatically.

---

**Q7.** You want a coroutine that loads data when the screen appears and reloads when `userId` changes. Which API and key?

- A) `rememberCoroutineScope().launch` in the body.
- B) `LaunchedEffect(userId) { ... }` — runs on enter, cancels and restarts when `userId` changes, cancels on leave.
- C) `SideEffect { ... }`.
- D) `derivedStateOf { ... }`.

---

**Q8.** A button's `onClick` needs to launch a coroutine. Which API?

- A) `LaunchedEffect` inside the `onClick`.
- B) `rememberCoroutineScope().launch { ... }` — the work starts from an event, and you can't call `LaunchedEffect` from a lambda.
- C) `produceState`.
- D) `DisposableEffect`.

---

**Q9.** You register a `LifecycleObserver` while a composable is on screen. Which API ensures it's removed?

- A) `LaunchedEffect` — it cleans up automatically.
- B) `DisposableEffect(key) { ...register...; onDispose { ...unregister... } }` — setup paired with mandatory teardown on leave/key change.
- C) `SideEffect`.
- D) `remember`.

---

**Q10.** When does `derivedStateOf` earn its keep?

- A) Always — wrap every computed value in it.
- B) When a calculation reads frequently-changing state but produces a result that changes rarely (so readers recompose only when the *result* changes, not on every input change).
- C) Never; it's deprecated.
- D) Only for `String` concatenation.

---

**Q11.** A `LaunchedEffect(Unit)` body reads `userId`, but the effect never restarts when `userId` changes. Why, and what's the fix?

- A) `Unit` is the wrong type; use `true`.
- B) Keys are the effect's dependencies; keyed on `Unit`, it captures the first `userId` and never restarts. Fix: `LaunchedEffect(userId)`.
- C) `LaunchedEffect` can't read parameters.
- D) The fix is to add a `delay`.

---

**Q12.** You need one 5-second timer that, when it fires, calls the *latest* `onTimeout` lambda — without restarting on recomposition. What do you use?

- A) `LaunchedEffect(onTimeout)`.
- B) `rememberUpdatedState(onTimeout)` to hold the latest lambda, plus `LaunchedEffect(Unit)` so the timer isn't restarted; call the updated state inside.
- C) `derivedStateOf`.
- D) `SideEffect`.

---

**Q13.** How do you debounce a `TextField`'s query and cancel a prior in-flight search when a new query arrives, using this week's tools?

- A) A `Handler.postDelayed` in the `onValueChange`.
- B) `snapshotFlow { query }.debounce(300).distinctUntilChanged().flatMapLatest { repo.search(it) }`, collected in a `LaunchedEffect` — `snapshotFlow` bridges to Flow, `debounce` waits for a pause, `flatMapLatest` cancels the prior search.
- C) A `while` loop polling the query.
- D) `derivedStateOf { repo.search(query) }`.

---

## Answer key

**Q1 — B.** A `MutableState` is a snapshot-backed cell: reading `.value` subscribes the current scope, writing it notifies subscribers (if the value actually changed by the mutation policy). It is not a plain variable and not a `StateFlow`. (Lecture 1, §1–2.)

**Q2 — B.** `mutableStateOf` provides observability (reads subscribe, writes notify); `remember` provides identity across recompositions (the same `MutableState` is returned). Without `remember` you'd get a fresh state every recomposition and never see a change. (Lecture 1, §1.)

**Q3 — B.** `remember` survives recomposition but not a configuration change — the Activity and composition are recreated, and `remember` has nothing to restore from. `rememberSaveable` writes to the saved-state bundle and survives. (Lecture 1, §3–4.)

**Q4 — B.** The saved-state bundle supports primitives, `String`, `Parcelable`/`Serializable`, and arrays/lists thereof. Anything else needs a `Saver` (or `@Parcelize`). Keep it small — oversizing throws `TransactionTooLargeException`. (Lecture 1, §4.)

**Q5 — B.** Hoisting lifts state out so the composable is a stateless, pure function of its inputs: state down as parameters, events up as lambdas (`value`/`onValueChange`). That's UDF, and it makes composables testable, previewable, and reusable. (Lecture 1, §6.)

**Q6 — B.** The composable body runs on every recomposition, so a bare network call fires repeatedly. Launch-work belongs in `LaunchedEffect` (on-appear/key-change) or `rememberCoroutineScope` (event). (Lecture 2, §1.)

**Q7 — B.** `LaunchedEffect(userId)` runs the coroutine on enter and cancels-and-restarts when `userId` changes (and cancels on leave). The key is the dependency. (Lecture 2, §2.)

**Q8 — B.** Event-driven work launches from `rememberCoroutineScope().launch`; you can't call the composable `LaunchedEffect` from inside a lambda, and gating it with a flag would just reimplement `LaunchedEffect` badly. (Lecture 2, §3.)

**Q9 — B.** `DisposableEffect` pairs setup with a mandatory `onDispose` teardown that runs on leave (and before re-running on key change). Anything you register, you unregister there. A `LaunchedEffect` with no cleanup leaks. (Lecture 2, §4.)

**Q10 — B.** `derivedStateOf` is for churny-input / stable-output: it recomputes when inputs change but only notifies readers when the *result* changes. Wrapping every computed value is the wrong instinct; it's a notification filter, not a memoizer. (Lecture 2, §6.)

**Q11 — B.** The key list is the dependency list. Keyed on `Unit`, the effect captures the first `userId` and never restarts. Fix: key on `userId` so it cancels-and-restarts when the id changes. (Lecture 2, §2 + §10 footgun 2.)

**Q12 — B.** `rememberUpdatedState(onTimeout)` keeps a `State` pointing at the latest lambda; `LaunchedEffect(Unit)` runs the timer once (not restarted on recomposition); the body calls the updated state to invoke the freshest lambda. (Lecture 2, §10 footgun 3.)

**Q13 — B.** `snapshotFlow { query }` bridges snapshot state to a `Flow`; `debounce` waits for a typing pause; `distinctUntilChanged` drops duplicates; `flatMapLatest` cancels the prior search when a new query arrives. Collected in a `LaunchedEffect` so it's tied to the composition lifecycle. (Lecture 2, §8; mini-project Milestone 2.)

---

*Score 11+? On to Week 09. Below 9? Re-read both lecture notes and re-run exercises 2 and 3 — picking the right side-effect API and the `snapshotFlow`-debounce bridge are the two ideas this week is graded on.*
