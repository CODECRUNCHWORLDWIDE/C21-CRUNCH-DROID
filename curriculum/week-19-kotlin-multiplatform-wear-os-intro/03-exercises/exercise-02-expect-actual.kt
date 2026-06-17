// Exercise 2 — expect/actual across Android and iOS
//
// Goal: Declare an expect API in commonMain for two genuinely platform-specific
//       concerns — a UUID and a platform name — and provide actual implementations
//       in androidMain AND iosMain, keeping the common code platform-agnostic. The
//       PROOF is that the iOS target compiles. This is lecture 1, §3, made concrete.
//
// Estimated time: 50 minutes. Needs a KMP module; the iOS target must COMPILE
// (running a simulator needs macOS, but compiling does not).
//
// HOW TO USE THIS FILE
//
//   This is THREE files across THREE source sets of one KMP :shared module. Markers
//   below say which source set each block belongs in. Compile the iOS target:
//     ./gradlew :shared:compileKotlinIosSimulatorArm64
//   and the Android side:
//     ./gradlew :shared:compileDebugKotlinAndroid
//   Both must succeed — the iOS compile is the whole point.
//
// ACCEPTANCE CRITERIA
//
//   [ ] The expect declarations live in commonMain and have NO implementation.
//   [ ] androidMain provides actual impls using JVM/Android APIs.
//   [ ] iosMain provides actual impls using Foundation/UIKit APIs.
//   [ ] commonMain code (RequestEnvelope) uses the expect API and stays platform-agnostic.
//   [ ] BOTH compileKotlinIosSimulatorArm64 AND the Android compile succeed, 0 warnings.
//   [ ] A commonTest asserts randomUuid() returns distinct, non-blank values.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

// ============================================================================
// COMMON  —  shared/src/commonMain/kotlin/com/crunch/shared/Platform.kt
// ============================================================================

package com.crunch.shared

// The CONTRACT. No bodies — each platform provides the implementation.
// These are common in SHAPE (every platform can make a UUID and name itself)
// but platform-specific in IMPLEMENTATION (java.util.UUID vs NSUUID).
expect fun randomUuid(): String

expect fun platformName(): String

// COMMON CODE that USES the expect API and stays fully platform-agnostic.
// This compiles for every target; it doesn't know or care which platform it's on.
data class RequestEnvelope(
    val id: String = randomUuid(),          // calls the expect fun; resolved per platform
    val origin: String = platformName(),
    val payload: String
) {
    fun describe(): String = "[$origin#$id] $payload"
}

// ============================================================================
// ANDROID  —  shared/src/androidMain/kotlin/com/crunch/shared/Platform.android.kt
// ============================================================================

/*
package com.crunch.shared

// TODO 1: provide the Android actuals using JVM/Android APIs.
//   actual fun randomUuid(): String = java.util.UUID.randomUUID().toString()
//   actual fun platformName(): String = "Android ${android.os.Build.VERSION.SDK_INT}"

actual fun randomUuid(): String = TODO("replace with java.util.UUID.randomUUID().toString()")
actual fun platformName(): String = TODO("replace with Android Build.VERSION.SDK_INT")
*/

// ============================================================================
// iOS  —  shared/src/iosMain/kotlin/com/crunch/shared/Platform.ios.kt
// ============================================================================

/*
package com.crunch.shared

import platform.Foundation.NSUUID
import platform.UIKit.UIDevice

// TODO 2: provide the iOS actuals using Foundation/UIKit. These compile against the
//   iOS SDK because iosMain targets iOS — that's why expect/actual exists.
//   actual fun randomUuid(): String = NSUUID().UUIDString()
//   actual fun platformName(): String =
//       UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion

actual fun randomUuid(): String = TODO("replace with NSUUID().UUIDString()")
actual fun platformName(): String = TODO("replace with UIDevice systemName + systemVersion")
*/

// ============================================================================
// COMMON TEST  —  shared/src/commonTest/kotlin/com/crunch/shared/PlatformTest.kt
//
// Runs on EVERY target (JVM and iOS), proving the shared behavior is identical.
// ============================================================================

/*
package com.crunch.shared

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlatformTest {

    // TODO 3: assert randomUuid() returns distinct, non-blank values.
    @Test
    fun `uuids are distinct and non-blank`() {
        val a = randomUuid()
        val b = randomUuid()
        assertTrue(a.isNotBlank())
        assertNotEquals(a, b)          // two calls produce different ids
    }

    @Test
    fun `envelope embeds an id and origin`() {
        val e = RequestEnvelope(payload = "hello")
        assertTrue(e.id.isNotBlank())
        assertTrue(e.origin.isNotBlank())
        assertTrue(e.describe().contains("hello"))
    }
}
*/

// ============================================================================
// WHY expect/actual AND NOT just an Android impl (write before reading):
//
//   If randomUuid() were a plain commonMain function using java.util.UUID, the iOS
//   compile would FAIL — java.util.UUID doesn't exist on iOS. expect/actual lets the
//   COMMON code call randomUuid() (so RequestEnvelope is fully shared) while each
//   platform supplies the implementation in terms it actually has (UUID on JVM, NSUUID
//   on iOS). The seam is exactly the size of the platform difference — one function —
//   and everything around it stays shared.
// ============================================================================
// HINTS (read only if stuck > 15 min)
// ============================================================================
//
// - "iosMain can't find NSUUID/UIDevice." Those come from the iOS SDK, imported as
//   platform.Foundation.* / platform.UIKit.*. They're only visible in iosMain (which
//   targets iOS), NOT in commonMain — that's the point. If you see them in commonMain,
//   you put the actual in the wrong source set.
//
// - "Compile error: expect declaration has no corresponding actual." You're missing an
//   actual on one platform. EVERY expect needs an actual in EVERY target's source set.
//   If only androidMain has it, the iOS compile fails — which is the discipline working.
//
// - "Can I run the iOS target?" Compiling it (compileKotlinIosSimulatorArm64) is the
//   requirement and works on any OS. RUNNING a simulator needs macOS + Xcode; that's
//   a stretch, not required. The green iOS COMPILE is your proof of portability.
//
// - "Should I use expect class instead?" For two standalone functions, expect fun is
//   right. expect class is for a stateful platform type; here you don't need state.
//   (Lecture 1's interface-plus-factory pattern is for the stateful case.)
//
// - "TODO(...) still in my actuals." Replace every TODO with the real implementation
//   shown in the comment above it. A TODO() throws at runtime — the commonTest will
//   fail until you implement both actuals.
// ============================================================================
