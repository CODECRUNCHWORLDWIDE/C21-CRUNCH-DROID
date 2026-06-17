# Week 19 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 20. Answer key with explanations at the bottom — don't peek.

---

**Q1.** What does Kotlin Multiplatform's design deliberately share, and what does it leave to each platform?

- A) It shares the UI; the business logic is platform-specific.
- B) It shares the business layer (domain, networking, rules) in `commonMain`; the UI stays native per platform (Compose on Android, SwiftUI on iOS).
- C) It shares everything, including the UI, like React Native.
- D) It shares nothing; it just compiles Kotlin for iOS.

---

**Q2.** Why is sharing the *UI* across platforms the trap KMP avoids?

- A) Shared UI is impossible to write.
- B) A shared UI fights each platform's native conventions, accessibility, and input — the platform-channel ceiling that burned React Native/Flutter; the business layer is where the real logic and bugs live, the UI is where platform conventions live.
- C) Kotlin can't render UI.
- D) The UI is the easiest part, so there's no point sharing it.

---

**Q3.** Which source set may contain code that compiles for *every* target, and is therefore the most constrained?

- A) `androidMain` — it's the default.
- B) `commonMain` — it compiles for every target, so it may only use multiplatform-compatible APIs.
- C) `iosMain` — iOS is the strictest.
- D) `commonTest` — tests are most constrained.

---

**Q4.** You need a UUID in shared code, but `java.util.UUID` is JVM-only. The right tool is:

- A) Put `java.util.UUID` in `commonMain` anyway.
- B) `expect`/`actual` — declare `expect fun randomUuid(): String` in `commonMain`, implement with `UUID` on Android and `NSUUID` on iOS (or use a KMP UUID library).
- C) Make the whole feature Android-only.
- D) Write the UUID logic by hand in `commonMain`.

---

**Q5.** Which set of libraries is KMP-friendly (usable in `commonMain`)?

- A) Retrofit, Gson, `java.time`.
- B) Ktor Client, kotlinx-serialization, kotlinx-coroutines, kotlinx-datetime.
- C) OkHttp, Moshi, `java.util.UUID`.
- D) Room, LiveData, `java.util.TimeZone`.

---

**Q6.** A `commonMain` accidentally imports `java.time.LocalDate`. What happens?

- A) Nothing; it works everywhere.
- B) The Android compile works but the iOS compile fails, because `java.time` is JVM-only — and that failure is the discipline catching a non-portable dependency.
- C) The app crashes at runtime on Android.
- D) Gradle auto-converts it to kotlinx-datetime.

---

**Q7.** In the shared-core architecture, the `WeatherRepository` *interface* and the `KtorWeatherRepository` *implementation* go where?

- A) The interface in the Android app, the implementation in iOS.
- B) Both in `commonMain` — the interface is platform-agnostic and Ktor is multiplatform, so the implementation is too (only the HTTP engine is platform-specific, injected in).
- C) Both must be `expect`/`actual`.
- D) The interface in `commonMain`, the implementation in `androidMain` only.

---

**Q8.** How does the shared core relate to the UI?

- A) The shared core renders the UI for both platforms.
- B) The shared core knows nothing about the UI; it exposes domain types and a repository interface, and each platform's UI maps those to its own view models at the boundary.
- C) The UI lives in `commonMain` too.
- D) The UI is generated from the shared core automatically.

---

**Q9.** For this week's architecture, where does the `ViewModel` live?

- A) In `commonMain`, always shared.
- B) Platform-side (an Android `ViewModel`, an iOS `ObservableObject`) consuming the shared repository — the conservative choice, since a ViewModel is half-UI.
- C) It doesn't exist in KMP.
- D) In `iosMain` only.

---

**Q10.** Compose for Wear OS components come from which package, and why not phone Material?

- A) `androidx.compose.material3.*` — same as phone.
- B) `androidx.wear.compose.*` — the Wear components handle the round screen, glance-optimized sizing, and ambient awareness that phone Material ignores.
- C) `android.widget.*` — Wear uses Views.
- D) There's no Wear-specific component set.

---

**Q11.** What does `ScalingLazyColumn` do that `LazyColumn` doesn't?

