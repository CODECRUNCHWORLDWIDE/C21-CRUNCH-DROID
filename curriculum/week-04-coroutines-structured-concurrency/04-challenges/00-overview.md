# Week 04 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can commit to your portfolio and point at in an interview: three real cancellation bugs, found, explained, and fixed with a test proving each.

## Index

1. **[Challenge 1 — The cancellation bug hunt](./challenge-01-cancellation-bug-hunt.md)** — a small URL fetcher ships with the three canonical cancellation bugs planted in it (a non-cooperative loop, a swallowed `CancellationException`, and a `GlobalScope` leak). Reproduce each with a failing test, explain the root cause, fix it, and prove the fix. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — "I found three cancellation bugs in a fetcher and here are the tests that prove each fix" is the kind of concrete, quantified win that lands in code reviews and senior interviews. The bug-hunt instinct you build here is exactly what the career pack's "coroutines pitfalls — three real production bugs and the fix for each" interview drill asks for.
