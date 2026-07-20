# Week 13 — Dependency injection with Hilt (and the Dagger graph beneath)

Welcome to Week 13 of **C21 · Crunch Droid**, and the opening of Phase 3. For twelve weeks you have built the front of the app: Kotlin as a language, coroutines and Flow, Compose, navigation, Material 3, and the MVVM-with-UDF spine from Week 12. Every one of those ViewModels needed a repository; every repository needed a data source; every data source needed an `OkHttpClient` or a `RoomDatabase`. So far you have been constructing those by hand — `MyViewModel(NotesRepository(NotesApi(retrofit), notesDao))` — or papering over it with a `ServiceLocator` singleton. This week that ad-hoc wiring becomes a **dependency graph** the compiler builds for you, and the tool that builds it is **Hilt**.

Hilt is Google's opinionated dependency-injection framework for Android, and in 2026 it is the default answer for a new app. But the most important fact about Hilt — the fact this week hammers on — is that **Hilt is a thin opinionated layer over Dagger**. The same Dagger that has been the JVM's compile-time DI framework for over a decade. Hilt is not a new injector; it is a curated set of Dagger components, predefined scopes, and annotation-processor-generated glue that spares you the Dagger boilerplate everyone got wrong. Almost everything that confuses people about Hilt — why a binding is "not provided", why a scope mismatch fails to compile, why `@Inject` works here but not there, why the generated class is named `Hilt_MainActivity` — is explained by what Dagger is doing one layer down. We teach Hilt as the thing you write and Dagger as the thing you debug.

The mental shift this week is from "I `new` up my dependencies" to "I **declare** how each dependency is made, and the compiler **wires the graph** and hands me finished objects." A `@Module` is where you teach the graph how to construct a type it cannot construct itself (an interface, a third-party class, a configured singleton). A **component** is a generated container scoped to a lifetime — the application, an activity, a ViewModel. A **scope** (`@Singleton`, `@ActivityRetainedScoped`, `@ViewModelScoped`) tells the graph how long a binding's single instance should live. `@Inject` on a constructor says "the graph already knows how to build this — just ask." And the whole thing is checked **at compile time**: a missing binding is a build error with a file and line, not a `NullPointerException` in front of a user three weeks after release.

We close the week by building a **multi-module Hilt graph** — `:core-network`, `:core-database`, `:feature-auth`, `:app` — exactly the shape the capstone needs. Each module exposes its bindings through its own Hilt modules; `:app` consumes them without knowing how they are constructed. You will open the Dagger-generated factories with `./gradlew :app:kaptDebugKotlin` (or KSP) and read the `DaggerApplicationComponent` the processor wrote, so that when an error says `MissingBinding`, you know exactly which generated file to read and which `@Provides` you forgot.

## Learning objectives

By the end of this week, you will be able to:

- **Explain** Hilt's relationship to Dagger — that it is a predefined set of Dagger components, scopes, and generated entry points — and predict which Hilt behaviours (component hierarchy, scope lifetimes, generated factory names) come straight from Dagger.
- **Annotate** an Android app for Hilt: `@HiltAndroidApp` on the `Application`, `@AndroidEntryPoint` on activities/fragments/services, and `@HiltViewModel` with `@Inject constructor` on ViewModels.
- **Provide** bindings the graph cannot construct itself with `@Module` + `@Provides` (for configured or third-party types) and `@Binds` (for interface-to-implementation), installed into the correct component with `@InstallIn`.
- **Scope** bindings deliberately with `@Singleton`, `@ActivityRetainedScoped`, `@ViewModelScoped`, and friends, and explain the lifetime each one is keyed to and the cost of getting it wrong.
- **Qualify** two bindings of the same type with `@Qualifier` annotations (e.g. an authenticated vs. unauthenticated `OkHttpClient`) so the graph is never ambiguous.
- **Construct** objects whose dependencies are partly runtime values with **assisted injection** (`@AssistedInject` / `@AssistedFactory`), and know when assisted injection is the right tool versus a plain factory.
- **Design** a multi-module Hilt graph where feature and core modules each own their bindings and `:app` aggregates them, and reason about where a module's `@InstallIn` component must live.
- **Read** the Dagger-generated code — `Hilt_*` base classes, `*_Factory`, `DaggerApplicationComponent` — and map a compile-time DI error (`MissingBinding`, `DuplicateBindings`, scope mismatch) back to the annotation that caused it.

## Prerequisites

This week assumes you have completed **C21 weeks 1–12**, or have equivalent fluency. Specifically:

