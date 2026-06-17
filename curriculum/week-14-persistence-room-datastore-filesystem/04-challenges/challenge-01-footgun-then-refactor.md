# Challenge 1 — Plant a Room footgun, then refactor it (with numbers)

**Time.** 60–120 minutes.
**Deliverable.** A short report (`PERF.md`) with two timings, two `EXPLAIN QUERY PLAN` outputs, and one Database Inspector screenshot, plus the refactored code, committed to your Week 14 repo.

## The premise

Every senior engineer has, at least once, shipped the "load everything, then filter in Kotlin" footgun. It works perfectly in the demo. Then the user accumulates real data and the search field drops frames or the app gets a memory warning. The skill this challenge builds is not "know the footgun exists" — it's **plant it, feel it, measure it, fix it, and prove the fix with a number and a query plan.** A fix you can't quantify is a guess.

You will build a search over a large Room store the *wrong* way, measure it, then rewrite it the right way and measure again. The grading is the gap between the two numbers and your explanation of it.

## What to build

Start from your exercise 1 app (or the mini-project). The entity needs enough text to make a search meaningful and a relation to demonstrate the N+1:

```kotlin
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val topic: String,
    val createdAt: Long
)

@Entity(tableName = "tags") data class Tag(@PrimaryKey val name: String)
@Entity(primaryKeys = ["noteId", "tagName"])
data class NoteTagCrossRef(val noteId: Long, val tagName: String)
```

### Step 1 — Seed a large store

Write a one-shot seeder (a debug button) that inserts **50,000+** notes if the store is empty, with realistic text, plus a few tags each. Insert in one transaction and save once — don't insert per row outside a transaction, that's a different footgun.

```kotlin
suspend fun seedIfEmpty(dao: NotesDao) {
    if (dao.count() > 0) return
    val topics = listOf("kotlin", "compose", "coroutines", "room", "hilt", "ktor")
    val notes = (0 until 50_000).map { i ->
        Note(
            title = "Note number $i",
            body = "This note is about ${topics[i % topics.size]} and item $i. " +
                "Lorem ipsum dolor sit amet, persistence edition.",
            topic = topics[i % topics.size],
            createdAt = i.toLong()
        )
    }
    dao.insertAll(notes)   // one @Insert call -> Room wraps it in a transaction
}
```

### Step 2 — Plant the footgun (the WRONG search)

Implement a search that loads **all** notes and filters the list in Kotlin. Wrap it in a timing harness so every search produces a number in logcat.

```kotlin
@Query("SELECT * FROM notes")
suspend fun allNotes(): List<Note>          // <- the footgun: load everything

suspend fun searchNaive(query: String, dao: NotesDao): List<Note> {
    val nanos: Long
    val result: List<Note>
    nanos = measureNanoTime {
        result = dao.allNotes().filter {
            it.title.contains(query, ignoreCase = true) ||
                it.body.contains(query, ignoreCase = true)
        }
    }
    Log.d("perf", "searchNaive('$query') took ${nanos / 1_000_000.0} ms, ${result.size} rows")
    return result
}
```

Run it. Search a query. Read logcat. On 50k rows this is typically tens to hundreds of milliseconds and allocates the entire table. Record the number.

### Step 3 — Read the query plan (the "before" evidence)

Open **Android Studio ▸ App Inspection ▸ Database Inspector**, connect to the running app, and run:

```sql
EXPLAIN QUERY PLAN SELECT * FROM notes;
```

You should see `SCAN notes` — a full table scan, every row read. Screenshot it. This is your "before" evidence: the naive path reads the whole table.

### Step 4 — Refactor to a `WHERE`-clause query (the RIGHT search)

Push the filtering into SQLite, and add an index so it doesn't scan:

```kotlin
@Entity(
    tableName = "notes",
    indices = [Index(value = ["title"]), Index(value = ["topic"])]   // index the filtered columns
)
data class Note( /* ...as above... */ )

@Query("""
    SELECT * FROM notes
    WHERE title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%'
    ORDER BY createdAt DESC
""")
suspend fun searchSql(q: String): List<Note>
```

Run it. Same query. Read logcat. Record the new number. Then read the plan again:

```sql
EXPLAIN QUERY PLAN SELECT * FROM notes WHERE topic = 'kotlin';
```

With the `topic` index this now says `SEARCH notes USING INDEX index_notes_topic` instead of `SCAN notes`. (Note: a `LIKE '%...%'` substring search *can't* use a B-tree index — it's still a scan — but an exact `topic =` filter can. Measure and explain both; that distinction is worth more than the timing.)

### Step 5 — The relation N+1, measured

Add a relation query and contrast it with manually looping:

```kotlin
// THE N+1: query notes, then read each note's tags one at a time.
val notes = dao.allNotes()
notes.forEach { dao.tagsForNote(it.id) }   // one query PER note

// THE FIX: a single @Transaction relation query batches the children.
data class NoteWithTags(
    @Embedded val note: Note,
    @Relation(parentColumn = "id", entityColumn = "name",
        associateBy = Junction(NoteTagCrossRef::class, "noteId", "tagName"))
    val tags: List<Tag>
)

@Transaction
@Query("SELECT * FROM notes LIMIT 200")
suspend fun notesWithTags(): List<NoteWithTags>
```

Time both for 200 notes. The N+1 loop fires ~200 child queries; the relation query fires 2 (parent + batched children). Record both.

## Acceptance criteria

- [ ] The store is seeded with **≥ 50,000** notes.
- [ ] `searchNaive` and `searchSql` both exist, return the **same rows** for the same query (assert this — a faster wrong answer is worthless), and are both timed.
- [ ] `PERF.md` records: the naive timing, the SQL timing, the speedup factor, and the device/emulator you measured on.
- [ ] Two `EXPLAIN QUERY PLAN` outputs — the `SCAN` before and the `SEARCH ... USING INDEX` after (for the indexed exact-match case) — and one Database Inspector screenshot.
- [ ] A 3–5 sentence explanation of **why** the SQL version wins (in-SQLite filtering, index use, fewer rows materialised across the Cursor) — in your own words.
- [ ] The relation N+1 measured: the per-note-loop count vs. the `@Transaction` relation query count, with an explanation.
- [ ] Build with **0 warnings**.

## What "great" looks like

A weak submission says "the SQL one was faster." A great submission says:

> On a 50,000-row store on a Pixel 8 emulator (API 35), `searchNaive` averaged 138 ms and materialised all 50,000 `Note` objects across the Cursor; `searchSql` for the exact-match `topic = 'kotlin'` case averaged 1.6 ms once an index on `topic` existed — a ~86× speedup. `EXPLAIN QUERY PLAN` confirms it: the naive query is `SCAN notes`, the indexed one is `SEARCH notes USING INDEX index_notes_topic`. The substring `LIKE '%q%'` search stayed a scan even with the index (a B-tree can't accelerate a leading-wildcard match), so its win over the naive path comes purely from not materialising 50,000 objects to keep a handful, not from the index. The relation N+1 was the sharper bite: looping `tagsForNote(id)` over 200 notes fired 200 child queries; the `@Transaction` relation query fired 2.

Quantified, explained with the query plan, and honest about what the index did *not* do (substring). That's the senior-engineer answer.

## Where this reappears

The "measure, don't guess" instinct and the `EXPLAIN QUERY PLAN` / Database Inspector workflow are exactly what Week 18's performance work (macrobenchmark, Baseline Profiles) builds on. The footgun you fixed here — a full-table scan and an N+1 — is the same shape as the main-thread-query jank you'll diagnose then, just with a database query plan instead of a flame graph.
