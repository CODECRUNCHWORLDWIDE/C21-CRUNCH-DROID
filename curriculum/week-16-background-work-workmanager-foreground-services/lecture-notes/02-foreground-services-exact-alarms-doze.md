# Lecture 2 — Foreground services, exact alarms, Doze, and the "which tool" decision

> "Background work on Android is a regulated industry now. The senior move is not knowing every regulation — it's reaching for the *least* powerful tool that does the job, and never asking for power you haven't earned."

Lecture 1 gave you WorkManager — the default, the workhorse, the answer to most background-work questions. This lecture gives you the two other tools (foreground services and exact alarms), the power regimes that constrain all of them (Doze and App Standby), and — the most important deliverable of the week — the *decision framework* for choosing between the three. We take it in order: the decision framework first (so everything else hangs off it), then foreground services and the Android 14 rules that crash you, then exact alarms and why Android 12 made them a last resort, then Doze and standby, then a worked "which tool" walkthrough.

---

## 1. The decision framework — choose the least power

Before any API, the question: **what kind of work is this?** Three categories, three tools:

| The work is... | Tool | Why |
|---|---|---|
| **Deferrable + durable** — must run eventually, can wait for good conditions, must survive reboot (sync, upload queue, cache refresh) | **WorkManager** | The OS picks the moment within the power rules; durability for free. |
| **User-aware + ongoing right now** — the user knows it's happening and is waiting on it (music playback, active navigation, a live workout, an in-progress upload they're watching) | **Foreground service** | A persistent notification keeps the user informed; survives Doze; but Android 14 requires a declared type + permission. |
| **Precise wall-clock + user-chosen** — must happen at an exact time the *user* picked (an alarm clock, a medication reminder, a calendar event firing) | **Exact alarm** | The only tool that fires at a precise moment through Doze — but permission-gated since Android 12 and Play-scrutinized. |

The governing principle: **use the least powerful mechanism that satisfies the requirement.** Foreground services and exact alarms are *more* powerful than WorkManager — they punch through the power rules — and that power costs the user battery and costs you compliance burden and Play scrutiny. So the default is always WorkManager, and you escalate only when the work genuinely needs more.

The two anti-patterns a senior reviewer rejects on sight:

- **"I'll use an exact alarm to make sure my sync runs on time."** No — a sync is deferrable; it's WorkManager. Exact alarms are for user-chosen *moments*, not for "I want it to be prompt."
- **"I'll request a battery-optimization exemption so my work always runs."** Almost never. Exemptions (§4) are a last resort for a narrow class of apps; requesting one for ordinary work is a Play-policy risk and a battery sin.

Hold this table. The rest of the lecture is the detail of each tool — but the *judgment* is this framework.

---

## 2. Foreground services — and the Android 14 rules that crash you

