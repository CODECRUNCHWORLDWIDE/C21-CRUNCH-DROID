// Exercise 2 — Modules, @Binds, scopes, and qualifiers (without an emulator)
//
// Goal: Practice the four building blocks of a real Hilt module — @Provides,
//       @Binds, a scope (@Singleton), and a @Qualifier to disambiguate two
//       bindings of the same type — and PROVE with tests that (a) the interface
//       resolves to the right implementation, (b) a @Singleton binding is the
//       SAME instance every time, and (c) two qualified OkHttpClient-style
//       bindings stay distinct.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// To keep the focus on the GRAPH (not the Android lifecycle), this exercise uses
// a tiny hand-rolled "container" that mimics what Hilt's generated component does:
// it constructs bindings, caches scoped ones, and disambiguates by qualifier.
// Writing the container by hand once is the point — it demystifies what Hilt
// generates. The annotations and patterns are identical to real Hilt; only the
// component is hand-assembled so the test runs on plain JVM with `./gradlew test`.
//
// Drop this in `src/test/kotlin/`. Run with `./gradlew test` or the green arrow.
// A second, commented block at the bottom shows the EXACT same graph written as
// real Hilt modules, so you can see the one-to-one mapping.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] All tests pass.
//   [ ] You can explain, in one sentence each: why @Binds is cheaper than
//       @Provides for an interface, why @Singleton means "same instance", and
//       why two same-typed bindings need a qualifier.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.di.week13

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

// ----------------------------------------------------------------------------
// The domain: an HttpClient (stands in for OkHttpClient) and an interface
// MessageProvider bound to an implementation. We want TWO HttpClients — one
// "auth" with an interceptor, one "public" without — and they must stay distinct.
// ----------------------------------------------------------------------------

/** Stand-in for OkHttpClient: expensive to build, must be shared (a @Singleton). */
class HttpClient(val interceptors: List<String>) {
    // A unique id so tests can tell two instances apart.
    val instanceId: Int = nextId()
    companion object {
        private var counter = 0
        private fun nextId(): Int = counter++
    }
}

interface MessageProvider {
    fun message(): String
}

class FriendlyMessageProvider : MessageProvider {
    override fun message(): String = "hello from the graph"
}

// ----------------------------------------------------------------------------
// Qualifiers. In real Hilt these are @Qualifier annotations; here they are an
// enum key into the container, which is exactly what a qualifier IS to the graph:
// part of the binding's identity so the same TYPE can have two distinct bindings.
// ----------------------------------------------------------------------------

enum class ClientQualifier { Auth, Public }

// ----------------------------------------------------------------------------
// A minimal "component": it knows how to PROVIDE bindings and CACHES scoped ones.
// This is a teaching model of what DaggerApp_HiltComponents_SingletonC does.
// ----------------------------------------------------------------------------

class MiniComponent {

    // Cache for @Singleton-style bindings, keyed by (type, qualifier).
    private val singletons = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> singleton(key: String, create: () -> T): T =
        singletons.getOrPut(key) { create() } as T

    // --- @Provides @Singleton @Auth HttpClient ---
    // Scoped: built once, cached, same instance forever (like @Singleton).
    fun authClient(): HttpClient =
        singleton("HttpClient:Auth") { HttpClient(interceptors = listOf("AuthInterceptor")) }

    // --- @Provides @Singleton @Public HttpClient ---
    fun publicClient(): HttpClient =
        singleton("HttpClient:Public") { HttpClient(interceptors = emptyList()) }

    // --- @Binds: MessageProvider -> FriendlyMessageProvider ---
    // Unscoped here: each request makes a fresh one (like an unscoped @Binds).
    // @Binds is "cheaper" because it doesn't construct anything new beyond the
    // impl's own constructor — it just renames the impl's binding to the interface.
    fun messageProvider(): MessageProvider = FriendlyMessageProvider()

    // A consumer that needs the AUTH client specifically (qualifier in action).
    fun feedApi(): FeedApi = FeedApi(authClient())
}

/** Stand-in for a Retrofit API that must use the AUTHENTICATED client. */
class FeedApi(val client: HttpClient)

// ----------------------------------------------------------------------------
// Tests
// ----------------------------------------------------------------------------

class ModulesBindsScopesQualifiersTest {

