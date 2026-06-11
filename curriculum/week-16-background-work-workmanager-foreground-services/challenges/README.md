# Week 16 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can point at in an interview: a diagnostic runbook where you reproduced four real "my background work didn't run" scenarios, traced each to its cause with `adb`/`dumpsys` evidence, and — in one case — recognized that the right fix was to use a *less* powerful tool.

## Index

1. **[Challenge 1 — "Why did my work not run?"](challenge-01-why-did-my-work-not-run.md)** — diagnose four background-work failures: (1) work stuck in ENQUEUED because of a wrong/unmet constraint, (2) work deferred by Doze, (3) expedited work that didn't run promptly because its quota was exhausted in a low standby bucket, and (4) an exact alarm that should have been WorkManager (the "too much power" anti-pattern). For each: reproduce it, gather `adb`/inspector evidence, identify the cause, and fix it correctly. You produce a `BACKGROUND-DEBUGGING.md` runbook. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — "the sync was stuck in ENQUEUED; I read the pending network constraint in the Background Task Inspector and saw the device was in Doze via `dumpsys deviceidle`, so the fix was a maintenance window, not a retry" is exactly the calm, evidence-based background-work debugging that separates senior Android engineers from everyone adding retries and battery-optimization exemptions in desperation. The "categorize the work, choose the least power, diagnose with evidence" instinct you build here is the single most-tested background-work skill in senior interviews and the spine of the capstone's sync feature.
