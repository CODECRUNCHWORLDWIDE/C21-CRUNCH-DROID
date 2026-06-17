# Week 12 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can commit to your portfolio and point at in an interview: a reproduced-then-fixed process-death bug.

## Index

1. **[Challenge 1 — Plant a "lost on process death" bug, then fix it](./challenge-01-process-death-bug-then-fix.md)** — deliberately put user-created state (a search query, a selected filter) in a `remember` instead of where it survives a process kill, reproduce the data loss with "Don't keep activities", then fix it by moving the right slice to `SavedStateHandle` — and prove the fix survives the kill while the derived state recomputes. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — and "our search lost the user's query on a low-memory kill; I moved the input to `SavedStateHandle` and proved it survives, here's the before/after" is the kind of concrete, real-incident win that lands in code reviews and interviews. The "save the inputs, recompute the outputs" instinct you build here is exactly what every production Android app needs, and what Phase 3's offline-sync and WorkManager weeks build on.
