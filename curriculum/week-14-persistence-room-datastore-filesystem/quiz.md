# Week 14 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 15. Answer key with explanations at the bottom — don't peek.

---

**Q1.** Which statement best describes Room's relationship to SQLite in 2026?

- A) Room is a brand-new storage engine that replaced SQLite; the two share no code.
- B) Room is a compile-time-verified, annotation-driven front end over SQLite — the same engine, the same `.db` file underneath.
- C) SQLite is a front end over Room; Room is the lower-level engine.
- D) They are unrelated; Room uses its own binary format.

---

**Q2.** What is the single biggest correctness win of a Room `@Query` over a raw `db.rawQuery(...)`?

- A) It's faster at runtime.
- B) The SQL is **verified at compile time** against the schema — a typo in a column name is a build error, not a runtime `Cursor` crash.
- C) It uses less memory.
- D) It encrypts the data.

---

**Q3.** You want a DAO method that keeps a list in sync with the table. What return type, and why?

- A) `List<Note>` — Room re-queries automatically.
- B) `Flow<List<Note>>` — Room re-runs the query and re-emits whenever the queried tables change, via the `InvalidationTracker`.
- C) `LiveData<Note>` — the only reactive option.
- D) `suspend fun List<Note>` — suspend makes it reactive.

---

**Q4.** You need to store an `Instant` in a Room column. SQLite doesn't know `Instant`. What's the mechanism?

- A) Room stores any type automatically.
- B) A `@TypeConverter` that converts `Instant` to/from a primitive (e.g. epoch-millis `Long`), registered with `@TypeConverters`.
- C) Store it as a `@Embedded`.
- D) You can't; use a `String` everywhere.

---

**Q5.** A `@Relation` query that loads notes with their tags requires which annotation, and why?

- A) `@Embedded`, to flatten the tags.
- B) `@Transaction`, because Room satisfies a relation with more than one query and they must be atomic to avoid a torn read.
- C) `@Ignore`, to skip the relation.
- D) None; relation queries are single queries.

---

**Q6.** Given a 50,000-row table, what's the practical difference between loading everything and filtering in Kotlin versus a `WHERE`-clause query (both returning the same matches)?

- A) No difference; Room optimizes them the same.
- B) The Kotlin filter materialises all 50,000 objects across the Cursor then keeps a few; the `WHERE`-clause query filters in SQLite (using an index if present) and materialises only the matches — far cheaper.
- C) The Kotlin filter is cheaper because it avoids SQL.
- D) The `WHERE` clause is slower due to parsing overhead.

---

**Q7.** For a structured, typed settings object with several fields and defaults, which storage should you choose?

- A) `SharedPreferences`.
- B) A Room table with one row.
- C) **Proto DataStore** — a typed `.proto`-defined object, read/written as a `Flow`, with defaults.
- D) A JSON file you parse by hand.

---

**Q8.** Where should you store a note's image attachment, and what goes in the Room row?

- A) Store the image bytes as a `BLOB` column in the row.
- B) Store the file in the app's private storage (`filesDir`) and keep the **path** in the row — a blob inline bloats every query that reads the row.
- C) Store it in `SharedPreferences`.
- D) Request `WRITE_EXTERNAL_STORAGE` and write it anywhere on external storage.

---

**Q9.** You add `val isPinned: Boolean = false` to a shipped `@Entity`. What kind of Room migration is this?

- A) A manual `Migration` with hand-written SQL is required.
- B) An additive change an `AutoMigration(from, to)` can generate — Room writes the `ALTER TABLE ... ADD COLUMN ... DEFAULT 0`.
- C) Impossible without deleting the database.
- D) It requires renaming the table.

---

**Q10.** You need to add `wordCount` and **backfill** it from each note's existing `body`. Why can't an `AutoMigration` do this?

- A) Auto-migrations can't add columns.
- B) Backfilling is a data *transformation* (computing a value from existing data), which Room can't infer — it needs a manual `Migration` with `UPDATE ... SET` SQL.
- C) `wordCount` is too big a column.
- D) It can; no manual migration is needed.

---

**Q11.** Why does testing only a fresh install hide a broken migration?

- A) Fresh installs run all migrations, so they always pass.
- B) A fresh install creates the database directly at the latest version, so **no** migration code runs — a broken upgrade path stays green. You must seed an old version and open it with the new schema (`MigrationTestHelper`).
- C) Fresh installs use an in-memory database that can't migrate.
- D) It doesn't; a fresh install fully exercises migrations.

---

