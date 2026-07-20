# Mini-Project — Search-as-you-type: debounced, cancellation-correct, rotation-proof

This week you build a **search-as-you-type** screen entirely with this week's tools: snapshot state for the query, `snapshotFlow` to bridge it into a `Flow`, Week 5's `debounce`/`flatMapLatest` to make it efficient and race-free, and `rememberSaveable` so a rotation never wipes the user's typed text. No `ViewModel` (that's Week 12), no real network (that's Phase 3) — just the snapshot-and-effect plumbing that every search screen in every Android app is built on, done correctly.

The point of the project is to prove how much you can do right with snapshots and effects alone: a field that debounces input so you don't fire a request per keystroke, cancels the prior in-flight query when a new keystroke arrives (so a slow old result never overwrites a fast new one), shows loading and empty and error states, and survives a configuration change without losing the query or restarting the search. That combination — debounced, cancellation-correct, state-complete, rotation-proof — is the senior instinct this week installs.

This is a *fresh* screen, not a continuation. You start from an Empty Activity Compose project. The architecture, theming, and real networking come later; this week is state and side effects, alone, doing their job well.

---

## Where you're starting from

An Empty Activity Compose project (or your Week 7 `Scratch`/Pomodoro app). You need:

- The Compose BOM and the Compose Compiler plugin (template-wired).
- `kotlinx-coroutines` (from Week 4) for `Flow` and the operators.
- For the optional test: Turbine + `kotlinx-coroutines-test`.

## What you're building toward

By the end you have:

- A `SearchScreen` with a `TextField` whose query lives in `rememberSaveable`.
- A `snapshotFlow { query }` pipeline that `debounce`s, dedupes, filters short queries, and `flatMapLatest`es into a (fake) repository search.
- A `SearchUiState` (Loading / Empty / Results / Error) rendered correctly for each case.
- Cancellation correctness: a slow query that's superseded never overwrites the newer result.
- Rotation survival: type a query, rotate, the text and results are intact; the search doesn't restart from scratch.
- A short clip or screenshots in your README showing the rotation survival and the debounce behavior (one request per pause, not per keystroke).

---

## Milestone 1 — The fake repository and the UI state (≈ 1 h)

Build a repository with an artificial delay so debounce and cancellation matter, and model the UI state as an immutable sealed type (Week 7 stability + Week 2 sealed types).

```kotlin
data class Hit(val id: String, val title: String)

class FakeSearchRepository {
    private val corpus = listOf(
        Hit("1", "Jetpack Compose"), Hit("2", "Kotlin Coroutines"),
        Hit("3", "Compose Snapshots"), Hit("4", "Kotlin Flow"),
        Hit("5", "Android Runtime"), Hit("6", "Coroutine Cancellation")
    )

    // Artificial latency so debounce/cancellation are observable. Cooperative:
    // delay() is a cancellation point, so flatMapLatest can cancel a slow search.
    suspend fun search(query: String): List<Hit> {
        delay(600)                                  // pretend network
        return corpus.filter { it.title.contains(query, ignoreCase = true) }
    }
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Results(val hits: List<Hit>) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
}
```

Decisions you must be able to defend in review:

- **Why a sealed `SearchUiState`?** Loading, results, empty, and error are mutually exclusive; a sealed type makes the `when` in the UI exhaustive (Week 2), so you can't forget a state. It's also stable (Week 7), so the rendering composable is skippable.
- **Why the `delay(600)`?** Without latency, debounce and cancellation are invisible. The delay makes "the old search was cancelled when I kept typing" something you can *see* in the result timing.

## Milestone 2 — The query state and the snapshotFlow pipeline (≈ 1.5 h)

Hold the query in `rememberSaveable` (survives rotation, lecture 1), the UI state in `remember`, and wire the pipeline in a `LaunchedEffect`:

```kotlin
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Composable
fun SearchScreen(repo: FakeSearchRepository) {
    var query by rememberSaveable { mutableStateOf("") }
    var uiState by remember { mutableStateOf<SearchUiState>(SearchUiState.Idle) }

    LaunchedEffect(Unit) {
        snapshotFlow { query }                       // bridge snapshot state -> Flow<String>
            .map { it.trim() }
            .debounce(300)                           // wait for a typing pause
            .distinctUntilChanged()                  // drop duplicate queries
            .flatMapLatest { q ->                    // CANCEL the prior search on a new query
                if (q.length < 2) {
                    flowOf(SearchUiState.Idle)
                } else {
                    flow {
                        emit(SearchUiState.Loading)
                        val hits = repo.search(q)    // cancelled if a newer q arrives
                        emit(if (hits.isEmpty()) SearchUiState.Empty else SearchUiState.Results(hits))
                    }.catch { emit(SearchUiState.Error(it.message ?: "Search failed")) }
                }
            }
            .collect { uiState = it }                // push each state back into snapshot state
    }

    SearchContent(query = query, onQueryChange = { query = it }, state = uiState)
}
```

This is the heart of the project. Read it as a sentence: *take the stream of query values, normalize whitespace, wait for a typing pause, drop duplicates, and for each settled query — cancelling any in-flight search — emit Loading then the result (or Empty/Error), pushing each state back into the UI.* Every operator is Week 5; `snapshotFlow` is the only new bridge.

The cancellation correctness comes entirely from **`flatMapLatest`**: when a new debounced query arrives, the previous inner flow (including the in-flight `repo.search`) is cancelled. Because `delay` is a cooperative cancellation point (Week 4), the slow old search actually stops, and its result can never land after the new one. That's the race this design eliminates.

## Milestone 3 — Render every state (≈ 1 h)

The content composable is stateless (hoisted, lecture 1) and renders the sealed state exhaustively:

```kotlin
@Composable
fun SearchContent(
    query: String,
    onQueryChange: (String) -> Unit,
    state: SearchUiState
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        when (state) {                               // exhaustive over the sealed type
            SearchUiState.Idle ->
                Text("Type at least 2 characters to search.")
            SearchUiState.Loading ->
                CircularProgressIndicator()
            SearchUiState.Empty ->
                Text("No results for \"$query\".")
            is SearchUiState.Results ->
                LazyColumn {
                    items(state.hits, key = { it.id }) { hit ->   // keyed list (Week 7)
                        Text(hit.title, Modifier.padding(vertical = 8.dp))
                    }
                }
            is SearchUiState.Error ->
                Text("Error: ${state.message}")
        }
    }
}
```

Note the exhaustive `when` (no `else` — the compiler enforces you handled every state) and the keyed `LazyColumn` (Week 7, so reorders don't discard remembered state).

## Milestone 4 — Prove the debounce (≈ 0.5 h)

Add a request counter to the fake repo (increment in `search`) and log it. Type "compose" quickly and watch logcat: with `debounce(300)`, you should see **one** `search(...)` call after you pause, not one per keystroke. Remove the `debounce` operator temporarily and type the same word — now you see a call per keystroke. Put it back. Record both counts in your README; the difference is the debounce earning its keep.

## Milestone 5 — Prove the cancellation (≈ 0.5 h)

Type a query that takes the full 600ms, then immediately type more before it returns. Log the start and end of each `search`. You should see the first search **start but never finish** (it's cancelled by `flatMapLatest`), and only the latest query's search complete. This proves no stale result can overwrite a newer one — the race condition this design eliminates. Record the logcat.

## Milestone 6 — The rotation test (≈ 0.5 h)

The acceptance bar for the whole week.

1. Launch, type "kotlin", let results appear.
2. **Rotate the emulator** (`Ctrl+F11`).
3. The query text "kotlin" is **still in the field**, and the results are still shown (the `LaunchedEffect` re-runs `snapshotFlow { query }` against the restored "kotlin" and re-derives the state — it doesn't make the user retype).
4. Flip `rememberSaveable` back to `remember` and rotate again — the field clears, proving the boundary.
5. (Harder) Turn on "Don't keep activities," background and reopen — "kotlin" survives with `rememberSaveable`.

Record this as a short clip or screenshots in your README. "It survived a rotation without losing my query" is the deliverable.

---

## Acceptance criteria

- [ ] The query lives in `rememberSaveable`; it survives **rotation** and **process death**.
- [ ] The pipeline uses `snapshotFlow { query }` → `debounce` → `distinctUntilChanged` → `flatMapLatest`; you can explain each operator's job.
- [ ] **Debounce works:** one `search` call per typing pause, not per keystroke (proven with a counter/logcat).
- [ ] **Cancellation works:** a superseded slow search is cancelled and never overwrites a newer result (proven with start/end logs).
- [ ] `SearchUiState` is a sealed type rendered with an **exhaustive `when`** (Idle/Loading/Results/Empty/Error).
- [ ] The results list is a **keyed** `LazyColumn`.
- [ ] No `ViewModel` is used (deliberately — proving snapshots alone suffice this week).
- [ ] A short clip/screenshots show the rotation survival and the debounce behavior.
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **Test the pipeline.** Extract the `snapshotFlow`-fed pipeline into a pure `Flow<String> -> Flow<SearchUiState>` function (exercise 3's shape) and unit-test it with Turbine + `runTest`, asserting Loading→Results and the debounce/cancellation behavior in virtual time.
- **`produceState` variant.** Re-implement the search with `produceState(initial = Idle, debouncedQuery)` instead of a manual `LaunchedEffect` + `collect`, and compare which reads more clearly.
- **`derivedStateOf` for the submit button.** Add a "clear" button that's only enabled when `query` is non-empty, derived with `derivedStateOf` so it doesn't recompose on every unrelated state change.
- **Search history.** Keep the last 5 distinct successful queries in a `rememberSaveable` list (with a `listSaver`) and show them as chips when the field is empty — proving `rememberSaveable` with a custom `Saver`.

## Common pitfalls (and how to spot them)

These are the failures a reviewer sees most often on this project; catch them before you submit.

- **The search fires per keystroke, not per pause.** You forgot `debounce`, or you put it after `flatMapLatest` (so each query still launches a search before the debounce can coalesce). Order is `snapshotFlow → map/trim → debounce → distinctUntilChanged → flatMapLatest`. The request counter in Milestone 4 makes this visible — if it climbs per keystroke, the debounce isn't doing its job.
- **A stale result overwrites a newer one.** You used `flatMapConcat` or `flatMapMerge` instead of `flatMapLatest`, so a slow old search completes *after* a fast new one and clobbers it. Only `flatMapLatest` cancels the prior inner flow. The start/end logs in Milestone 5 expose this: if you ever see an *old* query's search complete after a newer one started, you're not using `flatMapLatest`.
- **Rotation clears the query.** The query is in `remember`, not `rememberSaveable`. Rotate the emulator; if the field empties, fix the holder. This is the week's promise — it's a hard fail if rotation loses the text.
- **The `LaunchedEffect` re-launches the collector on every recomposition.** You keyed it on something that changes each recomposition (a fresh lambda or object). Key it on `Unit` — the `snapshotFlow` inside already reacts to `query` changes, so the *collector* should start once and stay.
- **`@FlowPreview`/experimental warnings.** `debounce`/`flatMapLatest` need `@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)`. Add the opt-in; don't suppress the warning some other way.
- **The `when` over `SearchUiState` has an `else`.** If you needed an `else`, your state type isn't sealed, or you're not handling every case. Make it sealed and exhaustive — the compiler should force you to handle Idle/Loading/Results/Empty/Error with no `else`.
- **The results list isn't keyed.** `items(hits)` without `key = { it.id }` discards per-row state on changes (Week 7). Key it.

If you hit one of these, the fix is almost always one line, and the relevant lecture section is cited above. The point of the project is that a *correct* search is a specific composition of well-understood pieces, not a pile of code that happens to work.

## What this milestone earns you

You can now build a real, efficient, race-free, configuration-change-proof search screen using only this week's primitives: snapshot state, the `snapshotFlow` bridge, Week 5's Flow operators, and `rememberSaveable`. That is the literal "skill earned" line for the week — picking the right side-effect API the first time, composing snapshot state with Flow, and surviving rotation without a `ViewModel`. Week 9 takes the state-and-effect plumbing you wired here and adds the *interaction* layer — gestures, custom layout, animation, accessibility — and a draggable, dismissable card needs gesture state managed exactly the way you managed query state this week. Week 12 finally introduces the `ViewModel` you deliberately did *without* here, and you'll appreciate exactly what it adds because you'll have felt where `rememberSaveable` alone stops being enough.
