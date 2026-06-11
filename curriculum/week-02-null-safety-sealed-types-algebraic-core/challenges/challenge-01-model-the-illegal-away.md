# Challenge 1 — Model the illegal away (so the bad state won't compile)

**Time.** 60–120 minutes.
**Deliverable.** The redesigned model, a short report (`MODEL.md`) listing each illegal state the old model allowed and how the new model makes it unrepresentable, and at least two "this no longer compiles" demonstrations (the old buggy call site, commented out with the compiler error it now produces). All committed to your Week 2 repo.

## The premise

Every codebase has a model that started simple and grew dangerous: a `status` string, a pile of nullable fields that are "only set sometimes," ids that are all `Long` and silently interchangeable. It works until someone constructs a state that shouldn't exist — a refunded order with no refund amount, a logged-out session with a username, a charge against the wrong account because two `Long` ids got swapped. The skill this challenge builds is the senior reflex: **find the illegal states a model allows, then redesign so the compiler makes them unrepresentable.** A bug the compiler catches is a bug no user ever sees.

## What to redesign

Start from this model. It compiles, it "works," and it is a minefield. Drop it into `src/main/kotlin/com/crunch/orders/BadModel.kt`.

```kotlin
package com.crunch.orders

// PROBLEM 1: ids are all Long — nothing stops swapping a customerId for a productId.
// PROBLEM 2: `status` is a String — typos compile; the set of values isn't enforced.
// PROBLEM 3: nullable fields are "only valid for some statuses" — illegal combos compile.
data class Order(
    val id: Long,
    val customerId: Long,
    val productId: Long,
    val quantity: Int,            // could be 0 or negative — illegal but constructible
    val status: String,           // "pending" | "paid" | "shipped" | "refunded" — or a typo
    val paymentConfirmation: String?,   // only valid when paid/shipped
    val trackingNumber: String?,         // only valid when shipped
    val refundReason: String?,           // only valid when refunded
)

// This function is a bug farm. Several illegal Orders typecheck fine:
fun illegalStatesThatCompile() {
    // (a) a "pending" order that somehow has a tracking number:
    Order(1, 10, 20, 1, "pending", null, "TRACK-123", null)
    // (b) a "refunded" order with no refund reason but a tracking number:
    Order(2, 10, 20, 1, "refunded", "PAY-1", "TRACK-9", null)
    // (c) a typo in status that no one catches until runtime:
    Order(3, 10, 20, 1, "shippd", null, null, null)
    // (d) a zero-quantity order:
    Order(4, 10, 20, 0, "pending", null, null, null)
    // (e) swapped ids: customerId and productId are both Long, so this compiles
    //     even though the arguments are in the wrong order:
    chargeCustomer(productId = 20L, customerId = 10L)   // oops, swapped — compiles!
}

fun chargeCustomer(customerId: Long, productId: Long) { /* ... */ }
```

Read `illegalStatesThatCompile()` carefully — every line in it is a state that *should not exist* but does, because the types are too loose.

## The work

### Step 1 — Inventory the illegal states

In `MODEL.md`, list every illegal state the model allows (the five in the function, plus any others you spot — e.g. a negative quantity, a "paid" order with no confirmation). For each, write one sentence on why it's illegal.

### Step 2 — Redesign

Rebuild the model in `src/main/kotlin/com/crunch/orders/GoodModel.kt` so the illegal states are unrepresentable. The toolkit (all from this week):

1. **Inline value classes for ids** so they can't be swapped:

   ```kotlin
   @JvmInline value class CustomerId(val raw: Long)
   @JvmInline value class ProductId(val raw: Long)
   @JvmInline value class OrderId(val raw: Long)
   ```

   Now `chargeCustomer(customerId: CustomerId, productId: ProductId)` makes the swapped-argument call a *compile error*.

2. **An inline value class (or a validating factory) for quantity** so a non-positive quantity can't exist:

   ```kotlin
   @JvmInline value class Quantity private constructor(val value: Int) {
       companion object {
           fun of(value: Int): Quantity? = if (value > 0) Quantity(value) else null
       }
   }
   ```

   (A private constructor + a validating factory is "parse, don't validate" — the only way to get a `Quantity` is through `of`, which rejects non-positive values.)

