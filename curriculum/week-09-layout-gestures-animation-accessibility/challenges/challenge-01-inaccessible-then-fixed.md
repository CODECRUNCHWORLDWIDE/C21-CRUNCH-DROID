# Challenge 1 — Make an inaccessible component accessible (and prove it)

**Time.** 60–120 minutes.
**Deliverable.** A short report (`A11Y.md`) with a before/after TalkBack transcript, a WCAG contrast table, a touch-target note, and the refactored code, committed to your Week 09 repo.

## The premise

Every Android engineer has, at least once, shipped a gorgeous custom component that a screen-reader user literally cannot operate — a swipe-to-delete with no action, an icon button with no label, "elegant" gray text no one with low vision can read. It demos beautifully and excludes real people. The skill this challenge builds is not "know accessibility matters" — it's **audit a real component with TalkBack actually on, find the exact ways it fails, fix each, and prove the fix with a transcript and a contrast ratio.** A claim of accessibility you can't demonstrate is a guess.

You'll build a deliberately-inaccessible swipe-to-delete list item, audit it three ways, then fix it and prove it.

## What to build

### Step 1 — The inaccessible version

A list-item card you swipe left to delete, with an icon-only "favorite" button, in low-contrast colors:

```kotlin
@Composable
fun ListItemBad(item: Item, onDelete: () -> Unit, onFavorite: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    // swipe far enough -> onDelete()  (gesture-only, no a11y action)
                }
            }
    ) {
        Text(
            text = item.title,
            color = Color(0xFFBBBBBB),                 // FAIL: light gray on white, ~1.9:1
            modifier = Modifier.weight(1f).padding(8.dp)
        )
        // FAIL: icon button with no contentDescription, and likely < 48dp target
        Icon(
            imageVector = Icons.Default.FavoriteBorder,
            contentDescription = null,                  // invisible to TalkBack
            modifier = Modifier.size(24.dp).clickable { onFavorite() }   // 24dp target, too small
        )
    }
}
```

### Step 2 — Audit it three ways (the "before" evidence)

**Audit 1 — TalkBack.** Turn on TalkBack (Settings ▸ Accessibility ▸ TalkBack). Navigate to the item and write down exactly what it announces. You'll find: the title is read (Text gets a label for free), but the **favorite icon is silent** (no `contentDescription`), and there is **no way to delete** — the swipe is intercepted by TalkBack, and there's no action. Transcribe what TalkBack says (and doesn't).

**Audit 2 — contrast.** Compute the contrast ratio of `#BBBBBB` on white (use exercise 3's `contrastRatio`, or WebAIM). It's ~1.9:1 — far below the 4.5:1 AA bar. Record it.

**Audit 3 — touch target.** The favorite icon's tap target is 24dp, below the 48dp minimum. Note it (the Accessibility Scanner flags this automatically if you run it).

### Step 3 — Fix every failure (the accessible version)

```kotlin
@Composable
fun ListItem(item: Item, onDelete: () -> Unit, onFavorite: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> change.consume() /* swipe-delete */ }
            }
            // FIX: expose delete as a custom action; merge into one stop.
            .semantics(mergeDescendants = true) {
                customActions = listOf(
                    CustomAccessibilityAction("Delete") { onDelete(); true }
                )
            }
    ) {
        Text(
            text = item.title,
            color = Color(0xFF1A1A1A),                  // FIX: near-black on white, ~17:1, passes AA
            modifier = Modifier.weight(1f).padding(8.dp)
        )
        IconButton(                                      // FIX: IconButton is 48dp by default
            onClick = onFavorite,
            modifier = Modifier.semantics {
                stateDescription = if (item.isFavorite) "Favorited" else "Not favorited"
            }
        ) {
            Icon(
                imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (item.isFavorite) "Remove from favorites" else "Add to favorites"  // FIX: labeled
            )
        }
    }
}
```

Fixes applied: a **custom "Delete" action** so the swipe-only delete is operable; **`contentDescription`** on the favorite icon; **`stateDescription`** so TalkBack announces the favorite state; **`IconButton`** (48dp target by default) instead of a bare 24dp clickable icon; **near-black text** that passes AA; and **`mergeDescendants`** so the row is one TalkBack stop.

### Step 4 — Re-audit and prove (the "after" evidence)

- **TalkBack:** navigate the fixed item. It now announces "Title, Add to favorites button, Not favorited" as one stop, and the actions menu offers "Delete." Transcribe it.
- **Contrast:** `#1A1A1A` on white is ~17:1 — passes AA with room to spare. Record it next to the before value.
- **Touch target:** the `IconButton` is 48dp. Note the fix.

### Step 5 (optional, stretch) — font scale and `liveRegion`

Set the emulator font scale to 200% (Settings ▸ Display ▸ Font size) and confirm the item's text scales and the layout doesn't clip (because you used `sp`, not `dp`, for text). Add a `liveRegion` announcement when an item is deleted ("Item deleted") so TalkBack confirms the action without the user navigating. Document both.

## Acceptance criteria

- [ ] The bad version reproduces all three failures (silent icon, no delete action, sub-AA contrast, sub-48dp target).
- [ ] The fixed version: custom "Delete" action, labeled favorite icon, `stateDescription`, 48dp target, AA-passing text, merged into one stop.
- [ ] With TalkBack ON, you can favorite *and* delete the item using only the screen reader (no sighted-only path).
- [ ] `A11Y.md` records: before/after TalkBack transcripts, a contrast table (before ~1.9:1 fail / after ~17:1 pass, with the exact ratios), and the touch-target note.
- [ ] A 3–5 sentence explanation, in your own words, of why each fix matters (which user it includes).
- [ ] Build with **0 warnings**.

## What "great" looks like

A weak submission says "I added contentDescription." A great submission says:

> The original `ListItemBad` was unusable for a TalkBack user in two ways: the favorite icon announced nothing (no `contentDescription`), and there was no way to delete an item at all — TalkBack intercepts the horizontal swipe for its own navigation, and delete was swipe-only. The title text was `#BBBBBB` on white, a 1.9:1 contrast ratio against the 4.5:1 AA requirement — illegible for low-vision users and in sunlight. The favorite tap target was 24dp, half the 48dp minimum. After the fix, the row is one merged TalkBack stop announcing "Read the docs, Add to favorites button, Not favorited," the actions menu offers "Delete," the text is `#1A1A1A` at 17.4:1 (well past AA), and the favorite is a 48dp `IconButton`. With TalkBack on I favorited and deleted items using only the screen reader. At 200% font scale the layout held because the text is sized in `sp`.

Specific, measured, and centered on the actual humans each fix includes. That's the senior-engineer answer.

## Where this reappears

The "turn on TalkBack and actually use it, compute the real contrast ratio, check the touch target" discipline is exactly what Phase III's testing week automates (Espresso accessibility checks, the Accessibility Test Framework in CI) and what Week 11's Material 3 mostly does *for* you — which you'll only appreciate because you did it by hand here. Accessibility-by-default is one of the clearest senior signals a reviewer or interviewer looks for, because it's the habit juniors skip and seniors never do.
