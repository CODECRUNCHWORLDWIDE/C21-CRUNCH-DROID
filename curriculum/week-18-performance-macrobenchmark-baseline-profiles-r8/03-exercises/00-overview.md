# Week 18 — Exercises

Short, focused drills. Each one should take 30–55 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Read a macrobenchmark report](./exercise-01-read-a-macrobenchmark-report.md)** — given real macrobenchmark output, interpret the P50/P90 distribution, decide whether a change is signal or noise, spot a noisy (untrusted) run, and recommend the fix. The whole point of lecture 1's "read the distribution" rule, on paper, before you run hardware. (~40 min)
2. **[Exercise 2 — A startup macrobenchmark](./exercise-02-startup-macrobenchmark.kt)** — write a `StartupTimingMetric` macrobenchmark with `StartupMode.COLD`, benchmark `CompilationMode.None()` vs `Partial()`, run both on a real device, and report the delta as a distribution. (~50 min)
3. **[Exercise 3 — R8 keep rules](./exercise-03-r8-keep-rules.kt)** — enable R8, watch a reflection-based serialization call break in the release build, write the *minimal* keep rule that fixes it without disabling R8, and read `usage.txt`/`seeds.txt` to confirm. (~45 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Exercises 2 and 3 need a **real physical device** — macrobenchmark and R8 are release-on-real-hardware concerns. The emulator's numbers are meaningless (lecture 1, §2). Exercise 1 is on paper.
- The `.kt` exercises drop into a `:benchmark` module (exercise 2) and the `:app` module + `proguard-rules.pro` (exercise 3). Each file's header says where each piece belongs.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A performance claim without a reproducible distribution is a *failing* exercise this week — the number is the grade.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-18` to compare.
