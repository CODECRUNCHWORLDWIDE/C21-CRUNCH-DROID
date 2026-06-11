// Exercise 3 — A fastlane supply lane: tag -> signed AAB -> internal track
//
// Goal: Author a fastlane Fastfile lane that builds a signed AAB and uploads it to
//       the Play internal track (draft status), plus the Gradle/Kotlin glue and the
//       CI step that runs it. On the no-Play-account path, replace the upload with
//       a validate-only dry run and publish the signed bundle as a workflow
//       artifact. The lesson: the release is ONE named lane that runs identically
//       on your laptop and on the runner.
//
// Estimated time: 50 minutes.
//
// HOW TO USE THIS FILE
//
// The Ruby goes in fastlane/Fastfile and fastlane/Appfile. The Kotlin is a tiny
// version-code helper for build.gradle.kts. The YAML goes in the release job.
// Run `bundle exec fastlane internal` locally first if you have a service account;
// otherwise run the no-account `internal_dryrun` lane.
//
//   1. Install fastlane (Gemfile + `bundle install`, or `gem install fastlane`).
//   2. Create a Play service account (Android Publisher role) and download its JSON
//      — OR skip it and use the no-account lane.
//   3. Write the Fastfile/Appfile (below) and run the lane.
//   4. Wire `bundle exec fastlane internal` as the release job's last step.
//
// ACCEPTANCE CRITERIA
//
//   [ ] A `build` lane bundles a signed Release AAB (reusing exercise 2's signing).
//   [ ] An `internal` lane uploads that AAB to the Play internal track as a DRAFT,
//       via upload_to_play_store (supply). (Or `internal_dryrun` validates + uploads
//       the AAB as a workflow artifact on the no-account path.)
//   [ ] The service-account JSON is read from a path fed by a SECRET, never
//       committed.
//   [ ] The lane runs identically locally and in CI (same Fastfile).
//   [ ] You can name the four tracks and explain why internal is the CI target.
//   [ ] Gradle side builds with 0 warnings.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

// ----------------------------------------------------------------------------
// STEP 1 — A monotonic version code (so each tag uploads a higher code). Play
// rejects an AAB whose versionCode isn't greater than the last on that track.
// A clean trick: derive it from the CI run number, falling back to 1 locally.
// In app/build.gradle.kts:
//
//   android {
//       defaultConfig {
//           versionCode = (System.getenv("VERSION_CODE")?.toInt()) ?: 1
//           versionName = System.getenv("VERSION_NAME") ?: "1.0.0-dev"
//       }
//   }
//
// And in the workflow, feed them from the tag and the run number:
//   env:
//     VERSION_CODE: ${{ github.run_number }}
//     VERSION_NAME: ${{ github.ref_name }}     # e.g. "v1.4.0"
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
// STEP 2 — fastlane/Appfile (app identity + service account path):
//
//   package_name("com.crunch.weather")
//   json_key_file(ENV["PLAY_SERVICE_ACCOUNT_JSON_PATH"])   # fed by a secret in CI
//
// ----------------------------------------------------------------------------
// STEP 3 — fastlane/Fastfile (the lanes):
//
//   default_platform(:android)
//
//   platform :android do
//
//     desc "Build a signed release AAB"
//     lane :build do
//       gradle(task: "bundle", build_type: "Release")     # ./gradlew bundleRelease
//     end
//
//     desc "Upload the signed AAB to the Play internal track as a draft"
//     lane :internal do
//       build
//       upload_to_play_store(                              # = supply
//         track: "internal",
//         aab: "app/build/outputs/bundle/release/app-release.aab",
//         release_status: "draft",                         # sits as a draft; not auto-published
//         skip_upload_metadata: true,
//         skip_upload_images: true,
//         skip_upload_screenshots: true
//       )
//     end
//
//     desc "NO-PLAY-ACCOUNT PATH: build, validate the bundle, keep the AAB for upload-artifact"
//     lane :internal_dryrun do
//       build
//       # If you have a service account but don't want to publish, validate only:
//       # upload_to_play_store(track: "internal", validate_only: true,
//       #   aab: "app/build/outputs/bundle/release/app-release.aab")
//       # Otherwise the workflow's actions/upload-artifact step keeps the signed AAB.
//       UI.message("AAB built and signed; upload to internal skipped (no-account path).")
//     end
//
//     desc "HUMAN-GATED: start a 10% production rollout. NOT run on every tag."
//     lane :production_rollout do
//       upload_to_play_store(
//         track: "production",
//         rollout: "0.1",                  # 10% staged rollout
//         release_status: "inProgress"
//       )
//     end
//   end
//
// ----------------------------------------------------------------------------
// STEP 4 — The release job's tail (.github/workflows/release.yml). Reuses the
// keystore decode from exercise 2; adds the service-account JSON and the lane.
//
//   - uses: ruby/setup-ruby@v1
//     with: { ruby-version: '3.2', bundler-cache: true }
//
//   - name: Write Play service-account JSON
//     env: { PLAY_JSON: '${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}' }
//     run: |
//       echo "$PLAY_JSON" > "$RUNNER_TEMP/play.json"
//       echo "PLAY_SERVICE_ACCOUNT_JSON_PATH=$RUNNER_TEMP/play.json" >> "$GITHUB_ENV"
//
//   - name: Build + upload to internal
//     env:
//       KEYSTORE_FILE:     ${{ env.KEYSTORE_FILE }}
//       KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
//       KEY_ALIAS:         ${{ secrets.KEY_ALIAS }}
//       KEY_PASSWORD:      ${{ secrets.KEY_PASSWORD }}
//       VERSION_CODE:      ${{ github.run_number }}
//       VERSION_NAME:      ${{ github.ref_name }}
//     run: bundle exec fastlane internal       # or `internal_dryrun` on the no-account path
//
//   # No-account path only — keep the signed AAB downloadable from the run:
//   - uses: actions/upload-artifact@v4
//     with:
//       name: app-release-aab
//       path: app/build/outputs/bundle/release/app-release.aab
// ----------------------------------------------------------------------------

