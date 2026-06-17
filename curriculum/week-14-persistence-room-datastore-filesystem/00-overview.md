# Week 14 — Persistence: Room, DataStore, the file system

Welcome to Week 14 of **C21 · Crunch Droid**. Last week you built the Hilt graph and stubbed out a `:core-database` module that `@Provides`-d a `RoomDatabase` you never actually implemented. This week you implement it for real. Your app's data stops living in RAM and starts living on disk — in a SQLite database Room manages, in a Proto DataStore for preferences, and in scoped-storage files for attachments — so that when the process dies and relaunches, the data is still there.

Room is Google's persistence library, a thin, annotation-driven, compile-time-checked layer **over SQLite** — the same SQLite that ships in every Android device, the same C library that has been the on-device database since 2009. That lineage is the most important fact about Room and the thing this week hammers on. Room is not a new database engine. It is a typed, verified *front end* on SQLite, and almost everything that confuses people about Room — why a query is slow, why a migration fails, why a `Flow` query re-emits, why a `@Relation` does a second query — is explained by what SQLite is doing one layer down. Room verifies your SQL **at compile time** (a typo in a `@Query` is a build error, not a runtime crash), generates the boilerplate that binds rows to objects, and integrates with coroutines and Flow so a query becomes a reactive stream. We teach Room as the thing you write and SQLite as the thing you debug.

The mental shift this week is from "I hold an in-memory list" to "I declare *entities* (the table shape), *DAOs* (the typed query interface), and a *database* (the SQLite file), and Room generates the code that moves rows between them." An `@Entity` is a table. A `@Dao` is an interface of `@Query`/`@Insert`/`@Update`/`@Delete` methods Room implements for you, with the SQL verified against the schema. A `@Database` ties entities and DAOs to an on-disk file and a version number. Return a `Flow<List<T>>` from a DAO method and Room re-runs the query and re-emits whenever the underlying table changes — the reactive read path that drives a Compose UI without manual refresh. And **migrations** are where every Android app eventually breaks: ship v2 with a new column and no migration, and the app crashes on launch for every existing user. Avoiding that is the actual skill this week earns.

Alongside Room you learn the two other on-device persistence tools every app needs. **DataStore** replaces the deprecated `SharedPreferences` for small key-value and typed settings — `Preferences DataStore` for loose key-value, `Proto DataStore` for a typed, schema-defined settings object — both `Flow`-based and coroutine-safe (no more `apply()` on the main thread). And the **file system**: internal versus external storage, and the **scoped storage** model that post-Android-11 enforces, so your attachment backup doesn't crash on a modern device for touching files it isn't allowed to.

We close the week by building a **local-first notes app**: a Room database with a `Note` entity, a `Tag` entity and a many-to-many relation, Proto DataStore for user preferences, scoped-storage backup of an attachment, and a tested migration from v1 to v3 with a schema export checked into source control. By the end, the data survives a cold launch, the preferences survive a reinstall-shaped migration, and you have proven the migration works by seeding an old database and opening it with the new schema — the test most people skip and then ship a data-loss bug.

## Learning objectives

By the end of this week, you will be able to:

- **Explain** Room's relationship to SQLite — that it is a compile-time-verified, annotation-driven front end over the same SQLite engine — and predict which Room behaviours (query cost, `Flow` invalidation, relation N+1) come from the SQLite layer.
- **Model** a schema with `@Entity` (primary keys, indices, embedded types), wire object graphs with `@Relation` and a `@Junction` for many-to-many, and convert non-primitive columns with a `@TypeConverter`.
- **Query** with a `@Dao`: `@Query` with compile-time-verified SQL, `@Insert`/`@Update`/`@Delete`/`@Upsert`, suspend functions for one-shot reads, and `Flow<T>` for reactive reads that re-emit on change.
- **Integrate** Room with Paging 3 (`PagingSource` from a DAO) for large lists, and reason about when paging earns its keep versus a bounded query.
- **Choose** between Preferences DataStore and Proto DataStore on the right criteria, read and write both as `Flow`s, and migrate off `SharedPreferences`.
- **Use** the Android file system correctly: internal vs. external storage, the cache directory, and scoped-storage-compliant file access post-Android-11 (MediaStore / SAF where required).
- **Write** Room migrations end-to-end: an `AutoMigration` for additive changes, a manual `Migration` with SQL for transformations, a schema export checked into Git, and a `MigrationTestHelper` test that proves the upgrade path preserves data.
- **Recognise** the persistence footguns — main-thread queries, the `@Relation` N+1, an unbounded reactive query, a destructive migration — and the fallbacks (raw SQL, `RoomDatabase.Callback`, `supportSQLiteDatabase`) when Room hides too much.