A **foreground service** is a `Service` that does work the user is actively aware of, signaled by a **mandatory persistent notification**. Because the user can see it (and dismiss the app's work via the notification), the OS treats a foreground service as high-priority: it won't be killed for memory the way a background process is, and it keeps running through Doze. Music players, navigation, fitness tracking, ongoing calls — all foreground services.

The classic shape:

```kotlin
class PlaybackService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildPlaybackNotification()
        // Promote to foreground — the notification is REQUIRED.
        startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        return START_STICKY
    }
    override fun onBind(intent: Intent?) = null
}
```

**The Android 14 lockdown — get this wrong and your app crashes.** Since Android 14 (API 34), every foreground service must:

1. **Declare a foreground service *type*** in the manifest, and
2. **Hold the matching permission**, and
3. **Pass the type to `startForeground`.**

The type taxonomy (each with its own permission and its own justification rules):

| Type | For | Permission |
|---|---|---|
| `dataSync` | uploading/downloading/syncing data | `FOREGROUND_SERVICE_DATA_SYNC` |
| `mediaPlayback` | playing audio/video | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` |
| `location` | GPS/location tracking | `FOREGROUND_SERVICE_LOCATION` |
| `connectedDevice` | talking to a Bluetooth/companion device | `FOREGROUND_SERVICE_CONNECTED_DEVICE` |
| `microphone`, `camera`, `phoneCall`, `mediaProjection`, ... | as named | their respective permissions |

The manifest declaration:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<service
    android:name=".SyncService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

**If the type isn't declared, or the permission isn't held, `startForeground` throws and your app crashes** (`MissingForegroundServiceTypeException` / a `SecurityException`). This is the single most common Android-14 migration crash. And `dataSync` specifically is now *time-limited* (the OS caps how long a `dataSync` foreground service may run per day) — Google's strong steer is: **for data sync, use WorkManager's foreground-promotion path, not a raw foreground service.**

**WorkManager's foreground-promotion path — the right way to do user-aware sync.** Instead of writing a raw `Service`, you let a `CoroutineWorker` *become* a foreground service for the duration of its work:

```kotlin
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        // Promote THIS worker to the foreground with a progress notification.
        setForeground(getForegroundInfo())
        return runSync()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = buildSyncNotification(progress = 0)
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC      // the type, here too
        )
    }
}
```

You get WorkManager's durability and constraint model *and* a user-visible notification when promotion is warranted — without hand-writing a `Service`. This is exactly what the mini-project does: the sync is ordinary WorkManager work, but if the user opens the app mid-sync, the worker calls `setForeground` to surface progress. You still declare the `dataSync` type and permission in the manifest. Best of both: durable + user-aware, compliant on Android 14.

### The Android-14 crash, traced

To make the failure mode concrete, here's what it looks like when you forget the type — because you *will* see this if you migrate an old app. You promote a worker:

```kotlin
override suspend fun getForegroundInfo() =
    ForegroundInfo(NOTIFICATION_ID, notification)   // <-- no type passed, no permission declared
```

On a device running Android 14+ (targetSdk 34+), the moment `setForeground` runs you get:

```text
android.app.MissingForegroundServiceTypeException:
  Starting FGS without a type  callerApp=... targetSdkVersion=34 ...
   ── or ──
java.lang.SecurityException: Starting FGS with type dataSync ...
  requires permission android.permission.FOREGROUND_SERVICE_DATA_SYNC
```

The fix is three coordinated changes — and all three are required:

1. **Manifest permission:** `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />`.
2. **Service type on WorkManager's service:** `android:foregroundServiceType="dataSync"` on the merged `SystemForegroundService` entry.
3. **Type in `ForegroundInfo`:** the three-arg constructor with `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC`.

Miss any one and it still crashes. This is *the* most common Android-14 migration failure, and the reason the week's promise singles out "never crash on Android 14+" — because the default of an un-migrated app is exactly this crash. The mini-project and exercise 2 both make you reproduce it once (so you recognize it) and fix it (so you can ship).

### When you genuinely do need a raw foreground `Service`

WorkManager's promotion covers most user-aware work, but a few cases still want a hand-written `Service` — an ongoing one that lives independent of any single unit of work: a music player that plays for an hour across many tracks, a navigation session, a live call. For those, know the `Service` lifecycle:

```text
startForegroundService(intent)   ──▶  onCreate()  ──▶  onStartCommand()
                                                            │  (you have ~5s to call
                                                            │   startForeground() or you CRASH
                                                            │   with ForegroundServiceDidNotStartInTime)
                                                            ▼
                                              RUNNING (foreground, notification shown)
                                                            │
   stopSelf() / stopService()  ──▶  onDestroy()             │
