# Mini-Project — News Feed: a ViewModel-driven, Now-In-Android-shaped app

This week — the last of Phase 2 — the app gets its nervous system. You will build **News Feed**, a two-screen app (a feed list and an article detail) with a real architecture: a Jetpack `ViewModel` exposing a single `StateFlow<UiState>`, unidirectional data flow, a data/domain/UI layer split modelled on Now-In-Android, full process-death survival via `SavedStateHandle`, and a tested round-trip — all verified on the JVM with no device. By the end it is a system you could hand to a teammate, not a demo.

This is the *architecture* project. The screens are simple (a list, a detail) and the data is in-memory, on purpose — the point is the *shape*: who owns state, which way it flows, where each layer's code lives, and what survives a process kill. If you built Catalog Companion / Pocket Reader (Weeks 10–11), give *that* app this architecture and keep your navigation and theme; either way, the deliverable is a correctly-architected, process-death-surviving, JVM-tested app.

---

## Where you're starting from

You have, from this week's exercises:

- A sealed `UiState` (`Loading | Error | Success`) and exhaustive `when` rendering (exercise 1).
- A `ViewModel` deriving `StateFlow<UiState>` from a fake repository, tested with Turbine (exercise 2).
- A `SavedStateHandle` round-trip for a saved input with derived output (exercise 3).

And from earlier weeks: Nav3 navigation (Week 10) — the article detail is reached by a typed route, and its `ViewModel` is scoped to the back-stack entry — and Compose fluency. News Feed assembles all of it.

## What you're building toward

By the end you have:

- A **data layer**: a `NewsRepository` interface exposing `Flow`s, with an in-memory implementation (Room slots in behind it in Week 14 — that's why it's an interface).
- An optional **domain** touch: one use case *only if* it earns its keep (combining articles with bookmarks).
- A **UI layer**: a `FeedViewModel` and a `DetailViewModel`, each owning a `StateFlow<UiState>`, with the composables rendering state and reporting events.
- **Unidirectional data flow** throughout: state down via `collectAsStateWithLifecycle`, events up via `ViewModel` methods, `StateFlow` exposed (never `MutableStateFlow`).
- **Process-death survival**: the feed's scroll/filter and the detail's article id in `SavedStateHandle`; derived state recomputed.
- **JVM tests**: `ViewModel` `StateFlow` transitions (Turbine + fake repo) and a `SavedStateHandle` round-trip — green, no emulator.

---

## Milestone 1 — The data layer (≈ 1.5 h)

Define the repository interface and an in-memory implementation. This is the boundary the rest of the app depends on.

```kotlin
data class Article(val id: Int, val title: String, val summary: String, val body: String, val isBookmarked: Boolean = false)

interface NewsRepository {
    fun newsStream(): Flow<List<Article>>       // reactive: emits when data changes
    fun article(id: Int): Flow<Article?>        // a single article, reactive
    suspend fun toggleBookmark(id: Int)         // an action
}

class InMemoryNewsRepository : NewsRepository {
    private val articles = MutableStateFlow(SeedArticles.all)
    override fun newsStream(): Flow<List<Article>> = articles
    override fun article(id: Int): Flow<Article?> = articles.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun toggleBookmark(id: Int) {
        articles.update { list -> list.map { if (it.id == id) it.copy(isBookmarked = !it.isBookmarked) else it } }
    }
}
```

Decisions to defend in review:

- **Why an interface, not a concrete class the `ViewModel` news?** So the data source behind it can change — in-memory now, Room in Week 14 — without touching the UI. The repository *interface* is the stable contract; this is the dependency rule (UI → data) doing its job.
- **Why expose `Flow`, not `suspend fun list(): List<Article>`?** Reactivity. `newsStream()` emits whenever the data changes (a bookmark toggle), so the `ViewModel`'s derived `StateFlow<UiState>` re-derives and the UI re-renders — no manual refresh.

## Milestone 2 — The feed ViewModel and its `StateFlow<UiState>` (≈ 2 h)

The `FeedViewModel` derives a `StateFlow<FeedUiState>` from the repository stream.

