# Week 24 — Capstone polish, chaos drill, Play submission, interview prep

Welcome to the final week of **C21 · Crunch Droid**. You do not learn a new framework. You ship — to real users, through Google's pipeline — and then you prove the system survives the kind of failure that takes Android apps down in production, and finally you sit the interviews that turn twenty-four weeks of work into a job.

Last week you locked a release candidate: a signed, R8-optimized, Baseline-Profile-verified AAB on a Play Console internal track, a signed Wear OS APK, the full test suite green on CI, four ADRs, an architecture diagram, and a pre-submission audit reading all-PASS. This week the build leaves your hands. You **submit to a Play Console closed track** and land it cleanly, run **all three chaos drills** against the system, write a **blameless postmortem** for each, record the **five-minute side-by-side walkthrough** (phone screen + Wear screen), and sit **four mock senior-Android interviews** — two technical (live Kotlin), one mobile system design, one behavioral. By Sunday you have shipped a multi-form-factor production system, survived three documented failures, and rehearsed the interview that gets you hired.

The week has a deliberate rhythm, and it is the opposite of the all-nighter this course is named to avoid. **Submit early** — Play review and AAB processing take hours to days, and a staged rollout to a closed track is an external dependency you do not control; you do not want a chaos drill or the interviews blocked on a queue. **Run the chaos drills mid-week**, while the closed-track cohort is live, so each postmortem reflects a real system under real conditions. **Record the walkthrough and sit the interviews last**, when the app is reviewed and the drills are documented. The crunch this curriculum is named to avoid is the team that submits on Friday and prays; you submit Monday, so that if Play review flags something you have the week to land the resubmission instead of the weekend.

This is the capstone's final mile. Everything you built across twenty-three weeks now meets Google's gatekeepers, three real failure scenarios, and a senior-Android interview panel. Treat it like a launch, because it is one — and treat the interviews like the thing the whole track was for, because they are.

## Learning objectives

By the end of this week, you will be able to:

- **Submit** an Android App Bundle to a Play Console closed track and pass review on the first attempt — by knowing what Play review actually enforces (the policies with teeth: data safety, foreground-service justification, target-API level, permissions), what it never checks, and how to pre-empt the common rejections.
- **Execute** the three capstone chaos drills — offline-sync conflict, FCM token rotation, and Play Integrity attestation failure — driving each failure on purpose, measuring detection and recovery separately, and proving no data was lost and no message silently dropped.
- **Write** a blameless postmortem an incident review would accept: the timeline, the root cause, the blast radius, what you changed, and what you would do differently with another week — system gaps, not human blame.
- **Record** a five-minute walkthrough a hiring manager can watch and a peer can reproduce — phone and Wear side by side — tracing one dispatch write end to end through the offline-first sync, the conflict resolution, the Wear complication, and the Play Integrity gate.
- **Answer** the senior-Android interview question set live: Compose recomposition and stability, coroutines pitfalls, cold vs hot flows, WorkManager vs foreground service vs exact alarm, mobile system design (the WhatsApp send pipeline), and reading an ANR/memory stack trace out loud.
- **Assemble** the career engineering pack: the six interview drills with worked answers, the production runbook, and the portfolio of three projects (the capstone plus two original public ones) — the closest thing the job market has to a senior-Android certification.

## Prerequisites

This week assumes you completed Week 23 with the build-week deliverables in hand. Specifically, you need:

- **A locked release candidate** — a signed, R8-optimized AAB tagged `v1.0.0-rc1`, on the Play Console internal track, with the full test suite (unit, Robolectric, Compose UI, Paparazzi, Espresso) green on CI behind it.
- **A verified Baseline Profile** demonstrating ≥ 20% cold-start improvement, packaged in the release variant.
- **The four ADRs and the architecture diagram** committed to `docs/`, plus the trace-one-write doc and the all-PASS pre-submission audit.
- **The offline-first write path, the Wear companion, and the Play Integrity gate working** — because the three chaos drills exercise exactly these three contracts. Drill A needs the offline-first sync and the conflict policy; drill B needs the FCM pipeline; drill C needs the no-brick attestation design.
- **A Play Console account** with the app record (the USD 25 one-time fee). The documented no-fee fallback is an **F-Droid** submission, which the syllabus accepts for students who choose not to pay — the chaos drills and interviews are identical either way.
- **Two physical-device or emulator targets** for the side-by-side walkthrough: a Pixel 8 (API 35) and a Wear OS emulator (API 34), plus a Play-Services-less emulator for drill C.

