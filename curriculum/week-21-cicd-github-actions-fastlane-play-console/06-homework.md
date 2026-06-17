# Week 21 Homework

Six practice problems that revisit the week's topics. The full set should take about **5 hours** in total. Work in your Week 21 Git repository so each problem produces at least one commit you can point to later.

Each problem includes:

- A short **problem statement**.
- **Acceptance criteria** so you know when you're done.
- A **hint** if you get stuck.
- An **estimated time**.

All code targets a real GitHub repo, GitHub Actions, Kotlin 2.0+, fastlane, compileSdk 35. The Gradle side must build with **0 warnings**. The no-Play-account path is acceptable everywhere a real upload is mentioned.

---

## Problem 1 — Measure your cache

**Problem statement.** On a real repo with the CI workflow from exercise 1, measure the cold and warm Gradle build times (a cache-miss run and a cache-hit run). Then *break* the cache on purpose by bumping a dependency version, and confirm the next run goes cold again (the key changed). Record all three numbers in `notes/cache-behavior.md` with an explanation of why the third run was cold.

**Acceptance criteria.**

- `notes/cache-behavior.md` records cold, warm, and post-dependency-bump times.
- The explanation correctly states that changing a Gradle/lock input changes the cache key, so the cache misses (correctly, not as a bug).
- Committed.

**Hint.** The build cache is keyed on inputs. A new dependency version is a new input → a new key → a cache miss. That's the cache being *correct*, not broken — you never want a stale output for changed inputs.

**Estimated time.** 40 minutes.

---

## Problem 2 — A parallel matrix

**Problem statement.** Split your CI into three parallel jobs — `unit`, `lint`, and `screenshots` (Paparazzi) — and a fourth `gate-summary` job that `needs` all three. Confirm in the Actions tab that the three run in parallel and `gate-summary` only runs after all pass. Record the wall-clock time vs the sum of the three in `notes/matrix-timing.md`.

**Acceptance criteria.**

- Three parallel jobs and a `needs`-gated summary job.
- `notes/matrix-timing.md` records the parallel wall-clock vs the serial sum, showing the parallelism win.
- A note on when parallelism is *not* worth it (per-job fixed overhead on a tiny project).
- 0 warnings. Committed.

**Hint.** Jobs run in parallel by default; `needs: [unit, lint, screenshots]` makes the summary wait. The win is roughly the slowest job, not the sum — unless the fixed checkout+JDK+cache overhead dominates a trivial project.

**Estimated time.** 45 minutes.

---

## Problem 3 — Sign and verify

**Problem statement.** Generate an upload keystore, wire the env-fed `signingConfig` (exercise 2), build a signed release APK locally, and verify it with `apksigner verify --print-certs`. Then base64 the keystore into a GitHub secret and prove (in a workflow run) that the signed build also works in CI. Record the verification output and the secret list (names only, never values) in `notes/signing.md`.

**Acceptance criteria.**

- A local signed APK whose `apksigner` output names your upload key's certificate.
- The same signing working in a CI run via secrets (keystore decoded from base64).
- `notes/signing.md` shows the verification and lists the secret *names* (no values).
- No keystore committed (`.gitignore` enforced). 0 warnings. Committed.

**Hint.** `keytool -genkeypair ...` to make the keystore; `base64 -w0` (Linux) or `base64 -i` (macOS) to encode it; decode in the workflow into `$RUNNER_TEMP`. Verify with `apksigner verify --print-certs`.

**Estimated time.** 50 minutes.

---

## Problem 4 — The upload-key-vs-app-signing-key memo

**Problem statement.** Write `notes/play-app-signing.md`: explain, in your own words, the two-key Play App Signing model — which key Google holds, which you sign with in CI, what happens if you lose the upload key, and what happens if (pre-Play-App-Signing) you lost your single signing key. Include a one-paragraph "what could go wrong" for a team that commits their upload keystore to a public repo.

**Acceptance criteria.**

