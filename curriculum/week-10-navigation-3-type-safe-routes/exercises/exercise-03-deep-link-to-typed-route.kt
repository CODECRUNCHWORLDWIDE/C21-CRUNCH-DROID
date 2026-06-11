// Exercise 3 — Deep link to a typed route
//
// Goal: Write a pure function `routeForUri(uri): NavKey?` that maps an incoming
//       deep-link Uri to a typed route, and unit-test it — including the bad
//       inputs that a careless parser would crash or mis-handle. Then prove that
//       a deep link SEEDS a back stack (so back from the deep-linked screen lands
//       somewhere sensible), not just shows a screen over an empty history.
//
// Estimated time: 40 minutes.
//
// HOW TO USE THIS FILE
//
// This is a ROBOLECTRIC unit-test suite (JVM `test` source set). It needs
// Robolectric because android.net.Uri is an Android type with no JVM stub; under
// Robolectric, Uri.parse works on the JVM with no emulator. The parser itself is
// pure Kotlin you can read and reason about without any Android UI.
//
//   1. Add this file to src/test/java.
//   2. Ensure Robolectric is a testImplementation dependency.
//   3. Run with `./gradlew test` or the gutter arrow.
//
// The point of a Robolectric test here is speed: the hard part of a deep link is
// PARSING, and parsing is a pure function you should test exhaustively in
// milliseconds, not by firing `adb shell am start` at an emulator over and over.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] routeForUri handles: custom scheme, https App Link, missing/!numeric id,
//       and unknown paths — returning null (never crashing) for bad input.
//   [ ] seedBackStackFor builds [root, deepLinkedScreen] so back lands on root.
//   [ ] You can explain why parsing is unit-tested but launching is not.
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package dev.crunch.nav3.exercise3

import android.net.Uri
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// ----------------------------------------------------------------------------
// The routes a deep link can land on.
// ----------------------------------------------------------------------------

sealed interface Route : NavKey

@Serializable data object CatalogRoot : Route
@Serializable data class ItemDetail(val itemId: Int) : Route
@Serializable data class UserProfile(val userId: String) : Route

// ----------------------------------------------------------------------------
// The parser — a PURE, TOTAL function from Uri to a route (or null). Total means
// it never crashes: every bad input maps to null, never to an exception. We
// accept both the custom scheme (catalog://item/42) and the https App Link
// (https://catalog.crunch.dev/item/42) by normalizing on the meaningful path.
// ----------------------------------------------------------------------------

fun routeForUri(uri: Uri): NavKey? {
    // For catalog://item/42 the "kind" is the host; for https://.../item/42 it's
    // the first path segment. Normalize so one branch handles both shapes.
    val segments = uri.pathSegments
    val kind = when (uri.scheme) {
        "catalog" -> uri.host                       // custom scheme: kind is the host
        "https" -> segments.firstOrNull()           // App Link: kind is path[0]
        else -> return null                         // unsupported scheme
    }
    return when (kind) {
        "item" -> {
            val id = uri.lastPathSegment?.toIntOrNull() ?: return null  // non-numeric -> null, no crash
            ItemDetail(itemId = id)
        }
        "profile" -> {
            val userId = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
            UserProfile(userId = userId)
        }
        else -> null                                // unknown kind -> not handled
    }
}

// ----------------------------------------------------------------------------
// Seeding: a deep link BUILDS a back stack, it doesn't just show a screen.
// Back from a deep-linked detail should land on the catalog root, not exit.
// ----------------------------------------------------------------------------

fun seedBackStackFor(route: NavKey): List<NavKey> = when (route) {
    is ItemDetail -> listOf(CatalogRoot, route)     // back from detail -> catalog root
    is UserProfile -> listOf(CatalogRoot, route)
    else -> listOf(route)                            // a root deep link is just itself
}