If the RC is not locked from last week, this week becomes a sprint — and a sprint the same week you run three chaos drills and sit four interviews is exactly the crunch the build week front-loaded its work to avoid. Lock it last week; ship it this week.

## Topics covered

- **Play Console submission.** The closed-track flow, the Data Safety form (matching the label to the code), the target-API-level requirement, the foreground-service-type justification (your sync's foreground promotion needs one), the permissions declaration, and the staged rollout with halt criteria.
- **What Play review really checks.** The actually-enforced policies — data safety accuracy, foreground service justification, deceptive behavior, the permissions you declared vs the ones you use, crashes on review — and how to land on the first try. What it never checks.
- **Play App Signing and the upload key** — the final confirmation that Google holds the app key and you hold the upload key, and what that means for key rotation and recovery.
- **The three chaos drills, end to end.** Drill A (offline-sync conflict): two devices edit the same dispatch offline, reconnect, converge — prove the ADR-0003 policy and zero unexpected loss. Drill B (FCM token rotation): force a token rotation mid-session, prove the path from rotated token to server re-registration with no message silently dropped. Drill C (Play Integrity attestation failure): run on a Play-Services-less emulator, prove graceful sign-in failure with a clear message and a documented fallback.
- **The blameless postmortem.** The structure an incident review accepts: timeline, root cause, blast radius, what we changed, what we would do differently — and why the blameless tone (replace the person with the system component) is load-bearing.
- **The five-minute walkthrough.** The phone-plus-Wear side-by-side trace-one-write narration, pre-staged data, a fallback recording, narrating the mechanism at each hop.
- **Senior-Android interview prep.** The six syllabus drills: Compose recomposition/stability, coroutines pitfalls, cold vs hot flows, WorkManager/foreground/exact-alarm design, the WhatsApp-send mobile system design, and reading an ANR/OOM stack trace out loud. Two live-Kotlin technicals, one system design, one behavioral.
- **The career engineering pack.** The interview-prep drills, the production runbook (Crashlytics triage, Play vitals, ANR budgets, rollout halt criteria), and the three-project portfolio.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract; the final week deserves whatever it takes to ship clean and interview well.

| Day       | Focus                                                              | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|--------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | Play review reality; Data Safety + foreground-service; SUBMIT early |    2h    |    1h     |     0h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Tuesday   | Drill A: offline-sync conflict; convergence; the postmortem        |    1h    |    2h     |     0h     |    0.5h   |   1h     |     2h       |    0h      |     6.5h    |
| Wednesday | Drill B: FCM token rotation; drill C: attestation failure          |    1h    |    2h     |     1h     |    0.5h   |   1h     |     1.5h     |    0.5h    |     7.5h    |
| Thursday  | Write the three postmortems; respond to any Play review feedback   |    0h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     6h      |
| Friday    | Record the 5-minute side-by-side walkthrough; interview drills 1–3 |    0h    |    0h     |     0h     |    0.5h   |   1h     |     2.5h     |    0.5h    |     4.5h    |
| Saturday  | Portfolio + runbook; four mock senior-Android interviews           |    0h    |    0h     |     0h     |    0h     |   0h     |     2.5h     |    0.5h    |     3h      |
| Sunday    | Quiz, course retrospective, final push                            |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                    | **4h**   | **6h**    | **2h**     | **3.5h**  | **5h**   | **15h**      | **2.5h**   | **36h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./README.md) | This overview (you are here) |
| [resources.md](./resources.md) | The Play policy and submission docs, the chaos-engineering and postmortem references, the FCM and Play Integrity docs, the senior-Android interview references, and the career-pack guides |
| [lecture-notes/01-play-submission-and-shipping-to-the-closed-track.md](./lecture-notes/01-play-submission-and-shipping-to-the-closed-track.md) | What Play review really checks, the Data Safety form and foreground-service justification, the common rejections and how to pre-empt them, and the closed-track staged rollout |
| [lecture-notes/02-the-chaos-drills-the-postmortem-and-the-interview.md](./lecture-notes/02-the-chaos-drills-the-postmortem-and-the-interview.md) | The three chaos drills end to end, the blameless postmortem structure, the five-minute walkthrough, and the senior-Android interview drills |
| [exercises/README.md](./exercises/README.md) | Index of the three exercises |
| [exercises/exercise-01-play-review-readiness-audit.md](./exercises/exercise-01-play-review-readiness-audit.md) | Audit the capstone against the actually-enforced Play policies before you submit |
| [exercises/exercise-02-offline-conflict-chaos-drill.kt](./exercises/exercise-02-offline-conflict-chaos-drill.kt) | Drive and verify drill A — the offline-sync conflict — asserting convergence and the documented loss bound |
| [exercises/exercise-03-fcm-token-rotation-drill.kt](./exercises/exercise-03-fcm-token-rotation-drill.kt) | Drive and verify drill B — FCM token rotation — proving re-registration and no message silently dropped |
| [challenges/README.md](./challenges/README.md) | Index of the final challenge |
| [challenges/challenge-01-ship-survive-and-interview.md](./challenges/challenge-01-ship-survive-and-interview.md) | Ship to the closed track, survive all three chaos drills, record the walkthrough, and sit the four interviews |
| [quiz.md](./quiz.md) | 13 questions, answer key at the bottom |
| [homework.md](./homework.md) | The final week's deliverables with a rubric |
| [mini-project/README.md](./mini-project/README.md) | The capstone delivery brief — ship, survive, present, interview |

