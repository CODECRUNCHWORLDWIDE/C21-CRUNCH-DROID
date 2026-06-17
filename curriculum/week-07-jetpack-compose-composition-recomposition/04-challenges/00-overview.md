# Week 07 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can commit to your portfolio and point at in an interview: a measured before/after recomposition fix backed by the Compose Compiler report and the Layout Inspector.

## Index

1. **[Challenge 1 — Plant a recomposition footgun, then fix it](./challenge-01-recomposition-footgun-then-fix.md)** — deliberately write a feed screen that recomposes every row on every change (unstable `List` parameter, a `var` in the item, an unkeyed list, an animating value read in composition), measure it with the Compiler report and the Layout Inspector's recomposition counts, refactor to fully skippable with deferred reads, and document the before/after with the report excerpts and the counts. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — and "I took this screen from recomposing 1,000 rows per scroll to recomposing zero, and here's the Compiler report proving it" is the kind of concrete, quantified win that lands in code reviews and senior interviews. The recomposition-diagnosis instinct you build here reappears in Phase III's performance week, where the same skill shows up as a macrobenchmark and a Baseline Profile.