- You can read and write idiomatic Kotlin — constructors, interfaces, `object`, `companion object`, generics, annotations — Weeks 1–3. Constructor injection is the whole game this week, so the `class Foo @Inject constructor(...)` shape needs to read naturally.
- You understand coroutines, `CoroutineScope`, and dispatchers — Week 4. You will inject a `@Dispatcher(IO) CoroutineDispatcher` and a `CoroutineScope`, and the lifetime of an injected scope is a real decision.
- You can write a Jetpack `ViewModel` with a `StateFlow<UiState>` — Week 12. `@HiltViewModel` is the integration point; you must already know what a ViewModel is and why it outlives configuration change.
- You can read `build.gradle.kts` and a `libs.versions.toml` version catalog — Week 6. Hilt is a Gradle plugin plus an annotation processor (KSP in 2026), and you will add it to multiple modules.

**Toolchain.** Android Studio (2025.1 / Narwhal or later), AGP 8.7+, Kotlin 2.1+, Hilt 2.52+, JDK 17. KSP 2 is the annotation-processing backend for Hilt in 2026 (kapt is legacy and slower; we use KSP and flag the kapt fallback where the older docs assume it). Everything this week runs on the emulator or a device; a couple of generated-code exercises run as a plain `./gradlew` build with no UI.

## Topics covered

- **Hilt as a Dagger overlay.** What Dagger is (a compile-time DI annotation processor over the JVM), what Hilt adds (predefined components, predefined scopes, generated Android entry points), and which Hilt behaviours are Dagger behaviours wearing an Android hat.
- **The entry-point annotations.** `@HiltAndroidApp` (generates the application component and the `Application` base class), `@AndroidEntryPoint` (generates the `Hilt_*` base for activities/fragments/services and performs field injection), `@HiltViewModel` (bridges Hilt to the Jetpack `ViewModel` factory).
- **`@Inject` constructor injection.** The default and preferred mechanism — the graph constructs the type directly. Why constructor injection beats field injection everywhere you can use it.
- **`@Module`, `@Provides`, `@Binds`.** Teaching the graph to build types it cannot construct: third-party classes (`OkHttpClient`, `Retrofit`), interfaces (`@Binds` an implementation to its interface), and configured singletons. `@Binds` vs. `@Provides` and why `@Binds` is cheaper.
- **`@InstallIn` and the component hierarchy.** `SingletonComponent`, `ActivityRetainedComponent`, `ViewModelComponent`, `ActivityComponent`, `FragmentComponent`, `ServiceComponent`, `ViewComponent` — the tree, the parent/child relationship, and what each lifetime means.
- **Scopes.** `@Singleton`, `@ActivityRetainedScoped`, `@ViewModelScoped`, `@ActivityScoped`, `@FragmentScoped`. Unscoped vs. scoped bindings, the scope-component pairing rules the compiler enforces, and the memory cost of over-scoping.
- **Qualifiers.** `@Qualifier` for disambiguating two bindings of the same type (`@AuthClient OkHttpClient` vs. `@PublicClient OkHttpClient`); the built-in qualifier pattern for dispatchers (`@Dispatcher(IO)`).
- **Assisted injection.** `@AssistedInject` constructors, `@Assisted` parameters, `@AssistedFactory` interfaces — for objects that need both graph-provided and call-site-provided arguments (a presenter that needs an injected repo *and* a runtime `itemId`).
- **Multi-module DI.** Where bindings live in a layered project, why `@InstallIn(SingletonComponent::class)` is the usual choice for core modules, the `:core-*` / `:feature-*` / `:app` topology, and how feature modules expose bindings without leaking implementations.
- **`@EntryPoint`.** Reaching into the Hilt graph from a place Hilt does not own (a `ContentProvider`, a `BroadcastReceiver`, a non-Hilt library callback) via `EntryPointAccessors`.
- **Reading generated code.** `Hilt_MainActivity`, `MyClass_Factory`, `DaggerApp_HiltComponents_SingletonC`, the `_HiltModules` aggregation — what each is, and how a `MissingBinding` / `DuplicateBindings` / scope error points you at the annotation to fix.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                                 | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|-----------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | Hilt-over-Dagger; entry points; `@Inject` constructor injection       |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | `@Module`/`@Provides`/`@Binds`; `@InstallIn`; the component hierarchy  |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Scopes; qualifiers; reading the generated Dagger graph                 |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | Assisted injection; `@EntryPoint`; multi-module DI; challenge          |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — the four-module Hilt graph                             |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work; wire `:feature-auth`, verify the graph builds  |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                            |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                       | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./README.md) | This overview (you are here) |
| [resources.md](./resources.md) | The Hilt and Dagger docs, the dagger.dev component pages, the Now-In-Android module setup, and the canonical writing on multi-module DI |
| [lecture-notes/01-hilt-as-a-dagger-overlay.md](./lecture-notes/01-hilt-as-a-dagger-overlay.md) | Hilt end to end: what Dagger is, what Hilt adds, the entry-point annotations, `@Inject`/`@Module`/`@Provides`/`@Binds`, `@InstallIn`, the component hierarchy, and where it leaks Dagger |
| [lecture-notes/02-scopes-qualifiers-assisted-and-multi-module.md](./lecture-notes/02-scopes-qualifiers-assisted-and-multi-module.md) | Scopes and their lifetimes, qualifiers, assisted injection, `@EntryPoint`, the multi-module graph, and reading the generated Dagger code to debug a `MissingBinding` |
| [exercises/README.md](./exercises/README.md) | Index of the three exercises |
| [exercises/exercise-01-annotate-an-app-for-hilt.md](./exercises/exercise-01-annotate-an-app-for-hilt.md) | Take a hand-wired app, annotate it for Hilt, and delete the manual construction — prove the graph builds the same objects |
| [exercises/exercise-02-modules-binds-scopes-qualifiers.kt](./exercises/exercise-02-modules-binds-scopes-qualifiers.kt) | Write `@Provides`/`@Binds` modules, scope a singleton, and disambiguate two `OkHttpClient`s with `@Qualifier`; tested without an emulator |
| [exercises/exercise-03-assisted-injection.kt](./exercises/exercise-03-assisted-injection.kt) | Build an `@AssistedInject` type that needs an injected repo and a runtime id, with an `@AssistedFactory`, and test it |
| [challenges/README.md](./challenges/README.md) | Index of the challenge |
| [challenges/challenge-01-servicelocator-to-hilt.md](./challenges/challenge-01-servicelocator-to-hilt.md) | Take a `ServiceLocator`-based god-object, migrate it to a Hilt graph one binding at a time, and document what each migration step bought you |
| [quiz.md](./quiz.md) | 13 questions on the lineage, entry points, modules/binds, components/scopes, qualifiers, assisted injection, and generated code |
| [homework.md](./homework.md) | Six practice problems for the week |
| [mini-project/README.md](./mini-project/README.md) | Full spec for the four-module Hilt graph: `:core-network`, `:core-database`, `:feature-auth`, `:app` |

