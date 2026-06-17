# Week 21 — CI/CD: GitHub Actions, fastlane, Play Console API

Welcome to Week 21 of **C21 · Crunch Droid**. Last week you built for three form factors — a phone/foldable adaptive app and a Wear OS companion. This week you stop building those artifacts *by hand* and teach a machine to do it: a complete release pipeline that, on every tagged commit, checks out your code, restores the Gradle cache, runs the full test suite, generates screenshots, builds a *signed* App Bundle and a *signed* Wear APK, and uploads them to the Play Console internal track — with zero human in the loop and zero secrets in the repo.

The mental shift this week is from "I run `./gradlew` and click *Upload* in the Play Console" to **"a tagged commit is a release, and the pipeline is the only thing allowed to ship."** Manual releases are where Android teams get hurt: someone builds a debug AAB by accident, or signs with the wrong key, or skips the tests because they're in a hurry, or pastes the upload key into a Slack message. CI/CD removes the human from the dangerous parts. The build is reproducible because the runner is clean every time. The signing is correct because the key lives in an encrypted secret, decoded in memory, never written to disk in plaintext, never in the repo. The tests *always* run because the pipeline gates the upload on them. And the upload is one `fastlane` lane, not a sequence of clicks you might do in the wrong order at 6pm on a Friday.

The thing this week hammers on is that **releasing on Android is a four-step pipeline, and you automate three of them.** The four steps: (1) build a signed artifact, (2) run the full quality gate (tests + screenshots), (3) upload to a track, (4) promote/roll out to users. Steps 1–3 are pure automation — a machine does them perfectly, every time, faster than you. Step 4 — the staged rollout to production, halting if the crash rate spikes — keeps a human in the loop *on purpose*, because deciding to expose real users to new code is a judgment call, not a build step. We wire 1–3 fully and teach you exactly where step 4's human gate belongs. The deep, dangerous topic underneath all of it is **secrets and signing**: Play App Signing, the upload key versus the app signing key, encoding a keystore as a base64 secret, the service-account JSON for the Play Console API, and the OIDC-versus-long-lived-secret trade-off. Get signing wrong and you either can't release or you ship something users can't update. Get secrets wrong and you leak your signing key to the world. This is the week where "it works on my machine" stops being acceptable.

We close the week by building a **GitHub Actions workflow that, on a tagged commit, builds a signed AAB, runs the full test suite, generates Paparazzi screenshots, and uploads to the Play Console internal track via fastlane** — the exact pipeline the capstone's deliverable #7 requires. You will write the workflow YAML, configure the Gradle caching that takes the build from eight minutes to ninety seconds on a warm cache, store the keystore and the service-account JSON as encrypted secrets, write the fastlane `Fastfile` with `screengrab` and `supply` lanes, and watch a `git push --tags` turn into an artifact sitting on the internal track without you touching a mouse. That green checkmark on a tag — "the machine built it, tested it, signed it, and shipped it to internal" — is the senior-engineer instinct this week installs.

## Learning objectives

By the end of this week, you will be able to:

- **Author** a GitHub Actions workflow for Android: triggers (`push` tags, `pull_request`), a JDK + Android SDK setup, a Gradle build/test job, and artifact upload.
- **Cache** Gradle correctly with `gradle/actions/setup-gradle` (or `actions/cache`) so warm CI builds are sub-two-minutes, and explain what is and isn't safe to cache.
- **Build a matrix** that runs jobs in parallel (e.g. unit tests on one runner, instrumented/screenshot on another, lint on a third) and understand when a matrix helps versus hurts.
- **Explain Play App Signing**: the difference between the *upload key* (you hold, CI signs with) and the *app signing key* (Google holds, re-signs the delivered artifact), and why this scheme means a lost upload key is recoverable.
- **Manage secrets** the right way: base64-encode a keystore into a GitHub secret, decode it in-memory at build time, store the Play service-account JSON as a secret, and never commit either — with a clear-eyed view of the OIDC alternative.
- **Sign a release AAB in CI** by wiring a `signingConfig` that reads the keystore path/passwords from environment variables fed by secrets, and verify the output is correctly signed.
- **Write fastlane lanes**: a `Fastfile` with `gradle` (build), `screengrab` (screenshots), and `supply` (Play upload) actions; configure `Appfile`/`Pluginfile`; and run a lane locally and in CI.
- **Use the Play Console API** via fastlane `supply`: the four tracks (internal, closed, open, production), uploading an AAB to a track, attaching release notes, and configuring a **staged rollout** percentage — and where the human gate for promotion belongs.

