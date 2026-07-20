# Exercise 1 — Read a macrobenchmark report

**Goal.** Before you run a single benchmark on hardware this week, build the instinct that reads a macrobenchmark *distribution* like an engineer — P50 vs. P90, signal vs. noise, trustworthy vs. not-yet. You're given four real-shaped macrobenchmark outputs and you interpret each: is the change real? Is the run trustworthy? What would you do next? This is lecture 1, §3–4, applied on paper.

**Estimated time.** 40 minutes.

**Prerequisites.** Lecture 1 read. No tooling — write your answers in `notes/report-reading.md` in your Week 18 repo.

---

## The setup

You're reviewing a teammate's performance PR. They claim a Baseline Profile cut cold start. They pasted four macrobenchmark runs into the PR. For each, decide: **(a)** what the numbers say (P50, P90, spread), **(b)** whether you believe the claim, **(c)** what you'd ask for or do next.

## Report A — the "before" (no profile, `CompilationMode.None()`)

```
StartupBenchmark_coldStartup   [Pixel 6a, 20 iterations]
timeToInitialDisplayMs
    min 498.1,   median 521.4,   P90 612.0,   max 644.8
```

## Report B — the "after" (profile on, `CompilationMode.Partial(Require)`)

```
StartupBenchmark_coldStartup   [Pixel 6a, 20 iterations]
timeToInitialDisplayMs
    min 322.7,   median 341.6,   P90 408.2,   max 451.9
```

## Report C — a different "after" run, same code as B

```
StartupBenchmark_coldStartup   [Pixel 6a, 20 iterations]
timeToInitialDisplayMs
    min 338.0,   median 352.1,   P90 419.5,   max 470.3
```

## Report D — a suspicious run someone posted as a "win"

```
StartupBenchmark_coldStartup   [Pixel 6a emulator, 3 iterations]
timeToInitialDisplayMs
    min 210.4,   median 489.9,   max 1840.2
```

## The questions (answer each in `notes/report-reading.md`)

1. **A vs. B:** What's the P50 improvement (absolute ms and %)? The P90 improvement? Which percentile matters more for "users stopped complaining about a slow launch," and why?

2. **B vs. C:** These are the *same code*, run twice. What does the difference between them tell you about the **noise floor** of this benchmark? Given that noise floor, is the A→B improvement real, or could it be noise?

3. **Report D:** Three things are wrong with this run that make its numbers untrustworthy. Name all three. (Hint: look at the device, the iteration count, and the spread between min, median, and max.)

4. **The recommendation:** Your teammate wants to merge the PR claiming "35% faster cold start." Write the one or two sentences you'd put in the PR review — affirming what's real, and what (if anything) you'd want tightened before believing the headline number.

## What a strong answer looks like

A weak answer says "B is faster than A, so the profile works." A strong answer says:

> A→B is a P50 improvement of 521→342ms (−179ms, −34%) and a P90 improvement of 612→408ms (−204ms, −33%). The P90 matters most for "users stopped complaining" — a P50 win means the *typical* launch is faster, but it's the *tail* (the one-in-ten slow launch) that users notice and remember as "this app is sluggish," so a P90 that drops by a third is the headline. B vs. C (same code) differ by ~10ms at P50 and ~11ms at P90, so the run-to-run noise floor is roughly ±10-15ms. The 179ms A→B delta is more than ten times that noise floor, so the improvement is unambiguously real — not noise. Report D is untrustworthy on three counts: it's an *emulator* (lecture 1 §2 — emulated hardware gives meaningless startup numbers), only *3 iterations* (far too few to average out noise), and its spread is enormous (min 210, median 490, max 1840 — a 9× range, screaming uncontrolled device state). I'd approve the A↔B/C claim as a genuine ~34% win, and reject Report D entirely.

That's the level: absolute and percentage deltas, the noise floor established from the repeated run, the percentile that matters named with a reason, and the bad run's three flaws called out.

---

## Acceptance criteria

- [ ] `notes/report-reading.md` answers all four questions.
- [ ] The A→B improvement is computed for *both* P50 and P90, in ms and %.
- [ ] The noise floor is derived from the B-vs-C repeated run, and used to judge whether A→B is real.
- [ ] All three problems with Report D are identified (emulator, too few iterations, huge spread).
- [ ] Question 4's recommendation distinguishes what's believable from what needs tightening.

## What you just proved

You proved you can read a macrobenchmark distribution the way it must be read: as a distribution, not a number. The P50/P90 split, the noise floor from a repeated run, and the instinct to distrust an emulator-with-3-iterations are exactly what separate a real performance claim from a vibe. The single most common junior mistake this week isn't writing a bad benchmark — it's *believing* one: measuring once, seeing a smaller number, and declaring a win that's half noise. You just practiced the skepticism that prevents it.

## Hints (read only if stuck > 10 min)

- **% improvement** = (before − after) / before × 100. For P50: (521.4 − 341.6) / 521.4 ≈ 34%.
- **Noise floor:** if the same code run twice differs by X, then any "improvement" smaller than ~X is indistinguishable from noise. Here X ≈ 10-15ms, and the real delta is ~180ms — comfortably real.
- **Why P90 over P50 for user complaints?** Users don't average their launches; they remember the *worst* ones. A great P50 with a terrible P90 still feels like "this app is sometimes slow."
- **Report D's spread:** a healthy benchmark has min/median/max close together (a few percent). A 9× range means the device was thermal-throttling, running background work, or otherwise uncontrolled — the number is noise, not signal.
