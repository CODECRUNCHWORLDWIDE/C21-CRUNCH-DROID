# Mini-Project — Local-first notes app: Room, Proto DataStore, scoped storage

This week the notes app gets a real local store. You will build a **local-first notes app** backed by a Room database with a `Note` entity and a `Tag` entity in a **many-to-many** relation, Proto DataStore for user preferences, a scoped-storage backup of an attachment, and a tested **v1→v3 migration** with the schema exported to source control. By the end the data survives a cold launch, the preferences survive beside it, and you have *proven* the migration works by seeding an old database and opening it with the new schema.

This is a *compounding* project. The Room database you build here is `@Provides`-d into the **exact Hilt graph you built in Week 13** — fill in the `:core-database` module you stubbed last week. Next week (Week 15) gives this store a network *source*; the week after (Week 16) makes the sync offline-first. This week you build the local source of truth they all read and write. Get the schema and the migration right now and the rest of Phase 3 reads and writes it safely.

---

## Where you're starting from

Your Week 13 app has a Hilt graph with a stubbed `:core-database` module. You also have the exercise-1 skeleton (a `Note` entity, a DAO, a database). You need:

- The Room dependencies and KSP applied, and the `androidx.room` Gradle plugin with `room { schemaDirectory(...) }` so the schema exports.
- The DataStore dependencies (`datastore` + `datastore-preferences`, and protobuf for Proto DataStore).
- The Hilt graph from Week 13 to plug the database into.

If you don't have a clean Week 13 checkpoint, build the minimal Hilt graph first; the persistence work is the same either way.

## What you're building toward

By the end you have:

- A `@Entity Note` and `@Entity Tag` with a many-to-many relation via a `NoteTagCrossRef` join table and a `@Transaction` relation query.
- A `@Dao` with verified `@Query` reads (one `Flow` reactive read, one filtered query), `@Upsert` writes, and a bulk `@Query("DELETE ...")`.
- A `RoomDatabase` provided as `@Singleton` into the Hilt graph, with `exportSchema = true` and the JSON committed.
- **Proto DataStore** holding a typed `UserPreferences` (theme, sort order), read and written as a `Flow`.
- A **scoped-storage backup**: export the notes to a JSON file in the app's private storage (and, stretch, to a user-chosen location via SAF), with the file path referenced — never the blob inline in SQLite.
- A passing **relaunch test** and a passing **v1→v3 migration test** with `MigrationTestHelper`.

---

## Milestone 1 — Model the schema (≈ 1.5 h)

Define `Note`, `Tag`, the join table, and the relation. Index the columns you filter and sort on.

```kotlin
@Entity(
    tableName = "notes",
    indices = [Index(value = ["title"]), Index(value = ["updatedAt"])]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val attachmentPath: String? = null         // PATH to a file, not the blob itself
)

@Entity(tableName = "tags")
data class Tag(@PrimaryKey val name: String)   // natural key: one row per tag name

@Entity(primaryKeys = ["noteId", "tagName"])
data class NoteTagCrossRef(val noteId: Long, val tagName: String)

data class NoteWithTags(
    @Embedded val note: Note,
    @Relation(
        parentColumn = "id", entityColumn = "name",
        associateBy = Junction(NoteTagCrossRef::class, "noteId", "tagName")
    )
    val tags: List<Tag>
)
```

Decisions you must be able to defend in review:

- **Why a join table for many-to-many?** A note has many tags; a tag belongs to many notes. SQLite models that with a third table of (noteId, tagName) pairs. Room's `@Junction` reads it for you. There is no "list column" — a `List<Tag>` stored inline would be an unqueryable blob.
- **Why a natural key (`name`) on `Tag`, not an auto-generated id?** The tag's identity *is* its name — you want exactly one "kotlin" tag. A natural primary key on `name` enforces that at the database level (insert a duplicate name and the conflict strategy decides).
- **Why store `attachmentPath`, not the attachment bytes?** A blob inline in SQLite bloats every query that reads the row (Cursor reads the whole row). Store the file in private storage, keep the *path* in the row. (Lecture 2, §2.)

