# Lecture 1 — ART, the process model, and the lifecycle as history

> "Your Kotlin doesn't change when you target Android. Everything *around* it does — the bytecode format, the runtime that executes it, the heap it lives in, and an OS that will kill it. A senior Android engineer carries that difference in their head at all times."

This is the lecture where "Android" stops meaning "Kotlin with extra libraries" and starts meaning "a specific runtime on a specific machine with specific constraints." We build the model from the metal up: what ART is and how it differs from the desktop JVM you've used for five weeks, what DEX bytecode is and why it exists, how an app becomes a *process*, why that process can die at any moment, what the Activity lifecycle actually models, and — at the end — a conceptual tour of AOSP so "the framework" becomes a thing you can read instead of a black box.

---

## 1. Two runtimes, one language

For five weeks your Kotlin compiled to JVM bytecode (`.class` files) and ran on a desktop JVM (HotSpot/OpenJDK) with `./gradlew run`. That JVM had: gigabytes of heap, wall power, a fan, and a JIT compiler free to spend CPU optimizing hot code because nobody's battery was at stake.

On Android, your Kotlin *still* compiles to JVM bytecode first — `kotlinc` doesn't know or care about Android. But then a **second compilation step** converts those `.class` files into **DEX** (Dalvik Executable) bytecode, and the runtime that executes DEX is **ART**, the Android Runtime. ART is *not* the JVM. It's a different virtual machine, with a different bytecode format, designed from the ground up for a phone.

The constraints that shaped ART, and why each matters to you:

- **Tiny heap.** A desktop JVM app can have a multi-gigabyte heap. An Android app gets a per-app heap limit measured in *tens to a few hundred* megabytes, set by the device and enforced by the OS. Exceed it and you get an `OutOfMemoryError` and a crash. This is why bitmap handling, large lists, and leaks matter so much more on Android — you have far less room.
- **Battery.** The desktop JVM's JIT recompiles hot code aggressively because CPU is free. ART can't burn battery like that, so it uses a *hybrid* strategy (§2) that does expensive compilation only when the device is idle and charging.
- **An OS that kills you.** The desktop JVM process lives until you `exit()`. An Android app process can be **killed by the OS at any time** to reclaim memory for the foreground app. Your process dying is *normal*, not exceptional (§3). This single fact drives half of Android architecture — saved state, process-death survival, `WorkManager` for guaranteed work.

Your Kotlin source is identical. The machine underneath is not. Hold that.

---

## 2. DEX and the AOT/JIT hybrid

**Why DEX exists.** JVM `.class` files are *stack-based* and one-class-per-file. DEX is *register-based* and packs many classes into a single `classes.dex`, which is smaller and faster to load on a constrained device. The conversion (JVM bytecode → DEX) is done by **D8** (the dexer) on debug builds and **R8** (which dexes *and* shrinks/optimizes) on release builds. We trace that in lecture 2; for now the fact is: **your `.class` files never reach the device — only DEX does.**

**How ART runs DEX — the hybrid.** Early Android (Dalvik) interpreted DEX, then JIT-compiled hot paths. ART changed the model and it's now a *three-part hybrid*:

1. **Interpretation.** When code first runs, ART interprets the DEX.
2. **JIT.** Hot methods get just-in-time compiled to native code, *and ART records a profile* of which methods are hot.
3. **AOT (profile-guided).** When the device is idle and charging, `dex2oat` ahead-of-time compiles the profiled hot methods to native code stored on disk, so next launch they're already native — no warm-up.

This is **profile-guided compilation**, and it's the foundation of a Phase 3 topic you should bank now: a **Baseline Profile** is a profile you *ship with your app* so ART can AOT-compile the critical startup path on *first* launch, before the device has had a chance to profile it itself. That's how you cut cold-start time (Week 18). The mental model to carry: **ART compiles what's hot, lazily, to save battery — and a Baseline Profile lets you tell it what's hot in advance.**

The three on-device runtime states, visualized:

```text
DEX method, first time it runs:
   INTERPRETED  ── runs hot? ──▶  JIT-COMPILED to native  ── profiled ──▶  recorded as "hot"
                                                                                  │
                                          (device idle + charging) ── dex2oat ────┘
                                                                                  ▼
                                                                AOT-COMPILED native on disk
                                                            (ready, fast, next launch onward)
```

