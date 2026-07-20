# Week 07 — Resources

Every primary resource on this page is **free**. Android's developer documentation is free. The AndroidX source is public on the Android Code Search and on GitHub mirrors. The conference talks are free on YouTube. A couple of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"Thinking in Compose."** The framing document for the whole mental shift — declarative UI, recomposition, why your composable is a description not a constructor. Read this before you write a single `@Composable`:
  <https://developer.android.com/develop/ui/compose/mental-model>
- **"Jetpack Compose phases."** The canonical three-phases article — composition, layout, draw — with the diagram you should be able to redraw from memory by Friday:
  <https://developer.android.com/develop/ui/compose/phases>
- **"State and Jetpack Compose."** Read the first half this week (what `State` is, recomposition triggered by reads); the side-effect half is Week 08:
  <https://developer.android.com/develop/ui/compose/state>
- **"Lifecycle of composables."** Enters/leaves the composition, recomposition, the slot table, `key()` and positional memoization:
  <https://developer.android.com/develop/ui/compose/lifecycle>
- **"Stability in Compose."** The stability and skippability story — `@Stable`, `@Immutable`, why `List` is unstable, stability inference — central to lecture 2:
  <https://developer.android.com/develop/ui/compose/performance/stability>

## Performance, the Compiler report, and diagnosis

- **"Compose performance — overview."** The performance landing page; recomposition costs, deferred reads, the diagnosis workflow:
  <https://developer.android.com/develop/ui/compose/performance>
- **"Diagnose stability issues with the Compose Compiler report."** Turning on `reportsDestination`/`metricsDestination`, reading `composables.txt` and `classes.txt`:
  <https://developer.android.com/develop/ui/compose/performance/stability/diagnose>
- **"Fix stability issues."** The practical-fix companion to the diagnosis page — immutable collections, `@Immutable`, hoisting:
  <https://developer.android.com/develop/ui/compose/performance/stability/fix>
- **The Compose Compiler Gradle plugin docs** (since Kotlin 2.0 the compiler is the `org.jetbrains.kotlin.plugin.compose` plugin, configured with a `composeCompiler { }` block):
  <https://developer.android.com/develop/ui/compose/compiler>
- **Layout Inspector — recomposition counts.** Android Studio's tool for watching which composables recompose and how often:
  <https://developer.android.com/studio/debug/layout-inspector>

## The runtime, read at the source

You will not write `Composer` code this week, but reading a little of the runtime makes the slot table and recomposition concrete. Use Android Code Search:

- **`androidx.compose.runtime`** — `Composer`, `Composition`, `RecomposeScope`, the snapshot system:
  <https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/>
- **`Recomposer.kt`** — the engine that schedules and applies recomposition:
  <https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/Recomposer.kt>
- **`SnapshotState.kt`** — `mutableStateOf` and the `MutableState` interface (full treatment Week 08, but skim it now):
  <https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/runtime/runtime/src/commonMain/kotlin/androidx/compose/runtime/SnapshotState.kt>

## Talks (free, watch in this order)

- **"Understanding Compose"** (Leland Richardson & Adam Powell, Android Dev Summit) — the positional-memoization and slot-table talk; the single best explanation of how the runtime works:
  <https://www.youtube.com/watch?v=Q9MtlmmN4Q0>
- **"Compose by example"** (ADS) — how the pieces fit together in real screens:
  <https://www.youtube.com/watch?v=DDd6IOlH3io>
- **"Composing an app, behind the scenes"** — the compiler-plugin transform: what `@Composable` becomes:
  <https://www.youtube.com/watch?v=6BRlI5zfCCk>
- **"Practical Compose performance" / "Compose performance"** (recent Google I/O performance sessions) — stability, the Compiler report, deferred reads in practice. Search the current year's I/O Android playlist for the latest.

## The compiler transform (deep, optional)

If you want to understand *why* a composable is skippable rather than just *that* it is, read about the compiler plugin's transform:

- **"Under the hood of Jetpack Compose"** (Leland Richardson, two-part write-up) — the positional-memoization compiler transform explained:
  <https://medium.com/androiddevelopers/under-the-hood-of-jetpack-compose-part-2-of-2-37b2c20c6cdd>
- **The Compose compiler design docs** in the AndroidX repo (`compose/compiler/design/`) — the actual specification of skippability and the comparison-propagation it generates.

## Community writing (current, opinionated, correct)

- **Zach Klippenstein's blog** — the deepest independent writing on the Compose runtime, snapshots, and subcomposition; an Android team engineer who writes the long-form pieces:
  <https://blog.zachklipp.com/>
- **Jorge Castillo — "Jetpack Compose internals."** The book/site that traces the compiler transform and runtime in depth (the early chapters are free):
  <https://jorgecastillo.dev/book/>
- **Chris Banes' blog** — practical Compose performance and accessibility from a former Android team engineer:
  <https://chrisbanes.me/>
- **Android Developers Medium publication** — the official long-form articles; filter for the Compose performance series:
  <https://medium.com/androiddevelopers>

## Open-source projects to read this week

You learn more from one hour reading a real Compose codebase than three hours of tutorials. Pick one and read how they structure composables and where they hoist state:

- **`android/nowinandroid`** — Google's reference app; the architecture reference for the whole track, but this week just read how screens are decomposed into stateful/stateless pairs:
  <https://github.com/android/nowinandroid>
- **`android/compose-samples`** — Jetsnack, Jetchat, Crane, Owl; small, focused, idiomatic. Jetsnack in particular shows custom layout and deferred reads:
  <https://github.com/android/compose-samples>
- **`chrisbanes/tivi`** — a real, large Compose app with a Compose Compiler report wired into CI; read its `composeCompiler` configuration:
  <https://github.com/chrisbanes/tivi>

## Tools you'll use this week

- **Android Studio Ladybug (2024.2)+** — `Help ▸ About` to confirm. The Layout Inspector (`View ▸ Tool Windows ▸ Layout Inspector`) shows recomposition counts; enable "Show Recomposition Counts."
- **The Compose Compiler Gradle plugin** — add `org.jetbrains.kotlin.plugin.compose` to your version catalog and a `composeCompiler { reportsDestination = layout.buildDirectory.dir("compose_compiler") }` block to generate the report.
- **`./gradlew :app:assembleRelease`** (or a debug build with the metrics flag) — generates `composables.txt` and `classes.txt` under your `reportsDestination`. Read them with any text editor.
- **The emulator** — a Pixel 8 API 35 image is the reference device for this week. `Tools ▸ Device Manager` to create one.

## Free books (chapter-level, not whole books)

- **"Jetpack Compose internals"** (Jorge Castillo) — the first chapters, covering the compiler transform and the runtime, are free on the site above. The best free deep dive on *why* recomposition is intelligent.
- **Android's "Compose pathways" and codelabs** — the "Jetpack Compose basics," "Basic layouts," and "State in Jetpack Compose" codelabs are effectively a free guided book:
  <https://developer.android.com/courses/jetpack-compose/course>

## Paid books (optional, clearly marked)

- **"Jetpack Compose internals" — Jorge Castillo** (paid for the full book). The definitive runtime-level treatment; worth it if you intend to write Compose libraries or debug it for a living.
- **"Programming Android with Kotlin" — Pierre-Olivier Laurence et al. (O'Reilly)** (paid). Broader than Compose, but the Compose and coroutines chapters are solid and current.

---

*If a link 404s, please open an issue so we can replace it.*
