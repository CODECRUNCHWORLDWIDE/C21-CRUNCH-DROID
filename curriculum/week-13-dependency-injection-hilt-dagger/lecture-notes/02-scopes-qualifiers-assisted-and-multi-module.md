# Lecture 2 — Scopes, qualifiers, assisted injection, and the multi-module graph

Lecture 1 gave you the stack and the happy path: annotate, declare bindings, let Hilt generate the graph. This lecture is about the four things that actually decide whether a DI graph is *correct* in a real, multi-module, multi-team app: a **scope** that controls how long an instance lives, a **qualifier** that disambiguates two bindings of the same type, **assisted injection** for the object that needs both graph dependencies and runtime arguments, and the **multi-module topology** that lets feature teams ship without stepping on each other. We close on the single most valuable debugging skill of the week: reading the generated Dagger code when the build fails.

These are not edge cases. The first version of your app has one `OkHttpClient`; version two needs an authenticated one *and* a public one, and now you need a qualifier. A presenter needs an injected repository *and* the `itemId` the user tapped, and now you need assisted injection. The app grows past one module, and now you need to decide where each binding lives. Everything in this lecture is in service of "the graph stays correct as the app and the team grow."

---

## 1. Scopes — how long does an instance live?

A binding is, by default, **unscoped**: every time the graph needs it, it constructs a fresh instance. `class FeedRepository @Inject constructor(...)` with no scope means each injection point that asks for a `FeedRepository` gets its *own* new one. That is often fine and often wrong.

A **scope** annotation tells the graph: "within this component's lifetime, create *one* instance and reuse it." Hilt's predefined scopes pair one-to-one with the components from lecture 1:

| Scope | Component | Lifetime | Typical use |
|---|---|---|---|
| `@Singleton` | `SingletonComponent` | the whole `Application` process | `OkHttpClient`, `RoomDatabase`, `Retrofit`, repositories |
| `@ActivityRetainedScoped` | `ActivityRetainedComponent` | across configuration change (like a ViewModel) | shared state between an Activity's ViewModels |
| `@ViewModelScoped` | `ViewModelComponent` | one ViewModel instance | a use-case bundle scoped to one screen's ViewModel |
| `@ActivityScoped` | `ActivityComponent` | one Activity instance (dies on rotation) | something tied to a specific Activity |
| `@FragmentScoped` | `FragmentComponent` | one Fragment instance | rarely needed |

**The pairing is enforced by the compiler.** A `@Singleton` annotation on a `@Provides` in a `@InstallIn(ActivityComponent::class)` module is a *build error*: the scope and the component must match. This is one of the most common confusing errors for newcomers, and it has a precise meaning — "you asked for a single instance over the Application's lifetime, but installed the binding in a component that only lives for one Activity; those lifetimes contradict, so I refuse."

### The cost of over-scoping

A reflex many engineers have is to make *everything* `@Singleton` "to be safe." That is a memory leak waiting to happen. A `@Singleton` instance lives for the *entire process* — it is never garbage collected while the app runs. If you `@Singleton`-scope something that holds an `Activity` `Context`, or a large cache, or a `Bitmap`, it lives forever. Scope is a lifetime decision with a memory cost:

```kotlin
// FINE to @Singleton: stateless, small, genuinely app-wide.
@Provides @Singleton
fun provideJson(): Json = Json { ignoreUnknownKeys = true }

// DANGER to @Singleton: if Analytics holds an Activity context, it leaks the
// first Activity for the life of the process.
@Provides @Singleton
fun provideAnalytics(@ApplicationContext ctx: Context): Analytics = Analytics(ctx)
//                    ^ note: ApplicationContext, NOT an Activity context — safe.
```

The rule: **scope a binding to the *shortest* component whose lifetime it genuinely needs, and only scope at all if sharing the instance matters.** A stateless helper does not need a scope — let the graph make fresh ones; they are cheap. Scope when (a) construction is expensive (a `RoomDatabase`, an `OkHttpClient` with its connection pool) or (b) sharing state across injection points is the point (a repository with an in-memory cache). Otherwise leave it unscoped.

`@Singleton` on `OkHttpClient` and `RoomDatabase` is not optional, by the way — it is *required* for correctness, not just performance. `OkHttpClient` owns a connection pool and thread pools; creating a fresh one per request defeats connection reuse and leaks threads. `RoomDatabase` must be a singleton or you get multiple connections to the same SQLite file. These are the canonical "must be `@Singleton`" bindings.

---

## 2. Qualifiers — when two bindings have the same type

