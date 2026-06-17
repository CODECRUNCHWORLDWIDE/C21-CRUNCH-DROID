# Week 19 — Resources

Every primary resource on this page is **free**. Kotlin Multiplatform, Ktor, kotlinx-serialization/coroutines/datetime, and Compose for Wear OS are all open source. The conference talks are free on YouTube. A couple of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"Kotlin Multiplatform — get started."** The framing document: what KMP shares (the business layer), the source-set model, and the `commonMain`/platform structure. Read this before you write a single shared line:
  <https://www.jetbrains.com/help/kotlin-multiplatform/get-started.html>
- **"Multiplatform programming — share code on platforms."** The conceptual guide to `commonMain`, `expect`/`actual`, and what belongs where:
  <https://kotlinlang.org/docs/multiplatform.html>
- **"Use platform-specific APIs (`expect`/`actual`)."** The canonical guide to declaring a common API and providing platform implementations:
  <https://kotlinlang.org/docs/multiplatform-expect-actual.html>
- **"Compose for Wear OS — get started."** The Wear `Scaffold`, `TimeText`, `ScalingLazyColumn`, and the Wear Material components:
  <https://developer.android.com/training/wearables/compose>
- **"Wear OS principles."** What makes a watch app a watch app — glanceability, the round screen, input, surfaces. Read this so you don't ship a shrunken phone app:
  <https://developer.android.com/training/wearables/design>

## The KMP-friendly libraries, at the source

- **Ktor Client** — the multiplatform HTTP client (the KMP-friendly alternative to Retrofit). Read "Create a client" and the kotlinx-serialization content negotiation section:
  <https://ktor.io/docs/client-create-new-application.html>
- **kotlinx-serialization** — multiplatform JSON (and more). The same library you used in Week 15, now shared:
  <https://github.com/Kotlin/kotlinx.serialization>
- **kotlinx-coroutines** — multiplatform coroutines and Flow; the concurrency you already know, available in `commonMain`:
  <https://github.com/Kotlin/kotlinx.coroutines>
- **kotlinx-datetime** — multiplatform date/time (the `java.time` replacement that compiles for iOS):
  <https://github.com/Kotlin/kotlinx-datetime>

## Compose Multiplatform and the readiness question

- **"Compose Multiplatform" — JetBrains.** The overview of Compose UI shared across Android/desktop/iOS/web, and the current stability picture per target:
  <https://www.jetbrains.com/compose-multiplatform/>
- **"Compose Multiplatform iOS" status** — read this to form an honest judgment on when shared UI is production-ready vs. when native-UI-per-platform is the safer bet today:
  <https://www.jetbrains.com/help/kotlin-multiplatform/compose-multiplatform.html>

## Wear OS — surfaces and deeper concepts (Week 20 goes deep; skim now)

- **"Tiles" — Wear OS.** The glanceable swipeable surface that is *not* an activity:
  <https://developer.android.com/training/wearables/tiles>
- **"Complications."** Data your app provides to a watch face:
  <https://developer.android.com/training/wearables/complications>
- **"Ongoing activities."** A persistent surface for active tasks (a workout, a timer):
  <https://developer.android.com/training/wearables/ongoing-activity>

## Read a real KMP / Wear codebase this week

You learn more from one hour reading a real shared module than three hours of docs:

- **`Kotlin/kmm-production-sample` (KaMPKit / "ToDo" / the JetBrains samples)** — a production-shaped KMP app sharing a business core between Android and iOS; read how `commonMain` is structured and what stays platform-specific:
  <https://github.com/Kotlin/kmm-production-sample>
- **`android/wear-os-samples`** — Google's Wear OS samples: a Compose Wear app, a tile sample, a complication sample. The reference for this week's Wear screen and next week's surfaces:
  <https://github.com/android/wear-os-samples>
- **`touchlab/KaMPKit`** — Touchlab's opinionated KMP starter; the canonical "how a real team structures a shared core" reference:
  <https://github.com/touchlab/KaMPKit>

## Talks (free, watch in this order)

- **"Kotlin Multiplatform — share the business logic, not the UI"** (KotlinConf) — the discipline of what to share, demonstrated.
- **"Ktor for multiplatform networking"** (KotlinConf / Android Dev Summit) — the shared repository pattern.
- **"Building for Wear OS with Compose"** (Android Dev Summit) — the Wear `Scaffold`, `ScalingLazyColumn`, and glanceable surfaces.
- **"Compose Multiplatform — where it is and where it's going"** — the honest readiness talk; search the current year's KotlinConf playlist.

## Tools you'll use this week

- **Android Studio Ladybug (2024.2)+** with the **Kotlin Multiplatform plugin** — for the KMP module wizard and the multiplatform Gradle support.
- **`./gradlew :shared-core:compileKotlinIosSimulatorArm64`** (or the relevant iOS target) — *compiles* the iOS target to prove portability; runs on any OS, no Xcode needed to compile (running a simulator needs macOS).
- **`./gradlew :shared-core:assemble` / `:androidApp:assembleDebug`** — builds the Android side that consumes the shared core.
- **A Wear OS emulator** — `Tools ▸ Device Manager ▸ Create Device ▸ Wear OS` (a round Wear API 34 image is the reference) for the Wear forecast screen.
- **Xcode + the iOS simulator (macOS only, optional)** — to actually *run* the iOS target as a stretch goal. Not required; compiling the target is the week's requirement.

## Free books and codelabs

- **"Kotlin Multiplatform" official tutorials and the "Create a multiplatform app" codelab** — a free guided build of a shared core consumed by Android and iOS:
  <https://www.jetbrains.com/help/kotlin-multiplatform/multiplatform-getting-started.html>
- **"Wear OS Compose" codelab** — a free guided build of a Wear Compose app with `ScalingLazyColumn` and Wear Material:
  <https://developer.android.com/codelabs/compose-for-wear-os>
- **Touchlab's KMP guides** — free, current, opinionated writing on structuring shared modules and the iOS interop boundary:
  <https://touchlab.co/learn>

## Paid books (optional, clearly marked)

- **"Kotlin Multiplatform by Tutorials" — Kodeco** (paid). A guided, current treatment of shared modules and the iOS boundary; worth it if you intend to ship KMP for a living.
- **"Programming Android with Kotlin" — Pierre-Olivier Laurence et al. (O'Reilly)** (paid). Broader than KMP, but the modules and Compose chapters give useful surrounding context.

---

*If a link 404s, please open an issue so we can replace it.*
