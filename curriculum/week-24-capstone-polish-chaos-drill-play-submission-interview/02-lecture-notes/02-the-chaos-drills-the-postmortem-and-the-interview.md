# Lecture 2 — The chaos drills, the postmortem, and the interview

> "App review confirms your app launches. It does not confirm your sync resolves a conflict without losing an edit, that a rotated FCM token re-registers before a message is dropped, or that sign-in degrades gracefully on a device without Play Services. The only way to know is to cause those failures on purpose, while watching."

Lecture 1 got the capstone through Google's gate. This lecture is about the thing the gate never checks — **does the system survive a real failure** — and the thing that turns a shipped capstone into a job: the senior-Android interview. We build it in four parts. First, **what a chaos drill is** and why you run all three. Second, the **three drills end to end** — offline-sync conflict, FCM token rotation, Play Integrity attestation failure — each with the injection, the measurement, and the recovery bar. Third, the **blameless postmortem** and the **five-minute walkthrough**. Fourth, the **senior-Android interview drills** — the six topics the syllabus requires, with the shape of a strong answer for each.

There is a reason this is the *final* lecture of the entire track, and it is not just chronology. The whole course has been building toward one competence: not "can you write Compose" but "can you ship and operate a real system, and explain it to a staff engineer." The chaos drills test the operating; the interview tests the explaining. Everything before this week earned the right to fail safely and to speak fluently; this week you exercise both.

---

## 1. What a chaos drill is, and why all three

Chaos engineering is the discipline of injecting a failure into a running system, on purpose, to learn how the system actually behaves — not how you *think* it behaves. The premise is humbling and correct: you do not know your system's failure behavior until you have seen it fail. A design doc that says "the rotated token re-registers in under a second" is a hypothesis; a drill that measures the gap between rotation and re-registration is data. The gap between the two is exactly what the drill exists to find.

Unlike the Swift capstone (which runs one drill), the Field-Force Companion requires **all three**, because each exercises a *different* contract the syllabus considers load-bearing for a senior Android engineer:

- **Drill A (offline-sync conflict)** exercises the offline-first sync and conflict-resolution contract — the spine of the whole app (ADR-0001, ADR-0003).
- **Drill B (FCM token rotation)** exercises the push pipeline's resilience — the contract that no message is silently dropped when a token rotates mid-session.
- **Drill C (Play Integrity attestation failure)** exercises the security gate's graceful degradation — the no-brick, no-fail-open design (ADR-0004).

Three properties make each drill worth the name:

1. **The failure is real and injected on purpose** — not simulated in a unit test, but caused in the running system (two real devices editing offline, a real token rotation, a real Play-Services-less device).
2. **You measure** — detection time (when did you know), recovery time (when was it healthy again), and the data-correctness verdict (did anything get lost, dropped, or corrupted).
3. **You document the gap** — between what you expected and what happened, because the surprise is the valuable part.

You run the drills *this* week, after the build is locked and on the closed track, because a drill is only meaningful against a system that is actually running with real observability. Drilling a half-integrated system tests the integration, not the resilience.

A word on the relationship between the deterministic exercises and the live drills, because students conflate them. The `.kt` exercises (2 and 3) are **deterministic proofs of correctness** — they model the system, drive the failure, and assert the contract (convergence, no-drop) with zero flakiness, runnable in CI forever. The **live drills** are *measurements of the real system under real conditions* — they drive the failure on actual devices and measure latency, propagation, and the gaps the model cannot see. You need both: the exercise proves the policy is *correct*, and the live drill proves the implementation *honors the policy at real-world latency and surfaces the real surprises*. A team that only runs the exercise has proven the design; a team that only runs the live drill has anecdotes without a contract. The capstone wants the contract *and* the measurement, which is why each drill has both an exercise and a live run.

---

## 2. The three drills, end to end

### Drill A — Offline-sync conflict resolution

**The scenario.** Two devices edit the same dispatch while offline; both reconnect within 60 seconds of each other. This exercises ADR-0003's conflict policy under real conditions.

