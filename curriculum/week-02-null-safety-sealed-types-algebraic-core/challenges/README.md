# Week 02 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can commit to your portfolio and point at in an interview: a domain model redesigned so that illegal states *fail to compile*, with the previously-buggy call sites demonstrated to no longer build.

## Index

1. **[Challenge 1 — Model the illegal away](challenge-01-model-the-illegal-away.md)** — take a primitive-obsessed, nullable-everywhere domain model where illegal states are constructible (a "payment" that's both pending and confirmed; ids that are interchangeable `Long`s; a status string with no compiler help), redesign it with sealed types, inline value classes, and required per-case data, and *prove* that the old illegal call sites no longer compile. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — "I redesigned this model so the bad state is a type error, and here's the screenshot of the old code failing to compile" is exactly the kind of concrete, defensible win that lands in code reviews and senior interviews. The "make illegal states unrepresentable" instinct you build here is the foundation of Phase 2's `UiState` modelling and the whole track's approach to correctness.
