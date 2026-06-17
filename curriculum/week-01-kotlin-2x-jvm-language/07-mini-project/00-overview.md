# Mini-Project — `kt-stat`: a Gradle Kotlin DSL CLI

This week you build your first real Kotlin program: **`kt-stat`**, a command-line tool that walks a directory, counts source lines per language, and prints a colorized report. It is deliberately **not** an Android app. The point of Week 1 is to meet Kotlin and Gradle on their own terms — on the plain JVM, where you can run `javap` on your own classes and reason about a clean build with no Android toolchain layered on top. By the end you will have stood up a Gradle Kotlin DSL build from an empty directory, written idiomatic Kotlin throughout, and packaged the whole thing as a runnable **fat JAR** you can `java -jar` from anywhere.

This is the toolchain you will use every single week for the next twenty-three. Stand it up properly now and the rest of the course inherits a solid foundation.

---

## What you're building

`kt-stat` is a `cloc`-style line counter. Pointed at a directory, it:

- Recursively walks the tree, skipping noise directories (`.git`, `build`, `node_modules`, `.gradle`, `.idea`).
- Classifies each file by extension into a **language** (`.kt` → Kotlin, `.java` → Java, `.py` → Python, `.swift` → Swift, `.ts`/`.js` → TypeScript/JavaScript, etc.).
- For each file, counts **total lines**, **blank lines**, and **code lines** (total minus blank — we keep comment detection out of scope; see "what's not here").
- Aggregates per language and prints a **colorized table** sorted by code lines descending, with a totals row.

Sample run:

```text
$ java -jar build/libs/kt-stat-all.jar ~/code/my-project

 Language      Files     Code    Blank    Total
 ───────────────────────────────────────────────
 Kotlin           42     5,310      820    6,130
 Java             11     1,204      190    1,394
 TypeScript        7       540       95      635
 ───────────────────────────────────────────────
 TOTAL            60     7,054    1,105    8,159
```

The colorization (green for the highest-count language, dimmed separators, a bold totals row) uses ANSI escape codes — no library, just strings — and degrades gracefully to plain text when output is not a terminal.

---

## Where you're starting from

An empty directory. You build the whole thing, including the Gradle build, from scratch — that's part of the exercise. By the end your tree looks like:

```text
kt-stat/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   ├── wrapper/                      (created by `gradle wrapper`)
│   └── libs.versions.toml            (the version catalog)
├── gradlew  gradlew.bat
└── src/
    ├── main/kotlin/com/crunch/ktstat/
    │   ├── Main.kt                    (entry point + arg parsing)
    │   ├── Language.kt                (extension → Language mapping)
    │   ├── FileWalker.kt             (directory traversal + filtering)
    │   ├── LineCounter.kt            (per-file counting)
    │   ├── Report.kt                 (aggregation + the data classes)
    │   └── AnsiReport.kt             (colorized table rendering)
    └── test/kotlin/com/crunch/ktstat/
        ├── LineCounterTest.kt
        ├── LanguageTest.kt
        └── ReportTest.kt
```

---

## Milestone 1 — Stand up the Gradle Kotlin DSL build (≈ 1.5 h)

Create the build from scratch so you understand every line.

`settings.gradle.kts`:

```kotlin
rootProject.name = "kt-stat"
```