**The injection.** Two emulators (or two devices), both signed in, both showing the same dispatch. Take both offline (`adb shell svc data disable`, or airplane mode). On device 1, advance the status to `OnSite`; on device 2, advance the *same* dispatch to `Done` and add a note. Bring both back online within 60 seconds.

**What you measure.** The time from reconnect to convergence (both devices showing the same resolved dispatch), and the data verdict: under last-writer-wins by server timestamp (the reference ADR-0003), the later write wins the status field, and the note (a different field) survives via field-level merge. Did the note survive? Was the status loss the *expected* one (the later timestamp), or did a write vanish unexpectedly? Exercise 2 automates the offline-edit-reconnect-converge sequence and asserts the contract.

**The recovery bar.** Both devices converge to the *same* dispatch (the determinism contract), and the loss is exactly the documented one — a same-field LWW conflict resolves to the later edit, and a different-field edit survives. Zero *unexpected* loss. The postmortem documents the policy, why you picked it, and the measured convergence latency.

**The gotcha this drill teaches.** "Reconnect within 60 seconds" is not "converge within 60 seconds." The gRPC sync to the second device has latency — the second device must pull (or be pushed) the server's converged state, and under load that propagation can lag the reconnect by seconds. If your convergence SLO budgeted only for the merge computation (microseconds) and not the propagation (seconds), the drill blows past it, and the finding is "budget for propagation, not just merge." That is a genuinely useful surprise you only learn by running the drill.

**Driving it with adb.** The live injection is two emulators and the network toggle:

```bash
# two emulators, both signed in, both showing dispatch d1.
adb -s emulator-5554 shell svc data disable   # device 1 offline
adb -s emulator-5556 shell svc data disable   # device 2 offline

# (in the app) device 1: advance d1 to OnSite. device 2: advance d1 to Done + add a note.

adb -s emulator-5554 shell svc data enable     # device 1 reconnects
adb -s emulator-5556 shell svc data enable     # device 2 reconnects (within 60s)

# measure: t_reconnect on each, t_converge when both show the same resolved d1.
```

The measured timeline you record (the postmortem's spine): `t0` (steady state, both showing Assigned), `t_fault` (both offline + edited), `t_reconnect` (data re-enabled), `t_detect` (the second device receives the server's update), `t_converge` (both identical). The data verdict: the status resolved to the later write (Done), the note survived (different field), and zero different-field edits were lost. Exercise 2 proves this contract deterministically with a modeled server and two replicas; the live drill measures the real propagation latency that the deterministic test cannot see.

### Drill B — FCM token rotation

**The scenario.** Firebase Cloud Messaging rotates a device's registration token mid-session (it does this on app reinstall, data clear, or periodically for security). If your app does not re-register the new token with your backend promptly, the backend keeps sending to the *old* token, and those messages are silently dropped. This drill proves the path from rotation to re-registration leaves no message behind.

**The injection.** Force a token rotation. You can delete the token (`FirebaseMessaging.getInstance().deleteToken()`), which causes FCM to issue a new one and fire `onNewToken`; or clear the app's FCM data; or use the Firebase console / FCM v1 API to send a test message immediately after rotation to probe the window. The realistic injection is: rotate the token, then send a dispatch-update push *during* the re-registration window, and confirm it is delivered (or re-delivered), not dropped.

**What you measure.** The time from `onNewToken` firing to your backend acknowledging the re-registration, and the verdict: was the message sent during the window delivered? The contract is **no message silently dropped** — either the message lands on the new token, or it is queued/retried so it is not lost.

**The recovery bar.** `onNewToken` triggers an immediate, *retryable* re-registration call to your backend (it must survive a network blip — enqueue it through the same outbox/WorkManager path your dispatch writes use, not a fire-and-forget call). The backend updates the device's token record. A message sent during the window is either delivered on the new token or retried after re-registration. The postmortem documents the window and proves no drop.

