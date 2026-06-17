# Week 18 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 19. Answer key with explanations at the bottom — don't peek.

---

**Q1.** Which startup type is the worst case and the most important to optimize?

- A) Hot start — the Activity is brought to the foreground.
- B) Cold start — the process is created from scratch, `Application` and the first screen built and drawn. It's the first impression.
- C) Warm start — the Activity is recreated but the process is alive.
- D) They all cost the same.

---

**Q2.** Time to initial display (TTID) vs. time to full display (TTFD):

- A) They're the same thing.
- B) TTID is when the first frame is drawn (the app is "on screen"); TTFD is when the content is fully loaded and usable (you signal it with `reportFullyDrawn()`).
- C) TTID is for warm start, TTFD for cold start.
- D) TTFD is always faster than TTID.

---

**Q3.** Why must Macrobenchmark run on a real physical device, not the emulator?

- A) The emulator can't install APKs.
- B) Emulated hardware, host-CPU contention, and no thermal reality make emulator startup numbers meaningless; real (ideally mid/low-end) hardware finds the real bottlenecks.
- C) Macrobenchmark requires a SIM card.
- D) It can run on the emulator and the numbers are identical.

---

**Q4.** A macrobenchmark prints `min 318, median 341, P90 408, max 498`. For "users stopped complaining the app is sometimes slow," which number matters most?

- A) The min — best case.
- B) The P90 (the tail) — users remember their *worst* launches, not the average; a bad P90 feels like "sometimes slow" even with a good median.
- C) The max only.
- D) The median is the only number that matters.

---

**Q5.** You benchmark the *same code* twice and the two medians differ by 12ms. A later change shows a 9ms improvement. Is it real?

- A) Yes — any improvement counts.
- B) No — 9ms is inside the ~12ms run-to-run noise floor, so it's indistinguishable from noise; you can't claim it without more iterations or better device control.
- C) Yes — 9ms is close to 12ms.
- D) Can't tell without the max.

---

**Q6.** To isolate a Baseline Profile's effect, you benchmark:

- A) `CompilationMode.Full()` only.
- B) `CompilationMode.None()` (no profile, worst case) vs. `CompilationMode.Partial(Require)` (profile applied); the difference is the profile's effect.
- C) `CompilationMode.Partial()` only.
- D) A debug build vs. a release build.

---

**Q7.** What does a Baseline Profile actually do?

- A) Caches network responses.
- B) Lists classes/methods that ART AOT-compiles at install time, so the startup path is native code on first launch instead of interpreted-then-JIT'd — typically 20–40% off cold start.
- C) Disables R8.
- D) Pre-loads images.

---

**Q8.** How do you generate a Baseline Profile?

- A) Write the `baseline-prof.txt` by hand.
- B) Run a `BaselineProfileRule().collect { }` generator test that drives the cold-start journey (launch + key interactions); the tooling records which methods ran and writes the profile. Commit it.
- C) Download it from Play Console.
- D) Enable a Gradle flag with no test.

---

**Q9.** R8 does three jobs on the release build. They are:

- A) Compile, sign, install.
- B) Shrink (delete unreachable code), optimize (inline, dead-branch removal), and obfuscate (rename to short names); it writes `mapping.txt`/`usage.txt`/`seeds.txt`.
- C) Encrypt, compress, upload.
- D) Test, lint, document.

---

**Q10.** A Gson-serialized model produces `{"a":1,"b":2}` in the release build but the right keys in debug. Cause and fix?

- A) Gson is broken; switch libraries.
- B) R8 obfuscated (renamed) the field names, which Gson reflects on; fix with a narrow keep rule (e.g. `-keepclassmembers class MyModel { <fields>; }`) — *not* by disabling R8.
- C) The network changed the keys.
- D) A debug/release theme difference.

---

**Q11.** When R8 breaks a reflection-heavy path, the senior move is:

- A) Set `isMinifyEnabled = false` to make the crash go away.
- B) Read the release crash, find the reflected name, and write the *narrowest* keep rule that fixes it — preserving R8's shrinking for everything else. Never `-keep class ** { *; }`.
- C) Add `try/catch` around the reflection.
- D) Ship the debug build to production.

