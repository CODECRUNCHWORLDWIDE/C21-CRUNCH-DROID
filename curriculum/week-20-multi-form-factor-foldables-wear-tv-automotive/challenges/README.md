# Week 20 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 90–150 minutes and produces something you can commit to your portfolio and point at in an interview: one feature shipped *correctly* on three form factors — phone, unfolded foldable, and Wear — over a single shared domain layer, with each UI using the right surface and constraints for its form factor.

## Index

1. **[Challenge 1 — One codebase, three form factors](challenge-01-one-codebase-three-form-factors.md)** — take a single feature (a forecast browser) and ship it on a phone (adaptive single/two-pane), an unfolded foldable (tabletop-aware), and Wear OS (scaling list + a tile), all consuming one shared `:domain` layer. Document each design decision and prove no surface is a port of another. (~120 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — and "I shipped one feature on phone, foldable, and Wear from one domain layer, each with the right surface, and here's the decision log" is the kind of concrete, multi-form-factor win that lands in code reviews and senior interviews. The "right surface per form factor" instinct you build here is exactly what the capstone's `:app` + `:wear` split is graded on, and what Week 21 then learns to *release* through CI/CD.
