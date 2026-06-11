// Exercise 3 — Assisted injection: graph dependencies + a runtime argument
//
// Goal: Build an object that needs BOTH an injected dependency (from the graph)
//       and a value only known at the call site (a runtime id), using the
//       @AssistedInject / @Assisted / @AssistedFactory pattern. Prove with tests
//       that the factory threads the runtime value through while the graph
//       supplies the rest.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// Like exercise 2, this runs on plain JVM with `./gradlew test`. We model the
// assisted-injection pattern with a hand-written factory so you can see exactly
// what Hilt's @AssistedFactory generates: an interface with a create(runtimeArg)
// method that captures the graph deps and supplies the assisted one. The
// commented block at the bottom shows the identical code in real Hilt.
//
// The teaching point: an @AssistedInject object's IDENTITY is tied to a runtime
// value (here, an itemId). You cannot @Inject it directly because the graph has
// no binding for "itemId" — it is a value, not a dependency. The factory is the
// bridge: the graph injects the FACTORY, and you call create(itemId) per item.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] All tests pass.
//   [ ] You can explain, in one sentence, WHY you cannot just @Inject the
//       presenter directly (no binding for the runtime value).
//   [ ] You can name one real Android case where assisted injection is required
//       (hint: HiltWorker + WorkerParameters).
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.di.week13

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

// ----------------------------------------------------------------------------
// The graph-provided dependency: a repository the presenter needs. In a real app
// this is itself @Inject-constructed; here we make it a simple in-memory store.
// ----------------------------------------------------------------------------

data class Item(val id: String, val title: String)

interface ItemRepository {
    fun item(id: String): Item?
}

class FakeItemRepository(
    private val items: Map<String, Item>
) : ItemRepository {
    override fun item(id: String): Item? = items[id]
    // A unique id so tests can confirm the SAME repo is shared across presenters.
    val instanceId: Int = nextId()
    companion object {
        private var counter = 0
        private fun nextId(): Int = counter++
    }
}

// ----------------------------------------------------------------------------
// The assisted type: it needs the injected ItemRepository (from the graph) AND
// the runtime itemId (from the call site). In real Hilt this is:
//
//   class ItemDetailPresenter @AssistedInject constructor(
//       private val repository: ItemRepository,   // graph-provided
//       @Assisted private val itemId: String       // call-site-provided
//   )
//
// Here we write the same constructor; the "assisted" parameter is just the one
// the factory supplies.
// ----------------------------------------------------------------------------

class ItemDetailPresenter(
    private val repository: ItemRepository,   // @Inject-style: from the graph
    private val itemId: String                // @Assisted: from the call site
) {
    fun title(): String =
        repository.item(itemId)?.title ?: "Unknown item ($itemId)"

    fun id(): String = itemId

    // Expose the repo identity so a test can prove the SAME repo is shared
    // across presenters built for different ids.
    val repo: ItemRepository get() = repository
}

// ----------------------------------------------------------------------------
// The assisted factory. In real Hilt this is an @AssistedFactory interface whose
// implementation Hilt generates; here we implement it by hand. The factory
// CAPTURES the graph deps (repository) and supplies the assisted one (itemId)
// at create() time. The graph injects the FACTORY, not the presenter.
// ----------------------------------------------------------------------------

interface ItemDetailPresenterFactory {
    fun create(itemId: String): ItemDetailPresenter
}

/** Hand-written stand-in for the Hilt-generated @AssistedFactory implementation. */
class ItemDetailPresenterFactoryImpl(
    private val repository: ItemRepository   // captured once, from the graph
) : ItemDetailPresenterFactory {
    override fun create(itemId: String): ItemDetailPresenter =
        ItemDetailPresenter(repository, itemId)   // graph dep + runtime arg
}

// ----------------------------------------------------------------------------
// Tests
// ----------------------------------------------------------------------------

class AssistedInjectionTest {

    private fun repo(): FakeItemRepository = FakeItemRepository(
        mapOf(
            "a1" to Item("a1", "Apples"),
            "b2" to Item("b2", "Bananas")
        )
    )

