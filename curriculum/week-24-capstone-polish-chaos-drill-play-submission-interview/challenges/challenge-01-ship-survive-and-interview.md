# Challenge 1 — Ship, survive, and interview

**Time.** Spread across the whole week — submission Monday, drills mid-week, walkthrough and interviews late.
**Deliverable.** A capstone live on a Play closed track (or F-Droid), three blameless postmortems, a five-minute side-by-side walkthrough, and four mock-interview retrospectives — all linked from the capstone README.

## The premise

Every Android team has shipped the app that worked in the demo and then went dark in production — a sync that lost an edit, a push pipeline that stopped delivering after a token rotation, a sign-in that bricked on a device without Play Services. The difference between an engineer who *built* an app and one who *operated* one is that the second has caused those failures on purpose, watched them, and proven recovery. This challenge is that proof, plus the interview that turns it into a job. It is the capstone's final mile and the last thing you do in C21.

The grading is not "did you submit" — it is the four things a senior launch delivers: **reviewed** (passed Play review on the first try), **resilient** (survived all three chaos drills with no unexpected loss and no message dropped), **demonstrable** (a five-minute walkthrough a peer can reproduce), and **interview-ready** (four mock interviews with retrospectives).

## What to do

### Step 1 — Submit Monday (reviewed)

Run the readiness audit (Exercise 1) until every row is PASS, then promote the locked `v1.0.0-rc1` AAB to a Play Console **closed track** (or submit to **F-Droid** for the no-fee path). Fill the Data Safety form from the code, justify the foreground-service type, confirm the account-deletion path, and roll out to a closed cohort with pre-committed halt criteria (Lecture 1). Submit **Monday** — the queue is an external dependency; submitting early means a rejection costs you days you have, not your weekend. Watch the pre-launch report and fix any crash it flags.

### Step 2 — Run all three chaos drills mid-week (resilient)

Run each drill against the live (closed-track or internal) system, mid-week, while the cohort is live. For each: establish steady state, inject exactly one failure, watch your observability, measure detection and recovery *separately*, verify the data verdict, and reverse the fault.

- **Drill A — offline-sync conflict.** Two emulators, both offline, both edit the same dispatch (`adb shell svc data disable`), reconnect within 60s. Prove convergence and the documented loss bound (Exercise 2 is the deterministic proof; the live drill measures real gRPC propagation latency). Screenshots of the conflict UI.
- **Drill B — FCM token rotation.** Force a rotation (`FirebaseMessaging.deleteToken()`), send a dispatch push during the window, prove re-registration through the retryable path and no message dropped (Exercise 3 is the deterministic proof).
- **Drill C — Play Integrity attestation failure.** Run on a Play-Services-less (AOSP) emulator, attempt sign-in, prove graceful failure: a clear message and a documented fallback, never a brick, never a fail-open (Week 23 Exercise 3 is the proof; this is the live run).

### Step 3 — Write the three blameless postmortems (resilient)

For each drill, write a `postmortem-<drill>.md` with the five sections (Lecture 2 §3): timeline (real timestamps), root cause (the system mechanism), blast radius, what we changed (tagged accept/fix-now/fix-later), and what we'd do differently with another week. Blameless tone — every action item must survive the "replace the person with the component" test. Each postmortem names a *surprise*; recovery succeeding is the least interesting part. Address every "fix-now" item, or ship a 1.0.1 with a killswitch holding the line.

### Step 4 — Record the five-minute walkthrough (demonstrable)

Record the phone-and-Wear side-by-side video (Lecture 2 §4): trace one dispatch write offline, show it update instantly (Room is the truth), reconnect and sync, show the Wear complication update and a converged conflict, the tile/complication/ongoing-activity surface, the Play Integrity gate and its fallback, and the three drills' findings. Narrate the *mechanism* at each hop. Pre-stage the data; keep a fallback recording of the sync step.

### Step 5 — Sit four mock interviews (interview-ready)

Sit and record four mock senior-Android interviews: two technical (live Kotlin — Compose recomposition, coroutines pitfalls, flows, a small live-coded problem), one mobile system design (the WhatsApp send pipeline), one behavioral (your chaos-drill postmortem told as a story with a systemic fix). Write a retrospective for each: what you got right, what you fumbled, what you'd say differently. The six drill answers (homework) are your prep.

### Step 6 — Assemble the career engineering pack

Finalize `interview-prep/` (the six drills with worked answers), `production-runbook.md` (Crashlytics triage, Play vitals, ANR budgets, rollout halt criteria), and `portfolio.md` (three projects: the capstone plus two original public GPL-3.0 projects, each with a one-page engineering narrative). Link everything from the capstone README.

## Acceptance criteria

- [ ] Live on a Play closed track (or F-Droid); passed review on the first attempt (or a documented resubmission with the specific fix).
- [ ] The readiness audit (Exercise 1) is committed and was all-PASS before submission.
- [ ] All three chaos drills executed against the live system with measured timelines.
- [ ] Three blameless postmortems — each naming a real surprise, with the timeline, root cause, blast radius, what changed, and what you'd do differently; every "fix-now" addressed.
- [ ] Drill A: convergence proven, loss bound documented. Drill B: re-registration retried, no message dropped. Drill C: graceful fallback, no brick, no fail-open.
- [ ] A five-minute phone-and-Wear walkthrough, narrating the mechanism at each hop.
- [ ] Four mock interviews completed and recorded, each with a written retrospective.
- [ ] The career engineering pack assembled: six interview drills, the runbook, the three-project portfolio.
- [ ] No credentials in the repo; no data lost in a drill beyond the documented same-field LWW bound; no message silently dropped.

## What "great" looks like

A weak submission says "I submitted the app and it didn't crash." A great submission says:

> The capstone passed Play review on the first attempt — the readiness audit caught an unused `ACCESS_FINE_LOCATION` and a Data Safety mismatch (Crashlytics undeclared) before submission. Drill A: in 50 same-field conflict trials the later server timestamp won 100% deterministically; zero different-field edits were lost; the surprise was that convergence lagged reconnect by a median 3.1s due to gRPC propagation, not the microsecond merge — so the SLO now budgets for propagation. Drill B: the naive `onNewToken` made a single non-retryable call, and under a throttled network the token never reached the backend — every push went dark; routing re-registration through a WorkManager job with exponential backoff fixed it, and a push sent during the window was queued and delivered after re-registration. Drill C: on an AOSP emulator, sign-in returned `PlayServicesUnavailable` and offered the web fallback — no brick, no fail-open. Three blameless postmortems, each with a systemic action item. The five-minute walkthrough traces one write offline through Room, the outbox, sync, the conflict, and the Wear complication. Four mock interviews recorded; the behavioral was the drill-B postmortem told as "we went dark and here's the systemic fix."

Reviewed, resilient, demonstrable, interview-ready — and honest about every surprise.

## Where this completes

This is the Phase IV gate and the end of C21: the capstone accepted on the closed track, the three chaos-drill postmortems signed off, the portfolio and runbook published, and the four mock interviews complete. There is no Week 25. You have shipped a multi-form-factor offline-first Android system through Play review, proven it survives three real failures, and assembled the portfolio that demonstrates you can build, ship, and operate. Present it, and interview. You earned the launch.
