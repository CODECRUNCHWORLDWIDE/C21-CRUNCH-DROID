# Week 07 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 08. Answer key with explanations at the bottom — don't peek.

---

**Q1.** Which statement best describes a `@Composable` function?

- A) A constructor that builds a `View` object you hold and mutate.
- B) A description of UI for the current state, which the runtime re-invokes (partially, intelligently) when the state it reads changes.
- C) A coroutine that emits UI frames.
- D) An XML layout compiled to Kotlin.

---

**Q2.** What triggers recomposition of a particular composable?

- A) Any state change anywhere in the app.
- B) A read, inside that composable, of a `State` whose value changed.
- C) The parent recomposing, always.
- D) A call to `invalidate()` you make manually.

---

**Q3.** What are the three Compose phases, in order, and what does each produce?

- A) Draw, layout, composition — pixels, positions, the tree.
- B) Composition (the tree of what to show), layout (size + position of each node), draw (pixels).
- C) Measure, place, paint — all part of one phase.
- D) Inflate, bind, render.

---

**Q4.** You animate a value that moves a box's position 60 times a second. To avoid recomposing every frame, in which phase should you read the animating value?

- A) Composition — read it in the composable body.
- B) Layout — e.g. `Modifier.offset { IntOffset(...) }` (the lambda form).
- C) It must be read in composition; there is no choice.
- D) Draw, always, even for layout-affecting values.

---

**Q5.** Why is `List<T>` considered **unstable** by the Compose compiler?

- A) Lists are slow.
- B) `List` is an interface; the compiler can't prove the backing implementation isn't a `MutableList` upcast to `List`, so it can't trust equality to mean "unchanged."
- C) Lists can't be compared with `equals`.
- D) `List` isn't `Serializable`.

---

**Q6.** What does it mean for a composable to be **skippable**?

- A) It will never recompose.
- B) When all its parameters are equal to last time, the runtime can skip re-invoking its body.
- C) It can be skipped at compile time and removed from the binary.
- D) It runs only in release builds.

---

**Q7.** A `data class User(val id: String, var name: String)` is passed to a composable, which the report marks **not skippable**. What's the cause and the fix?

- A) Cause: `id` is a `String`. Fix: use an `Int`.
- B) Cause: the `var name` makes `User` unstable. Fix: make it `val name` and produce a new `User` via `copy()` when the name changes.
- C) Cause: it's a `data class`. Fix: make it a regular class.
- D) Cause: too many properties. Fix: split the class.

---

**Q8.** What does `remember { }` do?

- A) Makes a value observable so changes trigger recomposition.
- B) Computes its block once and returns the stored result on subsequent recompositions, until the composable leaves the composition.
- C) Saves a value across process death.
- D) Caches a value on disk.

---

**Q9.** In a manual loop emitting composables, `items` can reorder. Why wrap each in `key(item.id) { }`?

- A) For accessibility.
- B) So the runtime tracks each item by identity rather than loop position, moving remembered state on reorder instead of discarding it.
- C) To make the list scrollable.
- D) `key` is required syntax; the loop won't compile without it.

---

**Q10.** You mark a type `@Immutable` but later mutate one of its properties. What's the consequence?

- A) A compile error.
- B) Nothing; `@Immutable` is just documentation.
- C) A correctness bug: the runtime may skip recompositions it should have run, showing stale UI, because you lied to the compiler.
- D) The app crashes immediately.

---

**Q11.** Where do you turn on the Compose Compiler report, and what two files does it produce that you care about this week?

- A) In the manifest; it produces `AndroidManifest.txt` and `R.txt`.
- B) In a `composeCompiler { reportsDestination = ... }` block; it produces `composables.txt` (skippability per function) and `classes.txt` (stability per class).
- C) In `gradle.properties`; it produces `build.log`.
- D) You can't; the report is internal to Google.

---

**Q12.** Tapping a like button on one row of a `LazyColumn` recomposes *every visible row*, though only the one row's data changed. Most likely cause?

