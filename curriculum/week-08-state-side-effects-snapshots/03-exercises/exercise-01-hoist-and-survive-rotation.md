# Exercise 1 — Hoist state, then survive rotation

**Goal.** Take a stateful composable, hoist its state to make it stateless (UDF), then move the owner's state from `remember` to `rememberSaveable` and *prove* it survives a rotation and a process death. This is lecture 1's two big ideas — hoisting and retention boundaries — in one screen. If you can do this, you understand where state lives and which boundary each holder crosses.

**Estimated time.** 40 minutes.

**Prerequisites.** Android Studio Ladybug+, a Pixel 8 API 35 emulator. The `Scratch` Compose app from Week 7 works, or a fresh Empty Activity project.

---

## Step 1 — Start with a stateful composable (the "before")

In your app, add this deliberately-stateful login form. It owns its own state, which makes it hard to test, preview, and reuse:

```kotlin
@Composable
fun LoginFormStateful() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp)) {
        TextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
        TextField(value = password, onValueChange = { password = it }, label = { Text("Password") })
        Button(onClick = { /* log in with email, password */ }) { Text("Log in") }
    }
}
```

Run it. Type into both fields. Now **rotate the emulator** (`Ctrl+F11`). The fields clear — `remember` doesn't survive a configuration change. That's the bug we'll fix, but first we'll hoist.

## Step 2 — Hoist the state (make it stateless)

Refactor `LoginForm` to take its values and emit events, and move the state ownership up to a caller:

```kotlin
// STATELESS: a pure function of its inputs. Testable, previewable, reusable.
@Composable
fun LoginForm(
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        TextField(value = email, onValueChange = onEmailChange, label = { Text("Email") })
        TextField(value = password, onValueChange = onPasswordChange, label = { Text("Password") })
        Button(onClick = onSubmit) { Text("Log in") }
    }
}

// OWNER: holds the state, passes values down and events up (UDF).
@Composable
fun LoginScreen() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    LoginForm(
        email = email,
        password = password,
        onEmailChange = { email = it },
        onPasswordChange = { password = it },
        onSubmit = { /* log in */ }
    )
}
```

State flows **down** (the `email`/`password` parameters), events flow **up** (the `on*Change`/`onSubmit` lambdas). That's unidirectional data flow.

## Step 3 — Prove it's reusable with a preview

Because `LoginForm` is stateless, you can render it in a `@Preview` with any values — no state to stand up:

```kotlin
@Preview
@Composable
fun LoginFormPreview() {
    LoginForm(
        email = "ada@example.com",
        password = "secret",
        onEmailChange = {},
        onPasswordChange = {},
        onSubmit = {}
    )
}
```

The stateful version couldn't be previewed with a chosen value — that's one concrete payoff of hoisting. Open the preview pane and confirm it renders the supplied values.

## Step 4 — Survive rotation with `rememberSaveable`

In the *owner* (`LoginScreen`), change `remember` to `rememberSaveable`:

```kotlin
@Composable
fun LoginScreen() {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    LoginForm(
        email = email,
        password = password,
        onEmailChange = { email = it },
        onPasswordChange = { password = it },
        onSubmit = { /* log in */ }
    )
}
```

Run, type into both fields, and **rotate** (`Ctrl+F11`). The text stays. Only the owner changed; the stateless `LoginForm` didn't change at all — proving the component doesn't care where its state comes from.

## Step 5 — The harder test: process death

Rotation is the easy half. Force process death:

1. In the emulator, **Settings ▸ System ▸ Developer options ▸ "Don't keep activities"** → ON. (Enable Developer options by tapping Build Number 7 times if needed.)
2. Run the app, type into the fields.
3. Press Home (background the app). With "Don't keep activities" on, the Activity is destroyed immediately.
4. Reopen the app from Recents. **The text is still there** — `rememberSaveable` wrote it to the saved-state bundle, which survives the Activity's destruction.

Now flip `rememberSaveable` back to `remember` and repeat: the text is gone. That single-word difference is the retention boundary from lecture 1, §5, proven with the harshest test short of `adb shell am kill`.

## Step 6 — A type that needs a Saver (stretch)

Add a third piece of state that isn't a primitive — a small `data class`:

```kotlin
data class Credentials(val email: String, val password: String)

val CredentialsSaver = listSaver<Credentials, String>(
    save = { listOf(it.email, it.password) },
    restore = { Credentials(it[0], it[1]) }
)

// In the owner:
var creds by rememberSaveable(stateSaver = CredentialsSaver) {
    mutableStateOf(Credentials("", ""))
}
```

Rotate and confirm `creds` survives. (Alternatively, make `Credentials` `@Parcelize` and skip the `Saver` entirely.) This is the "what `rememberSaveable` can store, and how to extend it" lesson from lecture 1, §4.

---

## Acceptance criteria

- [ ] `LoginForm` is **stateless**: it takes `value`s and emits `on*Change`/`onSubmit` events; the owner holds the state.
- [ ] A `@Preview` renders `LoginForm` with supplied values (proving reusability).
- [ ] The owner uses `rememberSaveable`; typed text survives a **rotation**.
- [ ] Typed text survives **process death** ("Don't keep activities" on, background + reopen).
- [ ] You confirmed `remember` (not saveable) loses the text on both, and can state the boundary difference in one sentence.
- [ ] (Stretch) A non-primitive state survives via a custom `Saver` (or `@Parcelize`).
- [ ] Build with **0 warnings, 0 errors**.

## What you just proved

You proved lecture 1's two central ideas: **hoisting** makes a composable a pure, testable, reusable function of its inputs (UDF — state down, events up), and the **retention boundary** of the holder decides what survives the lifecycle (`remember` crosses recomposition only; `rememberSaveable` crosses configuration change and process death). The single-word swap and the two tests (rotate, kill) are exactly the senior-review check behind this week's "survives rotation" promise.

---

## Hints (read only if stuck > 10 min)

- **Text still clears after `rememberSaveable`.** Make sure you changed the holder in the *owner* (`LoginScreen`), not inside `LoginForm`. The stateless component has no state to save.
- **`rememberSaveable` won't compile for your data class.** It can only store `Parcelable`/`Serializable`/primitives directly; for a plain `data class` pass `stateSaver = YourSaver` (a `listSaver`/`mapSaver`) or annotate the class `@Parcelize` (needs the `kotlin-parcelize` plugin).
- **Rotation does nothing in the emulator.** Auto-rotate may be off, or the app is locked to portrait. Use `Ctrl+F11` (or the rotate buttons on the emulator side toolbar) and ensure the manifest doesn't pin `screenOrientation`.
- **Process death test "passes" with plain `remember`.** Without "Don't keep activities," backgrounding keeps the process alive and `remember` survives by accident. Turn the developer option ON so the Activity is actually destroyed — that's the real test.
