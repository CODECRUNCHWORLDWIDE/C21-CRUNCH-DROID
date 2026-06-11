# Week 10 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 11. Answer key with explanations at the bottom — don't peek.

---

**Q1.** Which statement best describes Navigation 3's core change versus the old Navigation-Compose?

- A) It added a faster `NavController` but kept string routes.
- B) A route is now a `@Serializable` Kotlin type, and the app owns the back stack as Compose state, which `NavDisplay` renders.
- C) It replaced Compose navigation with Fragments.
- D) It is the same as Navigation-Compose with a new package name.

---

**Q2.** Why are Navigation 3 route types annotated `@Serializable`?

- A) So they can be sent over the network.
- B) So the back stack can be saved and restored across process death by serializing each route.
- C) Because `NavKey` requires it for equality.
- D) It is optional and only for logging.

---

**Q3.** You have an argument-free screen and a screen that carries an `Int` id. Which declarations are idiomatic?

- A) `class Home` and `class Detail(var itemId: Int)`
- B) `@Serializable data object Home : Route` and `@Serializable data class Detail(val itemId: Int) : Route`
- C) `object Home` and `data class Detail(val itemId: String) : Route`
- D) Both as `@Serializable data class` with no properties.

---

**Q4.** In Navigation 3, how do you navigate forward to a detail with id 42?

- A) `navController.navigate("detail/42")`
- B) `backStack.add(Detail(itemId = 42))`
- C) `NavDisplay.push(Detail(42))`
- D) `entryProvider.navigate<Detail>(42)`

---

**Q5.** How does a `Detail` entry read its `itemId` argument?

- A) `entry.arguments?.getInt("itemId")`
- B) `savedStateHandle.get<Int>("itemId")`
- C) The `entry<Detail> { route -> route.itemId }` lambda receives the typed route instance.
- D) From a global `NavController.currentArgs`.

---

**Q6.** Where must you put a `backStack.add(...)` call?

- A) In the composable's body, so it runs on every recomposition.
- B) In an event callback (e.g. `onClick`) or an effect — never in the composable body, which would re-navigate every recomposition.
- C) Anywhere; Nav3 dedupes navigations automatically.
- D) Only inside `NavDisplay`.

---

**Q7.** For a bottom-bar app where each tab keeps its own drill-down history, the correct model is:

- A) One shared back stack; tabs push their roots onto it.
- B) One back stack per tab; switching tabs chooses which stack `NavDisplay` renders, preserving each tab's history.
- C) A `Fragment` per tab.
- D) No back stack; recompose the whole app on each tab change.

---

**Q8.** You model onboarding as `sealed interface Onboarding : Route` with three `data object` screens. How do you pop the whole flow when it finishes?

- A) Call `backStack.removeLastOrNull()` three times and hope you counted right.
- B) `backStack.removeAll { it is Onboarding }` — exhaustive over the sealed sub-family.
- C) `backStack.clear()`, losing everything beneath onboarding too.
- D) You can't; nested graphs aren't supported.

---

**Q9.** What should a deep link do beyond showing the target screen?

- A) Nothing; rendering the target is the whole job.
- B) Seed a back stack (e.g. `[CatalogRoot, ItemDetail(42)]`) so back from the deep-linked screen lands on a root instead of exiting from a blank history.
- C) Open the browser as a fallback.
- D) Clear the back stack so back always exits.

---

**Q10.** Why is `routeForUri(uri): NavKey?` written as a *pure, total* function?

- A) Purity is required by `kotlinx.serialization`.
- B) So it never crashes on bad input (missing/non-numeric id, unknown path) and can be unit-tested exhaustively on the JVM in milliseconds.
- C) So it can run on the main thread.
- D) Total functions are faster at runtime.

---

**Q11.** For predictive back to *animate* the cross-screen preview, which must be true?

- A) Only `enableOnBackInvokedCallback="true"` in the manifest.
- B) `enableOnBackInvokedCallback` on, `NavDisplay` owns the pop (you don't intercept back first), and `transitionSpec`/`popTransitionSpec` are defined.
- C) You must write a custom `BackHandler` that pops the stack.
- D) Nothing; it works automatically with no transitions defined.