## Prerequisites

This week assumes you have completed **C21 weeks 1–20**, or have equivalent fluency. Specifically:

- You can read and write `build.gradle.kts`, version catalogs, build variants, and a `signingConfig` — Week 6. The whole signing story this week extends the debug-keystore work you did then to a real upload key.
- You have a test suite worth gating on — unit (Turbine/MockK), Robolectric, Compose UI test, Paparazzi screenshots, an Espresso smoke — from **Week 17**. CI/CD without tests to run is a fancy build button; the gate is the point.
- You understand R8/ProGuard and that a release build shrinks and obfuscates — Week 18. CI builds the *release* variant; you must know why the release output differs from debug.
- You have artifacts worth shipping: the phone/foldable app and the `:wear` module from **Week 20**. This week's pipeline builds and uploads both.
- You have a GitHub account and a repository for your project. A Play Console account (the one-time USD 25 fee) is recommended for the real upload; **a fully working no-Play-account path is provided** (build + sign + verify locally, and upload to a free F-Droid-style or draft path) so nobody is gated by the fee.

**Toolchain.** GitHub Actions (free tier is plenty), Ruby + fastlane (`gem install fastlane`, or the bundled `Gemfile` path), the Android command-line tools (the runner installs them), JDK 17, your existing Gradle Kotlin DSL project. For the real upload: a Play Console account, an app created in the console, and a **Google Cloud service account** with the *Android Publisher* role and a JSON key. Everything except the optional Play fee is free.

## Topics covered

- **GitHub Actions fundamentals.** Workflows, jobs, steps, runners (`ubuntu-latest`), triggers (`on: push: tags`, `on: pull_request`), `actions/checkout`, `actions/setup-java`, the Android SDK on the runner, and `actions/upload-artifact`.
- **Gradle caching in CI.** `gradle/actions/setup-gradle` with its build cache and dependency cache, what the cache key should include, read-only caching on PRs, and the warm-vs-cold build-time difference.
- **Parallel build matrices.** `strategy.matrix`, `needs:` for job dependencies, `fail-fast`, and splitting unit / instrumented / lint / screenshot work across runners — and when the parallelism overhead isn't worth it.
- **Play App Signing.** The two-key model: the *upload key* (you generate, CI signs with) and the *app signing key* (Google generates and holds, re-signs the delivered artifact). Key rotation, why a lost upload key is recoverable, and the enrollment flow.
- **Secrets management.** GitHub encrypted secrets, base64-encoding a keystore (`base64 -i keystore.jks`), decoding to a temp file or in-memory at build time, the Play service-account JSON as a secret, `GITHUB_TOKEN` scope, and the OIDC / keyless alternative for cloud auth.
- **Signing a release in CI.** A `signingConfig` that reads `storeFile`/`storePassword`/`keyAlias`/`keyPassword` from env vars, feeding those env vars from secrets in the workflow, and `apksigner verify` to confirm the signature.
- **fastlane.** Installing fastlane, the `Fastfile` (lanes), `Appfile` (package name, service account), `Pluginfile`, and the core actions: `gradle` (assemble/bundle), `screengrab` (UI-test-driven screenshots), `supply` (upload to Play), `build_android_app`.
- **The Play Console API and tracks.** `supply` against the Android Publisher API, the four tracks (internal / closed / open / production), uploading an AAB with release notes, **staged rollout** (`rollout: 0.1` = 10%), promoting between tracks, and the human gate before production.
- **The release-readiness gate.** Wiring tests + lint + screenshots as required checks *before* the upload step, so a red test blocks the release; and the difference between a CI build (every PR) and a release build (every tag).
- **A no-Play-account path.** Building and signing locally/in CI and verifying the signature without uploading, plus the F-Droid metadata path, so the fee is never a blocker for learning the pipeline.

