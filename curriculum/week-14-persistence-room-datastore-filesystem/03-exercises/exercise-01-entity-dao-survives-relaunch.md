# Exercise 1 — An Entity/DAO/Database that survives relaunch

**Goal.** Stand up the smallest possible real Room app: one `@Entity`, one `@Dao`, one `@Database`, a list driven by a `Flow`, and an "add" button. Then prove the data is durable by force-quitting the app and relaunching it from cold. This is the entire promise of the week distilled to one screen — if you can do this, persistence works; everything else this week is refinement.

**Estimated time.** 40 minutes.

**Prerequisites.** Android Studio (2025.1+), an emulator or device, a project with the Room dependencies and KSP applied. The full app is *not* required — we build a throwaway `Scratch` screen so the focus stays on the database.

---

## Step 1 — Add Room to the module

In your module's `build.gradle.kts`, apply KSP and the Room plugin and add the dependencies (via your version catalog):

```kotlin
plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room { schemaDirectory("$projectDir/schemas") }   // turn on schema export now; you'll need it later

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)        // coroutines + Flow support
    ksp(libs.androidx.room.compiler)
}
```

Sync. Confirm the project builds before you touch anything.

## Step 2 — Define an `@Entity`

Create `Note.kt`:

```kotlin
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

Three things to notice and be able to explain in a code review:

- It's a **data class** with annotations — the class is the table, each property is a column.
- `@PrimaryKey(autoGenerate = true)` gives a synthetic auto-incrementing key. We default `id = 0` so a freshly-constructed `Note` lets Room assign the real id on insert.
- Every property has a default, so adding one later is a *lightweight* (auto-)migration.

## Step 3 — Define a `@Dao`

Create `NotesDao.kt`. Note the reactive read (`Flow`) and the suspend write:

```kotlin
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Delete
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotesDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Note>>      // re-emits whenever the notes table changes

    @Insert
    suspend fun insert(note: Note): Long     // off the main thread; returns the new id

    @Delete
    suspend fun delete(note: Note)
}
```

The `@Query` SQL is verified at compile time against the `notes` table. Misspell `createdAt` and the build fails with the exact column — try it once to see the error, then fix it.

## Step 4 — Define the `@Database`

Create `CrunchDatabase.kt`:

```kotlin
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Note::class], version = 1, exportSchema = true)
abstract class CrunchDatabase : RoomDatabase() {
    abstract fun notesDao(): NotesDao
}
```

After your next build, confirm `schemas/CrunchDatabase/1.json` appears and **commit it** — that exported schema is what powers migrations and the migration test later.

## Step 5 — Provide the database (via Hilt, from Week 13)

Wire it into the Hilt graph you built last week — the database is the canonical `@Singleton`, the DAO is unscoped:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CrunchDatabase =
        Room.databaseBuilder(context, CrunchDatabase::class.java, "crunch.db").build()

    @Provides
    fun provideNotesDao(db: CrunchDatabase): NotesDao = db.notesDao()
}
```

