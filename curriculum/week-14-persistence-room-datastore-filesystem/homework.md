# Week 14 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 14 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Kotlin 2.1+, Room 2.7+, DataStore 1.1+, KSP, JDK 17, AGP 8.7+. Every problem must build with **0 warnings**.

---

## Problem 1 — Read the actual SQLite Room wrote

**Problem statement.** Using the exercise-1 app (or your mini-project), add at least five notes, then locate and inspect the on-disk SQLite database. Write your findings into `notes/db-anatomy.md`: the path to the `.db` file, the list of tables (`.tables`), the columns of the notes table (`.schema notes` or `PRAGMA table_info(notes)`), and one sentence on what `room_master_table` and `android_metadata` are for. Add a second sentence: which framework wrote those tables, and how do you know?

**Acceptance criteria.**

- `notes/db-anatomy.md` exists with the path, table list, column list, and the two sentences.
- The tables and columns are quoted from your actual database, not invented.
- Committed.

**Hint.** `adb shell run-as <pkg> sqlite3 databases/crunch.db ".tables"`, then `PRAGMA table_info(notes)`. `room_master_table` holds the schema hash Room checks on open; that hash is your evidence for "which framework". Or use the Database Inspector.

**Estimated time.** 30 minutes.

---

## Problem 2 — `EXPLAIN QUERY PLAN` an indexed vs. unindexed query

**Problem statement.** Seed a store with 20,000 notes. Run `EXPLAIN QUERY PLAN` on a `WHERE topic = ?` query *without* an index on `topic`, record the plan, add `@Index(value = ["topic"])`, rebuild, and run it again. Write both plans and a one-sentence explanation into `notes/query-plan.md`.

**Acceptance criteria.**

- `notes/query-plan.md` records the unindexed plan (`SCAN notes`) and the indexed plan (`SEARCH notes USING INDEX ...`), plus a sentence explaining what the index changed.
- Committed.

**Hint.** Use the Database Inspector to run `EXPLAIN QUERY PLAN SELECT * FROM notes WHERE topic = 'kotlin'`. Without the index you'll see `SCAN`; with it, `SEARCH ... USING INDEX index_notes_topic`. The index turns a full-table scan into a B-tree lookup.

**Estimated time.** 35 minutes.

---

## Problem 3 — `fetch().size` vs `COUNT(*)`

**Problem statement.** Seed an in-memory store with 20,000 rows. Measure (with `measureNanoTime`) and explain the difference between `dao.all().size` and `dao.count()` (a `SELECT COUNT(*)` query) for getting the number of rows. Record both timings in `notes/count-timing.md` and state which one materialises objects and which doesn't.

**Acceptance criteria.**

- An instrumented test or script that seeds 20,000 rows and times both approaches.
- `notes/count-timing.md` records both timings and the one-sentence explanation (`COUNT(*)` runs in SQLite and builds zero objects; `all().size` materialises every row across the Cursor to count them).
- Committed.

**Hint.** `@Query("SELECT COUNT(*) FROM notes") suspend fun count(): Int` vs `dao.all().size`. Make the table large enough that materialising every row is visibly more expensive than counting in SQL.

**Estimated time.** 35 minutes.

---

## Problem 4 — Proto DataStore round-trip

**Problem statement.** Define a `UserPreferences` proto with at least three typed fields (a bool, an enum, an int), wire a Proto DataStore, write all three, read them back as a `Flow`, and confirm they survive a relaunch. Drive one of them (the bool) from a UI toggle.

**Acceptance criteria.**

- A `.proto` with three typed fields, a `Serializer`, and a `DataStore<UserPreferences>` provided via Hilt.
- A repository exposing `preferences: Flow<UserPreferences>` and `suspend` setters using `updateData`.
- The bool is toggled in the UI, force-quit, and survives the relaunch (verify by eye).
- 0 warnings. Committed.

**Hint.** `dataStore.updateData { it.toBuilder().setDarkTheme(true).build() }`. Proto DataStore writes are atomic and coroutine-safe — no `apply()`, no main-thread I/O. The relaunch test is `adb shell am force-stop` then reopen.

**Estimated time.** 50 minutes.

---

## Problem 5 — A manual migration with a transformation

**Problem statement.** Extend your schema with a **v3** that adds `var category: String = "general"` to `Note` and a **manual `Migration(2, 3)`** whose SQL backfills `category` from `topic` (e.g. `category = 'tech'` where `topic IN ('kotlin','compose','room')`, else `'general'`). Write a `MigrationTestHelper` test that seeds v2 data, migrates to v3, and asserts `category` is correctly backfilled.

**Acceptance criteria.**

- A v3 entity with `category`, a `Migration(2, 3)` with `ALTER TABLE` + an `UPDATE ... SET ... WHERE` backfill, and the migration registered on the builder.
- A passing `MigrationTestHelper` test: seed v2 notes with known topics, migrate to v3, assert `category` matches the backfill rule.
- `exportSchema` on; `3.json` committed.
- 0 warnings. Committed.

**Hint.** Test the *upgrade* path (seed an old version, open at v3), not a fresh v3 install. `runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)` runs the migration and validates the schema against `3.json`.

**Estimated time.** 50 minutes.

---

## Problem 6 — A scoped-storage backup and restore

**Problem statement.** Write a `BackupManager` that exports all notes to a JSON file in `filesDir` (private storage, no permission) and restores from it. Confirm the backup file is *not* visible to other apps and is wiped on uninstall. Write a one-line note in `notes/backup.md` on why this needs no storage permission.

**Acceptance criteria.**

- `backupTo(context): File` writes notes JSON to `filesDir` on `Dispatchers.IO`; `restoreFrom(file)` reads it back and re-inserts.
- `notes/backup.md` states why `filesDir` needs no permission (it's the app's private sandbox) and one sentence on scoped storage post-Android-11.
- The attachment path (if any) is stored in the row, not the blob inline.
- 0 warnings. Committed.

**Hint.** `File(context.filesDir, "backup.json").writeText(json)` — no `WRITE_EXTERNAL_STORAGE`, because `filesDir` is private. Do the I/O on `Dispatchers.IO` (injected, Week 13), never the main thread.

**Estimated time.** 50 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, code is idiomatic Kotlin/Room, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. `all().size` left in, a missing `@Index`, a relation query without `@Transaction`, Preferences DataStore where Proto fit better). |
| 3 | Works, but misses one criterion (e.g. query plan recorded but not explained, migration tested only on fresh install, a blob stored inline). |
| 2 | Compiles and partially works; a core idea is wrong (filters in Kotlin where a `WHERE` was required; a rename done as drop-and-add; `WRITE_EXTERNAL_STORAGE` requested). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for `fallbackToDestructiveMigration()` in a non-throwaway build (data loss); **−2** for a blocking query on the main thread or `allowMainThreadQueries()`; **−1** for filtering in Kotlin where a `WHERE` clause was the point.

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — "filter in SQLite, not in Kotlin" (problems 2, 3) and "the migration upgrade path" (problem 5) — so re-run exercises 2 and 3 before resubmitting.
