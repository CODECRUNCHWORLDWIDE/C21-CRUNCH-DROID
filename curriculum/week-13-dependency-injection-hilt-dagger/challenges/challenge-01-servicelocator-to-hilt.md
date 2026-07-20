# Challenge 1 — Migrate a ServiceLocator god-object to a Hilt graph

**Time.** 60–120 minutes.
**Deliverable.** A short report (`MIGRATION.md`) documenting each step and what it bought you, plus the migrated code, committed to your Week 13 repo.

## The premise

Every Android codebase that predates a DI framework has a `ServiceLocator` — a global object that news up dependencies and hands them out. It works in the demo. Then the app grows, the locator becomes a 600-line god-object, tests start polluting each other because the singletons are mutable global state, and a missing dependency is a `NullPointerException` in production instead of a build error. The skill this challenge builds is not "know Hilt is better" — it's **migrate a real god-object to a clean graph incrementally, without a big-bang rewrite, and prove at each step what you fixed.**

You will take a `ServiceLocator` with the classic problems, migrate it one binding at a time, and document the win at each step. The grading is the quality of the migration and the clarity of your explanation of *why* each step is an improvement.

## What to start from

Here is the god-object. Type it in (or copy it once — this is the *before*, not the thing you're drilling). Note its three sins, called out in comments.

```kotlin
// ServiceLocator.kt — the thing we are going to dismantle.
object ServiceLocator {

    // SIN 1: mutable global singletons. Tests that swap these pollute each other,
    //        and there's no isolation between test cases.
    var httpClient: HttpClient = HttpClient(timeoutMs = 15_000)

    var notesApi: NotesApi = RetrofitNotesApi(httpClient)        // hidden ordering dependency
    var database: AppDatabase = AppDatabase.create("app.db")
    var notesDao: NotesDao = database.notesDao()

    // SIN 2: the repository depends on api + dao, wired by HAND, by ordering.
    //        Reorder these declarations and it crashes with an NPE at class-load.
    var notesRepository: NotesRepository = NotesRepository(notesApi, notesDao)

    // SIN 3: a consumer reaches into the global to find its dependency. There's no
    //        compile-time guarantee the dependency exists; a typo or a missing
    //        assignment is a runtime crash.
    fun provideNotesViewModel(): NotesViewModel =
        NotesViewModel(notesRepository)
}

// Supporting types (stubs — the migration is about the WIRING, not these).
class HttpClient(val timeoutMs: Long)
interface NotesApi { suspend fun fetch(): List<String> }
class RetrofitNotesApi(private val client: HttpClient) : NotesApi {
    override suspend fun fetch(): List<String> = listOf("from network")
}
class AppDatabase private constructor(val name: String) {
    fun notesDao(): NotesDao = NotesDao()
    companion object { fun create(name: String) = AppDatabase(name) }
}
class NotesDao
class NotesRepository(private val api: NotesApi, private val dao: NotesDao) {
    suspend fun notes(): List<String> = api.fetch()
}
class NotesViewModel(private val repository: NotesRepository)
```

## The migration — one binding at a time

Do these in order. After **each** step, the project must still build and run. Document each in `MIGRATION.md` with: what you changed, and what it bought you.

### Step 1 — Stand up the graph root

Add `@HiltAndroidApp class CrunchApp : Application()` and register it in the manifest. Nothing else changes yet; the `ServiceLocator` still exists. You've created the empty `SingletonComponent` the migration will fill.

> **What it bought you:** a place for the graph to live. The locator and the graph coexist during the migration — you never have a non-building state.

### Step 2 — Migrate the leaf with no dependencies (`HttpClient`)

`HttpClient` depends on nothing but a config value, so provide it in a module and `@Singleton`-scope it (a client owns a connection pool — it must be shared):

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(timeoutMs = 15_000)
}
```

Now anywhere the graph needs an `HttpClient`, it has one. The locator's `httpClient` field is still there but redundant.

> **What it bought you:** the `HttpClient` is now a *real* singleton owned by the graph, not a mutable global. A test can install a different one without mutating shared state.

### Step 3 — Migrate the interface (`NotesApi`) with `@Binds`

`NotesApi` is an interface; `RetrofitNotesApi` is the impl. Give the impl an `@Inject constructor` (its `HttpClient` dependency now comes from the graph), and `@Binds` the interface:

```kotlin
class RetrofitNotesApi @Inject constructor(
    private val client: HttpClient
) : NotesApi {
    override suspend fun fetch(): List<String> = listOf("from network")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ApiModule {
    @Binds
    abstract fun bindNotesApi(impl: RetrofitNotesApi): NotesApi
}
```

> **What it bought you:** the hidden ordering dependency (`notesApi` had to be declared after `httpClient` in the locator) is gone. The graph resolves the order. Consumers depend on the `NotesApi` interface, not the Retrofit impl.

### Step 4 — Migrate the database and DAO with correct scoping

The database is expensive and must be a singleton; the DAO is cheap and derived:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(): AppDatabase = AppDatabase.create("app.db")

    @Provides   // unscoped: the DAO is cheap; the DATABASE is the singleton.
    fun provideNotesDao(db: AppDatabase): NotesDao = db.notesDao()
}
```

> **What it bought you:** scope correctness made explicit. The database is `@Singleton` (one SQLite connection), the DAO is unscoped (cheap to re-derive). The locator made *everything* a singleton field implicitly; now the lifetimes are deliberate decisions.

### Step 5 — Migrate the repository to constructor injection

`NotesRepository` becomes `@Inject constructor` — the graph resolves its `NotesApi` and `NotesDao`:

```kotlin
class NotesRepository @Inject constructor(
    private val api: NotesApi,
    private val dao: NotesDao
) {
    suspend fun notes(): List<String> = api.fetch()
}
```

> **What it bought you:** the repository is now testable *without Hilt at all* — `NotesRepository(fakeApi, fakeDao)` in a unit test. The locator forced you through the global to build one.

### Step 6 — Migrate the ViewModel and delete the locator

`NotesViewModel` becomes a `@HiltViewModel`:

```kotlin
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: NotesRepository
) : ViewModel()
```

Then **delete `ServiceLocator.kt` entirely** and confirm nothing references it. The graph now owns the whole chain.

> **What it bought you:** the god-object is gone. Every dependency is constructor-injected, scoped deliberately, and resolved by the compiler. A missing binding is now a build error with a dependency chain, not a production NPE.

## Step 7 — Prove the two wins concretely

In `MIGRATION.md`, demonstrate the two headline improvements with evidence:

1. **Compile-time safety.** Temporarily delete one `@Provides`/`@Binds`, paste the `MissingBinding` error (with its dependency chain) into the report, then restore it. Contrast: in the locator, the same mistake was a runtime crash with no chain.
2. **Test isolation.** Write a tiny unit test that constructs `NotesRepository(fakeApi, fakeDao)` directly with fakes — no Hilt, no global mutation. Note that the same test against the old locator would have had to mutate `ServiceLocator.notesApi` (polluting other tests) or couldn't isolate at all.

## Acceptance criteria

- [ ] All six migration steps completed; the project builds and runs after **each** step (no big-bang).
- [ ] `ServiceLocator.kt` is **deleted** and nothing references it.
- [ ] `HttpClient` and `AppDatabase` are `@Singleton`; the DAO is unscoped — and `MIGRATION.md` explains why that scoping is correct.
- [ ] `NotesApi` is bound with `@Binds`; the repository and ViewModel use `@Inject constructor` / `@HiltViewModel`.
- [ ] `MIGRATION.md` documents each step's "what it bought you", includes the pasted `MissingBinding` error from the compile-time-safety demo, and includes the no-Hilt unit test from the test-isolation demo.
- [ ] Build with **0 warnings**.

## What "great" looks like

A weak submission says "I replaced the ServiceLocator with Hilt." A great submission says:

> The 600-line `ServiceLocator` had three structural problems: mutable global singletons that leaked state between tests, an implicit declaration-order dependency (reordering the fields crashed at class-load with an NPE), and consumers reaching into the global so a missing dependency surfaced only at runtime. Migrating bottom-up — leaf `HttpClient` first, then the `@Binds`-bound `NotesApi`, then the correctly-scoped database/DAO pair, then the constructor-injected repository and `@HiltViewModel` — kept the app building at every step. The payoff is measurable: deleting the `NotesApi` `@Binds` now produces `[Dagger/MissingBinding] NotesApi ... injected at NotesRepository(api) ... injected at NotesViewModel(repository)` *at compile time*, where the locator gave a production `NullPointerException` with no chain; and `NotesRepository(fakeApi, fakeDao)` is now unit-testable in isolation, where the locator forced tests to mutate shared global state.

Specific, incremental, and honest about what each step fixed. That's the senior-engineer answer.

## Where this reappears

The incremental-migration discipline and the "scope deliberately, expose interfaces" instinct are exactly what the **mini-project** (the four-module graph) and the **capstone** (the seven-module graph) demand. The footgun you removed here — global mutable singletons and runtime-resolved dependencies — is the same shape as the un-testable, un-scoped code you'll refactor when you wire Room (Week 14) and networking (Week 15) into this graph.
