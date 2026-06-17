# Lecture 2 — The Now-In-Android layers, deriving state, SavedStateHandle, and surviving process death

Lecture 1 gave you the grammar — state down, events up, one source of truth — and the `ViewModel` that owns a sealed `UiState`. This lecture puts a real structure under it and makes it survive the worst thing the OS does to your app: killing the process. Four parts, in the order you hit them building a real feature: the **Now-In-Android layer split** (where code goes), **deriving state from a repository `Flow`** (how the `ViewModel` produces `UiState` reactively), **`SavedStateHandle`** (the slice that must outlive a process kill), and **testing the whole thing** (Turbine + a fake repository, no device).

We take them in build order: layers first (the skeleton), then state derivation (the wiring), then SavedStateHandle (the survival), then tests (the proof).

---

## 1. The Now-In-Android layers and the dependency rule

Now-In-Android organizes code into three layers, and the single most important fact about them is the **direction dependencies are allowed to point**: UI depends on domain depends on data — never the reverse.

```text
┌─────────────────────────────────────────────────────────┐
│  UI layer                                                │
│    ViewModel  +  composables                             │
│    owns UiState, collects from domain/data, renders      │
│         │  depends on                                    │
│         ▼                                                │
│  Domain layer (OPTIONAL — use only when it earns its keep)│
│    use cases: GetBookmarkedNewsUseCase, etc.             │
│    reusable business logic that combines repositories    │
│         │  depends on                                    │
│         ▼                                                │
│  Data layer                                              │
│    repositories  (the single source of truth for data)  │
│         │  depends on                                    │
│         ▼                                                │
│    data sources: Room DAO, Retrofit service, DataStore  │
└─────────────────────────────────────────────────────────┘
```

What each layer is for:

- **Data layer — the source of truth for *data*.** A `NewsRepository` exposes data as `Flow`s and `suspend` functions, hiding *where* the data comes from. The UI never knows whether news is from the network, a Room database, or an in-memory list — it asks the repository. This is why we draw the repository boundary now (Week 12) even though Room (Week 14) and networking (Week 15) come later: the repository *interface* is stable; the data source behind it swaps.

```kotlin
interface NewsRepository {
    fun newsStream(): Flow<List<Article>>          // reactive: emits when the data changes
    suspend fun refresh()                          // a one-shot action
}

// This week: an in-memory implementation. Week 14: Room. Week 15: network + Room.
class InMemoryNewsRepository : NewsRepository {
    private val state = MutableStateFlow(SeedArticles.all)
    override fun newsStream(): Flow<List<Article>> = state
    override suspend fun refresh() { delay(800); state.value = SeedArticles.shuffled() }
}
```

- **Domain layer — *optional* reusable business logic.** A use case (`GetBookmarkedNewsUseCase`) encapsulates logic that combines repositories or is reused across `ViewModel`s. NiA uses use cases *sparingly* — only when logic is genuinely shared or complex enough to extract. Do not add a use case per repository call out of habit; a `ViewModel` calling a repository directly is fine. The domain layer earns its place when two screens need the same combination, or the combination is intricate enough to test on its own.

- **UI layer — the `ViewModel` and composables.** The `ViewModel` collects from the data (or domain) layer, shapes it into `UiState`, and the composables render it (lecture 1). This is where state ownership lives.

The dependency rule — **UI → domain → data, never the reverse** — is what keeps the architecture testable and swappable. The data layer doesn't import the `ViewModel`; the repository doesn't know a UI exists. So you can test the data layer without a UI, swap the data source without touching the UI, and reuse the data layer across features. A dependency pointing the wrong way (a repository reaching up to a `ViewModel`) is the architectural smell that rots the whole thing.

### Layers as packages now, modules later

This week the layers are *packages* in one module (`ui/`, `domain/`, `data/`) — that's enough to enforce the dependency rule by convention and a code review. But the reason NiA structures code this way is that the layers grow into Gradle *modules*:

```text
:app                      ← assembles everything
:feature:news             ← UI layer for the news feature (ViewModel + screens)
:core:data                ← repositories
:core:database            ← Room (Week 14)
:core:network             ← Retrofit/Ktor (Week 15)
:core:model               ← shared domain models
```

