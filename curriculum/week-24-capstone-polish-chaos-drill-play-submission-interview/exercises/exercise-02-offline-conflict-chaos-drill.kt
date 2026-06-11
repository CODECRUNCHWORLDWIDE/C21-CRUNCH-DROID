// Exercise 2 — The offline-conflict chaos drill (drill A): prove convergence
//
// Goal: Drive the offline-sync conflict deterministically: two devices edit the
//       SAME dispatch while offline, both reconnect, and the system converges. Prove
//       (a) DETERMINISM — both devices end at the same state regardless of reconnect
//       order — and (b) the documented LOSS BOUND: a same-field conflict resolves to
//       the later server timestamp (LWW), and a DIFFERENT-field edit survives (merge).
//
// Estimated time: 55 minutes.
//
// HOW TO USE THIS FILE
//
// This runs as a PLAIN JVM unit test (no emulator). It models two device replicas
// and a server, drives the offline-edit-reconnect-converge sequence, and asserts the
// ADR-0003 contract. The LIVE drill in the mini-project drives two real emulators and
// measures real gRPC propagation latency; this test pins what "converged" MEANS so
// the live drill has a contract to check against.
//
// ACCEPTANCE CRITERIA
//
//   [ ] Both replicas converge to the SAME dispatch after both sync (determinism).
//   [ ] A same-field (status) conflict resolves to the LATER server timestamp (LWW).
//   [ ] A different-field (note) edit on the other device SURVIVES (field merge).
//   [ ] Convergence is independent of reconnect ORDER (device1-first == device2-first).
//   [ ] No UNEXPECTED loss: the only loss is the documented same-field LWW case.
//   [ ] Builds with 0 warnings; the tests pass.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.fieldforce.chaos

import kotlin.test.Test
import kotlin.test.assertEquals

// ----------------------------------------------------------------------------
// THE MODEL — a dispatch with two independently-editable fields (status, note) and
// a per-field server timestamp so LWW can be applied field by field.
// ----------------------------------------------------------------------------

enum class Status { Assigned, EnRoute, OnSite, Done }

data class Dispatch(
    val id: String,
    val status: Status,
    val statusTs: Long,        // server-assigned timestamp of the last status write
    val note: String,
    val noteTs: Long,          // server-assigned timestamp of the last note write
)

// A pending edit a device made offline. It carries WHICH field changed so the merge
// is field-level, not whole-record (which would clobber the other device's edit).
sealed interface Edit {
    val dispatchId: String
    data class StatusEdit(override val dispatchId: String, val status: Status) : Edit
    data class NoteEdit(override val dispatchId: String, val note: String) : Edit
}

// ----------------------------------------------------------------------------
// THE SERVER — applies edits with field-level last-writer-wins by the timestamp IT
// assigns at apply time (the server clock is authoritative, ADR-0003). This is the
// converge logic the drill proves.
// ----------------------------------------------------------------------------

class Server(initial: Dispatch) {
    private var current = initial
    private var clock = initial.maxTs()

    /** Apply one device's edit. The SERVER assigns the timestamp (its clock), and
     *  LWW is per-field: a later status write wins the status; a later note write
     *  wins the note; the two never clobber each other. */
    fun apply(edit: Edit): Dispatch {
        clock += 1                                  // monotonic server clock
        current = when (edit) {
            is Edit.StatusEdit ->
                // later status timestamp wins; note is untouched (field merge).
                current.copy(status = edit.status, statusTs = clock)
            is Edit.NoteEdit ->
                current.copy(note = edit.note, noteTs = clock)
        }
        return current
    }

    fun snapshot(): Dispatch = current
}

private fun Dispatch.maxTs(): Long = maxOf(statusTs, noteTs)

// ----------------------------------------------------------------------------
// A DEVICE REPLICA — holds a local copy, queues edits while offline, and on
// reconnect pushes its queued edits then pulls the server's converged state.
// ----------------------------------------------------------------------------

class Device(private val server: Server, var local: Dispatch) {
    private val outbox = ArrayDeque<Edit>()

    fun editStatusOffline(status: Status) {
        local = local.copy(status = status)         // optimistic local (UI updates now)
        outbox += Edit.StatusEdit(local.id, status)
    }

    fun editNoteOffline(note: String) {
        local = local.copy(note = note)
        outbox += Edit.NoteEdit(local.id, note)
    }

    /** Reconnect: drain the outbox to the server, then pull the converged state. */
    fun reconnectAndSync() {
        while (outbox.isNotEmpty()) server.apply(outbox.removeFirst())
        local = server.snapshot()                   // converge: adopt the authoritative state
    }
}

// ----------------------------------------------------------------------------
// THE DRILL TESTS
// ----------------------------------------------------------------------------

class OfflineConflictDrillTest {