```

Two crash-class facts: (1) if you call `startForegroundService` you **must** call `startForeground` within ~5 seconds or the OS kills you with a `ForegroundServiceDidNotStartInTime` exception; (2) the Android-14 type+permission rule (above) applies to the raw `Service` too — the `android:foregroundServiceType` and matching permission are mandatory. `onStartCommand` returns a restart policy (`START_STICKY` to be recreated if killed, `START_NOT_STICKY` to not); `onBind` returns `null` for a started (non-bound) service. The senior guidance stands: **prefer WorkManager's `setForeground` for work-shaped tasks; reach for a raw `Service` only for genuinely ongoing, work-independent sessions** — and even then, declare the type or crash.

---

## 3. Exact alarms — and why Android 12 made them a last resort

An **exact alarm** fires at a precise wall-clock time, even through Doze. `AlarmManager` offers inexact alarms (the OS batches them to save battery — fine for most reminders) and **exact** alarms:

```kotlin
val alarmManager = context.getSystemService(AlarmManager::class.java)

// setExactAndAllowWhileIdle: fires at the exact time, even in Doze. The strongest, rarest tool.
alarmManager.setExactAndAllowWhileIdle(
    AlarmManager.RTC_WAKEUP,
    triggerAtMillis,                 // the exact wall-clock moment
    pendingIntent                    // what to fire
)
```

**The Android 12 (API 31) regime — exact alarms are now privileged.** Setting an exact alarm requires a permission, and which one matters:

- **`SCHEDULE_EXACT_ALARM`** — the general exact-alarm permission. On Android 12+ it's *granted by default but revocable by the user*; on Android 13+ (and especially 14) it's **denied by default for most apps**, and the user must grant it in Settings. Worse: **Google Play restricts which apps may declare it** — your app must have a core use case that genuinely needs exact timing (alarm clock, calendar, reminders), or Play will reject the listing.
- **`USE_EXACT_ALARM`** — a narrower, *normal* permission (no user prompt) but **only allowed for apps whose core function is alarms/calendars/reminders**. Declare it falsely and Play rejects you.

Before scheduling, you must check at runtime:

```kotlin
if (alarmManager.canScheduleExactAlarms()) {
    alarmManager.setExactAndAllowWhileIdle(...)
} else {
    // Fall back: an inexact alarm, or WorkManager, or send the user to Settings to grant it.
    // NEVER assume you have the permission — it can be revoked at any time.
}
```

**Why this is a last resort.** The whole point of the Android 12 regime is that exact alarms wake the device at precise moments, which is *expensive* for battery, so the platform reserves them for cases where the *user explicitly chose a time*. The senior judgment: **if the user didn't pick a specific clock time, you don't need an exact alarm.** "Remind me to drink water periodically" → WorkManager (deferrable). "Wake me at 6:30 AM" → exact alarm (user-chosen moment). Most "I need an exact alarm" instincts fail that test — and reaching for one when you don't need it is a code-review red flag and a Play-rejection risk.

### What fires, and surviving reboot

An alarm doesn't "run code" directly — it fires a **`PendingIntent`**, which typically targets a `BroadcastReceiver` that does the (short) work or kicks off a WorkManager job:

```kotlin
// The alarm fires this PendingIntent at the scheduled time.
val intent = Intent(context, AlarmReceiver::class.java)
val pendingIntent = PendingIntent.getBroadcast(
    context, requestCode, intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE   // IMMUTABLE required on API 31+
)

