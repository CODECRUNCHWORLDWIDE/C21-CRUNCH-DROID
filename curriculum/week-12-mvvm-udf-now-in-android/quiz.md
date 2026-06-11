# Week 12 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 13. Answer key with explanations at the bottom — don't peek.

---

**Q1.** What is the grammar that MVVM-with-UDF, MVI, and pure Compose state all share?

- A) They all use a `ViewModel`.
- B) State flows down, events flow up, and there is one source of truth per piece of state.
- C) They all use Hilt.
- D) They all forbid `StateFlow`.

---

**Q2.** Why does Now-In-Android pick MVVM-with-UDF over MVI for most screens?

- A) MVI is deprecated.
- B) It gets the full benefit of UDF — one source of truth, UI as a function of state, testable state production — with the least ceremony for a typical screen.
- C) MVVM is the only one that supports Compose.
- D) MVI can't survive process death.

---

**Q3.** What's wrong with a flat `data class UiState(isLoading, articles, error)`?

- A) Nothing; it's the recommended shape.
- B) It can represent contradictions (loading AND errored AND has data at once), forcing each screen to defensively decide which flag wins.
- C) Data classes can't be used in Compose.
- D) It's too small.

---

**Q4.** Why model `UiState` as a sealed type?

- A) Sealed types are faster.
- B) A value is exactly one variant, so contradictory states are unrepresentable; rendering is an exhaustive `when`; and smart casts give typed data per variant.
- C) Sealed types serialize automatically.
- D) It's required by `StateFlow`.

---

**Q5.** Why does a `ViewModel` expose `StateFlow` and hold `MutableStateFlow` privately?

- A) Performance.
- B) So the UI can read state and call methods but cannot set state directly — the only path to a new state is a `ViewModel` method, preserving the single source of truth.
- C) `MutableStateFlow` can't be public in Kotlin.
- D) It's a style preference with no effect.

---

**Q6.** What does `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Loading)` do?

- A) Nothing; it's optional decoration.
- B) Converts a cold `Flow` into a hot `StateFlow` with `Loading` as the initial value, keeping the upstream alive while there's a collector plus 5s after the last leaves.
- C) Saves state to disk.
- D) Makes the flow collect on the main thread.

---

**Q7.** Why is the 5-second grace in `WhileSubscribed(5000)` there?

- A) To slow the app down.
- B) So a configuration change — which momentarily drops the collector while the UI recreates — doesn't tear down and re-run the upstream, while a real backgrounding still lets it stop.
- C) It's an arbitrary default with no purpose.
- D) To delay the first emission by 5 seconds.

---

**Q8.** Why use `collectAsStateWithLifecycle()` instead of `collectAsState()` for screen state?

- A) No difference.
- B) It collects only while the screen is at least STARTED, so collection stops when backgrounded — which, with `WhileSubscribed`, lets the upstream stop producing state nobody can see.
- C) `collectAsState` doesn't compile in Compose.
- D) It's faster to type.

---

**Q9.** In the Now-In-Android layers, which direction may dependencies point?

- A) Any direction.
- B) UI → domain → data, never the reverse; the data layer must not import a `ViewModel`.
- C) Data → UI.
- D) They must not depend on each other at all.

---

**Q10.** What survives a configuration change (rotation) but NOT process death?

- A) Nothing survives either.
- B) The `ViewModel` (and its in-memory state) survives rotation but is destroyed with the process on a system kill.
- C) `SavedStateHandle` survives rotation but not process death.
- D) Everything survives both.

---

**Q11.** What belongs in `SavedStateHandle`, and what should you recompute?

- A) Everything in `SavedStateHandle`.
- B) Save the small, user-created inputs (query, selection, id); recompute the large, derived outputs (search results, loaded lists) from the restored inputs.
- C) Save the outputs; recompute the inputs.
- D) Save nothing; recompute everything.

---

**Q12.** Why not store search *results* in `SavedStateHandle`?

- A) Results aren't serializable.
- B) They're derived from the saved query and recompute on recreation; saving them bloats the `Bundle` toward its hard size limit (`TransactionTooLargeException`) and risks stale data.
- C) `SavedStateHandle` can't hold lists.
- D) Results never change.

---

**Q13.** How do you test a `ViewModel`'s `Loading → Success` transition without an emulator?

- A) You can't; it needs an Activity.
- B) Construct the `ViewModel` with a fake repository, drive it under `runTest`, and assert the emission sequence with Turbine's `awaitItem()`.
- C) Mock the entire Android framework.
- D) Run it in the emulator and screenshot.

---

## Answer key

**Q1 — B.** All three spell the same grammar — state down, events up, one source of truth — differing only in how strictly they spell the event stream. (Lecture 1, §1–2.)

**Q2 — B.** MVVM-with-UDF gets UDF's full benefit with the least ceremony for a typical screen; MVI's explicit intent channel and reducer pay off for very state-heavy screens. (Lecture 1, §1.)

**Q3 — B.** Flat flags can represent contradictions (loading + errored + data), so each screen defensively picks a winner and they disagree — the inconsistent-UI bug source. (Lecture 1, §3.)

**Q4 — B.** A sealed value is exactly one variant (no contradictions), rendered with an exhaustive compiler-checked `when`, with smart casts exposing each variant's typed data. Same "illegal states unrepresentable" move as typed routes. (Lecture 1, §3.)

**Q5 — B.** Read-only exposure means the only path to a new state is a `ViewModel` method — the single source of truth. Exposing the mutable flow lets the UI set state, reintroducing the bugs UDF prevents. (Lecture 1, §4.)

**Q6 — B.** `stateIn` makes a cold `Flow` a hot `StateFlow` with an initial value, sharing one upstream while subscribed plus the grace period. (Lecture 2, §2.)

**Q7 — B.** The grace bridges the brief collector-drop of a configuration change so the upstream isn't torn down and re-run, while a real backgrounding (collector gone for good) still stops it. (Lecture 2, §2.)

**Q8 — B.** Lifecycle-aware collection stops when the screen isn't visible; paired with `WhileSubscribed`, the upstream then stops producing state nobody can see. (Lecture 1, §5; lecture 2, §2.)

**Q9 — B.** UI → domain → data, never reverse. The data layer not importing a `ViewModel` is what keeps it testable and swappable. (Lecture 2, §1.)

**Q10 — B.** The `ViewModel` survives rotation but dies with the process on a system kill — which is exactly why saved inputs go in `SavedStateHandle`. (Lecture 2, §3.)

**Q11 — B.** Save the inputs (query, selection, id — small, user-created, unrecoverable); recompute the outputs (results, lists) from them. (Lecture 2, §3.)

**Q12 — B.** Results are derived from the saved query and recompute on recreation; saving them bloats the `Bundle` (hard size limit) and can serve stale data. (Lecture 2, §3.)

**Q13 — B.** The `ViewModel` is plain Kotlin and the repository is a fakeable interface; `runTest` + Turbine assert the emission sequence on the JVM in milliseconds. (Lecture 2, §5; exercise 02.)

---

*Score 11+? On to Week 13 and Phase 3. Below 9? Re-read both lecture notes and re-run exercises 1 and 2 — sealed `UiState` and the ViewModel's derived `StateFlow` are the two ideas this week is graded on.*