**The gotcha this drill teaches.** The naive `onNewToken` implementation makes a *single, non-retryable* network call to register the token, and if that call fails (the rotation often happens exactly when connectivity is flaky), the new token never reaches the backend and *every* subsequent message is dropped until the next rotation — a silent, total push outage. The fix is to route re-registration through the same durable, retryable path as your data writes (the outbox + WorkManager), so a failed registration retries with backoff. The drill surfaces this by rotating the token under a flaky network and watching whether the backend ever learns the new token. Exercise 3 pins this contract: a failed registration must retry, not drop.

**The footgun and the fix, in code.** The wrong way and the right way are five lines apart:

```kotlin
// THE FOOTGUN — a single fire-and-forget call. A failure during rotation
// (when connectivity is often flaky) drops the token; push goes dark.
class FieldForceMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        api.registerToken(token)        // one call, no retry — if it fails, silent outage
    }
}

// THE FIX — enqueue re-registration through WorkManager, the same durable,
// retryable path the dispatch writes use. A failure retries with backoff.
class FieldForceMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val work = OneTimeWorkRequestBuilder<RegisterTokenWorker>()
            .setInputData(workDataOf("token" to token))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork("register_token", ExistingWorkPolicy.REPLACE, work)
    }
}
```

The `ExistingWorkPolicy.REPLACE` matters: if a second rotation happens before the first registration lands, you want the *latest* token registered, not a queue of stale ones. The `CONNECTED` constraint means WorkManager waits for connectivity instead of failing immediately. Together they turn a fragile fire-and-forget into a guaranteed-eventually registration — the same reliability your dispatch writes already have, applied to the token.

**Driving it.** Force the rotation and probe the window:

```bash
# force a new token (in a debug-only entry point or via the app's dev menu):
#   FirebaseMessaging.getInstance().deleteToken()  // triggers onNewToken with a fresh token
# then, during the re-registration window, send a test push from the FCM v1 API
# and confirm it lands (or is re-delivered), not dropped.
```

The verdict: a message sent during the window is delivered after re-registration; the backend's token record updates; no push is silently lost. The postmortem's likely surprise is the *detection* gap — you only knew the token rotated because you forced it; in production, a silent push outage would surface only via a user complaint, so the action item is a re-registration-success metric (the §6 Lecture 1 app-level signal).

### Drill C — Play Integrity attestation failure

**The scenario.** The capstone runs on an emulator (or device) without Google Play Services — a de-Googled phone, an AOSP image, an enterprise device. Play Integrity cannot attest. This drill proves sign-in degrades gracefully: a clear user-facing message and a documented fallback, never a silent fail-open and never a hard brick.

**The injection.** Run the closed-track build on an **AOSP emulator image** (no Google APIs). Attempt sign-in. Watch what the Play Integrity gate does when `isPlayServicesAvailable()` returns false.

**What you measure.** The outcome: does sign-in return `PlayServicesUnavailable` and surface a fallback (web sign-in / managed device), or does it hang, crash, or — worse — fail open and let an unattested device in? The verdict is binary against the ADR-0004 contract: graceful message + fallback = pass; brick or fail-open = fail.

**The recovery bar.** The `PlayServicesUnavailable` branch (Week 23 Exercise 3) fires, the UI shows the fallback, and a legitimate user on a Play-Services-less device can still reach the app via the documented path — while an attacker cannot bypass the control by simply removing Play Services. The postmortem documents the branch and the fallback.

**The gotcha this drill teaches.** Teams discover their attestation gate was never tested on a Play-Services-less device, because their dev machines all have Play Services. The drill is the first time the `PlayServicesUnavailable` path executes for real, and it frequently surfaces a `NullPointerException` or an infinite spinner where the graceful branch was supposed to be. Building the branch in Week 23 and *running it* this week is the difference between a design that claims to degrade gracefully and one that does.

**Driving it.** The injection is the device, not a command — you run the *same closed-track build* on an emulator image with no Google APIs:

