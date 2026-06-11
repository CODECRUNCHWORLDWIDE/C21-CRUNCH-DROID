# Mini-Project — The Capstone Build: the Field-Force Companion to a locked RC

> Build the **Field-Force Companion** (the full spec is in [`SYLLABUS.md` § Capstone](../../../SYLLABUS.md)) from your twenty-two weeks of modules into one offline-first, multi-form-factor system, and lock it to a **release candidate** on a Play Console internal track: a signed, R8-optimized, Baseline-Profile-verified AAB, a signed Wear OS APK, the full test suite green on CI, four ADRs, a Mermaid architecture diagram, and a pre-submission audit reading all-PASS. This is the build half of the capstone — and the input to every Week 24 deliverable.

This is the capstone's first mile. It is **not new development of new frameworks** — every module is one you built in Weeks 1–22. The work this week is *integration and lock*: wire the seven modules into one Hilt graph, prove a write travels end to end, gate sign-in without a brick, and assemble the release candidate the way Play will serve it. By Sunday, twenty-two weeks of compounding work becomes one shipping system, locked clean enough that next week is delivery and a stress test, not a rescue.

The full capstone specification — the required architecture, the seven modules, the eight deliverables, the chaos-drill menu, and the 35%-of-course rubric — is in [`SYLLABUS.md` § Capstone](../../../SYLLABUS.md). This week's mini-project covers the **build-and-lock** half; Week 24's covers the **ship-and-survive** half. Together they deliver the capstone the whole track was building toward: an offline-first field-operations app with a Wear companion, a KMP core, gRPC sync, encrypted storage, Play Integrity, and a complete release pipeline.

**Estimated time:** ~12.5 hours of the week's schedule (Monday through Saturday mini-project blocks), on top of the exercises and Sunday's audit.

---

## Where you're starting from

You are *not* starting from an empty project. You are starting from twenty-two weeks of modules:

- A multi-module Gradle Kotlin DSL project with version catalogs and convention plugins (Week 6).
- Compose UI fluency with MVVM and `StateFlow<UiState>` (Weeks 7–12).
- A Hilt graph, a Room schema with migrations, a gRPC client with `NetworkResult`, a WorkManager sync engine, and a Baseline Profile workflow (Weeks 13–18).
- A KMP `:shared-core`, a Wear OS tile/complication, a CI/CD pipeline, and Play Integrity (Weeks 19–22).

The mini-project is to **specialize and integrate** these into the Field-Force Companion's seven modules. If you have been doing the weekly mini-projects, much of this is renaming and wiring; if you skipped some, this week is where the gaps show, and the honest move is to cut scope (the spec is deliberately small) rather than fake a module.

## What you're building toward

By the end you have, on the internal track and tagged `v1.0.0-rc1`:

- A `:shared-core` (KMP) with a typed `Dispatch` domain model, repository interfaces, a Ktor-shaped API surface, kotlinx-serialization, and kotlinx-datetime time math — depending on nothing in the project.
- An `:app` (Android) with a Compose dispatch list and detail screen, Material 3 + dynamic color, Navigation 3, MVVM, and the Hilt graph root.
- A `:wear` (Wear OS) with one tile (active dispatch), one complication (active count), and one ongoing activity (in-progress dispatch) — all reading the same `:shared-core` repository.
- A `:feature-sync` (WorkManager periodic job, exponential backoff, network/battery constraints, foreground promotion, driven by the outbox).
- A `:feature-auth` (Play Integrity attestation gate + Keystore-backed token store, with the three-outcome failure design).
- A `:core-network` (gRPC client with certificate pinning, structured retry, typed `NetworkResult`).
- A `:core-database` (Room with three entities, Proto DataStore preferences, schema export in source control, two migrations exercised in tests).
- The release candidate: a signed AAB on the internal track, a signed Wear APK, the suite green on CI, a verified Baseline Profile, the ADRs, and the architecture diagram.

---

## Milestone 1 — The module graph and the spine (≈ 2.5 h)

Set up the seven modules and the legal dependency graph (Exercise 1). Then build the **spine**: `:shared-core`'s `Dispatch` domain and `DispatchRepository` interface, `:core-database`'s Room schema and DAO, and `:core-network`'s gRPC client with `NetworkResult`. Wire them with a `DefaultDispatchRepository` that does the offline-first write (Lecture 1 §3, §5).

Decisions you must be able to defend in the architecture review:

