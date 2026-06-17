// Exercise 2 — A verified WHERE-clause query (in SQLite) vs. naive load-all-then-filter
//
// Goal: Prove, with two numbers, that filtering with a WHERE clause inside a Room
//       @Query is dramatically cheaper than loading every row and filtering the
//       list in Kotlin. Same answer, very different cost. Also confirm a Flow
//       query re-emits on change and that COUNT(*) builds zero objects.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// This is an INSTRUMENTED test (androidTest). Room's query verification and its
// SQLite engine need a real device/emulator, so this can't run as a plain JVM
// unit test. It builds its own IN-MEMORY Room database, so it never touches your
// real store and needs no app UI.
//
//   1. Add this file to your androidTest source set.
//   2. Run with `./gradlew :app:connectedDebugAndroidTest` or the green arrow.
//   3. Read the printed timings in the test log (logcat). The WHERE-clause query
//      should be many times faster; the assertions enforce "correct AND cheaper".
//
// ACCEPTANCE CRITERIA
//
//   [ ] Builds with 0 warnings.
//   [ ] All tests pass.
//   [ ] The log prints two timings, and the WHERE-clause query is not slower than
//       the load-all-then-filter (same row count, no longer).
//   [ ] You can explain, in one sentence, WHY the WHERE-clause query wins.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.persistence.week14

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.system.measureNanoTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ----------------------------------------------------------------------------
// The entity under test. We index `topic` so the WHERE-clause query can use it.
// ----------------------------------------------------------------------------

@Entity(
    tableName = "articles",
    indices = [androidx.room.Index(value = ["topic"])]
)
data class Article(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val topic: String,
    val views: Int
)

@Dao
interface ArticleDao {

    @Insert
    suspend fun insertAll(articles: List<Article>)

    // Load EVERYTHING — the naive path filters this in Kotlin.
    @Query("SELECT * FROM articles")
    suspend fun all(): List<Article>

    // Filter in SQLite with a WHERE clause — only matching rows materialise.
    @Query("SELECT * FROM articles WHERE topic = :topic")
    suspend fun byTopic(topic: String): List<Article>

    // Count in SQLite — builds zero objects.
    @Query("SELECT COUNT(*) FROM articles WHERE topic = :topic")
    suspend fun countByTopic(topic: String): Int

    // A reactive read to prove Flow re-emits on change.
    @Query("SELECT COUNT(*) FROM articles")
    fun observeCount(): kotlinx.coroutines.flow.Flow<Int>
}

@Database(entities = [Article::class], version = 1, exportSchema = false)
abstract class ArticleDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
}

// ----------------------------------------------------------------------------
// The test
// ----------------------------------------------------------------------------

class FlowQueryVsNaiveTest {

    private lateinit var db: ArticleDatabase
    private lateinit var dao: ArticleDao

    @BeforeTest
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // In-memory: a real, fully-functional Room database that lives only in RAM.
        // Perfect for tests — fast, isolated, never touches disk.
        db = Room.inMemoryDatabaseBuilder(context, ArticleDatabase::class.java).build()
        dao = db.articleDao()
    }

    @AfterTest
    fun tearDown() = db.close()

    /** Seed `count` articles, of which exactly `matching` have topic == "kotlin". */
    private suspend fun seed(count: Int, matching: Int) {
        val articles = (0 until count).map { i ->
            Article(
                title = "Article $i",
                topic = if (i < matching) "kotlin" else "swift",
                views = i % 1000
            )
        }
        dao.insertAll(articles)
    }

    @Test
    fun whereClause_and_naive_return_the_same_rows() = runBlocking {
        seed(count = 5_000, matching = 37)

        // Naive: load everything, filter the list in Kotlin.
        val naive = dao.all().filter { it.topic == "kotlin" }

        // WHERE clause: SQLite does the filtering; only matches materialise.
        val whereClause = dao.byTopic("kotlin")

        assertEquals(37, naive.size)
        assertEquals(37, whereClause.size)
        assertEquals(naive.size, whereClause.size)
    }

    @Test
    fun whereClause_query_is_not_slower_than_naive() = runBlocking {
        seed(count = 50_000, matching = 50)

        val naiveNanos = measureNanoTime {
            dao.all().filter { it.topic == "kotlin" }
        }
        val whereNanos = measureNanoTime {
            dao.byTopic("kotlin")
        }

        println("naive  (load-all + filter): ${naiveNanos / 1_000_000.0} ms")
        println("where  (filter in SQLite):  ${whereNanos / 1_000_000.0} ms")

        // Same answer...
        assertEquals(dao.byTopic("kotlin").size, dao.all().filter { it.topic == "kotlin" }.size)
        // ...and the WHERE-clause version should not be slower. (Generous margin so
        // CI variance never flakes the test: allow the indexed query up to the
        // naive time. On a real 50k store it's typically far faster.)
        assertTrue(whereNanos <= naiveNanos * 2,
            "WHERE-clause query ($whereNanos ns) should not be materially slower than naive ($naiveNanos ns)")
    }

    @Test
    fun count_uses_sqlite_and_builds_zero_objects() = runBlocking {
        seed(count = 10_000, matching = 250)
        // COUNT(*) returns the number without materialising any Article objects.
        assertEquals(250, dao.countByTopic("kotlin"))
    }

    @Test
    fun flow_query_reemits_when_the_table_changes() = runBlocking {
        // First emission: empty table.
        assertEquals(0, dao.observeCount().first())
        // Insert, then the Flow's NEXT emission reflects the change — Room's
        // InvalidationTracker re-runs the query because the articles table changed.
        seed(count = 5, matching = 5)
        assertEquals(5, dao.observeCount().first())
    }
}

// ----------------------------------------------------------------------------
// WHY the WHERE-clause query wins (write this in your own words before reading):
//
//   The WHERE clause is evaluated INSIDE SQLite, using the index on `topic`, so
//   only the matching rows are ever read and turned into Kotlin Article objects.
//   The naive version runs SELECT * (a full table scan), reads every row out of
//   the Cursor into a full Article object, hands you a 50,000-element list, and
//   then throws ~49,950 of them away. You paid to materialise the whole table to
//   keep 0.1% of it. COUNT(*) is the extreme case: it returns a number and builds
//   zero objects at all.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - Room throws if you call a blocking/suspend DAO method on the wrong thread.
//   `runBlocking { }` in a test is fine — it provides a coroutine scope and the
//   in-memory database allows it. In the APP, never runBlocking on the main thread.
//
// - If `flow_query_reemits` hangs, you're collecting the Flow forever. Use
//   `.first()` to take a single emission, or `Turbine` (Week 5) for richer Flow
//   assertions. Here `.first()` after the insert grabs the post-change emission.
//
// - The @Index on `topic` is what lets the WHERE query avoid a full scan. Remove
//   it and re-run `EXPLAIN QUERY PLAN SELECT * FROM articles WHERE topic = 'kotlin'`
//   in the Database Inspector: without the index it says "SCAN articles"; with it,
//   "SEARCH articles USING INDEX". That difference IS the speedup.
//
// - If the timing test flakes on a tiny dataset, increase `count` to 50_000+ so
//   the gap dwarfs the noise. At small N both are sub-millisecond.
//
// ----------------------------------------------------------------------------
