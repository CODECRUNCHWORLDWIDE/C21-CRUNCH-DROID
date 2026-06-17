# Challenge 1 — "Why did my work not run?" (four scenarios, four causes)

**Time.** 60–120 minutes.
**Deliverable.** A `BACKGROUND-DEBUGGING.md` runbook in your Week 16 repo with four entries — one per scenario — each recording the symptom, the `adb`/inspector evidence you gathered, the root cause, and the correct fix. In scenario 4, the fix is to use a *less* powerful tool. Commit the working project too.

## The premise

The most common WorkManager support ticket is "I scheduled it and it's not running." The engineers who are *fast* don't add retries and battery exemptions and hope — they gather evidence (the Background Task Inspector, `adb shell dumpsys deviceidle`, `adb shell dumpsys jobscheduler`), identify *which clause failed* (a constraint, the power policy, a quota), and fix the actual cause. This challenge makes you do that four times, across the four real reasons work doesn't run. A failure you can't *diagnose* you can only flail at; this challenge is the diagnosis muscle for background work — the most-tested topic in senior Android interviews.

## Setup

Start from a project with WorkManager wired (your exercise-1 project is fine). Reproduce each scenario *one at a time*, gather the evidence, fix it, and move on. Keep the `adb` evidence — it's the deliverable.

## Scenario 1 — Stuck in ENQUEUED: a wrong constraint

**Reproduce.** Schedule a one-time worker with `setRequiredNetworkType(NetworkType.UNMETERED)` (Wi-Fi only) while the emulator/device is on cellular data only (or with Wi-Fi off). Run it.

**Symptom.** The work sits in ENQUEUED and never runs.

**Gather evidence.**

```
# The Background Task Inspector (App Inspection) shows the work ENQUEUED with a
# pending NETWORK_UNMETERED constraint. Also:
adb shell dumpsys jobscheduler | grep -A 20 <your.package.name>
```

You should see the job listed with its constraints, and the network constraint *unsatisfied*.

**Root cause.** `UNMETERED` requires Wi-Fi; on cellular the constraint is never met (lecture 1, §3). The constraint pipeline is working *correctly* — you asked for Wi-Fi and there's no Wi-Fi.

**Fix.** Either connect Wi-Fi (and watch it run instantly), or — if the work doesn't actually need Wi-Fi — change the constraint to `CONNECTED`. Document which fix is right for *which requirement*: a big download should stay `UNMETERED` (don't burn cellular); a small sync should be `CONNECTED`.

## Scenario 2 — Deferred by Doze

**Reproduce.** Schedule ordinary (non-expedited, no foreground) one-time or periodic work, then force the device into Doze:

```
adb shell dumpsys deviceidle force-idle
```

**Symptom.** The work doesn't run promptly even though its constraints are met.

**Gather evidence.**

```
adb shell dumpsys deviceidle            # shows state = IDLE (Doze)
adb shell dumpsys jobscheduler | grep -A 10 <your.package>   # job pending, deferred
```

The device is in Doze; ordinary background work is deferred to the next maintenance window.

**Root cause.** Doze suspends ordinary background work, batching it into periodic maintenance windows (lecture 2, §4). This is *by design* — it's how Android gets standby battery. Nothing is broken.

**Fix.** Exit Doze and watch it run:

```
adb shell dumpsys deviceidle unforce
```

Document the *correct* response: for genuinely deferrable work (a sync), Doze deferral is fine — you *accept* it. The *wrong* response would be a battery-optimization exemption to escape Doze; note why that's a Play-policy risk and almost never justified (lecture 2, §4). If the work truly needed to punch through Doze (it doesn't here), that's a foreground service or a high-priority FCM message — not an exemption.

## Scenario 3 — Expedited work that didn't run promptly: exhausted quota

**Reproduce.** Force your app into a low standby bucket, then enqueue several expedited workers in a row:

```
adb shell am set-standby-bucket <your.package> rare
```

```kotlin
repeat(10) {
    val req = OneTimeWorkRequestBuilder<SomeWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()
    WorkManager.getInstance(ctx).enqueue(req)
}
```

**Symptom.** The first expedited jobs run promptly; later ones fall back to ordinary (non-expedited) execution and run *later*.

**Gather evidence.** The Background Task Inspector shows later jobs not running expedited; `dumpsys jobscheduler` shows the app's expedited quota and that it's depleted. The `rare` bucket grants a small quota.

**Root cause.** Expedited work has a *quota* tied to the standby bucket (lecture 1, §4; lecture 2, §4). In `rare`, the quota is small and refills slowly. When exhausted, `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST` correctly degrades the job to ordinary work rather than dropping it.

