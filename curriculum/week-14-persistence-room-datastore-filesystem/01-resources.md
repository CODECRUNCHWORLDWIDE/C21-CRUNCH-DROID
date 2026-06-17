# Week 14 — Resources

Every primary resource on this page is **free**. The Android developer documentation is free. The SQLite documentation is public. The Now-In-Android sample is open source on GitHub under Apache-2.0. A handful of paid books are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"Save data in a local database using Room"** — Android's canonical Room guide. Read this before you write an `@Entity`:
  <https://developer.android.com/training/data-storage/room>
- **"Define relationships between objects"** — the `@Relation` / `@Junction` guide for one-to-many and many-to-many; central to lecture 1, §5:
  <https://developer.android.com/training/data-storage/room/relationships>
- **"Migrate your Room database"** — auto-migrations, manual migrations, the schema export, and `MigrationTestHelper`; central to lecture 2, §3:
  <https://developer.android.com/training/data-storage/room/migrating-db-versions>
- **"DataStore"** — Preferences and Proto DataStore, the `Flow` model, and migrating off `SharedPreferences`:
  <https://developer.android.com/topic/libraries/architecture/datastore>
- **"Data and file storage overview"** + **"Scoped storage"** — internal vs. external, and the post-Android-11 storage model:
  <https://developer.android.com/training/data-storage> and <https://developer.android.com/training/data-storage/use-cases>

## The SQLite lineage (why this matters)

Room is a front end over SQLite. When Room behaves surprisingly, the explanation is almost always one layer down. You will not write raw SQLite C this week, but you should be able to read the SQL and the schema.

- **SQLite documentation home:** <https://www.sqlite.org/docs.html>
- **The SQLite query planner** (why a query is slow without an index): <https://www.sqlite.org/queryplanner.html>
- **`EXPLAIN QUERY PLAN`** — see exactly how SQLite will run your query: <https://www.sqlite.org/eqp.html>
- **Write-Ahead Logging (WAL)** — Room's default journal mode and why it matters for concurrent reads/writes: <https://www.sqlite.org/wal.html>
- **Indexes** — what `@Index` actually builds underneath: <https://www.sqlite.org/lang_createindex.html>

## The annotations and APIs (reference, skim don't memorize)

- **`@Entity`, `@PrimaryKey`, `@ColumnInfo`, `@Index`:** <https://developer.android.com/training/data-storage/room/defining-data>
- **`@Dao`, `@Query`, `@Insert`, `@Update`, `@Delete`, `@Upsert`:** <https://developer.android.com/training/data-storage/room/accessing-data>
- **`@TypeConverter`:** <https://developer.android.com/training/data-storage/room/referencing-data>
- **`@Database`, `RoomDatabase`, `Room.databaseBuilder`:** <https://developer.android.com/reference/androidx/room/RoomDatabase>
- **Paging 3 with Room:** <https://developer.android.com/topic/libraries/architecture/paging/v3-paged-data>
- **`MigrationTestHelper`:** <https://developer.android.com/reference/kotlin/androidx/room/testing/MigrationTestHelper>
- **Proto DataStore + protobuf serializer:** <https://developer.android.com/topic/libraries/architecture/datastore#proto-datastore>

## Build setup

- **Room KSP setup and the `androidx.room` Gradle plugin (schema export):** <https://developer.android.com/training/data-storage/room#setup>
- **KSP overview:** <https://kotlinlang.org/docs/ksp-overview.html>
- **Enabling `exportSchema` and committing the JSON:** see the migration guide above — the exported schema is the source-of-truth for auto-migrations and tests.

## Source to read this week (this is the assignment that teaches the most)

You learn more from one hour reading a production Room data layer than from three hours of tutorials. Read **Now-In-Android** — Google's reference app — specifically `core/database/`:

- **`android/nowinandroid`** — read `core/database/.../dao/`, the `@Entity` definitions, the `@TypeConverter`s, and the migration tests:
  <https://github.com/android/nowinandroid>
- **Now-In-Android architecture guide** — the data layer the Room code lives in:
  <https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md>
- **`android/architecture-samples`** — smaller Room + DataStore examples if Now-In-Android is too much at once:
  <https://github.com/android/architecture-samples>

## Inspecting the database

- **Android Studio's "App Inspection ▸ Database Inspector"** — live-browse the running app's Room database, run ad-hoc SQL, watch tables update. The single most useful tool this week.
- **`adb shell run-as <pkg> sqlite3 databases/<name>.db ".tables"`** — open the actual SQLite file on a debuggable build and confirm the schema Room generated.
- **`PRAGMA table_info(notes)`** and **`.schema notes`** — read the columns Room created from your `@Entity`.

## Community writing (current, opinionated, correct)

- **Florina Muntenescu (Android team) — Room and Paging articles** on the Android Developers Medium publication:
  <https://medium.com/androiddevelopers>
- **Pierre-Yves Ricau / the Square engineering blog** — long-form on SQLite and persistence performance:
  <https://developer.squareup.com/blog/>
- **Chris Banes' blog** — practical DataStore and migration notes:
  <https://chrisbanes.me/>

## Tools you'll use this week

- **Android Studio Database Inspector** (above) — the primary teaching tool.
- **`./gradlew :app:connectedDebugAndroidTest`** — run the instrumented `MigrationTestHelper` tests on a device/emulator.
- **`adb shell am force-stop <pkg>`** — kill the process for the relaunch test (the equivalent of swiping the app away).
- **The schema JSON under `schemas/`** — after a build with `exportSchema = true`, read the generated `<version>.json` to see exactly what Room thinks the schema is.

## Free books (chapter-level)

- **Android's "Guide to app architecture"** (the data layer) is effectively a free book and the backbone of where Room/DataStore live in a real app:
  <https://developer.android.com/topic/architecture/data-layer>

## Paid books (optional, clearly marked)

- **"Android Persistence" / "Room in Action"** — various (paid). Useful for a single linear narrative; the docs above cover everything for free.
- **"The Definitive Guide to SQLite"** — Apress (paid). Older, but the clearest deep dive on the engine under Room; the chapters on the query planner and indexing are still excellent.

---

*If a link 404s, please open an issue so we can replace it.*
