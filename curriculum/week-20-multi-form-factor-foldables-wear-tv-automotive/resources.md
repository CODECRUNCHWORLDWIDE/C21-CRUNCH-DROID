# Week 20 — Resources

Every primary resource on this page is **free**. Android's developer documentation is free. The AndroidX source is public on Android Code Search and on GitHub mirrors. The conference talks are free on YouTube. A couple of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"Support different screen sizes" / "Adaptive layouts."** The framing document for the whole adaptive mental shift — design for window space, not device identity. Read this before you write a single `if (isTablet)`:
  <https://developer.android.com/develop/ui/compose/layouts/adaptive>
- **"Window size classes."** The canonical breakpoints article — `COMPACT`, `MEDIUM`, `EXPANDED`, `currentWindowAdaptiveInfo()`, and how to drive a layout from them:
  <https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes>
- **"List-detail layout" / `ListDetailPaneScaffold`.** The adaptive scaffold that reflows from single-pane to two-pane, with the back behavior you must get right:
  <https://developer.android.com/develop/ui/compose/layouts/adaptive/list-detail>
- **"Make your app fold-aware."** `WindowInfoTracker`, `FoldingFeature`, tabletop and book postures — the foldable half of lecture 1:
  <https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables>
- **"Get started with Compose for Wear OS."** The Wear Compose entry point — `material3`, `AppScaffold`, `ScreenScaffold`, the scaling list, navigation. Central to lecture 2:
  <https://developer.android.com/training/wearables/compose>

## Wear OS — the deep set

- **"Wear OS design principles."** Glanceability, the three-second interaction, why a watch app is not a phone app. Read this before you build the companion:
  <https://developer.android.com/design/ui/wear>
- **"Lists on Wear OS"** — `TransformingLazyColumn` (the Wear 4/5 scaling list) and `ScalingLazyColumn` (the older equivalent), edge scaling, `PositionIndicator`:
  <https://developer.android.com/training/wearables/compose/lists>
- **"Rotary input on Wear OS"** — handling the crown and rotating bezel with `Modifier.rotaryScrollable`:
  <https://developer.android.com/training/wearables/compose/rotary-input>
- **"Tiles" overview and "Build a tile"** — `TileService`, the Tiles + ProtoLayout model, `protolayout-material3`, freshness intervals, resource versioning:
  <https://developer.android.com/training/wearables/tiles>
  <https://developer.android.com/training/wearables/tiles/build-tile>
- **"Complications" — expose data to complications** — `ComplicationDataSourceService`, `ComplicationType`, preview data, update requesters:
  <https://developer.android.com/training/wearables/data-layer/complications>
- **"Ongoing activities"** — the `OngoingActivity` API on top of a notification, status, and the surfaces it appears on:
  <https://developer.android.com/training/wearables/ongoing-activities>

## TV and Automotive (overview — read enough to know the boundary)

- **"Build TV apps with Compose" / `tv-material`** — the `androidx.tv:tv-material` component set, the D-pad focus model, the 10-foot UI:
  <https://developer.android.com/training/tv/playback/compose>
- **"TV design / the 10-foot UI"** — overscan, focus, D-pad navigation, why touch idioms fail at three metres:
  <https://developer.android.com/design/ui/tv>
- **"Android for Cars overview" and the Car App Library** — `androidx.car.app`, the template model (`ListTemplate`, `PaneTemplate`, `NavigationTemplate`), and the app categories:
  <https://developer.android.com/training/cars>
- **"Driver distraction guidelines"** — the rules that govern what an Automotive UI may show while driving; the reason you cannot ship arbitrary Compose to a car:
  <https://developer.android.com/training/cars/apps#driver-distraction>

## The libraries, read at the source

You will not need the AndroidX internals this week, but a peek at the adaptive scaffold and the Wear scaling list makes the APIs concrete. Use Android Code Search:

- **`androidx.compose.material3.adaptive`** — `ListDetailPaneScaffold`, `currentWindowAdaptiveInfo`, the pane model:
  <https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/material3/material3-adaptive/>
- **`androidx.window.layout`** — `WindowInfoTracker`, `WindowLayoutInfo`, `FoldingFeature`:
  <https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:window/window/src/main/java/androidx/window/layout/>
- **`androidx.wear.compose`** — the Wear Material 3 component set and `TransformingLazyColumn`:
  <https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:wear/compose/>

## Talks (free, watch in this order)

- **"Building adaptive Android apps"** (Google I/O) — window size classes and the adaptive scaffolds in practice; the single best overview of the adaptive story:
  <https://www.youtube.com/results?search_query=google+io+building+adaptive+android+apps>
- **"What's new in Wear OS"** (the current year's I/O Wear session) — the Wear Material 3 components, tiles, complications, and the design principles:
  <https://www.youtube.com/results?search_query=whats+new+in+wear+os+google+io>
- **"Designing for foldables"** — postures, hinge-aware layout, and the real-world reflow cases:
  <https://www.youtube.com/results?search_query=android+designing+for+foldables>

## Sample projects to read this week

You learn more from one hour reading a real multi-form-factor codebase than three hours of tutorials. Pick one and read how it splits surfaces:

- **`android/wear-os-samples`** — the official Wear samples: tiles, complications, the Compose Wear UI, ongoing activities. The reference for the mini-project:
  <https://github.com/android/wear-os-samples>
- **`android/compose-samples` → JetNews / Reply** — Reply in particular is the canonical *adaptive* sample: window size classes driving a list-detail-supporting-pane layout:
  <https://github.com/android/compose-samples>
- **`android/nowinandroid`** — the architecture reference for the track; read how it structures the data/domain layers the Wear surfaces would consume:
  <https://github.com/android/nowinandroid>
- **`android/tv-samples`** — the official TV samples, for the overview-level look at `tv-material` and the focus model:
  <https://github.com/android/tv-samples>

## Tools you'll use this week

- **The Resizable (Experimental) emulator** — `Tools ▸ Device Manager ▸ Create Device ▸ Resizable (Experimental)`. Switch between phone / unfolded / tablet at runtime to exercise window size classes live.
- **A foldable emulator** — a Pixel Fold image, or the resizable device in its unfolded state, to drive `FoldingFeature` and the half-opened posture. The emulator's "Virtual sensors" panel can pose the hinge.
- **A Wear OS emulator** — `Create Device ▸ Wear OS ▸ Wear OS Large Round`, API 34+. The Wear companion and the tile/complication run here. No physical watch required.
- **The Tiles "preview"** and the Wear OS Tiles Material catalog — render a tile without flashing it to a device.
- **`./gradlew :wear:assembleDebug`** — the Wear module builds and installs to the Wear emulator just like a phone module.

## Free books and codelabs (chapter-level, not whole books)

- **"Adaptive layouts" codelab** and **"Compose for Wear OS" codelab** — effectively a free guided book on the two halves of this week:
  <https://developer.android.com/codelabs/jetpack-compose-adaptive-layouts>
  <https://developer.android.com/codelabs/compose-for-wear-os>
- **"Build a Wear OS tile" codelab** — a step-by-step ProtoLayout tile, mirroring exercise 3:
  <https://developer.android.com/codelabs/wear-tiles>

## Paid books (optional, clearly marked)

- **"Programming Android with Kotlin" — Pierre-Olivier Laurence et al. (O'Reilly)** (paid). Broader than form factors, but the Compose and lifecycle chapters give useful grounding for the Wear lifecycle differences.
- **"Android UI Development with Jetpack Compose" — Thomas Künneth (Packt)** (paid). Covers adaptive layouts and Wear in dedicated chapters; current enough for the Material 3 adaptive set.

---

*If a link 404s, please open an issue so we can replace it.*