The takeaway: there is no single "compile" on Android. There's `kotlinc` (→ JVM bytecode), D8/R8 (→ DEX), and `dex2oat` (→ native, on-device, profile-guided). Three stages, and you'll touch all three across the course. And the practical payoff you'll measure in Week 18: a Baseline Profile ships the "recorded as hot" list *in the APK*, so `dex2oat` can pre-compile your startup path before the user has run the app even once — turning a slow first launch into a fast one.

---

## 2b. The ART garbage collector and the heap you actually have

The constrained heap (§1) is not just a number — it's a runtime you share with a garbage collector that has very different priorities from the desktop JVM's. The desktop JVM optimizes for *throughput* (get the most work done; long GC pauses are acceptable on a server). ART optimizes for *not dropping frames* — a long stop-the-world GC pause on a phone is a visible stutter, a jank the user feels. So ART's GC is tuned to be **mostly-concurrent**: it does as much collection work as possible *while your app keeps running*, minimizing the moments it has to pause every thread.

Two consequences you carry into every week of UI work:

- **Allocation in a hot loop is expensive.** Every object you allocate is memory the GC must eventually trace and reclaim. In a per-frame path — a Compose draw, a `RecyclerView` bind, an animation tick — allocating throwaway objects (a new `Paint`, a boxed `Integer`, a temporary `List`) creates *GC pressure* that eventually forces a collection, and a collection at the wrong moment drops a frame. This is *the* reason "avoid allocations in the draw phase" is a recurring Compose performance rule (Week 07). On a server you'd never think about it; on ART it's the difference between smooth and janky.
- **Leaks are louder.** With tens of megabytes of heap instead of gigabytes, a leak — an `Activity` retained after it should have been destroyed, a `Bitmap` never recycled, a coroutine that outlives its scope — fills the heap fast and triggers an `OutOfMemoryError` crash. The desktop JVM might absorb the same leak for hours; ART crashes in minutes. This is why leak detection (LeakCanary) is standard on Android and why structured concurrency (Week 04) — scopes that cancel and free their work — matters more here than anywhere.

You can watch the GC and the heap directly in Android Studio's **Memory Profiler**: it shows live allocations, GC events, and the heap composition, and it's where you'll diagnose the leak that's crashing your app. The mental model: **ART gives you a small heap and a frame-conscious GC; your job is to allocate little on hot paths and free everything you no longer need, because the runtime has far less slack than the JVM you came from.**

---

## 2c. JVM bytecode versus DEX — seeing the difference

You've used `javap` (Week 01) to read JVM bytecode. It's worth making the JVM-vs-DEX distinction concrete, because it explains why a few Android quirks exist.

A JVM `.class` file is **stack-based**: operations push and pop operands on an evaluation stack. Adding two locals looks like:

```text
# JVM bytecode (stack-based) for `a + b`:
iload_1        # push local a onto the stack
iload_2        # push local b onto the stack
iadd           # pop two, push their sum
ireturn        # return the top of the stack
```

DEX is **register-based**: operations name virtual registers directly, no stack juggling:

```text
# DEX bytecode (register-based) for `a + b`:
add-int v0, v1, v2     # v0 = v1 + v2, in one instruction
return v0
```

The register-based form is denser (fewer instructions for the same work) and maps better onto real CPU registers, which is why DEX is more compact and faster to load — exactly what a constrained device wants. You don't write either, but two practical facts fall out:

- **One `classes.dex` holds many classes.** Where the JVM has one `.class` per class, DEX packs them together. A large app can exceed the DEX method-count limit (~65k methods per DEX) and need **multidex** (multiple `classes.dex` files) — a thing you'll see in build output and occasionally have to configure. R8's shrinking (Week 18) helps by removing unused methods.
- **You can disassemble DEX too.** The tool is `dexdump` (in the SDK build-tools): `dexdump -d classes.dex`. You rarely need it, but when you're debugging an obfuscated release crash or verifying what shipped, reading the DEX is the ground truth — the same "read the bytecode for confidence" instinct Week 01 built, one level down.

The point isn't to make you a DEX expert; it's to cement that **the thing on the device is not your `.class` files** — it's a different, denser, register-based bytecode, and that difference is a deliberate adaptation to the phone, not an accident.

---

## 3. The process model — why your app dies

