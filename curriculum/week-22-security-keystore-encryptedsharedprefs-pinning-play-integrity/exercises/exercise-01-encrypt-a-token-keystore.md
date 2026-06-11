# Exercise 1 — Encrypt a token with the Keystore

**Goal.** Take a token stored in plaintext `SharedPreferences`, replace it with a Keystore-backed `EncryptedSharedPreferences` store, and then *prove* it by pulling the file off the device and confirming the on-disk bytes are ciphertext — not your readable token. This is lecture 1's core claim made visible: a secret you can `adb pull` and read is not a secret; after this exercise, you can pull it and see ciphertext.

**Estimated time.** 45 minutes.

**Prerequisites.** Android Studio Ladybug+, an emulator or a debuggable device, `adb` on your PATH. The Week-14 notes app is the natural host; any app that writes a string to `SharedPreferences` works.

---

## Step 1 — Establish the insecure baseline (so you can see the before)

Write the plaintext version first and confirm it's readable. This is the thing you're about to fix:

```kotlin
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    fun save(token: String) = prefs.edit().putString("token", token).apply()
    fun read(): String? = prefs.getString("token", null)
}
```

Call `TokenStore(this).save("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.SECRET")` somewhere on launch. Run it.

## Step 2 — Read the plaintext off the device (the "before" proof)

```bash
# For a debuggable build:
adb shell run-as com.crunch.notes cat shared_prefs/auth.xml
```

You will see your token *verbatim* in the XML:

```xml
<map><string name="token">eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.SECRET</string></map>
```

That readable token is the vulnerability. Anyone with the device (rooted), an ADB backup, or a forensic image reads it. Screenshot this — it's your "before."

## Step 3 — Add the Jetpack Security dependency

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("androidx.security:security-crypto:1.1.0-alpha06")  // or the current version
}
```

(Note: the library is in maintenance mode — lecture 1, §3. It's the right tool for this exercise; the Tink-direct path is the modern alternative you'd reach for in greenfield production code.)

## Step 4 — Replace with a Keystore-backed encrypted store

Keep the *same API* so the rest of the app doesn't change — only the storage hardens:

```kotlin
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureTokenStore(context: Context) {
    // A Keystore-backed master key (AES-256-GCM). Generated/looked-up by alias;
    // its private material never leaves secure hardware.
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_auth",                                  // a NEW file (the old one stays plaintext)
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(token: String) = prefs.edit().putString("token", token).apply()
    fun read(): String? = prefs.getString("token", null)
}
```

Swap the call site to `SecureTokenStore(this).save(...)`. Run it. Confirm the app *reads the token back correctly* — encryption is transparent on read, so `read()` returns your original token.

## Step 5 — Read the encrypted file (the "after" proof)

```bash
adb shell run-as com.crunch.notes cat shared_prefs/secure_auth.xml
```

Now you see **ciphertext** — base64 blobs for both the key names and the values, *nothing* resembling your token:

```xml
<map>
  <string name="AXt9c2...base64...">Z3ZqY2...different base64...==</string>
</map>
```

The token is gone from the readable bytes. The key that decrypts it lives in the Keystore (hardware-backed), unreachable even on a rooted device. Screenshot this — your "after." Record both in `notes/at-rest.md`.

## Step 6 — Migrate and delete the plaintext (cleanup)

Don't leave the old plaintext file behind — it's still a leak. On first run of the secure version, read the old token (if present), write it to the secure store, and *clear* the old prefs:

```kotlin
fun migrateAndClearLegacy(context: Context, secure: SecureTokenStore) {
    val legacy = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    legacy.getString("token", null)?.let { token ->
        secure.save(token)
        legacy.edit().clear().apply()    // remove the plaintext copy
    }
}
```

Confirm `auth.xml` is now empty (or gone) and only `secure_auth.xml` (ciphertext) holds the token.

---

## Acceptance criteria

- [ ] A `SecureTokenStore` using `EncryptedSharedPreferences` with a Keystore-backed `MasterKey`.
- [ ] The app reads the token back correctly (encryption transparent on read).
- [ ] `adb` shows `auth.xml` plaintext (before) and `secure_auth.xml` ciphertext (after); both recorded in `notes/at-rest.md`.
- [ ] The legacy plaintext is migrated and cleared (no readable token left on disk).
- [ ] You can state, in one sentence, the threat this defends against *and* one it doesn't (data at rest vs a compromised running process).
- [ ] Build with **0 warnings, 0 errors**.

## What you just proved

You proved lecture 1's central claim with your eyes: a plaintext secret is readable off the device, and a Keystore-backed encrypted store turns the on-disk bytes into ciphertext whose key can't be extracted even from a rooted device. You also practiced the discipline that matters more than the API — *prove the control*, don't assert it. `adb pull` is the attacker's tool; you used it to confirm the fix works.

---

## Hints (read only if stuck > 10 min)

- **`run-as` says "not debuggable".** `run-as` only works on debuggable builds. Use a debug build, or root the emulator (`adb root`) and `adb pull /data/data/<pkg>/shared_prefs/secure_auth.xml`.
- **`EncryptedSharedPreferences.create` throws on second run.** A keyset mismatch — usually you changed the master-key scheme or the file got into a bad state in dev. Uninstall the app (`adb uninstall <pkg>`) to clear the Keystore + files and start clean. (In production you handle this with care; in dev, reinstall.)
- **The "after" file still shows readable text.** You're reading the *old* `auth.xml`, not `secure_auth.xml`. Confirm the filename, and that you actually called `SecureTokenStore.save`.
- **`MasterKey`/`EncryptedSharedPreferences` unresolved.** Add `androidx.security:security-crypto`. Use a version your project resolves (the 1.1.0-alpha line is current for this API).
- **You want the modern path.** The Tink-direct approach (lecture 1, §3) is the maintained alternative. For this exercise, `EncryptedSharedPreferences` is fine and the model is identical — a Keystore master key wrapping AEAD data keys.