## Weekly schedule

The schedule below adds up to approximately **36 hours**. Treat it as a target, not a contract — some days you will move faster, some slower.

| Day       | Focus                                                                | Lectures | Exercises | Challenges | Quiz/Read | Homework | Mini-Project | Self-Study | Daily Total |
|-----------|----------------------------------------------------------------------|---------:|----------:|-----------:|----------:|---------:|-------------:|-----------:|------------:|
| Monday    | GitHub Actions for Android; caching; the build/test job              |    2h    |    1.5h   |     0h     |    0.5h   |   1h     |     0h       |    0.5h    |     5.5h    |
| Tuesday   | Matrices, parallel jobs, required checks; signing config in CI       |    2h    |    2h     |     0h     |    0.5h   |   1h     |     0h       |    0h      |     6.5h    |
| Wednesday | Play App Signing; secrets management; base64 keystore; the OIDC path |    1h    |    2h     |     1h     |    0.5h   |   1h     |     0h       |    0.5h    |     6h      |
| Thursday  | fastlane lanes; supply; tracks and staged rollout; challenge         |    1h    |    1h     |     1h     |    0.5h   |   1h     |     2h       |    0.5h    |     7h      |
| Friday    | Mini-project — the release workflow: build, sign, test, screenshot   |    0h    |    1h     |     0h     |    0.5h   |   1h     |     3h       |    0h      |     5.5h    |
| Saturday  | Mini-project deep work — fastlane upload to internal; verify a tag run |    0h    |    0h     |     0h     |    0h     |   0h     |     3h       |    0h      |     3h      |
| Sunday    | Quiz, review, polish, push                                           |    0h    |    0h     |     0h     |    1h     |   0h     |     0.5h     |    0h      |     1.5h    |
| **Total** |                                                                      | **6h**   | **7.5h**  | **2h**     | **3.5h**  | **5h**   | **11.5h**    | **1.5h**   | **37h**     |

## How to navigate this week

| File | What's inside |
|------|---------------|
| [README.md](./00-overview.md) | This overview (you are here) |
| [resources.md](./01-resources.md) | The GitHub Actions docs, the `setup-gradle` action, the Play App Signing guide, the fastlane docs (`supply`, `screengrab`), the Play Console API reference, and the canonical talks |
| [lecture-notes/01-github-actions-caching-signing-secrets.md](./02-lecture-notes/01-github-actions-caching-signing-secrets.md) | GitHub Actions for Android end to end: workflows, caching, matrices, Play App Signing, secrets management, and signing a release AAB in CI without leaking a key |
| [lecture-notes/02-fastlane-play-console-api-tracks-rollout.md](./02-lecture-notes/02-fastlane-play-console-api-tracks-rollout.md) | fastlane lanes, `supply` against the Play Console API, the four tracks, staged rollout, the human gate before production, and the no-Play-account path |
| [exercises/README.md](./03-exercises/00-overview.md) | Index of the three exercises |
| [exercises/exercise-01-android-ci-workflow.md](./03-exercises/exercise-01-android-ci-workflow.md) | Write a GitHub Actions workflow that builds and tests on every PR, with Gradle caching, and measure the warm-vs-cold build time |
| [exercises/exercise-02-signing-config-from-secrets.kt](./03-exercises/exercise-02-signing-config-from-secrets.kt) | Wire a `signingConfig` that reads the keystore and passwords from environment variables fed by secrets, and verify the signed output |
| [exercises/exercise-03-fastlane-supply-lane.kt](./03-exercises/exercise-03-fastlane-supply-lane.kt) | Author a fastlane lane (and the supporting Gradle/Kotlin glue) that bundles a signed AAB and uploads it to the Play internal track with a staged rollout |
| [challenges/README.md](./04-challenges/00-overview.md) | Index of the challenge |
| [challenges/challenge-01-tag-to-internal-track-pipeline.md](./04-challenges/challenge-01-tag-to-internal-track-pipeline.md) | Build the complete tag→release pipeline: a tagged commit builds a signed AAB + Wear APK, runs the full gate, screenshots, and uploads to internal — with secrets handled correctly and the human gate documented |
| [quiz.md](./05-quiz.md) | 13 questions on GitHub Actions, caching, Play App Signing, secrets, fastlane, tracks, and staged rollout |
| [homework.md](./06-homework.md) | Six practice problems for the week |
| [mini-project/README.md](./07-mini-project/00-overview.md) | Full spec for the GitHub Actions release workflow: build, test, screenshot, sign, and upload to the Play internal track on every tag |

