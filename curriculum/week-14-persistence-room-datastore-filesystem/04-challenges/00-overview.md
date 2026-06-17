# Week 14 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can commit to your portfolio and point at in an interview: a measured before/after performance fix on a real Room store.

## Index

1. **[Challenge 1 — Plant a footgun, then refactor it](./challenge-01-footgun-then-refactor.md)** — deliberately write the "load everything, filter in Kotlin" footgun (and a relation N+1) over a large seeded Room store, measure both with the Database Inspector and `EXPLAIN QUERY PLAN`, refactor them into a `WHERE`-clause query (with an index) and a `@Transaction` relation query, and document the before/after timing and query plans. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — "I made a Room query 80× faster, here's the `EXPLAIN QUERY PLAN` before and after" is the kind of concrete, quantified win that lands in code reviews and interviews. The performance instinct you build here reappears in Phase 3's Week 18 (macrobenchmark and Baseline Profiles) — the footgun you fixed here is the same shape as the main-thread-query jank you'll profile then, just with a database query plan instead of a flame graph.
