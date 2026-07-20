# Week 21 — Resources

Every primary resource on this page is **free**. GitHub Actions has a generous free tier. fastlane is open source. The Play Console API docs are free; the Play Console itself has a one-time USD 25 developer fee (and this week's no-account path means it never blocks your learning). The conference talks are free on YouTube. A couple of paid items are listed at the bottom and clearly marked.

## Required reading (work it into your week)

- **"GitHub Actions — understanding workflows."** The framing document for the whole CI model — workflows, jobs, steps, runners, triggers. Read this before you write a single `.yml`:
  <https://docs.github.com/en/actions/using-workflows/about-workflows>
- **"Building and testing Android" (GitHub Actions guide).** The official Android-on-Actions walkthrough — JDK setup, the SDK, Gradle build and test:
  <https://docs.github.com/en/actions/automating-builds-and-tests/building-and-testing-java-with-gradle>
- **`gradle/actions/setup-gradle`.** The caching action that makes warm CI builds fast — the cache model, the cache key, read-only caching on PRs:
  <https://github.com/gradle/actions/tree/main/setup-gradle>
- **"Use Play App Signing."** The two-key model (upload key vs app signing key), enrollment, and key rotation — central to lecture 1:
  <https://support.google.com/googleplay/android-developer/answer/9842756>
- **"Encrypted secrets" (GitHub Actions).** Storing the keystore and the service-account JSON safely, and how secrets are exposed to a workflow:
  <https://docs.github.com/en/actions/security-guides/using-secrets-in-github-actions>

## Signing and secrets — the dangerous part

- **"Sign your app" (Android docs)** — `signingConfig`, the upload key, `apksigner`, and verifying a signature:
  <https://developer.android.com/studio/publish/app-signing>
- **`r0adkll/sign-android-release`** (or signing inline) — a common Action for decoding a base64 keystore and signing in CI; read it to understand what it does so you can also do it by hand:
  <https://github.com/r0adkll/sign-android-release>
- **"OpenID Connect in GitHub Actions"** — the keyless alternative to long-lived secrets for cloud auth (the OIDC path lecture 1 contrasts with base64 secrets):
  <https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/about-security-hardening-with-openid-connect>
- **"Workload identity federation" (Google Cloud)** — the GCP side of OIDC, so a GitHub workflow can mint a short-lived token instead of holding a service-account key:
  <https://cloud.google.com/iam/docs/workload-identity-federation>

## fastlane — the lanes

- **fastlane docs — getting started (Android)** — `Fastfile`, `Appfile`, lanes, the install:
  <https://docs.fastlane.tools/getting-started/android/setup/>
- **`supply`** — uploading metadata, screenshots, and binaries to the Play Console API; the four tracks; staged rollout:
  <https://docs.fastlane.tools/actions/supply/>
- **`screengrab`** — UI-test-driven localized screenshots for the Play listing:
  <https://docs.fastlane.tools/actions/screengrab/>
- **`gradle` / `build_android_app`** — building and bundling from a lane:
  <https://docs.fastlane.tools/actions/gradle/>

## The Play Console API

- **"Google Play Developer API — overview"** — the Android Publisher API that `supply` drives under the hood; the edit/commit transaction model:
  <https://developers.google.com/android-publisher>
- **"Create a service account for the Play Console"** — granting the *Android Publisher* role and downloading the JSON key fastlane needs:
  <https://docs.fastlane.tools/actions/supply/#setup>
- **"Tracks and releases"** — internal, closed, open, production; promoting between them; staged rollouts:
  <https://support.google.com/googleplay/android-developer/answer/9859348>

## The workflow, read at the source

You learn more from one hour reading a real Android CI workflow than three hours of docs. Read how a large project wires its pipeline:

- **`android/nowinandroid` → `.github/workflows/`** — Google's reference app's CI: build, test, lint, and a release path. The single best real-world Android Actions reference:
  <https://github.com/android/nowinandroid/tree/main/.github/workflows>
- **`chrisbanes/tivi` → `.github/workflows/`** — a real, large Compose app with a full release pipeline, fastlane, and signing-from-secrets:
  <https://github.com/chrisbanes/tivi/tree/main/.github/workflows>
- **`ReactiveCircus/android-emulator-runner`** — the de-facto Action for running instrumented tests on an emulator in CI; read its README for the gotchas:
  <https://github.com/ReactiveCircus/android-emulator-runner>

## Talks (free, watch in this order)

- **"CI/CD for Android with GitHub Actions"** — the practical end-to-end; search the current year's Android conference playlists:
  <https://www.youtube.com/results?search_query=android+ci+cd+github+actions>
- **"Shipping with fastlane"** — the lanes-and-supply workflow demonstrated:
  <https://www.youtube.com/results?search_query=fastlane+android+supply+play+console>
- **"App signing on Android"** (Google I/O) — the Play App Signing two-key model explained by the team:
  <https://www.youtube.com/results?search_query=google+io+play+app+signing>

## Tools you'll use this week

- **GitHub Actions** — the free tier (2,000 minutes/month for private repos, unlimited for public) is plenty. Public repos cost nothing.
- **fastlane** — `brew install fastlane` (macOS) or `gem install fastlane`, or the bundled `Gemfile` + `bundle exec fastlane` path for a pinned version.
- **`base64`** — to encode your keystore into a secret: `base64 -i upload-keystore.jks | pbcopy` (macOS) and paste into a GitHub secret.
- **`apksigner` / `bundletool`** — to verify a signed APK/AAB locally: `apksigner verify --print-certs app-release.apk`.
- **A Google Cloud service account** (free) with the Android Publisher role and a JSON key, for the real Play upload. The no-account path skips this.

## Free books and codelabs (chapter-level, not whole books)

- **"Continuous integration for Android" codelab / pathway** — a free guided build of an Android CI pipeline:
  <https://developer.android.com/codelabs/build-android-ci>
- **The fastlane "android" example** in the fastlane repo — a runnable reference `Fastfile`:
  <https://github.com/fastlane/fastlane/tree/master/fastlane/examples>

## Paid items (optional, clearly marked)

- **Play Console developer account** — a one-time **USD 25** fee for the real upload. Recommended but not required: the no-Play-account path (build + sign + verify, F-Droid metadata) covers every learning objective without it.
- **"Continuous Delivery in Practice" type courses** (various platforms) (paid). Not Android-specific, but useful if you want the broader release-engineering theory behind the pipeline.

---

*If a link 404s, please open an issue so we can replace it.*
