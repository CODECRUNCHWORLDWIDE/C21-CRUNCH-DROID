# Week 23 — Quiz

Thirteen questions on capstone integration, the module graph, offline-first, the release candidate, Wear, Play Integrity, and the ADRs. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 24. Answer key with explanations at the bottom — don't peek.

---

**Q1.** In the capstone's seven-module graph, which module depends on nothing else in the project?

- A) `:app`, because it is the entry point.
- B) `:shared-core`, the dependency-free KMP root that everything else depends on.
- C) `:core-network`, because the network is the lowest layer.
- D) `:feature-sync`, because it runs in the background.

---

**Q2.** `:feature-sync` needs an auth token. Why must it NOT add `implementation(project(":feature-auth"))`?

- A) It would slow the build.
- B) It is a sideways feature-to-feature dependency; features must communicate through an interface in `:shared-core`, wired in `:app`, to stay independent and testable.
- C) `:feature-auth` is an app module and can't be depended on.
- D) Hilt forbids it.

---

**Q3.** In the offline-first design, what is the source of truth the UI reads from?

- A) The gRPC backend — the UI calls the network and renders the result.
- B) Room — the UI observes a DB-backed `Flow`; the network is a background sync target, not the UI's data source.
- C) An in-memory cache that the network fills.
- D) `SharedPreferences`.

---

**Q4.** A field worker taps "mark On-Site" while offline. In the correct design, what happens?

- A) A spinner shows until the network returns; the tap is queued client-side as a retry of the network call.
- B) The repository writes the new status to Room (and an outbox row) in one transaction and returns immediately; the UI updates from the DB Flow; sync happens later in the background.
- C) The tap is dropped because there is no network.
- D) The app switches to a read-only mode.

---

**Q5.** Why are the dispatch update and the outbox row written in a single Room transaction?

- A) For speed.
- B) Atomicity: a crash between them can't leave a changed dispatch with no pending sync op, or an op for a change that didn't land. The transaction makes the write durable and consistent.
- C) Room requires all writes to be in a transaction.
- D) To avoid recomposition.

---

**Q6.** "Trace one write end to end" asks you to narrate a write through eight hops. What is the value of being able to do that?

- A) It is documentation busywork.
- B) Each hop has exactly one owner (UI renders; repo owns the offline-first policy; WorkManager owns *when*; network owns the wire format; Room owns the truth). If you can name the owner at each hop, the system is integrated; the hop you hand-wave is where your bug lives.
- C) It proves the app is fast.
- D) It is only useful for the demo video.

---

**Q7.** The capstone's `:wear` module shows a tile, a complication, and an ongoing activity. What makes it a correct *companion* rather than a second app?

- A) It uses Compose for Wear.
- B) The three surfaces are three views of the SAME `:shared-core` repository the phone reads; change the data on the phone and the watch reflects it. A second app would have its own model and its own network calls.
- C) It is signed with the same key.
- D) It runs on a watch.

---

**Q8.** Play Integrity attestation fails (network error) at sign-in. What must the gate do?

- A) Treat the error as success and let the user in (fail open).
- B) Return a `Failed` result carrying a clear user message and a retry — never `Attested`, never a silent pass.
- C) Crash so the user knows something is wrong.
- D) Retry forever in a loop.

---

**Q9.** The capstone runs on an emulator with no Google Play Services (next week's chaos drill C). What is the correct behavior?

- A) Hard-require attestation — block sign-in entirely with a hang or crash.
- B) Return `PlayServicesUnavailable` and offer a documented fallback (web/managed-device sign-in) — never a brick, never a fail-open.
- C) Disable all security and let everyone in.
- D) Show a blank screen.

---

**Q10.** Why model the attestation outcome as a sealed interface with three cases rather than a `Boolean`?

- A) Sealed types are faster.
- B) A `Boolean` can't carry the user message or distinguish "Play Services unavailable" from "verdict rejected"; a sealed type forces the UI's `when` to handle every outcome the compiler can see, so a case can't be silently dropped.
- C) It is required by Play Integrity.
- D) For serialization.

---

**Q11.** Building the release variant, sign-in crashes but the debug build is fine. Most likely cause?

- A) The emulator is slow.
- B) R8 full mode stripped a class your reflective code (gRPC / kotlinx-serialization / Room) needs; `missing_rules.txt` names it, and a narrow `-keep` rule fixes it.
- C) The Baseline Profile is wrong.
- D) The signing key is wrong.

