# Week 06 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 07. Answer key with explanations at the bottom — don't peek.

---

**Q1.** What runs on the device — JVM `.class` bytecode or something else?

- A) Your `.class` files run directly on ART.
- B) Your `.class` bytecode is converted to **DEX** (by D8/R8), and ART loads and runs the DEX. The `.class` files never reach the device.
- C) Kotlin source is shipped and compiled on-device.
- D) ART runs native ARM code only; there's no bytecode.

---

**Q2.** Why does Android use DEX bytecode instead of JVM `.class` files?

- A) Licensing.
- B) DEX is register-based and packs many classes into one file — smaller and faster to load on a memory-constrained device than stack-based, one-class-per-file JVM bytecode.
- C) DEX is encrypted.
- D) It isn't — DEX and `.class` are identical.

---

**Q3.** What is "profile-guided compilation" in ART?

- A) ART interprets all code forever.
- B) ART interprets, then JIT-compiles hot methods while recording a profile, then AOT-compiles the profiled hot methods (via `dex2oat`) when the device is idle/charging — and a Baseline Profile lets you ship that profile so the hot path is AOT-compiled on first launch.
- C) The developer manually compiles each method.
- D) It's a Gradle feature, not a runtime one.

---

**Q4.** Why is "process death" treated as normal on Android, not an edge case?

- A) Apps crash frequently.
- B) The OS forks each app from the Zygote and **kills background app processes** to reclaim memory for the foreground app, without reliably running `onDestroy` — so your process and in-memory state can vanish at any time.
- C) Users force-quit apps constantly.
- D) ART has no garbage collector.

---

**Q5.** What happens to your `Activity` (by default) when the user rotates the phone?

- A) Nothing; the same instance keeps running.
- B) The framework **destroys** the current Activity and **creates a new one** (because resources/layout may differ), so any state held only in the old instance is lost unless saved.
- C) The app crashes.
- D) Only `onResume` is called again.

---

**Q6.** What did Jetpack Compose replace, and what did it NOT replace?

- A) It replaced ART; the JVM still runs underneath.
- B) It replaced the old View world (XML inflation, `findViewById`, `RecyclerView.Adapter`, manual view state) — but NOT the `Activity` host or the lifecycle; Compose runs inside an Activity's `setContent { }` and the OS still drives the lifecycle.
- C) It replaced Gradle.
- D) It replaced the Android runtime with a new one.

---

**Q7.** Put these build-pipeline stages in order for `assembleDebug`: (i) dexing, (ii) Kotlin compilation, (iii) signing, (iv) manifest merge, (v) resource compilation.

- A) ii → i → v → iv → iii (compile → dex → resources → manifest → sign)
- B) iii → ii → i → iv → v
- C) i → ii → iii → iv → v
- D) v → iv → iii → ii → i

---

**Q8.** A build fails with `Manifest merger failed`. Which Gradle task/stage is responsible, and what kind of problem is it?

- A) `compileDebugKotlin` — a code error.
- B) `processDebugMainManifest` — **stage 5, the manifest merge**; your manifest conflicts with a library's (e.g. clashing `allowBackup` or a duplicate component). Resolve with `tools:replace` or by removing the conflict.
- C) `minifyReleaseWithR8` — an R8 problem.
- D) `signingReport` — a signing problem.

---

**Q9.** What is a version catalog (`libs.versions.toml`) and why use one?

- A) A list of app versions for the Play Store.
- B) A central, typed file of dependency/plugin versions — referenced via `libs.*`/`libs.plugins.*` accessors — so versions live in one place, a bump is a one-line change, and typos are caught at configuration time instead of as failed downloads.
- C) A changelog.
- D) A Gradle cache.

---

**Q10.** You declare `free` and `pro` product flavors on one dimension, with `debug` and `release` build types. How many variants is that, and how do `free`/`pro` install side by side?

- A) Two variants; they can't coexist.
- B) **Four** variants (free/pro × debug/release); they install side by side because each flavor sets a distinct `applicationIdSuffix`, giving different `applicationId`s.
- C) Eight variants; via different `versionCode`s.
- D) One variant; flavors are cosmetic.

---

**Q11.** What does signing an APK with a keystore prove?

