# Week 10 — Exercises

Short, focused drills. Each one should take 30–50 minutes. Do them in order; later ones assume earlier ones.

## Index

1. **[Exercise 1 — String routes to typed routes](exercise-01-string-routes-to-typed-routes.md)** — take a small string-route `NavHost` graph and migrate it to Navigation 3: define `@Serializable` route types, render with `NavDisplay` + `entryProvider`, and delete every route string and every `Bundle` argument read. The whole point of the week, in one exercise. (~45 min)
2. **[Exercise 2 — The back stack and the entry provider](exercise-02-back-stack-and-entry-provider.kt)** — drive an app-owned back stack and an `entryProvider` from a Robolectric/Compose test, asserting the rendered screen after each typed navigation and after a back. You prove navigation logic is just state mutation. (~50 min)
3. **[Exercise 3 — Deep link to a typed route](exercise-03-deep-link-to-typed-route.kt)** — write a pure `routeForUri(uri): NavKey?` that maps incoming `Uri`s to typed routes, and unit-test it (including the bad-input cases), then prove a deep link seeds a sensible back stack. (~40 min)

## How to work the exercises

- Read the prompt. Skim, don't memorize.
- **Type the code yourself.** Do not copy-paste. Muscle memory is the entire point of these drills.
- Run it on the **Android emulator** (for the Compose UI test) or on the **JVM via Robolectric** (for the back-stack and deep-link tests). See the output. Read the error if it failed.
- The `.kt` exercises are written to drop into an `androidTest` source set (instrumented) or a Robolectric `test` source set, as each file's header says. Exercise 03's parser test runs on plain JVM with Robolectric for `Uri`.
- If you get stuck for more than 10 minutes, peek at the inline hints at the bottom of each file.
- Every exercise must **build with zero warnings** and pass its stated acceptance criteria. The whole point this week is that the *compiler* catches navigation bugs — so a warning you suppressed is a bug you hid.

There are no solutions checked in. The course is open source — solutions live in forks. After you finish, search GitHub for `c21-week-10` to compare.
