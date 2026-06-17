# Week 13 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can commit to your portfolio and point at in an interview: a real before/after migration of a god-object service locator to a clean compile-time DI graph.

## Index

1. **[Challenge 1 — ServiceLocator to Hilt, one binding at a time](./challenge-01-servicelocator-to-hilt.md)** — take a `ServiceLocator` god-object that hand-wires a dozen dependencies (with the classic mutable-singleton and test-pollution problems), migrate it to a Hilt graph incrementally, and document what each migration step bought you — testability, compile-time safety, scope correctness. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — "I migrated a 600-line ServiceLocator to a Hilt graph and here's the diff and what it fixed" is the kind of concrete, senior-level story that lands in code reviews and interviews. The migration instinct you build here is exactly what you'll need in the capstone when you assemble the seven-module graph, and what real teams do for months when they adopt Hilt onto a legacy app.