**Q12.** A `Flow<List<Note>>` query "re-emits for no reason" — you see a new emission even when the matching rows didn't change. What's the cause and a fix?

- A) A bug in Room; report it.
- B) Room's `InvalidationTracker` invalidates at **table** granularity — any write to the `notes` table re-runs the query. Add `.distinctUntilChanged()` (or rely on `StateFlow` equality) to suppress equal re-emissions.
- C) The query is wrong; remove the `WHERE`.
- D) Autosave is firing.

---

**Q13.** What is `fallbackToDestructiveMigration()` and why is it a footgun in production?

- A) A faster migration path; always use it.
- B) It "handles" a missing migration by **deleting the database and rebuilding it empty** — i.e. by losing all user data. Fine in early dev, a data-loss bug in a release build.
- C) It encrypts the database.
- D) It rolls back to the previous version safely.

---

## Answer key

**Q1 — B.** Room is a compile-time-verified front end over SQLite. The `.db` is a portable SQLite file (`notes` table, `room_master_table`, `android_metadata`); the query plan, WAL journal, and indices are all SQLite's. Knowing this doubles the help available when something leaks. (Lecture 1, §1.)

**Q2 — B.** Room verifies your `@Query` SQL against the schema at compile time. A misspelled column is a build error with the exact name — eliminating the "stringly-typed query crashed in production" bug class that `rawQuery` allowed. (Lecture 1, §2, §4.)

**Q3 — B.** A `Flow`-returning DAO method is reactive: Room's `InvalidationTracker` watches the queried tables and re-runs the query, re-emitting on every relevant change. `suspend` is a one-shot read; `Flow` is the live subscription. (Lecture 1, §4, §6.)

**Q4 — B.** A `@TypeConverter` converts a non-primitive (`Instant`, enum, `List<String>`) to/from one of SQLite's storage classes. Register it with `@TypeConverters` on the database. Note a converted blob isn't queryable with SQL. (Lecture 1, §4.)

**Q5 — B.** Room satisfies a `@Relation` with multiple queries (parent, then a batched child query); `@Transaction` makes them atomic so you don't read a torn state mid-change. Room warns if you omit it. (Lecture 1, §5.)

**Q6 — B.** The Kotlin filter runs `SELECT *` (a full scan), materialises all 50,000 rows across the Cursor, and keeps a few; the `WHERE` clause filters in SQLite (using an index if present) so only matches materialise. Same answer, vastly different cost — the week's central footgun. (Lecture 1, §4; lecture 2, §4.)

**Q7 — C.** A structured, typed settings object with defaults is the Proto DataStore case — a `.proto`-defined type, read/written as a `Flow`, type-checked. Preferences DataStore is for loose flags; `SharedPreferences` is deprecated. (Lecture 2, §1.)

**Q8 — B.** Store the file in private storage and keep its path in the row. An inline `BLOB` bloats every Cursor read of that row. You don't need `WRITE_EXTERNAL_STORAGE` for private storage. (Lecture 1, §8; lecture 2, §2.)

**Q9 — B.** Adding a column with a default is additive — an `AutoMigration(from, to)` generates the `ALTER TABLE ADD COLUMN`. Room diffs the exported schema JSONs to write it. (Lecture 2, §3.)

**Q10 — B.** Backfilling computes the new value from existing data — a transformation Room can't infer. You write a manual `Migration` with `ALTER TABLE` plus an `UPDATE ... SET` that computes the value. (Lecture 2, §3.)

**Q11 — B.** A fresh install creates the database at the latest version, so no migration runs and a broken upgrade stays green. `MigrationTestHelper.createDatabase(db, oldVersion)` then `runMigrationsAndValidate(db, newVersion, ...)` seeds an old store and exercises the real upgrade. (Lecture 2, §3; exercise 03.)

**Q12 — B.** Room's invalidation is table-granular: any write to the queried table re-runs the `Flow`, even a non-matching one, so you can get an equal-list re-emission. `.distinctUntilChanged()` (or `StateFlow`'s equality) suppresses it. (Lecture 1, §6.)

**Q13 — B.** `fallbackToDestructiveMigration()` resolves a missing migration by dropping and recreating the database empty — losing all user data. It's a dev convenience and a production data-loss bug; a review blocker in a release build. (Lecture 2, §3.)

---

*Score 11+? On to Week 15. Below 9? Re-read both lecture notes and re-run exercises 2 and 3 — the "filter in SQLite, not in Kotlin" footgun and the migration upgrade path are the two ideas this week is graded on.*