The graph identifies a binding by its **type**. The moment you have two bindings of the *same type*, the graph is ambiguous and the build fails with `DuplicateBindings` — "`OkHttpClient` is bound multiple times." The fix is a **qualifier**: a custom annotation that becomes part of the binding's identity, so `@AuthClient OkHttpClient` and `@PublicClient OkHttpClient` are two *distinct* keys in the graph.

You declare a qualifier once:

```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PublicClient
```

Then you tag both the provider and the injection site:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton @PublicClient
    fun providePublicClient(): OkHttpClient =
        OkHttpClient.Builder().build()

    @Provides @Singleton @AuthClient
    fun provideAuthClient(tokenProvider: TokenProvider): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenProvider))
            .build()
}

// At the injection site, the qualifier disambiguates:
class FeedApi @Inject constructor(
    @AuthClient private val client: OkHttpClient
)
```

Without the qualifier the build fails; with it, the graph has two unambiguous keys. The same pattern handles the dispatcher case you will use constantly in this track:

```kotlin
@Qualifier annotation class Dispatcher(val value: AppDispatcher)
enum class AppDispatcher { Default, IO }

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides @Dispatcher(AppDispatcher.IO)
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Dispatcher(AppDispatcher.Default)
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

class NotesRepository @Inject constructor(
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher
)
```

Injecting the dispatcher rather than calling `Dispatchers.IO` directly is what makes the repository testable — in a test you inject a `StandardTestDispatcher` instead. This is the Week 4 concurrency discipline meeting the Week 13 DI discipline: never reach for a global; inject it so a test can swap it.

(`@Named("auth")` is the built-in string qualifier and works, but a typed `@Qualifier` annotation is preferred — a typo in a string qualifier is a runtime-ish surprise; a typo in an annotation name is a compile error.)

---

## 3. Assisted injection — graph dependencies *and* runtime arguments

Some objects need a mix: most of their dependencies come from the graph, but *one or two* are only known at the call site. The canonical example is a per-item presenter or a worker that needs an injected repository (graph) *and* the specific `itemId` the user tapped (runtime). You cannot `@Inject constructor(repo, itemId)` because the graph has no `itemId` binding — `itemId` is a value, not a dependency.

The wrong fixes are: making the runtime value a mutable property set after construction (now the object has an invalid window), or pushing the value through a setter. The right tool is **assisted injection**.

```kotlin
class ItemDetailPresenter @AssistedInject constructor(
    private val repository: ItemRepository,        // from the graph
    @Assisted private val itemId: String           // from the call site
) {
    suspend fun load(): Item = repository.item(itemId)
}

// The factory is an interface Hilt implements; you inject the FACTORY,
// then call it with the runtime value.
@AssistedFactory
interface ItemDetailPresenterFactory {
    fun create(itemId: String): ItemDetailPresenter
}

// Usage: inject the factory (the graph builds it), then create with the id.
class ItemDetailViewModel @Inject constructor(
    private val presenterFactory: ItemDetailPresenterFactory,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val itemId: String = checkNotNull(savedStateHandle["itemId"])
    private val presenter = presenterFactory.create(itemId)   // graph deps + runtime id
}
```

The mechanics:

- `@AssistedInject` on the constructor marks it as a mixed constructor.
- `@Assisted` on a parameter marks it as "supplied at the call site, not by the graph."
- `@AssistedFactory` on an interface with a single `create(...)` method whose parameters match the `@Assisted` ones; Hilt generates the implementation and makes the *factory* injectable.

**When is assisted injection the right tool?** When an object genuinely needs both graph-provided dependencies and a value only known at runtime, *and* you want it constructed fresh per value. If the runtime value is just configuration you could pass into a method, a plain method parameter is simpler — don't reach for `@AssistedInject` to pass an argument you could pass to `load(itemId)`. The signal for assisted injection is: "this object's *identity* is tied to the runtime value, and it holds graph dependencies too." A common real case is constructing a custom `ListenableWorker` (WorkManager, Week 16) that needs both injected dependencies and the runtime `WorkerParameters`; Hilt's `HiltWorker` is assisted injection under the hood.

---

## 4. `@EntryPoint` — reaching the graph from a place Hilt doesn't own

Sometimes you need a graph-provided dependency in a class Hilt cannot annotate: a `ContentProvider` (which is constructed before the Application's `onCreate` completes), a non-Hilt library's callback, or a place where `@AndroidEntryPoint` isn't available. For these you define an **entry point** — an interface that exposes the bindings you need, installed in a component, retrieved with `EntryPointAccessors`:

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AnalyticsEntryPoint {
    fun analytics(): Analytics
}

// From a non-Hilt class (e.g. a ContentProvider):
class MyContentProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val entryPoint = EntryPointAccessors.fromApplication(
            context!!.applicationContext,
            AnalyticsEntryPoint::class.java
        )
        val analytics = entryPoint.analytics()   // pulled out of the graph manually
        return true
    }
}
```

