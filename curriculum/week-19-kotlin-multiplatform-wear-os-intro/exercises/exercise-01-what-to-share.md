# Exercise 1 — What to share

**Goal.** Before you set up a single KMP module this week, build the instinct that draws the share/don't-share line correctly. Given a phone app's modules and classes, you decide for each: `commonMain` (genuinely shared), an Android-only source set, or split via `expect`/`actual` — and you justify each call. This is lecture 1's "share the business layer, not the UI" rule and the `expect`/`actual` discipline, applied.

**Estimated time.** 40 minutes.

**Prerequisites.** Lecture 1 read. No tooling — write your answers in `notes/share-decisions.md` in your Week 19 repo.

---

## The setup

You're extracting a shared core from an existing Android weather app so it can be reused by an iOS app and a Wear app. The app has the pieces below. For each, decide its home and justify it with the one-line test from lecture 1: *would the answer be identical on every device, or does it depend on the screen/hardware/OS service?*

## The twelve pieces

For each, write down: **(a)** `commonMain` / Android-only / `expect`-`actual`, and **(b)** one sentence of justification.

1. **`data class WeatherForecast(val location: String, val tempC: Double, val condition: Condition)`** — the domain model.

2. **`class ForecastViewModel`** — an Android Jetpack `ViewModel` exposing `StateFlow<UiState>`.

3. **`fun celsiusToFahrenheit(c: Double): Double`** — a pure unit-conversion function.

4. **`interface WeatherRepository { fun forecast(loc: String): Flow<NetworkResult<WeatherForecast>> }`** — the repository interface.

5. **`class KtorWeatherRepository`** — the Ktor-backed implementation of the repository.

6. **`fun randomRequestId(): String`** — generates a unique request id (currently uses `java.util.UUID`).

7. **`@Composable fun ForecastScreen(state: UiState)`** — the Compose UI.

8. **`fun shouldShowRainAlert(forecast: WeatherForecast): Boolean`** — a business rule (precip > 60%).

9. **`object ForecastJson`** — kotlinx-serialization config for the wire format.

10. **`fun currentTimeZoneId(): String`** — returns the device's time zone (currently `java.util.TimeZone.getDefault().id`).

11. **`class AndroidLocationProvider`** — reads GPS via Android's `LocationManager`.

12. **`fun formatObservedTime(instant: Instant): String`** — formats a timestamp for display (currently uses `java.time.format`).

## The two trick questions

Two of these have a subtlety worth calling out:

- **Piece 12 (formatting a time for display).** The *time math* is shareable (kotlinx-datetime), but is *display formatting* — turning an instant into a localized "3:45 PM" string — business logic or UI? Think about where localized display strings belong (lecture 1's "resources are platform-specific" gotcha).
- **Piece 2 (the ViewModel).** Lecture 1 §6 raised the "where does the ViewModel live" debate. State your call (shared vs. platform) and your reason — there's a defensible answer either way, but the *conservative, this-week* answer is specific.

## What a strong answer looks like

A weak answer says "piece 6: `expect`/`actual`." A strong answer says:

> **Piece 6 (`randomRequestId`):** `expect`/`actual`. The *concept* — generate a unique id — is common and the shared repository needs it, so it can't be Android-only. But `java.util.UUID` is JVM-only and doesn't exist on iOS, so it can't be plain `commonMain` either. Declare `expect fun randomUuid(): String` in `commonMain`, implement with `java.util.UUID` in `androidMain` and `NSUUID().UUIDString()` in `iosMain`. This is the textbook `expect`/`actual` case: common in shape, platform-specific in implementation. (Better still: prefer a KMP UUID library if one's available, so you avoid the seam entirely.)

That's the level: the home, *and* a reason that names why it can't be the other two options.

---

## Acceptance criteria

- [ ] `notes/share-decisions.md` has all twelve pieces with a home and a one-sentence justification each.
- [ ] At least four justifications name *why* the other options are wrong (not just "it's shared"), e.g. "can't be `commonMain` because it uses a JVM-only API."
- [ ] The `expect`/`actual` pieces (6, 10, and arguably 12's formatting) are correctly identified as common-in-shape, platform-specific-in-implementation.
- [ ] You answered both trick questions (12's display-formatting and 2's ViewModel placement) with reasoning.

## What you just proved

You proved you can draw the share/don't-share line *before* writing a module — the single most important skill in KMP, and the one that separates a genuinely portable core from a `commonMain` that secretly only compiles for Android. The one-line test (would the answer be identical on every device?) is most of the work: decisions and calculations and parses are universal; anything touching the screen, the hardware, or an OS service is local; and the small in-between (UUIDs, time zones) is the `expect`/`actual` seam. You just sorted twelve real pieces into those buckets.

## Reference answer sketch (read only after you've written yours)

- **1, 3, 4, 8, 9** — `commonMain`. Domain model, pure functions, the repository *interface*, business rules, serialization config — all platform-agnostic.
- **5** — `commonMain`. The Ktor implementation is multiplatform (Ktor is KMP); only the HTTP *engine* is platform-specific, injected in.
- **2** — Platform (Android). The conservative this-week answer: the ViewModel is half-UI; keep it platform-side consuming the shared repository. (A shared-ViewModel approach exists but couples the core to a UI-state shape.)
- **6, 10** — `expect`/`actual`. UUID and time zone: common shape, platform implementation.
- **7** — Android-only. Compose UI is platform-specific by definition.
- **11** — Android-only (with an `expect`/`actual` *interface* if iOS also needs location). Reads Android hardware via `LocationManager`.
- **12** — Split: the time *math* is `commonMain` (kotlinx-datetime); *localized display formatting* belongs in the platform UI (it's a localized resource concern). Don't put localized display strings in the shared core.

## Hints (read only if stuck > 10 min)

- **The one-line test:** would the answer be identical on a phone, a watch, and an iPhone? Yes → `commonMain`. Depends on the device → platform or `expect`/`actual`.
- **Stuck on something with a `java.*` import?** That's your tell: `java.*` is JVM-only, so it can't be plain `commonMain`. Either swap for a multiplatform library (kotlinx-datetime for `java.time`) or, if the concept is genuinely platform-specific, use `expect`/`actual`.
- **Interface vs. implementation:** an *interface* is almost always shareable (`commonMain`); a concrete *implementation* may or may not be, depending on what it touches.
