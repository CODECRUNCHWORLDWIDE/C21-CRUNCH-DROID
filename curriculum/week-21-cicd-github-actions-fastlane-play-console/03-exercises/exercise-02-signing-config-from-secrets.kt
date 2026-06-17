// Exercise 2 — A signing config fed by secrets (the keystore never touches the repo)
//
// Goal: Wire a release `signingConfig` that reads the keystore path and passwords
//       from ENVIRONMENT VARIABLES (fed by GitHub secrets in CI), encode a keystore
//       as a base64 secret, decode it in the workflow into $RUNNER_TEMP, build a
//       signed release, and VERIFY the signature with apksigner. The lesson: the
//       upload key signs the bundle, but the key file lives only in an encrypted
//       secret decoded in memory — never in the repo, never in an artifact.
//
// Estimated time: 45 minutes.
//
// HOW TO USE THIS FILE
//
// The Kotlin here goes in app/build.gradle.kts. The shell/YAML in the comments
// goes in your workflow and your local terminal. Run the verification locally
// first (you control the keystore), then wire the same thing in CI.
//
//   1. Generate an UPLOAD keystore (keytool, below) — this is your upload key.
//   2. Add the signingConfig (below) to app/build.gradle.kts, reading from env.
//   3. Locally: export the env vars, run ./gradlew bundleRelease, verify.
//   4. In CI: base64 the keystore into a secret, decode in the workflow, build.
//
// ACCEPTANCE CRITERIA
//
//   [ ] The signingConfig reads storeFile/passwords from System.getenv, never from
//       a committed keystore.properties.
//   [ ] A debug build still works locally with the env vars unset (the config is
//       guarded for the absent case).
//   [ ] The keystore is stored as a base64 GitHub secret and decoded into
//       $RUNNER_TEMP in the workflow — it is NOT in the repo.
//   [ ] You verified the signed output with `apksigner verify --print-certs` and it
//       shows your upload key's certificate.
//   [ ] You can explain why a lost upload key is recoverable (Play App Signing).
//   [ ] Builds with 0 warnings.
//
// Inline hints are at the bottom. Don't peek until you've tried for 15 minutes.

// ----------------------------------------------------------------------------
// STEP 0 — Generate the UPLOAD keystore (run once, locally). DO NOT COMMIT IT.
//
//   keytool -genkeypair -v \
//     -keystore upload-keystore.jks \
//     -alias upload -keyalg RSA -keysize 2048 -validity 9125 \
//     -storepass changeit -keypass changeit \
//     -dname "CN=Crunch Weather, OU=Eng, O=Crunch, C=GB"
//
//   Add `*.jks` and `keystore.properties` to .gitignore IMMEDIATELY.
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
// STEP 1 — The signingConfig in app/build.gradle.kts. Reads from env vars so the
// secret values come from CI; guarded so a local debug build works without them.
// ----------------------------------------------------------------------------

/*
android {
    signingConfigs {
        create("release") {
            // CI sets these from secrets (step 2). Locally they're usually unset.
            val ksFile = System.getenv("KEYSTORE_FILE")
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
            // If ksFile is null (local dev), this config is left empty and the
            // release build would be UNSIGNED locally — that's fine; CI signs it.
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true                         // R8 (Week 18)
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
*/