`@EntryPoint` is the escape hatch, not the default. If you can use `@AndroidEntryPoint` or constructor injection, do; reach for `@EntryPoint` only at the genuine boundary where Hilt's generated injection cannot reach. Knowing it exists keeps you from concluding "this class can't use the graph" and duplicating a dependency.

There is a second flavour you will meet: `EntryPointAccessors.fromActivity` and `.fromFragment` retrieve an entry point installed in `ActivityComponent` or `FragmentComponent` rather than the application's `SingletonComponent`. The component you pull from must match the component the `@EntryPoint` is `@InstallIn`-ed into — pulling an `ActivityComponent` entry point `fromApplication` fails, because the application root cannot see Activity-scoped bindings (the inheritance rule from lecture 1, §5, again). When you write an entry point, the `@InstallIn` you choose and the `EntryPointAccessors.from*` you call must agree on the component.

---

## 5. The multi-module graph — where bindings live

A real app is many modules: `:core-network`, `:core-database`, `:feature-auth`, `:feature-feed`, `:app`. The question multi-module DI answers is: **where does each binding's module live, and how does `:app` aggregate them?** This is exactly the mini-project's shape, so internalise it.

The topology:

```text
                    :app   (the @HiltAndroidApp lives here)
                   /  |  \
                  /   |   \
        :feature-auth |   :feature-feed
                  \   |   /
                   \  |  /
            :core-network   :core-database
                   \  |
                  :core-common  (Dispatchers, Clock, qualifiers)
```

The rules that make this work:

1. **Bindings live in the module that owns the implementation.** `:core-network` contains `NetworkModule` (`OkHttpClient`, `Retrofit`, the APIs). `:core-database` contains `DatabaseModule` (`RoomDatabase`, the DAOs). A feature module contains its feature's repository bindings. Each module ships its own `@Module`s.

2. **Most shared bindings install into `SingletonComponent`.** A database, a network client, a repository are application-lifetime. Installing them in `SingletonComponent` makes them visible to every other module's injection sites (children see ancestors, lecture 1 §5). This is why core infrastructure is almost always `@InstallIn(SingletonComponent::class)`.

3. **`:app` doesn't need to know how things are built.** Because Hilt aggregates *all* `@InstallIn(SingletonComponent::class)` modules across every module in the build into the one generated `SingletonComponent`, `:app` just declares `@HiltAndroidApp` and the whole graph assembles. A feature module's `@Module` doesn't get imported by `:app` explicitly — Hilt's aggregation finds it. (This aggregation is why Hilt needs a full annotation-processing pass and why one new binding can trigger recompilation across modules.)

4. **Expose interfaces, hide implementations.** `:core-network` should expose `FeedApi` (an interface) and bind `FeedApiImpl` internally; `:feature-feed` depends on the *interface*, never the impl. A module's `internal` implementation classes stay invisible to consumers; the Hilt module (which can see them) does the `@Binds`. This is how feature teams ship independently: they consume each other's interfaces, not internals.

A concrete `:core-database` module that the *next* week (Room) and the capstone consume:

```kotlin
// in module :core-database
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CrunchDatabase =
        Room.databaseBuilder(context, CrunchDatabase::class.java, "crunch.db")
            .build()

    @Provides
    fun provideNotesDao(db: CrunchDatabase): NotesDao = db.notesDao()
    //         ^ unscoped: the DAO is cheap; the DATABASE is the @Singleton.
}
```

Note the `RoomDatabase` is `@Singleton` (expensive, must be shared) while the DAO is unscoped (cheap, derived from the singleton database). That is the scoping discipline from §1 applied: scope the expensive shared thing, leave the cheap derived thing unscoped.

---

## 6. Reading the generated Dagger code — debugging a `MissingBinding`

This is the skill that separates "I add `@Provides` until it compiles" from "I read the error and know what to fix." When a Hilt build fails, the error is **Dagger's**, and it tells you precisely what couldn't resolve. Learn to read it.

### `MissingBinding`

```text
error: [Dagger/MissingBinding] com.crunch.feed.FeedApi cannot be provided
without an @Provides-annotated method.
    com.crunch.feed.FeedApi is injected at
        com.crunch.feed.FeedRepositoryImpl(api, …)
    com.crunch.feed.FeedRepository is injected at
        com.crunch.feed.FeedViewModel(repository, …)
```

