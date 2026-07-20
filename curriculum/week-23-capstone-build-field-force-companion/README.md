# Week 23 — Capstone build week: Field-Force Companion

Welcome to Week 23 of **C21 · Crunch Droid**, and to the build half of the capstone. For twenty-two weeks you learned the pieces in isolation: Kotlin 2.x and coroutines, Compose and its runtime, Navigation 3, Material 3, MVVM with `StateFlow<UiState>`, Hilt, Room, gRPC, WorkManager, testing across every layer, Baseline Profiles, Wear OS, CI/CD, and Play Integrity. Each week shipped a small, real module that exercised one topic end to end. This week those modules stop being a portfolio of labs and become **one shipping system** — the **Field-Force Companion**, an offline-first multi-form-factor Android application for a fictional field-operations team.

You do not learn a new framework this week. You **integrate**. That is a different and harder skill, and it is the one a senior Android engineer is actually paid for. The hard part of integration is never any single module — you have already built every one. The hard part is the seams: the gRPC `NetworkResult` that has to flow into a Room transaction without losing the offline queue; the WorkManager job that has to promote itself to a foreground service when the user opens the app mid-sync; the Hilt graph that has to wire a KMP `:shared-core` repository into an Android `ViewModel` and a Wear OS tile at the same time; the Play Integrity token that has to gate sign-in without bricking the app on a Play-Services-less emulator. Integration is where the architectural decisions you made in Weeks 7–22 get paid back, with interest or with debt.

The mental shift this week is from "does this module work" to "is this system **release-ready**." Those are different questions. A module works when its tests pass; a system is release-ready when it survives the *combination* — a cold start under a Baseline Profile, a sync conflict under a flaky network, a process death mid-transaction, an attestation failure on a device without Google Play Services. By Sunday you have a **release-candidate AAB** signed and uploaded to a Play Console internal track, a **signed Wear OS APK**, a **KMP `:shared-core`** consumed by both, a **Mermaid architecture diagram** in `docs/architecture.md`, four **Architecture Decision Records**, and a **pre-submission audit** every row of which reads PASS. Next week — the final week — you polish, run the chaos drills, submit to the Play closed track, and sit your senior-Android interviews. This week you build the thing that makes next week possible.

The week has a deliberate rhythm, and it is the opposite of the all-nighter this course is named to avoid. You **integrate the spine first** (Monday–Tuesday): the `:shared-core` KMP model, the gRPC client, the Room cache, and the one screen that proves a write travels end to end. You **wire the form factors** (Wednesday): the Wear tile, the complication, the ongoing activity. You **harden** (Thursday): the WorkManager sync engine, the Play Integrity gate, the Baseline Profile. You **lock the release candidate** (Friday–Saturday): assemble the signed AAB, run the full test suite green on CI, upload to the internal track, write the ADRs and the architecture diagram. You **review and audit** (Sunday): the pre-submission checklist, the quiz, and the architecture review that surfaces your own biggest risk before next week's chaos drill does it for you. Build the release candidate early so that *if* something is wrong, you have the week to fix it instead of the weekend.

## Learning objectives

By the end of this week, you will be able to:

- **Integrate** a multi-module Android system — `:shared-core` (KMP), `:app`, `:wear`, `:feature-sync`, `:feature-auth`, `:core-network`, `:core-database` — into one Hilt-wired dependency graph where a write travels from UI through the domain layer, the gRPC client, the Room cache, and the WorkManager sync queue without losing the offline-first guarantee.
- **Trace one write end to end** — narrate, with mechanism at every hop, how a field dispatch update travels from a Compose `TextField` to the gRPC backend and back to a Wear OS complication, and identify which layer owns which concern.
- **Assemble** a release-candidate AAB: a Release-configuration, Play-App-Signing-ready build, with R8 full mode and a verified Baseline Profile, tagged `v1.0.0-rc1`, with the full test suite (unit, Robolectric, Compose UI, Paparazzi, one Espresso smoke) green on CI.
- **Wire** the Wear OS companion to the same shared core: one tile showing the active dispatch, one complication for the system watch face, and an ongoing activity for an in-progress dispatch — all reading the same `:shared-core` repository the phone reads.
- **Gate** sign-in with Play Integrity attestation backed by Keystore token storage, and design the gate so an attestation failure produces a clear user-facing message and a documented fallback — never a silent fail-open and never a hard brick.
- **Write** an Architecture Decision Record an interviewer would respect: the decision, the alternatives considered, the trade-off, and the consequence you are willing to live with.
- **Audit** the system against a pre-submission readiness checklist — Play Console requirements, R8 keep rules, the signing config, the Baseline Profile verification, the test suite — until every row reads PASS, so next week's submission walks in clean.

## Prerequisites

This week assumes you completed **C21 weeks 1–22** with their mini-projects passing the rubric, and that you can reach for any of them without re-learning it. Specifically, you need in hand:

- **A working multi-module Gradle Kotlin DSL setup** with version catalogs (`libs.versions.toml`) and convention plugins from Week 6, so adding a seventh module does not blow your build time past a minute on a clean build.
- **Compose fluency at the runtime level** — recomposition scope, stability, the three phases (Week 7), state and side effects (Week 8), and MVVM with `StateFlow<UiState>` (Week 12). The capstone UI is graded on the same "recompose the minimum" contract Week 7 installed.
- **A Hilt graph you can extend** (Week 13), a Room schema with migrations and a schema export (Week 14), a gRPC client with a typed `NetworkResult` (Week 15), a WorkManager sync engine with exponential backoff and foreground promotion (Week 16), and a Baseline Profile workflow (Week 18).
- **A KMP `:shared-core` module** from Week 19 and a Wear OS tile/complication from Week 20, plus a CI/CD pipeline from Week 21 and Play Integrity from Week 22. This week stitches all of them together — none is taught fresh.
- **A reachable backend.** The capstone talks gRPC to a typed backend. The course provides a reference `field-force-backend` (a small Kotlin/`grpc-kotlin` server you run locally, or the shared staging instance); a stubbed in-memory fake is acceptable for learners who cannot run the server, as long as the `NetworkResult` contract is identical.

**Toolchain.** Android Studio Ladybug (2024.2) or newer, JDK 17, Kotlin 2.0+ with the Compose Compiler plugin, compileSdk 35 (Android 15), minSdk 26 (Wear OS requires 26+; the phone app could go lower but we hold one floor), targetSdk 35. A Pixel 8 API 35 emulator and a Wear OS API 34 emulator are the reference devices. A Play Console account with an app record on the internal track (the USD 25 one-time fee; F-Droid is the documented no-fee fallback for the final submission, but the internal-track upload this week wants a real console).

## Topics covered

- **Capstone integration.** Pulling Weeks 1–22 into one shipping system: the module graph, the dependency directions (who may depend on whom), and the seams where modules meet.
- **The trace-one-write discipline.** Following a single field-dispatch update from the Compose UI through the `ViewModel`, the domain repository in `:shared-core`, the gRPC client in `:core-network`, the Room cache in `:core-database`, the WorkManager queue in `:feature-sync`, and back out to the Wear complication — naming the owner of each concern.
- **Offline-first as a system property, not a feature.** Local-first writes (Room is the source of truth, the network is a sync target), the outbox/queue pattern, the conflict-resolution policy you commit to, and why the UI never blocks on the network.
- **The release-candidate AAB.** Release configuration, R8 full mode with the keep rules your reflection-heavy code (gRPC, kotlinx-serialization, Room) actually needs, the signing config and Play App Signing, the Baseline Profile packaged and verified, the `v1.0.0-rc1` tag.
- **The Wear OS companion against the shared core.** One tile, one complication, one ongoing activity, all reading the same `:shared-core` repository the phone reads — proving the KMP split earned its keep.
- **Play Integrity as a sign-in gate, done right.** The attestation flow, Keystore-backed token storage, and the design discipline that turns an attestation failure into a clear message and a documented fallback rather than a silent fail-open or a hard brick.
- **Architecture Decision Records.** The four ADRs the capstone requires — the offline-first source-of-truth choice, the conflict-resolution policy, the KMP boundary, and the attestation/fallback design — each with the decision, the alternatives, the trade-off, and the consequence.
- **The architecture diagram.** A Mermaid module-and-data-flow diagram in `docs/architecture.md` you can defend on a whiteboard, showing the module graph and the trace-one-write path.
- **The pre-submission readiness audit.** A row-by-row checklist — Play Console requirements, the signing config, R8, the Baseline Profile verification, the full test suite green on CI — that must be all-PASS before next week's submission.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract; the build week deserves whatever it takes to lock a clean release candidate.

