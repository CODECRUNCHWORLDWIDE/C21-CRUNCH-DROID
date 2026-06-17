# Week 13 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 14. Answer key with explanations at the bottom — don't peek.

---

**Q1.** Which statement best describes Hilt's relationship to Dagger in 2026?

- A) Hilt is a brand-new dependency injector that replaced Dagger; the two share no code.
- B) Hilt is an opinionated layer over Dagger — predefined components, predefined scopes, and generated Android entry points over the same compile-time graph engine.
- C) Dagger is a layer over Hilt; Hilt is the lower-level engine.
- D) They are unrelated frameworks that both happen to do DI.

---

**Q2.** Why is constructor injection preferred over field injection wherever you can use it?

- A) Field injection is deprecated.
- B) Constructor injection makes dependencies explicit and final, removes the `lateinit` null window, and makes the class testable without Hilt at all.
- C) Field injection only works on the JVM.
- D) Constructor injection is faster at runtime.

---

**Q3.** You have `interface FeedRepository` and `class FeedRepositoryImpl @Inject constructor(...)`. What is the idiomatic way to tell the graph "FeedRepository means FeedRepositoryImpl"?

- A) `@Provides` a function that returns `FeedRepositoryImpl()`.
- B) `@Binds` an abstract function `bind(impl: FeedRepositoryImpl): FeedRepository`.
- C) Annotate the interface with `@Inject`.
- D) Nothing — Dagger infers it automatically.

---

**Q4.** A `@Module` provides an `OkHttpClient` you want shared app-wide. Which component should it install into, and which scope should the binding carry?

- A) `ActivityComponent`, `@ActivityScoped`.
- B) `ViewModelComponent`, `@ViewModelScoped`.
- C) `SingletonComponent`, `@Singleton`.
- D) No component; unscoped is required for clients.

---

**Q5.** Your build fails with `[Dagger/DuplicateBindings] okhttp3.OkHttpClient is bound multiple times`. You genuinely need two — an authenticated and a public client. What is the fix?

- A) Delete one of them.
- B) Add a `@Qualifier` annotation to each provider and each injection site so the two become distinct binding keys.
- C) Make both `@Singleton`.
- D) Move one into a different module.

---

**Q6.** What does `@AndroidEntryPoint` on an Activity generate and do?

- A) Nothing; it's a marker.
- B) It generates `Hilt_<Activity>`, a base class that asks the component for the `@Inject`-field bindings and sets them before your `onCreate` body runs.
- C) It creates a new `SingletonComponent` per Activity.
- D) It makes the Activity a `@HiltViewModel`.

---

**Q7.** Why is making *every* binding `@Singleton` "to be safe" a bad reflex?

- A) It slows the build.
- B) A `@Singleton` lives for the entire process and is never GC'd; if it holds an Activity context or a large cache, that leaks for the life of the app.
- C) `@Singleton` bindings can't be injected into ViewModels.
- D) It's fine; always use `@Singleton`.

---

**Q8.** You need an object that has an injected `ItemRepository` *and* a runtime `itemId` known only at the call site. What is the right tool?

- A) Make `itemId` a `var` and set it after construction.
- B) Assisted injection: `@AssistedInject` constructor with `@Assisted itemId`, plus an `@AssistedFactory` interface you inject and call `create(itemId)` on.
- C) `@Provides` a function that takes `itemId`.
- D) A `@Singleton` holding a mutable `itemId`.

---

**Q9.** In a multi-module app, `AuthRepositoryImpl` in `:feature-auth` has a constructor parameter `AuthApi`, which is provided in `:core-network`. `:feature-auth` does *not* import `NetworkModule`. Why does it still resolve?

- A) It doesn't; you must import every module explicitly.
- B) Both modules install into `SingletonComponent`, and Hilt aggregates every `@InstallIn(SingletonComponent::class)` module across the whole build into the one generated component, so the binding is visible.
- C) `:app` manually passes the binding down.
- D) Hilt copies the binding into `:feature-auth` at build time.

---

**Q10.** A `@Singleton` `@Provides` lives in a `@InstallIn(ActivityComponent::class)` module. What happens?

- A) It works; the singleton is created per Activity.
- B) It's a compile error — a scope must match its component, and `@Singleton`'s lifetime contradicts `ActivityComponent`'s.
- C) It works but leaks.
- D) The scope is ignored.

---

**Q11.** You need a graph-provided `Analytics` inside a `ContentProvider`, which Hilt cannot annotate with `@AndroidEntryPoint`. What's the mechanism?

- A) You can't; `ContentProvider`s can't use the graph.
- B) Define an `@EntryPoint` interface installed in a component and retrieve it with `EntryPointAccessors`.
- C) Make `Analytics` a global `object`.
- D) Inject it into the `Application` and read it statically.

