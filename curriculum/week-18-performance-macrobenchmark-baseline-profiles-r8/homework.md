# Week 18 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 18 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets Android Studio Ladybug+, Kotlin 2.0+, the Macrobenchmark library, the Baseline Profile plugin, and R8. Problems 2, 4, and 6 need a **real physical device** (emulator startup numbers are meaningless). Every problem must build with **0 warnings**.

---

## Problem 1 — Cold/warm/hot in your own words

**Problem statement.** Write, in `notes/startup-types.md`, a one-paragraph explanation of cold, warm, and hot start: what the system does in each, what the user sees, and why cold start is the metric you optimize. Then explain TTID vs. TTFD and give a concrete example of a screen with a fast TTID and a slow TTFD.

**Acceptance criteria.**

- All three startup types explained in terms of what the *system* rebuilds, not just "slow/fast."
- TTID and TTFD distinguished, with a concrete fast-TTID/slow-TTFD example (e.g. a feed that shows a skeleton instantly but takes a second to load articles).
- Committed.

**Hint.** Cold = process from scratch; warm = process alive, Activity recreated; hot = both in memory. TTID = first frame; TTFD = `reportFullyDrawn()`. The gap between them is what the user feels as "loading."

**Estimated time.** 30 minutes.

---

## Problem 2 — A startup benchmark and a noise floor

**Problem statement.** Stand up a `:benchmark` module and write a cold-start `StartupTimingMetric` benchmark (`CompilationMode.None()`, ≥15 iterations). Run it on a real device **twice** and record both distributions. Compute the run-to-run noise floor.

**Acceptance criteria.**

- The benchmark uses `StartupMode.COLD`, `StartupTimingMetric`, ≥15 iterations.
- Two runs of the *same* config recorded in `notes/noise-floor.md`, with P50/P90 each.
- The noise floor (difference between the two P50s) computed and stated.
- 0 warnings. Committed.

**Hint.** `measureRepeated(... compilationMode = CompilationMode.None()) { pressHome(); startActivityAndWait() }`. The noise floor is why "I measured once and it's faster" is never a valid claim.

**Estimated time.** 45 minutes.

---

## Problem 3 — Predict the CompilationMode result

**Problem statement.** Before running anything, *predict* in `notes/compilation-modes.md` the relative cold-start order of `CompilationMode.None()`, `Partial(Require)`, and `Full()` for an app with a Baseline Profile, and explain why `Full()` — though fastest — is *not* what you ship. Then (if you have a profile from the mini-project/challenge) confirm the prediction by measuring.

**Acceptance criteria.**

- A predicted ordering (slowest → fastest) with reasoning for each mode.
- An explanation of why `Full()` isn't realistic for release (install size/time; you don't AOT the whole app).
- If measured: the confirmation; if not: a note that the prediction stands to be tested.
- Committed.

**Hint.** `None()` = interpret/JIT (slowest), `Partial()` = AOT the profiled startup path, `Full()` = AOT everything (fastest but huge). Release ships `Partial` via the packaged profile.

**Estimated time.** 35 minutes.

---

## Problem 4 — Generate and inspect a Baseline Profile

**Problem statement.** Add the Baseline Profile plugin and a generator that drives a cold-start journey (launch + one interaction). Generate the profile, commit `baseline-prof.txt`, and in `notes/profile-anatomy.md` quote five lines from it and decode their prefixes (`H`/`S`/`P`/`L`).

**Acceptance criteria.**

- A generator test using `BaselineProfileRule().collect { }` driving launch + an interaction.
- `baseline-prof.txt` generated and committed.
- Five real lines quoted and their flags decoded (`H`=hot, `S`=startup, `P`=post-startup, `L`=class).
- 0 warnings. Committed.

**Hint.** `./gradlew :app:generateBaselineProfile` writes the file under `app/src/release/generated/baselineProfiles/`. A line like `HSPLcom/.../MainActivity;-><init>()V` is a hot, startup, post-startup method of `MainActivity`.

**Estimated time.** 45 minutes.

---

## Problem 5 — Write a keep rule, read the mapping outputs

**Problem statement.** Enable R8 on a release build. Take a model serialized by reflection (Gson) and reproduce the obfuscated-keys bug in release. Fix it with the narrowest keep rule, then quote from `seeds.txt` (the field kept) and `usage.txt` (it was not removed) to prove the rule took effect, into `notes/keep-rule.md`.

**Acceptance criteria.**

- `isMinifyEnabled = true`; the release-only obfuscated-keys bug reproduced (debug fine, release wrong).
- A narrow keep rule (one model's members, or annotation-scoped) — *not* `-keep class ** { *; }`.
- Evidence from `seeds.txt`/`usage.txt`/`mapping.txt` that the fields are kept and not renamed.
- R8 stays enabled. 0 warnings. Committed.

**Hint.** `-keepclassmembers class com.example.MyModel { <fields>; }`. Outputs live in `app/build/outputs/mapping/release/`. kotlinx-serialization is largely R8-safe (consumer rules) — use Gson to *see* the rename.

**Estimated time.** 45 minutes.

---

## Problem 6 — Find a startup span in a trace

**Problem statement.** Capture a startup trace (a macrobenchmark iteration captures one, or use Android Studio's "Profile 'app'" → system trace). Open it, find the widest span on the startup critical path, and in `notes/trace.md` name it, its approximate duration, and what you'd do to shrink it. Label one of your own startup operations with `Trace.beginSection`/`endSection` and confirm it appears.

**Acceptance criteria.**

- A startup trace captured and opened (Perfetto / Studio).
- The widest startup-path span identified with an approximate duration and a proposed fix (defer it, move off main thread, lazy-init).
- One custom `Trace.beginSection("...")` span added and confirmed visible in the trace.
- Committed.

**Hint.** Look in `Application.onCreate`, `bindApplication`, and provider-init spans, and at the gap before the first `Choreographer#doFrame`. `androidx.tracing.Trace.beginSection("ReaderDb.open")` ... `endSection()` labels your own work.

**Estimated time.** 40 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, builds with 0 warnings, measurements are on real hardware as distributions, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor lapse (e.g. P90 omitted, a slightly-too-broad keep rule, a noise floor noted but not used in the conclusion). |
| 3 | Works, but misses one criterion (e.g. measured once instead of twice, profile generated but not committed, trace span found but no proposed fix). |
| 2 | Compiles/runs but a core idea is wrong (benchmarked on the emulator, claimed a win inside the noise floor, disabled R8 to "fix" a keep-rule problem). |
| 1 | Does not build, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−2** for an emulator-measured startup number presented as a real result; **−2** for disabling R8 (`isMinifyEnabled = false`) to avoid writing a keep rule, or using `-keep class ** { *; }`; **−1** for claiming a cold-start improvement without establishing or clearing the noise floor.

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — measuring cold start as a distribution against a noise floor (problems 2, 3) and the Baseline-Profile / R8 mechanics (problems 4, 5) — so re-run exercises 02 and 03 before resubmitting.
