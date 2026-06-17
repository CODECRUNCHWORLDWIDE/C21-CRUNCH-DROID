// Exercise 3 — A Room migration with MigrationTestHelper
//
// Goal: Seed a v1 database on disk, then open it with a v2 schema that ADDS a
//       column (auto-migration) and a v3 schema that adds a column requiring a
//       data TRANSFORMATION (manual migration). Prove the v1 data survives both
//       upgrades. This is the test most people skip and then ship a data-loss bug.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// This is an INSTRUMENTED test (androidTest). A migration only happens when an
// OLDER database is opened by a NEWER schema; MigrationTestHelper needs the real
// device/emulator SQLite and the EXPORTED schema JSON. (That "you must export the
// schema" requirement is itself part of the lesson — without it, neither
// auto-migrations nor this test can work.)
//
//   1. Ensure exportSchema = true and the room { schemaDirectory(...) } block are
//      set, and that 1.json / 2.json / 3.json are generated and committed.
//   2. Add this file to androidTest.
//   3. Run with `./gradlew :app:connectedDebugAndroidTest`.
//   4. Read the assertions: v1 rows keep their values, the v2 column gets its
//      default, and the v3 column is correctly COMPUTED for migrated rows.
//
// ACCEPTANCE CRITERIA
//
//   [ ] exportSchema is on; 1/2/3.json exist and are committed.
//   [ ] Builds with 0 warnings.
//   [ ] migrate1To2 passes — old rows survive, new column defaulted.
//   [ ] migrate2To3 passes — old rows survive, new column COMPUTED from old data.
//   [ ] You can explain why testing ONLY a fresh install would hide a data-loss bug.
//
// Inline hints are at the bottom. Don't peek for 15 minutes.

package com.crunch.persistence.week14

import androidx.room.testing.MigrationTestHelper
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

// ----------------------------------------------------------------------------
// The schema evolution this test exercises. The actual @Database lives in your
// app; here we describe what each version looks like so the SQL below matches.
//
//   v1: notes(id INTEGER PK, title TEXT, body TEXT, createdAt INTEGER)
//   v2: + isPinned INTEGER NOT NULL DEFAULT 0          <- AutoMigration (additive)
//   v3: + wordCount INTEGER NOT NULL DEFAULT 0,         <- manual Migration:
//          backfilled from body for existing rows          column add + transform
//
// In your @Database you'd declare:
//   @Database(entities=[Note::class], version=3, exportSchema=true,
//             autoMigrations=[AutoMigration(from=1, to=2)])
//   ...and register MIGRATION_2_3 (below) on the builder.
// ----------------------------------------------------------------------------

private const val TEST_DB = "migration-test.db"

// Manual v2 -> v3 migration: add wordCount, then BACKFILL it from body. A
// transformation (computing a value from existing data) is exactly what an
// auto-migration cannot do — so it's a hand-written Migration with SQL.
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN wordCount INTEGER NOT NULL DEFAULT 0")
        // Word count = (spaces in body) + 1 for non-empty bodies. SQLite string math:
        db.execSQL(
            """
            UPDATE notes
            SET wordCount = (LENGTH(body) - LENGTH(REPLACE(body, ' ', '')) + 1)
            WHERE body != ''
            """.trimIndent()
        )
    }
}

class RoomMigrationTest {

    // The helper seeds OLD databases from the exported schema JSON and runs
    // migrations against them, validating the result schema against the next JSON.
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CrunchDatabase::class.java,            // your real @Database class
        emptyList(),                            // auto-migration specs, if any
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_preservesData_andDefaultsNewColumn() {
        // --- Phase 1: the OLD app writes v1 data, then "ships" (we close it). ---
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, createdAt) " +
                    "VALUES (1, 'Groceries', 'milk eggs bread', 0)"
            )
            execSQL(
                "INSERT INTO notes (id, title, body, createdAt) " +
                    "VALUES (2, 'Standup', '', 0)"
            )
            close()
        }

        // --- Phase 2: the NEW app opens the same file at v2 (auto-migration runs). ---
        // runMigrationsAndValidate validates the migrated schema against 2.json.
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, /* validateDroppedTables = */ true)

        // The two v1 notes are STILL HERE, and the new isPinned column defaulted to 0.
        db.query("SELECT id, title, isPinned FROM notes ORDER BY id").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getLong(0))
            assertEquals("Groceries", c.getString(1))
            assertEquals(0, c.getInt(2))         // isPinned defaulted
            c.moveToNext()
            assertEquals("Standup", c.getString(1))
        }
    }

    @Test
    fun migrate2To3_preservesData_andComputesNewColumn() {
        // Seed at v1, migrate to v2 automatically, THEN to v3 with our manual migration.
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, createdAt) " +
                    "VALUES (1, 'Groceries', 'milk eggs bread', 0)"   // 3 words
            )
            execSQL(
                "INSERT INTO notes (id, title, body, createdAt) " +
                    "VALUES (2, 'Empty', '', 0)"                       // 0 words
            )
            close()
        }

        // Run all migrations up to v3: auto (1->2) then manual (2->3).
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        // Old data survived BOTH upgrades, and wordCount was COMPUTED from body.
        db.query("SELECT title, wordCount FROM notes ORDER BY id").use { c ->
            c.moveToFirst()
            assertEquals("Groceries", c.getString(0))
            assertEquals(3, c.getInt(1))         // "milk eggs bread" -> 3 words
            c.moveToNext()
            assertEquals("Empty", c.getString(0))
            assertEquals(0, c.getInt(1))         // empty body -> 0 words (the WHERE guard)
        }
    }
}

// ----------------------------------------------------------------------------
// WHY testing only a fresh install hides the bug (write it before reading):
//
//   A fresh install creates the database directly at the LATEST version, so NO
//   migration code ever runs. If MIGRATION_2_3 is broken — a typo in the ALTER,
//   a wrong backfill, a rename done as drop-and-add — the fresh-install path is
//   green while every existing user's upgrade silently drops the column, computes
//   garbage, or crashes on launch with "A migration from 2 to 3 was required but
//   not found". You MUST seed an old database (createDatabase(TEST_DB, 1)) and
//   open it at the new version (runMigrationsAndValidate(TEST_DB, 3, ...)) to
//   exercise the migration. That is what Phase 1 + Phase 2 above do.
//
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - "Cannot find the schema file" — exportSchema isn't on, or the schemas/ JSON
//   isn't on the test's asset path. Add the room { schemaDirectory(...) } block,
//   rebuild so 1/2/3.json generate, and make sure the androidTest source set can
//   see schemas/ (the Room Gradle plugin wires this for you in recent versions).
//
// - runMigrationsAndValidate also VALIDATES the post-migration schema against the
//   target version's JSON. If your manual migration produces a different shape than
//   the @Entity declares (e.g. wrong column type), the test fails on validation —
//   that's the helper catching a migration that "ran" but produced the wrong schema.
//
// - The SQLite word-count trick: LENGTH(body) - LENGTH(REPLACE(body,' ','')) counts
//   spaces; +1 turns spaces into words. The WHERE body != '' guard keeps empty
//   bodies at 0 instead of 1.
//
// - Auto-migration (1->2) needs the AutoMigration(from=1,to=2) in your @Database
//   annotation and BOTH 1.json and 2.json committed; Room diffs them to generate
//   the ALTER. The manual migration (2->3) is the `object : Migration(2,3)` above.
//
// ----------------------------------------------------------------------------
