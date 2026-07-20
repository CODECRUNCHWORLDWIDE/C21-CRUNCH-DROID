# Challenge 1 — Plant a "lost on process death" bug, then fix it (and prove it)

**Time.** 60–120 minutes.
**Deliverable.** A short report (`PROCESS_DEATH.md`) with the symptom, the reproduction steps, the before (state lost) and after (state survives), the diff that moved the slice to `SavedStateHandle`, and a `SavedStateHandle` round-trip test — committed to your Week 12 repo.

## The premise

Every Android engineer has, at least once, shipped state that survives *rotation* (so it looked fine in testing) but vanishes on *process death* (so users on memory-constrained phones lose their work). The classic: a search query in a `remember`, or in a `ViewModel` field but *not* in `SavedStateHandle`. It survives rotation because the `ViewModel` survives rotation. It dies when the system kills the backgrounded process to reclaim memory — which happens constantly on real low-end devices and almost never on your dev phone.

The skill this challenge builds is not "know process death exists." It is: **plant the bug, reproduce the loss with the system tool that simulates it, fix it by moving the right slice to `SavedStateHandle`, and prove the fix — the input survives, the output recomputes.** A fix you haven't reproduced and re-tested is a guess.

## What to build

### Step 1 — A search screen with the state in the wrong place (the *before*)

Build a search screen where the query lives in a `remember` (or in a plain `ViewModel` field, not `SavedStateHandle`). It works perfectly until the process dies.

```kotlin
@Composable
fun SearchScreenBuggy(repository: NewsRepository) {
    // THE BUG: query in a remember. Survives recomposition and rotation
    // (rememberSaveable would survive rotation too) — but a plain remember does
    // NOT survive process death, and even a ViewModel field wouldn't.
    var query by remember { mutableStateOf("") }
    val results by produceState(emptyList<Article>(), query) {
        value = if (query.isBlank()) emptyList() else repository.search(query).first()
    }
    Column {
        TextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search") })
        LazyColumn { items(results) { Text(it.title) } }
    }
}
```

### Step 2 — Reproduce the loss with "Don't keep activities"

This is the heart of the challenge — *reproduce the bug*, don't just assert it exists. Enable the system tool that simulates process death:

1. On the emulator: **Settings ▸ System ▸ Developer options ▸ Don't keep activities → ON.** (This destroys every activity the moment it's backgrounded — simulating the worst-case memory kill, every time.)
2. Run the app, type a query (`kotlin`), see results.
3. Press **Home** to background the app.
4. Reopen the app from Recents.
5. **The query field is empty.** The state is gone. (Without "Don't keep activities," you'd have to wait for a real low-memory kill, which is exactly why this bug ships — it's invisible on a healthy dev device.)

Record this: the steps, and a screenshot or note of the empty field. That's your "before" evidence — a reproduced, not theorized, data-loss bug.

### Step 3 — Confirm it's process death, not rotation, that breaks it

To make the distinction concrete: with "Don't keep activities" *off*, rotate the device on the search screen. The query *survives* (if it's in a `rememberSaveable` or a `ViewModel`) — proving rotation isn't the problem. Turn "Don't keep activities" back *on* and repeat: it dies. The difference between the two is the entire lesson — rotation keeps the process alive; a memory kill doesn't.

### Step 4 — Fix it: move the input to `SavedStateHandle`

Move the query — the user-created *input* — into a `ViewModel`'s `SavedStateHandle`. Keep the results *derived*, not stored:

```kotlin
class SearchViewModel(
    private val savedStateHandle: SavedStateHandle,
    repository: NewsRepository
) : ViewModel() {
    val query = savedStateHandle.getStateFlow("query", "")        // SAVED input — survives process death
    fun onQueryChange(q: String) { savedStateHandle["query"] = q }

    val results = query.flatMapLatest { q ->                       // DERIVED output — recomputed, not saved
        if (q.isBlank()) flowOf(emptyList()) else repository.search(q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun SearchScreenFixed(viewModel: SearchViewModel) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    Column {
        TextField(value = query, onValueChange = viewModel::onQueryChange, placeholder = { Text("Search") })
        LazyColumn { items(results) { Text(it.title) } }
    }
}
```

### Step 5 — Prove the fix survives the kill

Repeat Step 2's reproduction with the fixed screen and "Don't keep activities" ON:

1. Type `kotlin`, see results.
2. Home, reopen.
3. **The query is restored to `kotlin`, and the results re-appear** — derived from the restored query.

Record the "after": the query survived, the results recomputed, nothing was lost. Then write the `SavedStateHandle` round-trip test (exercise 3's template) so the survival is verified in CI, not just by hand:

```kotlin
@Test
fun query_survivesRecreation() = runTest {
    val saved = SavedStateHandle()
    SearchViewModel(saved, FakeRepo()).onQueryChange("kotlin")
    assertEquals("kotlin", SearchViewModel(saved, FakeRepo()).query.value)   // survived a "kill"
}
```

### Step 6 (optional, for the stretch) — measure what you did NOT save

Demonstrate *why* you don't save the results: log (or estimate) the serialized size of the query vs. the results. The query is a handful of bytes; a list of full articles could be kilobytes-to-megabytes, and the saved-state `Bundle` has a hard limit (`TransactionTooLargeException`). Note the size difference and the "recompute outputs" rationale.

## Acceptance criteria

- [ ] `SearchScreenBuggy` puts the query in a `remember` (or a non-saved `ViewModel` field), and you **reproduced** the loss with "Don't keep activities" on (steps + evidence recorded).
- [ ] You demonstrated the rotation-survives / process-death-loses distinction (Step 3).
- [ ] `SearchViewModel` with the query in `SavedStateHandle` and the results **derived** (not stored).
- [ ] You **reproduced the survival**: query restored and results recomputed after a simulated kill.
- [ ] A passing `SavedStateHandle` round-trip test.
- [ ] `PROCESS_DEATH.md` records: the symptom, the reproduction steps, before/after, the diff, and the "save inputs, recompute outputs" rationale.
- [ ] (Stretch) The size comparison justifying not saving the results.
- [ ] Build with **0 warnings**.

## What "great" looks like

A weak submission says "I moved the query to `SavedStateHandle`." A great submission says:

> With "Don't keep activities" on, typing a query, backgrounding, and reopening left the search field empty — the query lived in a `remember`, which survives rotation (I confirmed it does) but not a process kill. Real users hit this on memory-constrained phones; my dev Pixel almost never kills the process, which is why the bug was invisible until I forced it. I moved the query — the user-created input — into `SavedStateHandle` via `getStateFlow`, and kept the results *derived* from it with `flatMapLatest`. After the fix, the same reproduction restores the query and recomputes the results: nothing lost. I save the query (~6 bytes) and not the results (a list of articles, potentially kilobytes, against the `Bundle`'s hard size limit) because the results are an output the ViewModel re-derives from the restored input. The round-trip test now guards it in CI.

Reproduced, distinguished from rotation, fixed at the right layer, tested, and honest about why the input is saved and the output isn't. That's the senior-engineer answer.

## Where this reappears

The "save the inputs, recompute the outputs" instinct — and the discipline of *reproducing* a state-survival bug rather than reasoning about it — is exactly what Phase 3's offline-sync and WorkManager weeks build on, where the question becomes "what survives a process kill mid-sync, and what re-derives from the persisted source of truth?" Process death is the smallest version of the durability problems the production phase is all about. You met it here.
