# Mini-Project — The four-module Hilt graph

This week you build the dependency-injection backbone the capstone needs: a **multi-module Hilt graph** with `:core-network`, `:core-database`, `:feature-auth`, and `:app`. Each module owns its bindings and exposes them through its own Hilt modules; `:app` aggregates the whole thing with a single `@HiltAndroidApp` and consumes everything without knowing how any of it is constructed. By the end, the graph builds clean, a deliberate break produces a readable `MissingBinding`, and you can point at the generated `SingletonComponent` and see your wiring.

This is the *foundation* project for Phase 3. It is not throwaway. The exact module shape you build here — `:core-network`, `:core-database`, `:feature-auth`, `:app` — is the capstone's shape. Week 14 fills `:core-database` with a real Room database; Week 15 fills `:core-network` with real Retrofit/OkHttp/Ktor; Week 16 adds `:feature-sync`. This week you build the graph they all plug into. Get the wiring right now and the rest of the phase clicks into it.

---

## Where you're starting from

A fresh multi-module Android project, or your Week 12 app split into modules. You need:

- The Hilt Gradle plugin and KSP applied in every module that has Hilt code.
- A version catalog (`libs.versions.toml`) with `hilt`, `hilt-compiler`, and the AndroidX libraries — Week 6 toolchain.
- Module declarations in `settings.gradle.kts`: `:app`, `:core-network`, `:core-database`, `:feature-auth`.

If you don't have a clean Week 12 checkpoint, scaffold the minimal version first; the DI work is the same either way. We provide **stub** implementations for the network and database types — the point of this week is the *wiring*, not the real Retrofit/Room code (those are Weeks 15 and 14).

## What you're building toward

By the end you have:

- A `:core-network` module that `@Provides` an `OkHttpClient` (`@Singleton`), a `Retrofit` (stubbed base URL), and an `AuthApi` interface — with a `@Qualifier` distinguishing an authenticated from a public client.
- A `:core-database` module that `@Provides` a `RoomDatabase` (`@Singleton`, stubbed) and exposes a `SessionDao` (unscoped).
- A `:feature-auth` module that `@Binds` an `AuthRepository` interface to its implementation and exposes a `@HiltViewModel` `AuthViewModel`.
- An `:app` module with `@HiltAndroidApp`, an `@AndroidEntryPoint` Activity, and the assembled graph that wires all of the above.
- A passing **graph-builds verification**: the whole thing compiles, a deliberate `@Provides` deletion produces a readable `MissingBinding`, and you can read the generated component.

---

## Milestone 1 — `:core-network` with a qualified client (≈ 2 h)

Build the network module. Two `OkHttpClient`s — one authenticated, one public — disambiguated by qualifier, plus a stubbed `Retrofit` and an `AuthApi` interface.

```kotlin
// in module :core-network — qualifiers (typed, not @Named)
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AuthClient
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class PublicClient

// The API the auth feature depends on (an INTERFACE — implementation hidden).
interface AuthApi {
    suspend fun signIn(token: String): String   // returns a session id (stub)
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton @PublicClient
    fun providePublicClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides @Singleton @AuthClient
    fun provideAuthClient(@PublicClient base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .addInterceptor { chain ->
                val authed = chain.request().newBuilder()
                    .header("Authorization", "Bearer stub-token")
                    .build()
                chain.proceed(authed)
            }
            .build()

    @Provides @Singleton
    fun provideRetrofit(@AuthClient client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.crunch.example/")   // stub; real client is Week 15
            .client(client)
            .addConverterFactory(
                Json { ignoreUnknownKeys = true }
                    .asConverterFactory("application/json".toMediaType())
            )
            .build()

    @Provides @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)
}
```

Decisions you must be able to defend in review:

