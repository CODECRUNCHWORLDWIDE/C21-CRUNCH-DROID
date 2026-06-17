# Week 05 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can commit to your portfolio and point at in an interview: the single most common Flow bug in Android — an event replayed on rotation — reproduced, explained, and fixed, with Turbine proving the fix.

## Index

1. **[Challenge 1 — The event-replay bug](./challenge-01-event-replay-bug.md)** — model a one-shot "show snackbar" event as a `StateFlow` (the wrong way), reproduce the "it fires twice on rotation" bug by simulating a re-subscription, then fix it with `SharedFlow(replay = 0)` and contrast a `Channel`. Prove every step with Turbine. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — "I found why our snackbar double-fired on rotation and here's the Turbine test that proves the fix" is the kind of concrete, quantified win that lands in code reviews and senior interviews. The event-vs-state distinction you nail here is exactly the career pack's "cold versus hot flows — when to pick which" interview drill, and it is the bug you will catch in real code reviews for years.