## Milestone 2 — The DAO and the reactive read (≈ 1.5 h)

```kotlin
@Dao
interface NotesDao {
    @Transaction                                          // required for relation queries
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeNotesWithTags(): Flow<List<NoteWithTags>>  // reactive read for the list

    @Transaction
    @Query("""
        SELECT * FROM notes
        WHERE id IN (SELECT noteId FROM NoteTagCrossRef WHERE tagName = :tag)
        ORDER BY updatedAt DESC
    """)
    fun observeNotesForTag(tag: String): Flow<List<NoteWithTags>>   // filter in SQLite

    @Upsert suspend fun upsert(note: Note): Long
    @Upsert suspend fun upsertTag(tag: Tag)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(crossRef: NoteTagCrossRef)
    @Query("DELETE FROM NoteTagCrossRef WHERE noteId = :noteId AND tagName = :tag")
    suspend fun unlink(noteId: Long, tag: String)
    @Delete suspend fun delete(note: Note)
    @Query("SELECT COUNT(*) FROM notes") suspend fun count(): Int
}
```

The tag-filter query does the membership test **in SQLite** with a subquery over the join table — it does *not* load every note and filter tags in Kotlin. That's the efficient query the week's "skill earned" line demands. The list read returns a `Flow`, so the Compose UI re-renders automatically when any note or tag changes.

## Milestone 3 — Provide the database into the Hilt graph (≈ 0.5 h)

Fill in the `:core-database` module you stubbed in Week 13:

```kotlin
@Database(
    entities = [Note::class, Tag::class, NoteTagCrossRef::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CrunchDatabase : RoomDatabase() {
    abstract fun notesDao(): NotesDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CrunchDatabase =
        Room.databaseBuilder(context, CrunchDatabase::class.java, "crunch.db")
            .build()

    @Provides   // unscoped: cheap accessor; the DATABASE is the @Singleton
    fun provideNotesDao(db: CrunchDatabase): NotesDao = db.notesDao()
}
```

Confirm `schemas/CrunchDatabase/1.json` is generated and **commit it** — the migration in Milestone 5 depends on it.

## Milestone 4 — Proto DataStore for preferences (≈ 1.5 h)

User preferences are a structured, typed object, so use **Proto DataStore**, not loose Preferences keys.

```proto
// user_prefs.proto
syntax = "proto3";
option java_package = "com.crunch.notes.prefs";
option java_multiple_files = true;
message UserPreferences {
  bool dark_theme = 1;
  SortOrder sort_order = 2;
  enum SortOrder { UPDATED = 0; TITLE = 1; }
}
```

```kotlin
object UserPreferencesSerializer : Serializer<UserPreferences> {
    override val defaultValue: UserPreferences = UserPreferences.getDefaultInstance()
    override suspend fun readFrom(input: InputStream): UserPreferences = UserPreferences.parseFrom(input)
    override suspend fun writeTo(t: UserPreferences, output: OutputStream) = t.writeTo(output)
}

class PreferencesRepository @Inject constructor(
    private val dataStore: DataStore<UserPreferences>
) {
    val preferences: Flow<UserPreferences> = dataStore.data
    suspend fun setDarkTheme(enabled: Boolean) =
        dataStore.updateData { it.toBuilder().setDarkTheme(enabled).build() }
    suspend fun setSortOrder(order: UserPreferences.SortOrder) =
        dataStore.updateData { it.toBuilder().setSortOrder(order).build() }
}
```

Provide the `DataStore<UserPreferences>` as a `@Singleton` in a Hilt module. Wire the theme toggle and sort-order picker in the UI to `setDarkTheme` / `setSortOrder`, and drive the list's sort from the `preferences` `Flow`. Confirm a toggle survives a relaunch — that's DataStore persisting beside Room.

## Milestone 5 — The migration to v3 (≈ 1 h)

Evolve the schema across two versions and prove the upgrade preserves data.