// ----------------------------------------------------------------------------
// STEP 2 — Encode the keystore as a base64 secret, and the workflow that decodes
// it. The keystore is BINARY, so base64 turns it into a string a secret can hold.
//
//   # Locally, copy the base64 string to your clipboard:
//   base64 -i upload-keystore.jks | pbcopy        # macOS
//   base64 -w0 upload-keystore.jks                # Linux (no line wraps)
//
//   # In GitHub: Settings > Secrets and variables > Actions > New secret:
//   #   KEYSTORE_BASE64      = <the base64 string>
//   #   KEYSTORE_PASSWORD    = changeit
//   #   KEY_ALIAS            = upload
//   #   KEY_PASSWORD         = changeit
//
//   # In .github/workflows/release.yml (the release job from lecture 1):
//
//   - name: Decode keystore
//     env:
//       KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
//     run: |
//       echo "$KEYSTORE_BASE64" | base64 --decode > "$RUNNER_TEMP/upload-keystore.jks"
//       echo "KEYSTORE_FILE=$RUNNER_TEMP/upload-keystore.jks" >> "$GITHUB_ENV"
//
//   - name: Build signed bundle
//     env:
//       KEYSTORE_FILE:     ${{ env.KEYSTORE_FILE }}
//       KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
//       KEY_ALIAS:         ${{ secrets.KEY_ALIAS }}
//       KEY_PASSWORD:      ${{ secrets.KEY_PASSWORD }}
//     run: ./gradlew bundleRelease --stacktrace
//
// The keystore exists only in $RUNNER_TEMP, on a throwaway VM, for one job. Gone
// when the job ends. Never in the repo, never in an uploaded artifact.
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
// STEP 3 — Verify the signature. An AAB isn't directly verifiable with apksigner;
// build an APK set first, OR just sign and verify an APK to prove the wiring.
//
//   # Local proof with an APK (fastest path to "the signing wiring works"):
//   export KEYSTORE_FILE=$PWD/upload-keystore.jks
//   export KEYSTORE_PASSWORD=changeit KEY_ALIAS=upload KEY_PASSWORD=changeit
//   ./gradlew assembleRelease
//   apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
//
//   # Expect output naming your CN ("Crunch Weather"). That cert is the UPLOAD key;
//   # Play re-signs with the APP SIGNING key (which Google holds) on delivery.
// ----------------------------------------------------------------------------

// A tiny JVM test you CAN check in — it documents the policy that the keystore is
// never in the repo. (Drop into app/src/test.) It fails CI if someone commits a
// .jks under the module, catching the most common signing-security mistake.
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class NoKeystoreInRepoTest {
    @Test fun `no keystore files are committed under the module`() {
        // Walk the module dir; assert no *.jks / *.keystore is tracked here.
        val moduleRoot = File(System.getProperty("user.dir"))   // the module dir under test
        val offenders = moduleRoot.walkTopDown()
            .filter { it.isFile }
            .filter { it.extension == "jks" || it.extension == "keystore" }
            // allow a debug keystore if your project intentionally checks one in:
            .filterNot { it.name == "debug.keystore" }
            .toList()
        assertTrue(
            offenders.isEmpty(),
            "Committed keystore(s) found: ${offenders.map { it.name }}. " +
                "Upload keys belong in an encrypted secret, never in the repo."
        )
    }
}

// ----------------------------------------------------------------------------
// WHY A LOST UPLOAD KEY IS RECOVERABLE (write it before reading):
//
//   Play App Signing uses TWO keys. The APP SIGNING key (signs what users
//   install) is held by GOOGLE — you never have it, so you can't lose it. The
//   UPLOAD key (what CI signs the bundle with, proving the upload is yours) is the
//   only one you hold. If you lose the upload key, you ask Google to reset it and
//   register a new one — the app-signing key is untouched, so users keep updating
//   normally. Before Play App Signing, losing your single signing key meant you
//   could NEVER update the app again. That disaster is gone.
// ----------------------------------------------------------------------------
// HINTS (read only if stuck > 15 min)
// ----------------------------------------------------------------------------
//
// - `keytool: command not found`. It ships with the JDK; use the full path
//   ($JAVA_HOME/bin/keytool) or ensure your JDK's bin is on PATH.
//
// - Release build is unsigned locally. Expected if KEYSTORE_FILE is unset — the
//   guarded config leaves signing empty. Export the four env vars (step 3) to sign
//   locally, or let CI sign it. The point is the config READS from env.
//
// - apksigner says "DOES NOT VERIFY". You signed with the wrong alias/password, or
//   the env vars didn't reach the build. Echo (don't log secrets!) that
//   KEYSTORE_FILE points at a real file and the alias matches the keystore.
//
// - base64 on Linux wraps lines and breaks decoding. Use `base64 -w0` (no wrap)
//   when creating the secret.
//
// - Tempted to commit keystore.properties with the passwords "just for now". Don't.
//   The NoKeystoreInRepoTest above and your .gitignore exist to stop exactly that.
//
// ----------------------------------------------------------------------------
