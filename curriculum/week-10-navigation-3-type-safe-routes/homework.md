# Week 10 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 10 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Android 15 (`compileSdk 35`), `minSdk 24`, Kotlin 2.0+ with the Compose Compiler plugin and `kotlin("plugin.serialization")`, Navigation 3 artifacts. Every problem must build with **0 warnings**.

---

## Problem 1 — Grep your own graph for string routes

**Problem statement.** Take your mini-project (or exercise 1) and prove there is not a single string route or `Bundle` read in it. Run the two greps below, paste the (empty) output into `notes/no-strings.md`, and write two sentences: one naming the bug class string routes invite, one naming what replaced each (route string → `@Serializable` type; `Bundle` key → typed property).

**Acceptance criteria.**

- `notes/no-strings.md` exists with the grep commands, their empty output, and the two sentences.
- `grep -rn 'navigate("' app/src` and `grep -rn 'arguments?.get' app/src` both return nothing.
- Committed.

**Hint.** If a grep *does* find a hit, that's the homework working — go convert that call site to `backStack.add(SomeRoute(...))` or `route.someProperty` and re-run until both are empty.

**Estimated time.** 25 minutes.

---

## Problem 2 — Three back-stack operations, three behaviours

**Problem statement.** In a JVM/Robolectric test, build a `SnapshotStateList<NavKey>` and write three clearly-separated assertions: (a) `add` pushes and the top is the new route; (b) `removeLastOrNull` on a one-element stack returns the element and empties it; (c) `removeAll { it is Onboarding }` pops a contiguous onboarding run while leaving the screens beneath it. One comment per case explaining when that operation is the right tool in a real app.

**Acceptance criteria.**

- Three passing assertions covering push, pop, and pop-a-sealed-sub-family.
- Each has a one-line justification.
- 0 warnings. Committed.

**Hint.** Use `mutableStateListOf<NavKey>(...)` to construct the stack. For (c), seed `[HomeRoot, Welcome, Permissions]` and assert the result is exactly `[HomeRoot]`. The `it is Onboarding` test only compiles if `Onboarding` is a sealed interface your onboarding routes implement.

**Estimated time.** 45 minutes.

---

## Problem 3 — Dynamic argument round-trip across process death

**Problem statement.** Add a route `@Serializable data class Search(val query: String, val page: Int = 0)`. Navigate to it with a non-default query and page, then write a test (or a manual experiment with "Don't keep activities") that confirms the route round-trips through serialization — the restored route has the same `query` and `page`. Record in `notes/serialization-roundtrip.md` what would break if you forgot `@Serializable`.

**Acceptance criteria.**

- A `Search` route with a defaulted `page` argument; navigation passes a non-default value.
- A test serializes/deserializes the route (via `kotlinx.serialization`'s `Json`) and asserts equality, OR a documented "Don't keep activities" experiment showing the restored stack.
- `notes/serialization-roundtrip.md` states the failure mode of a missing `@Serializable`.
- 0 warnings. Committed.

**Hint.** `Json.encodeToString(Search("kotlin", 2))` then `Json.decodeFromString<Search>(...)` and assert equality — that's the same machinery Nav3 uses to save the back stack. The default on `page` makes `Search(query = "kotlin")` valid; confirm the default survives too.

**Estimated time.** 40 minutes.

---

## Problem 4 — Guarded back behaviour for tab roots

**Problem statement.** Implement and test the `onBack` policy from lecture 2, §1: within a tab, back pops; at a non-Home tab root, back switches to the Home tab; at the Home root, back is not consumed (system exits). Drive it with assertions on the back stack and the selected tab (no UI needed — model `selectedTab` and the active stack as plain state).

**Acceptance criteria.**

- A testable `onBack` function (or lambda) implementing all three branches.
- Three assertions: deep-in-a-tab pops; at a non-Home root switches to Home; at Home root leaves state unchanged (and signals "not consumed").
- 0 warnings. Committed.

**Hint.** Make `onBack` return a `Boolean` ("did I consume it?") so the Home-root case can return `false` and you can assert on it. The other two return `true`. This mirrors how `OnBackPressedCallback.isEnabled` decides whether the system handles back.

**Estimated time.** 45 minutes.

---

## Problem 5 — A deep-link parser with an extra route and a malformed-input gauntlet

**Problem statement.** Extend exercise 03's `routeForUri` with a third route — `@Serializable data class Search(val query: String)` from `catalog://search?q=kotlin` and `https://catalog.crunch.dev/search?q=kotlin` (read the query parameter, not a path segment). Then write a "gauntlet" test of malformed inputs (missing `q`, empty `q`, wrong scheme, unknown path) and assert every one returns null without throwing.

**Acceptance criteria.**

- `routeForUri` handles `search` via the `q` query parameter, in both scheme shapes.
- A gauntlet test of at least four malformed inputs, each asserted to return null (never throw).
- `routeForUri` stays **total** — no `!!`, no bare `toInt()`.
- 0 warnings. Committed.

**Hint.** `uri.getQueryParameter("q")` reads the query param; `?.takeIf { it.isNotBlank() } ?: return null` keeps it total. Test `catalog://search` (no `q`), `catalog://search?q=` (empty), `mailto:x` (wrong scheme), and `catalog://nope/x` (unknown) all map to null.

**Estimated time.** 45 minutes.

---

## Problem 6 — A ViewModel scoped to a back-stack entry

**Problem statement.** Give a `Detail` entry a `DetailViewModel(itemId)` scoped to the entry (via the ViewModel entry decorator), holding a `StateFlow<DetailUiState>` that starts `Loading` and flips to `Loaded`. Write a Compose UI test that navigates in, asserts the loaded content appears, navigates away (pops the entry), and — by navigating back in with a *different* id — confirms a *fresh* ViewModel is created (the new id's data shows, not the old).

**Acceptance criteria.**

- `DetailViewModel` scoped to the entry via `viewModel { DetailViewModel(route.itemId) }`, seeded by the route id.
- A UI test: navigate to `Detail(1)`, assert its content; pop; navigate to `Detail(2)`, assert *its* content (proving a per-entry ViewModel, not a shared one).
- The ViewModel entry decorator is installed on `NavDisplay`.
- Builds with **0 warnings**, including any concurrency warnings from `viewModelScope`. Committed.

**Hint.** Install `rememberViewModelStoreNavEntryDecorator()` in `entryDecorators`. Seed two distinct catalog items so `Detail(1)` and `Detail(2)` show distinguishable text. Use `collectAsStateWithLifecycle()` to read the flow in the composable. If both navigations show the same data, your ViewModel is being shared — confirm it's scoped to the *entry*, not the Activity.

**Estimated time.** 50 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, code is idiomatic Kotlin/Compose/Nav3, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. a `var` route property, an unnecessary `BackHandler`, a missing default on an optional argument). |
| 3 | Works, but misses one criterion (e.g. deep-link parser not total, ViewModel shared instead of per-entry, back policy missing the Home-root case). |
| 2 | Compiles and partially works; a core idea is wrong (a string route left in, an argument read from a `Bundle`, navigation triggered from the composable body). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for any string route (`navigate("…")`) or `Bundle` argument read (`arguments?.get…`) left in code that should be typed; **−2** for a `routeForUri` that can throw on malformed input (not total); **−1** for navigating from a composable body instead of an event callback.

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — routes-as-types (problems 1, 3) and the app-owned back stack (problems 2, 4) — so re-run exercises 01 and 02 before resubmitting.
