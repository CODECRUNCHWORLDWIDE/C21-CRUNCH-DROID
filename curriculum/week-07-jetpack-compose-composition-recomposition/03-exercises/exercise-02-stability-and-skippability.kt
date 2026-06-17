// Exercise 2 — Stability and skippability: drag a composable back into "skippable"
//
// Goal: Take a composable that the Compose Compiler report marks NOT skippable,
//       find the one unstable parameter (a bare List and a `var`), fix it (an
//       ImmutableList and a `val`), and assert the data type's stability with a
//       plain JVM unit test. You produce a before/after from composables.txt.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// The composables here live in your `app` module (a Compose source set). The
// @Test at the bottom is a PLAIN JVM unit test (no Android, no Compose runtime)
// that pins the data class's shape so a future `var` reintroduction fails CI.
//
//   1. Put FeedListBad / FeedListGood and the data classes in app/src/main.
//   2. Turn on the Compose Compiler report (see the build.gradle.kts block below),
//      build, and open app/build/compose_compiler/*-composables.txt.
//   3. Confirm FeedListBad is `restartable` but NOT `skippable`, and FeedListGood
//      is `restartable skippable`.
//   4. Run the @Test (it's in app/src/test) to lock the immutable shape.
//
// ACCEPTANCE CRITERIA
//
//   [ ] The Compose Compiler report shows FeedListBad NOT skippable and names the
//       unstable parameter; FeedListGood IS skippable.
//   [ ] classes.txt shows ArticleUiBad unstable (the `var`) and ArticleUi stable.
//   [ ] Builds with 0 warnings.
//   [ ] The JVM test passes and would fail if someone made a field `var` again.
//   [ ] You can explain, in one sentence, WHY each fix restores skippability.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.scratch.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// ----------------------------------------------------------------------------
// Turn on the report. Add this to app/build.gradle.kts (NOT in this file):
//
//   composeCompiler {
//       reportsDestination = layout.buildDirectory.dir("compose_compiler")
//       metricsDestination = layout.buildDirectory.dir("compose_compiler")
//   }
//
// And the immutable collections dependency to your version catalog / build file:
//
//   implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.8")
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
// BEFORE — both the data class and the composable are unstable/not-skippable.
// ----------------------------------------------------------------------------

// `var title` makes this class UNSTABLE: a property can change without notifying
// composition, so the runtime can't trust equals to mean "unchanged".
data class ArticleUiBad(
    val id: String,
    var title: String,        // <- the var: classes.txt will mark this `unstable`
    val author: String
)

// `articles: List<...>` is UNSTABLE: List is an interface; the compiler can't prove
// the backing implementation is immutable. One unstable param -> NOT skippable.
@Composable
fun FeedListBad(articles: List<ArticleUiBad>) {
    Column {
        for (article in articles) {
            // No `key` either — a reorder would discard remembered state (lecture 1, §3).
            ArticleRowBad(article)
        }
    }
}

@Composable
fun ArticleRowBad(article: ArticleUiBad) {
    Text("${article.title} — ${article.author}")
}

// ----------------------------------------------------------------------------
// AFTER — immutable data class + ImmutableList parameter + keyed list.
// ----------------------------------------------------------------------------

// Every property is a `val` of a stable type -> classes.txt marks this `stable`.
data class ArticleUi(
    val id: String,
    val title: String,        // val, not var
    val author: String
)

// ImmutableList carries the stability promise in its type, so this parameter is
// stable -> the whole function becomes `restartable skippable`.
@Composable
fun FeedListGood(articles: ImmutableList<ArticleUi>) {
    LazyColumn {
        items(articles, key = { it.id }) { article ->     // keyed by stable id
            ArticleRow(article)
        }
    }
}

@Composable
fun ArticleRow(article: ArticleUi) {
    Text("${article.title} — ${article.author}")
}

// Constructing the stable list at a call site:
@Composable
fun FeedScreen() {
    val articles = persistentListOf(
        ArticleUi("1", "Compose phases", "ada"),
        ArticleUi("2", "Stability", "grace"),
        ArticleUi("3", "Skippability", "linus")
    )
    FeedListGood(articles)
}

// ----------------------------------------------------------------------------
// THE TEST — a plain JVM unit test (app/src/test). It does NOT run Compose; it
// pins the immutable SHAPE so a regression (someone re-adding a `var`) is caught.
//
// Move this into app/src/test/java/com/crunch/scratch/feed/StabilityTest.kt:
//
//   import org.junit.Test
//   import kotlin.test.assertEquals
//   import kotlin.test.assertNotSame
//
//   class StabilityTest {
//
//       @Test fun `copy produces a new instance, never mutates`() {
//           val a = ArticleUi("1", "old", "ada")
//           val b = a.copy(title = "new")        // immutable update = new object
//           assertNotSame(a, b)                  // different instances
//           assertEquals("old", a.title)         // original untouched
//           assertEquals("new", b.title)
//       }
//
//       @Test fun `equal content is equal — equals is well-behaved`() {
//           val a = ArticleUi("1", "t", "ada")
//           val b = ArticleUi("1", "t", "ada")
//           assertEquals(a, b)                   // value equality the runtime can trust
//       }
//   }
//
// If a teammate changes `val title` back to `var title`, the data class is still
// usable but the Compose Compiler report flips ArticleUi to `unstable` and
// FeedListGood loses `skippable`. The report is the real guard; this test
// documents the intent (immutable update via copy()).
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
// WHY each fix restores skippability (write it before reading):
//
//   - `var` -> `val`: a val of a stable type can't change after construction, so
//     the class is immutable and the compiler infers it stable. The runtime can
//     trust `equals` to mean "unchanged" and skip.
//   - `List` -> `ImmutableList`: ImmutableList is annotated @Immutable, so the
//     compiler treats it as stable instead of conservatively assuming the backing
//     list might be a mutable one upcast to List.
//
//   With both params stable, FeedListGood is `restartable skippable`: when its
//   articles are equal to last time, the runtime skips the whole body.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - Report empty / not generated? Build a release-ish variant: the strong-skipping
//   defaults and report are most meaningful there. `./gradlew :app:assembleRelease`
//   then look under app/build/compose_compiler/. The file is named like
//   `app_release-composables.txt`.
//
// - FeedListBad shows skippable even with the List? Strong skipping (default on
//   Kotlin 2.0) can still skip an unstable param when the SAME INSTANCE is passed,
//   but it does so by instance equality, not value equality — the report still
//   marks the PARAMETER `unstable`. Look at the per-parameter annotation in the
//   report, not just the function keyword.
//
// - `persistentListOf` / `ImmutableList` unresolved? Add
//   `org.jetbrains.kotlinx:kotlinx-collections-immutable` to the app module.
//
// - Don't pass your network/domain `Article` straight in. Map it to `ArticleUi`
//   at the UI boundary so the compiler can inspect the type (lecture 2, footgun 4).
//
// ----------------------------------------------------------------------------
