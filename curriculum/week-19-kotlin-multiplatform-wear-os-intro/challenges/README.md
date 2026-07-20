# Week 19 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can commit to your portfolio and point at in an interview: a Kotlin Multiplatform `:shared-core` that genuinely compiles for *two* platforms, with a shared test that runs on both — real portability, not aspiration.

## Index

1. **[Challenge 1 — A shared core that compiles for two platforms](challenge-01-shared-core-two-platforms.md)** — build a KMP `:shared-core` module with a typed domain model, a Ktor-backed repository, an `expect`/`actual` pair, and a `commonTest` that runs on Android *and* iOS. Prove portability by compiling the iOS target green and running the shared test on both. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — and "I built a shared Kotlin core that compiles for Android and iOS, with one test suite that passes on both, and here's the green iOS build proving it" is exactly the concrete win that lands in senior interviews and is the architectural spine of the capstone's `:shared-core`. The discipline you build here — keep the common code honestly portable, prove it with the compiler — is the difference between a `commonMain` that travels and one that only pretends to. The capstone *requires* a KMP shared core; this challenge is the rehearsal.
