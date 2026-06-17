// Exercise 2 — Dynamic color with a hand-tuned fallback
//
// Goal: Write and TEST the scheme-selection logic that routes Android 12+ to the
//       wallpaper-derived dynamic palette and everything else (older OS, or user
//       opted out) to a hand-tuned fallback — across the full matrix of
//       {dynamic on/off} x {light/dark} x {API >= 31 / API < 31}. The selection
//       is a pure function of three booleans; you prove it picks the right
//       scheme in every cell.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// This is a ROBOLECTRIC unit-test suite (JVM `test` source set). We factor the
// SELECTION DECISION out of the @Composable so it's a pure function we can test
// exhaustively without a device or a wallpaper. The actual dynamicLight/Dark
// ColorScheme(context) calls need an Android context and a real OS palette, so
// we represent the *decision* (which source to use) as an enum and test that;
// the composable that turns the decision into a ColorScheme is trivial and
// verified visually on two emulators.
//
//   1. Add this file to src/test/java.
//   2. Ensure Robolectric is a testImplementation dependency.
//   3. Run with `./gradlew test`.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] schemeSource returns DYNAMIC only when dynamicColor && apiLevel >= 31;
//       otherwise FALLBACK, with the correct light/dark flavor.
//   [ ] All matrix cells are asserted (8 combinations).
//   [ ] You can explain why the selection is unit-tested but the dynamic
//       ColorScheme(context) call is not.
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package dev.crunch.theme.exercise2

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// ----------------------------------------------------------------------------
// The decision, as a value we can assert on. In the real composable, DYNAMIC_*
// maps to dynamicLight/DarkColorScheme(context) and FALLBACK_* to your
// LightColors/DarkColors. Here we test the DECISION, not the ColorScheme.
// ----------------------------------------------------------------------------

enum class SchemeSource {
    DYNAMIC_LIGHT,
    DYNAMIC_DARK,
    FALLBACK_LIGHT,
    FALLBACK_DARK
}

// API 31 == Android 12 == Build.VERSION_CODES.S. Passed in so the function is
// pure and testable without reading Build.VERSION.SDK_INT (which is fixed at
// runtime). The real composable passes Build.VERSION.SDK_INT here.
const val ANDROID_12 = 31

/**
 * The selection logic, extracted as a pure function. Dynamic color is available
 * only on Android 12 (API 31) and up, and only if the user hasn't opted out.
 */
fun schemeSource(dynamicColor: Boolean, darkTheme: Boolean, apiLevel: Int): SchemeSource = when {
    dynamicColor && apiLevel >= ANDROID_12 ->
        if (darkTheme) SchemeSource.DYNAMIC_DARK else SchemeSource.DYNAMIC_LIGHT
    darkTheme -> SchemeSource.FALLBACK_DARK
    else -> SchemeSource.FALLBACK_LIGHT
}

