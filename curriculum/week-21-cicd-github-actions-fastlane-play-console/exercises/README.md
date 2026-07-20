# Week 21 — Exercises

Short, focused drills. Each one should take 30–55 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — An Android CI workflow](exercise-01-android-ci-workflow.md)** — write a GitHub Actions workflow that builds and tests on every PR with Gradle caching, push it, open a PR, and *measure* the warm-vs-cold build time. The whole point of lecture 1's first half, proven by the Actions tab. (~50 min)
2. **[Exercise 2 — A signing config fed by secrets](exercise-02-signing-config-from-secrets.kt)** — wire a `signingConfig` that reads the keystore path and passwords from environment variables, encode a keystore as a base64 secret, decode it in the workflow, build a signed release, and *verify the signature* with `apksigner`. You prove the secret never touches the repo. (~45 min)
3. **[Exercise 3 — A fastlane supply lane](exercise-03-fastlane-supply-lane.kt)** — author a `Fastfile` lane (and the Gradle/Kotlin glue) that builds a signed AAB and uploads it to the Play internal track with a draft status — or, on the no-account path, validates and publishes the bundle as a verified artifact. (~50 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Run the workflows on a **real GitHub repo** (a throwaway repo with a tiny Compose app is fine — even the Week-7 `Scratch` app). Watch them in the **Actions** tab.
- The `.kt`/`.rb`/`.yml` content drops into your project (`build.gradle.kts`, `.github/workflows/`, `fastlane/`). Each file's header says where.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** (the Gradle side) and pass its stated acceptance criteria. A keystore in the repo, or a release step that runs when tests fail, is a bug this week — the "reproducible, gated, secret-safe" rule is the arbiter, not your intuition.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-21` to compare.
