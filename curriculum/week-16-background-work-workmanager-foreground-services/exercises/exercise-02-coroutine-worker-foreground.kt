// Exercise 2 — A foreground-promoting CoroutineWorker, Android-14-compliant
//
// Goal: Write a CoroutineWorker that promotes itself to the foreground with a
//       progress notification and the correct `dataSync` foreground service type.
//       Declare the type + permission in the manifest, pass the type to
//       ForegroundInfo, and confirm it does NOT crash on Android 14 (targetSdk 35).
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// Runs on an emulator (Pixel 8 API 35). Put the worker in app/src/main, add the
// MANIFEST entries shown below (they're REQUIRED or it crashes), and enqueue the
// worker from a button. Watch the notification appear and the work complete.
//
//   1. Add the manifest permissions + service type (see the block below).
//   2. Implement the TODOs in getForegroundInfo() and doWork().
//   3. Enqueue it; confirm the progress notification shows and no crash occurs.
//
// ACCEPTANCE CRITERIA
//
//   [ ] The manifest declares FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC and
//       the worker's foregroundServiceType is dataSync (or WorkManager's default fgs).
//   [ ] getForegroundInfo() returns a ForegroundInfo with the DATA_SYNC type.
//   [ ] doWork() calls setForeground(getForegroundInfo()) and updates progress.
//   [ ] It runs to completion on targetSdk 35 with NO crash at promotion.
//   [ ] Builds with 0 warnings.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

package com.crunch.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

// ----------------------------------------------------------------------------
// REQUIRED MANIFEST ENTRIES (add to AndroidManifest.xml — NOT in this file).
// Without these, startForeground/setForeground CRASHES on Android 14 (lecture 2, §2).
//
//   <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
//   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
//   <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
//
//   <!-- WorkManager's own foreground service must also declare the type on API 34+.
//        Override its <service> entry's foregroundServiceType, or set it via the
//        ForegroundInfo type below (the modern path). -->
//
//   <service
//       android:name="androidx.work.impl.foreground.SystemForegroundService"
//       android:foregroundServiceType="dataSync"
//       tools:node="merge" />
// ----------------------------------------------------------------------------

private const val CHANNEL_ID = "sync_channel"
private const val NOTIFICATION_ID = 1001

class ForegroundSyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ensureChannel(context)

        // TODO 1: promote this worker to the foreground BEFORE the long work, so the
        //   user sees a notification while it runs.
        //   setForeground(getForegroundInfo())

        // Simulate a 10-step sync, updating the notification progress each step.
        for (step in 0..10) {
            // TODO 2: update the foreground notification to show `step * 10`% progress.
            //   setForeground(createForegroundInfo(progress = step * 10))
            delay(500)   // pretend each step does real I/O
        }
        return Result.success()
    }

    // getForegroundInfo() is what WorkManager calls if it needs to run this worker as
    // an expedited foreground job; it MUST carry the foreground service type on API 34+.
    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(progress = 0)

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Syncing")
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

        // TODO 3: return a ForegroundInfo that, on Android 10+ (API 29+), passes the
        //   DATA_SYNC foreground service type. On older APIs the 2-arg constructor is used.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                // TODO 3 (cont.): replace 0 with ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                0   // <- replace 0 with the DATA_SYNC type constant
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}

private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Sync", NotificationManager.IMPORTANCE_LOW
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}

// ----------------------------------------------------------------------------
// ENQUEUE IT (from an Activity/button):
//
//   val request = OneTimeWorkRequestBuilder<ForegroundSyncWorker>()
//       .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
//       .build()
//   WorkManager.getInstance(context).enqueue(request)
//
// On Android 13+ you must also REQUEST the POST_NOTIFICATIONS runtime permission for
// the notification to show. Grant it via the system dialog or Settings.
// ----------------------------------------------------------------------------
// WHY each piece is required (write it before reading):
//
//   - The manifest FOREGROUND_SERVICE_DATA_SYNC permission + the foregroundServiceType
//     on the WorkManager service: Android 14 REQUIRES a declared type and matching
//     permission, or setForeground throws (lecture 2, §2). This is the #1 Android-14
//     foreground-service crash.
//   - The DATA_SYNC type in ForegroundInfo: tells the OS what kind of foreground work
//     this is, which it cross-checks against the manifest permission.
//   - setForeground(...) BEFORE the work: promotes the worker so the user sees the
//     notification while it runs — the legitimate "user is now aware" path.
//
//   The senior point: this is the RIGHT way to do user-aware sync — WorkManager's
//   durability + a foreground notification — instead of hand-writing a raw Service.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - Crash: "Starting FGS with type dataSync ... requires permission" — you didn't add
//   FOREGROUND_SERVICE_DATA_SYNC to the manifest, or the WorkManager <service> entry
//   lacks android:foregroundServiceType="dataSync". Both are required on API 34+.
//
// - TODO 3: the constant is ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC. Import
//   android.content.pm.ServiceInfo (already imported above).
//
// - No notification shows on Android 13+: you didn't grant POST_NOTIFICATIONS. It's a
//   runtime permission now — request it before enqueueing, or grant it in Settings.
//
// - "setForeground was called from a worker that isn't running as foreground" warning:
//   that's fine for ordinary enqueue; setExpedited makes it run as a foreground job so
//   the promotion is clean. Either way it shouldn't crash with the type declared.
//
// - dataSync is TIME-LIMITED on Android 14 — for a long real sync, that's a reason to
//   prefer ordinary deferrable WorkManager and only promote when the user opens the
//   app (the mini-project's design), not to run dataSync foreground indefinitely.
//
// ----------------------------------------------------------------------------