(If you're not using Hilt yet, build the database in your `Application` and hold it there — but the `@Singleton` discipline from Week 13 is the right pattern.)

## Step 6 — Read with the `Flow`, write with the DAO

A minimal ViewModel + screen:

```kotlin
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val dao: NotesDao
) : ViewModel() {
    val notes: StateFlow<List<Note>> =
        dao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addNote() = viewModelScope.launch {
        dao.insert(Note(title = "Note #${notes.value.size + 1}"))
    }
    fun delete(note: Note) = viewModelScope.launch { dao.delete(note) }
}

@Composable
fun NotesScreen(viewModel: NotesViewModel = hiltViewModel()) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Notes (${notes.size})") }) },
        floatingActionButton = { FloatingActionButton(onClick = viewModel::addNote) { Text("+") } }
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(notes, key = { it.id }) { note ->
                ListItem(headlineContent = { Text(note.title) })
            }
        }
    }
}
```

Notice you never call "refresh" — the `Flow` re-emits when the table changes, so adding a note updates the list automatically. That's Room's `InvalidationTracker` doing its job.

## Step 7 — Run, add, and SEE the data

Run on the emulator. Tap **+** three or four times. The list grows, newest first, and the count updates. So far this looks like an in-memory list. The difference is invisible until you kill the process.

## Step 8 — The relaunch test (the whole point)

Backgrounding is *not* the test — the process stays alive. You must **kill the process** and relaunch cold.

**Option A — the UI way:** open the recents/app switcher and swipe the app away, then tap the icon to relaunch.

**Option B — the CLI way (reliable, scriptable):**

```bash
adb shell am force-stop com.yourname.app   # kill the process
adb shell monkey -p com.yourname.app 1     # relaunch cold (or tap the icon)
```

Replace `com.yourname.app` with your applicationId.

**Expected result:** the app relaunches and your notes are **still there**, newest first. If they vanished, you either forgot to build the database (check the Hilt module / Application), or accidentally used an in-memory builder (`Room.inMemoryDatabaseBuilder`) for the real app.

## Step 9 — Look at the actual database (optional, illuminating)

Prove there's a real SQLite file with your table in it:

```bash
adb shell run-as com.yourname.app sqlite3 databases/crunch.db ".tables"
adb shell run-as com.yourname.app sqlite3 databases/crunch.db "SELECT id, title FROM notes;"
```

Seeing `notes` plus Room's `room_master_table` and `android_metadata` makes lecture 1's "Room is SQLite underneath" concrete. You wrote `@Entity`; SQLite wrote the `notes` table.

---

## Acceptance criteria

- [ ] A `@Entity data class Note`, a `@Dao` with a `Flow` read and `suspend` writes, and a `@Database` with `exportSchema = true`.
- [ ] `schemas/CrunchDatabase/1.json` exists and is **committed**.
- [ ] The database is provided as a `@Singleton` and the DAO unscoped (Week 13 pattern), or held in the `Application`.
- [ ] The list is driven by `observeAll(): Flow`, collected with `collectAsStateWithLifecycle`; add/delete go through `suspend` DAO methods.
- [ ] Build with **0 warnings, 0 errors**.
- [ ] You added at least 4 notes, force-quit the process (`adb shell am force-stop` or the app switcher), relaunched cold, and the notes were **still there in the correct order**.
- [ ] (Stretch) You opened `crunch.db` with `sqlite3` and listed the `notes` table.

## What you just proved

You proved the three runtime objects from lecture 1 actually work together: the **database** owns the SQLite file on disk, the **DAO** ran your verified queries, and the **`Flow`** read them back reactively and re-rendered the list with no manual refresh. And you proved the week's promise — *state the user created survived the process dying* — with a kill-and-relaunch, not a vibe. Every other exercise this week builds on this skeleton.

---

## Hints (read only if stuck > 10 min)

- **Notes vanish on relaunch but the app didn't crash.** Almost always: the real database is built with `Room.inMemoryDatabaseBuilder` (which is RAM-only — fine for tests, fatal for the app). Use `Room.databaseBuilder` with a file name for the real database; reserve in-memory for tests.
- **Build fails "Cannot verify the query".** Room verified your `@Query` SQL against the schema and it didn't match — read the error, it names the column/table. A typo in `createdAt` or `notes` is the usual cause.
- **The list doesn't update after adding.** You're collecting a one-shot `suspend` read instead of the `Flow`. The list must come from `observeAll(): Flow`, not a `suspend fun all(): List<Note>`.
- **`@Singleton` complains it can't find `@ApplicationContext`.** Confirm the Hilt setup from Week 13 — `@ApplicationContext context: Context` needs the Hilt Android dependency and an `@HiltAndroidApp` Application.
- **No `schemas/` JSON appears.** Add the `room { schemaDirectory(...) }` block and the `androidx.room` Gradle plugin, then rebuild. The export is what later exercises depend on.
