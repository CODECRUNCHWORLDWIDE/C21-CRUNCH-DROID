# Exercise 1 — `UiState` as a sealed type

**Goal.** Model a screen's UI state as a sealed `Loading | Error | Success` type, render it with an exhaustive `when`, and convert a flat-flags screen (`isLoading` + `error` + `data`, where many combinations are nonsense) to it. By the end, contradictory states are *unrepresentable* — you cannot be loading and errored and showing data at once, because the type forbids it. This is the entire premise of the week's state shape distilled to one conversion.

**Estimated time.** 40 minutes.

**Prerequisites.** Android Studio Ladybug+, a Compose project. No emulator needed for the modelling; a preview confirms each state renders. You can do this in a `@Preview` or a tiny app.

---

## Step 1 — The flat-flags screen (the *before*)

Type out this feed screen the naive way — a data class with three independent fields. It compiles and "works," and it can represent states that make no sense.

```kotlin
// THE ANTI-PATTERN — flat flags. Count the impossible combinations.
data class FeedState(
    val isLoading: Boolean = false,
    val articles: List<Article> = emptyList(),
    val error: String? = null
)

@Composable
fun FeedScreenBefore(state: FeedState) {
    // Which flag wins? Every screen has to decide, and they disagree.
    if (state.isLoading) {
        CircularProgressIndicator()
    }
    if (state.error != null) {
        Text("Error: ${state.error}")
    }
    if (state.articles.isNotEmpty()) {
        LazyColumn { items(state.articles) { Text(it.title) } }
    }
    // What renders for FeedState(isLoading = true, error = "boom", articles = [a, b])?
    // A spinner AND an error AND a list, stacked. That state shouldn't exist.
}
```

Construct the nonsense state and render it to *see* the problem:

```kotlin
@Preview
@Composable
fun PreviewContradiction() {
    FeedScreenBefore(FeedState(isLoading = true, error = "boom", articles = listOf(Article(1, "A"))))
    // The type let you build "loading AND errored AND has data" — and the UI shows all three.
}
```

That preview is the bug: the *type* permitted a state that can't really happen, and the UI dutifully rendered the contradiction.

## Step 2 — The sealed `UiState` (the *after*)

Model the state as a sealed hierarchy. A value is *exactly one* variant — the contradictions can't be constructed.

```kotlin
sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Error(val message: String) : FeedUiState
    data class Success(val articles: List<Article>) : FeedUiState
}
```

Now `FeedUiState` is `Loading`, *or* `Error(message)`, *or* `Success(articles)` — never two at once. And the data only exists where it makes sense: `articles` lives in `Success`, `message` lives in `Error`. There's no nullable `articles` to defend against, because in the non-`Success` states it simply isn't there.

## Step 3 — Render with an exhaustive `when`

Rewrite the screen as a single `when` the compiler checks for exhaustiveness.

```kotlin
@Composable
fun FeedScreenAfter(state: FeedUiState) {
    when (state) {
        FeedUiState.Loading -> CircularProgressIndicator()
        is FeedUiState.Error -> Text("Error: ${state.message}")        // smart-cast: state.message available
        is FeedUiState.Success -> LazyColumn {                          // smart-cast: state.articles available
            items(state.articles) { Text(it.title) }
        }
    }
    // No `else`. Exactly one branch runs. Add a 4th state and this won't compile until you handle it.
}
```

Notice three wins:

- **Exactly one branch runs.** There's no stacking of spinner + error + list, because the state is one thing.
- **Smart casts give you typed data.** Inside `is FeedUiState.Success`, `state.articles` is a non-null `List<Article>`. Inside `is FeedUiState.Error`, `state.message` is a non-null `String`. No null checks.
- **The `when` is exhaustive.** Drop the `else`; the compiler is satisfied because a sealed type has a known set of variants. Add `data object Empty : FeedUiState` and the `when` *fails to compile* until you handle it — the compiler hands you the list of screens to update.

## Step 4 — Prove the contradiction can't be built

Try to construct the Step-1 nonsense state in the new model. You can't:

```kotlin
FeedUiState(isLoading = true, error = "boom", articles = listOf())   // COMPILE ERROR: no such constructor
// The only things you can build are Loading, Error("…"), or Success([…]) — one at a time.
```

There is no constructor that takes all three, because the states are *alternatives*, not *fields*. The contradiction isn't handled at runtime; it's unrepresentable at compile time.

## Step 5 — Preview every state

```kotlin
@Preview @Composable fun PreviewLoading() = FeedScreenAfter(FeedUiState.Loading)
@Preview @Composable fun PreviewError()   = FeedScreenAfter(FeedUiState.Error("Network down"))
@Preview @Composable fun PreviewSuccess() = FeedScreenAfter(FeedUiState.Success(listOf(Article(1, "Kotlin 2.0"))))
```

Three clean previews, one per state. No preview can show a contradiction, because none can be built.

---

## Acceptance criteria

- [ ] A `sealed interface FeedUiState` with `Loading` (`data object`), `Error(message)`, and `Success(articles)` (`data class`es).
- [ ] `FeedScreenAfter` renders it with a single **exhaustive `when`** and **no `else`** branch.
- [ ] Inside `Success` and `Error`, the data is read via smart cast (`state.articles`, `state.message`) with no null checks.
- [ ] You confirmed the Step-1 contradiction state is a **compile error** in the new model.
- [ ] (Demonstration) Adding a 4th state makes the `when` fail to compile until handled — try it, then revert.
- [ ] Build with **0 warnings, 0 errors**.

## What you just proved

You proved the week's state-shape claim: a sealed `UiState` makes illegal states unrepresentable. The flat-flags model let you build (and render) "loading and errored and showing data," and every screen had to defensively decide which flag wins — the source of inconsistent-UI bugs. The sealed model permits exactly one state at a time, renders it with an exhaustive `when` the compiler enforces, and hands you typed data via smart cast. This is the same "make illegal states unrepresentable" move you met with typed routes in Week 10 — there a route was a type; here the state is a type. Every `ViewModel` you build this week exposes a sealed `UiState` shaped exactly like this.

---

## Hints (read only if stuck > 10 min)

- **The `when` demands an `else`.** Then `FeedUiState` isn't sealed, or you're `when`-ing on a non-sealed supertype. A `sealed interface` (or `sealed class`) with all variants in the same module gives the compiler the closed set it needs to skip `else`.
- **`Loading` as `object` vs `data object`.** Use `data object` — it gives a sensible `toString`/`equals` and reads well in tests (`assertEquals(FeedUiState.Loading, state)`).
- **Smart cast "fails" on `state.message`.** Make sure you're inside `is FeedUiState.Error { … }` — the smart cast only applies within the branch that narrowed the type. (If `state` were a `var`, the cast wouldn't hold; keep it a `val` parameter.)
- **Where does `isRefreshing` go?** If a screen genuinely has orthogonal sub-state (loaded *and* a pull-to-refresh in progress), put it *inside* the variant: `Success(val articles: …, val isRefreshing: Boolean)`. The rule isn't "no booleans" — it's "the top-level state is sealed so it can't contradict itself."
- **Which state is the initial one?** `Loading`. A `StateFlow<UiState>` always has a value (next week's lecture); it starts at `Loading` so there's always something to render.