// ----------------------------------------------------------------------------
// The tests
// ----------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeepLinkTests {

    @Test
    fun customScheme_itemDetail() {
        val route = routeForUri(Uri.parse("catalog://item/42"))
        assertEquals(ItemDetail(itemId = 42), route)
    }

    @Test
    fun httpsAppLink_itemDetail() {
        val route = routeForUri(Uri.parse("https://catalog.crunch.dev/item/42"))
        assertEquals(ItemDetail(itemId = 42), route)
    }

    @Test
    fun profileLink_carriesStringId() {
        assertEquals(UserProfile(userId = "ada"), routeForUri(Uri.parse("catalog://profile/ada")))
        assertEquals(UserProfile(userId = "ada"), routeForUri(Uri.parse("https://catalog.crunch.dev/profile/ada")))
    }

    @Test
    fun nonNumericItemId_returnsNull_doesNotCrash() {
        // The careless parser would throw on toInt(); ours returns null.
        assertNull(routeForUri(Uri.parse("catalog://item/not-a-number")))
    }

    @Test
    fun missingId_returnsNull() {
        assertNull(routeForUri(Uri.parse("catalog://item")))     // no trailing id segment
    }

    @Test
    fun unknownPath_returnsNull() {
        assertNull(routeForUri(Uri.parse("catalog://unknown/x")))
    }

    @Test
    fun unsupportedScheme_returnsNull() {
        assertNull(routeForUri(Uri.parse("mailto:ada@crunch.dev")))
    }

    @Test
    fun deepLink_seedsAStackThatBacksToRoot() {
        val route = routeForUri(Uri.parse("catalog://item/42"))!!
        val seeded = seedBackStackFor(route)
        // Back from the deep-linked detail should reach CatalogRoot.
        assertEquals(listOf(CatalogRoot, ItemDetail(42)), seeded)
        assertEquals(CatalogRoot, seeded.first())   // bottom of the stack is a sane "home" for back
        assertEquals(ItemDetail(42), seeded.last())  // top is where the link landed
    }

    @Test
    fun rootDeepLink_isJustItself() {
        // A deep link straight to a root needs no synthetic parent.
        assertEquals(listOf<NavKey>(CatalogRoot), seedBackStackFor(CatalogRoot))
    }
}

// ----------------------------------------------------------------------------
// WHY parsing is unit-tested but launching is not (write it before reading):
//
//   The error-prone part of a deep link is turning an arbitrary, attacker- or
//   user-supplied Uri into a valid typed route — handling missing segments,
//   non-numeric ids, unknown paths, and unsupported schemes WITHOUT crashing.
//   That logic is a pure function, so it's testable exhaustively in milliseconds
//   on the JVM. The launching half (manifest <intent-filter>, App Link
//   verification, Activity intent delivery) is framework plumbing you verify once
//   manually with `adb shell am start`; re-running it per-case in an emulator
//   would be slow and would test Android, not your code.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - Uri.parse needs Robolectric. Without @RunWith(RobolectricTestRunner::class)
//   you get "Method parse in android.net.Uri not mocked". Robolectric provides
//   the real Uri implementation on the JVM.
//
// - For catalog://item/42 the structure is scheme=catalog, host=item,
//   pathSegments=[42]. For https://catalog.crunch.dev/item/42 it's scheme=https,
//   host=catalog.crunch.dev, pathSegments=[item, 42]. That's why "kind" is the
//   host for one and path[0] for the other — print uri.host and uri.pathSegments
//   in a failing test to see the shape.
//
// - lastPathSegment is "42" in both shapes, which is why it's the clean place to
//   read the id regardless of scheme. toIntOrNull (not toInt) is what makes a
//   non-numeric id return null instead of throwing — that's the totality.
//
// - Keep routeForUri TOTAL: every path through it must either return a route or
//   null, never throw. If you find yourself reaching for `!!` or `.toInt()`,
//   you're about to make it partial — use ?: return null and toIntOrNull instead.
//
// - The seeding function is where "a deep link builds a back stack" lives. If you
//   returned just listOf(route), back from the detail would exit the app — try it
//   and feel why seeding the root matters.
//
// ----------------------------------------------------------------------------