- A) Nothing; they're identical.
- B) It scales and fades items toward the screen edges and auto-centers content, for the round Wear screen — while keeping the same `items`/`key`/lazy-state API.
- C) It only works on phones.
- D) It disables scrolling.

---

**Q12.** A Wear *tile* is:

- A) Just another Compose screen in your Wear app.
- B) A glanceable, swipeable surface reached from the watch face — *not* an activity, built with a separate constrained layout API (`ProtoLayout`), because it must render even when your app's process isn't running.
- C) The same as a complication.
- D) A notification.

---

**Q13.** What's the right way to take a phone forecast screen to the wrist?

- A) Shrink the phone composable to fit the watch.
- B) Share the business core (the same `WeatherForecast` and repository) and *rebuild* the UI in Wear idioms with a glance-length subset of the data — port the concept, not the code.
- C) Show all the same information, just smaller.
- D) Use the phone UI unchanged; Wear renders it fine.

---

## Answer key

**Q1 — B.** KMP shares the business layer (domain, networking, rules) in `commonMain` and leaves the UI native per platform. That's the opposite of sharing the UI. (Lecture 1, §1.)

**Q2 — B.** A shared UI fights each platform's conventions and hits the platform-channel ceiling that burned React Native/Flutter. The business layer is where the real logic and bugs live; the UI is where platform conventions live — so KMP shares the first. (Lecture 1, §1.)

**Q3 — B.** `commonMain` compiles for every target, so it's the most constrained — multiplatform libraries only. Platform sets are less constrained but reach fewer targets. (Lecture 1, §2.)

**Q4 — B.** UUID is common in shape, platform-specific in implementation — the textbook `expect`/`actual` case (or a KMP UUID library). Putting `java.util.UUID` in `commonMain` breaks the iOS compile. (Lecture 1, §3.)

**Q5 — B.** Ktor, kotlinx-serialization, kotlinx-coroutines, kotlinx-datetime are multiplatform. Retrofit, Gson, `java.time`, OkHttp, Room are JVM/Android-only and can't go in `commonMain`. (Lecture 1, §4.)

**Q6 — B.** The Android compile works but the iOS compile fails — `java.time` is JVM-only. That failure is the feature: the compiler enforces portability. (Lecture 1, §2, §4.)

**Q7 — B.** Both in `commonMain`: the interface is platform-agnostic, and Ktor is multiplatform so the implementation is too. Only the HTTP *engine* is platform-specific, injected in. (Lecture 1, §5.)

**Q8 — B.** The shared core knows nothing about the UI; it exposes domain types and a repository interface, and each platform's UI maps those to its own view models — the Week 12 boundary, now spanning platforms. (Lecture 1, §5–6.)

**Q9 — B.** This week's conservative choice keeps the `ViewModel` platform-side (Android `ViewModel` / iOS `ObservableObject`) consuming the shared repository — a ViewModel is half-UI. A shared-ViewModel approach exists but couples the core to a UI-state shape. (Lecture 1, §6.)

**Q10 — B.** Wear components come from `androidx.wear.compose.*` and handle the round screen, glance sizing, and ambient mode that phone Material ignores. Mixing in phone Material is the #1 Wear mistake. (Lecture 2, §2.)

**Q11 — B.** `ScalingLazyColumn` scales/fades items toward the edges and auto-centers for the round screen, while keeping the same `items`/`key`/lazy-state API as `LazyColumn`. (Lecture 2, §4.)

**Q12 — B.** A tile is a glanceable, swipeable, non-activity surface built with `ProtoLayout` (a constrained layout API), because it must render even when your app isn't running. It's not a Compose screen, not a complication, not a notification. (Lecture 2, §6.)

**Q13 — B.** Share the core, rebuild the UI in Wear idioms with a glance-length subset — port the concept, not the code. Shrinking the phone screen is the anti-pattern; the watch needs a smaller *answer*, not a smaller layout. (Lecture 2, §1a, §7.)

---

*Score 11+? On to Week 20. Below 9? Re-read both lecture notes and re-run exercises 02 and 03 — the `expect`/`actual` seam (proving the iOS compile) and the Wear screen (using the Wear component set) are the two skills this week is graded on.*