| Day       | Focus                                                                | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|----------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | Integration plan; the module graph; the spine (shared-core + gRPC + Room) |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     1.5h     |    0.5h    |     7h      |
| Tuesday   | Trace one write end to end; offline-first wiring; the first screen   |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     1.5h     |    0h      |     6.5h    |
| Wednesday | Wire the form factors: Wear tile, complication, ongoing activity     |    1h    |    1.5h   |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7.5h    |
| Thursday  | Harden: WorkManager sync, Play Integrity gate, Baseline Profile      |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Lock the RC: signed AAB, R8, CI green, internal-track upload         |    0h    |    1h     |     0h     |    0.5h   |   1h     |     2.5h     |    0h      |     5h      |
| Saturday  | The ADRs and the architecture diagram; the trace-one-write doc       |    0h    |    0h     |     0h     |    0h     |   0h     |     2.5h     |    0.5h    |     3h      |
| Sunday    | Pre-submission audit; architecture review; quiz; push               |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                      | **6h**   | **6.5h**  | **2h**     | **3.5h**  | **5h**   | **12.5h**    | **2h**     | **37.5h**   |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./README.md) | This overview (you are here) |
| [resources.md](./resources.md) | The Field-Force Companion spec, the architecture references (Now-In-Android), the module-graph and ADR guides, the Play Console internal-track and AAB docs, and the Wear/KMP/Play-Integrity references |
| [lecture-notes/01-from-lab-modules-to-a-release-candidate.md](./lecture-notes/01-from-lab-modules-to-a-release-candidate.md) | The integration architecture: the module graph and dependency directions, offline-first as a system property, the trace-one-write discipline, and the release-candidate checklist |
| [lecture-notes/02-wiring-the-form-factors-and-the-release-candidate.md](./lecture-notes/02-wiring-the-form-factors-and-the-release-candidate.md) | The Wear OS companion against the shared core, the Play Integrity sign-in gate done right, the Baseline Profile, R8 and the signing config, and the ADRs and architecture diagram |
| [exercises/README.md](./exercises/README.md) | Index of the three exercises |
| [exercises/exercise-01-module-graph-and-dependency-directions.md](./exercises/exercise-01-module-graph-and-dependency-directions.md) | Draw the capstone module graph, fix an illegal dependency direction, and enforce it with a Gradle dependency rule |
| [exercises/exercise-02-trace-one-write-end-to-end.kt](./exercises/exercise-02-trace-one-write-end-to-end.kt) | Wire a dispatch-status write from the `ViewModel` through the offline-first repository to the Room cache and the WorkManager outbox; assert the local-first ordering with a test |
| [exercises/exercise-03-play-integrity-signin-gate.kt](./exercises/exercise-03-play-integrity-signin-gate.kt) | Implement the Play Integrity attestation gate with a Keystore-backed token store and a documented fallback; assert the three outcomes (attested, failed-with-message, no-Play-Services) |
| [challenges/README.md](./challenges/README.md) | Index of the build-week challenge |
| [challenges/challenge-01-lock-the-release-candidate.md](./challenges/challenge-01-lock-the-release-candidate.md) | Assemble the signed RC AAB with R8 + Baseline Profile, get the full test suite green on CI, upload to the Play internal track, and write the four ADRs |
| [quiz.md](./quiz.md) | 13 questions on integration, the module graph, offline-first, the RC, Wear, Play Integrity, and the ADRs |
| [homework.md](./homework.md) | Six build-week deliverables with a rubric |
| [mini-project/README.md](./mini-project/README.md) | The capstone build brief — build the Field-Force Companion to a locked, internal-track release candidate |

## The "release-candidate, not a demo" promise

Every week of Crunch Droid adds one production-grade contract a senior reviewer actually checks. This week's is the capstone's first half:

> **By Sunday you have a signed, R8-optimized, Baseline-Profile-verified release-candidate AAB on a Play Console internal track, a signed Wear OS APK reading the same `:shared-core` the phone reads, the full test suite green on CI, and a pre-submission audit every row of which is PASS.** Not "it runs on my emulator." Not "the modules each pass their tests." A *locked release candidate* — assembled, signed, optimized, tested, uploaded, and audited — so next week is submission and chaos drill, not a build sprint.

You will *prove* this by tracing one write end to end (Exercise 2), gating sign-in without a brick (Exercise 3), and locking the RC on the internal track (the challenge). "It builds" is not the bar. "It is signed, optimized, on the internal track, green on CI, and audited PASS" is the bar.

## A note on what's not here

This is the build week, and it deliberately does **not** include:

- **The chaos drills, the submission, and the interviews.** Running the three chaos drills, writing the postmortems, submitting to the Play closed track, and sitting the senior-Android mock interviews are **Week 24**, the final week. This week ends at a *locked release candidate on the internal track*; next week ships it and breaks it on purpose.
- **New features after the RC tag.** Once you tag `v1.0.0-rc1` Saturday, the app is feature-frozen. New code after that is limited to the audit fixes the readiness checklist surfaces — never new scope. A feature added the day before the freeze is the feature that fails the audit.
- **New frameworks.** Everything this week is a framework you already learned in Weeks 1–22. If you find yourself learning a new library this week, you are gold-plating; stop and integrate what you have.

The point of Week 23 is integration discipline: take twenty-two weeks of modules, wire them into one offline-first multi-form-factor system, and lock it to a release candidate clean enough that the final week is a victory lap and a stress test, not a rescue.

## Up next

Continue to **Week 24 — Capstone polish, chaos drill, Play submission, interview prep** once you have a locked `v1.0.0-rc1` on the internal track, the test suite green on CI, the four ADRs and the architecture diagram committed, and the pre-submission audit all-PASS. Week 24 is the final week: you run the three chaos drills (offline-sync conflict, FCM token rotation, Play Integrity attestation failure), write the blameless postmortems, submit the signed AAB to a Play Console closed track, and sit four mock senior-Android interviews. Everything in Week 24 assumes the RC is locked from this week — submit late and a chaos-drill finding has nowhere to go; lock it now and the final week is the launch you earned.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
