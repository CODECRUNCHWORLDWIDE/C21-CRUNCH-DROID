# Week 04 — Resources

Every primary resource on this page is **free**. The Kotlin language documentation and the `kotlinx.coroutines` guide are free and open. The KEEP proposals are public on GitHub. Roman Elizarov's structured-concurrency essays are free on Medium. A couple of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **Kotlin coroutines — official guide.** Read "Coroutine basics," "Cancellation and timeouts," "Composing suspending functions," and "Coroutine context and dispatchers" before Wednesday:
  <https://kotlinlang.org/docs/coroutines-guide.html>
- **"Coroutines: basics."** The mental model and the first builders:
  <https://kotlinlang.org/docs/coroutines-basics.html>
- **"Cancellation and timeouts."** The cooperative-cancellation contract — central to lecture 02:
  <https://kotlinlang.org/docs/cancellation-and-timeouts.html>
- **"Coroutine context and dispatchers."** `Dispatchers.Main/IO/Default/Unconfined`, `withContext`, `CoroutineName`:
  <https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html>
- **"Coroutine exceptions handling."** Propagation rules, `CoroutineExceptionHandler`, `SupervisorJob` — read before Thursday:
  <https://kotlinlang.org/docs/exception-handling.html>

## The structured-concurrency canon (why this matters)

Structured concurrency is the idea the whole week hangs on. These three essays by the lead designer of Kotlin coroutines are the clearest writing on it anywhere:

- **Roman Elizarov — "Structured concurrency."** The founding essay; why scopes and parent-child trees:
  <https://elizarov.medium.com/structured-concurrency-722d765aa952>
- **Roman Elizarov — "Coroutine context and scope."** The distinction between context and scope that trips everyone up:
  <https://elizarov.medium.com/coroutine-context-and-scope-c8b255d59055>
- **Roman Elizarov — "The reason to avoid GlobalScope."** The leak this week's "never leak a coroutine" promise is about:
  <https://elizarov.medium.com/the-reason-to-avoid-globalscope-835337445abc>

## The API reference (skim, don't memorize)

- **`kotlinx.coroutines` API docs (root):** <https://kotlinlang.org/api/kotlinx.coroutines/>
- **`CoroutineScope`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-scope/>
- **`Job`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-job/>
- **`coroutineScope` / `supervisorScope`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/coroutine-scope.html> and <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/supervisor-scope.html>
- **`launch` / `async`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/launch.html> and <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/async.html>
- **`withContext`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html>
- **`Semaphore` / `limitedParallelism`:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-semaphore/>
- **`Continuation` (stdlib):** <https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.coroutines/-continuation/>

## The design documents (the "why" underneath)

- **KEEP — Coroutines design proposal.** The original language-design document; the CPS transform and the `Continuation` are specified here:
  <https://github.com/Kotlin/KEEP/blob/master/proposals/coroutines.md>
- **"Coroutines under the hood" (the suspend-to-state-machine transform).** Pair this with disassembling your own `suspend` function in exercise 01:
  <https://kotlinlang.org/spec/asynchronous-programming-with-coroutines.html>

## Talks (free, watch in this order)

- **Roman Elizarov — "Structured concurrency" (KotlinConf).** The canonical talk; the live demos of scope cancellation are exactly this week's model:
  <https://www.youtube.com/watch?v=Mj5P47F6nJg>
- **Roman Elizarov — "Deep dive into Coroutines on JVM" (KotlinConf).** The state-machine transform with bytecode; watch after exercise 01:
  <https://www.youtube.com/watch?v=YrrUCSi72E8>
- **Manuel Vivo — "Coroutines and Flow on Android" (Android Dev Summit).** The Android-scope payoff you collect in Phase 2; useful preview:
  <https://www.youtube.com/results?search_query=manuel+vivo+coroutines+android+dev+summit>

## Cancellation and exceptions — the deep cuts

- **"Cancellation in coroutines" (Android Developers, three-part series by Manuel Vivo).** The clearest treatment of cooperative cancellation and the `CancellationException` rule:
  <https://medium.com/androiddevelopers/cancellation-in-coroutines-aa6b90163629>
- **"Exceptions in coroutines" (Android Developers).** `launch` vs `async` propagation and where `CoroutineExceptionHandler` fires:
  <https://medium.com/androiddevelopers/exceptions-in-coroutines-ce8da1ec060c>
- **"Coroutines: first things first" and "Patterns for work that shouldn't be cancelled" (Android Developers):**
  <https://medium.com/androiddevelopers/coroutines-first-things-first-e6187bf3bb21>

## Testing coroutines

- **`kotlinx-coroutines-test` guide.** `runTest`, `TestScope`, `StandardTestDispatcher` vs `UnconfinedTestDispatcher`, virtual time. You use `runTest` in every exercise and the mini-project:
  <https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-test/README.md>
- **`runTest` API:** <https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/kotlinx.coroutines.test/run-test.html>

## Open-source code to read this week

You learn more from one hour reading real coroutine code than three hours of tutorials. Pick one and trace how it owns its scopes:

- **`Kotlin/kotlinx.coroutines`** — the library itself. Read `Builders.common.kt` (`launch`, `async`), `CoroutineScope.kt`, and `JobSupport.kt` for the parent-child machinery:
  <https://github.com/Kotlin/kotlinx.coroutines>
- **`android/nowinandroid`** — the reference Android app. Search for `viewModelScope`, `coroutineScope`, and `SupervisorJob` to see structured concurrency in a production codebase (you will revisit this app all through Phase 2 and 3):
  <https://github.com/android/nowinandroid>
- **`square/retrofit`** — the `suspend`-function adapter (`KotlinExtensions.kt`) shows how a callback API is bridged to a suspend function with `suspendCancellableCoroutine`; you do the same bridge yourself next week:
  <https://github.com/square/retrofit>

## Tools you'll use this week

- **JDK 21 + `javap`.** `javap -c -p` disassembles your compiled `suspend` function so you can read the state machine. Build with Gradle, then `javap` the class in `build/classes/kotlin/main/...`.
- **Gradle Kotlin DSL.** Add `org.jetbrains.kotlinx:kotlinx-coroutines-core` (main) and `kotlinx-coroutines-test` (test) via `libs.versions.toml`. The application plugin runs your `main`.
- **IntelliJ IDEA / Android Studio coroutine debugger.** The debugger shows coroutine state and the dispatcher; enable "Kotlin coroutine debugger" in run config. Useful for the cancellation exercises.

## Free books (chapter-level, not whole books)

- **"Kotlin Coroutines by Tutorials" sample chapters** (raywenderlich/Kodeco) — the free chapters cover builders and cancellation; the structured-concurrency chapter is the relevant one.
- **The official guide (above) is effectively a free book** — the eight articles in the "Coroutines" section read end to end are a complete primer.

## Paid books (optional, clearly marked)

- **"Kotlin Coroutines: Deep Dive" — Marcin Moskała** (paid). The most thorough coroutines book in 2026; the chapters on the CPS transform, cancellation, and exception handling are the clearest in print and map directly onto this week.

---

*If a link 404s, please open an issue so we can replace it.*