- A) That the app is bug-free.
- B) **Integrity** (the APK wasn't tampered with after signing) and a **stable identity across updates** (Android installs an update only if signed with the same key) — which is why a leaked release key is a security incident.
- C) That the app passed Play review.
- D) Only the developer's email address.

---

**Q12.** An app works in debug but crashes in release with `ClassNotFoundException` on a class reached via reflection. Most likely cause?

- A) The emulator is broken.
- B) **R8** (which runs on release, not debug) shrank or renamed the class — reflection is invisible to R8's reachability analysis, so it removed/obfuscated code it couldn't see was used. Fix with a `-keep` rule.
- C) The minSdk is too low.
- D) A missing internet permission.

---

**Q13.** When you call `getSystemService(...)` to reach the `ActivityManager`, what's actually happening across the process boundary?

- A) Nothing crosses a boundary; it's a local object.
- B) You get a **client** that talks over **Binder** (Android IPC) to the real service running in the separate `system_server` process — which is why some framework calls are cheap (in-process) and some are an expensive cross-process round-trip.
- C) It launches a new app.
- D) It reads a file from disk.

---

## Answer key

**Q1 — B.** Kotlin → JVM `.class` (kotlinc) → DEX (D8/R8) → ART loads the DEX. Your `.class` files never ship; only `classes.dex` reaches the device. (Lecture 1, §1–2; lecture 2, §1.)

**Q2 — B.** DEX is register-based and multi-class-per-file, designed to be smaller and faster to load on constrained devices than stack-based, one-class-per-file JVM bytecode. (Lecture 1, §2.)

**Q3 — B.** ART interprets, JIT-compiles hot methods while profiling, then AOT-compiles the profile when idle/charging; a Baseline Profile ships that profile so the hot path is native on first launch (Week 18). (Lecture 1, §2.)

**Q4 — B.** The OS forks apps from the Zygote and kills background processes to reclaim memory, often skipping `onDestroy`. Process death is routine, so you persist state and never rely on in-memory survival. (Lecture 1, §3.)

**Q5 — B.** By default a configuration change destroys and recreates the Activity; unsaved state in the old instance is lost. `rememberSaveable`/`ViewModel` exist to survive this (Weeks 08, 12). (Lecture 1, §4.)

**Q6 — B.** Compose replaced the old View world (XML, `findViewById`, adapters, manual view state) but not the Activity host or the lifecycle — Compose runs inside `setContent { }` and the OS still drives the lifecycle. (Lecture 1, §4.)

**Q7 — A.** Kotlin compile → dex → resource compile → manifest merge → sign (roughly; resources/manifest run in parallel with dexing, but compilation precedes dexing and signing is last). (Lecture 2, §1.)

**Q8 — B.** `processDebugMainManifest` is stage 5, the manifest merge; the failure is a conflict between your manifest and a library's, fixed with `tools:replace` or by removing the conflict. The task name alone identifies the stage. (Lecture 2, §1, §8.)

**Q9 — B.** A version catalog centralizes versions in a typed `libs.versions.toml`, referenced via `libs.*` accessors — one-line bumps, IDE navigation, and config-time typo detection. (Lecture 2, §3.)

**Q10 — B.** Two flavors × two build types = four variants; distinct `applicationIdSuffix` gives different `applicationId`s, so `free` and `pro` install side by side. (Lecture 2, §4.)

**Q11 — B.** A signature proves integrity and a stable cross-update identity; Android refuses an update signed with a different key — which is why a release key must never leak. (Lecture 2, §6.)

**Q12 — B.** R8 runs on release (not debug); it shrinks/renames statically-unreachable code, and reflection is invisible to it, so it removed/obfuscated the class. A `-keep` rule fixes it. "Works in debug, crashes in release" is R8 until proven otherwise. (Lecture 2, §7.)

**Q13 — B.** `getSystemService` returns a client that talks over Binder to the real service in `system_server`; the boundary explains why some calls are cheap and some are an expensive cross-process round-trip. (Lecture 1, §3, §6.)

---

*Score 11+? On to Week 07. Below 9? Re-read both lecture notes and re-run exercises 1 and 2 — tracing the build pipeline and wiring the catalog+variants are the two ideas this week is graded on.*