// The receiver that runs when the alarm fires:
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Keep this SHORT — onReceive runs on the main thread with a ~10s budget.
        // For real work, enqueue a WorkManager job here, don't do it inline.
        showReminderNotification(context)
    }
}
```

Two facts that bite people: (1) on API 31+ a `PendingIntent` **must** be explicitly `FLAG_IMMUTABLE` or `FLAG_MUTABLE` — omitting it is a crash. (2) `onReceive` runs on the **main thread** with a short budget (~10 seconds) — do *not* do real work there; show a notification or enqueue WorkManager and return.

And the durability gap: **alarms do not survive a reboot.** When the device restarts, all your scheduled alarms are gone — unlike WorkManager, which persists to disk and reschedules itself across reboot. If your exact alarm must survive a reboot, you register a `BOOT_COMPLETED` receiver (with the `RECEIVE_BOOT_COMPLETED` permission) and re-schedule the alarm there. This extra fragility is one more reason exact alarms are a last resort — WorkManager gives you reboot-durability for free, and an alarm makes you re-implement it.

---

## 4. Doze and App Standby — the power regimes that defer everything

These are the *reasons* ordinary background work waits, and understanding them is what lets you stop blaming WorkManager.

**Doze.** When the device is unplugged, stationary, and the screen is off for a while, it enters **Doze**: the OS suspends most background activity to save battery. Network access is cut off, alarms are deferred, jobs are held. Periodically, Doze opens a **maintenance window** — a brief period where deferred work runs in a batch — then returns to sleep, with the windows growing further apart the longer the device idles. So ordinary WorkManager work in Doze doesn't run *when scheduled*; it runs *in the next maintenance window*.

What's **exempt** during Doze (i.e. can still run/fire):

- **Foreground services** (the user is aware — §2).
- **`setExactAndAllowWhileIdle`** alarms (the "AllowWhileIdle" is exactly this exemption — §3).
- **High-priority FCM messages** (a server-pushed urgent message can wake the app — this is why real-time chat uses FCM, not polling; full FCM is capstone material).

What's **deferred**: ordinary background work, ordinary jobs, inexact alarms, network access. This is *by design* — it's how Android gets multi-day standby battery.

**App Standby.** Independently, the OS buckets each app by how recently/often the user uses it:

- **active** — in use right now (no restrictions).
- **working set** — used regularly (light restrictions).
- **frequent** — used often but not daily.
- **rare** — rarely used (heavy restrictions: jobs and syncs throttled, expedited quota tiny).
- **restricted** — the most aggressive bucket (Android 9+); near-total background restriction.

The lower the bucket, the harder your background work and expedited quota are throttled. An app the user hasn't opened in two weeks sits in `rare`/`restricted`, and its sync runs maybe once a day. You can't fight this — and you shouldn't; it's the user telling the OS they don't care about your app right now.

**Battery-optimization exemptions — the last resort you rarely earn.** An app can ask the user to exempt it from battery optimization (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), which loosens Doze/standby for it. **Do not reach for this casually.** Google Play restricts which app categories may request it; for most apps it's a policy violation, and for the user it's a battery drain. The cases that legitimately need it are narrow (some companion/communication apps). For everything this course builds, the answer is: **design within the power rules, don't ask to escape them.** If your sync "needs" an exemption, your design is wrong — make the work deferrable and let it run in maintenance windows.

You can *observe* all of this with `adb`: `adb shell dumpsys deviceidle force-idle` drops the device into Doze so you can watch ordinary work get deferred; `adb shell am set-standby-bucket <pkg> rare` forces a bucket. Exercise and challenge use these to make the invisible visible.

The Doze timeline, so you can picture *when* your work runs:

```text
screen off, unplugged, stationary
        │
        ▼  (after a while)
   ┌─────────── DOZE (deep sleep) ───────────┐  ordinary work HELD
   │  maintenance     DOZE      maintenance  │  network cut
   │   window  ───▶  (longer) ───▶ window    │  exempt work (FGS, allow-while-idle, hi-pri FCM) still fires
   └──────────────────────────────────────────┘
   ▲ windows grow further apart the longer it idles
