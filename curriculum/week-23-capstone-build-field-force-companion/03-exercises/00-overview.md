# Week 23 — Exercises

Integration drills. Each one isolates a seam of the capstone so you can get the wiring right in the small before you do it in the large. Do them in order; they map onto the mini-project's milestones.

## Index

1. **[Exercise 1 — The module graph and dependency directions](./exercise-01-module-graph-and-dependency-directions.md)** — draw the capstone's seven-module dependency graph, find and fix an illegal dependency edge, and enforce the legal directions with a Gradle dependency rule so a future violation fails the build. The system's spine, made explicit. (~50 min)
2. **[Exercise 2 — Trace one write end to end](./exercise-02-trace-one-write-end-to-end.kt)** — wire a dispatch-status write from the `ViewModel` through the offline-first repository into the Room cache and the WorkManager outbox, and assert the local-first ordering (DB committed before any network call) with a JVM test using a fake DAO and a fake client. The trace-one-write discipline, in code. (~50 min)
3. **[Exercise 3 — The Play Integrity sign-in gate](./exercise-03-play-integrity-signin-gate.kt)** — implement the attestation gate with a Keystore-backed token store (faked for the JVM test) and a documented fallback, then assert all three outcomes: attested, failed-with-message, and Play-Services-unavailable. The line of code that earns next week's chaos drill C. (~50 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. The capstone is integration, and integration is muscle memory for where each seam goes.
- The `.kt` exercises are written to run as **plain JVM unit tests** (no emulator, no Android runtime) so you can prove the *wiring contract* deterministically. The integration against the real Room/gRPC/Play-Integrity happens in the mini-project; these drills pin the contract the real code must satisfy.
- Each `.kt` file says at the top exactly where it goes (`:shared-core`'s test source set, or a JVM test module) and what it asserts.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. An offline-first write that touches the network before Room is a bug this week — the test is the arbiter, not your intuition.

These exercises are the seams of the capstone in isolation. When all three pass, the mini-project is "do this for real, with the real Room, the real gRPC stub, and the real Play Integrity client, on the two emulators." The contracts are identical; only the implementations change.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-23` to compare.
