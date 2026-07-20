# Week 16 — Resources

Every primary resource on this page is **free**. Android's developer documentation is free. The AndroidX (WorkManager) and AOSP source is public on Android Code Search and GitHub. The conference talks are free on YouTube. A couple of paid references are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"Schedule tasks with WorkManager."** The canonical WorkManager guide — requests, constraints, observing state, chaining, unique work. This is the spine of lecture 1:
  <https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started>
- **"Define work requests" + "Work constraints."** The detail pages on `OneTime`/`Periodic`/expedited requests, backoff, and the constraint types:
  <https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work>
- **"Foreground services overview" and "Foreground service types are required" (Android 14).** The lifecycle, `startForeground`, and — critically — the Android 14 requirement to declare a type and hold the matching permission. Read the type table carefully; getting it wrong crashes your app:
  <https://developer.android.com/develop/background-work/services/foreground-services>
  <https://developer.android.com/about/versions/14/changes/fgs-types-required>
- **"Schedule alarms" + "Exact alarm permission" (Android 12).** `AlarmManager`, `setExactAndAllowWhileIdle`, the `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` permissions, and `canScheduleExactAlarms()`. Read the "use an inexact alarm or WorkManager instead" guidance — it's the point:
  <https://developer.android.com/develop/background-work/services/alarms/schedule>
- **"Optimize for Doze and App Standby."** Maintenance windows, standby buckets, what's exempt, and why ordinary background work is deferred. The power-regime foundation for lecture 2:
  <https://developer.android.com/training/monitoring-device-state/doze-standby>

## WorkManager in depth — backoff, expedited, testing

- **"Advanced WorkManager" + "Expedited work."** Expedited jobs and `OutOfQuotaPolicy`, the foreground-promotion path (`setForeground`), and long-running workers:
  <https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running>
- **"Integration tests with WorkManager."** `WorkManagerTestInitHelper`, `SynchronousExecutor`, and `TestDriver` (`setAllConstraintsMet`, `setPeriodDelayMet`, `setInitialDelayMet`). Exactly what exercise 3 and the mini-project's test use:
  <https://developer.android.com/develop/background-work/background-tasks/testing/persistent/integration-testing>
- **"Hilt and WorkManager" (`@HiltWorker`).** Injecting dependencies into a worker with `HiltWorkerFactory` — the pattern the real sync worker uses:
  <https://developer.android.com/training/dependency-injection/hilt-jetpack#workmanager>

## Read it at the source — AndroidX and AOSP

You will not modify WorkManager, but reading a little of it makes the constraint pipeline concrete. Use Android Code Search:

- **`androidx.work` (WorkManager) source** — `WorkManagerImpl`, `Processor`, the `Scheduler` implementations, and the `constraints/` package (`ConstraintsTracker`, the `ConstraintController`s for network/battery/charging). Reading `constraints/` is the "why didn't my work run" lecture, in code:
  <https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:work/>
- **`JobScheduler` and `JobServiceContext`** (AOSP `frameworks/base`) — the OS service WorkManager schedules onto for deferrable work; the layer that actually negotiates with Doze and standby:
  <https://cs.android.com/android/platform/superproject/+/master:frameworks/base/services/core/java/com/android/server/job/>
- **`AlarmManagerService`** (AOSP) — how exact vs inexact alarms are queued and how `setExactAndAllowWhileIdle` punches through Doze. Deep, optional, but it demystifies the alarm restrictions.

## Talks (free, watch in this order)

- **"WorkManager: beyond the basics" / the current Google I/O background-work session** — expedited work, foreground promotion, and the constraint model in practice. Prefer the most recent year's session.
- **"Foreground services in Android 14"** — the type taxonomy and the migration; the talk that makes the manifest-declaration-plus-permission rule click.
- **"What's new in background work" (each year's I/O)** — the running history of Doze/standby/foreground/exact-alarm tightening. Search the current year's Android I/O playlist; this area changes every release.

## Community writing (current, opinionated, correct)

- **The Android Developers Medium publication** — the official long-form WorkManager and background-work series; filter for the WorkManager and foreground-service articles:
  <https://medium.com/androiddevelopers>
- **Pierre-Yves Ricau / Square engineering on background work and process death** — sharp writing on what actually survives on Android and why. Search for the "process death" and "background execution limits" posts.
- **Chris Banes / Android team engineers' blogs** — practical, current notes on foreground-service-type migration and Doze behavior:
  <https://chrisbanes.me/>

## Open-source projects to read this week

You learn more from one hour reading a real sync engine than three hours of docs:

- **`android/nowinandroid`** — its `sync/` module is a production WorkManager sync implementation with `@HiltWorker`, constraints, and a `SyncManager`. The reference for the mini-project's shape:
  <https://github.com/android/nowinandroid>
- **`android/architecture-samples` and the WorkManager samples** — focused, idiomatic WorkManager usage including foreground promotion and chaining:
  <https://github.com/android/architecture-samples>
- **Any app with an `:feature-sync` or offline-first module on GitHub** — read how they map a transient failure to `Result.retry()` and how they decide foreground promotion.

## Tools you'll use this week

- **`adb shell dumpsys deviceidle`** — inspect and force Doze state. `adb shell dumpsys deviceidle force-idle` puts the device into Doze so you can *see* ordinary work get deferred; `unforce` exits. The single most useful command for understanding Doze.
- **`adb shell dumpsys jobscheduler`** — see the jobs WorkManager scheduled onto `JobScheduler`, their constraints, and why they're pending.
- **`adb shell am set-standby-bucket <pkg> <bucket>`** — force your app into a standby bucket (`rare`, `restricted`) to test throttled behavior.
- **`adb shell cmd jobscheduler run -f <pkg> <jobId>`** — force-run a scheduled job for testing.
- **The Background Task Inspector** (Android Studio: `View ▸ Tool Windows ▸ App Inspection ▸ Background Task Inspector`) — a live view of your WorkManager jobs, their state, constraints, and chains. Use it constantly this week.

## Free books (chapter-level, not whole books)

- **Android's "Background work" pathway and codelabs** — the "WorkManager" and "Advanced WorkManager" codelabs are effectively a free guided book on the whole topic:
  <https://developer.android.com/courses>
- **The Doze/App-Standby and power-management docs as a guided path** — read top to bottom they form a free, authoritative treatise on Android's power regime.

## Paid books (optional, clearly marked)

- **"Programming Android with Kotlin" — Laurence et al. (O'Reilly)** (paid). The concurrency and background-execution chapters are solid and current.
- **"Android Internals" — Jonathan Levin** (paid, advanced). Far deeper than this week — the `JobScheduler`, `AlarmManagerService`, and power-management internals at the source level, if you want to go all the way down.

---

*If a link 404s, please open an issue so we can replace it.*
