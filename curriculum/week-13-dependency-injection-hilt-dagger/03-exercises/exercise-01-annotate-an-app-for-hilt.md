# Exercise 1 — Annotate a hand-wired app for Hilt

**Goal.** Take an app that wires its dependencies by hand through a `ServiceLocator`, convert it to Hilt with the four entry-point annotations and one module, and *delete* the manual construction. Then prove the graph builds the same objects by running the app and confirming the injected `Greeter` works. This is the entire promise of the week distilled to one screen — if you can do this, dependency injection works; everything else this week is refinement.

**Estimated time.** 40 minutes.

**Prerequisites.** Android Studio (2025.1+), an emulator or device, a project with the Hilt Gradle plugin and KSP applied. The full app is *not* required — we build a throwaway `:app` so the focus stays on the graph.

---

## Step 1 — Start from the hand-wired version

Here is the starting point: an app that constructs everything by hand. A `Greeter` needs a `MessageProvider`; a `ServiceLocator` news both up; the Activity reaches into the locator. Read it and feel the boilerplate — this is what Hilt deletes.

```kotlin
// MessageProvider.kt
interface MessageProvider { fun message(): String }

class FriendlyMessageProvider : MessageProvider {
    override fun message(): String = "Hello from the graph"
}

// Greeter.kt
class Greeter(private val provider: MessageProvider) {
    fun greeting(): String = provider.message()
}

// ServiceLocator.kt — the thing we are about to delete.
object ServiceLocator {
    private val provider: MessageProvider = FriendlyMessageProvider()
    val greeter: Greeter = Greeter(provider)
}

// MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val greeter = ServiceLocator.greeter      // <- manual wiring
        setContent { Text(greeter.greeting()) }
    }
}
```

Build and run it. You should see "Hello from the graph". This works — and it is exactly the pattern that rots as the app grows: every new dependency means editing `ServiceLocator`, and a missing one is a runtime `NullPointerException`, not a compile error.

## Step 2 — Add the Application and `@HiltAndroidApp`

Create the Application and register it in the manifest:

```kotlin
// CrunchApp.kt
import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CrunchApp : Application()
```

```xml
<!-- AndroidManifest.xml -->
<application
    android:name=".CrunchApp"
    ... >
```

`@HiltAndroidApp` generates the `SingletonComponent` — the root of the graph. Without it there is no graph, and the build tells you so. This is always the first annotation in a Hilt app.

## Step 3 — Make the graph able to build the interface

`Greeter` is a class you own, so it can get an `@Inject constructor`. But `MessageProvider` is an *interface* — the graph cannot construct an interface, so you bind the implementation to it with `@Binds`:

```kotlin
// MessageProvider.kt
class FriendlyMessageProvider @Inject constructor() : MessageProvider {
    override fun message(): String = "Hello from the graph"
}

// Greeter.kt — now constructor-injected; the graph resolves MessageProvider for it.
class Greeter @Inject constructor(
    private val provider: MessageProvider
) {
    fun greeting(): String = provider.message()
}

// GreetingModule.kt — teach the graph "MessageProvider means FriendlyMessageProvider".
@Module
@InstallIn(SingletonComponent::class)
abstract class GreetingModule {
    @Binds
    abstract fun bindMessageProvider(impl: FriendlyMessageProvider): MessageProvider
}
```

`@Binds` is an abstract function with no body — Dagger optimises it to "when someone asks for `MessageProvider`, give them the already-injectable `FriendlyMessageProvider`." It lives in an `abstract class` because `@Binds` has no implementation to put in an `object`.

## Step 4 — Field-inject into the Activity

The OS constructs your Activity, so you cannot use a constructor — you use `@AndroidEntryPoint` and field injection:

```kotlin
// MainActivity.kt
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var greeter: Greeter      // <- Hilt sets this before onCreate runs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text(greeter.greeting()) }   // no ServiceLocator, no manual wiring
    }
}
```