Here is the fact that makes Android architecture make sense: **every Android app runs in its own Linux process, and the OS starts, stops, and kills that process on its own schedule.**

When the device boots, a special process called the **Zygote** starts, loads the core framework classes and the runtime, and then *forks* to create every app process. Forking from a pre-warmed Zygote is why app launch is fast — the new process inherits an already-initialized runtime and framework instead of loading them from scratch.

The **`system_server`** process (the OS's brain — it hosts the `ActivityManager`, `WindowManager`, `PackageManager`, and dozens of other services) decides which app processes exist. When the user opens your app, `system_server` tells the Zygote to fork a process for you, and your process's `main()` (in the framework's `ActivityThread`) starts a message loop and calls your `Activity`'s `onCreate`.

And when memory runs low — the user opened a camera, a game, three other apps — `system_server` **kills** background app processes to reclaim memory, in a priority order (empty/background processes first, the foreground app last). It does not ask. It does not run your `onDestroy` reliably. Your process is simply gone.

The consequences you design for, starting now:

- **Process death is normal.** Your app *will* be killed in the background and the user *will* return to it expecting their state. You cannot hold important state only in memory. (This is why Week 08's `rememberSaveable`, Week 12's `SavedStateHandle`, and Week 14's persistence exist.)
- **The main thread is sacred.** Each process has one **main (UI) thread**; the framework dispatches lifecycle callbacks and input events on it. Block it for ~5 seconds and the OS shows an **ANR** ("Application Not Responding") dialog. This is why every five weeks of coroutines mattered — you move work off the main thread or you ANR.
- **Background work is regulated.** Because the OS aggressively kills processes and restricts background CPU/network to save battery, you cannot just spawn a thread and trust it to finish. You schedule guaranteed work through `WorkManager` (Week 16) and the OS runs it within the battery rules.

The model: **you don't own your process; the OS does. It forks you from the Zygote, runs you on one main thread, and kills you when it needs the memory. Every architectural choice on Android is downstream of those three facts.**

---

## 3b. The main thread, the Looper, and the ANR

Of the three facts, the "one sacred main thread" deserves its own treatment, because it's where the most common production crashes (ANRs) come from and where five weeks of coroutines finally cash in.

Your app process has exactly one **main thread** (also called the **UI thread**). When `ActivityThread.main()` runs after the Zygote fork, it sets up a **`Looper`** — a loop that pulls messages off a **`MessageQueue`** and dispatches them, forever:

```text
main thread:
  Looper.loop() {
    while (true) {
      val msg = queue.next()      // blocks until there's a message
      msg.target.dispatchMessage(msg)   // run it — a lifecycle callback, an input event, a UI update
    }
  }
```

*Everything* that touches the UI runs as a message on this one loop: `onCreate`/`onResume` and the other lifecycle callbacks, every touch and key event, every Compose recomposition and frame draw, every `View.invalidate`. They run **one at a time, in order**, on the single main thread. There is no second UI thread; this is a deliberate design that frees you from locking your UI state — but it has a sharp edge.

**The sharp edge: block the main thread and you block everything.** If a message handler takes a long time — a network call, a big file read, a heavy computation — the `Looper` can't pull the next message until it returns. So input freezes, the screen stops updating, animations stall. If you block the main thread for roughly **5 seconds** while the user is trying to interact, the OS's watchdog declares an **ANR** — "Application Not Responding" — and shows the user a dialog offering to kill your app. An ANR is a *worse* user experience than a crash, and Play Console tracks your **ANR rate** as a vitals metric that, if too high, suppresses your app in the store.

The canonical ANR causes, all "long work on the main thread":

- A synchronous **network call** in `onCreate` or a click handler (the classic — never do I/O on the main thread).
- A large **database query** or file read on the main thread.
- A heavy **computation** (parsing a huge JSON, processing a bitmap) in a UI callback.
- A **deadlock** or a `synchronized` block on the main thread waiting on a background lock.

**This is why coroutines (Weeks 4–5) exist for Android.** The discipline is: lifecycle callbacks and UI updates run on the main thread (`Dispatchers.Main`); everything *slow* — I/O, network, heavy CPU — runs on a background dispatcher (`Dispatchers.IO`, `Dispatchers.Default`) and only the *result* comes back to the main thread to update the UI. `viewModelScope.launch { val data = withContext(Dispatchers.IO) { repo.load() }; uiState = data }` is the shape of nearly every screen you'll write: launch on main, do the work off-main, update on main. Get that wrong — do the `repo.load()` on the main thread — and you ANR.

The model: **one main thread runs a `Looper` that dispatches every UI message in order; block it and the whole UI freezes into an ANR; so you move all slow work off it with coroutines and bring only results back.** This single constraint shapes the entire concurrency story of the platform.

---

## 4. The Activity lifecycle — what it models, and what Compose replaced

An **`Activity`** is the framework's unit of "a screen the user interacts with" — the host the OS talks to. Because the OS creates, pauses, stops, and destroys your UI on *its* schedule (the user rotates the phone, gets a call, switches apps, the OS reclaims memory), the framework gives the `Activity` a **lifecycle** of callbacks so you can react:

```text
onCreate()   -> the Activity is being created; set up your UI here
onStart()    -> becoming visible
onResume()   -> in the foreground, interactive
   ... user interacts ...
onPause()    -> losing foreground (a dialog, another app partially covers you)
onStop()     -> no longer visible
onDestroy()  -> being destroyed (finished, or the OS reclaiming) — NOT guaranteed on a kill
```

Two things make the lifecycle subtle, and you need both:

- **Configuration changes destroy and recreate the Activity.** Rotate the phone and — by default — the framework *destroys* your `Activity` and creates a *new one*, because the layout/resources may differ. Any state held in the old `Activity` instance is gone unless you saved it. This is the classic "I rotated and lost my data" bug, and it's *by design*. (Compose's `rememberSaveable` and the `ViewModel` exist to survive exactly this — Weeks 08 and 12.)
- **`onDestroy` is not guaranteed.** On a process kill (§3), the OS may skip `onDestroy` entirely. So `onDestroy` is for *graceful* teardown, never for *critical* persistence — persist in `onStop`/as-you-go, not in `onDestroy`.

**`Fragment`** is historical context worth knowing but not dwelling on: before Compose, `Fragment`s were the framework's answer to "reusable, lifecycle-aware chunks of UI inside an Activity," and they came with their own famously tricky lifecycle (a separate *view* lifecycle, the back stack, `FragmentManager` transactions). Compose largely replaces the *reason* `Fragment`s existed — composable functions are the reusable UI unit now, and a single-Activity-plus-Compose-navigation architecture (Week 10) is the modern norm.

**What Compose replaced, and what it didn't.** Compose replaced the *old View world*: manually inflating XML layouts, holding `findViewById` references, wiring `RecyclerView.Adapter`s, and tying mutable view state to lifecycle callbacks. What it did **not** replace: the `Activity` is *still the host*. Compose runs *inside* an `Activity`'s `setContent { }`. The lifecycle still happens underneath — the Activity is still created, paused, and destroyed by the OS; process death is still real. Compose changed how you describe UI; it did not change the machine or the host the UI runs in. That's why this week (the host and the runtime) comes *before* Compose (the UI on top of it).

### The two state-survival mechanisms, previewed

Because the lifecycle destroys-and-recreates (configuration change) and the OS kills-and-reforks (process death), the framework gives you *two* mechanisms to carry state across those events — and they survive *different* things, which is the distinction half of Android architecture rests on:

- **Saved instance state** (`onSaveInstanceState` / the `Bundle`). The framework calls `onSaveInstanceState(bundle)` *before* a destroy it might recover from, and hands the same `Bundle` back to `onCreate(savedInstanceState)` on recreation. It survives **both** a configuration change **and** a process kill (the OS persists the `Bundle` to disk for you). The catch: it's *small* — it's serialized and held by `system_server`, so it's for a few primitives and small parcelables (a scroll position, a text field's contents), never a screen's worth of data.
- **`ViewModel`** (Jetpack — Week 12). A `ViewModel` survives a **configuration change** (the framework keeps it across the Activity's destroy/recreate) but **not** a process kill (it lives in memory, which the kill discards). It's for the *working set* of a screen — the loaded data, the in-flight requests — that you don't want to refetch on every rotation but can afford to reload after a full process death.

The senior summary, which you'll apply constantly from Week 12 on:

| Mechanism | Survives config change? | Survives process death? | Holds |
|---|---|---|---|
| `ViewModel` | yes | **no** | the working set (loaded data, in-flight state) |
| Saved instance state (`Bundle`) | yes | **yes** | small primitives (scroll position, form text) |
| Disk (Room/DataStore — Week 14) | yes | yes | everything durable (the source of truth) |

Compose's `rememberSaveable` (Week 08) is the Compose-shaped front door to saved instance state, and `SavedStateHandle` (Week 12) is the `ViewModel`-shaped one. You don't wire these this week — but you should leave this week knowing *why* they exist: because the runtime can destroy your UI two different ways (recreate vs kill), and each survival mechanism covers a different one. That's not a Compose detail; it's a direct consequence of the process and lifecycle model this lecture built.

---

## 5. A concrete trace — from tap to `onCreate`

Let's connect §3 and §4 in one trace, so the process model and the lifecycle are one picture.

The user taps your app icon:

1. The launcher sends an `Intent` to `system_server`'s `ActivityManager`: "start this app's main Activity."
2. `ActivityManager` sees your app has no process, so it asks the **Zygote** to fork one. The fork inherits the pre-loaded runtime and framework classes.
3. The new process runs the framework's `ActivityThread.main()` — your process's real entry point — which sets up the **main thread's message loop** (`Looper`).
4. `ActivityManager` tells your process to instantiate your `Activity` and the framework calls `onCreate()` *on the main thread*. Your `setContent { }` (Compose) or `setContentView(...)` (old world) runs here.
5. `onStart()`, `onResume()` — your UI is visible and interactive.
6. The user rotates the phone: the framework calls `onPause` → `onStop` → `onDestroy` on the *old* Activity, then `onCreate` → `onStart` → `onResume` on a *brand-new* Activity instance. State not saved is lost.
7. The user switches to a heavy game; memory runs low; `system_server` **kills** your background process. No reliable `onDestroy`. Your process is gone.
8. The user returns to your app: back to step 2 — a *fresh fork*, a *fresh* `onCreate`, with only the state you persisted (saved instance state, `ViewModel`+`SavedStateHandle`, disk) restored.

Every box in that trace is a thing the OS does *to* you, on the main thread, at a time it chooses. Your job is to react correctly at each callback and to never assume your process or state outlives the OS's needs. That's the whole lifecycle.

---

## 5b. `Application`, `Context`, and the process-versus-task distinction

Two more pieces of framework vocabulary you need before Phase 2, because they're the scaffolding everything else hangs on.

**The `Application` object.** Every process has exactly one `Application` instance, created *before* any `Activity`, and living for the entire life of the process. Its `onCreate` is the first code of *yours* that runs after the fork. This is where process-wide setup goes — initializing the dependency graph (Hilt's `@HiltAndroidApp` is an `Application` subclass — Week 13), setting up logging, configuring WorkManager. The key fact: `Application.onCreate` runs on *every* process start, including the fresh fork after a process kill — so it's the one place you can rely on to re-establish process-wide state.

**`Context` — the handle to the framework.** Almost every Android API needs a `Context`: to read resources, start an `Activity`, get a system service, access files. A `Context` is your object's handle into the framework — "the environment I'm running in." There are two kinds, and conflating them causes leaks:

- **`Activity` context** — tied to a specific `Activity`'s lifecycle. Use it for UI things (inflating views, showing dialogs) that are inherently screen-scoped. **Never** store it past the `Activity`'s life — holding an `Activity` context in a long-lived object (a singleton, a static field) leaks the entire `Activity` and its view hierarchy, exactly the OOM-inducing leak §2b warned about.
- **`Application` context** (`context.applicationContext`) — tied to the *process*, not a screen. Use it for anything that outlives a single `Activity`: a repository, a database, a WorkManager schedule. It can't leak an `Activity` because it isn't one.

The rule: **UI-scoped work uses the `Activity` context; process-scoped work uses the `Application` context; and storing an `Activity` context in a long-lived place is a leak.** Hilt's `@ApplicationContext` and `@ActivityContext` qualifiers (Week 13) make this distinction explicit in the DI graph.

**Process versus task.** Finally, don't confuse a **process** (the OS-level Linux process running your code — §3) with a **task** (the user-facing back-stack of activities they navigate). One process can host the activities of multiple tasks; the back button navigates the *task*; the OS kills the *process*. They're different axes — the process is about memory and the runtime, the task is about navigation and the user's mental model. Most of the time you think in tasks (navigation — Week 10); but when something is killed or state is lost, you're back in process-land (§3). Keeping the two straight is what lets you reason about "the user pressed back" (task) versus "the OS reclaimed my process" (process) as the genuinely different events they are.

---

## 6. A conceptual tour of AOSP

"The framework" — `Activity`, `Context`, `Intent`, `ActivityManager`, the lifecycle machinery — is not magic. It's the **Android Open Source Project (AOSP)**, a few million lines of mostly Java/Kotlin and C++ that you can read on **Android Code Search** (`cs.android.com`). You will not modify it, but a senior engineer navigates it to answer "how does this *actually* work" questions that docs don't cover.

The high-level map:

- **`frameworks/base`** — the bulk of the Java/Kotlin framework. `Activity.java`, `Context`, the `ActivityManager` client, `View`, the resource system. When you call `startActivity(intent)`, the code that handles it lives here.
- **`ActivityThread.java`** (in `frameworks/base/core/java/android/app/`) — your app process's actual entry point. Reading the top of it once demystifies the lifecycle: you'll see the `main()` the Zygote fork lands in, the `Looper`, and the message handlers that call `onCreate`/`onResume`/etc. on your Activity.
- **The split between *your process* and *`system_server`*.** Some framework code runs *in your process* (your `Activity`, `View` rendering). Some runs in `system_server` (the `ActivityManager` *service*, the `WindowManager`). They communicate over **Binder** (Android's IPC). When you call `getSystemService(...)` you get a *client* that talks across Binder to the real service in `system_server`. Knowing this boundary explains why some calls are cheap (in-process) and some are expensive (a Binder round-trip to another process).
- **The Zygote** (`ZygoteInit`) and **`SystemServer`** — the process-model code from §3, readable in the source.

The senior habit: when a framework behavior surprises you, **read the source on Code Search** instead of guessing from blog posts. "I'm not sure, let me check `ActivityThread`" is the difference between an engineer who repeats folklore and one who knows. You don't need to read it all this week — you need to know it's *there* and how to navigate to the right file.

### A worked source-reading — how `onCreate` actually gets called

To make "read the source" concrete, trace one thing: *how does the framework call your `onCreate`?* Open `ActivityThread` on Code Search and you'll find, conceptually, this shape (simplified — the real code has more bookkeeping):

```java
// ActivityThread.java (AOSP, simplified) — the message loop dispatching to your Activity.
public final class ActivityThread {
    // The Zygote fork lands here. This is your process's real main().
    public static void main(String[] args) {
        Looper.prepareMainLooper();              // set up the main thread's message loop
        ActivityThread thread = new ActivityThread();
        thread.attach(false, ...);               // tell system_server "my process is up"
        Looper.loop();                           // run forever, dispatching messages
    }

    // When system_server says "create this Activity", a message arrives and lands here:
    private void handleLaunchActivity(ActivityClientRecord r, ...) {
        Activity activity = performLaunchActivity(r, ...);   // instantiate + call onCreate
        // ... onStart, onResume dispatched as further messages ...
    }

    private Activity performLaunchActivity(ActivityClientRecord r, ...) {
        Activity activity = mInstrumentation.newActivity(...);   // your Activity, constructed
        activity.attach(appContext, this, ...);                  // wire up its Context
        mInstrumentation.callActivityOnCreate(activity, ...);    // <-- THIS calls your onCreate()
        return activity;
    }
}
```

Three facts you can now *see* instead of take on faith:

1. **Your `onCreate` is a message on the main `Looper`.** `system_server` doesn't call your method directly across the process boundary; it sends a Binder message, `ActivityThread`'s handler picks it up on the main thread, and *then* calls `onCreate`. That's why `onCreate` is guaranteed to run on the main thread (§3b) — it's dispatched from the main `Looper`.
2. **The Zygote fork lands in `main()`.** The `Looper.prepareMainLooper()` and `Looper.loop()` you read here are the same loop §3b described. Your process really does start at this `main`.
3. **The framework constructs your `Activity` for you.** You never `new` your `Activity`; `performLaunchActivity` does, via `Instrumentation`. That's why an `Activity` can't have a constructor with arguments — the framework calls the no-arg one. (And why you pass data via `Intent` extras, not constructor parameters.)

That's a five-minute read that turns three pieces of "Android folklore" into mechanism. You won't do this for everything — but when a behavior genuinely puzzles you, the source is right there, and reading it is the senior move.

---

## 6b. The full picture — what runs where

Before the recap, assemble the whole stack in one diagram, because seeing it together is what makes the individual facts cohere:

```text
┌─────────────────────────────────────────────────────────────────┐
│  system_server  (the OS's brain — a SEPARATE process)            │
│   • ActivityManager — starts/stops/KILLS your process            │
│   • WindowManager, PackageManager, JobScheduler, AlarmManager    │
│   • you reach these via getSystemService() over BINDER (IPC)     │
└───────────────────────────┬─────────────────────────────────────┘
                            │ forks (via Zygote), schedules, kills
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  YOUR app process  (forked from the Zygote)                      │
│                                                                  │
│   ART runtime  — runs classes.dex (AOT/JIT/profiled), small heap │
│   ┌────────────────────────────────────────────────────────┐    │
│   │  main thread — ONE Looper dispatching all UI messages   │    │
│   │    onCreate/onResume/onPause/... (lifecycle callbacks)   │    │
│   │    touch/key events, Compose recomposition + draw        │    │
│   │    BLOCK IT > 5s -> ANR                                   │    │
│   └────────────────────────────────────────────────────────┘    │
│   background threads / coroutine dispatchers (IO, Default)       │
│     — slow work goes HERE, results return to main                │
│                                                                  │
│   Application (1 per process)  •  Activities (the UI hosts)       │
└─────────────────────────────────────────────────────────────────┘
```

Trace any behavior through this picture and it explains itself: a janky animation is allocation churn fighting the ART GC in the main-thread draw path; an ANR is slow work parked on the main `Looper`; a "lost my data" is `system_server` having killed-and-reforked the process; an expensive `getSystemService` call is a Binder round-trip to `system_server`. The whole platform is *this* — one constrained runtime, in a process you don't own, on one sacred main thread, talking to the OS's brain over IPC. Every later week (Compose, Hilt, Room, WorkManager) is a layer drawn *on top of* this diagram, never a replacement for it.

---

## 7. Recap — the one-question habit

The reflex that turns Android from a pile of mysterious callbacks into a machine you reason about is to ask, on every behavior, **"what is the OS doing to my process right now?"**

- My app lost its data on rotation → the OS destroyed and recreated the Activity; I didn't save state.
- My app froze and showed an ANR → I blocked the one main thread for too long; move work off it.
- My background work didn't run → the OS killed my process or throttled background work; schedule it through `WorkManager` (Week 16).
- My app crashed with `OutOfMemoryError` → I exceeded the constrained per-app heap; ART has far less room than the desktop JVM, and a leak fills it fast.
- Startup is slow on first launch → ART hasn't AOT-compiled my hot path yet; a Baseline Profile fixes it (Week 18).
- A `getSystemService` call is surprisingly slow → it's a Binder round-trip to `system_server`, not an in-process call.
- My state survived rotation but not a force-stop → it was in a `ViewModel` (survives config change) not saved instance state or disk (survives a kill).

Each of those is the *same* habit applied: name the OS action (recreate, kill, throttle, IPC, GC) and the cause is immediate. The five weeks of "Kotlin on the JVM" gave you a language; this week gave you the *machine* that language runs on when the target is Android — and the machine is the thing that makes Android Android.

The model: **your Kotlin compiles to JVM bytecode, then to DEX, and runs on ART — a constrained, battery-conscious, profile-guided runtime — inside a process the OS forks from the Zygote and kills at will, on a single sacred main thread, hosted by an Activity whose lifecycle the OS drives, talking to the OS's brain over Binder.** Compose (next week) sits on top of all of this; it changes how you write UI, not the machine underneath. Carry the diagram from §6b into Phase 2 — every Compose, Hilt, Room, and WorkManager concept is a layer on it.

In lecture 2 we cross from the runtime to the *build that produces it*: how `./gradlew assembleDebug` turns your Kotlin into a signed APK full of DEX and compiled resources, the Gradle Kotlin DSL and version catalogs you'll configure it with, the build variants that let one codebase ship as `free` and `pro`, and a first real look at R8. You now know what your code runs on; next you learn exactly how it gets there.