`gradle/libs.versions.toml` — your first version catalog (you'll use this format in every multi-module week from Week 6):

```toml
[versions]
kotlin = "2.2.0"
junit = "5.11.0"

[libraries]
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit.jupiter)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.crunch.ktstat.MainKt")   // note the Kt suffix — top-level main lives on MainKt
}

tasks.test {
    useJUnitPlatform()
}

// The fat-JAR task: bundle the stdlib + your classes into one runnable jar.
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    manifest { attributes["Main-Class"] = "com.crunch.ktstat.MainKt" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}
```

Generate the wrapper and confirm the skeleton builds:

```bash
gradle wrapper --gradle-version 8.10    # one-time, uses your system Gradle
./gradlew build
```

**Decisions you must be able to defend in review:**

- **Why `mainClass = "...MainKt"` and not `...Main`?** Because `main()` is a top-level function (lecture 1, §3), and top-level functions compile to `static` methods on a `FileNameKt` class. Your `main` is in `Main.kt`, so it lives on `MainKt`.
- **Why a version catalog when there's one dependency?** Discipline. From Week 6 you'll have many modules sharing versions; the catalog is the single source of truth. Starting now means it's a habit, not a retrofit.
- **What does the `fatJar` task actually do?** It unpacks every runtime dependency JAR (including `kotlin-stdlib`) and your compiled classes into one archive with a `Main-Class` manifest entry, so `java -jar` has everything on the classpath. (In a real Android-adjacent project you'd use the Shadow plugin; here you write it by hand once to understand it.)

## Milestone 2 — Model the domain with data classes (≈ 1 h)

Idiomatic Kotlin starts with modelling the data. In `Report.kt`:

```kotlin
package com.crunch.ktstat

/** Counts for a single file. */
data class FileStats(val total: Int, val blank: Int) {
    val code: Int get() = total - blank
}

/** Aggregated counts for one language across many files. */
data class LanguageStats(
    val language: String,
    val files: Int,
    val code: Int,
    val blank: Int,
    val total: Int,
)

/** The full report: per-language rows plus a computed totals row. */
data class Report(val rows: List<LanguageStats>) {
    val totals: LanguageStats =
        LanguageStats(
            language = "TOTAL",
            files = rows.sumOf { it.files },
            code = rows.sumOf { it.code },
            blank = rows.sumOf { it.blank },
            total = rows.sumOf { it.total },
        )
}
```

Note what you got for free: `equals`/`hashCode` (so `Report`s compare structurally in tests), `toString` (readable test failures), and `copy` (handy in tests). `code` as a computed `val` with a custom getter shows a property that derives from others. The `sumOf { }` operator replaces a manual accumulation loop. This is the modelling-with-data-classes idiom from lecture 2, §3 in action.

## Milestone 3 — Classify files by language (≈ 1 h)

In `Language.kt`, map extensions to language names. A `when` over the extension is the idiom:

```kotlin
package com.crunch.ktstat

/** Map a file's extension to a language name, or null if we don't count it. */
fun languageFor(fileName: String): String? {
    val ext = fileName.substringAfterLast('.', missingDelimiterValue = "")
    return when (ext.lowercase()) {
        "kt", "kts" -> "Kotlin"
        "java" -> "Java"
        "swift" -> "Swift"
        "py" -> "Python"
        "ts", "tsx" -> "TypeScript"
        "js", "jsx", "mjs" -> "JavaScript"
        "go" -> "Go"
        "rs" -> "Rust"
        "c", "h" -> "C"
        "cpp", "cc", "cxx", "hpp" -> "C++"
        "rb" -> "Ruby"
        "sh", "bash" -> "Shell"
        "md" -> "Markdown"
        else -> null     // unrecognized extension -> not counted
    }
}
```

A file the function returns `null` for is skipped — no entry in the report. Test this thoroughly in `LanguageTest.kt`: `.kt` and `.kts` both map to Kotlin, an unknown extension maps to `null`, a file with no extension (`Makefile`) maps to `null`, case is normalized.

## Milestone 4 — Walk the tree and count lines (≈ 2 h)

In `FileWalker.kt`, recursively collect countable files, skipping noise directories. Kotlin's `File.walkTopDown()` is the idiomatic walker:

```kotlin
package com.crunch.ktstat

import java.io.File

private val SKIP_DIRS = setOf(".git", "build", "node_modules", ".gradle", ".idea", "out")

/** Every regular file under [root] that maps to a known language, skipping noise dirs. */
fun countableFiles(root: File): List<File> =
    root.walkTopDown()
        .onEnter { dir -> dir.name !in SKIP_DIRS }   // prune noise dirs (don't descend)
        .filter { it.isFile }
        .filter { languageFor(it.name) != null }
        .toList()
```

In `LineCounter.kt`, count lines for one file. Read it as a sequence of lines so you don't hold the whole file in memory:

```kotlin
package com.crunch.ktstat

import java.io.File

/** Total and blank line counts for one text file. */
fun countLines(file: File): FileStats {
    var total = 0
    var blank = 0
    file.useLines { lines ->
        for (line in lines) {
            total++
            if (line.isBlank()) blank++
        }
    }
    return FileStats(total = total, blank = blank)
}
```

This is one of the few places a `var` is justified — accumulating a count over a stream of lines. (You *could* do it with `fold` or by reading all lines and using `count { }`, but `useLines` streams the file and the explicit counters are clearest here. Be ready to defend the `var`: it's local, it's an accumulator, and it never escapes. That's the "every `var` justified out loud" bar from lecture 2.)

Handle the unreadable-file case gracefully: a binary file mislabeled with a text extension, or a permission error, should not crash the whole run. Wrap the read in a `runCatching { }` or `try`/`catch` that returns `FileStats(0, 0)` and logs a warning to `stderr`.

## Milestone 5 — Aggregate into a Report (≈ 1 h)

Back in `Report.kt` (or a `buildReport` function), turn the per-file stats into per-language rows. `groupBy` + `map` is the pipeline:

```kotlin
fun buildReport(files: List<File>): Report {
    val rows = files
        .groupBy { languageFor(it.name)!! }          // language -> list of files (non-null by construction)
        .map { (language, langFiles) ->
            val perFile = langFiles.map { countLines(it) }
            LanguageStats(
                language = language,
                files = langFiles.size,
                code = perFile.sumOf { it.code },
                blank = perFile.sumOf { it.blank },
                total = perFile.sumOf { it.total },
            )
        }
        .sortedByDescending { it.code }
    return Report(rows)
}
```

`groupBy`, `map`, `sumOf`, `sortedByDescending` — a four-operator pipeline that would be thirty lines of nested loops and mutable maps in Java. This is the collection-operator idiom (lecture 2, §3) doing real work.

## Milestone 6 — Render the colorized report (≈ 1.5 h)

In `AnsiReport.kt`, render the `Report` as a table with ANSI colors. Keep the color codes as `const val`s and detect whether output is a terminal:

```kotlin
package com.crunch.ktstat

object Ansi {
    const val RESET = "[0m"
    const val BOLD = "[1m"
    const val DIM = "[2m"
    const val GREEN = "[32m"
}

/** Render the report. If [color] is false, emit plain text (e.g. when piped to a file). */
fun renderReport(report: Report, color: Boolean): String = buildString {
    fun colorize(s: String, code: String) = if (color) "$code$s${Ansi.RESET}" else s

    val sep = colorize("─".repeat(48), Ansi.DIM)
    appendLine(colorize(" %-12s %6s %8s %8s %8s".format("Language", "Files", "Code", "Blank", "Total"), Ansi.BOLD))
    appendLine(" $sep")
    report.rows.forEachIndexed { index, row ->
        val line = " %-12s %6d %8s %8s %8s".format(
            row.language, row.files, row.code.thousands(), row.blank.thousands(), row.total.thousands(),
        )
        // Highlight the top language (first row, since sorted by code desc) in green.
        appendLine(if (index == 0) colorize(line, Ansi.GREEN) else line)
    }
    appendLine(" $sep")
    val t = report.totals
    appendLine(colorize(
        " %-12s %6d %8s %8s %8s".format("TOTAL", t.files, t.code.thousands(), t.blank.thousands(), t.total.thousands()),
        Ansi.BOLD,
    ))
}

/** Format an Int with thousands separators: 7054 -> "7,054". An extension function. */
private fun Int.thousands(): String = "%,d".format(this)
```

Note the **extension function** `Int.thousands()` (lecture 2, §3) — it reads as a method on `Int` at the call site, exactly the idiom. `buildString { }` accumulates the output without manual `StringBuilder` boilerplate. The `color` flag makes the renderer testable: tests assert on the plain-text (`color = false`) output and never have to strip escape codes.

## Milestone 7 — Wire `main` and package the fat JAR (≈ 1 h)

In `Main.kt`, parse arguments and drive the pipeline:

```kotlin
package com.crunch.ktstat

import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val path = args.firstOrNull() ?: "."
    val root = File(path)
    if (!root.exists() || !root.isDirectory) {
        System.err.println("kt-stat: '$path' is not a directory")
        exitProcess(1)
    }

    val report = buildReport(countableFiles(root))
    // Color only when stdout is an actual terminal (System.console() is null when piped).
    val useColor = System.console() != null
    print(renderReport(report, color = useColor))
}
```

Build and run the fat JAR:

```bash
./gradlew fatJar
java -jar build/libs/kt-stat-all.jar ~/some/code/directory
```

Point it at this very curriculum repo and read the report. Point it at a project with no source and confirm an empty (totals-only) report doesn't crash. Pipe it to a file (`java -jar ... . > out.txt`) and confirm the output is plain text (no escape codes) because `System.console()` was null.

---

## Acceptance criteria

- [ ] A Gradle Kotlin DSL build (`settings.gradle.kts`, `build.gradle.kts`, a `libs.versions.toml` version catalog) that builds with `./gradlew build`.
- [ ] The domain is modelled with **data classes** (`FileStats`, `LanguageStats`, `Report`); no hand-written `equals`/`hashCode`.
- [ ] File classification is a **`when`** over the extension; the tree walk and aggregation use **collection operators** (`filter`, `groupBy`, `map`, `sumOf`, `sortedByDescending`), not manual loops with mutable accumulators (the one justified `var` is the line counter).
- [ ] At least one **extension function** (`Int.thousands()` or `List<Item>`-style) is used idiomatically.
- [ ] The report is **colorized** with ANSI codes and **degrades to plain text** when `System.console()` is null (piped output).
- [ ] Unreadable/binary files are handled gracefully — no crash on a permission error or a binary mislabeled as text.
- [ ] A **fat JAR** is produced by a Gradle task and runs via `java -jar build/libs/kt-stat-all.jar <dir>`.
- [ ] JUnit 5 tests cover `countLines` (blank vs code counting), `languageFor` (mapping + the null cases), and `buildReport` (aggregation + sort order).
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **A `--by-file` flag** that lists every file with its counts, not just the per-language summary. Parse it with a tiny hand-rolled arg loop (no library yet).
- **Code vs comment lines.** Add naive single-line-comment detection per language (`//` for Kotlin/Java/TS, `#` for Python/Shell) so "code" excludes comment-only lines. Document the limitation (it won't handle block comments or strings-that-look-like-comments) — naming the limitation honestly is the point.
- **A JSON output mode** (`--json`) that emits the report as JSON. Hand-roll the JSON string this week (no kotlinx.serialization until later) — it's a good `buildString`/template exercise.
- **Run `kt-stat` on the kotlinx.coroutines repo** (from resources) and commit the report. Seeing a real codebase's line breakdown is satisfying and a sanity check on your walker.

## What this milestone earns you

You can now stand up a Gradle Kotlin DSL project from an empty directory, write idiomatic Kotlin end to end (data classes, `when`, extensions, collection-operator pipelines, scope functions, string templates), and package it as a runnable artifact. More than that: you did it on the plain JVM, where you can `javap` your own output and *know* the idioms cost nothing. Every later week builds on this toolchain — Week 6 turns this single-module build into a multi-module Android build with the same version-catalog discipline, and the data-class-and-operator modelling style you used here is exactly how you'll model `UiState`, network results, and Room entities for the next twenty-three weeks. The notes app of the Swift track is our `kt-stat`: a small, real thing that proves the foundation is solid before the stakes go up.
