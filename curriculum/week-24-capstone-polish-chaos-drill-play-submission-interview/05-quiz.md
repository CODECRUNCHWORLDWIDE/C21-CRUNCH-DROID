# Week 24 — Quiz

Thirteen questions on Play submission, the three chaos drills, the blameless postmortem, the walkthrough, and the senior-Android interview. Take it with your lecture notes closed. Aim for 11/13. Answer key with explanations at the bottom — don't peek. This is the last quiz of C21.

---

**Q1.** What is the single most common reason a capstone-shaped app is rejected at Play review?

- A) Bad code quality the reviewer reads.
- B) A mismatch between what the app *declares* (Data Safety, permissions, foreground service) and what it actually does.
- C) Too many features.
- D) A slow cold start.

---

**Q2.** Your app bundles Firebase Cloud Messaging and Crashlytics but the Data Safety form says "collects no data." What happens, and what's the fix?

- A) Nothing; the form is optional.
- B) A rejection for a Data Safety mismatch — review's SDK scanning finds the data those SDKs collect. Fix: declare honestly (or remove the SDK), never under-declare.
- C) The app is banned permanently.
- D) Firebase is blocked.

---

**Q3.** Your `:feature-sync` promotes to a foreground service mid-sync. What does Play require at submission?

- A) Nothing special.
- B) A declared foreground-service *type* and a written justification for why it must run now and user-visibly.
- C) A separate paid license.
- D) Removal of the service.

---

**Q4.** Why submit to the Play track on Monday rather than Friday?

- A) Reviewers work Mondays.
- B) Review and processing are an external dependency with variable latency; submitting early means a rejection costs you days you have (and the drills/interviews aren't blocked on the queue) instead of your weekend.
- C) Monday uploads are cheaper.
- D) It doesn't matter.

---

**Q5.** In drill A (offline-sync conflict), two devices edit the *same field* of a dispatch offline. Under last-writer-wins by server timestamp, what's the correct verdict?

- A) Both edits are kept.
- B) The edit with the later server-assigned timestamp wins; both devices converge to it deterministically; the loss is the documented same-field LWW bound.
- C) The app crashes.
- D) A random edit wins.

---

**Q6.** In drill A, two devices edit *different fields* (one the status, one the note). What must happen?

- A) One edit is lost.
- B) Both edits survive via field-level merge — different fields don't clobber each other — and both devices converge identically.
- C) The later device wins both fields.
- D) The server rejects the second edit.

---

**Q7.** The gotcha drill A teaches is that "reconnect within 60s" is not the same as what?

- A) "Edit within 60s."
- B) "Converge within 60s" — gRPC propagation to the second device adds latency the merge computation doesn't, so an SLO that budgeted only the merge cost blows past it.
- C) "Sign in within 60s."
- D) "Crash within 60s."

---

**Q8.** In drill B (FCM token rotation), the naive `onNewToken` makes a single non-retryable call to register the new token. What's the failure?

- A) Nothing — a single call is fine.
- B) If the call fails (and rotation often coincides with flaky connectivity), the backend never learns the new token, and *every* subsequent push is silently dropped until the next rotation — a total push outage.
- C) The app crashes.
- D) The old token keeps working.

---

**Q9.** What's the fix for the drill-B footgun?

- A) Retry the call once inline.
- B) Route re-registration through the same durable, retryable path as data writes (a WorkManager job with exponential backoff), so a transient failure retries and the token eventually lands.
- C) Disable token rotation.
- D) Poll for a new token every minute.

---

**Q10.** In drill C, the capstone runs on an emulator with no Google Play Services. What's the correct behavior?

- A) Hard-require attestation — block sign-in with a crash or hang.
- B) Return `PlayServicesUnavailable` and offer a documented fallback (web/managed-device) — never a brick, never a fail-open.
- C) Let everyone in (fail open).
- D) Show a blank screen.

---

**Q11.** What makes a postmortem "blameless," and why does it matter?