// A small JVM test you CAN check in: it pins the four track names so a typo in the
// Fastfile (e.g. "internel") is caught by a human reviewer comparing against this
// documented set. (Drop into app/src/test.) This is documentation-as-a-test.
import org.junit.Test
import kotlin.test.assertEquals

class PlayTrackPolicyTest {
    @Test fun `the four Play tracks, smallest to largest audience`() {
        val tracksInPromotionOrder = listOf("internal", "closed", "open", "production")
        // internal is the CI target (every tag); production is human-gated rollout.
        assertEquals("internal", tracksInPromotionOrder.first())
        assertEquals("production", tracksInPromotionOrder.last())
        assertEquals(4, tracksInPromotionOrder.size)
    }
}

// ----------------------------------------------------------------------------
// WHY INTERNAL IS THE CI TARGET (write it before reading):
//
//   The four tracks go internal -> closed -> open -> production, smallest/safest
//   audience to largest/riskiest. Internal testing is ~100 named testers, available
//   in minutes with minimal review — perfect for an automated, every-tag upload:
//   the team gets the build fast and privately. Closed/open/production reach more
//   users and you PROMOTE the proven artifact to them with intent, not on every
//   tag. Production specifically uses a STAGED ROLLOUT a human advances while
//   watching vitals. CI automates up to internal; humans gate the rest.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - "Google Api Error: ... versionCode N has already been used". Each upload needs
//   a HIGHER versionCode than the last on that track. Use the run-number trick
//   (step 1) so it increments automatically.
//
// - supply can't authenticate. The service account needs the *Android Publisher*
//   permission granted in the Play Console (Users & permissions), and the JSON path
//   must point at a real file. In CI it comes from $RUNNER_TEMP via the secret.
//
// - First upload to internal fails because the app doesn't exist yet. You must
//   create the app in the Play Console and upload one bundle MANUALLY once (the
//   console requires a first manual release to establish the app). After that,
//   supply uploads work. (No-account path: use internal_dryrun + upload-artifact.)
//
// - `gradle(task: "bundle", build_type: "Release")` builds debug. Confirm the task
//   resolves to bundleRelease — check `./gradlew tasks | grep bundle`.
//
// - You want to test the lane without publishing. Use release_status: "draft" (it
//   uploads but doesn't roll out) or validate_only: true (it checks without
//   uploading). Both are safe to run repeatedly.
//
// ----------------------------------------------------------------------------