`@AndroidEntryPoint` generates `Hilt_MainActivity`, which asks the component for a `Greeter` and sets the field before your `onCreate` body runs. That is why `lateinit var greeter` is safe to read in `onCreate` — Hilt has already populated it.

## Step 5 — Delete the ServiceLocator

Delete `ServiceLocator.kt` entirely. Nothing references it anymore — the graph does its job. If the project compiles, you have proven the graph constructs the same objects the locator did, with the wiring now the compiler's responsibility.

```bash
# From the project root:
rm app/src/main/java/com/yourname/app/ServiceLocator.kt
./gradlew :app:assembleDebug
```

Build and run. You should see the same "Hello from the graph". Same output, completely different wiring: the locator is gone, the graph is generated, and a missing binding would now be a build error instead of a crash.

## Step 6 — Prove it's compile-time-checked (the whole point)

Temporarily *break* the graph to see the compile-time guarantee. Delete (or comment out) the `@Binds` in `GreetingModule`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class GreetingModule {
    // @Binds
    // abstract fun bindMessageProvider(impl: FriendlyMessageProvider): MessageProvider
}
```

Build. You should get a Dagger error that reads roughly:

```text
error: [Dagger/MissingBinding] ...MessageProvider cannot be provided without an
@Provides-annotated method.
    ...MessageProvider is injected at
        ...Greeter(provider)
    ...Greeter is injected at
        ...MainActivity.greeter
```

Read it bottom-up: `MainActivity` wants a `Greeter`, which wants a `MessageProvider`, which has no binding. **This is the promise of the week** — the missing dependency is a *build error with the exact chain*, not a `NullPointerException` in front of a user. Restore the `@Binds` and confirm the build goes green again.

---

## Acceptance criteria

- [ ] `@HiltAndroidApp class CrunchApp : Application()` exists and is registered in the manifest.
- [ ] `Greeter` and `FriendlyMessageProvider` use `@Inject constructor`; `MessageProvider` is bound via `@Binds` in a `@InstallIn(SingletonComponent::class)` module.
- [ ] `MainActivity` is `@AndroidEntryPoint` and reads `@Inject lateinit var greeter`.
- [ ] `ServiceLocator.kt` is **deleted** and nothing references it.
- [ ] Build with **0 warnings, 0 errors**; the app shows "Hello from the graph".
- [ ] You temporarily removed the `@Binds`, observed the `MissingBinding` error with its dependency chain, and restored it.

## What you just proved

You proved the four entry-point annotations actually wire a graph: `@HiltAndroidApp` created the `SingletonComponent`, `@Binds` taught it the interface→impl edge, `@Inject constructor` let it build `Greeter` recursively, and `@AndroidEntryPoint` field-injected the finished object into the Activity. And you proved the week's promise — *a missing dependency is a compile error, not a runtime crash* — by deleting a binding and reading the exact chain Dagger reported. Every other exercise this week builds on this skeleton.

---

## Hints (read only if stuck > 10 min)

- **`@Inject lateinit var greeter` is null in `onCreate`.** Almost always: the Activity is missing `@AndroidEntryPoint`, or you read the field *before* `super.onCreate(...)`. Hilt injects in the generated base's `onCreate`, so read the field after `super`.
- **Build fails `@HiltAndroidApp`-not-found or "Expected @HiltAndroidApp".** The manifest's `android:name` doesn't point at your `CrunchApp`, so Hilt can't find the application root.
- **`@Binds` won't compile in an `object`.** `@Binds` is abstract — it needs an `abstract class` or `interface`, not an `object`. `@Provides` is the one that goes in an `object`.
- **`MissingBinding` even with the `@Binds` present.** Check that `FriendlyMessageProvider` has its own `@Inject constructor()` — `@Binds` only renames an existing binding; the impl still needs to be constructable.
- **KSP "annotation processor not found".** Confirm the Hilt Gradle plugin and `ksp(libs.hilt.compiler)` are both applied in the `:app` module's `build.gradle.kts`.
