# Week 15 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can commit to your portfolio and point at in an interview: a measured before/after fix of a retry storm, plus a written rotation-safety analysis of certificate pinning.

## Index

1. **[Challenge 1 — Plant a retry-storm footgun, then refactor it](challenge-01-footgun-then-refactor.md)** — deliberately write the "unbounded tight-loop retry against a flaky server" footgun, point it at a `MockWebServer` that returns 503s, *count the requests it fires per second*, then refactor into bounded exponential backoff with jitter and retryable-only logic, and measure the request count again. Then write a rotation-safety analysis of a single-leaf certificate pin and design a rotation-safe pin set. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — "I turned a 400-requests-per-second retry storm into 4 requests over 8 seconds, and here's the request-count graph" plus "here's why a single-pin cert config bricks 100% of installs on rotation day and how I'd design the pin set" is exactly the kind of concrete, quantified, operationally-aware story that lands in senior interviews. The resilience instinct you build here is what Week 16 (WorkManager) builds on — the job-level backoff is the same idea at a coarser timescale.