```kotlin
sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Error(val message: String) : FeedUiState
    data class Success(val articles: List<Article>) : FeedUiState
}

class FeedViewModel(private val repository: NewsRepository) : ViewModel() {

    val uiState: StateFlow<FeedUiState> =
        repository.newsStream()
            .map<List<Article>, FeedUiState> { FeedUiState.Success(it) }
            .catch { emit(FeedUiState.Error(it.message ?: "Failed to load")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState.Loading)

    fun onToggleBookmark(id: Int) {
        viewModelScope.launch { repository.toggleBookmark(id) }   // event up; state re-derives reactively
    }
}
```

And the screen renders it with an exhaustive `when`, reporting events:

```kotlin
@Composable
fun FeedScreen(viewModel: FeedViewModel, onOpen: (Int) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (state) {
        FeedUiState.Loading -> LoadingSpinner()
        is FeedUiState.Error -> ErrorView((state as FeedUiState.Error).message)
        is FeedUiState.Success -> FeedList(
            articles = (state as FeedUiState.Success).articles,
            onOpen = onOpen,
            onBookmark = viewModel::onToggleBookmark    // event up
        )
    }
}
```

Decisions to defend: **why `WhileSubscribed(5000)` + `collectAsStateWithLifecycle`?** Together they tie data production to the screen being visible — the upstream stops shortly after the screen is backgrounded, and resumes on return, surviving the brief collector-drop of a configuration change. **Why expose `StateFlow` not `MutableStateFlow`?** So the UI can read state and call `onToggleBookmark`, but cannot set state — one source of truth.

Wire the `ViewModel` into the Nav3 entry from Week 10 so it's scoped to the back-stack entry:

```kotlin
entry<FeedRoute> {
    val vm: FeedViewModel = viewModel { FeedViewModel(repository) }   // scoped to this entry
    FeedScreen(viewModel = vm, onOpen = { id -> backStack.add(DetailRoute(id)) })
}
```

The `viewModel { … }` lambda runs once per entry; the same `FeedViewModel` is returned across recompositions and rotation, and cleared when the `FeedRoute` entry is popped. This is the Week-10 seam (ViewModel-scoped-to-an-entry) carrying the Week-12 architecture — the two weeks meeting exactly where they should.

## Milestone 3 — The detail ViewModel, scoped to the nav entry (≈ 1.5 h)

The detail screen is reached by a typed Nav3 route (Week 10) carrying the article id; its `ViewModel` is scoped to the back-stack entry and seeded by the id from `SavedStateHandle`.

```kotlin
sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Success(val article: Article) : DetailUiState
}

class DetailViewModel(
    savedStateHandle: SavedStateHandle,        // the nav argument arrives here
    repository: NewsRepository
) : ViewModel() {
    private val articleId: Int = savedStateHandle["itemId"] ?: error("missing itemId")

    val uiState: StateFlow<DetailUiState> =
        repository.article(articleId)
            .map<Article?, DetailUiState> { if (it == null) DetailUiState.NotFound else DetailUiState.Success(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)
}
```

Decision to defend: **why does the id come from `SavedStateHandle`?** Because in a Nav3 app the route argument is delivered through `SavedStateHandle`, which means it *survives process death automatically* — after a kill, the detail `ViewModel` is recreated with the same id and re-derives the article. The id is the saved input; the article is the recomputed output.

## Milestone 4 — The layer split, drawn cleanly (≈ 1 h)

Organize the code into the Now-In-Android layers, with dependencies pointing one way only.

```text
ui/        FeedViewModel, DetailViewModel, FeedScreen, DetailScreen   (depends on data)
domain/    GetBookmarkableNewsUseCase  (OPTIONAL — only if reused/complex)
data/      NewsRepository (interface), InMemoryNewsRepository, SeedArticles
```

The dependency rule: `ui` imports `data` (and `domain`); `data` imports *neither*. Confirm no data-layer file imports a `ViewModel`. Add the domain use case *only* if you have a genuine reuse (e.g. both the feed and a "bookmarks" screen need articles-with-bookmark-flags); otherwise the `ViewModel` calling the repository directly is correct and a use case would be ceremony.