- A correct, in-your-own-words explanation of the two keys and the recovery story.
- The "what could go wrong" paragraph identifies that a leaked upload key lets an attacker upload to your console (bad, but rotatable) — and that the app signing key being with Google is what limits the damage.
- Committed.

**Hint.** App signing key = Google holds, signs what users install, recoverable-by-design. Upload key = you hold, CI signs with, resettable if lost. The disaster of the old single-key model (never update again) is what Play App Signing removed.

**Estimated time.** 30 minutes.

---

## Problem 5 — A fastlane lane that runs locally and in CI

**Problem statement.** Write a fastlane `internal` lane (exercise 3) that builds a signed AAB and uploads to the Play internal track as a draft — or, on the no-account path, builds + signs and the workflow attaches the AAB as an artifact. Run the lane *locally* (`bundle exec fastlane internal` or `internal_dryrun`), then run the *same lane* in CI. Record in `notes/fastlane.md` that the output was identical and explain why running the same lane both places matters.

**Acceptance criteria.**

- A working `internal` (or `internal_dryrun`) lane runnable locally and in CI.
- `notes/fastlane.md` shows the lane ran in both places with the same result, and explains why lane-identity (vs an ad-hoc script) reduces release risk.
- Service-account JSON (if used) read from a secret-fed path, never committed.
- 0 warnings. Committed.

**Hint.** The `Fastfile` is the same file locally and on the runner; the only difference is where the env/secrets come from. That identity is exactly why fastlane beats "the release script on someone's laptop."

**Estimated time.** 50 minutes.

---

## Problem 6 — The human-gate runbook

**Problem statement.** Write `notes/rollout-runbook.md`: a short runbook for the *human-gated* part of releasing — promoting from internal to closed/open and running a production staged rollout. Specify: which steps are automated (and where in your pipeline), which are human, the exact vitals you'd watch between rollout steps (crash-free rate, ANR rate), and your halt criteria. Include the `production_rollout` lane as the mechanism the human invokes.

**Acceptance criteria.**

- A runbook clearly separating automated steps from human-gated ones.
- Named vitals (crash-free users %, ANR rate) and concrete halt thresholds.
- The `production_rollout` lane (rollout `0.1`, etc.) referenced as the mechanism, with a note that its *trigger* is a person, not a tag.
- Committed.

**Hint.** Lecture 2, §4 is the template. The senior framing: automate build/test/upload-to-internal; keep promotion and the production rollout *trigger* human, reading vitals between each rollout step, ready to halt.

**Estimated time.** 35 minutes.

---

## Rubric

Each problem is graded out of the same five points; the week's homework is out of 30.

| Points | Meaning |
|-------:|---------|
| 5 | Meets every acceptance criterion, the Gradle side builds with 0 warnings, the pipeline is reproducible/gated/secret-safe, and the written explanation (where asked) is correct and in your own words. |
| 4 | Meets all criteria but with a minor non-idiomatic choice (e.g. a serial CI where a matrix was the point, a lane that only runs in CI not locally). |
| 3 | Works, but misses one criterion (e.g. cache numbers not recorded, signing works locally but not wired into CI, runbook missing halt thresholds). |
| 2 | Compiles and partially works; a core idea is wrong (a keystore committed to the repo, a release step that runs even when tests fail, claiming Google holds the upload key). |
| 1 | Does not build/run, or the approach fundamentally misunderstands the topic. |
| 0 | Not attempted. |

**Crosscutting deductions** (apply to any problem): **−3** for any committed keystore, password, or service-account JSON (a real secret leak, the cardinal sin of this week); **−2** for a release/upload step that isn't gated on tests passing; **−1** for a fastlane lane that can't run locally (CI-only), defeating the lane-identity benefit.

**Target: 24/30.** Below that, the two ideas to revisit are almost always the same two the quiz grades on — secrets-and-signing done safely (problems 3, 4) and the gated, reproducible pipeline with the human gate in the right place (problems 2, 5, 6) — so re-run exercises 02 and 03 before resubmitting.