---

**Q12.** You scope a `DetailViewModel` to its back-stack entry (via the ViewModel entry decorator). When is it cleared?

- A) On every recomposition.
- B) When the `ItemDetail` entry is popped off the back stack — `onCleared()` runs and `viewModelScope` is cancelled.
- C) When the Activity is destroyed only.
- D) Never; you must clear it by hand.

---

**Q13.** A Compose UI test for navigation flakes intermittently on the forward-navigation assertion. The most likely cause is:

- A) The emulator is too slow; nothing to do.
- B) Asserting before recomposition and the transition settle — missing `rule.waitForIdle()` after `performClick()`.
- C) `@Serializable` routes can't be tested.
- D) The back stack isn't a real list.

---

## Answer key

**Q1 — B.** Nav3's core change is the inversion: a route is a `@Serializable` type, the app owns the back stack as Compose state, and `NavDisplay` renders the top entry. It is not a faster controller (A), not Fragments (C), and not a rename (D). (Lecture 1, §2.)

**Q2 — B.** The back stack is saved across process death by serializing each route; `@Serializable` is what makes that possible. Not networking (A), not equality (`data class`/`object` give equality without it, C), not optional (forgetting it breaks save/restore, D). (Lecture 1, §3.)

**Q3 — B.** `data object` for no-argument screens, `data class` with a `val` property for screens that carry an argument, both `@Serializable` and implementing `Route`. `var` (A) and a `String` id where an `Int` was meant (C) are wrong idioms. (Lecture 1, §3.)

**Q4 — B.** You navigate by mutating the back stack you own: `backStack.add(Detail(itemId = 42))`. There is no string route (A) and no controller push API (C/D). (Lecture 1, §4.)

**Q5 — C.** The `entry<Detail> { route -> … }` lambda receives the typed route instance, so `route.itemId` is a checked property access — no `Bundle`, no key, no `SavedStateHandle` round-trip for an argument. (Lecture 1, §5–6.)

**Q6 — B.** Navigation is an event; `backStack.add(...)` goes in a callback or effect. In the composable body it re-navigates on every recomposition — an infinite loop. (Lecture 1, §4.)

**Q7 — B.** One back stack per tab; switching tabs selects which stack `NavDisplay` renders, so each tab's drill-down is preserved. A single shared stack (A) breaks per-tab history. (Lecture 2, §1.)

**Q8 — B.** `removeAll { it is Onboarding }` pops the entire flow exhaustively because `Onboarding` is a sealed sub-family — regardless of how deep the user got, and guaranteed to cover every onboarding screen. `clear()` (C) would also drop everything beneath. (Lecture 2, §2.)

**Q9 — B.** A deep link *builds a back stack*: seed `[root, target]` so back from the deep-linked screen lands on a root, not on a blank history that exits the app. (Lecture 2, §3.)

**Q10 — B.** Totality means it never throws on bad input (non-numeric id → `toIntOrNull` → null; unknown path → null), so the parser — the error-prone half of a deep link — is unit-testable exhaustively on the JVM. The launching half is verified once with `adb`. (Lecture 2, §3; exercise 03.)

**Q11 — B.** Three things: opt in (`enableOnBackInvokedCallback`), let `NavDisplay` own the pop (don't intercept with a raw `BackHandler`), and define `transitionSpec`/`popTransitionSpec` so the gesture has an animation to scrub. (Lecture 2, §4.)

**Q12 — B.** A ViewModel scoped to a back-stack entry is cleared when that entry is popped — `onCleared()` runs, cancelling `viewModelScope`. Its lifetime *is* the entry's lifetime; it survives recomposition and rotation in between. (Lecture 1, §7.)

**Q13 — B.** A typed `add` triggers recomposition and a transition animation; asserting before they settle flakes. `rule.waitForIdle()` after `performClick()`, and assert on the destination present / source absent. (Lecture 2, §5.)

---

*Score 11+? On to Week 11. Below 9? Re-read both lecture notes and re-run exercises 1 and 2 — routes-as-types and the app-owned back stack are the two ideas this week is graded on.*
