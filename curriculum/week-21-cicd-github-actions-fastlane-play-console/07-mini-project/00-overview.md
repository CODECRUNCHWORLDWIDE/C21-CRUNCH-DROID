# Mini-Project — The release workflow: tag to internal track

This week you build the **GitHub Actions release workflow** the capstone requires: on a tagged commit, it builds a signed AAB, runs the full test suite, generates Paparazzi screenshots, and uploads to the Play Console internal track via fastlane. One `git push --tags` turns into a tested, signed artifact on a track — no human running a build, no key in the repo, no skipped test.

The point of the project is not "write some YAML." It is to make a release a *reproducible consequence of a tag* — to wire the clean-runner build, the Gradle cache, the secrets decoded in memory, the signing config fed from the environment, and the fastlane upload, then watch the whole thing run green from the Actions tab. That "a tag is the release" instinct, with secrets and signing done correctly, is the senior skill this week installs — and it is exactly capstone deliverable #7.

This builds on a project you already have: the phone/foldable app (and ideally the `:wear` module) from Week 20, with the test suite from Week 17 and the R8 release build from Week 18. If your project is thin, the Week-7 `Scratch` app plus a couple of tests is enough to wire the full pipeline; the *pipeline* is the deliverable, not the app's size.

---

## Where you're starting from

- A repo on GitHub with an Android Compose app that builds with `./gradlew`.
- At least a unit test and a Paparazzi screenshot test (Week 17) so the gate is real.
- A release `signingConfig` you'll wire to read from environment variables (exercise 2).
- fastlane installed (a `Gemfile` pinning it, run via `bundle exec`).
- Optionally a Play Console account + service account; **the no-Play-account path is fully supported** (build + sign + verify + artifact upload, or F-Droid metadata).

## What you're building toward

By the end you have:

- A **CI workflow** (`ci.yml`) gating every PR on tests + lint + Paparazzi, with Gradle caching.
- A **release workflow** (`release.yml`) triggered only by a `v*` tag, gated on the tests, that builds a *signed* AAB.
- **Secrets** — the base64 keystore, its passwords, and the Play service-account JSON — stored in GitHub, decoded into `$RUNNER_TEMP`, never in the repo.
- A **fastlane `internal` lane** that uploads the signed AAB to the Play internal track (or, on the no-account path, validates the bundle and publishes it as a verified artifact).
- A **green tag run** you can screenshot: `v0.1.0` → checkout → cache → test → screenshot → sign → upload.
- A short `RELEASE.md` documenting the secrets and the human gate.

---

## Milestone 1 — The CI gate (≈ 1 h)

Wire the every-PR gate first (exercise 1). Fast feedback, no secrets:

```yaml
# .github/workflows/ci.yml
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
      - run: ./gradlew testDebugUnitTest lintDebug verifyPaparazziDebug --stacktrace
```

Decisions you must be able to defend in review:

- **Why gate on Paparazzi (`verifyPaparazziDebug`) but not Espresso?** Paparazzi runs on the plain JVM with no emulator — fast and deterministic, perfect for every PR. Espresso/instrumented tests need an emulator (slow, flaky); run a small smoke set on the release path, not on every PR.
- **Why `cache-read-only` on non-main refs?** So a malicious or buggy PR can't write a poisoned entry into the shared Gradle cache that a later trusted build reuses. Restore read-only on PRs; write only from `main`.

## Milestone 2 — The signing config from secrets (≈ 1 h)

Wire the release `signingConfig` to read from the environment (exercise 2), generate an upload keystore, and store it as a base64 secret:

```kotlin
// app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            System.getenv("KEYSTORE_FILE")?.let { ks ->
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

Store `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` as GitHub secrets. Add `*.jks` to `.gitignore`. Verify locally that exporting the env vars + `./gradlew assembleRelease` + `apksigner verify --print-certs` shows your upload key — the proof the wiring works *before* you depend on CI.

## Milestone 3 — The release workflow (≈ 1.5 h)

The tag-triggered, gated, signed build (lecture 1, §7):

```yaml
# .github/workflows/release.yml
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
    needs: [ gate ]
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
      - name: Build + upload to internal
        env:
          KEYSTORE_FILE:     ${{ env.KEYSTORE_FILE }}
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS:         ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD:      ${{ secrets.KEY_PASSWORD }}
          VERSION_CODE:      ${{ github.run_number }}
          VERSION_NAME:      ${{ github.ref_name }}
        run: bundle exec fastlane internal       # or `internal_dryrun` on the no-account path