## The "a tag is a release" promise

Every week of Crunch Droid adds one production-grade contract a senior reviewer actually checks. This week's:

> **A release must be a fully automated, reproducible consequence of a tagged commit — never a manual build, never a key on disk, never a skipped test.** The pipeline checks out clean, restores the cache, runs the full gate, signs with a key that exists only in an encrypted secret decoded in memory, and uploads to a track. If your release involves a human running `./gradlew bundleRelease` locally, or a keystore committed to the repo, or an upload step that runs even when tests fail, the review fails no matter how green the build looks.

You will *prove* this in the mini-project: a `git push --tags` triggers a workflow you can watch in the Actions tab — checkout, cache restore, test, screenshot, sign, upload — ending with a build sitting on the Play internal track (or a verified signed AAB on the no-account path). No mouse, no local build, no secret in the repo. That end-to-end automation is the deliverable, and it is exactly capstone requirement #7.

## A note on what's not here

Week 21 is the *release pipeline* week. It deliberately does **not** cover:

- **Security hardening of the app itself.** Keystore-backed encryption of user data, `EncryptedSharedPreferences`, certificate pinning, and Play Integrity attestation are **Week 22**. This week secures the *release* (signing, secrets); next week secures the *app* (data, network, attestation). Don't confuse the two keystores: the *app signing* keystore (this week) versus the Android *system Keystore* for user data (next week).
- **Writing the tests.** The unit/Robolectric/Compose/Paparazzi/Espresso suite was **Week 17**. This week *runs* it as a gate; it does not teach you to write it.
- **R8 and Baseline Profiles.** The release-build shrinking and the cold-start profile were **Week 18**. CI builds the release variant that uses them; the profile generation itself is last week's topic.
- **The full multi-track promotion lifecycle.** We wire internal-track upload and explain closed/open/production and staged rollout, but the full beta-program lifecycle (managing testers, halting a rollout on a vitals regression) is touched on and fully exercised in the capstone polish week (Week 24).

The point of Week 21 is narrow and operationally critical: one workflow that turns a tag into a signed, tested, screenshotted artifact on a Play track, with secrets and signing done correctly — the difference between a hobby project and a shipping product.

## Up next

Continue to **Week 22 — Security: Keystore, EncryptedSharedPreferences, certificate pinning, Play Integrity** once your pipeline ships a tagged commit to internal and you can explain every secret it touches. Week 22 turns from securing the *release* to securing the *app*: the Android Keystore for encrypting user data at rest, `EncryptedSharedPreferences` and `EncryptedFile`, certificate pinning so a compromised CA can't MITM your traffic, and Play Integrity attestation so your backend can tell a genuine app+device from a tampered one. The pipeline you built this week is what *ships* that hardened app — and the service-account and signing discipline you learned here is the same secrets-hygiene mindset Week 22 applies to the app's own secrets.

---

*If you find errors in this material, please open an issue or send a PR. Future learners will thank you.*
