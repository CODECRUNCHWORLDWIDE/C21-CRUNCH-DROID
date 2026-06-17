# Week 10 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can commit to your portfolio and point at in an interview: a before/after that turns a runtime crash into a compile error.

## Index

1. **[Challenge 1 — Plant a string-route footgun, then make it a compile error](./challenge-01-string-route-footgun-then-typed.md)** — deliberately ship the "argument read by the wrong `Bundle` key" bug in a string-route graph, write a test that catches it crashing at runtime, then refactor to typed routes so the *same* mistake is a compile error the test can no longer even express. You document the before/after and explain why "the compiler caught it" beats "the test caught it." (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — and "I turned a class of runtime navigation crashes into compile errors, here's the diff" is the kind of concrete, quantified win that lands in code reviews and interviews. The "make illegal states unrepresentable" instinct you build here reappears in Week 12 when you model `UiState` as a sealed type.
