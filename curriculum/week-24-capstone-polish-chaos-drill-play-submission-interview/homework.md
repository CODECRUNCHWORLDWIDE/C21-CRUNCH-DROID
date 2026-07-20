# Week 24 Homework

The final week's deliverables: the things you submit for capstone completion. The set should take about **5 hours**, on top of the mini-project and the live drills. Work in your capstone repository so each produces a committed artifact a grader can open.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

This is the last homework of C21. Every artifact here is graded for capstone completion.

---

## Problem 1 — The three chaos-drill postmortems

**Problem statement.** Run all three chaos drills against the live system and write a blameless `postmortem-<drill>.md` for each (Lecture 2 §3): drill A (offline-sync conflict), drill B (FCM token rotation), drill C (Play Integrity attestation failure). Each has the five sections — timeline (real timestamps), root cause (system mechanism), blast radius, what we changed (tagged accept/fix-now/fix-later), what we'd do differently.

**Acceptance criteria.**

- Three postmortem files, each with all five sections and a measured timeline.
- Each names a real *surprise*, not just "it recovered."
- Drill A documents the convergence and the loss bound; drill B proves no message dropped; drill C proves graceful fallback (no brick, no fail-open).
- Every "fix-now" action item is addressed or a 1.0.1 is planned with a killswitch. All three linked from the capstone README.
- Committed.

**Hint.** Run the blameless test on every action item: rewrite "I forgot X" as "the process had no enforced X"; if the rewrite points at a system fix, it belongs in the postmortem. The deterministic exercises (2 and 3) give you the correctness contracts; the live drills give you the timelines and the surprises.

**Estimated time.** 75 minutes.

---

## Problem 2 — The five-minute walkthrough

**Problem statement.** Record the phone-and-Wear side-by-side walkthrough (Lecture 2 §4): trace one dispatch write offline through Room and the outbox, reconnect and sync, show the conflict converge and the Wear complication update, the tile/complication/ongoing-activity surface, the Play Integrity gate and its fallback, and the three drills' findings. Narrate the *mechanism* at each hop.

**Acceptance criteria.**

- A ≤ 5-minute video, phone and Wear visible together, linked from the README.
- It traces one write end to end and narrates the mechanism (not just the feature) at each hop.
- The offline-first moment is shown live (network off → instant update from Room).
- A peer could reproduce the trace from the video.
- Linked and committed.

**Hint.** Pre-stage the data so you're not typing on camera, and keep a fallback recording of the sync step in case live sync stalls. "This updates instantly because the write went to Room, not the network" is mechanism; "here's the dispatch screen" is not.

**Estimated time.** 60 minutes.

---

## Problem 3 — The six interview drills with worked answers

**Problem statement.** Write `interview-prep/` with the six syllabus drills, each with a worked answer in your own words (Lecture 2 §5): Compose recomposition/stability, coroutines pitfalls (three real bugs), cold vs hot flows, WorkManager vs foreground service vs exact alarm, the WhatsApp-send system design, and reading an ANR/OOM stack trace.

**Acceptance criteria.**

- Six drill files (or one structured doc), each with a worked answer.
- The coroutines drill names three *real* bugs and the fix for each.
- The system-design drill handles offline, duplicates (idempotency), and ordering.
- The ANR/OOM drill reads a real (or realistic) stack trace and isolates the one blocking call / retained reference.
- Committed.

**Hint.** These are your prep for Problem 5's mock interviews. Write the answer you'd *say*, not an essay — the spine the interviewer listens for (Lecture 2 §5), in your voice, grounded in your capstone where you can ("my sync uses WorkManager because the work is deferrable and constraint-aware").

**Estimated time.** 75 minutes.

---

## Problem 4 — The production runbook

**Problem statement.** Write `production-runbook.md` — what an on-call rotation for a senior Android team actually looks like: Crashlytics triage (how you read a velocity alert and isolate the build), Play vitals (the ANR-rate and crash-rate budgets you watch), and staged-rollout halt criteria (the pre-committed thresholds from Lecture 1 §4).