## Prerequisites

This week assumes you have completed **C21 weeks 1–13**, or have equivalent fluency. Specifically:

- You can read and write idiomatic Kotlin — data classes, interfaces, suspend functions, generics, annotations — Weeks 1–3. An `@Entity` is a data class with annotations; a `@Dao` is an interface; both need to read naturally.
- You understand coroutines and Flow — Weeks 4–5. Room's reactive reads return `Flow<T>`; the cold-flow, operator, and `collect` machinery from Week 5 is exactly how you consume a Room query.
- You can wire a Hilt graph and reason about scopes — Week 13. The `RoomDatabase` is the canonical `@Singleton`; you will `@Provides` it into the same graph you built last week. The DAO is unscoped and derived. This is *why* Week 13 came first.
- You can read `build.gradle.kts` and a version catalog — Week 6. Room is a Gradle dependency plus an annotation processor (KSP), and the schema export needs a small `room.schemaLocation` argument.

**Toolchain.** Android Studio (2025.1 / Narwhal+), AGP 8.7+, Kotlin 2.1+, Room 2.7+, DataStore 1.1+, KSP, JDK 17. Room's KSP processor and the `androidx.room` Gradle plugin (which manages the schema export location) are the 2026 setup; we use KSP throughout and flag the older kapt assumptions. Most of this week runs on the emulator; the migration tests run as instrumented tests with `MigrationTestHelper`.

## Topics covered

- **The SQLite lineage.** What Room is (a compile-time-verified front end), what it is over (SQLite, the same C engine), and which behaviours are inherited: query planning, the WAL journal, `Flow` invalidation via the `InvalidationTracker`, and the fact that a `.db` file is portable SQLite you can open with `sqlite3`.
- **`@Entity`.** Tables, `@PrimaryKey` (auto-generate vs. natural keys), `@ColumnInfo`, `@Index` (and composite/unique indices), `@Embedded` for flattening a value type into columns, and `@Ignore` for non-persisted fields.
- **`@Dao`.** `@Query` with compile-time-verified SQL and named parameters, `@Insert`/`@Update`/`@Delete` with conflict strategies, `@Upsert`, suspend vs. `Flow` return types, and returning a `@Transaction`-wrapped multi-step read.
- **`@TypeConverter`.** Storing a type SQLite doesn't know (an `Instant`, an enum, a `List<String>`) by converting to and from a primitive, and registering converters on the database.
- **`@Relation` and `@Junction`.** One-to-many and many-to-many object graphs, the `@Transaction` requirement for relation queries, and the N+1 cost of a relation query (Room runs a second query to fill the relation).
- **Paging 3.** A `PagingSource` from a DAO, `Pager`/`PagingData`, `collectAsLazyPagingItems` in Compose, and `RemoteMediator` as a forward reference for the offline-sync week.
- **DataStore.** Preferences DataStore (loose key-value, `Flow<Preferences>`), Proto DataStore (a typed `.proto`-defined settings object), the coroutine-safe write model, and migrating off `SharedPreferences` with `SharedPreferencesMigration`.
- **The file system.** `filesDir` / `cacheDir` (internal), external storage and its volatility, scoped storage post-Android-11, `MediaStore` for shared media, the Storage Access Framework for user-chosen files, and why you almost never need `WRITE_EXTERNAL_STORAGE` anymore.
- **Migrations.** `AutoMigration` (additive, declarative), manual `Migration(from, to)` with SQL for transformations, `@DeleteColumn`/`@RenameColumn` auto-migration specs, the schema export (`exportSchema = true`) checked into Git, `fallbackToDestructiveMigration` and why it's a footgun in production, and the `MigrationTestHelper` upgrade-path test.
- **Performance footguns.** Main-thread queries (Room throws by default — and why), the relation N+1, an unbounded `Flow` query backing a list, redundant `@Transaction`s, and the write that janks a scroll.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                                  | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|------------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | The SQLite lineage; `@Entity`, `@Dao`, `@Database`, type converters     |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | `@Relation`/`@Junction`; `Flow` queries; Paging 3 intro                |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | DataStore (Preferences + Proto); the file system; footguns             |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | Migrations + schema export; `MigrationTestHelper`; challenge           |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — local-first notes app; Room + Proto DataStore           |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work; scoped-storage backup; v1→v3 migration test    |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                            |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                        | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./00-overview.md) | This overview (you are here) |
| [resources.md](./01-resources.md) | The Room, DataStore, and storage docs, the SQLite reference, the Now-In-Android data layer, and the canonical writing on migrations |
| [lecture-notes/01-room-and-the-sqlite-lineage.md](./02-lecture-notes/01-room-and-the-sqlite-lineage.md) | Room end to end: what SQLite is, what Room adds, `@Entity`/`@Dao`/`@Database`, type converters, relations, `Flow` queries, and where it leaks SQLite |
| [lecture-notes/02-datastore-filesystem-migrations-footguns.md](./02-lecture-notes/02-datastore-filesystem-migrations-footguns.md) | Preferences vs. Proto DataStore, the scoped-storage file system, migrations (auto and manual) with the schema export and the upgrade-path test, and the performance footguns measured |
| [exercises/README.md](./03-exercises/00-overview.md) | Index of the three exercises |
| [exercises/exercise-01-entity-dao-survives-relaunch.md](./03-exercises/exercise-01-entity-dao-survives-relaunch.md) | Define an `@Entity`/`@Dao`/`@Database`, insert and read with a `Flow`, and prove records survive a force-quit relaunch |
| [exercises/exercise-02-flow-query-vs-naive.kt](./03-exercises/exercise-02-flow-query-vs-naive.kt) | Query with a verified `@Query` + a `Flow`, then measure a naive load-everything-then-filter against a WHERE-clause query that runs in SQLite |
| [exercises/exercise-03-room-migration.kt](./03-exercises/exercise-03-room-migration.kt) | Seed a v1 database, add a column and a table across v2/v3, register migrations, and prove the old data survives with `MigrationTestHelper` |
| [challenges/README.md](./04-challenges/00-overview.md) | Index of the challenge |
| [challenges/challenge-01-footgun-then-refactor.md](./04-challenges/challenge-01-footgun-then-refactor.md) | Plant a load-everything-then-filter footgun (and a relation N+1), measure them, refactor into verified WHERE-clause queries with a `@Transaction`, and document the before/after |
| [quiz.md](./05-quiz.md) | 13 questions on the lineage, entities/DAOs, type converters, relations, `Flow`, DataStore, the file system, and migrations |
| [homework.md](./06-homework.md) | Six practice problems for the week |
| [mini-project/README.md](./07-mini-project/00-overview.md) | Full spec for the local-first notes app: Room + many-to-many + Proto DataStore + scoped-storage backup + a tested v1→v3 migration |

