# Week 23 Homework

Six build-week deliverables that move the capstone from integrated modules to a locked release candidate. The full set should take about **5 hours** in total, on top of the mini-project. Work in your capstone Git repository so each produces commits you point to in next week's submission.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Android Studio Ladybug+, Kotlin 2.0+, the Compose Compiler plugin, compileSdk 35, minSdk 26. Every problem must build with **0 warnings**.

---

## Problem 1 — The architecture diagram and trace-one-write doc

**Problem statement.** Commit `docs/architecture.md` with the Mermaid seven-module dependency graph (Lecture 1 §2) AND a `docs/trace-one-write.md` that narrates the eight-hop write path (Lecture 1 §4) for the Field-Force Companion, naming the owner of each hop. The trace must match your actual code — if your repository is named differently, use the real name.

**Acceptance criteria.**

- `docs/architecture.md` renders the module graph on GitHub (Mermaid) with the legal edges only.
- `docs/trace-one-write.md` walks UI → ViewModel → repository → Room → WorkManager → gRPC → converge → Wear, naming the owner at each hop.
- The trace references real type/function names from your code, not placeholders.
- Committed.

**Hint.** Start from the diagrams and trace in Lecture 1. Then open your code and replace the generic names with your real ones; if a hop doesn't map cleanly, that mismatch is a finding — fix the code or the trace.

**Estimated time.** 45 minutes.

---

## Problem 2 — The four ADRs

**Problem statement.** Write the four ADRs the capstone requires in `docs/adr/0001-…0004-…md` (Lecture 2 §5): offline-first source of truth, the `:shared-core` KMP boundary, the conflict-resolution policy, and the Play Integrity attestation + fallback design. Each must have the decision, the alternatives considered, the trade-off, and the consequence you accept.

**Acceptance criteria.**

- Four ADR files, each with the four parts.
- ADR-0003 commits to a specific conflict policy (e.g. last-writer-wins by server timestamp) and says how next week's drill A will demonstrate it.
- ADR-0004 says explicitly how the gate avoids both fail-open and brick.
- Committed.

**Hint.** An ADR is short — half a page. The hard part is the *trade-off* line: name what you give up, not just what you gain. "We accept eventual consistency in exchange for a UI that never blocks on the network" is a real trade-off; "this is the best design" is not.

**Estimated time.** 50 minutes.

---

## Problem 3 — R8 keep rules from the missing-rules report

**Problem statement.** Build the release variant and run the Espresso smoke test in release. If it crashes (it usually does on the first release build of a gRPC + serialization app), read `missing_rules.txt`, add the **narrowest** keep rules that fix it, and record in `notes/r8.md` which class R8 stripped, which rule fixed it, and the method-count delta from the APK analyzer.

**Acceptance criteria.**

- The release-variant Espresso smoke test passes.
- `notes/r8.md` names the stripped class, the keep rule, and the debug-vs-release method-count delta.
- No `-keep class ** { *; }` and R8 is not disabled.
- 0 warnings. Committed.

**Hint.** `./gradlew :app:connectedReleaseAndroidTest`. The report is under `app/build/outputs/mapping/release/`. Add `-keep` rules scoped to the named package (`...proto.**`, the `@Serializable` companions), not the whole app.

**Estimated time.** 50 minutes.

---

## Problem 4 — Verify the Baseline Profile delta

**Problem statement.** Generate the Baseline Profile from the cold-start journey, package it, and measure cold start with `CompilationMode.None()` and `CompilationMode.Partial()` via macrobenchmark. Record both numbers and the percentage improvement in `notes/baseline-profile.md`. The bar is ≥ 20%.

**Acceptance criteria.**

- A generated, packaged Baseline Profile covering the dispatch-list cold-start path.
- `notes/baseline-profile.md` records both `timeToInitialDisplay` medians and the percentage (≥ 20%).
- The generator journey exercises the real cold-start path, not a splash screen.
- 0 warnings. Committed.

**Hint.** If the delta is under 20%, your generator journey is wrong — it must drive the actual first screen users see (the dispatch list), including a scroll and one interaction, so the profile covers the methods that actually run on cold start.

**Estimated time.** 45 minutes.

---

## Problem 5 — The full suite green on CI

**Problem statement.** Confirm (or wire, from Week 21) the GitHub Actions workflow that runs the whole test suite — unit (Turbine + MockK), Robolectric (DAO + the two migrations), Compose UI (the dispatch screen), Paparazzi (Material 3 states), and one Espresso smoke — on the `v1.0.0-rc1` tag, and that it is **green on CI**, not just locally.

**Acceptance criteria.**

- A CI run on the tag shows all five test types green.
- The Room schema export is checked into source control and the migration tests reference it.
- `notes/ci.md` links the green CI run.
- 0 warnings. Committed.

**Hint.** A test green locally but red on CI is a hidden local dependency (a file path, an env var, an emulator image). Run the CI workflow on a branch first; fix the difference before you tag.

**Estimated time.** 45 minutes.

---

## Problem 6 — The pre-submission readiness audit

**Problem statement.** Fill in `docs/pre-submission-audit.md` row by row from the build-week gate (Lecture 1 §6): build (signed AAB, R8, signed Wear APK), performance (Baseline Profile verified), correctness (suite green on CI, migrations tested), integration (trace-one-write, Wear reads shared core, attestation graceful), delivery (uploaded to internal track, tagged), documentation (diagram, ADRs). Every row must read PASS, or the FAIL is your remaining work.

**Acceptance criteria.**

- Every checklist row from Lecture 1 §6 appears with a PASS/FAIL and a one-line evidence note (a link, a command output, a file).
- Any FAIL has a written plan to clear it before next week's submission.
- `grep -ri "STORE_PASSWORD\|KEY_PASSWORD\|BEGIN PRIVATE KEY" .` returns nothing but property names.
- Committed.

**Hint.** The audit is not a formality — it is the build-week's deliverable. A row you can't honestly mark PASS is the thing that would have failed next week's submission; finding it now, with a week to fix it, is the whole reason you build the RC early.

**Estimated time.** 45 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, the artifact is real (a signed AAB, a green CI run, a measured delta), and the written explanation is correct and in your own words. |
| 4 | Meets all criteria but with a minor gap (an ADR trade-off line that states a gain instead of a trade-off; an audit row marked PASS on thin evidence). |
| 3 | Works, but misses one criterion (the Baseline Profile delta is under 20%; the trace references placeholder names; CI green locally but not on CI). |
| 2 | Compiles and partially works; a core idea is wrong (R8 disabled to "fix" the crash; the attestation gate fails open; the diagram has an illegal edge). |
| 1 | Does not build, or the approach fundamentally misunderstands the capstone integration. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−3** for a credential committed to the repo (a security incident, not a style nit); **−2** for disabling R8 or `-keep class ** { *; }` instead of a scoped rule; **−2** for an attestation design that fails open or bricks; **−1** for an illegal sideways dependency in the diagram or build.

**Target: 24/30.** Below that, the two areas to revisit are the same two the quiz and the chaos drills grade on — the offline-first write path (Problems 1, 2) and the release mechanics (Problems 3, 4, 5, 6). A locked, audited RC this week is what makes next week a launch instead of a sprint.