**Acceptance criteria.**

- Crashlytics triage steps: from a velocity alert to a tagged build to a decision.
- The Android vitals budgets (ANR rate < 0.47%, crash-free sessions target) you'd hold.
- The rollout halt criteria, pre-committed (the same ones in your closed-track rollout).
- Committed.

**Hint.** The runbook is written for a future on-call engineer (possibly you at 3 AM). Make it actionable: "if the ANR rate crosses 0.47% in the rollout cohort, halt the rollout, identify the top ANR in Crashlytics, and ship a 1.0.1 or a killswitch." Not "monitor vitals."

**Estimated time.** 45 minutes.

---

## Problem 5 — Four mock interviews with retrospectives

**Problem statement.** Sit and record four mock senior-Android interviews (Lecture 2 §5): two technical (live Kotlin), one mobile system design, one behavioral. Write a `mock-interview-retro.md` for each: what you got right, what you fumbled, what you'd say differently.

**Acceptance criteria.**

- Four interviews completed and recorded (a peer, a mentor, or self-recorded against the drills).
- Four retrospectives, each honest about a fumble and a fix.
- The behavioral uses a real chaos-drill postmortem told as a story with a systemic fix.
- Committed.

**Hint.** The retrospective is where the learning is — "I blanked on `flatMapLatest` vs `flatMapConcat` and need to drill operator selection" is more useful than "it went well." The behavioral answer is your drill-B or drill-A postmortem; you already have the material.

**Estimated time.** (Interviews are scheduled separately; the retrospectives are ~30 minutes.)

---

## Problem 6 — The portfolio

**Problem statement.** Write `portfolio.md` with three projects suitable for a recruiter: the capstone (required) plus two original, public, GPL-3.0 (or permissive) projects, each with a one-page engineering narrative (the problem, the key decisions, the hardest bug, a production metric).

**Acceptance criteria.**

- Three projects, the capstone among them, each with a one-page narrative.
- Each narrative names a key decision and the hardest bug honestly.
- The two non-capstone projects are public with a license.
- Committed and linked from the README.

**Hint.** The narrative is not a feature list — it's "here's the interesting decision and here's what broke." The capstone's narrative writes itself from your ADRs and chaos-drill postmortems: the offline-first decision, the conflict policy, and the drill-B token-rotation bug you found.

**Estimated time.** 45 minutes.

---

## Rubric

Each problem is graded out of five; the week's homework is out of 30. These are the capstone-completion artifacts, so the bar is real.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every criterion; the artifact is real (a measured drill, a recorded video, a sat interview), the writing is honest and in your own words, and a grader could verify it. |
| 4 | Meets all criteria with a minor gap (a postmortem that states a fix but not the surprise; a walkthrough slightly over five minutes). |
| 3 | Works but misses one criterion (a drill run but the timeline not measured; an interview answer that misses the senior signal). |
| 2 | Partially done; a core idea is wrong (a postmortem that blames a person; a system-design answer that ignores offline/idempotency). |
| 1 | Not credibly done (a drill claimed but not evidenced). |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−3** for a credential committed to the repo; **−3** for data lost in a drill beyond the documented same-field LWW bound, or a message silently dropped; **−2** for a postmortem that assigns blame to a person rather than a system gap; **−2** for an attestation gate that fails open or bricks.

**Target: 26/30.** These are the deliverables that gate capstone completion (the three postmortems are all-required; the career pack is required). Below target, the things to revisit are the postmortems (Problem 1) and the interview drills (Problem 3) — the proof that you can operate the system and explain it, which is the entire competence C21 exists to certify.

---

*This is the last homework of C21 · Crunch Droid. Submit it, ship the capstone, survive the drills, and interview. There is no Week 25 — you earned the launch.*