## The "the graph builds, or it doesn't" promise

Week 12 gave you "state survives process death." Week 13 adds the promise a senior reviewer checks first when they open a DI PR:

> **A missing or ambiguous dependency is a compile error, not a runtime crash.** If your `OkHttpClient` is unprovided, the build fails with `MissingBinding: OkHttpClient`, pointing at the injection site — *before* the app ships. If you have two `OkHttpClient`s and no qualifier, the build fails with `DuplicateBindings`. Compile-time DI means the class of bug "I forgot to construct a dependency and it crashed in production" simply cannot reach a user.

You will *prove* this by deliberately deleting a `@Provides` and reading the exact compile error, then restoring it — so that when a real `MissingBinding` lands in a 30-module project, you read it as a sentence, not a wall.

## A note on what's not here

Week 13 is the *dependency-injection* week. It deliberately does **not** cover:

- **Koin or kotlin-inject.** Koin is a runtime service locator (resolution at runtime, not a compile-time graph); kotlin-inject and Metro are compile-time alternatives. Both are real choices, but Hilt is the Android default and the capstone target. We name them and move on; the trade-off discussion is one paragraph in lecture 1.
- **The actual networking and database implementations.** `:core-network` provides a `Retrofit`; *how* Retrofit works is Week 15. `:core-database` provides a `RoomDatabase`; *how* Room works is Week 14. This week the point is the **wiring**, not the things being wired — we provide stub or minimal real implementations and focus on the graph.
- **Testing with Hilt.** `@HiltAndroidTest`, `@BindValue`, `@UninstallModules`, and the `HiltAndroidRule` are how you swap a fake into the graph for a test. That is Week 17 (testing). This week we test the injectable *logic* with plain constructor injection — which is exactly the point: constructor injection makes a class testable *without* Hilt.

The point of Week 13 is narrow and deep: one graph, the annotations that declare it, the components and scopes that bound its lifetimes, and the generated Dagger code you read when it breaks.

## Up next

Continue to **Week 14 — Persistence: Room, DataStore, the file system** once you have shipped this week's mini-project and proven the four-module graph builds. Week 14 fills in the `:core-database` module you stubbed this week with a real Room database — and it will be `@Provides`-d into the exact same Hilt graph you built here. Week 15 does the same for `:core-network` with Retrofit, OkHttp, Ktor, and gRPC. Every week in Phase 3 assumes you can declare a binding and reason about its scope. Earn that this week — the rest of production engineering is built on top of the graph.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
