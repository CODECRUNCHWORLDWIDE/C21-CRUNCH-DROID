# Week 06 — Challenges

The exercises drill basics. **Challenges stretch you.** This one takes 60–120 minutes and produces something you can point at in an interview: a build-debugging runbook where you diagnosed four real failures, each in a different pipeline stage, and fixed each by tracing the failing task to its cause.

## Index

1. **[Challenge 1 — Debug a broken build](./challenge-01-debug-a-broken-build.md)** — start from a project with four planted build failures (a manifest-merge conflict, a version-catalog typo, a missing R8 keep rule causing a release-only crash, and a duplicate-class dependency clash at the dex stage). For each: reproduce it, read the failing Gradle task, map it to a pipeline stage, identify the cause, fix it, and document the trace. You produce a `BUILD-DEBUGGING.md` runbook of four traced fixes. (~90 min)

Challenges are optional. If you skip them, you can still pass the week. If you do this one, you'll be measurably ahead — "the build was red, I read the failing task name, mapped it to the manifest-merge stage, and fixed it in two minutes" is exactly the calm, systematic build-debugging that separates senior Android engineers from everyone pasting errors into a search engine. This is *the* most reused skill of the week: you will debug Android builds every single week for the rest of the track and the rest of your career.
