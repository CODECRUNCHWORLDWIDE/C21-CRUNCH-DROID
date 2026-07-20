# Week 01 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can commit to your portfolio and point at in an interview: a deliberately Java-flavoured Kotlin file rewritten idiomatically, with each change justified by the *bytecode* before and after.

## Index

1. **[Challenge 1 — From Javaism to idiom (with bytecode receipts)](challenge-01-javaism-to-idiom.md)** — take a working-but-ugly "Java written in Kotlin syntax" file, rewrite it into idiomatic Kotlin (expressions, `val`, data classes, extensions, scope functions, collection operators), and prove with `javap`/decompiled output that the idiomatic version costs nothing at runtime — same or simpler bytecode, fewer lines, fewer bugs. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — "I rewrote this module idiomatically and here's the decompiled bytecode proving it's identical at runtime" is the kind of concrete, defensible win that lands in code reviews and interviews. The bytecode-reading instinct you build here reappears in Week 3 (where `inline` functions make the bytecode story load-bearing) and Phase 3's R8/performance week.