When a layer is its own module, the dependency rule isn't just convention — it's *enforced by the build*: `:core:data` doesn't declare a dependency on `:feature:news`, so a repository *cannot even reference* a `ViewModel`; it won't compile. The wrong-direction dependency becomes impossible, not merely discouraged. Modules also buy parallel builds (independent modules compile concurrently) and clear ownership boundaries (a team owns `:feature:news`). We stay single-module this week to keep the focus on the architecture's *shape*; Week 13's Hilt and the multi-module DI graph turn these packages into modules. Draw the package boundaries cleanly now and the module split later is mechanical — that's why the boundary discipline matters even before the modules exist.

---

## 2. Deriving `StateFlow<UiState>` from a repository `Flow`

The interesting wiring is how the `ViewModel` turns a *reactive* repository `Flow` into a `StateFlow<UiState>` the UI collects. This is where Phase 1's Flow operators (`map`, `combine`, `stateIn`) do real work.

The pattern: take the repository's cold `Flow`, `map` it into `UiState`, and `stateIn` it to a hot `StateFlow` scoped to the `ViewModel`:

```kotlin
class FeedViewModel(repository: NewsRepository) : ViewModel() {

    val uiState: StateFlow<FeedUiState> =
        repository.newsStream()
            .map<List<Article>, FeedUiState> { articles -> FeedUiState.Success(articles) }
            .catch { e -> emit(FeedUiState.Error(e.message ?: "Failed to load")) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),   // see below
                initialValue = FeedUiState.Loading                  // value before the first emission
            )
}
```

Three pieces to understand precisely:

- **`map { Success(it) }`** turns each emission of the cold data flow into a `UiState`. The repository emits `List<Article>`; the `ViewModel` shapes it into the *screen's* state. `catch { emit(Error(...)) }` converts an upstream failure into the `Error` state rather than crashing the collector.
- **`stateIn`** converts the cold `Flow` (which would re-run its upstream for every collector) into a hot `StateFlow` (one shared upstream, a current value). `initialValue = Loading` gives the UI something to render immediately, before the first real emission — which is why `UiState` starts at `Loading` (lecture 1, §7).
- **`SharingStarted.WhileSubscribed(5_000)`** is the sharing policy: keep the upstream alive while there's a collector, and for **5 seconds** after the last one goes away. The 5-second grace is precisely so a configuration change (which momentarily drops the collector while the UI recreates) doesn't tear down and re-run the upstream — but a real backgrounding (collector gone for good, via `collectAsStateWithLifecycle`) lets the upstream stop. This is the exact partner of lecture 1, §5: lifecycle-aware collection drops the collector when backgrounded, and `WhileSubscribed(5000)` then stops the upstream shortly after. Together they tie data production to the screen being visible.

When a screen's state combines *multiple* inputs — say the news stream *and* a user's bookmark set — use `combine`:

```kotlin
val uiState: StateFlow<FeedUiState> =
    combine(repository.newsStream(), userData.bookmarks) { articles, bookmarks ->
        FeedUiState.Success(articles.map { it.copy(isBookmarked = it.id in bookmarks) })
    }
    .catch { emit(FeedUiState.Error(it.message ?: "error")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState.Loading)
```

`combine` re-emits whenever *either* input changes, so bookmarking an article (which updates `userData.bookmarks`) re-derives the `UiState` with the new flag — reactively, with no manual refresh. This is the reactive heart of the architecture: the `StateFlow<UiState>` is a *derived* value, recomputed whenever any of its inputs change, and the UI re-renders because it's collecting it.

### A `Result` wrapper in the data layer — the NiA idiom for async outcomes

There's a refinement Now-In-Android uses that's worth adopting: the *data layer* wraps its reactive results in a small sealed `Result` type, so that "loading," "success," and "error" are explicit *in the stream* rather than handled by exceptions and `catch`. The `ViewModel` then maps that `Result` to its `UiState` with a clean `when`:

```kotlin
// A reusable wrapper for an async result, in the data (or a shared) layer.
sealed interface Result<out T> {
    data object Loading : Result<Nothing>
    data class Success<T>(val data: T) : Result<T>
    data class Error(val exception: Throwable) : Result<Nothing>
}

// A Flow extension that turns any data Flow into a Flow<Result<T>>: it prepends
// Loading and converts a thrown exception into Error — so the stream never breaks.
fun <T> Flow<T>.asResult(): Flow<Result<T>> =
    this.map<T, Result<T>> { Result.Success(it) }
        .onStart { emit(Result.Loading) }
        .catch { emit(Result.Error(it)) }
```