---

**Q12.** The capstone requires a Baseline Profile that cuts cold start by ≥ 20%. How do you *verify* the improvement?

- A) Time the app by hand with a stopwatch.
- B) Run a macrobenchmark with `StartupTimingMetric`, once with `CompilationMode.None()` and once with `CompilationMode.Partial()` (profile applied), and compare `timeToInitialDisplay`.
- C) Check that the profile file exists in the AAB.
- D) Read the R8 report.

---

**Q13.** What is an Architecture Decision Record, and what four parts does each capstone ADR have?

- A) A bug report; severity, steps, expected, actual.
- B) A short, immutable record of one architectural decision: the decision, the alternatives considered, the trade-off, and the consequence you accept.
- C) A test plan; setup, run, assert, teardown.
- D) A changelog entry; version, date, changes.

---

## Answer key

**Q1 — B.** `:shared-core` is the dependency-free KMP root — pure Kotlin domain, no Android, no Hilt — and everything depends on it. That is what makes it iOS-ready and trivially testable. `:app` depends on *everything* (it's the composition root), which is the opposite. (Lecture 1, §2.)

**Q2 — B.** A feature-to-feature dependency couples two features and starts the slide back to a monolith. The fix is an interface (`TokenProvider`) in `:shared-core`, implemented by `:feature-auth`, bound in `:app`. Sync depends on the interface, not the feature. (Lecture 1, §2 + §5; Exercise 1.)

**Q3 — B.** Offline-first inverts the naive design: Room is the source of truth, the UI observes a DB-backed `Flow`, and the network is a background sync target. This is what lets the app work in a basement. (Lecture 1, §3; ADR-0001.)

**Q4 — B.** Local-first: the write commits to Room (plus an outbox row) and returns immediately; the UI updates from the Flow; sync happens later. The UI never awaits the network. A spinner that waits for the network (A) is the broken design. (Lecture 1, §3–4; Exercise 2.)

**Q5 — B.** Atomicity. The dispatch change and its pending-sync op must land together or not at all, or a crash leaves the system inconsistent (a synced-looking dispatch with no op, or an op for a change that rolled back). (Lecture 1, §3; Exercise 2.)

**Q6 — B.** Each layer owns exactly one concern and the concerns don't leak. Naming the owner at each hop is the test of integration; the hop you can't narrate is where the bug hides. It is also the staff-interview question and the demo's spine. (Lecture 1, §4.)

**Q7 — B.** The Wear surfaces are three glances at the same repository the phone reads — a second *reader*, not a second app. If `:wear` had its own model and network calls, the KMP split earned nothing. (Lecture 2, §1.)

**Q8 — B.** Never fail open (an error treated as success defeats attestation) and never crash. Return `Failed` with a clear message and a retry. (Lecture 2, §2; Exercise 3; ADR-0004.)

**Q9 — B.** A Play-Services-less device can't attest. Return `PlayServicesUnavailable` and offer a documented fallback. A brick fails next week's chaos drill C; a fail-open defeats the control. (Lecture 2, §2; Exercise 3.)

**Q10 — B.** A `Boolean` can't carry the message or distinguish the three outcomes; the sealed type forces an exhaustive `when` so no outcome is silently dropped. The message is load-bearing — the user needs to know what to do. (Lecture 2, §2; Exercise 3.)

**Q11 — B.** A bug that appears only in release is the R8 signature: full mode stripped a class reflective code needs. `missing_rules.txt` names it; add the narrowest `-keep` rule. Never disable R8. (Lecture 2, §4; Challenge Step 2.)

**Q12 — B.** Measure, don't guess: a macrobenchmark with `StartupTimingMetric`, `None()` vs `Partial()`, comparing `timeToInitialDisplay`. The file existing (C) proves packaging, not improvement. (Lecture 2, §3; Challenge Step 3.)

**Q13 — B.** An ADR is a short, immutable record of one decision with four parts: the decision, the alternatives, the trade-off, and the consequence you accept. It is what turns "I built an app" into "I can defend these decisions." (Lecture 2, §5.)

---

*Score 11+? On to Week 24 — the final week. Below 9? Re-read both lecture notes and re-run exercises 2 and 3 — the offline-first write ordering and the no-brick attestation gate are the two ideas the capstone is graded on, and the two the chaos drills test next week.*
