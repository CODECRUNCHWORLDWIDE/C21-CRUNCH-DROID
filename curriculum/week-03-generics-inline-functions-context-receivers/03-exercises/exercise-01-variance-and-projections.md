# Exercise 1 — Variance and projections

**Goal.** Take a generic container and a handful of functions with the variance annotations *removed*, add the right `in`/`out` (and one use-site projection) so a fixed list of assignments type-checks, then explain each annotation out loud. If you can say *why* `out` and not `in` for each one, you understand variance — and that fluency is the deliverable, not the compiling code.

**Estimated time.** 40 minutes.

**Prerequisites.** JDK 17+, Kotlin 2.0+, any JVM project (a Gradle `:app` JVM module or even the Kotlin Playground). No Android. Put the code in a single `.kt` file with a `main()`.

---

## Step 1 — The type hierarchy

Start with a small hierarchy so subtyping is unambiguous:

```kotlin
open class Animal(val name: String)
class Cat(name: String) : Animal(name)
class Dog(name: String) : Animal(name)
```

`Cat` and `Dog` are both subtypes of `Animal`. Every variance question below is "does the container follow that subtyping, reverse it, or refuse it?"

## Step 2 — The container, with variance removed

Here are three interfaces. The variance annotations have been stripped; each is currently **invariant**. Your job is to add `out` or `in` to each type parameter so the assignments in Step 3 compile — and *only* those that should.

```kotlin
// A) Produces values only. Should a Shelter<Cat> be usable as a Shelter<Animal>?
interface Shelter<T> {              // <-- add variance
    fun adopt(): T                  // T is produced (returned)
}

// B) Consumes values only. Should a Vet<Animal> be usable as a Vet<Cat>?
interface Vet<T> {                  // <-- add variance
    fun treat(patient: T)           // T is consumed (parameter)
}

// C) Both produces AND consumes. What variance can it have?
interface Kennel<T> {               // <-- think hard about this one
    fun checkIn(animal: T)          // consumes
    fun checkOut(): T               // produces
}
```

## Step 3 — The assignments that must compile

Add variance to A, B, C above so that **exactly** these assignments type-check. Some are meant to compile; one block is meant to *stay* a compile error — your annotations must not accidentally make it compile.

```kotlin
fun main() {
    // --- Shelter: should be COVARIANT (out) ---
    val catShelter: Shelter<Cat> = object : Shelter<Cat> { override fun adopt() = Cat("Mimi") }
    val animalShelter: Shelter<Animal> = catShelter      // must compile: a cat shelter is an animal shelter
    val a: Animal = animalShelter.adopt()

    // --- Vet: should be CONTRAVARIANT (in) ---
    val animalVet: Vet<Animal> = object : Vet<Animal> { override fun treat(patient: Animal) {} }
    val catVet: Vet<Cat> = animalVet                     // must compile: a vet for any animal can treat a cat
    catVet.treat(Cat("Mimi"))

    // --- Kennel: should be INVARIANT — neither of these may compile ---
    val catKennel: Kennel<Cat> = makeCatKennel()
    // val animalKennel: Kennel<Animal> = catKennel      // MUST STAY an error (you could check in a Dog)
    // val k2: Kennel<Cat> = makeAnimalKennel()          // MUST STAY an error (checkOut would lie)

    // --- Use-site projection: copy from a read-only view ---
    val cats: Array<Cat> = arrayOf(Cat("a"), Cat("b"))
    val animals: Array<Animal> = Array(2) { Animal("x") }
    copyInto(cats, animals)                              // must compile via the projection in Step 4
    println(animals.joinToString { it.name })
}

fun makeCatKennel(): Kennel<Cat> = object : Kennel<Cat> {
    private var held: Cat? = null
    override fun checkIn(animal: Cat) { held = animal }
    override fun checkOut(): Cat = held ?: Cat("default")
}
fun makeAnimalKennel(): Kennel<Animal> = object : Kennel<Animal> {
    private var held: Animal? = null
    override fun checkIn(animal: Animal) { held = animal }
    override fun checkOut(): Animal = held ?: Animal("default")
}
```

## Step 4 — One use-site projection

`Array<T>` is invariant (it has both a getter and a setter). Write `copyInto` so it reads from `source` and writes into `dest`, using a **use-site `out` projection** so an `Array<Cat>` is accepted as the source:

```kotlin
fun copyInto(source: Array<out Animal>, dest: Array<Animal>) {   // `out Animal` = read-only view
    for (i in source.indices) {
        if (i < dest.size) dest[i] = source[i]
        // source[i] = Animal("x")   // <-- uncomment to SEE the projection forbid writing
    }
}
```

Then, in a comment, answer: *why does `source[i] = Animal("x")` fail to compile inside `copyInto`?*

## Step 5 — Say it out loud

For each of A, B, C, write a one-sentence justification **in your own words** as a comment. The template (fill in):

- `Shelter` is `out T` because it only ____ `T`, so a `Shelter<Cat>` can stand in for a `Shelter<Animal>` since every cat you ____ is an animal.
- `Vet` is `in T` because it only ____ `T`, so a `Vet<Animal>` can stand in for a `Vet<Cat>` since a vet that treats any animal can treat a ____.
- `Kennel` is invariant because it both ____ and ____ `T`; making it covariant would let you `checkIn` a `Dog` to a `Kennel<Cat>`, and making it contravariant would make `checkOut` return the wrong type.

---

## Acceptance criteria

- [ ] `Shelter` is `out T`, `Vet` is `in T`, `Kennel` is invariant (no annotation).
- [ ] All the "must compile" assignments compile; the two commented Kennel assignments stay errors when uncommented.
- [ ] `copyInto` uses `Array<out Animal>` and accepts `Array<Cat>`; the in-body write is correctly rejected.
- [ ] All three out-loud justifications are written in your own words.
- [ ] Build with **0 warnings, 0 errors**.

## What you just proved

You proved lecture 1's variance rule by making it load-bearing: `out` for producers (covariant), `in` for consumers (contravariant), invariant for both. And you used a use-site projection to get covariance from an invariant `Array` at exactly one call site. The "say it out loud" step is the real exam — variance you can *paste* is worthless in an interview; variance you can *narrate* is the senior signal.

---

## Hints (read only if stuck > 10 min)

- **`Shelter` won't accept `out` — "type parameter T is declared as 'out' but occurs in 'in' position."** Then `adopt()` isn't the only use of `T` — check you didn't leave a parameter of type `T` somewhere. `out T` is allowed only if `T` appears solely in return positions.
- **The Kennel assignments compile when they shouldn't.** You accidentally added variance to `Kennel`. A type that both produces and consumes `T` *must* be invariant — leave it bare.
- **`copyInto` rejects `Array<Cat>` as source.** You wrote `Array<Animal>` instead of `Array<out Animal>`. The `out` projection is what lets the invariant array be passed covariantly here.
- **Why the in-body write fails:** inside `copyInto`, `source` is projected to `Array<out Animal>`, a read-only view — the setter is removed from the projected type, because writing an `Animal` into what might be an `Array<Cat>` is exactly the unsoundness `out` forbids.