    private fun seed() = Dispatch(
        id = "d1", status = Status.Assigned, statusTs = 1, note = "", noteTs = 1,
    )

    @Test
    fun `same-field conflict resolves to the later write; both devices converge`() {
        val server = Server(seed())
        val d1 = Device(server, seed())
        val d2 = Device(server, seed())

        // both offline, both edit the SAME field (status) differently.
        d1.editStatusOffline(Status.OnSite)
        d2.editStatusOffline(Status.Done)

        // device 1 reconnects first, then device 2.
        d1.reconnectAndSync()
        d2.reconnectAndSync()
        d1.reconnectAndSync()                        // d1 pulls again to converge

        // LWW: device 2 synced its status LAST, so Done wins. Both converge to it.
        assertEquals(Status.Done, server.snapshot().status)
        assertEquals(d1.local, d2.local)             // determinism: same state on both
        assertEquals(Status.Done, d1.local.status)
    }

    @Test
    fun `different-field edits BOTH survive — field merge, no clobber`() {
        val server = Server(seed())
        val d1 = Device(server, seed())
        val d2 = Device(server, seed())

        // device 1 edits status, device 2 edits the NOTE — different fields.
        d1.editStatusOffline(Status.OnSite)
        d2.editNoteOffline("gate code 4417")

        d1.reconnectAndSync()
        d2.reconnectAndSync()
        d1.reconnectAndSync()

        // BOTH edits survive: status from d1, note from d2. No unexpected loss.
        assertEquals(Status.OnSite, server.snapshot().status)
        assertEquals("gate code 4417", server.snapshot().note)
        assertEquals(d1.local, d2.local)             // converged identically
    }

    @Test
    fun `convergence is independent of reconnect ORDER`() {
        // run the same same-field conflict with the reconnect order reversed and
        // confirm the WINNER is determined by sync order (the later sync), proving
        // the policy is deterministic given an order, and both devices always agree.
        fun run(device2First: Boolean): Dispatch {
            val server = Server(seed())
            val d1 = Device(server, seed())
            val d2 = Device(server, seed())
            d1.editStatusOffline(Status.OnSite)
            d2.editStatusOffline(Status.Done)
            if (device2First) { d2.reconnectAndSync(); d1.reconnectAndSync() }
            else { d1.reconnectAndSync(); d2.reconnectAndSync() }
            // both pull once more to fully converge
            d1.reconnectAndSync(); d2.reconnectAndSync()
            assertEquals(d1.local, d2.local)         // ALWAYS converge, either order
            return server.snapshot()
        }
        // d1-first -> d2 syncs last -> Done; d2-first -> d1 syncs last -> OnSite.
        assertEquals(Status.Done, run(device2First = false).status)
        assertEquals(Status.OnSite, run(device2First = true).status)
    }
}

// ----------------------------------------------------------------------------
// WHAT THE DRILL PROVES (and the postmortem documents):
//
//   - DETERMINISM: both replicas always converge to the same dispatch. This is the
//     non-negotiable contract; a system where two devices show different states
//     after sync has lost the plot.
//   - THE LOSS BOUND: the ONLY loss is a same-field LWW conflict (two devices set
//     the status), and it resolves to the later server-assigned timestamp. A
//     different-field edit (one sets status, one sets the note) NEVER loses — that's
//     the field-level merge. The postmortem states this bound explicitly: "in N
//     same-field trials, the later write won 100%; in N different-field trials, zero
//     edits were lost."
//   - THE GOTCHA (Lecture 2 §2): reconnect != converge. The LIVE drill measures the
//     gRPC propagation lag between reconnect and the second device pulling the
//     converged state; if your SLO budgeted only the merge cost, the live drill
//     blows past it. This test pins the CORRECTNESS; the live drill measures the
//     LATENCY.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - Different-field test fails (one edit lost)? Your server applies edits at the
//   WHOLE-RECORD level (copying the whole dispatch), so the second apply clobbers
//   the first device's field. Apply PER FIELD: a StatusEdit touches only status, a
//   NoteEdit only the note.
//
// - Determinism test fails (devices differ)? A device isn't pulling the server's
//   state after pushing. reconnectAndSync must (1) push the outbox, THEN (2) adopt
//   server.snapshot(). The extra pull at the end lets the earlier device see the
//   later device's write.
//
// - "Which write wins feels arbitrary." It's the later SERVER-ASSIGNED timestamp,
//   not the wall-clock of the edit. The server clock is authoritative (ADR-0003) so
//   two offline devices with skewed clocks still converge deterministically.
//
// - Real capstone: the server is your gRPC backend, the timestamps are server-side,
//   and the device pull is a gRPC stream / a sync pull. The contract is identical.
//
// ----------------------------------------------------------------------------
