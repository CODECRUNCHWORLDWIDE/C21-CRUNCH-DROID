# Mini-Project — The Capstone Delivery: Ship, Survive, Present, Interview

> Take the locked release candidate from Week 23 and deliver it: submit to a Play Console **closed track** (or F-Droid) early in the week and land on the first try, run **all three** chaos drills against the live system, write a blameless **postmortem** for each, record the **five-minute phone-and-Wear walkthrough**, sit **four mock senior-Android interviews**, and assemble the **career engineering pack**. This is the culmination of the entire capstone — and the end of C21.

This is the capstone's final mile. It is **not new development** — the app is feature-frozen at the Week 23 RC. The work this week is *delivery*: through Google's pipeline, through three real failures, through a presentation you defend, and through the interviews that turn the work into a job. By the design of this track, twenty-three weeks of compounding work meets Google's gatekeepers, three deliberate failure scenarios, and a senior-Android interview panel, and you prove you can ship, operate, and explain — not just build.

The full capstone specification — the deliverables, the **three-drill chaos menu** (all required), and the career engineering pack — is in [`SYLLABUS.md` § Capstone](../../../SYLLABUS.md). Week 23's mini-project covered the build-and-lock half; this one covers the ship-and-survive-and-interview half. Together they deliver the capstone the whole track was building toward: an offline-first, multi-form-factor Android system, live on a distribution track, resilient under three documented failures, demonstrable in five minutes, and backed by an interview-ready portfolio.

**Estimated time:** ~15 hours of the week's schedule (Monday through Saturday mini-project blocks), on top of the exercises and the four interviews.

---

## What you deliver, in order

The week has a deliberate sequence. Each step unblocks the next; do them in order.

### 1. Submit to the closed track — Monday

Run the readiness audit (Exercise 1) until every row is PASS, then promote the locked `v1.0.0-rc1` AAB to a Play Console **closed track** (or submit to **F-Droid** for the no-fee path). Fill the Data Safety form from the code, justify the foreground-service type, confirm the account-deletion path. Submit **Monday** because the review queue is an external dependency you do not control: submit early and a rejection costs you days you have; submit late and it costs you the launch. The build link goes in the README; the per-cohort rollout uses pre-committed halt criteria (Lecture 1).

The readiness audit pre-empts the rejections that actually happen (Lecture 1 §1–2):

- **Data Safety matches the code** — every SDK mapped, no mismatch.
- **The foreground-service type is justified** (the sync promotion).
- **Only used permissions declared**; no leftover sensitive permission.
- **Account deletion** ships, with a URL.
- **targetSdk** meets the Play floor; **no crash** in the pre-launch report.

Each is a five-minute check and a multi-day rejection if missed. Walk in clean.

### 2. Run all three chaos drills — Tuesday/Wednesday

Run each drill against the live system, mid-week, while the cohort is live, so each postmortem reflects a real system. The execution discipline is the same five steps for each: establish steady state, inject exactly one failure, watch your observability, measure detection and recovery *separately*, and reverse the fault.

- **Drill A — offline-sync conflict.** Two emulators, both offline (`adb shell svc data disable`), both edit the same dispatch, reconnect within 60s. Prove convergence and the documented loss bound. (Exercise 2 is the deterministic proof; the live drill measures real gRPC propagation latency.) Capture the conflict-UI screenshots the syllabus requires.
- **Drill B — FCM token rotation.** Force a rotation (`FirebaseMessaging.deleteToken()`), send a dispatch push during the window, prove re-registration through the retryable path and no message dropped. (Exercise 3 is the deterministic proof.)
- **Drill C — Play Integrity attestation failure.** Run on a Play-Services-less AOSP emulator, attempt sign-in, prove graceful failure: a clear message and a documented fallback. (Week 23 Exercise 3 is the proof; this is the live run.)

### 3. Write the three postmortems — Thursday

