// Exercise 2 — Trace one write end to end: prove the offline-first ordering
//
// Goal: Wire a dispatch-status write from the ViewModel through the offline-first
//       repository into the Room cache and the WorkManager outbox, and prove the
//       LOCAL-FIRST ORDERING with a plain JVM test: the DB transaction (dispatch
//       row + outbox row) commits BEFORE any network call, and the UI's Flow emits
//       the new state without the network being involved at all.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// This runs as a PLAIN JVM unit test (no Android, no Room runtime, no emulator).
// We fake the DAO and the network client so the test pins the WIRING CONTRACT the
// real repository must satisfy. In the mini-project you swap the fakes for real
// Room + a real gRPC stub; the ordering contract this test enforces stays identical.
//
//   1. Put the domain types + DefaultDispatchRepository in :shared-core (or a JVM
//      module). They are pure Kotlin.
//   2. Put this test in the test source set and run it.
//
// ACCEPTANCE CRITERIA
//
//   [ ] updateStatus writes the dispatch row AND the outbox row in ONE transaction.
//   [ ] The DB write happens BEFORE any network call (local-first ordering).
//   [ ] The dispatches() Flow emits the new status with NO network call required.
//   [ ] If the DB write fails, NO outbox row is left behind (transactional).
//   [ ] Builds with 0 warnings; the test passes.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.fieldforce.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

// ----------------------------------------------------------------------------
// DOMAIN — pure Kotlin, :shared-core. No Android, no Room, no gRPC.
// ----------------------------------------------------------------------------

@JvmInline
value class DispatchId(val value: String)

enum class DispatchStatus { Assigned, EnRoute, OnSite, Done }

data class Dispatch(
    val id: DispatchId,
    val title: String,
    val status: DispatchStatus,
    val updatedAtEpochMs: Long,
) {
    val isActive: Boolean get() = status == DispatchStatus.EnRoute || status == DispatchStatus.OnSite
}

/** An outbox row: a pending sync operation, written in the SAME transaction as the
 *  dispatch change so a write is durable across offline periods. */
data class OutboxOp(
    val dispatchId: DispatchId,
    val newStatus: DispatchStatus,
    val enqueuedAtEpochMs: Long,
    val synced: Boolean = false,
)

// The repository interface lives in :shared-core (Lecture 1 §5). The UI/ViewModel
// depends on THIS, never on Room or the network.
interface DispatchRepository {
    fun dispatches(): Flow<List<Dispatch>>
    suspend fun updateStatus(id: DispatchId, status: DispatchStatus): Result<Unit>
}

// ----------------------------------------------------------------------------
// THE DAO SEAM — an interface the real Room DAO implements. We fake it in the test.
// withTransaction models Room's @Transaction: all-or-nothing.
// ----------------------------------------------------------------------------

interface DispatchDao {
    fun observe(): Flow<List<Dispatch>>
    suspend fun <R> withTransaction(block: suspend () -> R): R
    suspend fun updateStatus(id: DispatchId, status: DispatchStatus, updatedAtEpochMs: Long)
    suspend fun enqueueOutbox(op: OutboxOp)
    suspend fun outbox(): List<OutboxOp>
}

// The network seam. The repository does NOT call it on the write path — sync does.
// We pass it in only to PROVE the write path never touches it (the test asserts 0 calls).
interface DispatchClient {
    suspend fun pushStatus(id: DispatchId, status: DispatchStatus): Result<Unit>
}

// A monotonic clock seam so the test is deterministic (kotlinx-datetime in prod).
fun interface NowMs { fun now(): Long }

// ----------------------------------------------------------------------------
// THE REPOSITORY — the offline-first policy lives HERE and nowhere else.
// TODO 1: implement updateStatus so it writes the dispatch row AND the outbox row
//         in ONE dao.withTransaction { } block, and returns WITHOUT calling the
//         network. (The network is the sync worker's job, not the write path's.)
// ----------------------------------------------------------------------------

class DefaultDispatchRepository(
    private val dao: DispatchDao,
    private val client: DispatchClient,   // present, but the write path must NOT call it
    private val now: NowMs,
) : DispatchRepository {

    override fun dispatches(): Flow<List<Dispatch>> = dao.observe()

    override suspend fun updateStatus(id: DispatchId, status: DispatchStatus): Result<Unit> =
        runCatching {
            // TODO 1: one transaction — update the dispatch row, then enqueue the outbox row.
            //         Do NOT call client.pushStatus here.
            dao.withTransaction {
                dao.updateStatus(id, status, now.now())
                dao.enqueueOutbox(OutboxOp(id, status, now.now()))
            }
        }
}

// ----------------------------------------------------------------------------
// FAKES — a fake DAO (in-memory, transactional) and a counting fake client.
// ----------------------------------------------------------------------------

class FakeDispatchDao(initial: List<Dispatch>) : DispatchDao {
    private val state = MutableStateFlow(initial)
    private val outboxRows = mutableListOf<OutboxOp>()
    var failNextTransaction = false