- A) It hides who caused the incident.
- B) It replaces the person with the system component ("I forgot X" → "the process had no enforced X"); this produces *systemic* fixes that prevent the whole class of failure, where blame produces "be more careful," which prevents nothing.
- C) It assigns no action items.
- D) It is anonymous.

---

**Q12.** A strong chaos-drill postmortem names a *surprise*. Why is the surprise the valuable part?

- A) It isn't; the timeline is what matters.
- B) Recovery succeeding is expected; the surprise (e.g. "we had no detection path — we'd only have learned from a user complaint") is the finding that yields the real action item.
- C) Surprises make the report longer.
- D) It proves the drill failed.

---

**Q13.** In the WhatsApp-send-pipeline system-design drill, what's the senior signal an interviewer listens for?

- A) Naming a database vendor.
- B) Handling the offline case (local write + outbox), the duplicate-on-retry case (a client-generated idempotency key), and ordering — the same offline-first pattern the capstone uses.
- C) Drawing the most boxes.
- D) Mentioning microservices.

---

## Answer key

**Q1 — B.** Review enforces a finite set of policies plus an automated pre-launch report; the top rejection cause is a *declaration mismatch*, not code quality (no human reads your code). Get the declarations right and you land on the first try. (Lecture 1, §1.)

**Q2 — B.** Review's SDK scanning finds the data Firebase and Crashlytics collect regardless of your form. The fix is to declare honestly or remove the SDK — never under-declare and hope. (Lecture 1, §2.)

**Q3 — B.** Since Android 14 a foreground service needs a declared type, and Play requires a justification at submission. Your sync promotion is a `dataSync` type with a real "the user opened the app mid-sync" justification. (Lecture 1, §1.)

**Q4 — B.** The review queue is an external dependency you don't control. Submitting Monday makes a rejection recoverable (you have the week) and keeps the drills and interviews unblocked. Submitting Friday and praying is the crunch the course avoids. (Lecture 1, §5.)

**Q5 — B.** Last-writer-wins by *server-assigned* timestamp: the later write wins, both devices converge to it deterministically, and the only loss is this documented same-field bound. (Lecture 2, §2 drill A; Exercise 2.)

**Q6 — B.** Field-level merge: different fields don't clobber each other, so a status edit and a note edit both survive, and both devices converge identically. Zero unexpected loss. (Lecture 2, §2; Exercise 2.)

**Q7 — B.** Reconnect ≠ converge: the second device must receive the converged state over gRPC, and that propagation adds latency the merge doesn't. An SLO that budgeted only the merge cost blows past it — the drill's useful surprise. (Lecture 2, §2.)

**Q8 — B.** A single non-retryable call that fails during a flaky-network rotation means the backend never learns the new token, and every push is silently dropped until the next rotation — a total, silent push outage. (Lecture 2, §2 drill B; Exercise 3.)

**Q9 — B.** Route re-registration through the durable retryable path your data writes use (WorkManager + exponential backoff), so a transient failure retries and the token lands. (Lecture 2, §2; Exercise 3.)

**Q10 — B.** Return `PlayServicesUnavailable` and offer a documented fallback — graceful degradation. A brick fails the drill; a fail-open defeats the control. (Lecture 2, §2 drill C; Week 23 Exercise 3.)

**Q11 — B.** Blameless means replacing the person with the system component, which produces systemic fixes (prevent the whole class) rather than "be more careful" (prevents nothing). The tone is load-bearing, not etiquette. (Lecture 2, §3.)

**Q12 — B.** Recovery succeeding is expected and boring; the surprise — often a missing *detection* path — is the finding that yields the real action item (a metric, a synthetic prober). (Lecture 2, §3.)

**Q13 — B.** The senior signal is handling offline (local write + outbox), duplicates-on-retry (an idempotency key), and ordering — the exact offline-first pattern the capstone implements. You design from experience, not from a diagram. (Lecture 2, §5.)

---

*Score 11+? You're done — ship it, survive the drills, and interview. Below 9? Re-read Lecture 2 and re-run exercises 2 and 3 — the convergence contract and the no-dropped-message contract are the two the chaos drills are graded on. This is the last quiz of C21; pass it and present your capstone.*