- **Why does `:shared-core` depend on nothing?** So it is iOS-ready and trivially testable, and so the repository interface — the seam between UI and infrastructure — lives in the dependency-free root. (ADR-0002.)
- **Why is Room the source of truth?** Because the field worker is offline half the time; the UI must read from disk and never block on the network. (ADR-0001.)

Acceptance: the graph is legal and enforced (Exercise 1); the spine compiles; `DispatchRepository` is in `:shared-core` and the implementation is bound in `:app`.

## Milestone 2 — Trace one write end to end (≈ 1.5 h)

Wire the write path from Exercise 2 into the real app: the dispatch detail screen's "advance status" button → `ViewModel` → `DispatchRepository.updateStatus` → one Room transaction (dispatch row + outbox row) → the UI updates from the DB `Flow`. Confirm the write succeeds **offline** (toggle the emulator's network off and watch the status change instantly). Document the eight-hop trace in `docs/trace-one-write.md`.

The proof this milestone earns: turn off the network, advance a dispatch, and the UI updates immediately — because the write went to Room, not the wire. That is offline-first, demonstrated.

## Milestone 3 — Wire the form factors: the Wear companion (≈ 2 h)

Build the `:wear` module reading the same `:shared-core` repository (Lecture 2 §1): a `ScalingLazyColumn` dispatch list, the active-dispatch **tile**, the active-count **complication**, and the in-progress **ongoing activity**. Wire the seam where a completed sync calls `ActiveDispatchTileService.requestUpdate(context)` so the tile re-reads.

The test: change a dispatch on the phone, let it sync, and confirm the watch's complication reflects it. If the watch has its own model or its own network call, refactor it to read the shared repository — that refactor is the Wear lesson.

## Milestone 4 — Harden: the sync engine and the attestation gate (≈ 2 h)

Wire `:feature-sync`'s `SyncWorker` to drain the outbox: periodic WorkManager job, `BackoffPolicy.EXPONENTIAL`, network and battery constraints, foreground promotion when the user opens the app mid-sync. On `NetworkResult.Success`, mark the outbox row synced and write the server's state back into Room (the converge step). On conflict, apply ADR-0003's policy.

Wire `:feature-auth`'s Play Integrity gate (Exercise 3) into the real sign-in: the three-outcome sealed result, the Keystore-backed token store, and the documented fallback. Confirm on a Play-Services-less emulator that sign-in degrades gracefully (this is a dry run for next week's chaos drill C).

## Milestone 5 — Lock the release candidate (≈ 2.5 h)

This is the challenge, integrated: assemble the signed `:app:bundleRelease` AAB with R8 full mode and minimal keep rules (Lecture 2 §4), the signed `:wear:assembleRelease` APK, the packaged and verified Baseline Profile (≥ 20% cold-start improvement, Lecture 2 §3), the full test suite green on CI, the upload to the Play internal track, and the `v1.0.0-rc1` tag. After the tag, feature-freeze.

## Milestone 6 — The ADRs, the diagram, and the audit (≈ 2.5 h, into Saturday/Sunday)

Write the four ADRs and the Mermaid architecture diagram (Lecture 2 §5), and run the pre-submission readiness audit (Lecture 1 §6) until every row reads PASS. The architecture review on Sunday is the dry run for next week's interviews — surface your own biggest risk before it surfaces you.

---

## The seven modules, built (a per-module checklist)

Use this as a build sheet. Each module maps onto a prior week and a slice of the Field-Force Companion spec. Build the *real* thing small, never a fake thing large.

### `:shared-core` (KMP) — built in `commonMain`

- `Dispatch`, `DispatchId` (an inline value class), `DispatchStatus` (an enum), `OutboxOp` — the typed domain.
- `DispatchRepository` and `TokenProvider` interfaces — the seams the platform modules implement.
- The Ktor-shaped API surface and kotlinx-serialization wire types; kotlinx-datetime `Clock` for time math.
- **Test:** pure JVM unit tests on the domain and the repository's offline-first ordering (Exercise 2). No Android needed.
- **The contract:** zero project dependencies, zero `android.*` imports. If either appears, the boundary leaked (ADR-0002).

### `:core-database` — built on Room (Week 14)

- Three entities: `DispatchEntity`, `OutboxEntity`, and one more the spec implies (e.g. `NoteEntity` for dispatch notes, or a `SyncStateEntity`).
- The DAO with `observe()` returning a `Flow`, the transactional `updateStatus` + `enqueueOutbox`, and the outbox drain query.
- Proto DataStore for preferences; the schema export checked into source control; two migrations (v1→v2, v2→v3) exercised in Robolectric tests.
- **Test:** Robolectric DAO tests and the two migration tests against the exported schema.