    override fun observe(): Flow<List<Dispatch>> = state

    override suspend fun <R> withTransaction(block: suspend () -> R): R {
        // model Room's atomicity: snapshot, run, and roll back on failure.
        val snapshotDispatches = state.value
        val snapshotOutbox = outboxRows.toList()
        return try {
            if (failNextTransaction) error("simulated DB failure")
            block()
        } catch (e: Throwable) {
            state.value = snapshotDispatches            // roll back
            outboxRows.clear(); outboxRows.addAll(snapshotOutbox)
            throw e
        }
    }

    override suspend fun updateStatus(id: DispatchId, status: DispatchStatus, updatedAtEpochMs: Long) {
        state.update { list ->
            list.map { if (it.id == id) it.copy(status = status, updatedAtEpochMs = updatedAtEpochMs) else it }
        }
    }

    override suspend fun enqueueOutbox(op: OutboxOp) { outboxRows += op }
    override suspend fun outbox(): List<OutboxOp> = outboxRows.toList()
}

class CountingClient : DispatchClient {
    var pushCalls = 0
        private set
    override suspend fun pushStatus(id: DispatchId, status: DispatchStatus): Result<Unit> {
        pushCalls++
        return Result.success(Unit)
    }
}

// ----------------------------------------------------------------------------
// THE TEST — pins the offline-first ordering contract.
// ----------------------------------------------------------------------------

class TraceOneWriteTest {

    private val seed = listOf(
        Dispatch(DispatchId("d1"), "Inspect transformer", DispatchStatus.Assigned, 0L),
        Dispatch(DispatchId("d2"), "Replace meter", DispatchStatus.Assigned, 0L),
    )

    @Test
    fun `write commits to DB and outbox without touching the network`() = runTest {
        val dao = FakeDispatchDao(seed)
        val client = CountingClient()
        val repo = DefaultDispatchRepository(dao, client, now = NowMs { 1_000L })

        val result = repo.updateStatus(DispatchId("d1"), DispatchStatus.OnSite)

        assertTrue(result.isSuccess)
        // the UI's Flow shows the new status — from the DB, no network involved.
        val d1 = repo.dispatches().first().first { it.id == DispatchId("d1") }
        assertEquals(DispatchStatus.OnSite, d1.status)
        assertEquals(1_000L, d1.updatedAtEpochMs)
        // an outbox row was enqueued for the sync worker.
        assertEquals(1, dao.outbox().size)
        assertEquals(DispatchStatus.OnSite, dao.outbox().first().newStatus)
        // CRUX: the write path never touched the network.
        assertEquals(0, client.pushCalls, "the write path must be local-first; sync owns the network")
    }

    @Test
    fun `a failed transaction leaves no partial state — no orphan outbox row`() = runTest {
        val dao = FakeDispatchDao(seed)
        val client = CountingClient()
        val repo = DefaultDispatchRepository(dao, client, now = NowMs { 2_000L })
        dao.failNextTransaction = true

        val result = repo.updateStatus(DispatchId("d2"), DispatchStatus.EnRoute)

        assertTrue(result.isFailure)                    // surfaced, not swallowed
        // the dispatch is unchanged AND no outbox row leaked — the transaction rolled back.
        val d2 = repo.dispatches().first().first { it.id == DispatchId("d2") }
        assertEquals(DispatchStatus.Assigned, d2.status)
        assertTrue(dao.outbox().isEmpty(), "a rolled-back transaction must leave no outbox row")
        assertEquals(0, client.pushCalls)
    }
}

// ----------------------------------------------------------------------------
// WHY this ordering matters (write it before reading):
//
//   - The field worker is offline half the time. If the write path awaited the
//     network, the UI would block (a spinner that never resolves). Writing to Room
//     first makes the write succeed instantly; the dispatches() Flow emits, the UI
//     updates, and sync happens later in the background (the SyncWorker reads the
//     outbox). client.pushCalls == 0 on the write path is the offline-first proof.
//   - The dispatch row and the outbox row go in ONE transaction so a crash between
//     them can't leave a synced-looking dispatch with no pending op, or an op for a
//     change that didn't land. Atomicity is the durability guarantee.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - Test fails with pushCalls == 1? You called client.pushStatus in updateStatus.
//   Remove it — the write path is local-only. Pushing is the SyncWorker's job
//   (mini-project Milestone 4), driven by the outbox you just enqueued.
//
// - The roll-back test fails (outbox not empty)? Your two writes aren't inside the
//   SAME withTransaction block, so the failure leaves the first write committed.
//   Put updateStatus + enqueueOutbox both inside the one block.
//
// - runTest unresolved? Add kotlinx-coroutines-test to the test source set.
//
// - "Why pass the client at all if the write path never calls it?" To PROVE it
//   never calls it (the 0-call assertion). In the real repo the client is used by
//   the sync path, which this exercise deliberately leaves to the SyncWorker.
//
// ----------------------------------------------------------------------------