```bash
# create an AOSP (no Google Play) emulator — the "Google APIs" / "Play Store"
# images HAVE Play Services; pick a plain AOSP system image instead.
sdkmanager "system-images;android-34;default;arm64-v8a"   # 'default' = AOSP, no GMS
avdmanager create avd -n aosp34 -k "system-images;android-34;default;arm64-v8a"
emulator -avd aosp34
# install the closed-track build, attempt sign-in, observe the gate's branch.
```

The verdict is binary against ADR-0004: does sign-in return `PlayServicesUnavailable` and surface the documented fallback, or does it hang/crash/fail-open? The first time the `isPlayServicesAvailable()` check returns false for real is the moment you learn whether your graceful branch actually works. If it crashes, that crash *is* the finding — and fixing it (the null-safe fallback) is the drill's value. The postmortem documents the branch, the fallback path a legitimate Play-Services-less user takes, and the confirmation that an attacker cannot bypass attestation by simply removing Play Services (the fallback is a *different* auth path, not an unguarded one).

---

## 3. The blameless postmortem

For each drill you write a **blameless postmortem** — the structure an incident review accepts. The syllabus requires five sections, and the tone is load-bearing:

```text
POSTMORTEM — <drill name>

  Timeline.        t0 (steady state), t_fault (injection), t_detect (when you knew),
                   t_recover (when healthy again). Real timestamps, not "quickly."
  Root cause.      The system reason the failure behaved as it did — the mechanism,
                   not "the network was bad." E.g. "re-registration was a single
                   non-retryable call, so a blip during rotation dropped the token."
  Blast radius.    What was affected and for how long. "All pushes to device 2 from
                   t_fault to t_recover (94s) would have been dropped."
  What we changed. The fix, shipped or planned, tagged accept / fix-now / fix-later.
  What we'd do      With another week: the deeper fix you didn't have time for.
   differently.
```

The **blameless** discipline is the test that makes a postmortem credible: replace the person with the system component. "I forgot to make re-registration retryable" becomes "the re-registration path had no enforced retry." If the rewritten sentence points at a *system fix* (route it through WorkManager), it belongs in the postmortem; if it only points at a person, it is blame, not analysis. The reason this matters beyond etiquette: a blameless postmortem produces *systemic* fixes that prevent the whole class of failure, where a blameful one produces "be more careful," which prevents nothing.

A strong postmortem names a **surprise** — recovery succeeding is the least interesting part. If drill B recovered but you discovered you had no *detection* path (you only knew the token rotated because you injected it, not because anything alerted), that gap is the finding, and "add a re-registration success metric / a synthetic push prober" is the action item. The surprise is the value; the timeline is just the evidence.

Each postmortem links from the capstone README, and all three are required for completion (5% of the course grade, all-three gating).

Here is a worked postmortem for drill B, the shape yours should take:

```markdown
# Postmortem — FCM token rotation (Drill B)

## Summary
Forced an FCM token rotation under a throttled network. The naive re-registration
dropped the new token; a push sent during the window would have been lost. Fixed by
routing re-registration through WorkManager. No production impact (closed beta).

## Timeline (2026-06-10, device emulator-5554)
- t0     14:02:00  Steady state: push prober delivering, token-old registered.
- t_fault 14:02:30 Injected: FirebaseMessaging.deleteToken() under a throttled
                   network (adb shell settings put global ...). onNewToken fired.
- t_detect 14:04:10 The push prober's message did NOT arrive — first signal that
                   the backend still held token-old. (Detection by prober, ~100s.)
- t_recover 14:06:45 After the WorkManager fix + retry, the backend registered
                   token-new; the queued prober message delivered.

## Root cause
Re-registration was a single non-retryable `api.registerToken()` call in
onNewToken. Under the throttled network it failed once and was never retried, so
the backend kept token-old and every push went to a dead token.

## Blast radius
All pushes to this device from t_fault (14:02:30) to t_recover (14:06:45) — 4m15s —
would have been silently dropped in the unfixed version. One device in beta.

## What we changed
- fix-now: route onNewToken through a WorkManager OneTimeWorkRequest with
  EXPONENTIAL backoff and a CONNECTED constraint. (Shipped.)
- fix-now: emit a `token_registration` success metric so a future outage is
  visible without a user report. (Shipped.)

## What we'd do differently with another week
Add a server-side "last-seen token age" alert: if a device's token hasn't
re-registered in N days but the app is active, flag it — catching a rotation that
silently failed even past our retry budget.
```

