# Week 08 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can commit to your portfolio and point at in an interview: a screen that exhibited three classic side-effect bugs, each diagnosed by its lifecycle behavior and fixed with the correct API and key.

## Index

1. **[Challenge 1 — Plant the effect footguns, then fix them](./challenge-01-effect-footgun-then-fix.md)** — deliberately write a screen with three side-effect bugs at once (a coroutine that fires on every recomposition, a `LaunchedEffect` keyed wrong so it captures stale data, and a listener registered without teardown so it leaks), observe each broken behavior in logcat and the Layout Inspector, fix each with the right API and key, and document the before/after with logcat excerpts. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — and "I found a request firing on every recomposition and a leaked sensor listener, and here's the logcat proving the fix" is the kind of concrete, quantified win that lands in code reviews and senior interviews. The effect-lifecycle instinct you build here reappears in Phase III's testing and performance weeks, where a leaked listener shows up as a memory growth in a macrobenchmark.