- **Why two `OkHttpClient`s and a qualifier?** The auth client carries an `Authorization` interceptor; the public client (used for sign-in *before* you have a token) must not. Same type, two bindings — without qualifiers the build fails `DuplicateBindings`.
- **Why is the client `@Singleton`?** `OkHttpClient` owns a connection pool and thread pools. A fresh one per request defeats connection reuse and leaks threads. This is a *correctness* requirement, not just performance.
- **Why does `provideAuthClient` take the `@PublicClient` as a base?** It reuses the public client's connection pool via `newBuilder()` — one pool, two configured clients. Building two from-scratch clients would double the resources.

## Milestone 2 — `:core-database` with deliberate scoping (≈ 1.5 h)

Build the database module. The database is the expensive singleton; the DAO is the cheap derived binding.

```kotlin
// in module :core-database
// Stub RoomDatabase — Week 14 fills this in for real.
abstract class CrunchDatabase {
    abstract fun sessionDao(): SessionDao
    companion object { /* Room.databaseBuilder lands in Week 14 */ }
}

interface SessionDao {
    suspend fun saveSession(id: String)
    suspend fun currentSession(): String?
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CrunchDatabase =
        // Stub: real builder is Week 14. The SCOPE and the @ApplicationContext
        // injection are the lessons here, not the Room call.
        StubCrunchDatabase(context)

    @Provides   // unscoped: the DAO is cheap; the DATABASE is the @Singleton.
    fun provideSessionDao(db: CrunchDatabase): SessionDao = db.sessionDao()
}
```

Decisions to defend:

- **Why is the database `@Singleton` but the DAO unscoped?** A `RoomDatabase` must be a singleton or you get multiple connections to one SQLite file. The DAO is a thin accessor derived from the database — cheap to re-create, so scoping it buys nothing. Scope the expensive shared thing; leave the cheap derived thing unscoped.
- **Why `@ApplicationContext` and not an Activity context?** A `@Singleton` lives for the whole process. Holding an Activity context in a singleton leaks that Activity forever. `@ApplicationContext` is process-lifetime, so it is safe to hold in a singleton.

## Milestone 3 — `:feature-auth` consuming the core modules (≈ 1.5 h)

The auth feature depends on `:core-network`'s `AuthApi` and `:core-database`'s `SessionDao`, and exposes an `AuthRepository` interface (impl hidden) and a `@HiltViewModel`.

```kotlin
// in module :feature-auth
interface AuthRepository {
    suspend fun signIn(token: String): Boolean
    suspend fun isSignedIn(): Boolean
}

// internal: consumers see only the interface, never this class.
internal class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,          // from :core-network (resolved across modules)
    private val sessionDao: SessionDao // from :core-database
) : AuthRepository {
    override suspend fun signIn(token: String): Boolean {
        val sessionId = api.signIn(token)
        sessionDao.saveSession(sessionId)
        return true
    }
    override suspend fun isSignedIn(): Boolean = sessionDao.currentSession() != null
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository   // the interface, not the impl
) : ViewModel() {
    fun signIn(token: String) = viewModelScope.launch {
        repository.signIn(token)
    }
}
```

The key insight: `AuthRepositoryImpl`'s constructor pulls `AuthApi` from `:core-network` and `SessionDao` from `:core-database` — **across module boundaries** — because all three modules install into the *same* `SingletonComponent`, which Hilt aggregates across the whole build. `:feature-auth` never imports `NetworkModule` or `DatabaseModule`; it just declares the dependency by type and Hilt's aggregation finds the binding. That cross-module resolution is the whole point of multi-module Hilt.

## Milestone 4 — `:app` assembling the graph (≈ 1 h)

The `:app` module ties it together: the application root, an entry-point Activity, and a Compose screen that uses the `AuthViewModel`.

```kotlin
// in module :app
@HiltAndroidApp
class CrunchApp : Application()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: AuthViewModel = hiltViewModel()   // hilt-navigation-compose
            AuthScreen(onSignIn = { viewModel.signIn(it) })
        }
    }
}
```