### `:core-network` — built on grpc-kotlin (Week 15)

- The gRPC client over a pinned OkHttp channel, returning a typed `NetworkResult` sealed type.
- Structured retry and timeout defaults; certificate pinning that survives a cert rotation without bricking.
- **Test:** unit tests mapping gRPC responses to `NetworkResult`; the pinning config documented.
- **The contract:** the network layer knows nothing of Room or the UI — it owns the wire format and `NetworkResult`, full stop.

### `:feature-sync` — built on WorkManager (Week 16)

- A periodic `SyncWorker` with `BackoffPolicy.EXPONENTIAL`, network and battery constraints, and a unique-work policy.
- The outbox drain: read pending ops, push each via `:core-network`, mark synced, write server state back to Room (converge).
- Foreground promotion when the user opens the app mid-sync; the conflict step applies ADR-0003.
- **Test:** an integration test with `WorkManagerTestInitHelper` that drives a sync and asserts the outbox drains.

### `:feature-auth` — built on Play Integrity + Keystore (Week 22)

- The `PlayIntegrityGate` with the three-outcome sealed result (Exercise 3) and the documented fallback.
- A Keystore-backed encrypted token store; the `TokenProvider` implementation bound in `:app`.
- **Test:** the three-outcome contract (Exercise 3), faked for JVM; the no-brick path verified on a Play-Services-less emulator.

### `:app` (Android) — the composition root (Weeks 7–13)

- The Compose dispatch list and detail screens, Material 3 + dynamic color, Navigation 3, MVVM with `StateFlow<UiState>`.
- The Hilt graph: every `@Binds` that ties an implementation to a `:shared-core` interface lives here, and only here.
- **Test:** Compose UI tests on the screens, Paparazzi snapshots of the Material 3 states, one Espresso end-to-end smoke (release variant).

### `:wear` (Wear OS) — the companion (Week 20)

- A `ScalingLazyColumn` dispatch list, the active-dispatch tile, the active-count complication, the in-progress ongoing activity.
- All reading the same `DispatchRepository`; the tile re-reads on a `requestUpdate` call from the converge step.
- **Test:** the tile and complication services build domain data into their respective `ComplicationData`/tile layouts deterministically.

---

## The deliverables, mapped to the capstone rubric

Each milestone earns a slice of the capstone's 35%-of-course rubric in `SYLLABUS.md`. This week earns the build-quality lines and sets up the ship-and-survive lines for Week 24.

```text
capstone-repo/
├── README.md                    # overview, Mermaid diagram link, internal-track link,
│                                #   Baseline Profile before/after numbers, known limitations
├── docs/
│   ├── architecture.md          # the seven-module graph + the trace-one-write path
│   ├── trace-one-write.md       # the eight-hop write trace, owner named at each hop
│   ├── adr/0001-0004.md         # the four ADRs (this week)
│   └── pre-submission-audit.md  # the readiness audit, all-PASS (this week)
├── shared-core/ app/ wear/      # the seven modules
├── feature-sync/ feature-auth/
├── core-network/ core-database/
└── .github/workflows/ci.yml     # the full suite green on tag (Week 21)
```

The README is the front door. It must link the internal-track build, render or link the architecture diagram, state the Baseline Profile delta with both numbers, and list the known limitations honestly — the things you cut for scope, named, not hidden. A grader reads the README first; make it the truth.

---

## Acceptance criteria

