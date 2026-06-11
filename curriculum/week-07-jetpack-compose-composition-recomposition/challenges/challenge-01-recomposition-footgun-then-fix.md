# Challenge 1 — Plant a recomposition footgun, then fix it (with the report)

**Time.** 60–120 minutes.
**Deliverable.** A short report (`PERF.md`) with two Compiler-report excerpts (before/after), two Layout Inspector recomposition-count screenshots, and the refactored code, committed to your Week 07 repo.

## The premise

Every Android engineer has, at least once, shipped a list that recomposes every visible row on every unrelated state change. It works perfectly in the demo with five rows. Then the list has a thousand rows, the user scrolls, and frames drop because each row re-runs its body needlessly. The skill this challenge builds is not "know the footgun exists" — it's **plant it, see the report mark it not-skippable, watch the recomposition counts climb, fix it, and prove the fix with the report turning green and the counts going still.** A fix you can't quantify is a guess.

You will build a feed screen the *wrong* way — every stability footgun from lecture 2 at once — measure it, then rewrite it the right way and measure again. The grading is the gap between the two and your explanation of it.

## What to build

A feed screen with a search field and a like button, over a list of articles. The model has enough surface to make recomposition meaningful.

### Step 1 — Plant the footguns (the WRONG version)

Write it with every footgun from lecture 2 deliberately present:

```kotlin
// FOOTGUN 2: a `var` makes ArticleUi unstable.
data class ArticleUi(
    val id: String,
    var title: String,             // <- var
    val body: String,
    val likes: Int
)

@Composable
fun FeedScreenBad(viewModelState: FeedState) {
    var query by remember { mutableStateOf("") }
    Column {
        TextField(value = query, onValueChange = { query = it })
        // FOOTGUN 1: List<ArticleUi> parameter is unstable.
        // FOOTGUN: no key on the list, so a reorder discards remembered state.
        FeedListBad(articles = viewModelState.articles, onLike = { id ->
            // FOOTGUN 3: this lambda captures viewModelState (unstable holder)
            viewModelState.like(id)
        })
    }
}

@Composable
fun FeedListBad(articles: List<ArticleUi>, onLike: (String) -> Unit) {
    LazyColumn {
        items(articles) { article ->          // no key = matched by position
            ArticleRowBad(article, onLike)
        }
    }
}

@Composable
fun ArticleRowBad(article: ArticleUi, onLike: (String) -> Unit) {
    Row {
        Text(article.title)
        Spacer(Modifier.weight(1f))
        Text("${article.likes}")
        IconButton(onClick = { onLike(article.id) }) { Icon(Icons.Default.Favorite, null) }
    }
}
```

### Step 2 — Read the Compiler report (the "before" evidence)

Turn on the report in `app/build.gradle.kts`:

```kotlin
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}
```

Build (`./gradlew :app:assembleRelease`) and open `app/build/compose_compiler/`. In `*-composables.txt`, confirm and copy the lines showing `FeedListBad` and `ArticleRowBad` are **not skippable**, with `unstable articles: List<ArticleUi>`. In `*-classes.txt`, copy the line showing `ArticleUi` is `unstable` because of `var title`. These excerpts are your "before."

### Step 3 — Watch the recomposition counts (the live "before")

Run the app. Open `View ▸ Tool Windows ▸ Layout Inspector`, enable "Show Recomposition Counts." Type in the search field one character at a time. Watch **every visible `ArticleRowBad` recomposition count climb on every keystroke** — even though the articles didn't change, only the unrelated `query` did. Screenshot it. That is the bug: an unrelated state change recomposes every row because the rows aren't skippable.

### Step 4 — Fix every footgun (the RIGHT version)