Read it bottom-up: `FeedViewModel` wants a `FeedRepository`, which is `FeedRepositoryImpl`, which wants a `FeedApi`, and `FeedApi` **has no binding**. The fix is exactly one of: give `FeedApi` an `@Inject constructor` (if you own it and it has constructable deps), or `@Provides`/`@Binds` it in a module installed in a component the injection site can see. The error literally hands you the dependency chain — you do not guess.

### `DuplicateBindings`

```text
error: [Dagger/DuplicateBindings] okhttp3.OkHttpClient is bound multiple times
```

Two `@Provides`/`@Inject` for `OkHttpClient` with no qualifier. The fix is a `@Qualifier` on each (§2), or deleting the redundant binding.

### Scope mismatch

```text
error: [Dagger/IncompatiblyScopedBindings]
... @Singleton ... may not reference bindings with different scopes:
    @ActivityScoped ...
```

A `@Singleton` binding is trying to depend on an `@ActivityScoped` one — an app-lifetime object cannot hold an Activity-lifetime object (it would outlive and leak it). The fix is to rethink the scope: either the dependency shouldn't be Activity-scoped, or the dependent shouldn't be a singleton.

### Where the generated code lives

Open `app/build/generated/ksp/debug/kotlin/` after a build. You will find:

- **`Hilt_<Activity>.java`** — the base class that does field injection.
- **`<Class>_Factory.java`** — for each `@Inject constructor` type, a factory that constructs it. Read one: it is just `get()` calling the constructor with resolved args.
- **`Dagger<App>_HiltComponents_SingletonC`** — the assembled singleton component, with every `@Singleton` binding wired. This is the graph, made concrete. Scroll it when you want to *see* the wiring instead of imagining it.

Reading these once removes the magic. The graph is not a black box; it is generated source you can open, and every error points at a specific edge in it.

---

## 7. Putting it together — a production checklist

Before you call a Hilt graph "done," walk this list. It is the code-review checklist a senior reviewer applies:

- **Constructor injection everywhere possible.** Field injection only on framework types that can't be constructor-injected. Grep for `@Inject lateinit var` — each one should be in an `@AndroidEntryPoint` class with no constructor option.
- **Scopes are deliberate.** Every `@Singleton`/`@*Scoped` is there because the instance is expensive or shared, not "to be safe." No `@Singleton` holds an Activity `Context`.
- **`@Binds` for interface→impl, `@Provides` for constructed/configured.** Grep for `@Provides` that just `return SomethingImpl()` — those should be `@Binds`.
- **Qualifiers on every same-type pair.** No `DuplicateBindings` worked around by deleting one; if two are genuinely needed, both are qualified.
- **Dispatchers and clocks are injected, not global.** No `Dispatchers.IO` or `System.currentTimeMillis()` inline in a class that has a constructor — inject `@Dispatcher(IO)` and a `Clock`.
- **Core bindings install in `SingletonComponent`.** `:core-*` modules' `@Module`s are `@InstallIn(SingletonComponent::class)`, and they expose interfaces, hide impls.
- **Assisted injection where identity is tied to a runtime value**, not a mutable property set after construction.
- **The graph compiles clean.** No suppressed warnings, no `@Suppress` on a DI error. A green DI build is a *proof* the graph is complete.

---

## 8. Recap

Lecture 1 sold you on Hilt's happy path for good reason — it really is four annotations and a graph. This lecture was the other half: the decisions that keep the graph *correct* as the app grows. Four habits carry it:

1. **Scope by lifetime, not by reflex.** Scope the expensive or shared thing to the shortest component it truly needs; leave cheap things unscoped; never `@Singleton` an Activity context.
2. **Qualify same-type bindings.** Two `OkHttpClient`s need two qualifiers; inject dispatchers and clocks behind qualifiers so tests can swap them.
3. **Assisted injection for runtime-arg objects.** When an object needs graph deps *and* a call-site value, `@AssistedInject` + `@AssistedFactory`, not a mutable property.
4. **Lay the multi-module graph by ownership.** Bindings live with their implementation, install in `SingletonComponent`, expose interfaces; `:app` aggregates the whole thing with one `@HiltAndroidApp`.

And the debugging discipline that underlies all of it: **the error is Dagger's, and it names the edge.** Read it bottom-up, find the unresolved or ambiguous binding, fix the one annotation. Open the generated code when you need to see the graph instead of imagining it.

