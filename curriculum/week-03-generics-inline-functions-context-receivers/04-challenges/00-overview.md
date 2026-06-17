# Week 03 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can commit to your portfolio and point at in an interview: a small type-safe DSL built from receiver lambdas, `@DslMarker`, and one reified builder method — with a `javap` disassembly proving the inlining.

## Index

1. **[Challenge 1 — A type-safe DSL builder](./challenge-01-type-safe-dsl-builder.md)** — build a small HTML-ish DSL where `html { body { p { +"hi" } } }` type-checks and `@DslMarker` stops you from accidentally nesting the wrong builder, then add a reified `element<T>()` helper and disassemble it to show the type was substituted at the call site. You combine every idea from the week — receiver function types, inline, reified, and the DSL ergonomics they enable. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — "I built a type-safe builder DSL and here's the bytecode proving the lambdas are inlined and the reified type is baked in" is exactly the concrete, mechanism-level answer that lands in senior interviews. The DSL-and-inline instinct you build here reappears the instant you touch Compose (`Column { }` is this), Gradle Kotlin DSL (`dependencies { }` is this), and every Kotlin builder you'll ever read.