## The "shipped, survived, and hireable" promise

C21 has carried one recurring marker through every phase. The final week's marker is the one that means you are done:

> **The capstone is live on a Play Console closed track (or F-Droid), it cleared review on the first attempt, it survived all three documented chaos drills with no data lost and no message silently dropped, you can demonstrate it phone-and-Wear in five minutes, and you have sat four mock senior-Android interviews with a written retrospective.** Not "it's on my phone." Not "it builds." Reviewed, resilient, demonstrable, and interview-ready — the four things a senior Android engineer's launch actually delivers.

You will *prove* this by submitting early and pre-empting the rejections (Exercise 1), driving three real failures and recovering from each (Exercises 2–3 and the chaos drills), recording the side-by-side walkthrough, and answering the senior-Android question set under pressure. "I clicked submit" is not the bar. "It passed review, it survived three drills, here's the walkthrough, and here are my four interview retrospectives" is the bar.

## A note on what's not here

This is the final week, and it deliberately does **not** add new app features. The app is feature-frozen at the Week 23 release candidate. New code this week is limited to: pre-empting a Play review rejection, the chaos-drill drivers and any fix a drill surfaces, and the killswitch/feature-flag toggles. The discipline of a launch week is *stop building features and start shipping* — a feature added the day before submission is the feature that crashes on review. If a chaos drill surfaces a real bug, you fix that bug; you do not add scope.

## Up next

There is no Week 25. After you ship the capstone, survive all three chaos drills, publish the portfolio, and clear the four senior-Android mock interviews, you have completed **C21 · Crunch Droid**. You have written Kotlin 2.x with structured concurrency and Flow, built Compose UIs at the recomposition level, wired a Hilt graph over Room and gRPC, shipped an offline-first WorkManager sync engine, built a Wear OS companion against a KMP shared core, generated a Baseline Profile that meaningfully cut cold start, gated sign-in with Play Integrity, rotated an FCM token under a chaos drill, and landed an app through Play review on the first try. The recommended next tracks — **C5 (AI / DS)** for on-device ML, **C22 (Mesh)** for the typed-RPC backend that talks to your client, or **C23 (Agents)** for agent orchestration on Android — are in the [track README](../../README.md) and the Crunch Labs Charter. But first: ship this one, survive the drills, present it, and interview. You earned the launch.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
