# Challenge 1 — The tag-to-internal-track pipeline

**Time.** 90–150 minutes.
**Deliverable.** A working `.github/workflows/release.yml` (+ CI workflow, `Fastfile`, `Appfile`, signing config) that, on a `v*` tag, builds and signs the phone AAB and the Wear APK, runs the full gate, screenshots, and uploads to the Play internal track (or the no-account artifact path). Plus a `RELEASE.md` documenting every secret, the human gate, and a screenshot of a green tag run. Committed to your Week 21 repo.

## The premise

Every team has a "release person" — the one who knows the seventeen manual steps, builds the AAB on their laptop, clicks through the Play Console, and is on holiday the week you need a hotfix. The skill this challenge builds is making that person *unnecessary*: **a tagged commit is the entire release, the pipeline does the dangerous parts perfectly, the secrets are invisible, and the one judgment call (production rollout) is a documented human-gated lane rather than a manual mystery.** A release process in one person's head is a liability; a release process that is `git push --tags` is an asset.

You will wire the complete pipeline for a *multi-form-factor* project (the phone/foldable app and the `:wear` module from Week 20), and the grading is whether a tag genuinely turns into a signed, tested, screenshotted artifact on a track with no human touching a build — plus the honesty of your `RELEASE.md` about secrets and the human gate.

## What to build

A two-workflow setup over a project with an `:app` (phone) and a `:wear` module.

### Step 1 — The CI gate (every PR)

`.github/workflows/ci.yml` — fast feedback on every change, no secrets:

```yaml
name: CI
on: { pull_request: , push: { branches: [ main ] } }
jobs:
  gate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
        with: { cache-read-only: ${{ github.ref != 'refs/heads/main' }} }
      - run: ./gradlew testDebugUnitTest lintDebug verifyPaparazziDebug
```

### Step 2 — The signing configs (both modules)

A release `signingConfig` in *both* `app/build.gradle.kts` and `wear/build.gradle.kts`, each reading the keystore from env vars (exercise 2). You may share one upload key across both modules or use two — document your choice. Neither keystore is in the repo.

### Step 3 — The release workflow (every `v*` tag)

`.github/workflows/release.yml` — gated, signed, multi-artifact:

```yaml
name: Release
on: { push: { tags: [ 'v*' ] } }
jobs:
  gate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew testDebugUnitTest verifyPaparazziDebug

  release:
    needs: [ gate ]                       # gated on the test gate passing
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: ruby/setup-ruby@v1
        with: { ruby-version: '3.2', bundler-cache: true }
      - uses: gradle/actions/setup-gradle@v4
      - name: Decode keystore
        env: { KEYSTORE_BASE64: '${{ secrets.KEYSTORE_BASE64 }}' }
        run: |
          echo "$KEYSTORE_BASE64" | base64 --decode > "$RUNNER_TEMP/ks.jks"
          echo "KEYSTORE_FILE=$RUNNER_TEMP/ks.jks" >> "$GITHUB_ENV"
      - name: Write Play service account
        env: { PLAY_JSON: '${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}' }
        run: |
          echo "$PLAY_JSON" > "$RUNNER_TEMP/play.json"
          echo "PLAY_SERVICE_ACCOUNT_JSON_PATH=$RUNNER_TEMP/play.json" >> "$GITHUB_ENV"
      - name: Build phone AAB + Wear APK, upload to internal
        env:
          KEYSTORE_FILE:     ${{ env.KEYSTORE_FILE }}
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS:         ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD:      ${{ secrets.KEY_PASSWORD }}
          VERSION_CODE:      ${{ github.run_number }}
          VERSION_NAME:      ${{ github.ref_name }}
        run: bundle exec fastlane release_all   # builds both, uploads both
```

### Step 4 — The fastlane `release_all` lane

A lane that bundles the phone AAB, assembles the signed Wear APK, and uploads both to internal:

```ruby
  lane :release_all do
    # Phone: signed AAB to the internal track.
    gradle(task: ":app:bundle", build_type: "Release")
    upload_to_play_store(
      track: "internal",
      aab: "app/build/outputs/bundle/release/app-release.aab",
      release_status: "draft",
      skip_upload_metadata: true, skip_upload_images: true, skip_upload_screenshots: true
    )
    # Wear: signed APK. (Wear is delivered alongside the phone app; upload per your
    # project's Wear delivery setup, or attach as an artifact on the no-account path.)
    gradle(task: ":wear:assemble", build_type: "Release")
  end
```

(Wear delivery via Play has its own setup; for the challenge, signing + assembling the Wear APK and attaching it as a verified artifact is acceptable, with a note on the real Wear delivery path.)

### Step 5 — The human-gated production lane (documented, not run)

A `production_rollout` lane (exercise 3) present in the `Fastfile` but **not** wired to any automatic trigger, with a comment explaining that a human runs it while watching vitals.

### Step 6 — `RELEASE.md`

The graded artifact. Document:

- **Every secret** the pipeline uses (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`), what each is, and how it's injected (decoded into `$RUNNER_TEMP`, never in the repo).
- **The signing model**: upload key vs app signing key, which one your secret holds, why a lost upload key is recoverable.
- **The human gate**: which steps are automated (build, gate, upload-to-internal) and which are human (promotion, production staged rollout), and *why* the production rollout trigger stays human.
- **The no-account path** (if you used it): what you substituted for the Play upload and how you verified the signed artifact.

## Acceptance criteria

- [ ] A `ci.yml` gate on every PR (tests + lint + Paparazzi), with Gradle caching.
- [ ] A `release.yml` that triggers only on `v*` tags and `needs:` the gate job.
- [ ] Both `:app` and `:wear` have a `signingConfig` reading from env vars; no keystore is committed.
- [ ] The keystore and the Play JSON are GitHub secrets, decoded into `$RUNNER_TEMP`, never in the repo or an artifact.
- [ ] A `fastlane release_all` lane builds the signed phone AAB and the signed Wear APK and uploads to internal (or the no-account artifact path), runnable identically locally and in CI.
- [ ] A `production_rollout` lane exists, is human-gated (not auto-triggered), and is documented as such.
- [ ] A green tag run exists (screenshot it): pushing `v0.1.0` produced a build on internal (or a verified signed AAB artifact).
- [ ] `RELEASE.md` documents every secret, the signing model, and the human gate honestly.
- [ ] Both modules build with **0 warnings**.

## What "great" looks like

A weak submission says "I have a workflow that uploads to Play." A great submission says:

> Pushing `v0.1.0` triggers `release.yml`, which runs the test+Paparazzi gate, then a `release` job that decodes the upload keystore and the Play service-account JSON into `$RUNNER_TEMP` (gone when the job ends; never in the repo). `fastlane release_all` builds the signed phone AAB and the signed Wear APK — the signing config in both modules reads `KEYSTORE_FILE`/passwords from env fed by secrets — and uploads the AAB to the internal track as a draft. The `versionCode` comes from `github.run_number` so each tag is monotonic. The whole thing is gated: a failing unit or Paparazzi test in the `gate` job blocks the `release` job via `needs`. Promotion to closed/open and the production staged rollout are a documented `production_rollout` lane that a human runs while watching crash-free and ANR rates — I did *not* auto-trigger it, because exposing real users to new code is a judgment call, not a build step. `RELEASE.md` lists all five secrets, explains that my secret holds the *upload* key (Play holds the app signing key, so a lost upload key is recoverable), and documents the no-account fallback I used to verify signing without the fee.

Reproducible, gated, secret-safe, multi-artifact, and honest about the human gate. That's the senior release-engineering answer.

## Where this reappears

This pipeline is **capstone deliverable #7** verbatim: "GitHub Actions workflow that builds, tests, screenshots, and uploads to the Play internal track on tag." Building it now means the capstone's hardest infrastructure requirement is already done. And the secrets-hygiene discipline — base64 keystore, JSON in `$RUNNER_TEMP`, OIDC where available — is the same mindset Week 22 applies to the *app's* secrets: the Android Keystore, encrypted storage, and the Play Integrity service credentials.