---

**Q12.** A `MissingBinding` error prints a chain: `FeedApi is injected at FeedRepositoryImpl(api) ... FeedRepository is injected at FeedViewModel(repository)`. How do you read it, and what does it tell you?

- A) Top-down; the first line is the bug.
- B) Bottom-up: `FeedViewModel` wants a `FeedRepository`, which is `FeedRepositoryImpl`, which needs a `FeedApi` — and `FeedApi` has no binding. Provide or `@Binds` `FeedApi`.
- C) It's random; just add `@Provides` until it compiles.
- D) It means `FeedViewModel` is missing `@HiltViewModel`.

---

**Q13.** Why does a `RoomDatabase` need to be `@Singleton`, while the DAO derived from it can be unscoped?

- A) DAOs can't be scoped.
- B) The database is expensive and must be a single shared connection to the SQLite file; the DAO is a cheap accessor derived from it, so re-creating it costs nothing and scoping buys nothing.
- C) Room requires it; there's no reason.
- D) It's the opposite — the DAO must be `@Singleton`.

---

## Answer key

**Q1 — B.** Hilt is an opinionated overlay on Dagger: it generates the components every Android app needs, predefined scopes, and the Android entry points, over the same compile-time graph engine. The errors you'll debug are Dagger's. (Lecture 1, §1–2.)

**Q2 — B.** Constructor injection makes dependencies explicit and `val`, removes the `lateinit` null window, and — critically — makes the class constructable in a unit test with plain fakes, no Hilt. Field injection is the fallback only for framework-constructed types. (Lecture 1, §3.)

**Q3 — B.** `@Binds` is the idiomatic, cheaper way to point an interface key at an injectable implementation — it generates no extra factory, just a cast. `@Provides` returning `FeedRepositoryImpl()` works but is wasteful. (Lecture 1, §4.)

**Q4 — C.** App-wide shared infrastructure installs in `SingletonComponent` with `@Singleton`. `OkHttpClient` in particular *must* be a singleton for connection-pool reuse — a correctness requirement, not just performance. (Lecture 1, §5; lecture 2, §1.)

**Q5 — B.** The graph keys bindings by type; two of the same type are ambiguous. A `@Qualifier` annotation on each makes them distinct keys (`@AuthClient OkHttpClient` vs `@PublicClient OkHttpClient`). (Lecture 2, §2.)

**Q6 — B.** `@AndroidEntryPoint` generates the `Hilt_<Activity>` base that performs field injection (asking the component for each `@Inject` field) before your `onCreate` runs. That's why reading the field after `super.onCreate` is safe. (Lecture 1, §6.)

**Q7 — B.** A `@Singleton` lives for the whole process and is never collected. Over-scoping something that holds an Activity context or a big cache leaks it for the app's life. Scope to the shortest lifetime that's genuinely needed. (Lecture 2, §1.)

**Q8 — B.** Assisted injection is exactly the "graph deps + call-site value" tool: `@AssistedInject` + `@Assisted` + `@AssistedFactory`. You inject the *factory* and call `create(itemId)`. Setting a `var` after construction creates an invalid window. (Lecture 2, §3.)

**Q9 — B.** Hilt aggregates every `@InstallIn(SingletonComponent::class)` module across the *entire* build into the one generated `SingletonComponent`, so a binding provided in `:core-network` is visible to an injection site in `:feature-auth` without an explicit import. (Lecture 2, §5.)

**Q10 — B.** A scope must match its component. `@Singleton` (Application lifetime) in an `ActivityComponent` (Activity lifetime) is a contradiction the compiler rejects with `IncompatiblyScopedBindings`. (Lecture 2, §1, §6.)

**Q11 — B.** `@EntryPoint` + `EntryPointAccessors` is the escape hatch for reaching the graph from a type Hilt can't own (a `ContentProvider`, a non-Hilt callback). It's the deliberate boundary mechanism, not the default. (Lecture 2, §4.)

**Q12 — B.** Read the chain bottom-up: it names the exact injection path down to the unresolved edge (`FeedApi`). The fix is to provide or `@Binds` that one type. The error hands you the dependency chain; you don't guess. (Lecture 2, §6.)

**Q13 — B.** The `RoomDatabase` must be a single shared connection (multiple instances mean multiple connections to one file). The DAO is a thin accessor derived from it — cheap to re-create, so scoping it buys nothing. Scope the expensive shared thing; leave the cheap derived thing unscoped. (Lecture 2, §1, §5.)

---

*Score 11+? On to Week 14. Below 9? Re-read both lecture notes and re-run exercises 1 and 2 — the "Hilt is Dagger underneath" framing and the modules/scopes/qualifiers mechanics are the two ideas this week is graded on.*