## Milestone 5 — Process-death survival (≈ 1 h)

Make the feed's UI-input state — a filter or the selected category — survive a process kill via `SavedStateHandle`, and confirm the detail id survives (Milestone 3 already does, via the nav argument).

```kotlin
class FeedViewModel(savedStateHandle: SavedStateHandle, repository: NewsRepository) : ViewModel() {
    val filter = savedStateHandle.getStateFlow("filter", Filter.All)   // saved input
    fun onFilterChange(f: Filter) { savedStateHandle["filter"] = f }

    val uiState = combine(repository.newsStream(), filter) { articles, f ->
        FeedUiState.Success(articles.filter { f.matches(it) })          // derived output
    }.catch { emit(FeedUiState.Error(it.message ?: "error")) }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUiState.Loading)
}
```

The filter survives a kill (saved); the filtered list recomputes from it (derived). Save the input, recompute the output.

## Milestone 6 — Tests and the process-death proof (≈ 1 h)

Test the architecture on the JVM, and prove process-death survival on-device.

- **`ViewModel` tests** (exercise 2 template): `FeedViewModel` emits `Loading → Success`; toggling a bookmark re-derives the state; an upstream failure becomes `Error`.
- **`SavedStateHandle` round-trip** (exercise 3 template): the filter survives a `ViewModel` recreation; `DetailViewModel` re-derives from a restored id.
- **On-device process-death proof**: enable "Don't keep activities", set a filter and open an article, background, return — filter restored, article re-shown. Record it.

---

## Acceptance criteria

- [ ] A `NewsRepository` **interface** with an in-memory implementation; the `ViewModel`s depend on the interface.
- [ ] `FeedViewModel` and `DetailViewModel` each own a **`StateFlow<UiState>`** (sealed `UiState`), exposed read-only; events are `ViewModel` methods.
- [ ] State is **derived reactively** (`map`/`combine`/`stateIn` with `WhileSubscribed(5000)`), collected with **`collectAsStateWithLifecycle`**.
- [ ] The **layer split** is clean (ui → domain → data, never reverse); no data-layer file imports a `ViewModel`.
- [ ] **`SavedStateHandle`** holds the saved inputs (filter, and the detail id via the nav argument); derived state is recomputed, not stored.
- [ ] **JVM tests** pass: `ViewModel` `StateFlow` transitions (Turbine + fake repo) and a `SavedStateHandle` round-trip.
- [ ] **Process-death survives**, proven with "Don't keep activities" (filter + open article restored on return).
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **A bookmarks screen sharing a use case.** Add a second screen showing only bookmarked articles, and *now* extract a `GetBookmarkedNewsUseCase` — demonstrating when the domain layer earns its place (genuine reuse).
- **Optimistic bookmark toggle.** Make `onToggleBookmark` update the `StateFlow` optimistically before the repository confirms, rolling back on failure — a taste of the optimistic-UI patterns Phase 3's sync week needs.
- **A `Result` wrapper.** Wrap the repository's async outcomes in a sealed `Result<T> { Success; Error; Loading }` and have the `ViewModel` map it to `UiState` — the NiA pattern for representing async results in the data layer.
- **Inject the dispatcher.** Pass a `CoroutineDispatcher` into the `ViewModel` (defaulting to `Dispatchers.Default`) and a `TestDispatcher` in tests — the testability pattern Hilt formalizes next week.

## What this milestone earns you

You can now draw the line between Compose state and `ViewModel` state, own screen state in a `ViewModel` as a sealed `StateFlow<UiState>`, lay code out in the Now-In-Android layers, and survive process death — the literal "skills earned" lines for the week. More than that: you wired the data graph *by hand* (a repository, a factory), so you understand exactly what the architecture *is* before Week 13 shows you how Hilt assembles it for you. This is the end of Phase 2: your app is navigated (Week 10), themed (Week 11), and now architected — a real system. Phase 3 makes it production-grade. The chassis you built this week is what every production week bolts onto. Well done — you've earned the foundation.