`:app` declares `@HiltAndroidApp` exactly once, depends on the three other modules in its `build.gradle.kts`, and consumes their bindings. It does not need to import any other module's `@Module` — Hilt's aggregation assembles every `@InstallIn(SingletonComponent::class)` module across the build into the one generated component.

## Milestone 5 — Verify the graph builds, and read it (≈ 0.5 h)

The acceptance bar. Three checks:

1. **It compiles.** `./gradlew :app:assembleDebug` succeeds. The whole four-module graph resolves with no `MissingBinding`, no `DuplicateBindings`, no scope error.
2. **A deliberate break is readable.** Comment out `provideAuthApi` in `NetworkModule`. Build. Confirm the error reads like:
   ```text
   [Dagger/MissingBinding] ...AuthApi cannot be provided ...
       ...AuthApi is injected at ...AuthRepositoryImpl(api, …)
       ...AuthRepository is injected at ...AuthViewModel(repository, …)
   ```
   Read it bottom-up across module boundaries: `AuthViewModel` (in `:feature-auth`) → `AuthRepository` → `AuthRepositoryImpl` → `AuthApi` (unprovided, in `:core-network`). Restore the `@Provides`.
3. **You can read the generated component.** Open `app/build/generated/ksp/debug/kotlin/` (or `kapt/`), find `DaggerCrunchApp_HiltComponents_SingletonC`, and locate where the `@Singleton @AuthClient` `OkHttpClient` is wired. Take a screenshot or note the file/line in your README. The graph is generated source — read it.

---

## Acceptance criteria

- [ ] Four modules — `:core-network`, `:core-database`, `:feature-auth`, `:app` — each with the Hilt plugin and KSP applied where it has Hilt code.
- [ ] `:core-network` provides two qualified `OkHttpClient`s (`@AuthClient`/`@PublicClient`), a `@Singleton` `Retrofit`, and an `AuthApi` interface.
- [ ] `:core-database` provides a `@Singleton` database and an **unscoped** DAO, using `@ApplicationContext` (no Activity context in a singleton).
- [ ] `:feature-auth` `@Binds` `AuthRepository` to an `internal` impl, whose constructor resolves `AuthApi` and `SessionDao` **across module boundaries**, and exposes a `@HiltViewModel`.
- [ ] `:app` declares `@HiltAndroidApp` exactly once, has an `@AndroidEntryPoint` Activity, and consumes `AuthViewModel` via `hiltViewModel()`.
- [ ] **The graph builds clean**, a deliberate `@Provides` deletion produces a readable cross-module `MissingBinding`, and you located the binding in the generated `SingletonComponent`.
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **Add `:core-common` with injected dispatchers.** Create a `@Dispatcher(IO)` / `@Dispatcher(Default)` qualifier pair providing `CoroutineDispatcher`s, and inject `@Dispatcher(IO)` into `AuthRepositoryImpl` so its suspend work is on the IO dispatcher (and swappable in a test).
- **Assisted injection in the feature.** Add a per-session `SessionPresenter` that needs the injected `AuthRepository` *and* a runtime `sessionId`, with an `@AssistedFactory`. Wire it into `AuthViewModel`.
- **An `@EntryPoint` for a non-Hilt boundary.** Add a `ContentProvider` (or a `BroadcastReceiver`) that reaches `AuthRepository` via `EntryPointAccessors` — demonstrate the escape hatch.
- **A `@HiltAndroidTest` swap.** Preview Week 17: write one test that uses `@UninstallModules(NetworkModule::class)` and `@BindValue` to substitute a fake `AuthApi`, proving the graph is swappable for tests.

## What this milestone earns you

You can now design a multi-module Hilt graph — the literal "skill earned" line for the week. More than that: you built the exact module topology the capstone needs, with qualifiers, deliberate scoping, interface-hiding, and cross-module resolution all in place. The graph you built this week is the chassis; Weeks 14, 15, and 16 bolt the real engine, database, and sync onto it. You'll be glad the wiring is solid before you start filling it with the things being wired.