---

**Q12.** Why do five SDKs each declaring a `ContentProvider` hurt cold start, and what fixes it?

- A) They don't; providers are free.
- B) Android instantiates every `ContentProvider` on the main thread during `Application` creation, before the first frame; the App Startup library merges them into one provider and lets you defer/lazy-init what isn't needed at startup.
- C) Providers leak memory; restart the app.
- D) They block the network; disable Wi-Fi.

---

**Q13.** StrictMode in a debug build, with `detectDiskReads().detectNetwork().penaltyLog()`, is used to:

- A) Speed up the release build.
- B) Flag main-thread disk/network I/O (and leaks via `VmPolicy`) during development, so you can move that work off the startup critical path — a debug-only smoke detector, never `penaltyDeath` in release.
- C) Encrypt SharedPreferences.
- D) Generate a Baseline Profile.

---

## Answer key

**Q1 — B.** Cold start builds the process from scratch — the worst case and the first impression, the metric you optimize. Hot/warm reuse the process and/or Activity and are far cheaper. (Lecture 1, §1.)

**Q2 — B.** TTID = first frame drawn (on screen); TTFD = fully loaded and usable, which you signal with `reportFullyDrawn()`. A fast TTID with a slow TTFD shows a skeleton then makes the user wait for content. (Lecture 1, §1.)

**Q3 — B.** Emulated hardware and host contention make emulator startup numbers meaningless; you need real hardware, ideally mid/low-end, to find real bottlenecks. The emulator is the #1 beginner mistake here. (Lecture 1, §2.)

**Q4 — B.** Users remember their worst launches, so the P90 tail is what "sometimes slow" feels like. Always report P50 *and* P90; a change that helps the median but hurts the tail is often a lived-experience regression. (Lecture 1, §3.)

**Q5 — B.** 9ms is inside the ~12ms noise floor established by the repeated run, so it's indistinguishable from noise. Believe a delta only when it clears the run-to-run spread. (Lecture 1, §3.)

**Q6 — B.** `None()` is the no-profile worst case; `Partial(Require)` applies the profile; their difference isolates the profile's effect. Benchmarking only `Partial` tells you nothing about what the profile bought. (Lecture 1, §4.)

**Q7 — B.** A Baseline Profile lists methods ART AOT-compiles at install, so the startup path is native on first launch instead of interpreted-then-JIT'd — a 20–40% cold-start win, free. (Lecture 1, §5.)

**Q8 — B.** You generate it by driving the cold-start journey in a `BaselineProfileRule().collect { }` test; the tooling records the methods that ran and writes `baseline-prof.txt`, which you commit. You never write it by hand. (Lecture 1, §6.)

**Q9 — B.** R8 shrinks (tree-shakes unreachable code), optimizes (inlines, removes dead branches), and obfuscates (renames). `mapping.txt` de-obfuscates crashes; `usage.txt` lists what was removed; `seeds.txt` lists kept entry points. (Lecture 2, §1.)

**Q10 — B.** R8 renamed the fields Gson reflects on, so release emits obfuscated keys. Fix with a narrow keep rule on the model's members — disabling R8 throws out all the shrinking to avoid four lines. (Lecture 2, §2.)

**Q11 — B.** Read the release crash, find the reflected name, write the *narrowest* keep rule. Disabling R8 (A) or `-keep class ** { *; }` defeats the optimizer; a surgical rule preserves 99% of the win. (Lecture 2, §2.)

**Q12 — B.** Every `ContentProvider` is instantiated on the main thread during `Application` creation, before the first frame — N providers on the startup path. App Startup merges them into one and lets you defer/lazy-init the ones not needed at launch. (Lecture 2, §4.)

**Q13 — B.** StrictMode flags main-thread disk/network and leaks in debug so you can move that work off the startup path. It's a debug smoke detector — never `penaltyDeath` in release, where a third-party SDK's disk read would crash real users. (Lecture 2, §5.)

---

*Score 11+? On to Week 19. Below 9? Re-read both lecture notes and re-run exercises 02 and 03 — the cold-start macrobenchmark (None vs Partial) and the surgical R8 keep rule are the two skills this week is graded on.*