    @Test
    fun `binds resolves the interface to the implementation`() {
        val component = MiniComponent()
        val provider: MessageProvider = component.messageProvider()
        assertEquals("hello from the graph", provider.message())
        assertTrue(provider is FriendlyMessageProvider)
    }

    @Test
    fun `singleton scope returns the SAME instance every time`() {
        val component = MiniComponent()
        val a = component.authClient()
        val b = component.authClient()
        // Same identity: a @Singleton is constructed once and cached.
        assertSame(a, b)
        assertEquals(a.instanceId, b.instanceId)
    }

    @Test
    fun `unscoped binding returns a FRESH instance every time`() {
        val component = MiniComponent()
        val a = component.messageProvider()
        val b = component.messageProvider()
        // Different identity: unscoped bindings are rebuilt on each request.
        assertNotSame(a, b)
    }

    @Test
    fun `qualifiers keep two same-typed bindings distinct`() {
        val component = MiniComponent()
        val auth = component.authClient()
        val public = component.publicClient()

        // Same TYPE (HttpClient) but two DISTINCT bindings, told apart by qualifier.
        assertNotSame(auth, public)
        assertEquals(listOf("AuthInterceptor"), auth.interceptors)
        assertEquals(emptyList(), public.interceptors)
    }

    @Test
    fun `the auth-requiring consumer gets the authenticated client`() {
        val component = MiniComponent()
        val api = component.feedApi()
        // The FeedApi was wired to the AUTH client, not the public one.
        assertEquals(listOf("AuthInterceptor"), api.client.interceptors)
        // And because the auth client is a singleton, it is the same instance
        // the rest of the graph shares.
        assertSame(component.authClient(), api.client)
    }
}

// ----------------------------------------------------------------------------
// THE SAME GRAPH IN REAL HILT (read this — it is the one-to-one mapping)
//
// The MiniComponent above is a teaching stand-in. In a real app you would write
// exactly this, and Hilt's generated component would do what MiniComponent does:
//
//   @Qualifier @Retention(BINARY) annotation class AuthClient
//   @Qualifier @Retention(BINARY) annotation class PublicClient
//
//   @Module
//   @InstallIn(SingletonComponent::class)
//   object NetworkModule {
//       @Provides @Singleton @PublicClient
//       fun providePublicClient(): OkHttpClient =
//           OkHttpClient.Builder().build()
//
//       @Provides @Singleton @AuthClient
//       fun provideAuthClient(tokenProvider: TokenProvider): OkHttpClient =
//           OkHttpClient.Builder()
//               .addInterceptor(AuthInterceptor(tokenProvider))
//               .build()
//
//       @Provides @Singleton
//       fun provideFeedApi(@AuthClient client: OkHttpClient): FeedApi =
//           Retrofit.Builder().client(client).baseUrl(BASE_URL)
//               .build().create(FeedApi::class.java)
//   }
//
//   @Module
//   @InstallIn(SingletonComponent::class)
//   abstract class MessageModule {
//       @Binds
//       abstract fun bindMessageProvider(impl: FriendlyMessageProvider): MessageProvider
//   }
//
//   class FriendlyMessageProvider @Inject constructor() : MessageProvider { ... }
//
// Note: @Provides @Singleton == MiniComponent.singleton(...) caching; the @Qualifier
// annotations == the ":Auth"/":Public" key suffixes; @Binds == the direct
// `MessageProvider = FriendlyMessageProvider()` with no extra factory.
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - assertSame checks reference identity (===), assertEquals checks value
//   equality (==). For "is it the SAME instance" you want assertSame.
//
// - If `singleton scope returns the SAME instance` fails, your authClient()
//   isn't going through the cache — make sure it calls `singleton("HttpClient:Auth")`
//   and not `HttpClient(...)` directly.
//
// - If `qualifiers keep two bindings distinct` fails, you probably cached both
//   under the same key. The key (type + qualifier) is what a qualifier IS; the
//   keys must differ ("HttpClient:Auth" vs "HttpClient:Public").
//
// - Why @Binds is cheaper than @Provides for an interface: @Provides generates a
//   factory whose body constructs and returns the impl; @Binds generates nothing
//   beyond a cast, because the impl already has its own @Inject-constructor
//   factory and @Binds only points the interface key at it.
//
// ----------------------------------------------------------------------------
