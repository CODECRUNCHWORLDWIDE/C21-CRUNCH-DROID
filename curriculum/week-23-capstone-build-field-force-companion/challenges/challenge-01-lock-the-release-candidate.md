# Challenge 1 — Lock the release candidate

**Time.** 2–4 hours, spread across Friday and Saturday.
**Deliverable.** A signed `v1.0.0-rc1` AAB on a Play Console internal track, the full test suite green on CI, four ADRs in `docs/adr/`, the Mermaid architecture diagram in `docs/architecture.md`, and a committed pre-submission readiness audit reading all-PASS.

## The premise

Every Android team has shipped the build that "worked on my machine" and then discovered, on submission day, that R8 stripped a gRPC reflection class, the Baseline Profile never packaged, or the upload key was wrong. The fix is never clever — it is *doing the release build early, the way Play will serve it, with time to spare.* This challenge is that discipline. You take the integrated system from the week's exercises and mini-project milestones and turn it into a **locked release candidate**: signed, shrunk, profiled, tested on CI, uploaded, and audited. A release candidate you cannot upload is not a candidate; it is a hope.

The grading is not "does it build" — it is the gap between a debug build that runs on your emulator and a *signed, R8-optimized, Baseline-Profile-verified AAB on the internal track, green on CI, audited PASS.* That gap is the build week.

## What to do

### Step 1 — Build the signed release AAB

Wire the release signing config from properties (never hard-coded), enroll in Play App Signing, and assemble:

```bash
./gradlew :app:bundleRelease
```

Confirm the output `app-release.aab` is **signed with your upload key** (not the debug key) and that `isMinifyEnabled` and `isShrinkResources` are true in the release build type. Then do the same for the watch:

```bash
./gradlew :wear:assembleRelease
```

Open the AAB in `Build ▸ Analyze APK` (or build APKs with `bundletool` and analyze those) and confirm R8 actually shrank the code — the method count should be visibly lower than the debug build, and the obfuscated package names confirm R8 ran.

### Step 2 — Fix the R8 keep rules until release runs

Run the Espresso smoke test **in the release variant**, not debug:

```bash
./gradlew :app:connectedReleaseAndroidTest
```

If sign-in, sync, or serialization crashes only in release, R8 stripped a class your reflective code (gRPC, kotlinx-serialization, Room) needs. Read `app/build/outputs/mapping/release/missing_rules.txt` and add the **narrowest** keep rule that fixes it (Lecture 2 §4). Do **not** disable R8 or `-keep class ** { *; }` — that throws away the shrinking and the Baseline Profile's value. The smoke test passing in release is the gate.

### Step 3 — Generate, package, and verify the Baseline Profile

Generate the profile from the cold-start journey, package it in the release variant, and measure the delta with macrobenchmark (Lecture 2 §3):

```bash
./gradlew :app:generateReleaseBaselineProfile
./gradlew :baselineprofile:connectedCheck   # the StartupBenchmark, None vs Partial
```

Record both numbers in the capstone README: cold start with `CompilationMode.None()` and with `CompilationMode.Partial()`. The capstone bar is **≥ 20% improvement** on `timeToInitialDisplay`. If it does not move, your generator journey does not match the real cold-start path — fix the journey, not the number.

### Step 4 — Get the full test suite green on CI

Wire (or confirm from Week 21) a GitHub Actions workflow that, on the `v1.0.0-rc1` tag, runs the **whole** suite:

```text
[ ] unit            ViewModel (Turbine + MockK), repository ordering (exercise 2)
[ ] Robolectric     the DAO + the two Room migrations (schema export checked in)
[ ] Compose UI      the dispatch detail screen (createComposeRule)
[ ] Paparazzi       the Material 3 states of the dispatch list (snapshot)
[ ] Espresso        one end-to-end smoke (release variant)
```

Green on CI, not just locally. A test that passes on your machine and not on CI is a flaky test or a hidden local dependency; either is a finding.

### Step 5 — Upload to the internal track and tag

Upload the signed AAB to the Play Console **internal test track** (the closed track is next week). Confirm the upload processes without a policy or signing rejection. Then tag:

```bash
git tag v1.0.0-rc1 && git push --tags
```

After the tag, the app is **feature-frozen**. New code is limited to audit fixes.

### Step 6 — Write the four ADRs and the architecture diagram

In `docs/adr/`, write the four ADRs (Lecture 2 §5): offline-first source of truth (0001), the `:shared-core` KMP boundary (0002), the conflict-resolution policy (0003), and the Play Integrity attestation + fallback design (0004). Each has the decision, the alternatives, the trade-off, and the consequence. In `docs/architecture.md`, commit the Mermaid module + data-flow diagram and the trace-one-write path.

### Step 7 — Run the pre-submission readiness audit

Fill in `docs/pre-submission-audit.md` row by row (the build-week gate from Lecture 1 §6). Every row must read **PASS** before you call the RC locked. Any FAIL is your remaining homework — and finding it Saturday, with a week to fix it, is the entire reason you built the RC early.

## Acceptance criteria

- [ ] `:app:bundleRelease` produces a **signed** AAB (upload key, Play App Signing enrolled); `:wear:assembleRelease` produces a signed Wear APK.
- [ ] R8 full mode is on; the release-variant Espresso smoke test passes; the keep rules are minimal (no `-keep class ** { *; }`).
- [ ] A Baseline Profile is packaged and **verified ≥ 20%** cold-start improvement; both numbers are in the README.
- [ ] The full test suite (unit, Robolectric, Compose UI, Paparazzi, Espresso) is **green on CI** on the `v1.0.0-rc1` tag.
- [ ] The signed AAB is **uploaded to the Play internal track** and processed without rejection.
- [ ] Tagged `v1.0.0-rc1`; the app is feature-frozen.
- [ ] Four ADRs in `docs/adr/`; the Mermaid architecture diagram and trace-one-write in `docs/`.
- [ ] `docs/pre-submission-audit.md` is committed and **all-PASS**.
- [ ] No credentials in the repo: `grep -ri "STORE_PASSWORD\|KEY_PASSWORD\|BEGIN PRIVATE KEY" .` returns nothing but property *names*.

## What "great" looks like

A weak submission says "I built a release APK." A great submission says:

> `:app:bundleRelease` produces a signed AAB (Play App Signing enrolled, upload key in CI secrets); the APK analyzer confirms R8 full mode shrank the method count by ~38% versus debug. The release-variant Espresso smoke initially crashed on sign-in because R8 stripped a grpc-kotlin descriptor; `missing_rules.txt` named it, and a three-line `-keep class ...proto.**` rule fixed it without touching the rest of R8. The packaged Baseline Profile cut cold start from 612 ms to 472 ms on a Pixel 8 API 35 (`timeToInitialDisplay`, median of 10), a 23% improvement, both numbers in the README. The full suite is green on CI on `v1.0.0-rc1`, the signed AAB is on the internal track and processed clean, and the pre-submission audit is all-PASS. The four ADRs explain why Room is the source of truth, why `:shared-core` depends on nothing, why conflicts resolve last-writer-wins, and why an attestation failure shows a fallback instead of bricking.

Quantified, honest about what R8 broke and how the report fixed it, and locked early enough that Sunday's audit had nothing left to find.

## Where this reappears

This locked RC is the input to **every** Week 24 deliverable: the chaos drills run against it, the closed-track submission ships it, and the interviews defend its decisions. A locked RC this week is a victory lap next week; an unlocked one is a build sprint the same week you run chaos drills and present — exactly the crunch this course front-loads its work to avoid.
