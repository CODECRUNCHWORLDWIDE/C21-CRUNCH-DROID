# Exercise 1 — Read the bytecode

**Goal.** Compile four trivial Kotlin constructs and read the JVM bytecode they generate with `javap -c -p`. Prove to yourself that "Kotlin compiles to ordinary bytecode" is a fact you can see, not a claim you take on faith. This is the confidence skill the whole week rests on — once you can read what the compiler emitted, no syntax can fool you.

**Estimated time.** 40 minutes.

**Prerequisites.** A JDK 21 (`java -version` confirms) and the Kotlin compiler reachable from Gradle. You do **not** need Android, an emulator, or a device. You will use the `javap` tool that ships in your JDK and either the command-line Kotlin compiler or the IDE's "Show Kotlin Bytecode" window.

---

## Step 1 — Stand up the smallest Gradle Kotlin DSL project

In an empty directory, create this layout (the mini-project README walks the same scaffold in more detail; here we keep it minimal):

```text
bytecode-lab/
├── settings.gradle.kts
├── build.gradle.kts
└── src/main/kotlin/com/crunch/lab/Demo.kt
```

`settings.gradle.kts`:

```kotlin
rootProject.name = "bytecode-lab"
```

`build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.2.0"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}
```

`src/main/kotlin/com/crunch/lab/Demo.kt`:

```kotlin
package com.crunch.lab

// (1) a top-level function
fun add(a: Int, b: Int): Int = a + b

// (2) a data class
data class Point(val x: Int, val y: Int)

// (3) a function with a default argument
fun greet(name: String, greeting: String = "Hello"): String = "$greeting, $name!"

// (4) a compile-time constant and a runtime read-only
const val API_VERSION = 3
val startedAt: Long = System.nanoTime()

// a tiny main that touches the const so it isn't dead-code-eliminated
fun main() {
    println(greet("Ada"))
    println(add(2, 3))
    println(Point(1, 2))
    println("API v$API_VERSION started at $startedAt")
}
```

Confirm it compiles and runs:

```bash
./gradlew run
```

You should see the greeting, the sum, the `Point(x=1, y=2)` line, and the version line.

## Step 2 — Find the compiled `.class` files

Gradle puts compiled classes under `build/classes/kotlin/main`:

```bash
find build/classes/kotlin/main -name '*.class'
```

You should see at least:

```text
build/classes/kotlin/main/com/crunch/lab/DemoKt.class
build/classes/kotlin/main/com/crunch/lab/Point.class
```

Note the **`DemoKt`** class — that's the synthesized file-class holding your top-level functions and properties (lecture 1, §3). The file was `Demo.kt`; the class is `DemoKt`.

## Step 3 — Disassemble the top-level function

```bash
javap -c -p build/classes/kotlin/main/com/crunch/lab/DemoKt.class
```

Find the `add` method. You should see something like:

```text
public static final int add(int, int);
  Code:
     0: iload_0
     1: iload_1
     2: iadd
     3: ireturn
```

**Answer in `notes/bytecode.md`:** Is `add` an instance method or a `static` method? What does that tell you about how a top-level function is implemented? What JVM type are the parameters — `int` or `Integer` — and what does that say about boxing for a non-nullable `Int`?

## Step 4 — Disassemble the data class

```bash
javap -c -p build/classes/kotlin/main/com/crunch/lab/Point.class
```

List the method *signatures* (you can use `javap -p` without `-c` for just the signatures). You should find `getX`, `getY`, `component1`, `component2`, `copy`, `copy$default`, `toString`, `hashCode`, and `equals`, plus the constructor.

**Answer in `notes/bytecode.md`:** Which of these did you write, and which did the `data` modifier generate? Pick `equals` and read its bytecode (`javap -c`) — what two fields does it compare? Roughly how many lines of Java would you have written by hand to produce the same five methods?

## Step 5 — Disassemble the default-argument function

