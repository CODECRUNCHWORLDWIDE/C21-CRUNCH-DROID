# Week 17 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can commit to your portfolio and point at in an interview: a flaky-test autopsy that turns a red-then-green-then-red suite into a deterministic one, with a written diagnosis of each flake.

## Index

1. **[Challenge 1 — Flaky-test autopsy](challenge-01-flaky-test-autopsy.md)** — inherit a suite of five tests that pass *sometimes*. Diagnose each flake to its root cause — a real clock, a real `Dispatchers.Main`, shared mutable state, order dependence, an emission-timing race — fix it deterministically, and write the autopsy. Prove the fix by running the suite 100× green. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — and "I took a suite that flaked one run in five down to zero flakes in a thousand, and here's the diagnosis of each" is the kind of concrete, quantified win that lands in code reviews and senior interviews. Flaky tests are the single most corrosive thing in a team's CI: they teach engineers to ignore red. Curing them is a senior skill, and this challenge builds it directly. The determinism instinct you build here is the same one Week 18 needs to get a *stable* macrobenchmark number out of a noisy device.
