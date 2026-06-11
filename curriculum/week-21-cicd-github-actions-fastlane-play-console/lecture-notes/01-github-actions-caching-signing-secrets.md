# Lecture 1 — GitHub Actions for Android: caching, signing, secrets

> "A release is not something you do. It is something a tagged commit *causes*. Your job is to make the cause reliable and the secrets invisible."

This is the lecture that turns your `./gradlew bundleRelease` habit into a pipeline. We build it bottom-up: the GitHub Actions model first, then Gradle caching (the difference between an eight-minute and a ninety-second build), then parallel matrices, then the genuinely dangerous part — Play App Signing and the secrets that feed it. By the end you can write a workflow that builds and tests every PR and signs a release AAB on every tag, without a single key on disk or in the repo.

---

## 1. The model: workflows, jobs, steps, runners

A GitHub Actions **workflow** is a YAML file in `.github/workflows/`. It has:

- **Triggers** (`on:`) — what causes it to run (a push to a branch, a pull request, a pushed tag, a schedule, a manual dispatch).
- **Jobs** — units of work that run on a fresh **runner** (a clean VM, `ubuntu-latest` by default). Jobs run in parallel unless you declare dependencies.
- **Steps** — ordered commands within a job: either a shell `run:` or a reusable `uses:` action.

The smallest meaningful Android workflow — build and test on every PR:

```yaml
name: CI
on:
  pull_request:           # run on every PR
  push:
    branches: [ main ]    # and on pushes to main

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4                 # check out the repo
      - uses: actions/setup-java@v4               # install a JDK
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v4      # Gradle + its caching (next section)
      - run: ./gradlew testDebugUnitTest lintDebug   # the quality gate
```

Five things to internalize from this:

1. **The runner is clean every time.** No leftover state, no "works on my machine" — the VM starts empty and is destroyed after. That is *why* CI builds are trustworthy: they are reproducible by construction.
2. **`actions/checkout` is not free magic.** Without it, the runner has no code. It is always the first step.
3. **`setup-java` provisions the JDK** the runner doesn't ship the exact version you want. JDK 17 for modern Android Gradle.
4. **The Android SDK is preinstalled** on `ubuntu-latest` runners (the `ANDROID_HOME` is set), so `./gradlew` finds it. You don't install the SDK by hand.
5. **The gate is a `run:` step.** `./gradlew testDebugUnitTest lintDebug` — if it exits non-zero, the job fails, the PR shows a red X, and (with branch protection) the merge is blocked. *That* is the gate.

> The mental model: a workflow is a *cause-and-effect rule*. "On this trigger, run these jobs on clean runners." The reliability comes from the clean runner; the value comes from gating merges/releases on the jobs passing.

---

## 2. Gradle caching — the eight-minute vs ninety-second build

A cold Gradle build on a clean runner downloads every dependency and compiles everything — minutes you pay on every run. The fix is caching, and `gradle/actions/setup-gradle` does it well:

```yaml
- uses: gradle/actions/setup-gradle@v4
  with:
    # On PRs, restore the cache read-only so a PR can't poison the shared cache.
    cache-read-only: ${{ github.ref != 'refs/heads/main' }}
```

What it caches and why it's safe:

- **The dependency cache** (`~/.gradle/caches/modules-2`) — downloaded artifacts. Safe: dependencies are immutable for a given version.
- **The build cache** — task outputs keyed by inputs. Safe *because* the key includes the inputs; a changed input produces a new key, so you never reuse a stale output.

The cache **key** matters. `setup-gradle` derives it from your Gradle files and lockfiles, so a dependency change invalidates the cache correctly. What you must *not* cache: anything that should be rebuilt fresh every release (the signed artifact itself), and never cache secrets.

The PR-safety point — `cache-read-only` on non-`main` refs — is a real security control: a malicious PR could otherwise write a poisoned entry into the shared cache that a later `main` build reuses. Restore read-only on PRs, write only from trusted branches.

The payoff is concrete: a cold build of a medium Android project is commonly 6–10 minutes; a warm build with a good cache is often under 90 seconds, because compilation and dependency resolution are reused. You will *measure* this in exercise 1 — run twice, compare.

---