- A) `LazyColumn` always recomposes all rows.
- B) The row composable isn't skippable — typically an unstable parameter (a bare `List`, a `var` in the item, or a domain type the compiler can't inspect) — so the runtime can't prove the other rows are unchanged.
- C) The emulator is slow.
- D) You forgot `@Composable` on the row.

---

**Q13.** A once-per-second timer tick recomposes your entire screen. The ring should sweep smoothly without recomposing. What's the right design?

- A) Wrap the screen in `remember` so it doesn't recompose.
- B) Hold the fast-changing progress in its own `State<Float>` and read it only in the draw phase (`Canvas`/`drawBehind`), so advancing it invalidates only draw; derive the once-a-second text with `derivedStateOf`.
- C) Disable recomposition with a flag.
- D) Move the timer to a background thread; that stops recomposition.

---

## Answer key

**Q1 — B.** A composable is `UI = f(state)` — a description the runtime re-invokes, partially and intelligently, when read state changes. There is no `View` object you hold and mutate; that's the old world. (Lecture 1, §1–2.)

**Q2 — B.** Recomposition is triggered by a *read* of a changed `State` inside the composable. The read is the subscription — you don't manually invalidate, and not every state change touches every composable. (Lecture 1, §4.)

**Q3 — B.** Composition produces the tree of what to show; layout measures and places each node; draw paints. In that order, and each can read state independently. (Lecture 1, §6.)

**Q4 — B.** A layout-affecting value should be read in the layout phase via the lambda form (`Modifier.offset { ... }`), so composition is skipped and only layout + draw run per frame. Reading in composition (A) recomposes every frame — the canonical jank bug. (Lecture 1, §6.)

**Q5 — B.** `List` is an interface; the compiler can't prove the backing implementation is immutable, so it conservatively treats `List<T>` as unstable. Use `ImmutableList`/`PersistentList` to restore stability. (Lecture 2, §2, footgun 1.)

**Q6 — B.** Skippable means: if all parameters equal last time's, the runtime skips re-invoking the body. It does *not* mean "never recomposes" — a skippable composable still recomposes when its inputs genuinely change. (Lecture 2, §1.)

**Q7 — B.** One `var` makes the whole class unstable (a property can change without notifying composition), which de-skips every composable taking it. Fix: `val`, and update immutably via `copy()`. (Lecture 2, §2 + §4 footgun 2.)

**Q8 — B.** `remember` computes once and returns the stored value across recompositions, discarding it when the composable leaves. It does *not* make the value observable — that's `mutableStateOf`. You pair them. (Lecture 1, §5.)

**Q9 — B.** `key(item.id)` makes the runtime track each item by identity, so a reorder moves the remembered group (scroll, animation state) instead of throwing it away as positional memoization would. (Lecture 1, §3.)

**Q10 — C.** A false `@Immutable` is a correctness bug: the runtime trusts your promise and may skip recompositions it should have run, leaving stale UI. The annotations are load-bearing promises, not decorations. (Lecture 2, §2.)

**Q11 — B.** Turn it on with `composeCompiler { reportsDestination = ... }`; read `composables.txt` for per-function skippability and `classes.txt` for per-class stability (and which field broke it). Two-minute lookup instead of guessing. (Lecture 2, §3.)

**Q12 — B.** The row isn't skippable, so the runtime can't prove the unchanged rows are unchanged and re-invokes them all. The cause is almost always an unstable parameter — bare `List`, a `var` in the item, or a cross-module domain type. The report names it. (Lecture 2, §1 + §4.)

**Q13 — B.** Isolate the fast value in its own `State<Float>`, read it only in draw (so advancing it invalidates only draw), and use `derivedStateOf` for the once-a-second text. Three cadences, each in the right phase — the mini-project's whole design. (Lecture 1, §6; mini-project Milestones 1–4.)

---

*Score 11+? On to Week 08. Below 9? Re-read both lecture notes and re-run exercises 2 and 3 — the stability-and-skippability fix and the defer-read-to-draw move are the two ideas this week is graded on.*
