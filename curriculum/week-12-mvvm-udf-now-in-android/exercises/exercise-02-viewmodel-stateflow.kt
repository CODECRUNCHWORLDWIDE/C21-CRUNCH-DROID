// Exercise 2 — A ViewModel with a tested StateFlow<UiState>
//
// Goal: Build a ViewModel that derives a StateFlow<UiState> from a repository
//       Flow via map/stateIn, and TEST the Loading -> Success and error
//       transitions with Turbine and runTest — on the JVM, no emulator. You
//       prove that the architecture's whole payoff is testability: the state
//       production is plain Kotlin you can drive with a fake and assert on.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// This is a JVM unit-test suite using kotlinx-coroutines-test (runTest) and
// Turbine (.test { }). Drop it into src/test/java with these testImplementation
// deps: kotlinx-coroutines-test, app.cash.turbine:turbine, junit. No Android
// instrumentation — a ViewModel is plain Kotlin, and the repository is an
// interface we fake.
//
//   1. Add this file to src/test/java.
//   2. Run with `./gradlew test` or the gutter arrow.
//   3. Read the assertions: the StateFlow starts at Loading, becomes Success on
//      data, and becomes Error on failure.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] uiState starts at Loading and transitions to Success when the repo emits.
//   [ ] An upstream failure becomes UiState.Error, not a crash.
//   [ ] The ViewModel exposes StateFlow (read-only), never MutableStateFlow.
//   [ ] You can explain why this needs no emulator.
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package dev.crunch.arch.exercise2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
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

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Error(val message: String) : FeedUiState
    data class Success(val articles: List<Article>) : FeedUiState
}

// ----------------------------------------------------------------------------
// The data layer is an INTERFACE so we can fake it. The ViewModel depends on the
// interface, never on a concrete data source (the dependency rule).
// ----------------------------------------------------------------------------

interface NewsRepository {
    fun newsStream(): Flow<List<Article>>
}

/** A fake (a real simple implementation, not a mock) we drive from the test. */
class FakeNewsRepository : NewsRepository {
    // replay = 1 so a late collector still sees the last value we emitted.
    private val stream = MutableSharedFlow<List<Article>>(replay = 1)
    override fun newsStream(): Flow<List<Article>> = stream
    suspend fun emit(articles: List<Article>) = stream.emit(articles)
    // (Failure is tested with a separate throwing repository in the test below,
    // which is cleaner than trying to push a throwable through a value stream.)
}

// ----------------------------------------------------------------------------
// The ViewModel: derive StateFlow<UiState> from the repository Flow. State down
// (the StateFlow), events up (methods). Expose StateFlow, hold nothing mutable
// publicly.
// ----------------------------------------------------------------------------

class FeedViewModel(repository: NewsRepository) : ViewModel() {

    val uiState =
        repository.newsStream()
            .map<List<Article>, FeedUiState> { FeedUiState.Success(it) }   // shape data into UI state
            .catch { e -> emit(FeedUiState.Error(e.message ?: "Failed to load")) }  // failure -> Error state
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,    // Eagerly in tests so we don't need a collector to start
                initialValue = FeedUiState.Loading   // there's always a value; starts at Loading
            )
}

// ----------------------------------------------------------------------------
// The tests
// ----------------------------------------------------------------------------

class FeedViewModelTests {

    @Test
    fun startsAtLoading_thenSuccessOnEmit() = runTest {
        val repo = FakeNewsRepository()
        val vm = FeedViewModel(repo)

        vm.uiState.test {
            // Initial value before any emission.
            assertEquals(FeedUiState.Loading, awaitItem())

            repo.emit(listOf(Article(1, "Kotlin 2.0"), Article(2, "Compose")))
            val state = awaitItem()
            assertTrue("expected Success but was $state", state is FeedUiState.Success)
            assertEquals(2, (state as FeedUiState.Success).articles.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun emptyEmission_isSuccessWithNoArticles_notLoadingForever() = runTest {
        val repo = FakeNewsRepository()
        val vm = FeedViewModel(repo)

        vm.uiState.test {
            assertEquals(FeedUiState.Loading, awaitItem())
            repo.emit(emptyList())
            val state = awaitItem()
            // Empty is a SUCCESS with zero articles, NOT a stuck Loading or an Error.
            assertTrue(state is FeedUiState.Success)
            assertEquals(0, (state as FeedUiState.Success).articles.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun upstreamFailure_becomesErrorState_notACrash() = runTest {
        // A repository whose stream throws — the catch operator must turn it into Error.
        val failing = object : NewsRepository {
            override fun newsStream(): Flow<List<Article>> =
                kotlinx.coroutines.flow.flow { throw RuntimeException("boom") }
        }
        val vm = FeedViewModel(failing)

        vm.uiState.test {
            assertEquals(FeedUiState.Loading, awaitItem())
            val state = awaitItem()
            assertTrue("expected Error but was $state", state is FeedUiState.Error)
            assertEquals("boom", (state as FeedUiState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_isReadOnly_notMutable() {
        // The public type is StateFlow, not MutableStateFlow: the UI can read but not set.
        val vm = FeedViewModel(FakeNewsRepository())
        val isMutable = vm.uiState is kotlinx.coroutines.flow.MutableStateFlow<*>
        assertEquals("uiState must be exposed read-only", false, isMutable)
    }
}

// ----------------------------------------------------------------------------
// WHY this needs no emulator (write it before reading):
//
//   A ViewModel is plain Kotlin (androidx.lifecycle.ViewModel has no UI
//   dependency), and the data layer is an INTERFACE we fake. So we construct the
//   ViewModel with a FakeNewsRepository, drive it by emitting, and assert on its
//   StateFlow with Turbine on the JVM in milliseconds. The architecture's
//   decoupling (UI doesn't own state; state production depends on an interface)
//   is exactly what makes the logic testable without a device. That testability
//   is the reason the architecture is shaped this way.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - SharingStarted.Eagerly in the test means the upstream starts immediately, so
//   you don't need a live collector to begin. In production you'd use
//   WhileSubscribed(5000) so the upstream stops shortly after the screen is gone
//   (lecture 2); for a focused unit test, Eagerly is simpler.
//
// - Turbine's awaitItem() waits for the NEXT emission deterministically. Asserting
//   on uiState.value would only see the CURRENT value and miss the sequence
//   (Loading then Success). Use the .test { } block for transitions.
//
// - The catch operator must come AFTER map and BEFORE stateIn so it catches
//   upstream failures and emits an Error state into the same flow. Put it in the
//   wrong order and either it won't catch, or it catches the wrong thing.
//
// - map<List<Article>, FeedUiState> needs the explicit type args (or a typed
//   lambda) so the flow's element type is FeedUiState, letting catch's emit() send
//   a FeedUiState.Error. Without it, the element type stays List<Article> and the
//   Error emit won't type-check.
//
// - replay = 1 on the fake's SharedFlow means a late collector still sees the last
//   value. For testing the Loading->Success transition you generally emit AFTER
//   subscribing (inside the test block), so replay matters less — but it makes the
//   fake forgiving.
//
// ----------------------------------------------------------------------------
