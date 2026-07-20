# Week 24 — Exercises

The final week's drills. Each one prepares a real deliverable: the readiness audit pre-empts the Play rejection, and the two `.kt` drills are the deterministic proofs behind two of the three chaos drills you run live in the mini-project. Do them in order.

## Index

1. **[Exercise 1 — Play review readiness audit](exercise-01-play-review-readiness-audit.md)** — audit the capstone against the *actually-enforced* Play policies (Data Safety accuracy, foreground-service justification, permissions, target API, crashes) before you submit, so you land on the first try. The thing that turns a Friday rejection into a Monday pass. (~40 min)
2. **[Exercise 2 — The offline-conflict chaos drill (drill A)](exercise-02-offline-conflict-chaos-drill.kt)** — drive and verify the offline-sync conflict: two devices edit the same dispatch offline, reconnect, and converge. Assert determinism (both converge to the same state) and the documented loss bound (same-field LWW, different-field merge). The deterministic proof behind the live drill. (~55 min)
3. **[Exercise 3 — The FCM token-rotation drill (drill B)](exercise-03-fcm-token-rotation-drill.kt)** — drive and verify the token rotation: `onNewToken` re-registers through a *retryable* path, and a message sent during the window is not silently dropped. Assert that a failed registration retries rather than dropping the token. The line of code that keeps push from going dark. (~55 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. The chaos-drill drivers are the proofs your postmortems cite; you want to own them.
- The `.kt` exercises run as **plain JVM unit tests** (no emulator) so they prove the *convergence and re-registration contracts* deterministically. The live drills in the mini-project drive the *real* system (two emulators, a real token rotation, a Play-Services-less device) and measure real latency; these tests pin what "recovered" means so the live drill has a contract to check against.
- Exercise 1 is a written audit, not code — it produces `docs/play-readiness-audit.md`, every row PASS, before you submit.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A drill that "recovered" without a measured contract is a story, not a proof — the test is the arbiter.

Drill C (Play Integrity attestation failure) has no `.kt` exercise here because its contract is already pinned by Week 23 Exercise 3 (the three-outcome gate). This week you *run* it live on a Play-Services-less emulator and write its postmortem; the proof was built last week.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-24` to compare.