## The "survives a cold launch" promise

Week 13 gave you "the graph builds, or it doesn't." Week 14 adds the persistence contract a senior reviewer actually checks:

> **State the user created must survive the process dying.** Create a note, force-quit the app from the app switcher (kill it, don't just background it), relaunch from the home screen, and the note is still there, with its tags, in the right sort order — *and* a schema-versioned migration carried it forward when you bumped the version. If a relaunch loses data, or a version bump drops a column, the persistence layer is broken, no matter how clean the code looks.

You will *prove* the relaunch by force-quitting (`adb shell am force-stop` or the app switcher) and relaunching cold, and you will *prove* the migration by seeding an old database and opening it with the new schema — "it worked on a fresh install" is not the migration test.

## A note on what's not here

Week 14 is the *on-device persistence* week. It deliberately does **not** cover:

- **Server sync and conflict resolution.** Room is the local store; wiring it to a backend through an outbox/sync queue with exponential backoff and conflict resolution is Week 16 (WorkManager) and the capstone. We use `RemoteMediator` only as a forward reference. The notes app is offline-only this week.
- **Networking.** Where the data *comes from* over the wire — Retrofit, OkHttp, Ktor, gRPC — is Week 15. This week the data is local; next week it has a source.
- **Encryption at rest.** `SQLCipher`, `EncryptedFile`, and Keystore-backed encryption of the Room database are Week 22 (security). This week the store is plaintext SQLite; we flag where encryption would slot in and move on.

The point of Week 14 is narrow and deep: one schema, the entities and DAOs that declare it, the database that persists it, the `Flow` that reads it reactively, the DataStore that holds the settings beside it, and the migration that keeps it all alive across a version bump.

## Up next

Continue to **Week 15 — Networking: Retrofit, OkHttp, Ktor Client, gRPC** once you have shipped this week's mini-project and proven a cold-launch survival and a v1→v3 migration. Week 15 gives the local store a *source*: you'll fetch data over the wire and persist it into the exact Room database you built this week, through the exact Hilt graph you built the week before. Week 16 then makes that sync offline-first with WorkManager. Every week in Phase 3 assumes you can model a schema and migrate it safely. Earn that this week — the rest of production engineering reads and writes the store you build now.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