**Fix.** Restore the bucket (`adb shell am set-standby-bucket <pkg> active`) and document the real lesson: **expedited is a limited budget, not a bypass.** Don't enqueue ten expedited jobs — expedite the one that's truly user-initiated and let the rest be ordinary deferrable work. Note that the bucket reflects *user behavior* (they don't open your app), which you can't and shouldn't fight.

## Scenario 4 — The "too much power" anti-pattern: an exact alarm that should be WorkManager

**Reproduce.** Someone wrote a "refresh the feed every hour" feature using `AlarmManager.setExactAndAllowWhileIdle` with `SCHEDULE_EXACT_ALARM`. On Android 13+, `canScheduleExactAlarms()` returns `false` by default, so the alarm is never set — and even if it were, this is the wrong tool.

```kotlin
// THE ANTI-PATTERN:
val am = ctx.getSystemService(AlarmManager::class.java)
am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextHourMillis, pendingIntent)
// On Android 13+: canScheduleExactAlarms() is false by default -> SecurityException or no-op
```

**Symptom.** The refresh doesn't fire (permission not granted), or worse, it works in dev (where you granted it) and silently fails for users (who didn't), and Play flags the `SCHEDULE_EXACT_ALARM` declaration.

**Gather evidence.** `adb shell dumpsys alarm | grep <your.package>` (no exact alarm registered); `canScheduleExactAlarms()` logs `false`. And the conceptual evidence: *is this work user-chosen-exact-time?* No — "every hour" is deferrable, and the user didn't pick a clock time.

**Root cause.** This is the decision-framework failure (lecture 2, §1): a deferrable refresh was implemented with the *most* powerful tool (an exact alarm), which is permission-gated, Play-scrutinized, and battery-hostile — for no benefit. "Every hour, approximately" never needed exact timing.

**Fix.** Replace the exact alarm with **periodic WorkManager work** — the *less* powerful, correct tool:

```kotlin
val refresh = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .build()
WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
    "feed-refresh", ExistingPeriodicWorkPolicy.KEEP, refresh)
```

Remove the `SCHEDULE_EXACT_ALARM` permission from the manifest. Document: the requirement (approximate hourly refresh) justified WorkManager, not an exact alarm; the exact alarm was *more power than the task needed* — the exact anti-pattern the week's promise forbids.

## Acceptance criteria

- [ ] `BACKGROUND-DEBUGGING.md` has four entries, each with: the symptom, the `adb`/inspector evidence (commands + what you saw), the root cause, and the correct fix.
- [ ] Scenario 1 distinguishes "fix the constraint" from "satisfy the constraint" and ties the choice to the requirement (UNMETERED vs CONNECTED).
- [ ] Scenario 2 explicitly rejects a battery-optimization exemption as the fix and explains why Doze deferral is acceptable for deferrable work.
- [ ] Scenario 3 explains expedited quota as a bucket-tied budget, not a bypass, and ties the bucket to user behavior.
- [ ] Scenario 4 replaces the exact alarm with periodic WorkManager, removes the `SCHEDULE_EXACT_ALARM` permission, and explains the "least power" principle.
- [ ] A one-paragraph reflection: the general algorithm — *symptom → gather evidence → which clause failed (constraint / power policy / quota / wrong tool) → correct fix* — in your own words.

## What "great" looks like

A weak submission says "I fixed the work." A great submission says:

> Scenario 1's work sat in ENQUEUED; the Background Task Inspector showed a pending `NETWORK_UNMETERED` constraint and `dumpsys jobscheduler` confirmed the network constraint unsatisfied on cellular — the pipeline was working correctly, I'd asked for Wi-Fi. Since the sync was small, I relaxed it to `CONNECTED`. Scenario 4 was the instructive one: a feed-refresh implemented with `setExactAndAllowWhileIdle` failed silently for users because `canScheduleExactAlarms()` is false by default on Android 13+, and `dumpsys alarm` showed no alarm registered. But the real bug wasn't the permission — it was the *tool*: an approximate hourly refresh is deferrable, so it never needed an exact alarm at all. I replaced it with periodic WorkManager and deleted the `SCHEDULE_EXACT_ALARM` declaration, which also removed the Play-policy risk. Across all four, I never added a retry or a battery exemption to paper over a diagnosable cause.

Evidence-based, framework-driven, and biased toward *less* power. That's the senior background-work answer.

## Where this reappears

The capstone's `:feature-sync` is exactly this, at production scale — and its chaos drills (FCM token rotation, offline-sync conflict) build directly on the "which clause failed" diagnosis you drilled here. Every real app you ever ship will have a "why didn't my background work run" incident, and the calm, evidence-first algorithm — *symptom → evidence → failed clause → correct, least-powerful fix* — is the single most reused background-work skill in the field.