// ----------------------------------------------------------------------------
// The tests — every cell of the 2 x 2 x 2 matrix.
// ----------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SchemeSelectionTests {

    // --- Android 12+ : dynamic when enabled ---

    @Test fun api35_dynamicOn_light_isDynamicLight() =
        assertEquals(SchemeSource.DYNAMIC_LIGHT, schemeSource(dynamicColor = true, darkTheme = false, apiLevel = 35))

    @Test fun api35_dynamicOn_dark_isDynamicDark() =
        assertEquals(SchemeSource.DYNAMIC_DARK, schemeSource(dynamicColor = true, darkTheme = true, apiLevel = 35))

    // --- Android 12+ : user opted out -> fallback ---

    @Test fun api35_dynamicOff_light_isFallbackLight() =
        assertEquals(SchemeSource.FALLBACK_LIGHT, schemeSource(dynamicColor = false, darkTheme = false, apiLevel = 35))

    @Test fun api35_dynamicOff_dark_isFallbackDark() =
        assertEquals(SchemeSource.FALLBACK_DARK, schemeSource(dynamicColor = false, darkTheme = true, apiLevel = 35))

    // --- Android 11 (API 30) : never dynamic, even when "enabled" ---

    @Test fun api30_dynamicOn_light_isFallbackLight_becauseNoDynamicBelow12() =
        assertEquals(SchemeSource.FALLBACK_LIGHT, schemeSource(dynamicColor = true, darkTheme = false, apiLevel = 30))

    @Test fun api30_dynamicOn_dark_isFallbackDark() =
        assertEquals(SchemeSource.FALLBACK_DARK, schemeSource(dynamicColor = true, darkTheme = true, apiLevel = 30))

    @Test fun api30_dynamicOff_light_isFallbackLight() =
        assertEquals(SchemeSource.FALLBACK_LIGHT, schemeSource(dynamicColor = false, darkTheme = false, apiLevel = 30))

    @Test fun api30_dynamicOff_dark_isFallbackDark() =
        assertEquals(SchemeSource.FALLBACK_DARK, schemeSource(dynamicColor = false, darkTheme = true, apiLevel = 30))

    // --- The boundary: exactly API 31 is the first dynamic version ---

    @Test fun exactlyApi31_dynamicOn_isDynamic() =
        assertEquals(SchemeSource.DYNAMIC_LIGHT, schemeSource(dynamicColor = true, darkTheme = false, apiLevel = 31))

    @Test fun api30_isLastNonDynamicVersion() =
        assertEquals(SchemeSource.FALLBACK_LIGHT, schemeSource(dynamicColor = true, darkTheme = false, apiLevel = 30))
}

// ----------------------------------------------------------------------------
// HOW THE DECISION MAPS TO A REAL ColorScheme (for reference — not under test):
//
//   @Composable
//   fun chooseColorScheme(darkTheme: Boolean, dynamicColor: Boolean): ColorScheme {
//       val context = LocalContext.current
//       return when (schemeSource(dynamicColor, darkTheme, Build.VERSION.SDK_INT)) {
//           SchemeSource.DYNAMIC_LIGHT -> dynamicLightColorScheme(context)
//           SchemeSource.DYNAMIC_DARK  -> dynamicDarkColorScheme(context)
//           SchemeSource.FALLBACK_LIGHT -> LightColors
//           SchemeSource.FALLBACK_DARK  -> DarkColors
//       }
//   }
//
// ----------------------------------------------------------------------------
// WHY the selection is unit-tested but dynamicXColorScheme(context) is not
// (write it before reading):
//
//   The DECISION — which source to use — is a pure function of three booleans
//   (dynamic enabled, dark, api level), so it's testable exhaustively across all
//   8 cells in milliseconds with no device. The dynamicLight/DarkColorScheme call
//   needs a real Android context and a real OS-extracted wallpaper palette, which
//   is the framework's job, not yours; you verify THAT path once, visually, by
//   changing the emulator wallpaper and watching the app re-tint. Testing the
//   framework's extraction would test Android, not your code.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - The bug everyone writes: `apiLevel > 31` instead of `>= 31`. API 31 IS
//   Android 12 and HAS dynamic color. The boundary tests (exactlyApi31_*,
//   api30_isLast*) exist to catch exactly this off-by-one.
//
// - Don't read Build.VERSION.SDK_INT inside schemeSource — it's a constant at
//   runtime and you can't vary it in a test. Pass apiLevel in. The composable
//   supplies Build.VERSION.SDK_INT at the call site.
//
// - The "user opted out" case (dynamicColor = false on API 35) must still pick
//   the right LIGHT/DARK fallback. A common miss is returning a single FALLBACK
//   without the light/dark flavor.
//
// - SchemeSource is a stand-in so the test asserts a value. In production the
//   enum is optional — you can branch straight to the ColorScheme — but extracting
//   the decision is exactly what makes it testable.
//
// ----------------------------------------------------------------------------
