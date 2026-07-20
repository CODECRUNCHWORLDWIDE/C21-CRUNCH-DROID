# Week 16 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — Constraints and backoff](exercise-01-constraints-and-backoff.md)** — schedule a network-constrained one-time worker with `EXPONENTIAL` backoff, observe its `WorkInfo` state with the Background Task Inspector, then force a `retry` and watch the backoff delay grow. The constraint pipeline and the retry machinery, made visible in one worker. (~45 min)
2. **[Exercise 2 — A foreground-promoting CoroutineWorker](exercise-02-coroutine-worker-foreground.kt)** — write a `CoroutineWorker` that promotes itself to the foreground with a progress notification and the correct `dataSync` foreground service type, Android-14-compliant — declared type, held permission, passed to `ForegroundInfo`. Get it wrong and it crashes; get it right and it runs. (~50 min)
3. **[Exercise 3 — A deterministic WorkManager test](exercise-03-workmanager-test.kt)** — test a constrained periodic worker with `WorkManagerTestInitHelper`, `SynchronousExecutor`, and `TestDriver`: drive the constraints and the period delay manually, assert the `WorkInfo` state, zero flakiness. Async OS-scheduled work, made deterministic. (~40 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. WorkManager fluency only comes from typing the builders.
- Run on the **Android toolchain**: Android Studio Ladybug+, JDK 17, a Pixel 8 API 35 emulator, **targetSdk 35** (required so the Android 14 foreground-service rules apply). Exercise 1 uses the Background Task Inspector; exercise 2 runs on the emulator; exercise 3 is a JVM/Robolectric test.
- Add the dependencies: `androidx.work:work-runtime-ktx`, `androidx.work:work-testing` (exercise 3), and `androidx.hilt:hilt-work` if you wire `@HiltWorker`.
- Use `adb shell dumpsys deviceidle` and the Background Task Inspector liberally — this week is about *seeing* the OS defer and run your work.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. A foreground service that crashes on Android 14 is a failing exercise, not a passing one.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-16` to compare.