    @Test
    fun `factory threads the runtime id into the presenter`() {
        val factory: ItemDetailPresenterFactory = ItemDetailPresenterFactoryImpl(repo())

        val applesPresenter = factory.create("a1")
        val bananasPresenter = factory.create("b2")

        // Each presenter resolved the title for the id it was created with.
        assertEquals("Apples", applesPresenter.title())
        assertEquals("Bananas", bananasPresenter.title())
        assertEquals("a1", applesPresenter.id())
        assertEquals("b2", bananasPresenter.id())
    }

    @Test
    fun `the graph dependency is shared across presenters`() {
        val sharedRepo = repo()
        val factory = ItemDetailPresenterFactoryImpl(sharedRepo)

        val p1 = factory.create("a1")
        val p2 = factory.create("b2")

        // Both presenters share the ONE repository the factory captured — that's
        // the @Singleton-shared graph dependency. Only the itemId differs.
        assertEquals((p1.repo as FakeItemRepository).instanceId,
                     (p2.repo as FakeItemRepository).instanceId)
    }

    @Test
    fun `presenters built for different ids are different objects`() {
        val factory = ItemDetailPresenterFactoryImpl(repo())
        val p1 = factory.create("a1")
        val p2 = factory.create("a1")   // same id, but a fresh presenter each call
        assertNotSame(p1, p2)
    }

    @Test
    fun `missing item falls back gracefully`() {
        val factory = ItemDetailPresenterFactoryImpl(repo())
        val ghost = factory.create("does-not-exist")
        assertEquals("Unknown item (does-not-exist)", ghost.title())
    }
}

// ----------------------------------------------------------------------------
// THE SAME PATTERN IN REAL HILT (read this — it is the one-to-one mapping)
//
//   class ItemDetailPresenter @AssistedInject constructor(
//       private val repository: ItemRepository,    // graph-provided
//       @Assisted private val itemId: String         // call-site-provided
//   ) {
//       fun title(): String =
//           repository.item(itemId)?.title ?: "Unknown item ($itemId)"
//   }
//
//   @AssistedFactory
//   interface ItemDetailPresenterFactory {
//       fun create(itemId: String): ItemDetailPresenter
//   }
//
//   // Hilt INJECTS the factory; you call create(itemId) with the runtime value:
//   @HiltViewModel
//   class ItemDetailViewModel @Inject constructor(
//       presenterFactory: ItemDetailPresenterFactory,
//       savedStateHandle: SavedStateHandle
//   ) : ViewModel() {
//       private val itemId: String = checkNotNull(savedStateHandle["itemId"])
//       private val presenter = presenterFactory.create(itemId)
//   }
//
// The hand-written ItemDetailPresenterFactoryImpl above is EXACTLY what Hilt
// generates for the @AssistedFactory: it captures the injected repository and
// supplies the assisted itemId at create() time.
//
// A REAL Android case where assisted injection is REQUIRED: a HiltWorker. A
// ListenableWorker needs both injected dependencies AND the runtime
// `WorkerParameters` the framework hands it. @HiltWorker uses @AssistedInject
// with @Assisted WorkerParameters under the hood (you'll meet this in Week 16).
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - WHY you can't @Inject the presenter directly: the graph resolves bindings by
//   TYPE. There is no binding for "the String itemId" — it's a runtime value, not
//   a dependency. Assisted injection is precisely the mechanism for "some args
//   from the graph, some from the call site".
//
// - If `the graph dependency is shared` fails, you constructed a NEW repo inside
//   create() instead of capturing the one passed to the factory. The factory must
//   hold the repo and reuse it; only the itemId varies per create().
//
// - assistedFactory.create() returns a FRESH presenter each call (different
//   identity), but they all share the ONE captured repo. That split — fresh
//   per-value object, shared graph deps — is the whole point.
//
// - Don't be tempted to make itemId a `var` set after construction. That gives
//   the presenter an invalid window where itemId is unset. Assisted injection
//   keeps the object valid from construction.
//
// ----------------------------------------------------------------------------