3. **A sealed `OrderStatus` (sum type) where each case carries exactly its valid data**, replacing the status string and the three nullable fields:

   ```kotlin
   sealed interface OrderStatus {
       data object Pending : OrderStatus
       data class Paid(val paymentConfirmation: String) : OrderStatus
       data class Shipped(val paymentConfirmation: String, val trackingNumber: String) : OrderStatus
       data class Refunded(val refundReason: String) : OrderStatus
   }
   ```

   Now: `Pending` carries no payment/tracking/refund fields (can't be a "pending with tracking"); `Shipped` *requires* both a confirmation and a tracking number (can't be a "shipped with no tracking"); `Refunded` *requires* a reason; and the typo `"shippd"` is impossible because there is no string — only the four typed cases.

4. **The redesigned `Order`** composes them:

   ```kotlin
   data class Order(
       val id: OrderId,
       val customerId: CustomerId,
       val productId: ProductId,
       val quantity: Quantity,
       val status: OrderStatus,
   )
   ```

### Step 3 — Prove the illegal states no longer compile

This is the heart of the challenge. Write a `notes/no-longer-compiles.kt` (or commented blocks in `GoodModel.kt`) that *attempts* each old illegal state with the new model and shows it fails. For each, paste the compiler error as a comment. Examples of what you're proving:

- The swapped-id call `chargeCustomer(productId, customerId)` → "type mismatch: required `CustomerId`, found `ProductId`."
- A "pending with tracking number" → impossible to express; `Pending` has no tracking field, so there's nowhere to put one.
- A "shipped with no tracking" → won't compile; `Shipped` *requires* a `trackingNumber` argument.
- The status typo → impossible; you select a typed case, not a string.
- A zero quantity → `Quantity.of(0)` returns `null`, so you must handle the rejection at the boundary; there's no way to construct a `Quantity(0)` directly.

### Step 4 — A consumer that's exhaustive

Write a function that acts on an `Order` by its status — e.g. `fun summary(order: Order): String` — using an **exhaustive `when` over `OrderStatus` with no `else`**. Then note in `MODEL.md` what happens to this function if a fifth status (say `Cancelled`) is added later: it fails to compile until you handle the new case. That's the refactoring-assistant property from lecture 2.

### Step 5 — Tests

Write JUnit 5 tests proving the new model: valid orders construct fine, `Quantity.of` rejects non-positives, the exhaustive `when` consumer handles every case, and (in comments) the illegal states don't compile.

## Acceptance criteria

- [ ] Ids are inline value classes; the swapped-argument call to `chargeCustomer` is a compile error (demonstrated, with the error pasted).
- [ ] Quantity can only be a positive value (validating factory or equivalent); `Quantity.of(0)`/`of(-1)` returns `null`.
- [ ] `OrderStatus` is a sealed sum where each case carries exactly its valid data; the three "only sometimes valid" nullable fields are gone.
- [ ] `MODEL.md` inventories the old illegal states and explains, per state, how the new model makes it unrepresentable.
- [ ] At least **two** "no longer compiles" demonstrations with the compiler error pasted as a comment.
- [ ] An exhaustive `when` consumer over `OrderStatus` with **no `else`**, plus a note on what a fifth case would do to it.
- [ ] JUnit 5 tests; build with **0 warnings, 0 errors**.

## What "great" looks like

A weak submission says "I used sealed classes." A great submission says:

> The old model allowed seven illegal states; the new one makes all seven unrepresentable. The biggest win is `OrderStatus`: the three nullable fields (`paymentConfirmation`, `trackingNumber`, `refundReason`) that were "valid for some statuses only" are gone — `Shipped` now *requires* both a confirmation and a tracking number as non-null constructor args, so a "shipped order with no tracking" can't be constructed, and `Pending` has no tracking field at all, so a "pending with tracking" has nowhere to put the value. The id value classes turned the swapped `chargeCustomer(productId, customerId)` call into a `type mismatch: required CustomerId, found ProductId` error (screenshot attached) — a real bug class (transposed-id charges) eliminated at the type level. `Quantity.of` rejects non-positives at the boundary, so downstream code never sees a zero-quantity order. And the exhaustive `when` in `summary` means that when we add `Cancelled` next quarter, the compiler will list every consumer that needs updating instead of letting cancelled orders silently fall through an `else`.

Specific, defended at the type level, and honest about what each technique bought. That's the senior-engineer answer.

## Where this reappears

This is the exact skill Phase 2's Week 12 leans on, where every screen's `UiState` is a sealed sum (`Loading`/`Success`/`Error`) consumed by an exhaustive `when`, and where process-death survival depends on the state being a clean, serializable algebraic type. The "parse, don't validate" boundary you built with `Quantity.of` is the same pattern as the JSON parser's clean output and Phase 3's network-result modelling. Make illegal states unrepresentable once, here, and it becomes how you think about every model for the rest of the track.