Notice what makes it strong: real timestamps (not "quickly"), a root cause that is a *system mechanism* (a non-retryable call), a blast radius with a duration, action items that are *systemic* and owned, and a "what we'd do differently" that names a deeper fix the week didn't have room for. The surprise — that detection took 100 seconds via the prober and would have been invisible otherwise — is the most valuable line, and it produced the metric action item.

---

## 4. The five-minute walkthrough

Record a five-minute video that traces one dispatch write end to end, **phone and Wear side by side**, narrating the mechanism at each hop. This is the artifact a hiring manager watches and a peer reproduces. The structure that fits five minutes:

```text
0:00–0:30   What it is. Phone and Wear on screen together. The dispatch list on both.
0:30–2:30   Trace one write OFFLINE. Turn off the network. Advance a dispatch on the
            phone — it updates instantly (Room is the source of truth). Show the
            outbox holding the pending op. Reconnect. Show it sync, and the Wear
            complication update. Force a conflict (drill A) and show it converge.
2:30–3:30   The platform surface. The Wear tile, the complication, the ongoing
            activity for an in-progress dispatch.
3:30–4:30   The security gate. Sign-in with Play Integrity; then show the
            Play-Services-less fallback (drill C) — graceful, not a brick.
4:30–5:00   The resilience. The three chaos drills and the one surprise each
            postmortem surfaced.
```

Pre-stage the data so you are not typing on camera, and keep a fallback recording of the sync step in case live sync stalls during recording. The walkthrough narrates the *mechanism* — "this updates instantly because the write went to Room, not the network" — not the feature. Narrating the mechanism is what signals you understand your own system; narrating the feature is what a demo of someone else's app sounds like.

A pre-flight checklist before you hit record, because re-recording is expensive:

- [ ] Both devices (phone + Wear emulator) visible and signed in, showing the same dispatch list.
- [ ] A dispatch seeded that you will edit, in a known starting state.
- [ ] The network-toggle command ready (`adb shell svc data disable`) so the offline moment is one keystroke.
- [ ] A fallback recording of the sync step, in case live sync stalls on camera.
- [ ] The Play-Services-less emulator booted and on the sign-in screen for the drill-C fallback shot.
- [ ] A script — not word-for-word, but the eight beats — so you narrate the mechanism at each hop, not "um, and here's the screen."
- [ ] Screen recorder confirmed capturing both devices (`adb shell screenrecord`, or the studio recorder per device, composited).

The single most common walkthrough mistake is *demoing features* — "here's the list, here's the detail screen, here's the settings" — which is indistinguishable from a demo of any app. The senior walkthrough *traces one write* and explains why each hop behaves as it does. A hiring manager watching the first kind learns you can build screens; watching the second, they learn you understand a distributed system. The second is the one that gets the callback.

---

## 5. The senior-Android interview drills

The career engineering pack requires six interview drills with worked answers and four mock interviews. Here is the shape of a strong answer for each of the six syllabus topics — not the full answer (that is your homework), but the spine an interviewer is listening for.

- **Compose recomposition phases and stability.** A strong answer names the three phases (composition, layout, draw), explains that recomposition recomposes the smallest scope that *read* the changed state, defines *skippable* (equal params → skip the body) and *stable*, and shows you can read a Compose Compiler report. The senior signal: "an animation should recompose *zero* times — read the animating value in the draw phase." (This is Week 7; you can already say it.)

