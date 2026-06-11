# Week 14 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — An Entity/DAO/Database that survives relaunch](exercise-01-entity-dao-survives-relaunch.md)** — define an `@Entity`, a `@Dao`, and a `@Database`, insert and read with a `Flow`, and *prove* the records survive a force-quit cold launch. The whole point of the week, in one exercise. (~40 min)
2. **[Exercise 2 — A verified `Flow` query vs. naive load-everything](exercise-02-flow-query-vs-naive.kt)** — query with a compile-time-verified `@Query` returning a `Flow`, then measure a naive load-everything-then-filter against a `WHERE`-clause query that runs in SQLite. You produce two numbers and explain the gap. (~50 min)
3. **[Exercise 3 — A Room migration with `MigrationTestHelper`](exercise-03-room-migration.kt)** — seed a v1 database, add a column and a table across v2/v3, register an `AutoMigration` and a manual `Migration`, and prove the old data survives the upgrade with `MigrationTestHelper`. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Run on the **emulator** (or a device — Room needs the Android SQLite). Exercises 2 and 3 are instrumented tests (`androidTest`) because Room's verification and `MigrationTestHelper` need a real device/emulator SQLite; the file headers say so. See the output. Read the error if it crashed.
- The `.kt` exercises are written to drop into an `androidTest` source set using an in-memory or temp-file database; each file's header says which and why.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A Room compile-time SQL error is not a warning you suppress — it's a query bug you fix. A relation query without `@Transaction` is a warning you *do* fix.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-14` to compare.