For each drill, write a blameless `postmortem-<drill>.md` from the measured timeline: timeline, root cause (the system mechanism), blast radius, what you changed (tagged accept/fix-now/fix-later), and what you'd do differently with another week. Blameless tone — every action item survives the "replace the person with the component" test. Each postmortem names a *surprise*; address every "fix-now" item or ship a 1.0.1 with the killswitch holding the line. Link all three from the README.

### 4. Record the walkthrough — Friday

Record the five-minute phone-and-Wear side-by-side video (Lecture 2 §4): trace one write offline through Room and the outbox, reconnect and sync, show the conflict converge and the Wear complication update, the platform surface (tile, complication, ongoing activity), the Play Integrity gate and its fallback, and the three drills' findings. Narrate the *mechanism* at each hop. Pre-stage the data; keep a fallback recording of the sync step.

### 5. Sit the four interviews — Friday/Saturday

Sit and record four mock senior-Android interviews: two technical (live Kotlin), one mobile system design (the WhatsApp send pipeline), one behavioral (a chaos-drill postmortem told as a story with a systemic fix). The six interview drills (homework) are your prep. Write a retrospective for each.

### 6. Assemble the career pack — Saturday

Finalize `interview-prep/` (the six drills), `production-runbook.md` (Crashlytics triage, Play vitals, ANR budgets, rollout halt criteria), and `portfolio.md` (the capstone plus two original public projects, each with a one-page narrative). Link everything from the README.

---

## The drill execution discipline

Whichever drill you run, the execution is the same five steps (Lecture 2 §1):

1. **Establish steady state.** Start a health probe (a sync probe, a test push, a sign-in probe) *before* injecting anything, so you have a baseline and a clock.
2. **Inject one failure.** Change exactly one thing — do not rotate the token *and* deploy a new build at once, or you cannot attribute the recovery.
3. **Watch your observability.** Crashlytics, the structured backend logs, the per-cohort feedback, the probe. A drill you cannot observe teaches you nothing.
4. **Measure detection and recovery separately.** When you *knew* (detection) and when it was *healthy* (recovery) are different numbers; the gap is your observability quality.
5. **Reverse the failure.** Leave the system in steady state — a drill that leaves a dropped token or a stuck conflict is an outage, not a drill.

The output is a measured timeline (t0, t_fault, t_detect, t_recover, recovery_seconds, data verdict), which is the spine of the postmortem.

A common mistake is to skip step 1 (steady state) and inject the failure cold. Without a baseline you cannot say what "recovered" *means* — recovered to what? A two-minute health prober running before you inject (a sync probe, a test push, a sign-in probe) gives you both the baseline and the clock that makes detection and recovery measurable. Another common mistake is to inject *two* changes at once ("I'll rotate the token and also deploy the WorkManager fix"), which makes the recovery unattributable — you cannot tell whether recovery came from the retry or from the redeploy. One failure, one variable, one measured timeline.

---

## The deliverables, mapped to the rubric

Each deliverable maps onto the capstone's rubric in `SYLLABUS.md`. This week earns the ship-and-survive lines, the career-pack 10%, and validates the build-quality lines from Week 23.

```text
capstone-repo/
├── README.md                       # overview, architecture diagram, track link,
│                                   #   Baseline Profile numbers, drill postmortem links,
│                                   #   walkthrough link, known limitations
├── docs/
│   ├── architecture.md             # the seven-module graph + trace-one-write (Week 23)
│   ├── adr/0001-0004.md            # the four ADRs (Week 23)
│   ├── pre-submission-audit.md     # the build audit, all-PASS (Week 23)
│   └── play-readiness-audit.md     # the Play audit, all-PASS (this week, Exercise 1)
├── postmortem-offline-conflict.md  # drill A (this week)
├── postmortem-fcm-rotation.md      # drill B (this week)
├── postmortem-attestation.md       # drill C (this week)
├── production-runbook.md           # the on-call runbook (this week)
├── interview-prep/                 # the six drills (this week)
├── portfolio.md                    # three projects (this week)
└── (the seven modules + the backend, from Week 23)
```

