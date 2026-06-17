# Week 11 — Quiz

Thirteen questions. Take it with your lecture notes closed. Aim for 11/13 before moving to Week 12. Answer key with explanations at the bottom — don't peek.

---

**Q1.** In Material 3, what is a color *role*?

- A) A specific hex value reused across the app.
- B) A semantic name (e.g. `primary`, `onSurface`) that the `ColorScheme` resolves to an actual color for the current configuration.
- C) A user permission for theming.
- D) A Compose `CompositionLocal` you must provide manually on every screen.

---

**Q2.** Why is `Color.White` text on a `Color(0xFF2196F3)` button a problem?

- A) It's never a problem; white on blue always works.
- B) Hardcoded colors look right in one configuration and wrong in others (dark mode, dynamic color), and the contrast isn't guaranteed across them.
- C) `Color.White` isn't a valid Compose color.
- D) Buttons can't take a text color.

---

**Q3.** What does the `on*` role (e.g. `onPrimary`) guarantee?

- A) Nothing; it's just a naming convention.
- B) It's the same color as its base role.
- C) It's guaranteed to contrast with its base role — content drawn on `primary` should use `onPrimary`.
- D) It's only used in dark mode.

---

**Q4.** How does a deeply nested `Text` read `onSurface` without anyone passing it down?

- A) It's a global variable.
- B) `MaterialTheme.colorScheme` is a `CompositionLocal` provided by `MaterialTheme` high in the tree and read anywhere below.
- C) Each composable re-declares the color.
- D) Compose injects it via reflection.

---

**Q5.** What is the relationship between a *seed* color, a *tonal palette*, and a *role*?

- A) They're three names for the same thing.
- B) A seed is a source color; a tonal palette is that hue across all tones (0–100); a role is a specific tone assigned a job. Light and dark assign different tones to the same roles.
- C) A role generates a seed, which generates a palette.
- D) Roles are random; palettes are fixed.

---

**Q6.** When is dynamic color available?

- A) On every Android version.
- B) On Android 12 (API 31) and up — `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`; below that you need a fallback.
- C) Only on Pixel devices.
- D) Only in dark mode.

---

**Q7.** Where does the wallpaper color extraction for dynamic color happen?

- A) In your app — you sample the wallpaper bitmap yourself.
- B) In the system; `dynamicLightColorScheme(context)` reads palettes the OS already extracted. Your app asks for the result, it doesn't sample the wallpaper.
- C) On Google's servers.
- D) At build time.

---

**Q8.** A user on an Android 14 phone wants your brand colors, not their wallpaper's. How?

- A) Impossible; dynamic color is forced on Android 12+.
- B) Expose a `dynamicColor: Boolean` toggle; when off, the selection logic returns your hand-tuned fallback even on Android 12+.
- C) Tell them to change their wallpaper.
- D) Ship two separate apps.

---

**Q9.** After calling `enableEdgeToEdge()`, your top app bar's title overlaps the status-bar clock. Why, and what's the fix?

- A) A bug in `enableEdgeToEdge`; file a report.
- B) Content now draws behind the transparent system bars; pad for the status-bar inset (put the app bar in `Scaffold`'s `topBar`, which insets it, or apply `WindowInsets.statusBars`).
- C) Use a smaller font.
- D) Disable edge-to-edge.

---

**Q10.** For a `LazyColumn` under edge-to-edge, why use `contentPadding` instead of `Modifier.padding`?

- A) No difference.
- B) `contentPadding` pads the items so they clear the bars while the list background extends edge-to-edge behind them; `Modifier.padding` clips the whole list, leaving a dead band behind the bar.
- C) `Modifier.padding` doesn't compile on a `LazyColumn`.
- D) `contentPadding` is faster.

---

**Q11.** A bottom-anchored text field is covered by the keyboard. The fix is:

- A) Move the field to the top.
- B) Apply `imePadding()` (or `WindowInsets.ime`) so the field rises above the keyboard as it animates in.
- C) Disable the keyboard.
- D) Use a smaller keyboard.

---

**Q12.** Why is dark theme not just "the light theme with colors inverted"?

- A) It is exactly that.
- B) Dark theme re-picks tones (e.g. `primary` at tone-80 vs. tone-40) and uses elevation-tinted surfaces, so it's a peer configuration with its own role values, not an inversion.
- C) Inversion is faster.
- D) Dark theme uses a different `MaterialTheme`.

---

**Q13.** Your dark theme's secondary text measures 3.8:1 contrast against its surface. What's the standard and the fix?

- A) 3.8:1 is fine for body text.
- B) WCAG AA wants 4.5:1 for body text; fix it by moving a tonal value (lighten `onSurfaceVariant`) until it clears 4.5:1 — not by hardcoding a color on the component.
- C) Lower the threshold to 3:1.
- D) Remove the text.

---

## Answer key

**Q1 — B.** A role is a semantic name the `ColorScheme` resolves per configuration — `primary` is the right color in light, dark, dynamic, and fallback, all from one role. Not a fixed hex (A). (Lecture 1, §2.)

**Q2 — B.** Hardcoded colors look right in the one configuration you tuned and wrong in the others; the role model resolves the right color per configuration and the `on*` partner guarantees contrast. (Lecture 1, §1.)

**Q3 — C.** Every base role has an `on*` partner guaranteed to contrast with it. Use `onPrimary` for content on `primary`, `onSurface` on `surface` — that's most of accessible color for free. (Lecture 1, §2.)

**Q4 — B.** `MaterialTheme.colorScheme` is read from a `CompositionLocal` provided by `MaterialTheme` up the tree; any descendant reads it without it being passed through parameters. (Lecture 1, §3.)

**Q5 — B.** Seed → tonal palette (one hue, all tones) → role (a tone assigned a job). Light and dark differ because the *same roles* read *different tones* to stay legible on their surface. (Lecture 1, §4.)

**Q6 — B.** Dynamic color is Android 12 (API 31, "S") and up. The `>= S` gate routes older devices to the fallback. (Lecture 1, §5; exercise 02.)

**Q7 — B.** The system extracts the palette from the wallpaper; `dynamicLightColorScheme(context)` reads that result. You don't sample the wallpaper yourself (that's the wrong approach and would need a permission). (Lecture 1, §5.)

**Q8 — B.** A `dynamicColor: Boolean` toggle; off → the selection logic returns your fallback even on 12+. Dynamic color is a default, not a mandate. (Lecture 1, §5; exercise 02.)

**Q9 — B.** Edge-to-edge means content draws behind the now-transparent bars, so it overlaps them until you pad for the inset — `Scaffold`'s `topBar` insets the app bar, or apply `WindowInsets.statusBars`. (Lecture 2, §1–2.)

**Q10 — B.** `contentPadding` pads the items while letting the list background extend edge-to-edge behind the bars (the intended look); `Modifier.padding` clips the list into the safe area, leaving a dead band. (Lecture 2, §2; exercise 03.)

**Q11 — B.** The keyboard is a window inset; `imePadding()` lifts the field above it as it animates in. (Lecture 2, §3.)

**Q12 — B.** Dark theme re-picks tones and uses elevation-tinted surfaces; it's a peer configuration with its own values, not an inversion — which is why hardcoded dark colors and pure-black backgrounds break it. (Lecture 2, §4.)

**Q13 — B.** WCAG AA wants 4.5:1 for body text; fix by moving a tone (lighten `onSurfaceVariant`) until it clears the bar — at the `ColorScheme` layer, so every component reading that role benefits, not by patching one component. (Lecture 2, §5; challenge.)

---

*Score 11+? On to Week 12. Below 9? Re-read both lecture notes and re-run exercises 1 and 3 — roles-not-literals and edge-to-edge insets are the two ideas this week is graded on.*