```

The two conditions — `on: tags: [v*]` and `needs: [gate]` — together enforce "a release happens only on a tag, only when tests pass." The secrets touch only the `release` job, in `$RUNNER_TEMP`, gone with the VM.

## Milestone 4 — The fastlane lane (≈ 1 h)

The `internal` lane that bundles and uploads (exercise 3):

```ruby
# fastlane/Fastfile
default_platform(:android)
platform :android do
  lane :build do
    gradle(task: "bundle", build_type: "Release")
  end
  lane :internal do
    build
    upload_to_play_store(
      track: "internal",
      aab: "app/build/outputs/bundle/release/app-release.aab",
      release_status: "draft",
      skip_upload_metadata: true, skip_upload_images: true, skip_upload_screenshots: true
    )
  end
  lane :internal_dryrun do            # no-Play-account path
    build
    UI.message("Built + signed AAB; upload skipped (use upload-artifact).")
  end
end
```

`fastlane/Appfile` points `json_key_file` at `ENV["PLAY_SERVICE_ACCOUNT_JSON_PATH"]`. The lane runs identically locally (`bundle exec fastlane internal`) and in CI — that identity is the point.

## Milestone 5 — Screenshots in the pipeline (≈ 0.5 h)

Add Paparazzi as the visual gate (you already have it from Week 17 — it's in the `gate` job's `verifyPaparazziDebug`). If you have instrumented tests and want *listing* screenshots, add a `screengrab` lane (lecture 2, §5) on the release path. Note in `RELEASE.md` the difference: Paparazzi is the fast JVM *regression* gate on every PR; `screengrab` is the slower instrumented *listing-screenshot* generator on the release path.

## Milestone 6 — Run a tag, verify, document (≈ 1 h)

Push a tag and watch it:

```bash
git tag v0.1.0
git push origin v0.1.0
```

Open the Actions tab. Watch the `gate` job pass, then the `release` job: checkout → cache → keystore decode → JSON decode → build + upload. Confirm the build appears on the Play internal track (or, on the no-account path, that the signed AAB is a downloadable artifact and `apksigner` verifies it). Screenshot the green run.

Write `RELEASE.md`: list every secret and how it's injected, the upload-key-vs-app-signing-key model, and which steps are automated vs human-gated (the production rollout is a documented lane a human runs — it is *not* in this pipeline's automatic trigger).

---

## Acceptance criteria

- [ ] `ci.yml` gates every PR on tests + lint + Paparazzi, with Gradle caching (`cache-read-only` on non-main).
- [ ] `release.yml` triggers only on `v*` tags and `needs:` the gate job — a failing test blocks the release.
- [ ] The release `signingConfig` reads the keystore and passwords from env vars; no keystore is committed (`.gitignore` enforces it).
- [ ] The keystore (base64) and the Play JSON are GitHub secrets, decoded into `$RUNNER_TEMP`, never in the repo.
- [ ] A `fastlane internal` lane builds the signed AAB and uploads to the internal track (or `internal_dryrun` + `upload-artifact` on the no-account path), runnable identically locally and in CI.
- [ ] A green tag run exists and is screenshotted: a tag produced a build on internal (or a verified signed AAB artifact).
- [ ] `RELEASE.md` documents the secrets, the signing model, and the human gate.
- [ ] The project builds with **0 warnings, 0 errors**.

## Stretch goals

- **The Wear APK too.** Extend the lane to assemble and sign the `:wear` module's release APK alongside the phone AAB, mirroring the capstone's multi-form-factor delivery.
- **The OIDC path for Play auth.** Replace the `PLAY_SERVICE_ACCOUNT_JSON` secret with workload identity federation (`google-github-actions/auth` + `id-token: write`) so no long-lived JSON key exists. Note in `RELEASE.md` why this is a stronger posture (and why the keystore secret still can't be eliminated).
- **A staged-rollout lane, demonstrated.** Add `production_rollout` (rollout `0.1`) and, on a test app, actually run it to a 10% rollout, then advance to 100% — documenting the vitals you'd watch between steps. (Don't do this on a real public app casually.)
- **A release-notes flow.** Read release notes from `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and attach them with `supply`, so each tag carries its changelog.

## What this milestone earns you

You can now turn a tagged commit into a tested, signed, screenshotted artifact on a Play track with no human running a build and no secret in the repo — and you can explain every secret it touches and exactly where the human gate belongs. That is the literal "skill earned" line for the week: Android CI/CD without surprises, fastlane fluency, and Play Console API access with proper secrets management. This pipeline *is* capstone deliverable #7 — you've built it ahead of time. Week 22 next hardens the app the pipeline ships: the Android Keystore, encrypted storage, certificate pinning, and Play Integrity — the same secrets-discipline you used on the *release*, now applied to the *app*.
