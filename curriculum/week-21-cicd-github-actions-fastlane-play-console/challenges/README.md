# Week 21 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 90–150 minutes and produces something you can commit to your portfolio and point at in an interview: a complete tag→release pipeline that builds, tests, screenshots, signs, and uploads both your phone AAB and your Wear APK to the Play internal track — with secrets handled correctly and the human gate documented.

## Index

1. **[Challenge 1 — The tag-to-internal-track pipeline](challenge-01-tag-to-internal-track-pipeline.md)** — wire the full release pipeline from lecture 1 and 2 for a *multi-form-factor* project: on a `v*` tag, a gated job builds and signs the phone AAB *and* the Wear APK, runs the full test gate, generates screenshots, and uploads to the internal track via fastlane — secrets in `$RUNNER_TEMP`, nothing in the repo, the production rollout left as a documented human-gated lane. (~120 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — and "I push a tag and a signed, tested, screenshotted build lands on the internal track for both the phone and the watch, with zero secrets in the repo" is the kind of concrete, end-to-end win that lands in code reviews and senior interviews. This pipeline IS capstone deliverable #7 — building it now means the capstone's release requirement is already in hand.