Back in `DemoKt`, find both `greet` and `greet$default`:

```text
public static final java.lang.String greet(java.lang.String, java.lang.String);
public static java.lang.String greet$default(java.lang.String, java.lang.String, int, java.lang.Object);
```

**Answer in `notes/bytecode.md`:** Why are there *two* methods? What is the extra `int` parameter on `greet$default` for? When you call `greet("Ada")` (omitting `greeting`), which method does the call site route through? (Hint: decompile `main` or read its bytecode — you'll see the `$default` call with a bitmask.)

## Step 6 — Disassemble the constant vs the runtime val

Read how `API_VERSION` (a `const val`) and `startedAt` (a plain `val`) are referenced in `main`:

```bash
javap -c -p build/classes/kotlin/main/com/crunch/lab/DemoKt.class
```

In `main`'s bytecode, look at how the version line is built versus how `startedAt` is read.

**Answer in `notes/bytecode.md`:** Does `API_VERSION` appear as a literal value baked into `main` (look for `iconst_3` / `bipush 3` / `ldc`), or as a method call to a getter? Does `startedAt` appear as a literal or as an `invokestatic ...getStartedAt()` call? Explain the difference between `const val` and `val` using what you saw.

## Step 7 (optional, faster) — the IDE bytecode window

Open the project in IntelliJ IDEA or Android Studio, put the cursor in `Demo.kt`, and choose **Tools ▸ Kotlin ▸ Show Kotlin Bytecode**. Click **Decompile**. You now see the equivalent Java for the whole file. Compare the decompiled `Point` to your mental model from Step 4 — the generated `equals`/`hashCode`/`copy` are all there in Java form.

---

## Acceptance criteria

- [ ] A Gradle Kotlin DSL project that compiles and runs `Demo.kt` with `./gradlew run`.
- [ ] `notes/bytecode.md` answers the questions from Steps 3–6, quoting *your actual* `javap` output (not invented bytecode).
- [ ] You correctly identify: `add` is `static` (top-level → static on `DemoKt`); `Point`'s `equals`/`hashCode`/`toString`/`componentN`/`copy` are generated; `greet$default` carries a bitmask; `API_VERSION` is inlined while `startedAt` is a getter call.
- [ ] Build with **0 warnings, 0 errors**.
- [ ] (Stretch) You used the IDE's "Show Kotlin Bytecode ▸ Decompile" and compared it to the `javap` output.

## What you just proved

You proved the central claim of lecture 1: Kotlin compiles to ordinary JVM bytecode, and the "magic" features are the compiler writing boilerplate you didn't. A top-level function is a `static` method. A `data class` generates the value-type methods you'd otherwise hand-write. A default argument is a `$default` method with a bitmask. A `const val` is an inlined literal. You can now answer "what does this Kotlin do?" by looking, which is the difference between using a language and understanding it.

---

## Hints (read only if stuck > 10 min)

- **`javap: not found`.** It's in your JDK's `bin`. Use the full path (`$JAVA_HOME/bin/javap`) or ensure your JDK's `bin` is on `PATH`. `./gradlew -q javaToolchains` shows where Gradle's JDK lives if you're unsure which one compiled your code.
- **No `build/classes` directory.** You haven't compiled yet. Run `./gradlew compileKotlin` (or `./gradlew run`) first.
- **`add` shows `Integer`, not `int`.** You probably declared it with a nullable type (`Int?`) or inside a generic. A plain `fun add(a: Int, b: Int): Int` compiles to primitive `int`; check your signature.
- **Can't find `greet$default`.** Use `javap -p` (the `-p` shows private/synthetic members). The `$default` method is synthetic; without `-p` it may be hidden.
- **`const val` won't compile at file scope.** It must be a top-level, `object`, or `companion object` property of a primitive or `String` type, with a compile-time-constant initializer. `const val startedAt = System.nanoTime()` is illegal (not compile-time-constant) — that's exactly why `startedAt` is a plain `val`.