Now the `ViewModel` maps `Result` to `UiState` exhaustively — and the two sealed types stay distinct on purpose: `Result` is the *data layer's* outcome, `UiState` is the *screen's* state, and the mapping between them is where UI-specific shaping happens:

```kotlin
val uiState: StateFlow<FeedUiState> =
    repository.newsStream()
        .asResult()                                    // Flow<List<Article>> -> Flow<Result<List<Article>>>
        .map { result ->
            when (result) {
                Result.Loading -> FeedUiState.Loading
                is Result.Success -> FeedUiState.Success(result.data)
                is Result.Error -> FeedUiState.Error(result.exception.message ?: "Failed to load")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState.Loading)
```

Why bother, when `catch { emit(Error) }` already worked? Three reasons: (1) `asResult()` is *reusable* — every feature's `ViewModel` uses the same wrapper instead of re-writing `catch`; (2) `Loading` is now explicit in the stream, so a slow repository emits `Result.Loading` and the UI shows a spinner without special-casing; (3) the `Result` → `UiState` mapping is the seam where you keep the data outcome and the screen state *separate*, so a repository that's shared by two screens can map to two different `UiState`s. For a single simple screen, the plain `map`/`catch` is fine; the moment you have several features, the `asResult()` wrapper pays for itself. NiA uses it pervasively, which is why you'll recognise it when you read the source.

---

## 3. `SavedStateHandle` — surviving process death

Here is the distinction that trips up everyone, and the reason this week has a "survives process death" promise:

- **Configuration change** (rotation, dark-mode toggle, language change): the `ViewModel` **survives**. Its state, its in-flight coroutines, everything continues. You don't need to do anything.
- **Process death** (the system kills your backgrounded app to reclaim memory): the `ViewModel` is **destroyed** along with the whole process. When the user returns, Android recreates the Activity and a *fresh* `ViewModel` — its in-memory state is gone.

So state that must survive *process death* cannot live only in the `ViewModel`'s memory. It goes in **`SavedStateHandle`** — a `Bundle`-backed key-value store the `ViewModel` receives, which the system serializes when it kills your process and restores when it recreates it:

```kotlin
class SearchViewModel(
    private val savedStateHandle: SavedStateHandle,
    repository: NewsRepository
) : ViewModel() {

    // The query survives process death because it lives in SavedStateHandle.
    val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    fun onQueryChange(new: String) {
        savedStateHandle[KEY_QUERY] = new   // written through to the saved Bundle
    }

    // Results are DERIVED from the (saved) query — they don't need saving, they recompute.
    val results: StateFlow<SearchUiState> =
        query.flatMapLatest { q ->
            if (q.isBlank()) flowOf(SearchUiState.Empty)
            else repository.search(q).map { SearchUiState.Results(it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Empty)

    companion object { private const val KEY_QUERY = "query" }
}
```

The discipline — and it's the whole skill — is **what goes in `SavedStateHandle` vs. what you recompute**:

- **In `SavedStateHandle`: small, identity-shaped UI state that the user created and would be annoyed to lose.** The search query. The selected tab. The id of the article being viewed. A scroll position anchor. These are *inputs* — small, serializable, and not recoverable by recomputation (the system can't guess what the user typed).
- **Recomputed (not saved): the large, derived state.** The search *results*, the loaded article list, anything fetched from the repository. These are *outputs* of the saved inputs; on recreation the `ViewModel` re-derives them from the restored query/id. Saving them would bloat the `Bundle` (which has a hard size limit — `TransactionTooLargeException` is real) and they'd be stale anyway.

So: **save the inputs, recompute the outputs.** The query goes in `SavedStateHandle`; the results flow from it. After a process kill, the query restores and the results re-derive from it — the user sees exactly what they had, and you never serialized a megabyte of articles.

Navigation arguments arrive through `SavedStateHandle` too: in a Nav3 app the route's argument (Week 10) is available to the `ViewModel` via `SavedStateHandle`, so `savedStateHandle.get<Int>("itemId")` gives you the id the screen was opened with, surviving process death automatically because it's a saved argument.

`rememberSaveable` (Week 8) is the *Compose-level* sibling of `SavedStateHandle`: it saves a small piece of composable-local state across process death (a dropdown's expanded flag). The two tools, at two layers: `rememberSaveable` for transient UI-only state in the composable, `SavedStateHandle` for the `ViewModel`'s saved inputs. Use `SavedStateHandle` for state the *logic* owns; `rememberSaveable` for state the *view* owns.

---

## 4. The complete picture

Putting the layers, derivation, and saved state together for a search screen:

```text
  User types "kotlin"
        │  onQueryChange("kotlin")   (EVENT UP)
        ▼
  SearchViewModel
    savedStateHandle["query"] = "kotlin"     ← saved (survives process death)
        │
    query: StateFlow<String> (from SavedStateHandle)
        │  flatMapLatest
        ▼
    repository.search("kotlin")              ← data layer
        │  map -> SearchUiState.Results
        │  stateIn(WhileSubscribed)
        ▼
    results: StateFlow<SearchUiState>        ← derived, recomputed (not saved)
        │  collectAsStateWithLifecycle       (STATE DOWN)
        ▼
  SearchScreen renders the results

  --- process death here ---
  System kills process; ViewModel gone; "query"="kotlin" saved in the Bundle.
  User returns: new ViewModel, query restored to "kotlin", results re-derive.
  User sees their search exactly as they left it. Nothing lost.
```

The query — the input the user created — survived in `SavedStateHandle`. The results — the output — were recomputed from it. That's the architecture surviving the worst the OS does.

---

## 5. Testing the architecture — Turbine and a fake repository, no device

The deepest payoff of this architecture is that the logic — the `ViewModel`'s state production — is testable on the JVM with no UI and no device. Two things make it so: the `ViewModel` is plain Kotlin, and the data layer is an *interface* you can fake.

A **fake repository** (a real, simple implementation — not a mock) stands in for the data layer:

```kotlin
class FakeNewsRepository(initial: List<Article> = emptyList()) : NewsRepository {
    private val stream = MutableStateFlow(initial)
    override fun newsStream(): Flow<List<Article>> = stream
    override suspend fun refresh() { /* no-op or controllable */ }
    fun emit(articles: List<Article>) { stream.value = articles }   // drive the test
}
```

A **`ViewModel` test** with `runTest` and Turbine asserts the `StateFlow<UiState>` transitions:

```kotlin
@Test
fun feed_emitsLoadingThenSuccess() = runTest {
    val repo = FakeNewsRepository()
    val vm = FeedViewModel(repo)

    vm.uiState.test {                                   // Turbine
        assertEquals(FeedUiState.Loading, awaitItem())  // initial value before first emission
        repo.emit(listOf(Article(1, "Kotlin 2.0")))
        val state = awaitItem()
        assertTrue(state is FeedUiState.Success)
        assertEquals(1, (state as FeedUiState.Success).items.size)
        cancelAndIgnoreRemainingEvents()
    }
}
```

A **`SavedStateHandle` round-trip test** proves the saved slice survives:

```kotlin
@Test
fun query_survivesAcrossViewModelRecreation() = runTest {
    val saved = SavedStateHandle()
    val repo = FakeNewsRepository()

    // First ViewModel: user types a query.
    SearchViewModel(saved, repo).onQueryChange("kotlin")

    // Simulate process death + recreation: a NEW ViewModel from the SAME (restored) handle.
    val recreated = SearchViewModel(saved, repo)
    assertEquals("kotlin", recreated.query.value)   // the query survived
}
```

The traps these tests teach you to avoid:

- **Use a `TestDispatcher`.** `runTest` provides one; if the `ViewModel` launches on `Dispatchers.IO` directly, inject the dispatcher so the test controls time. Hardcoding a dispatcher makes the test flaky and slow — the testability argument for *injecting* dispatchers (which Hilt formalizes next week).
- **Turbine for `StateFlow`, not `assertEquals` on `.value`.** A `StateFlow`'s `.value` is the *current* value; to assert the *sequence* (`Loading` then `Success`) you need Turbine's `awaitItem()`, which waits for each emission deterministically.
- **Fakes over mocks.** A `FakeNewsRepository` you can `emit` into is clearer and less brittle than a mock with stubbed call expectations. NiA uses fakes throughout; so do we.

No emulator ran. The whole architecture — state production, error handling, saved-state survival — is verified in milliseconds on the JVM, because the layers are decoupled and the dependencies are injected. That testability is not a nice-to-have; it's the *reason* the architecture is shaped this way.

---

## 6. Putting it together — an architecture code-review checklist

Before you call a feature's architecture "done," walk this list. It's the checklist a senior reviewer applies:

- **One source of truth per piece of state.** No two places own the same state; the `ViewModel` owns screen state, the repository owns data.
- **`UiState` is a sealed type.** `Loading | Error | Success`, rendered with an exhaustive `when`; no flat-flags soup.
- **`StateFlow` exposed, `MutableStateFlow` private.** The UI can read state and call methods; it cannot set state.
- **State is derived reactively.** `repository.flow.map/combine.stateIn(WhileSubscribed)`; not imperative one-shot fetches scattered through the UI.
- **Lifecycle-aware collection.** `collectAsStateWithLifecycle()`, paired with `WhileSubscribed(5000)`, so production stops when the screen is gone.
- **The dependency rule holds.** UI → domain → data; no data-layer code imports a `ViewModel`.
- **Saved inputs, recomputed outputs.** The small user-created inputs (query, selection, id) are in `SavedStateHandle`; the large derived data is recomputed, not serialized.
- **Process death survives.** Tested with "Don't keep activities" (or `adb shell am kill`): the user-created state restores.
- **The logic is JVM-tested.** A fake repository, `runTest`, Turbine on the `StateFlow<UiState>`, a `SavedStateHandle` round-trip — green, with no device.
- **The dispatcher is injected.** Background work runs on an injected `CoroutineDispatcher` (default in production, a `TestDispatcher` in tests), not a hardcoded `Dispatchers.IO` — so tests control time and don't flake.
- **`UiState` carries UI-ready data.** The `Success` variant holds data the screen renders directly (formatted, flagged), not raw domain entities the UI then has to massage.
- **Use cases earn their place.** A domain use case exists only where logic is genuinely shared or complex — not one per repository call out of habit.

---

## 7. Recap

Lecture 1 gave you the grammar; this lecture gave it a structure and made it survive the OS. Four habits carry it:

1. **Layer it, and point dependencies one way.** Data (repositories) → exposed as `Flow`s; domain (use cases, sparingly) → reusable logic; UI (ViewModel + composables) → owns state. UI → domain → data, never the reverse — that's what keeps it testable and swappable.
2. **Derive state reactively.** `repository.flow.map/combine.stateIn(viewModelScope, WhileSubscribed(5000), Loading)`. The `StateFlow<UiState>` is a derived value, recomputed when its inputs change, collected lifecycle-aware so production tracks visibility.
3. **Save the inputs, recompute the outputs.** `SavedStateHandle` holds the small user-created slice that must survive process death (query, selection, id); the large derived data re-derives from it. That's the "survives process death" promise.
4. **Test without a device.** Plain-Kotlin `ViewModel` + faked repository + `runTest` + Turbine = the whole architecture verified on the JVM in milliseconds. The testability is the point, not a bonus.

And under all four: **know exactly which state lives where.** The four homes for state, from shortest-lived to most durable:

- **Compose `remember`** — transient, view-local, gone on rotation (a dropdown's expanded flag). Cheapest; doesn't survive anything.
- **`rememberSaveable`** — view-local state that must survive process death (a small UI toggle). The Compose-level sibling of `SavedStateHandle`.
- **`ViewModel` field / `StateFlow`** — durable screen state that survives rotation (the derived `UiState`, in-flight loads). Lost on process death.
- **`SavedStateHandle`** — the small user-created inputs that must survive process death (query, selection, nav id). The repository, behind it all, owns the data itself.

Draw those lines right — the right home for each piece — and the app survives rotation, process death, and the next engineer reading it.

The exercises put these to work — a sealed `UiState`, a `ViewModel` with a tested `StateFlow`, and a `SavedStateHandle` round-trip — and the mini-project assembles all of it into News Feed: a ViewModel-driven `StateFlow<UiState>`, the NiA layers, process-death survival, and tests with no device. This is the last week of Phase 2; you now have a navigated, themed, *architected* app. Phase 3 makes it production-grade — starting by letting Hilt wire the graph you just built by hand. Go draw the layers, own the state, and make it survive the process dying.