## 3. Parallel matrices — split the work

Jobs run in parallel by default. A **matrix** runs the *same* job across a set of parameters, and `needs:` orders jobs into a dependency graph. The common Android shape: run unit tests, lint, and instrumented/screenshot tests as separate parallel jobs, then gate a release job on all of them.

```yaml
jobs:
  unit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew testDebugUnitTest

  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew lintDebug

  screenshots:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew verifyPaparazziDebug    # Paparazzi runs on the JVM, no emulator

  release:
    needs: [ unit, lint, screenshots ]   # only runs if ALL THREE pass
    if: startsWith(github.ref, 'refs/tags/')   # ...and only on a tag
    runs-on: ubuntu-latest
    steps:
      - run: echo "build + sign + upload here (sections 4-6)"
```

The wins:

- **Parallelism.** Three jobs on three runners finish in roughly the time of the slowest, not the sum. A 4-minute unit job + 3-minute lint + 5-minute screenshots is ~5 minutes wall-clock, not 12.
- **`needs:` is the gate.** The `release` job lists `needs: [unit, lint, screenshots]`, so it *cannot* run unless all three succeeded. Combined with `if: startsWith(github.ref, 'refs/tags/')`, the release happens only on a tag, only when the gate is green. This is the structural enforcement of "a tag is a release, gated on tests."

When parallelism *isn't* worth it: very small projects where each job's fixed overhead (checkout + JDK + cache restore, ~30–60s) dwarfs the work. Then one sequential job is faster. And **instrumented (emulator) tests** are the expensive case — they need a hardware-accelerated emulator (`ReactiveCircus/android-emulator-runner`), are slow, and flaky; run a *small* smoke set on CI, not the whole suite. Paparazzi screenshot tests run on the plain JVM with no emulator, which is exactly why they're the CI-friendly visual gate.

---

## 4. Play App Signing — the two-key model

Here is the concept every Android engineer must hold precisely, because getting it wrong means either you can't release or you ship something users can't update.

When you enroll in **Play App Signing**, there are **two keys**:

- **The app signing key** — the key that *actually signs the APK delivered to users*. **Google holds it.** You never see it. Google re-signs your uploaded bundle with it before delivery. Because Google holds it, it can't be lost by you, and it's protected by Google's infrastructure.
- **The upload key** — the key *you* hold and *CI signs the uploaded bundle with*. The Play Console verifies the upload was signed by your registered upload key, then strips your signature and re-signs with the app signing key. The upload key proves "this upload is really from you."

Why this scheme is good engineering:

- **A lost upload key is recoverable.** Before Play App Signing, losing your one signing key was catastrophic — you could *never* update the app again (a new key = a different app, can't update). Now, if you lose the *upload* key, you ask Google to reset it; the *app signing* key (the one that matters for updates) is safe with Google. The disaster scenario is gone.
- **The key that updates the app is protected by Google,** not sitting on your laptop or in a CI secret. The worst a leaked upload key does is let someone upload to *your* console — bad, but rotatable, and gated by the rest of your account security.

So in CI you sign with the **upload key**. You generate it once (`keytool -genkey ... -keystore upload-keystore.jks`), register it during enrollment, and from then on the pipeline signs every release bundle with it. The app signing key is Google's problem, by design.

Key rotation: the upload key can be rotated (you request it in the console and register a new one); the app signing key supports rotation too, but it's rarely needed and Google manages it. For this course: generate an upload keystore, keep it as a secret, sign with it. That's the whole operational story.

---

## 5. Secrets management — the keystore that's never on disk

The upload keystore is a binary file with passwords. It must **never** be committed to the repo (a public repo would leak it instantly; even a private repo is the wrong place). The pattern: encode it as a GitHub **encrypted secret**, decode it at build time, and let the signing config read passwords from the environment.

**Step 1 — encode the keystore as base64 and store it as a secret.** Locally:

```bash
# Turn the binary keystore into a base64 string and copy it.
base64 -i upload-keystore.jks | pbcopy        # macOS; use `base64 -w0` on Linux
```

In the GitHub repo: **Settings ▸ Secrets and variables ▸ Actions ▸ New repository secret**. Create:

- `KEYSTORE_BASE64` — the base64 string above.
- `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` — the keystore/key credentials.
- `PLAY_SERVICE_ACCOUNT_JSON` — the Play Console service-account JSON (lecture 2), also as a secret (it's text, so paste it directly or base64 it).

**Step 2 — decode it in the workflow, into a temp file the build can read but the repo never sees.**

```yaml
- name: Decode upload keystore
  env:
    KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
  run: |
    echo "$KEYSTORE_BASE64" | base64 --decode > "$RUNNER_TEMP/upload-keystore.jks"
    echo "KEYSTORE_FILE=$RUNNER_TEMP/upload-keystore.jks" >> "$GITHUB_ENV"

- name: Build signed bundle
  env:
    KEYSTORE_FILE: ${{ env.KEYSTORE_FILE }}
    KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
  run: ./gradlew bundleRelease
```

Critical hygiene points:

- **The keystore lives in `$RUNNER_TEMP`,** which is destroyed when the job ends. It's on disk for the duration of one job, on a throwaway VM, never in the repo, never in an artifact.
- **Secrets are masked in logs.** GitHub redacts secret values from the workflow log automatically — but only the exact secret string. Don't `echo` a decoded password yourself; don't write secrets into files you upload as artifacts.
- **Secrets aren't exposed to PRs from forks** by default. A fork PR can't read your secrets, which is *why* you don't (and can't) sign releases from fork PRs — releases run on tags pushed to your own repo.
- **Least privilege.** The Play service account gets *only* the Android Publisher role, scoped to your app. The `GITHUB_TOKEN`'s `permissions:` block should grant only what the workflow needs.

### The OIDC alternative — keyless cloud auth

Long-lived secrets (a base64 keystore, a service-account JSON) are a real, if managed, risk: they sit in your secret store until you rotate them, and a leak is a standing liability. The modern alternative for *cloud* auth is **OIDC (OpenID Connect)**: GitHub's runner mints a short-lived, workflow-scoped token that Google Cloud (via **workload identity federation**) exchanges for temporary credentials. No long-lived service-account key exists to leak.

```yaml
permissions:
  id-token: write     # allow the runner to request an OIDC token
  contents: read
# ...then use google-github-actions/auth with workload_identity_provider
```

OIDC is the better posture for the *Play API auth* (it removes the standing JSON-key secret). It does **not** replace the *keystore* secret — signing still needs the actual upload key, which is inherently a long-lived secret you guard. The senior takeaway: prefer OIDC for cloud service auth where it's available; accept and tightly guard the keystore secret because signing genuinely requires the key. Know both; reach for OIDC when the provider supports it.

---

## 6. Signing a release in CI — wiring the config

The `signingConfig` reads its values from environment variables, which the workflow feeds from secrets (section 5). In `app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            // Read from env (set by CI from secrets). Locally these may be unset,
            // so guard with a fallback so a developer debug build doesn't break.
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true                          // R8 (Week 18)
            signingConfig = signingConfigs.getByName("release")
            // ...proguard rules...
        }
    }
}
```

Two design points:

- **Env vars, not a `keystore.properties` file committed to the repo.** A `keystore.properties` with passwords is the classic leak. Read from the environment, populated by secrets, so nothing sensitive is in the repo at all.
- **Guard for the absent case.** Locally, `KEYSTORE_FILE` is unset, so the `if` skips it and a developer's `assembleDebug` still works (debug uses the debug keystore). The release config is fully populated only in CI.

**Verify the output.** A signed AAB/APK must actually carry the right signature. After the build:

```bash
# For an APK; for an AAB, build an APK set with bundletool first, or trust supply's validation.
apksigner verify --print-certs app-release.apk
```

The printed certificate is your *upload* key (Play re-signs with the app signing key on delivery). In exercise 2 you confirm the signed output is signed by the expected key — the proof that the secrets-and-config wiring worked.

---

## 7. Putting the pipeline together — a worked CI/release split

The clean structure separates *CI* (every PR — fast feedback, no signing) from *release* (every tag — gated, signed, uploaded):

```yaml
name: Android
on:
  pull_request:
  push:
    branches: [ main ]
    tags: [ 'v*' ]          # releases are tagged v1.2.3

jobs:
  ci:                        # runs on every PR and push
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
        with: { cache-read-only: ${{ github.ref != 'refs/heads/main' }} }
      - run: ./gradlew testDebugUnitTest lintDebug verifyPaparazziDebug

  release:                   # runs ONLY on a v* tag, ONLY if ci passed
    needs: [ ci ]
    if: startsWith(github.ref, 'refs/tags/v')
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
      - name: Decode keystore
        env: { KEYSTORE_BASE64: '${{ secrets.KEYSTORE_BASE64 }}' }
        run: |
          echo "$KEYSTORE_BASE64" | base64 --decode > "$RUNNER_TEMP/ks.jks"
          echo "KEYSTORE_FILE=$RUNNER_TEMP/ks.jks" >> "$GITHUB_ENV"
      - name: Build signed bundle
        env:
          KEYSTORE_FILE: ${{ env.KEYSTORE_FILE }}
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew bundleRelease
      - name: Upload to Play internal track
        run: echo "fastlane supply  -- lecture 2"     # next lecture
```

Read the shape:

1. **`ci` runs on everything** — fast, no secrets, the merge gate.
2. **`release` runs only on a `v*` tag** (`if:`) **and only if `ci` passed** (`needs:`). Two independent conditions, both required.
3. **Secrets touch only the `release` job,** decoded into `$RUNNER_TEMP`, fed as env to the signed build. The CI job never sees them.
4. **The upload** is the next lecture — `fastlane supply` against the Play Console API.

That is the entire structure: clean runners, a cached fast CI gate on every change, and a signed-and-gated release that only a tag can trigger, with secrets isolated to the one job that needs them.

### Required checks and branch protection — making the gate *binding*

A green/red checkmark on a PR is advisory until you make it *binding*. GitHub's **branch protection** (Settings ▸ Branches ▸ Add rule) turns the `ci` job into a hard merge requirement:

- **Require status checks to pass before merging** — select the `ci` job. Now the *Merge* button is disabled while CI is red. A failing test physically cannot be merged.
- **Require branches to be up to date** — forces a PR to re-run CI against the latest `main` before merging, catching "passed in isolation, breaks when combined" failures.
- **Require a pull request before merging** — no direct pushes to `main`; everything goes through a PR and its gate.

Without branch protection, the gate is a suggestion a hurried developer clicks past. *With* it, "tests must pass" is enforced by the platform, not by discipline. This is the difference between a CI that *reports* problems and one that *prevents* them reaching `main`. For a release pipeline, it matters doubly: since releases come from tags on `main`, protecting `main` means every releasable commit already passed the gate before it could land.

A subtle interaction with the release job: the `release` job's `needs: [ci]` re-runs the gate on the *tag's* commit, even though that commit passed the gate when it was on `main`. That's intentional belt-and-suspenders — the tag could theoretically point at a commit that bypassed protection (an admin override), so the release re-verifies rather than assuming. Cheap insurance for the one job that signs and ships.

---

## 8. Recap — the one-question habit

The reflex this lecture installs: on every pipeline decision, ask **"is this reproducible, gated, and secret-safe?"**

- A release happened → a `v*` tag was pushed and the gated `release` job ran; no human built it locally.
- The build was fast → the Gradle cache was warm; `setup-gradle` restored task outputs keyed by inputs.
- A test failure blocked the release → `needs: [ci]` gated the release job on the CI gate passing.
- The keystore isn't in the repo → it's a base64 secret, decoded into `$RUNNER_TEMP`, gone when the job ends.
- The app updates correctly for users → it's signed by the *upload* key and Play re-signs with the *app signing* key it holds.
- I want to drop the standing JSON-key secret → use OIDC / workload identity federation for the Play API auth (but signing still needs the guarded keystore).

You now have the build half of the pipeline: a gated, cached, signed CI/release workflow with secrets done correctly. In lecture 2 we add the *delivery* half: fastlane's lanes, `supply` against the Play Console API, the four tracks, staged rollout, and the one place we deliberately keep a human in the loop — the promotion to production. Bring this workflow; we're about to give its `release` job somewhere to ship.
