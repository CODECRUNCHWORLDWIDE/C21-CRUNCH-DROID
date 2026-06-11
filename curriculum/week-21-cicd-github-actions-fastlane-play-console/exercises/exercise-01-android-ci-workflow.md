# Exercise 1 — An Android CI workflow with caching

**Goal.** Write a GitHub Actions workflow that builds and tests your app on every pull request, with Gradle caching wired correctly, then *measure* the difference between a cold (cache-miss) and a warm (cache-hit) run in the Actions tab. This is lecture 1's first half, proven: if you can see the warm build come in under two minutes where the cold build took eight, you understand why caching is the difference between a usable and a painful pipeline.

**Estimated time.** 50 minutes.

**Prerequisites.** A GitHub account and a repo with a small Android Compose project (the Week-7 `Scratch` app, or any project that builds with `./gradlew`). You need at least one unit test so the gate has something to run.

---

## Step 1 — A test worth gating on

If your project has no test, add a trivial one so the gate is real:

```kotlin
// app/src/test/java/com/crunch/scratch/SanityTest.kt
import org.junit.Test
import kotlin.test.assertEquals

class SanityTest {
    @Test fun `arithmetic still works`() {
        assertEquals(4, 2 + 2)
    }
}
```

Confirm `./gradlew testDebugUnitTest` passes locally before you wire CI — debug the test locally, not in the Actions tab.

## Step 2 — The workflow file

Create `.github/workflows/ci.yml`:

```yaml
name: CI
on:
  pull_request:
  push:
    branches: [ main ]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Gradle (with caching)
        uses: gradle/actions/setup-gradle@v4
        with:
          # On PRs (not main), restore the cache read-only so a PR can't poison it.
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}

      - name: Build and test
        run: ./gradlew testDebugUnitTest lintDebug --stacktrace
```

Commit it on a branch, push, and open a pull request against `main`.

## Step 3 — Watch the cold run

Open the **Actions** tab on GitHub. Your `CI` workflow runs against the PR. The **first** run is cold — no cache exists yet, so Gradle downloads every dependency and compiles everything. Note the total time of the `build-and-test` job (commonly 6–10 minutes for a fresh project). Expand the "Build and test" step and look for Gradle's cache-miss messages.

## Step 4 — Trigger a warm run and compare

Push a trivial second commit to the same PR (a comment change, a README tweak — anything that re-triggers CI but doesn't change dependencies). The **second** run reuses the cache `setup-gradle` saved from the first.

Open the new run and compare the `build-and-test` time to the cold run. You should see a **large** drop — often to under two minutes — because dependency resolution and task outputs are restored from the cache instead of recomputed.

Record your numbers in `notes/ci-timing.md`:

```text
Cold run (first):  __m __s
Warm run (second): __m __s
Speedup:           __x
```

## Step 5 — Make the gate bite

Prove the gate actually blocks bad code. Temporarily break the test:

```kotlin
@Test fun `arithmetic still works`() {
    assertEquals(5, 2 + 2)   // wrong on purpose
}
```

Push it. The `CI` workflow fails; the PR shows a red X. If you've enabled branch protection (**Settings ▸ Branches ▸ require status checks**), the *Merge* button is disabled. Revert the break and watch the PR go green and mergeable again. *That* is the gate doing its job.

---

## Acceptance criteria

- [ ] `.github/workflows/ci.yml` builds and tests on `pull_request` and on `push` to `main`.
- [ ] `gradle/actions/setup-gradle@v4` is configured with `cache-read-only` on non-main refs.
- [ ] You triggered a cold run and a warm run and recorded both times in `notes/ci-timing.md`, with the speedup.
- [ ] You demonstrated the gate failing on a broken test (red X, merge blocked) and recovering on the fix.
- [ ] No secrets used (this is the CI gate — secrets come in exercise 2).
- [ ] The project still builds with **0 warnings** locally.

## What you just proved

You proved lecture 1's first half: a clean-runner workflow gives reproducible builds, Gradle caching turns a painful cold build into a fast warm one (you have the numbers), and `needs`/branch-protection make a failing test *block* a merge. The runner is clean, the gate bites, and the cache makes it fast enough to live with. Every release pipeline this week sits on top of this.

---

## Hints (read only if stuck > 10 min)

- **The workflow doesn't run at all.** The file must be at `.github/workflows/ci.yml` exactly (the `.github/workflows/` path is required), and the YAML must be valid — a single bad indent silently disables it. Check the Actions tab for a parse error.
- **Cold and warm times are the same.** The cache isn't being restored. Confirm `setup-gradle` runs *before* the `./gradlew` step, and that the second commit didn't change a Gradle/lock file (which would invalidate the cache key correctly).
- **`./gradlew: Permission denied`.** The wrapper script lost its executable bit on checkout. `git update-index --chmod=+x gradlew` and recommit, or add `chmod +x gradlew` as a step.
- **Lint fails the build and you only wanted tests.** That's fine — lint *is* part of the gate. If lint is too noisy for now, drop `lintDebug` and gate on `testDebugUnitTest` alone, then re-add lint once it's clean.
- **The merge isn't blocked even though CI is red.** Branch protection isn't on. Settings ▸ Branches ▸ Add rule ▸ require the `build-and-test` status check to pass.
