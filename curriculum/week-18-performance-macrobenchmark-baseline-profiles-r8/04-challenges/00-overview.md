# Week 18 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can commit to your portfolio and point at in an interview: a measured, end-to-end Baseline Profile that cuts cold start by ≥20%, backed by the macrobenchmark output before and after.

## Index

1. **[Challenge 1 — Baseline Profile, end to end](./challenge-01-baseline-profile-end-to-end.md)** — take an app with no profile, benchmark its cold start as a distribution (the "before"), generate a Baseline Profile by driving the cold-start journey, package it into the release build, verify ART used it, benchmark again (the "after"), and document a ≥20% cold-start improvement with the macrobenchmark output and the noise floor. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — and "I cut cold start from 520ms median to 340ms by generating and packaging a Baseline Profile, and here's the macrobenchmark distribution proving it's real and not noise" is exactly the concrete, quantified win that lands in senior interviews. It's also the literal capstone deliverable (a Baseline Profile demonstrated to reduce cold start by ≥20%), so doing it now on a smaller app is rehearsal for the thing you'll be graded on in Week 23. The measure → fix → re-measure loop you build here is the entire discipline of performance engineering.
