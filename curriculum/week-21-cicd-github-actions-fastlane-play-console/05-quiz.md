# Week 21 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 22. Answer key with explanations at the bottom — don't peek.

---

**Q1.** In GitHub Actions, what runs each job, and why does that make CI builds trustworthy?

- A) Your local machine; it's fast.
- B) A fresh, clean runner (VM) that starts empty and is destroyed after — so the build is reproducible by construction, with no leftover "works on my machine" state.
- C) A shared server that keeps state between runs.
- D) The Play Console.

---

**Q2.** What does `gradle/actions/setup-gradle` cache, and why is it safe?

- A) Your source code.
- B) The dependency cache (immutable artifacts) and the build cache (task outputs keyed by their inputs) — safe because a changed input produces a new key, so stale outputs are never reused.
- C) Your secrets, for speed.
- D) Nothing; it just installs Gradle.

---

**Q3.** Why restore the Gradle cache `cache-read-only` on pull-request (non-main) refs?

- A) It's faster.
- B) So a malicious or buggy PR can't write a poisoned cache entry that a later trusted (`main`) build reuses — write only from trusted branches.
- C) PRs can't read the cache otherwise.
- D) It's required syntax.

---

**Q4.** In a workflow with `release: { needs: [unit, lint, screenshots], if: startsWith(github.ref, 'refs/tags/') }`, when does `release` run?

- A) On every push.
- B) Only on a tag, and only if all three of `unit`, `lint`, and `screenshots` succeeded.
- C) Whenever you click a button.
- D) Only if one of the three passed.

---

**Q5.** Under Play App Signing, which key does Google hold, and which do you sign with in CI?

- A) Google holds the upload key; you sign with the app signing key.
- B) Google holds the **app signing key** (re-signs the delivered artifact); you sign in CI with the **upload key** (which the Play Console verifies the upload against).
- C) There's only one key, and you hold it.
- D) Google holds both; you sign with neither.

---

**Q6.** Why is a *lost upload key* recoverable under Play App Signing, when losing your single signing key used to be catastrophic?

- A) It isn't; a lost upload key is still fatal.
- B) Because the *app signing* key (the one that lets users update) is held by Google and untouched — you just ask Google to reset the *upload* key and register a new one.
- C) Because keys never expire.
- D) Because you can email the key to yourself.

---

**Q7.** A keystore is a binary file with passwords. How do you get it into CI without committing it?

- A) Commit it to a private repo; private is safe enough.
- B) Base64-encode it into a GitHub encrypted secret, decode it in the workflow into `$RUNNER_TEMP` (destroyed when the job ends), and feed the passwords as env from other secrets.
- C) Paste it into the workflow YAML.
- D) Upload it as a workflow artifact.

---

**Q8.** Why can't a pull request *from a fork* sign a release?

- A) Forks can't run Actions.
- B) Secrets aren't exposed to fork PRs by default (a security control), so the fork can't read the keystore — which is why releases run on tags pushed to your own repo, not on fork PRs.
- C) Forks don't have Gradle.
- D) It's a fastlane limitation.

---

**Q9.** What is a fastlane *lane*, and why prefer it over an ad-hoc release script?

- A) A CI runner.
- B) A named, composable sequence of actions in the `Fastfile` that runs identically locally and in CI — legible intent, shared by the team, instead of a release process living in one person's head.
- C) A Play Console track.
- D) A Gradle task.

---

**Q10.** `upload_to_play_store` (`supply`) drives the Play API, which is transactional. What does that mean for a failed upload?

- A) It leaves a half-applied release.
- B) It works through an *edit* (create → change → commit); if anything fails, the edit isn't committed, so you never end up with a partially applied release.
- C) It retries forever.
- D) It deletes your app.

---

**Q11.** Name the four Play tracks from smallest to largest audience, and say which one CI uploads to on every tag.

