# Challenge 1 — From Javaism to idiom (with bytecode receipts)

**Time.** 60–120 minutes.
**Deliverable.** The refactored Kotlin file, a short report (`IDIOM.md`) listing each change and the idiom it applies, and at least two `javap`/decompiled-bytecode comparisons proving an idiomatic construct costs nothing (or less) at runtime. All committed to your Week 1 repo.

## The premise

Every team that adopts Kotlin gets a wave of "Java written in Kotlin syntax" — code that compiles and works but reads like it was mechanically translated. Utility classes full of `static`-style methods. `var`s assigned in `if/else` chains. Hand-written `equals`/`hashCode`. Manual loops building mutable lists. Null checks followed by casts. The skill this challenge builds is not "know the idioms" — it's **take real Javaism, refactor it idiomatically, and prove with the bytecode that idiomatic isn't slower.** A refactor you can't defend at the bytecode level is a style opinion; a refactor you can defend is engineering.

## What to refactor

Start from this file. It works — every function returns the right answer. It is also Javaism end to end. Drop it into `src/main/kotlin/com/crunch/lab/Inventory.kt` of your Week 1 Gradle project and confirm it compiles.

```kotlin
package com.crunch.lab

import java.util.regex.Pattern

// (1) A "utility class" of static-style methods — Javaism.
object InventoryUtils {

    // (2) A manual loop building a mutable list — Javaism.
    fun activeSkus(items: List<Item>): List<String> {
        val result = mutableListOf<String>()
        for (item in items) {
            if (item.quantity > 0) {
                result.add(item.sku)
            }
        }
        return result
    }

    // (3) A var assigned in an if/else chain — Javaism.
    fun stockLabel(quantity: Int): String {
        var label: String
        if (quantity <= 0) {
            label = "OUT OF STOCK"
        } else if (quantity < 10) {
            label = "LOW"
        } else if (quantity < 100) {
            label = "OK"
        } else {
            label = "PLENTY"
        }
        return label
    }

    // (4) A null check followed by an explicit cast — Javaism.
    fun describe(value: Any?): String {
        if (value != null) {
            if (value instanceof_String()) {       // see the helper below
                val s = value as String
                return "string of length " + s.length
            }
        }
        return "unknown"
    }

    // (5) String concatenation — Javaism.
    fun receipt(sku: String, qty: Int, unitPrice: Int): String {
        return "Item " + sku + " x" + qty + " = " + (qty * unitPrice) + " cents"
    }

    // (6) A throwaway helper that exists only because (4) avoided `is`.
    private fun Any.instanceof_String(): Boolean = this is String

    // (7) A hand-rolled parse-or-default with try as a statement — Javaism.
    fun parseQuantity(raw: String): Int {
        var q: Int
        try {
            q = Integer.parseInt(raw.trim())
        } catch (e: NumberFormatException) {
            q = 0
        }
        return q
    }
}

// (8) A plain class with hand-written equals/hashCode/toString — Javaism.
class Item {
    val sku: String
    val quantity: Int

    constructor(sku: String, quantity: Int) {
        this.sku = sku
        this.quantity = quantity
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (other !is Item) return false
        return this.sku == other.sku && this.quantity == other.quantity
    }

    override fun hashCode(): Int {
        return 31 * sku.hashCode() + quantity
    }

    override fun toString(): String {
        return "Item(sku=" + sku + ", quantity=" + quantity + ")"
    }
}
```

(The `instanceof_String()` helper is intentionally absurd — it's the kind of thing that appears when someone avoids a language feature they don't trust yet. Part of the challenge is deleting it.)

## The work

### Step 1 — Pin the behaviour with tests first

Before you touch anything, write a small JUnit 5 test that exercises every function with a handful of inputs (including edge cases: empty list, quantity 0, a non-String for `describe`, a non-numeric string for `parseQuantity`). This is your safety net — every refactor must keep these green. *Refactor under test, never blind.*

### Step 2 — Refactor each numbered Javaism to its idiom

Rewrite the file. The target transformations:

1. **Utility object → top-level / extension functions.** `InventoryUtils.activeSkus(items)` becomes either a top-level `fun activeSkus(...)` or, better, an extension `fun List<Item>.activeSkus()`. Delete the `object`.
2. **Manual loop → `filter`/`map` pipeline.** `activeSkus` becomes `items.filter { it.quantity > 0 }.map { it.sku }`.
3. **`var` in if/else → `when` (or `if`) as an expression.** `stockLabel` becomes a single-expression body returning a `when`.
4. **Null check + cast → smart cast.** `describe` becomes a `when (value)` with `is String ->` (the value is smart-cast; no `as`), and the `instanceof_String` helper is deleted.
5. **Concatenation → string template.** `receipt` becomes `"Item $sku x$qty = ${qty * unitPrice} cents"`.
6. **(deleted in step 4.)**
7. **try-statement → try-expression.** `parseQuantity` becomes `raw.trim().toIntOrNull() ?: 0` (even better than try/catch — `toIntOrNull` is the idiomatic "parse or null") *or* a `try { ... } catch { 0 }` expression. Discuss which you chose and why.
8. **Plain class with hand-written members → `data class`.** `Item` becomes `data class Item(val sku: String, val quantity: Int)` and the three overrides vanish.

### Step 3 — Prove it with bytecode (the receipts)

This is what separates this challenge from a style cleanup. Pick **at least two** of your refactors and prove, with bytecode, that the idiom costs nothing or less:

- **`data class Item` vs the hand-written class.** Decompile both (`Tools ▸ Kotlin ▸ Show Kotlin Bytecode ▸ Decompile`, or `javap -c -p`). Show that the generated `equals`/`hashCode`/`toString` are equivalent to (or better than) the ones you hand-wrote and deleted. Note the line count: ~1 line of source generated what was ~20.
- **`describe` smart cast vs the cast version.** Show that the smart-cast `is String` branch generates the same `checkcast` the explicit `as String` did — the cast is still there in bytecode, you just didn't type it and can't get it wrong.
- **`activeSkus` pipeline vs the loop.** Be honest here: the `filter`/`map` pipeline allocates an intermediate list the loop didn't. Show it in the bytecode (you'll see the `filter` and `map` calls). This is the one place idiom has a *cost* — and naming it accurately ("clearer, with one extra allocation; negligible for app-sized lists, and Week 3's sequences fix it if it ever matters") is exactly the senior-engineer honesty the challenge rewards.

### Step 4 — Write `IDIOM.md`

For each numbered change: the Javaism, the idiom you applied, one sentence on why it's better, and — for the two-plus you chose — the bytecode finding. End with a one-paragraph honest summary: which changes were pure wins (clearer *and* same-or-better bytecode) and which traded a tiny runtime cost for clarity.

## Acceptance criteria

- [ ] A JUnit 5 test pins the behaviour of every function; all tests stay green through the refactor.
- [ ] All eight Javaisms are refactored to their idiom; the `InventoryUtils` object and the `instanceof_String` helper are deleted.
- [ ] `Item` is a `data class`; the hand-written `equals`/`hashCode`/`toString` are gone.
- [ ] `IDIOM.md` lists each change with its idiom and rationale.
- [ ] At least **two** bytecode comparisons are included (decompiled or `javap`), with one honest note about where an idiom has a runtime cost (the `filter`/`map` allocation).
- [ ] Build with **0 warnings, 0 errors**.

## What "great" looks like

A weak submission says "I made it more idiomatic." A great submission says:

> The `data class Item` refactor deleted 18 lines of hand-written `equals`/`hashCode`/`toString` and the decompiled bytecode shows the generated `equals` compares `sku` and `quantity` identically to my hand-rolled version — same runtime, 1 line of source instead of 20, and it can't drift out of sync when I add a field. The `describe` smart-cast still emits a `checkcast java/lang/String` exactly like the explicit `as String` did, so the cast cost is unchanged; I just removed the chance of typing the wrong target type. The one trade-off is `activeSkus`: `filter { }.map { }` allocates one intermediate `List` the manual loop avoided — for the inventory sizes here (hundreds of items) it's noise, and if profiling ever flagged it, `asSequence()` (Week 3) makes the pipeline lazy with zero intermediate lists. Eight changes, seven pure wins, one honest trade.

Quantified, defended at the bytecode level, and honest about the one cost. That's the senior-engineer answer.

## Where this reappears

The decompile-and-compare workflow is exactly what Week 3's `inline` lecture leans on (where reading the inlined bytecode is the entire point) and what Phase 3's R8 week builds on. The "idiomatic is the same bytecode" instinct you build here is what lets you write clean Kotlin without ever wondering whether you're paying for the cleanliness.