- [ ] Seven modules with a legal, enforced dependency graph; `:shared-core` depends on nothing.
- [ ] A write traces end to end (`docs/trace-one-write.md`), demonstrated **offline** (UI updates from Room with the network off).
- [ ] The dispatch + outbox write is one Room transaction; the offline-first ordering is asserted by a test (Exercise 2's contract, on real Room).
- [ ] `:wear` shows a tile, a complication, and an ongoing activity, all reading the same `:shared-core` repository; a phone change reflects on the watch.
- [ ] `:feature-sync` drains the outbox with exponential backoff, constraints, and foreground promotion; the converge step writes server state back to Room.
- [ ] `:feature-auth` gates sign-in with Play Integrity; an attestation failure shows a message + fallback; a Play-Services-less device does not brick.
- [ ] A signed AAB on the Play internal track; a signed Wear APK; tagged `v1.0.0-rc1`; feature-frozen.
- [ ] A verified Baseline Profile (≥ 20% cold-start improvement, both numbers in the README).
- [ ] The full test suite (unit, Robolectric, Compose UI, Paparazzi, Espresso) green on CI.
- [ ] Four ADRs, the Mermaid architecture diagram, and an all-PASS pre-submission audit, committed.
- [ ] No credentials in the repo. Build with **0 warnings, 0 errors**.

## Cutting scope honestly when you must

The spec is deliberately small so you can build it *real*. But if you skipped a weekly mini-project and a module is genuinely not ready, the senior move is to cut scope honestly, not to fake it. The order to cut, from least to most damage:

1. **The gRPC backend → an in-memory fake** with the *identical* `NetworkResult` contract. You lose real sync latency (which weakens next week's drill A) but keep the offline-first write path intact. Acceptable, documented in the README's known-limitations.
2. **The third Wear surface → just the tile and complication.** The ongoing activity is the most involved Wear piece; if it is not ready, ship the tile and complication (both required by the spec) and note the ongoing activity as a known gap.
3. **The second migration → one migration.** The spec wants two; if you can only exercise one cleanly under a populated DB, ship one tested migration and name the gap. A tested migration beats two untested ones.

What you must **never** cut, because they are the capstone's load-bearing teaching points: the offline-first write path (Room as source of truth), the attestation gate's no-brick failure design, and the signed RC on the internal track. Cut a surface; never cut the spine.

The README's known-limitations section is where you name every cut, honestly. A grader respects "I shipped a fake backend because I couldn't run the reference server, here's the contract it satisfies" far more than a hidden mock discovered in the code. Honesty about scope is a senior signal; a hidden fake is a junior one.

## Stretch goals

- **The gRPC bonus over a fake.** If you can't run the reference backend, the in-memory fake is acceptable — but wire the *real* `grpc-kotlin` client against a local server if you can, so next week's chaos drill A exercises real sync latency, not a fake.
- **Proto DataStore preferences.** Add a user-preference (units, default status filter) in Proto DataStore, demonstrating the Preferences-vs-Proto choice from Week 14.
- **The Compose Compiler report in CI.** Wire the report (Week 7) into CI and fail the build if a hot dispatch-list composable loses `skippable`. A free recomposition regression guard on the capstone's busiest screen.
- **A second migration exercised under a populated DB.** Go beyond the two required migrations: write a test that populates v1, migrates to v3 with real rows, and asserts no data loss — a dry run for the migration question an interviewer will ask.

## The non-negotiables

Two things fail the build-week gate regardless of how good everything else is, because in a real launch they are incidents:

- **A credential in the repo.** The signing passwords live in CI secrets / `~/.gradle/gradle.properties`; the token lives in Keystore. `grep -ri "STORE_PASSWORD\|KEY_PASSWORD\|BEGIN PRIVATE KEY" .` must return nothing but property names. A leaked signing key is a security incident.
- **An attestation gate that fails open or bricks.** The gate must surface a message + fallback on failure and never treat an error as success. This is the exact design next week's chaos drill C tests; getting it wrong here fails the drill there.

Everything else — a region of the spec cut for scope, a stretch goal skipped, a keep rule you'll tighten next week — is recoverable. These two are not.

## What "done" looks like

A grader opens your repo, reads the README, and follows the internal-track link to a processed AAB. They read `docs/architecture.md` and see a legal seven-module graph. They read `docs/trace-one-write.md` and follow one write through eight hops, each with a named owner. They check out `v1.0.0-rc1`, build the release variant, and the Espresso smoke passes under R8. They read the Baseline Profile numbers and see a ≥ 20% cold-start cut. They open the four ADRs and find real trade-offs, not "this is best." They run the app on a Play-Services-less emulator and watch sign-in degrade to a fallback instead of bricking. The pre-submission audit is all-PASS. That is the capstone built and locked — and that is what makes Week 24 a launch.

---

## What this milestone earns you

You can now take twenty-two weeks of separate modules and integrate them into one offline-first, multi-form-factor production system, locked to a signed, optimized, tested, audited release candidate on a real distribution track. That is the literal "skills earned" line for the week: end-to-end production integration, pre-submission discipline, and living with your own architectural decisions. Week 24 — the final week — is the payoff: you run the three chaos drills against this RC, write the postmortems, submit it to the Play closed track, and defend every one of its decisions in four senior-Android interviews. You built the system; next week you ship it and prove it survives.
