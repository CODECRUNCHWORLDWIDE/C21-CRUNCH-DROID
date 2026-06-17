# Exercise 1 — Disassemble a suspend function

**Goal.** Take the lecture's central claim — "a `suspend` function is a continuation-passing state machine" — and *see it with your own eyes* in the bytecode. You will write a small function with two suspend calls, compile it, run `javap -c -p`, and point at the synthesised `Continuation` parameter, the integer `label`, and the suspend points. After this exercise, coroutines are no longer magic.

**Estimated time.** 40 minutes.

**Prerequisites.** JDK 21 with `javap` on the PATH, a Gradle Kotlin/JVM project with `kotlinx-coroutines-core`. You did this once in Week 1 with a trivial function; now we point it at a `suspend` function.

---

## Step 1 — A Gradle Kotlin/JVM project

If you don't have one, scaffold a minimal application project. The only dependency you need:

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.0"
    application
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
application { mainClass.set("com.crunch.droid.MainKt") }
kotlin { jvmToolchain(21) }
```

Confirm it builds with `./gradlew build` before you touch anything.

## Step 2 — Write a function with exactly two suspend points

Create `src/main/kotlin/com/crunch/droid/Suspendable.kt`. Keep it small so the disassembly is readable:

```kotlin
package com.crunch.droid

import kotlinx.coroutines.delay

// Two suspend points (the two `delay` calls) and one local (`first`) that must
// survive across a suspension. That local is what forces a state-machine field.
suspend fun twoStep(): Int {
    val first = stepOne()      // suspend point 1
    delay(10)                  // suspend point 2 (also a suspension)
    return first + stepTwo()   // `first` is used AFTER the suspension -> it survives
}

suspend fun stepOne(): Int {
    delay(10)
    return 21
}

suspend fun stepTwo(): Int {
    delay(10)
    return 21
}
```

The detail that makes this exercise work: **`first` is read *after* `delay(10)`.** That forces the compiler to preserve `first` across the suspension, which it does by making `first` a *field of the generated state machine* — exactly the thing you're going to find in the bytecode. If you returned before suspending again, the compiler could keep `first` on the stack and you'd see less.

## Step 3 — Compile and locate the class file

```bash
./gradlew compileKotlin
# The compiled class lands here (the file name is the source file + "Kt"):
find build -name "SuspendableKt.class"
```

## Step 4 — Disassemble with `javap`

```bash
javap -c -p -classpath build/classes/kotlin/main com.crunch.droid.SuspendableKt
```

`-c` prints the bytecode, `-p` shows private members (the state machine is synthetic and private). Read the output looking for four things:

1. **The method signature.** Find `twoStep`. Its descriptor is **not** `()I` (no-arg returning int). It is something like:

   ```
   public static final twoStep(kotlin.coroutines.Continuation<? super java.lang.Integer>): java.lang.Object
   ```

   There's your extra parameter — a `Continuation` — and the return type is `Object` (so it can return either the `Integer` result *or* the `COROUTINE_SUSPENDED` sentinel). This is §3 of lecture 1, in the bytecode.

2. **The synthetic continuation class.** Near the top you'll see a nested `class twoStep$1` (or similar) extending `kotlin.coroutines.jvm.internal.ContinuationImpl`. That is the state machine. Disassemble it too:

   ```bash
   javap -c -p -classpath build/classes/kotlin/main 'com.crunch.droid.SuspendableKt$twoStep$1'
   ```

3. **The `label` field and the `L$0` field.** Inside the continuation class you'll find an `int label;` and an `Object L$0;`. `label` is the state-machine cursor (which suspend point we're at); `L$0` is the spilled local — *that is where `first` is stored across the suspension.*

4. **The `tableswitch` (or `lookupswitch`).** In `twoStep`'s bytecode you'll see a `tableswitch` on `label`. That is the `when (label)` from lecture 1, §3 — the jump table that resumes at the right suspend point.

## Step 5 — Annotate what you found

Write `notes/disassembly.md` with four short bullets, each quoting the *actual* line from your `javap` output (not invented):

- The `twoStep` signature line showing the `Continuation` parameter and the `Object` return.
- The name of the synthetic continuation class.
- The `label` and `L$0` field declarations.
- The `tableswitch` instruction line.

Then one sentence in your own words: *why is `first` stored in `L$0` instead of a local slot?*

---

## Acceptance criteria

- [ ] A `twoStep()` `suspend` function with two suspend points and a local used after a suspension.
- [ ] `javap -c -p` output captured, showing the `Continuation` parameter and `Object` return on `twoStep`.
- [ ] `notes/disassembly.md` quotes the four real lines (signature, continuation class name, `label`/`L$0` fields, `tableswitch`) from *your* output.
- [ ] One correct sentence explaining why `first` is spilled to a field (it must survive a thread-changing resume, so it can't live on the stack).
- [ ] Committed.

## What you just proved

You proved lecture 1's central claim is not a metaphor: your `suspend fun` really does compile to a function with a hidden `Continuation` parameter, an `Object` return that can carry a "suspended" sentinel, a private state-machine class with a `label` cursor and spilled-local fields, and a `tableswitch` that resumes at the right point. Every confusing thing about coroutines this week — locals surviving a `delay`, resuming on a different thread, why a `suspend` function can only be called from a coroutine — traces back to this transform. You can now read it.

---

## Hints (read only if stuck > 10 min)

- **`javap` says "class not found."** Use the right classpath root (`build/classes/kotlin/main`) and the fully-qualified name with `Kt` suffix: `com.crunch.droid.SuspendableKt`.
- **No `tableswitch`, just one path.** Your local probably isn't used after the suspension, so the compiler didn't need to spill it. Make sure `first` is read *after* `delay(10)` (it is in the code above).
- **The continuation class name has `$` in it.** Quote it for the shell: `'...SuspendableKt$twoStep$1'`. Unquoted, the shell eats the `$1`.
- **Output is huge.** Pipe to a file and search: `javap -c -p ... > dis.txt` then open `dis.txt` and search for `twoStep`, `label`, `tableswitch`.
- **You see `INVOKESTATIC ... getCOROUTINE_SUSPENDED`.** Good — that's the sentinel from lecture 1, §3 being loaded to compare against. Note it; it's the "did I suspend?" check.