```kotlin
import kotlinx.collections.immutable.ImmutableList

// FIX 2: val, not var. Immutable updates via copy().
data class ArticleUi(
    val id: String,
    val title: String,
    val body: String,
    val likes: Int
)

@Composable
fun FeedScreenGood(
    articles: ImmutableList<ArticleUi>,        // FIX 1: ImmutableList = stable
    onLike: (String) -> Unit                   // FIX 3: a stable top-level lambda param
) {
    var query by remember { mutableStateOf("") }
    Column {
        TextField(value = query, onValueChange = { query = it })
        FeedListGood(articles = articles, onLike = onLike)
    }
}

@Composable
fun FeedListGood(articles: ImmutableList<ArticleUi>, onLike: (String) -> Unit) {
    LazyColumn {
        items(articles, key = { it.id }) { article ->   // FIX: keyed by stable id
            ArticleRow(article, onLike)
        }
    }
}

@Composable
fun ArticleRow(article: ArticleUi, onLike: (String) -> Unit) {
    Row {
        Text(article.title)
        Spacer(Modifier.weight(1f))
        Text("${article.likes}")
        IconButton(onClick = { onLike(article.id) }) { Icon(Icons.Default.Favorite, null) }
    }
}
```

### Step 5 — Re-read the report and re-watch the counts (the "after")

Rebuild. In `*-composables.txt`, confirm `FeedListGood`, `ArticleRow` are now `restartable skippable` with `stable articles: ImmutableList<ArticleUi>`. In `*-classes.txt`, `ArticleUi` is now `stable`. Copy those lines — your "after." Run again, type in the search field, and confirm the Layout Inspector shows **the article rows' recomposition counts do NOT climb** when you change `query` — only the `TextField` recomposes. Screenshot it.

### Step 6 (optional, stretch) — defer an animating value

Add a "new" badge that pulses on the most recent article. Implement it the wrong way (read the pulse value in composition, recomposing the row every frame), confirm the row's count climbs at ~60/s in the inspector, then move the read into `drawBehind { }` (draw phase) and confirm the count goes still while the pulse keeps animating. This connects the stability fix (Steps 1–5) to the phase-deferral fix (exercise 3): two different mechanisms, both eliminating needless recomposition.

## Acceptance criteria

- [ ] `FeedScreenBad` exhibits all four footguns; the Compiler report marks the list composables **not skippable** and `ArticleUi` **unstable**.
- [ ] `FeedScreenGood` is fully **skippable**; the report marks `ArticleUi` **stable**.
- [ ] Both versions render the same UI and like-toggling works in both (a faster wrong UI is worthless — verify behaviour parity).
- [ ] `PERF.md` records: the before/after `composables.txt` and `classes.txt` excerpts, the speedup story (rows recomposed per keystroke: before = all visible rows, after = zero), and the machine/emulator you measured on.
- [ ] One Layout Inspector screenshot showing rows recomposing on an unrelated keystroke (before), one showing them NOT recomposing (after).
- [ ] A 3–5 sentence explanation of **why** each fix works (in your own words): immutable `val` → stable class; `ImmutableList` → stable parameter; keyed list → identity-based remembering; (stretch) deferred read → draw-only invalidation.
- [ ] Build with **0 warnings**.

## What "great" looks like

A weak submission says "I made the list skippable." A great submission says:

> On the Pixel 8 API 35 emulator, the original `FeedScreenBad` recomposed all 11 visible `ArticleRowBad`s on every keystroke in the search field, because `ArticleUi`'s `var title` made the class unstable, the `List<ArticleUi>` parameter was unstable, and the unkeyed list matched rows by position. The Compiler report confirmed `FeedListBad` as `restartable` but not `skippable`, naming `unstable articles: List<ArticleUi>`. After switching to a `val`-only `ArticleUi`, an `ImmutableList` parameter, and a `key = { it.id }` list, the report shows all three composables `restartable skippable` and `ArticleUi` `stable`; the Layout Inspector confirms zero article-row recompositions when only `query` changes — only the `TextField` recomposes. The stretch pulse badge recomposed the newest row ~60×/s when its value was read in composition; moving the read into `drawBehind` dropped that to zero recompositions while the pulse still animated, because reading state in the draw phase invalidates only draw, not composition.

Quantified, explained, and honest about which mechanism fixed which problem. That's the senior-engineer answer.

## Where this reappears

The "read the report, watch the counts, prove the fix" workflow is exactly what Phase III's performance week (macrobenchmark, Baseline Profiles, Time Profiler) builds on. The unnecessary-recomposition footgun you fixed here is the same shape as the jank you'll diagnose then — just with a system trace instead of a recomposition count. And the immutable-UI-state discipline you installed is the foundation of Week 12's `StateFlow<UiState>` architecture.