- **Coroutines pitfalls — three real bugs.** (1) `GlobalScope.launch` leaks a coroutine past the screen's lifecycle — fix: a lifecycle-scoped scope. (2) Swallowing `CancellationException` in a `catch (e: Exception)` breaks cooperative cancellation — fix: rethrow it, or catch a narrower type. (3) A `try/catch` around a `launch` does not catch the child's exception (structured concurrency propagates it to the parent) — fix: a `CoroutineExceptionHandler` or a `supervisorScope`. The senior signal: you have *hit* these, not read them.

- **Cold vs hot flows — when to pick which.** Cold (`flow { }`) runs per-collector, from the start, ideal for a one-shot request. Hot (`StateFlow`/`SharedFlow`) is shared, always-on, ideal for state-of-the-world (`StateFlow`) or events (`SharedFlow(replay=0)`). The senior signal: "most production flow bugs are confusing the two — a `StateFlow` collected expecting a fresh fetch each time, or a cold flow used where you needed shared state." A follow-up they love is **operator selection**, so have these cold:

```kotlin
// flatMapLatest — cancel the previous inner flow when a new value arrives.
// The RIGHT choice for search-as-you-type: a new query cancels the in-flight one.
queryFlow.flatMapLatest { q -> repo.search(q) }

// flatMapConcat — process inner flows in order, one fully before the next.
// The RIGHT choice when ordering matters and you must not drop any: a queue drain.
opsFlow.flatMapConcat { op -> repo.push(op) }

// flatMapMerge — run inner flows concurrently, interleaving results.
// The RIGHT choice for independent parallel work: fetch N resources at once.
idsFlow.flatMapMerge(concurrency = 4) { id -> repo.fetch(id) }
```

The senior signal here is that you pick the operator by the *semantics you need* (cancel-previous vs ordered vs concurrent), not by which one you remember — and that you can name the canonical use of each: `flatMapLatest` for search, `flatMapConcat` for an ordered drain, `flatMapMerge` for bounded parallelism. Picking `flatMapLatest` for the outbox drain (it would cancel pending ops) or `flatMapConcat` for search (it would queue stale queries) are the wrong-tool bugs the question probes.

- **WorkManager vs foreground service vs exact alarm — a design exercise.** WorkManager for deferrable, constraint-aware, guaranteed-eventually work (your sync). A foreground service for user-visible, *now*, ongoing work (an active navigation). An exact alarm *only* for user-facing time-critical events (an alarm clock, a calendar reminder) — and after Android 12 it needs `SCHEDULE_EXACT_ALARM` and a justification. The senior signal: "the most common mistake is reaching for an exact alarm for background sync — it is the wrong tool and Play will question the permission."

- **Mobile system design — WhatsApp's message-send pipeline.** Compose offline-first into a senior answer: the message is written to a local DB immediately (source of truth, instant UI), enqueued in an outbox, sent over a persistent connection (or FCM-woken sync), with a client-generated message ID for idempotency, delivery/read receipts as separate state transitions, and retries with backoff. The senior signal: you handle the offline case, the duplicate-on-retry case (idempotency key), and the ordering case — the same outbox/idempotency pattern your capstone uses.

- **Memory and ANR debugging — read a stack trace out loud.** An ANR stack trace shows the main thread blocked — name what it is blocked on (a synchronous disk read, a long computation, a lock). An OOM is read from the dominator tree in the memory profiler — name what is retaining the memory (a leaked `Activity` held by a static, a `Bitmap` not recycled, a coroutine holding a context). The senior signal: you can isolate the *one* blocking call or the *one* retained reference, not just say "it's slow."

**A worked technical drill — the three coroutine bugs.** Interviewers love these because they separate people who read about coroutines from people who debugged them. Here are the three, with the bug and the fix, so you can say them cold:

```kotlin
// BUG 1 — GlobalScope leaks past the lifecycle. This coroutine outlives the
// screen; if the user navigates away mid-flight, it keeps running and may touch
// dead UI. Classic memory leak + crash.
GlobalScope.launch { val data = repo.load(); render(data) }          // WRONG
// FIX — a lifecycle-scoped scope ties the work to the screen's life.
viewModelScope.launch { val data = repo.load(); _state.value = data } // RIGHT

// BUG 2 — swallowing CancellationException breaks cooperative cancellation. The
// catch eats the cancel signal, so the coroutine keeps going after it was told
// to stop, and structured concurrency can't reason about it.
try { work() } catch (e: Exception) { log(e) }                        // WRONG
// FIX — rethrow CancellationException (or catch a narrower type).
try { work() } catch (e: CancellationException) { throw e }
catch (e: IOException) { log(e) }                                     // RIGHT

// BUG 3 — a try/catch around launch does NOT catch the child's exception;
// structured concurrency propagates it to the parent scope, crashing it.
try { scope.launch { mayThrow() } } catch (e: Exception) { /* never hit */ } // WRONG
// FIX — handle inside the coroutine, or use a CoroutineExceptionHandler /
// supervisorScope so one child's failure doesn't cancel its siblings.
scope.launch { try { mayThrow() } catch (e: IOException) { handle(e) } }     // RIGHT
```

When you walk an interviewer through these, the signal is not that you memorized them — it is that you say *why* each is wrong (the lifecycle, the cancellation contract, the propagation rule) and that you have seen the symptom (a crash after navigation, a coroutine that wouldn't stop, a scope that died for no obvious reason). That is the difference between "I know coroutines" and "I have shipped coroutines," and it is exactly what the technical screen is calibrated to find.

**A worked system-design spine — the WhatsApp send.** When asked to design the message-send pipeline, draw it as your capstone's offline-first pattern, scaled:

```text
  send(text):
    1. write the message to a local DB with state=PENDING and a client-generated
       message_id (UUID) — the UI shows it instantly (single source of truth).
    2. enqueue it in an outbox; a sender (a worker / a persistent socket) drains it.
    3. POST to the server with the message_id (idempotency key) — a retry after a
       lost ack does NOT create a duplicate, because the server dedupes on message_id.
    4. server ACK -> state=SENT; recipient delivery receipt -> state=DELIVERED;
       read receipt -> state=READ. Three separate state transitions, each its own event.
    5. offline? the message stays PENDING in the outbox; FCM wakes the app or the
       socket reconnects, and the outbox drains in order.
```

The senior signal is that you handle the *offline* case (local-first + outbox), the *duplicate-on-retry* case (the idempotency key), and the *ordering* case (the outbox drains in order) — the three failure modes a junior answer skips. You have built all three in the capstone; the interview is where you name them as a pattern.

The four mock interviews — two technical (live Kotlin), one system design, one behavioral — are recorded and reviewed with a written retrospective. The behavioral is not filler: "tell me about a time you shipped something that broke" is your chaos-drill postmortem, told as a story with a systemic fix. You have the material; this week you rehearse the telling.

---

## Where this lands, and where the course ends

You can now drive three real failures on purpose, measure detection and recovery, prove no data is lost and no message dropped, write the blameless postmortems that turn each into a systemic fix, record a five-minute side-by-side walkthrough that narrates the mechanism, and answer the six senior-Android interview drills with the spine an interviewer listens for. With Lecture 1's submission and this lecture's proof, the final week is complete: a reviewed, resilient, demonstrable, interview-ready capstone.

There is no Week 25. After you ship to the closed track, survive all three chaos drills, publish the portfolio, and clear the four mock interviews, you have completed **C21 · Crunch Droid**. You have written Kotlin 2.x with structured concurrency, built Compose at the recomposition level, wired Hilt over Room and gRPC, shipped an offline-first WorkManager sync engine, built a Wear OS companion against a KMP core, generated a Baseline Profile that cut cold start, gated sign-in with Play Integrity, rotated an FCM token under a chaos drill, and landed an app through Play review on the first try. The recommended next tracks are in the [track README](../../README.md). But first: ship this one, survive the drills, present it, and interview. You earned the launch.