```

Your "every 6 hours" sync doesn't run on a 6-hour clock during Doze — it runs in whichever maintenance window comes after 6 hours have elapsed *and* the device happens to be awake. That's the imprecision you accept for deferrable work, and it's why "my periodic job didn't run on time" is never a bug to file — it's Doze working as designed.

### The whole toolbox in one table

| Tool | Runs in Doze? | Survives reboot? | Permission burden (2026) | Use for |
|---|---|---|---|---|
| Ordinary WorkManager | deferred to maintenance window | yes (persisted) | none | deferrable durable work (the default) |
| WorkManager + `setForeground` | yes (while promoted) | yes | FGS type + permission | user-aware sync with a notification |
| Raw foreground `Service` | yes | via `START_STICKY`, manual | FGS type + permission | ongoing user-aware sessions (media, nav) |
| Exact alarm (`setExactAndAllowWhileIdle`) | yes | **no** (re-schedule on boot) | `SCHEDULE_EXACT_ALARM`, Play-scrutinized | user-chosen precise moments only |
| High-priority FCM | yes | n/a (server-pushed) | none (server-side) | server-initiated urgent wake (capstone) |

Read top to bottom as a power gradient: the higher rows are weaker and cheaper (and the default); the lower rows punch harder through the power rules but cost permissions, compliance, and battery. The framework's whole design — and this week's whole judgment — is "stay as high in this table as the requirement allows."

---

## 5. A worked decision — "send the user a daily digest"

Run the framework once, end to end, the way you would on a real ticket: *"We want to send the user a daily digest notification."* What's the tool?

**Step 1 — categorize the work.** Is it deferrable? Mostly — "sometime in the morning" is fine; it doesn't need to be 8:00:00 exactly. Is the user actively waiting on it right now? No — they're not watching. Did the user pick an exact clock time? Usually no (and if your product *lets* them pick "9:00 AM sharp," that changes the answer — see step 4).

**Step 2 — apply the table.** Deferrable, not user-aware-right-now, not a user-chosen exact moment → **WorkManager**, periodic, with a network constraint (it fetches the digest content), `KEEP` unique work so you don't double-schedule.

```kotlin
val digest = PeriodicWorkRequestBuilder<DigestWorker>(1, TimeUnit.DAYS)
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .build()
WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
    "daily-digest", ExistingPeriodicWorkPolicy.KEEP, digest)
```

**Step 3 — accept the imprecision.** It'll fire roughly daily, in a maintenance window, when there's network. "Roughly in the morning" is satisfied; you did *not* need an exact alarm, and using one would've been a Play-policy risk for no benefit.

**Step 4 — the exception that proves the rule.** *If* the product requirement were "fire at exactly the minute the user set, like an alarm clock," *then* — and only then — it's an exact alarm, you'd declare `SCHEDULE_EXACT_ALARM`, check `canScheduleExactAlarms()`, and accept the Play scrutiny because the user genuinely chose a precise moment. The *requirement* (user-chosen exact time) is what justifies the *power*, never the other way around.

That is the entire senior judgment of the week: **categorize the work, pick the least-powerful tool the table allows, and escalate only when the requirement — not your convenience — demands it.**

## 5b. A second worked decision — "back up the user's photos"

Run it once more, because the muscle is in the repetition: *"Back up the user's photos to our cloud."* This one has a twist that tests whether you really hold the framework.

**Step 1 — categorize.** Deferrable? Yes — photos can back up "eventually," ideally overnight on Wi-Fi while charging. User actively waiting? Not normally — they took the photo and moved on. User-chosen exact time? No.

**Step 2 — apply the table.** Deferrable + durable → **WorkManager**, with strong constraints to be a good citizen:

```kotlin
val backup = PeriodicWorkRequestBuilder<PhotoBackupWorker>(1, TimeUnit.DAYS)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)   // Wi-Fi only — don't burn cellular on photos
            .setRequiresCharging(true)                       // overnight, plugged in
            .setRequiresStorageNotLow(true)
            .build()
    )
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
    .build()
WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
    "photo-backup", ExistingPeriodicWorkPolicy.KEEP, backup)