- A) production → open → closed → internal; CI targets production.
- B) internal → closed → open → production; CI uploads to **internal** (fast, ~100 testers, minimal review), and you *promote* the proven artifact upward with intent.
- C) alpha → beta → gamma → release; CI targets beta.
- D) debug → staging → preprod → prod; CI targets staging.

---

**Q12.** Why is the production *staged rollout* (1% → 5% → … → 100%) the one place a human stays in the loop?

- A) The API requires manual clicks.
- B) Because exposing more real users to new code — and deciding to halt if crash-free/ANR rates dip — is a judgment call about risk and blast radius; automating that *trigger* is how a crash reaches 100% of users with nobody watching.
- C) Robots aren't allowed near production.
- D) It's slower to automate.

---

**Q13.** You have no Play Console account (don't want the USD 25 fee). Can you still complete the week's pipeline objectives?

- A) No; the fee is mandatory.
- B) Yes — build + sign + verify with `apksigner`, upload the signed AAB as a workflow artifact (and/or prepare F-Droid metadata, and/or `supply --validate_only`); you exercise the entire gated pipeline minus the real Play API call.
- C) Only if you borrow someone's account.
- D) Only the CI half, not signing.

---

## Answer key

**Q1 — B.** Each job runs on a fresh runner that starts empty and is destroyed after. That clean-slate property is *why* CI builds are reproducible and trustworthy — no leftover state. (Lecture 1, §1.)

**Q2 — B.** It caches immutable dependencies and input-keyed task outputs. Safe because the build-cache key includes the inputs, so a change produces a new key and stale outputs are never reused. (Lecture 1, §2.)

**Q3 — B.** Read-only on PRs prevents a malicious/buggy PR from writing a poisoned entry the shared cache, which a later trusted build would reuse. Write only from trusted branches. (Lecture 1, §2.)

**Q4 — B.** `needs` requires all three jobs to pass; `if: startsWith(..., 'refs/tags/')` restricts to tags. Both conditions must hold — gated *and* tag-only. (Lecture 1, §3, §7.)

**Q5 — B.** Google holds the app signing key (re-signs what users install); you sign in CI with the upload key, which the console verifies the upload against. (Lecture 1, §4.)

**Q6 — B.** The app signing key — the one that authorizes updates — is held by Google and never lost by you. A lost *upload* key is reset by Google; the app signing key is safe. Pre-Play-App-Signing, losing your single key meant no more updates, ever. (Lecture 1, §4.)

**Q7 — B.** Base64 it into a secret, decode into `$RUNNER_TEMP` at build time (gone with the job), feed passwords as env from secrets. Never in the repo, never in an artifact. (Lecture 1, §5.)

**Q8 — B.** Secrets aren't exposed to fork PRs by default — a real security control — so a fork can't read the keystore. That's why releases run on tags in your own repo, not fork PRs. (Lecture 1, §5.)

**Q9 — B.** A lane is a named, composable action sequence that runs identically locally and in CI. It turns "the release is in Jordan's head" into `fastlane internal`. (Lecture 2, §1.)

**Q10 — B.** The Play API is transactional (edit → change → commit). A failure means the edit isn't committed, so there's no half-applied release. `supply` manages the whole transaction for you. (Lecture 2, §2.)

**Q11 — B.** internal → closed → open → production. CI uploads to internal (fast, private, minimal review); higher tracks get the *promoted* proven artifact, deliberately. (Lecture 2, §3.)

**Q12 — B.** Deciding to widen exposure of new code, and to halt on a vitals regression, is a risk judgment. Automate the rollout *mechanism*, keep the *trigger* human — automating it is how a crash ships to everyone unwatched. (Lecture 2, §4.)

**Q13 — B.** Every objective is reachable without the fee: build + sign + `apksigner verify`, upload-artifact, F-Droid metadata, or `supply --validate_only`. The rubric grades the pipeline, not whether you paid Google. (Lecture 2, §7.)

---

*Score 11+? On to Week 22. Below 9? Re-read both lecture notes and re-run exercises 2 and 3 — the secrets-fed signing config and the fastlane supply lane are the two ideas this week is graded on.*