The exercises annotate a hand-wired app and make you read the generated factories; the challenge migrates a `ServiceLocator` god-object to a Hilt graph one binding at a time; the mini-project builds the four-module graph the capstone needs. Go make the wiring the compiler's job.

---

## 9. Appendix — a field guide to the scope decision

Because over-scoping is the most common scope mistake and under-scoping is the second, it helps to have a concrete decision procedure rather than a vibe. When you are about to add a scope annotation to a binding, ask three questions in order. First: **does sharing this instance across injection points actually matter?** If two parts of the app each getting their own fresh copy is fine — which is true for most stateless helpers, mappers, and small value objects — then the answer is to add *no scope at all*. Unscoped is the default for a reason; a fresh instance is cheap and carries no lifetime risk. Only proceed to scoping if sharing genuinely matters, either because construction is expensive or because the instance holds shared state.

Second, if sharing matters: **what is the shortest component lifetime that still lets the sharing work?** A repository with an in-memory cache that the whole app should see needs `@Singleton` — the cache must outlive any screen. But a bundle of use-cases that only one screen's ViewModel uses needs only `@ViewModelScoped` — sharing within that ViewModel is enough, and scoping it to the Application would keep it alive long after the screen is gone, for no benefit and some memory cost. The discipline is to scope to the *shortest* lifetime that satisfies the sharing requirement, never the longest "to be safe." Third, and this is the safety check that catches the dangerous case: **does this instance hold anything that should not outlive the chosen scope?** A `@Singleton` must not hold an `Activity` context, a `View`, a `Bitmap`, or anything tied to a screen, because the singleton lives for the whole process and would pin that object forever — a leak. If the binding needs a `Context`, it must be the `@ApplicationContext`, which is process-lifetime and safe to hold in a singleton.

Run those three questions and the scope almost always picks itself: no-scope for the cheap and unshared, the shortest-fitting scope for the shared, and a hard stop on anything Activity-lifetime living in an Application-lifetime instance.

The two failure modes this procedure prevents are the ones you will actually see in code review — the "everything is `@Singleton`" graph that slowly leaks, and the "nothing is scoped" graph that rebuilds an expensive `OkHttpClient` per injection. Both are scope decisions made without asking these questions; making the questions explicit is how you get scope right by default instead of by luck.

One more nuance, since it confuses people: an *unscoped* binding installed in `SingletonComponent` is still *visible* everywhere — installation determines visibility, scope determines instance sharing, and they are independent. So `@Provides fun provideDao(db: Db): Dao` in a `SingletonComponent` module, with no scope annotation, is reachable from every injection site but produces a *fresh* `Dao` on each request. That is usually exactly right for a cheap accessor like a DAO: globally available, but not worth caching. Don't conflate "I want this visible app-wide" (install in `SingletonComponent`) with "I want one shared instance" (add `@Singleton`) — they are two separate decisions, and the DAO case is the canonical example of wanting the first without the second.

## 10. Appendix — interfaces at module boundaries, and why feature teams stay decoupled

The single architectural rule that makes multi-module Hilt scale to many teams is worth stating on its own: **a module exposes interfaces and hides implementations, and other modules depend only on the interfaces.** When `:core-network` ships an `AuthApi` *interface* and keeps `AuthApiImpl` `internal`, the `:feature-auth` module that consumes `AuthApi` literally cannot reference the implementation — the Kotlin `internal` visibility makes it invisible across the module boundary. The only code that can see `AuthApiImpl` is the Hilt module inside `:core-network` that does the `@Binds`. This is not ceremony; it is what lets the network team change `AuthApiImpl` — rewrite it, swap Retrofit for Ktor, add a cache — without any consumer noticing, because consumers were never coupled to the implementation in the first place. They were coupled to the contract, and the contract didn't change.

This is also what makes the graph composable across teams without coordination. Because every module installs its bindings into the shared `SingletonComponent` and Hilt aggregates them at build time, the `:feature-auth` team writes `@Inject constructor(api: AuthApi, dao: SessionDao)` and the binding is resolved from whichever modules provide those interfaces — without `:feature-auth` importing, knowing about, or being recompiled by changes to those modules' implementations. The feature team depends on `:core-network`'s and `:core-database`'s *public API* (the interfaces) and is insulated from their internals. Scale that across a dozen feature teams and several core teams, and you have a build where teams ship independently, the graph assembles itself from everyone's contributions, and a change to one module's implementation does not ripple outward. That decoupling — interfaces at the seams, implementations hidden, the graph aggregating contracts — is the real reason large Android orgs standardise on this exact topology, and it is the shape the capstone's seven-module graph follows.