```

**Step 3 — the twist: "but the user tapped *Back up now*."** Now the user *is* actively waiting — they pressed a button and expect to see progress. Does that make it a foreground service? Not a *raw* one — it makes it a case for **WorkManager's `setForeground` promotion**: enqueue an *expedited* one-time backup, and have the worker `setForeground` with a `dataSync`-typed notification so the user sees progress. Same engine, promoted because the user is now aware. You did *not* hand-write a `Service`, and you did *not* reach for an exact alarm — you used the same WorkManager work and escalated *one notch* (foreground promotion) exactly when the requirement (user awareness) appeared.

**Step 4 — what you did NOT do.** You didn't request a battery-optimization exemption "to make sure backups happen" — the `UNMETERED`+charging constraints mean it runs overnight anyway, in maintenance windows, which is fine. You didn't use an exact alarm. You stayed as high in the power table as each requirement allowed, escalating only the one button-press case by exactly one notch. That discipline — *minimum power, escalate per requirement* — is the whole week in one feature.

---

## 6. Recap

Lecture 1 was WorkManager, the default. This lecture was the rest of the toolbox and the judgment to use it. Three habits carry it:

1. **Choose the least powerful tool.** Deferrable+durable → WorkManager. User-aware+ongoing → foreground service. Precise+user-chosen → exact alarm. The requirement justifies the power, never your convenience.
2. **Android 14 foreground services crash if you get the type wrong.** Declare the `foregroundServiceType`, hold the matching permission, pass the type to `startForeground`/`ForegroundInfo`. For data sync, use WorkManager's `setForeground` promotion, not a raw `Service`.
3. **The power regimes are by design, not bugs.** Doze defers ordinary work to maintenance windows; App Standby throttles unused apps; exact alarms and battery exemptions punch through but are permission-gated, Play-scrutinized last resorts. Design *within* the rules.

And the anti-patterns a senior reviewer rejects on sight, gathered in one place so they're reflexes:

- An **exact alarm for a sync or a refresh** — that's deferrable work; it's WorkManager. Exact alarms are user-chosen *moments* only.
- A **battery-optimization exemption "to make sure it runs"** — almost always a Play-policy risk and a battery sin; redesign the work to be deferrable instead.
- A **foreground service without its declared type** on Android 14+ — an instant crash at promotion.
- A **`PendingIntent` without `FLAG_IMMUTABLE`/`FLAG_MUTABLE`** on API 31+ — an instant crash.
- **Real work in a `BroadcastReceiver.onReceive`** — it's main-thread, ~10s budget; enqueue WorkManager and return.
- **Assuming `canScheduleExactAlarms()` is true** — it's revocable and denied-by-default; always check and fall back.

Each of those is the same failure: more power than the requirement earns, or a current-SDK rule ignored. Catch them in your own code before review does.

A final note on how this area *moves*. Background-work restrictions have tightened every single Android release since Marshmallow (2015): Doze and App Standby (M), background-execution limits (O), exact-alarm permissions (S/12), foreground-service-type enforcement (14). The direction is always the same — **more restriction, in service of battery and the user's control** — and it will keep going. So the durable skill isn't memorizing the current rule set (which expires); it's the *judgment*: categorize the work, use the least power, design within the regime rather than fighting it, and read the current release notes when you target a new SDK. An engineer who internalizes "the platform is steadily reclaiming background freedom, and my job is to need as little of it as possible" stays correct across releases; one who memorizes today's exact API surface is out of date in a year. The framework in §1 is the part that lasts.

You now have the whole background-work picture: the durable engine (WorkManager, lecture 1), the two escalations (foreground services, exact alarms), and the power regimes that govern all of them. The exercises drill constraints+backoff, a compliant foreground-promoting worker, and a deterministic WorkManager test. The challenge makes you diagnose four "why didn't my work run" scenarios. The mini-project builds an offline-first sync engine that is deferrable WorkManager work, promotes to the foreground only when justified, never touches an exact alarm, and is tested green — durable background work that respects the platform and crashes on no device. That's the senior bar, and it's the spine of the capstone's sync feature.
