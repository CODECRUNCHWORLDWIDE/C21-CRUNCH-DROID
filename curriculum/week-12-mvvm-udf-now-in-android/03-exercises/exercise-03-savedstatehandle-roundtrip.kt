// Exercise 3 — A SavedStateHandle round-trip
//
// Goal: Put a search query in SavedStateHandle, expose it as a StateFlow, derive
//       results from it, and TEST the round-trip that proves the query survives
//       process death while the results recompute. You learn the week's central
//       distinction by hand: SAVE THE INPUTS (the query), RECOMPUTE THE OUTPUTS
//       (the results).
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// This is a JVM unit-test suite. SavedStateHandle is plain Kotlin (it does NOT
// need an Activity), so we simulate process death by creating a NEW ViewModel
// from the SAME SavedStateHandle — exactly what the system does on recreation.
// runTest + Turbine drive and assert; no emulator.
//
//   testImplementation: kotlinx-coroutines-test, app.cash.turbine:turbine,
//   androidx.lifecycle:lifecycle-viewmodel-savedstate, junit.
//
//   1. Add this file to src/test/java.
//   2. Run with `./gradlew test`.
//   3. The key assertion: the query survives a ViewModel recreation; the results
//      re-derive from it.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] The query lives in SavedStateHandle and survives ViewModel recreation.
//   [ ] Results are DERIVED from the query (flatMapLatest), not stored.
//   [ ] You can explain why you save the query but not the results.
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package dev.crunch.arch.exercise3

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// ----------------------------------------------------------------------------
// Domain + state
// ----------------------------------------------------------------------------

data class Article(val id: Int, val title: String)

sealed interface SearchUiState {
    data object Empty : SearchUiState                              // no query yet
    data class Results(val articles: List<Article>) : SearchUiState
}

interface NewsRepository {
    fun search(query: String): Flow<List<Article>>
}

/** A fake that returns articles whose title contains the query, case-insensitively. */
class FakeNewsRepository(private val all: List<Article>) : NewsRepository {
    override fun search(query: String): Flow<List<Article>> =
        flowOf(all.filter { it.title.contains(query, ignoreCase = true) })
}

// ----------------------------------------------------------------------------
// The ViewModel. The QUERY is saved (survives process death); the RESULTS are
// derived from it (recomputed on recreation, never serialized).
// ----------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val savedStateHandle: SavedStateHandle,
    repository: NewsRepository
) : ViewModel() {

    // SAVED INPUT: getStateFlow reads the saved value (or the default) and emits
    // on every write. This is the slice that must outlive a process kill.
    val query = savedStateHandle.getStateFlow(KEY_QUERY, "")

    fun onQueryChange(new: String) {
        savedStateHandle[KEY_QUERY] = new   // written through to the saved Bundle
    }

    // DERIVED OUTPUT: recomputed from the (saved) query. Not stored — on
    // recreation it re-derives from the restored query.
    val results = query
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(SearchUiState.Empty)
            else repository.search(q).map<List<Article>, SearchUiState> { SearchUiState.Results(it) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchUiState.Empty)

    companion object { const val KEY_QUERY = "query" }
}

// ----------------------------------------------------------------------------
// The tests
// ----------------------------------------------------------------------------

class SavedStateRoundTripTests {

    private val seed = listOf(
        Article(1, "Kotlin 2.0 and K2"),
        Article(2, "Jetpack Compose state"),
        Article(3, "Kotlin coroutines deep dive")
    )

    @Test
    fun query_survivesViewModelRecreation() = runTest {
        val saved = SavedStateHandle()
        val repo = FakeNewsRepository(seed)

        // First ViewModel: the user types a query.
        SearchViewModel(saved, repo).onQueryChange("kotlin")

        // Simulate process death + recreation: a NEW ViewModel from the SAME handle.
        // The system restores the handle's contents; recreating the VM reads them.
        val recreated = SearchViewModel(saved, repo)
        assertEquals("kotlin", recreated.query.value)   // the input survived
    }

    @Test
    fun results_areDerivedFromQuery_andRecomputeOnRecreation() = runTest {
        val saved = SavedStateHandle()
        val repo = FakeNewsRepository(seed)

        // User searches "kotlin" on the first ViewModel.
        SearchViewModel(saved, repo).onQueryChange("kotlin")

        // After "process death", a fresh ViewModel re-derives results from the saved query.
        val recreated = SearchViewModel(saved, repo)
        recreated.results.test {
            val state = awaitItem()
            assertTrue("expected Results but was $state", state is SearchUiState.Results)
            // Two seed titles contain "kotlin": articles 1 and 3.
            assertEquals(setOf(1, 3), (state as SearchUiState.Results).articles.map { it.id }.toSet())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun blankQuery_isEmptyState() = runTest {
        val saved = SavedStateHandle()
        val vm = SearchViewModel(saved, FakeNewsRepository(seed))
        vm.results.test {
            assertEquals(SearchUiState.Empty, awaitItem())   // no query -> Empty, not Results([])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun changingQuery_reDerivesResults() = runTest {
        val saved = SavedStateHandle()
        val vm = SearchViewModel(saved, FakeNewsRepository(seed))
        vm.results.test {
            assertEquals(SearchUiState.Empty, awaitItem())
            vm.onQueryChange("compose")
            val state = awaitItem()
            assertEquals(listOf(2), (state as SearchUiState.Results).articles.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ----------------------------------------------------------------------------
// WHY save the query but not the results (write it before reading):
//
//   The query is a small, user-CREATED input that the system can't regenerate —
//   if it's lost on a process kill, the user has to retype it, which is exactly
//   the bug we're preventing. The results are a large, DERIVED output of the
//   query; on recreation the ViewModel recomputes them from the restored query,
//   so saving them would (a) bloat the Bundle toward its hard size limit
//   (TransactionTooLargeException) and (b) risk serving stale data. Save the
//   inputs, recompute the outputs.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - SavedStateHandle in a JVM test: just `SavedStateHandle()`. To simulate
//   process death, create a SECOND ViewModel from the SAME handle instance — the
//   system does exactly this (restores the Bundle, recreates the VM). If you make
//   a fresh handle, you've simulated a fresh install, not a recreation.
//
// - getStateFlow(key, default) gives you a StateFlow that reflects the saved value
//   and updates whenever you write savedStateHandle[key] = ... . That's the clean
//   way to expose saved state reactively.
//
// - flatMapLatest cancels the previous search when the query changes, so a fast
//   typist doesn't get stale results from an earlier query landing late. It's the
//   right operator for "latest input wins" (Week 5).
//
// - @OptIn(ExperimentalCoroutinesApi::class) is needed for flatMapLatest. It's an
//   opt-in, not a warning to suppress — flatMapLatest is stable in practice.
//
// - SharingStarted.Eagerly in the test so results start deriving without a live
//   collector; in production use WhileSubscribed(5000) so derivation stops shortly
//   after the screen is gone (lecture 2).
//
// ----------------------------------------------------------------------------