---

## Picking the order of the drills

You run all three, but the order matters for your week. Run them in *increasing* order of how much a fix might cost you:

1. **Drill C first (attestation failure).** It is the cheapest to run (one AOSP emulator, no second device) and the most likely to surface a hard crash you want to know about early. If the `PlayServicesUnavailable` branch crashes, you want Monday/Tuesday to fix it, not Thursday.
2. **Drill B second (FCM rotation).** It needs a real push and a throttled network but one device. If the naive `onNewToken` drops the token, the fix (route through WorkManager) is a contained change you can ship as part of the drill.
3. **Drill A last (offline conflict).** It needs two devices and the most setup, and its "fix" is usually not code but a *documentation* of the convergence policy and the propagation-latency budget. Run it once your tooling is warm from B.

This order also front-loads the drills most likely to find a *code* bug (C and B) before the one most likely to find a *measurement* surprise (A), so any code fix lands while you still have submission-week slack.

## Rules

- **Feature-frozen.** The app is locked at the Week 23 RC. New code is limited to: pre-empting a Play rejection, the chaos-drill drivers and any fix a drill surfaces, and the killswitch toggles. No new features the day before submission — that is the feature that crashes on review.
- **Submit early.** Monday, not Friday. The queue is an external dependency.
- **All three drills, done thoroughly.** The syllabus requires all three; one done well plus two hand-waved is a fail. Establish a baseline, inject one failure, measure, document, reverse.
- **Blameless postmortems.** System gaps, not human blame. Every action item survives the "replace the person with the component" test.
- **No credentials in the repo.** The token is in Keystore; the signing keys and any API keys are in CI secrets.
- **No data lost beyond the documented bound; no message dropped.** Drill A's only loss is the same-field LWW bound; drill B drops nothing.

---

## Acceptance criteria

### Shipped (rubric: on a distribution track)

- [ ] Live on a Play closed track (or F-Droid); links in the README.
- [ ] Passed review on the first attempt, or a documented resubmission with the specific fix.
- [ ] The Play readiness audit (Exercise 1) is committed and was all-PASS before submission.
- [ ] In-app account deletion works; the foreground-service type is justified; Data Safety matches the code.

### Survived (rubric: chaos-drill postmortems, all three required)

- [ ] All three chaos drills executed against the live system with measured timelines.
- [ ] Three blameless postmortems, each naming a real surprise, with timeline, root cause, blast radius, action items.
- [ ] Drill A: convergence + loss bound. Drill B: re-registration retried, no message dropped. Drill C: graceful fallback, no brick, no fail-open.
- [ ] Every "fix-now" action item addressed (or a 1.0.1 planned with the killswitch).

### Demonstrated (rubric: walkthrough)

- [ ] A ≤ 5-minute phone-and-Wear walkthrough tracing one write end to end, mechanism narrated at each hop.
- [ ] The offline-first moment shown live (network off → instant update from Room).

### Interview-ready (rubric: career pack, 10% of the course)

- [ ] Four mock interviews completed and recorded, each with a written retrospective.
- [ ] The six interview drills with worked answers, the production runbook, and the three-project portfolio committed.

### Validated from Week 23 (the build-quality lines)

- [ ] Multi-form-factor parity (phone + Wear) — demonstrated in the walkthrough.
- [ ] Offline-first sync and deterministic conflict resolution — proven in drill A.
- [ ] Play Integrity gate with graceful fallback — proven in drill C.
- [ ] Baseline Profile ≥ 20% cold-start improvement; the full test suite green on CI.

---

## The non-negotiables

Three things fail the capstone regardless of how good everything else is, because in a real launch they are incidents:

- **A credential in the repo.** The token is in Keystore; the signing keys and API keys are in CI secrets. `grep -ri "STORE_PASSWORD\|KEY_PASSWORD\|BEGIN PRIVATE KEY" .` must return nothing but property names. A leaked signing key is a security incident.
- **Data lost in a drill beyond the documented bound.** Drill A's only acceptable loss is the same-field last-writer-wins bound, documented honestly in the postmortem. A drill that silently eats a different-field edit is the exact failure the drill exists to catch.
- **A message silently dropped in drill B, or an attestation gate that fails open or bricks in drill C.** These are the two security/reliability contracts the drills certify; getting either wrong fails the capstone.

Everything else — a slightly late submission, a resubmission, a 1.0.1 — is recoverable. These three are not.

## What "done" looks like

A grader opens your repo, reads the README, and follows the track link to a build live on a closed track (or F-Droid). They read your committed readiness audits (build and Play) and see both were all-PASS before submission, and your review history shows a first-attempt pass. They read your three postmortems and find measured timelines, real surprises, honest data verdicts, and owned action items. They watch your five-minute walkthrough and see one write trace through Room, the outbox, sync, a converged conflict, and the Wear complication — narrated by mechanism. They read your four interview retrospectives and your six drill answers and hear an engineer who can explain the system, not just demo it. They read three case studies that say what was hard, what you decided, and what the number was. Every one passes. That is the capstone delivered. That is C21.

---

## What this completes

- **The Phase IV gate:** the capstone accepted on the closed track, the three chaos-drill postmortems signed off, the portfolio and runbook published, and the four mock interviews complete.
- **The course.** There is no Week 25. After you ship, survive, present, and interview, you have completed C21 · Crunch Droid. You have written Kotlin 2.x with structured concurrency, built Compose at the recomposition level, wired Hilt over Room and gRPC, shipped an offline-first WorkManager sync engine, built a Wear OS companion against a KMP core, generated a Baseline Profile that cut cold start, gated sign-in with Play Integrity, rotated an FCM token under a chaos drill, and landed an app through Play review on the first try.
- **The portfolio you walk into interviews with.** A deployed multi-form-factor app, three chaos-drill postmortems, a runbook, and an interview-prep pack — the closest thing the job market has to a senior-Android certification.

Ship it, survive the drills, present it, and interview. You earned the launch.

---

## The README's known-limitations section

A grader reads the README first, and the most senior thing in it is the honest known-limitations list. Name every cut and every bound:

- The conflict policy is same-field last-writer-wins; a same-field conflict loses one edit (documented in postmortem A), by design.
- (If you cut the gRPC backend) the sync runs against an in-memory fake with the identical `NetworkResult` contract; real propagation latency is not exercised.
- (If you cut a Wear surface) the ongoing activity is not shipped; the tile and complication are.
- The capstone targets a closed track (or F-Droid), not production; the staged rollout is demonstrated, not run at scale.

An honest limitations list reads as confidence, not weakness. A grader who finds a hidden gap you did not name trusts the rest of your claims less; one who reads a clear-eyed list of what you cut and why trusts the rest more. This is the same discipline as the blameless postmortem: name the gap, own it, and the system (and your credibility) is stronger for it.

---

## Submission

When the capstone delivery is done:

1. Confirm the app is live on a Play closed track (or F-Droid), with the link in the README.
2. Confirm review passed — or the resubmission landed with a documented fix.
3. Confirm the three postmortems are committed with measured timelines, named surprises, and owned action items.
4. Confirm the five-minute walkthrough is recorded and linked.
5. Confirm the four interviews are done with retrospectives, and the career pack (six drills, runbook, portfolio) is committed.
6. Confirm there are no credentials in the repo, no data lost beyond the documented bound, and no message dropped.
7. Post the repo URL and the track link in your cohort tracker.

That is the Phase IV gate cleared and **C21 · Crunch Droid complete.** You have shipped a real multi-form-factor Android app through Google's pipeline, proven it survives three real failures, and assembled the portfolio that demonstrates you can build, ship, and operate. There is no Week 25 — the recommended next tracks (C5 AI/DS for on-device ML, C22 Mesh for the typed-RPC backend, C23 Agents) are in the [track README](../../../README.md). But first: present this one. You earned it.