- **v2 — additive (`AutoMigration`):** add `val isPinned: Boolean = false` to `Note`. Declare `autoMigrations = [AutoMigration(from = 1, to = 2)]`, bump `version = 2`, rebuild so `2.json` generates, and commit it.
- **v3 — transformation (manual `Migration`):** add `val wordCount: Int = 0` to `Note`, write a `Migration(2, 3)` that `ALTER TABLE ... ADD COLUMN` and backfills `wordCount` from `body`, register it on the builder, bump `version = 3`, and commit `3.json`.

Then write the `MigrationTestHelper` test (exercise 03's shape, adapted to your `Note`): seed a v1 database, run migrations to v3, assert the v1 notes survived, `isPinned` defaulted, and `wordCount` computed correctly. **Test the upgrade path, not a fresh install** — a fresh install never runs the migration.

## Milestone 6 — Scoped-storage backup (≈ 1 h)

Export all notes to a JSON file in the app's **private** storage (no permission needed), and reference the path:

```kotlin
suspend fun backupTo(context: Context, notes: List<Note>): File =
    withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "notes-backup.json")
        file.writeText(Json.encodeToString(notes))
        file   // path lives in private storage; no WRITE_EXTERNAL_STORAGE
    }
```

(Stretch: add a "Export…" button that uses the Storage Access Framework `CreateDocument` contract so the user picks where the backup goes — still no storage permission.)

## Milestone 7 — The relaunch test (≈ 0.5 h)

The acceptance bar for the week.

1. Launch. Create two notes. Tag one with `#kotlin` and `#ideas`, the other with `#kotlin`. Toggle dark theme on.
2. Open the `#kotlin` filter — both notes appear. The `#ideas` filter — one appears.
3. **Force-quit:** `adb shell am force-stop <applicationId>` (or swipe the app away).
4. Relaunch cold.
5. Both notes, both tags, and the dark-theme setting are still there. The `#kotlin` filter still returns both. Nothing was lost.

Record this as a short clip or screenshots in your README.

---

## Acceptance criteria

- [ ] `Note`, `Tag`, and a `NoteTagCrossRef` join table model a **many-to-many**, with a `@Transaction` relation query and indexed filtered/sorted columns.
- [ ] The DAO does its tag filtering **in SQLite** (a subquery over the join table), returns a `Flow` for the list, uses `@Upsert`, and counts with `COUNT(*)`.
- [ ] The `RoomDatabase` is provided as `@Singleton` into the Week-13 Hilt graph, with `exportSchema = true` and `1/2/3.json` **committed**.
- [ ] **Proto DataStore** holds a typed `UserPreferences`, read/written as a `Flow`, and a setting survives a relaunch.
- [ ] A scoped-storage backup writes to **private storage** (no `WRITE_EXTERNAL_STORAGE`), with the attachment referenced by **path** in the row, never inline.
- [ ] A `MigrationTestHelper` test proves the **v1→v3 upgrade path** preserves data (v1 rows survive, `isPinned` defaults, `wordCount` is computed).
- [ ] **The relaunch test passes:** create + tag + set a pref, force-quit, relaunch cold, data intact.
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **Paging 3.** Convert the list to a `PagingSource` from the DAO and `collectAsLazyPagingItems` so a 50k-note store scrolls without loading everything (lecture 1, §5; the challenge's footgun fixed structurally).
- **Prefetch the relation.** Confirm the relation query batches (one parent + one child query) in the Database Inspector, not an N+1.
- **SAF export.** Add a user-chosen-location backup via the `CreateDocument` SAF contract.
- **SharedPreferences migration.** If your app has any legacy `SharedPreferences`, add a `SharedPreferencesMigration` into the Proto DataStore and delete the old prefs.
- **Encryption preview (Week 22).** Note in the README where SQLCipher / `EncryptedFile` would slot in to encrypt the database and the backup at rest — don't implement it yet.

## What this milestone earns you

You can now design a Room schema with relations and migrate it safely — the literal "skill earned" line for the week. More than that: you filled in the `:core-database` module the capstone needs, with a tested migration, exported schemas in source control, the right DataStore for settings, and scoped-storage-compliant file handling. The local store you built this week is the source of truth; Week 15 gives it a network source, Week 16 makes the sync offline-first. You'll be glad the schema and the migration are solid before you start filling the store from the wire.
