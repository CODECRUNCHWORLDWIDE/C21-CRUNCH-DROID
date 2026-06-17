# Challenge 1 — A type-safe DSL builder (receiver lambdas, `@DslMarker`, reified)

**Time.** 60–120 minutes.
**Deliverable.** A small `kt-html` DSL in your Week 03 repo, a passing test that renders a document, a `javap` excerpt proving the builder lambdas are inlined and a reified call site substituted, and a short `DSL.md` explaining the three mechanisms (receiver function types, `@DslMarker`, reified) in your own words.

## The premise

Every Kotlin builder you admire — `Column { }` in Compose, `dependencies { }` in Gradle, `buildList { }` in the stdlib, `install(ContentNegotiation) { }` in Ktor — is the same three ideas: a **receiver function type** (`Tag.() -> Unit`) so the lambda body can call the builder's members without qualification, a **`@DslMarker`** annotation so you can't accidentally reach an outer builder's methods from an inner block, and frequently an **inline** function so the whole tree-building costs no lambda allocations. This challenge has you build one from scratch so the magic becomes mechanism. A DSL you can *build* is a DSL you can *debug* — and you will debug Compose and Gradle DSLs for a living.

## What to build

A tiny HTML document builder that renders to a string:

```kotlin
val doc = html {
    head { title { +"Crunch Droid" } }
    body {
        h1 { +"Week 03" }
        p {
            +"Generics, inline functions, and "
            b { +"context receivers" }
        }
    }
}
println(doc.render())
```

### Step 1 — The tag model with receiver function types

A `Tag` holds a name, children, and text. The builder methods take **receiver lambdas** so the block runs *with the new child as `this`*:

```kotlin
@DslMarker
annotation class HtmlDsl                       // Step 2 explains why this matters

@HtmlDsl
open class Tag(val name: String) {
    private val children = mutableListOf<Tag>()
    private val text = StringBuilder()

    // A receiver function type: `Tag.() -> Unit`. Calling child("p") { ... } creates a
    // <p>, runs the block WITH that <p> as `this`, and nests it.
    protected fun <T : Tag> child(tag: T, block: T.() -> Unit): T {
        tag.block()              // run the block with `tag` as the receiver
        children.add(tag)
        return tag
    }

    // The unary-plus operator adds literal text — `+"hi"` inside a tag.
    operator fun String.unaryPlus() { text.append(this) }

    fun render(indent: String = ""): String = buildString {
        append("$indent<$name>")
        if (text.isNotEmpty()) append(text)
        if (children.isNotEmpty()) {
            append("\n")
            children.forEach { append(it.render("$indent  ")).append("\n") }
            append(indent)
        }
        append("</$name>")
    }
}

class Html : Tag("html") {
    fun head(block: Head.() -> Unit) = child(Head(), block)
    fun body(block: Body.() -> Unit) = child(Body(), block)
}
class Head : Tag("head") { fun title(block: Title.() -> Unit) = child(Title(), block) }
class Title : Tag("title")
class Body : Tag("body") {
    fun h1(block: H1.() -> Unit) = child(H1(), block)
    fun p(block: P.() -> Unit) = child(P(), block)
}
class H1 : Tag("h1")
class P : Tag("p") { fun b(block: B.() -> Unit) = child(B(), block) }
class B : Tag("b")

// The entry point. `inline` so the top-level lambda allocates nothing.
inline fun html(block: Html.() -> Unit): Html = Html().apply(block)
```

Render it and confirm the nesting is correct.

### Step 2 — Make `@DslMarker` earn its keep

`@DslMarker` (on the `HtmlDsl` annotation, applied to `Tag`) changes scope resolution: inside a nested builder block, the *implicit* receivers of outer blocks are hidden, so you cannot accidentally call an outer tag's method from an inner block. Prove it: **without** `@DslMarker`, this compiles and is a bug —

```kotlin
body {
    p {
        h1 { +"oops" }   // WITHOUT @DslMarker: this resolves to the OUTER body's h1() — wrong!
    }
}
```

Comment out the `@DslMarker` annotation, observe that the erroneous nesting compiles (a `<p>` should not be able to call `body`'s `h1`), then restore `@DslMarker` and confirm the same code is now a **compile error** ("'h1' can't be called in this context by implicit receiver"). Document the before/after in `DSL.md` — this is the single most useful thing `@DslMarker` does, and most people who *use* Compose have never seen *why* it exists.

### Step 3 — Add a reified builder helper

Add a generic, reified convenience that creates a child tag by type and tags it with its class name — so you can see reification inside a DSL:

```kotlin
// reified T lets us read T's simple name at the call site with no Class parameter.
inline fun <reified T : Tag> Tag.typedNote(noinline block: T.() -> Unit): String {
    val typeName = T::class.simpleName ?: "Unknown"   // reified -> concrete at call site
    return "built a $typeName"
    // (block is noinline only to illustrate forwarding; you may also just use it.)
}
```

Call `typedNote<P> { }` somewhere and confirm it returns `"built a P"`. The point is not the feature — it's that `T::class.simpleName` resolves to the concrete type *because the call site knows it*, exactly as in exercise 2.

### Step 4 — Disassemble and prove the inlining

Compile, then run `javap` on the call-site class (the one containing your `html { ... }` usage and the `typedNote<P>` call):

```
javap -c -p -classpath build/classes/kotlin/main com.crunch.kthtml.MainKt
```

Find and copy into `DSL.md`:

1. The `html { }` call site — because `html` is `inline`, there is **no `new Function0`** allocation for the top-level lambda; the `Html().apply(block)` is spliced in. Contrast with a non-inline builder if you want (temporarily remove `inline` and re-disassemble to see the `Function0` appear).
2. The `typedNote<P>` call site — find the literal `P` class reference (`getSimpleName`/`P.class`) baked in, proving the reified substitution.

### Step 5 — Test it

```kotlin
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HtmlDslTest {
    @Test fun `renders nested structure`() {
        val out = html { body { p { +"hi"; b { +"!" } } } }.render()
        assertTrue("<html>" in out && "<body>" in out && "<p>" in out && "<b>" in out)
        assertTrue("hi" in out && "!" in out)
    }
    @Test fun `reified helper reads the concrete type`() {
        val note = P().typedNote<B> { }
        assertEquals("built a B", note)
    }
}
```

## Acceptance criteria

- [ ] `html { ... }` builds and renders a correctly nested document; the test passes.
- [ ] With `@DslMarker` removed, the wrong nesting (`h1` inside `p`) compiles; with it restored, the same code is a compile error. Documented in `DSL.md`.
- [ ] A reified `typedNote<T>` reads `T::class.simpleName` and returns the concrete type name.
- [ ] `DSL.md` contains a `javap` excerpt showing the `html { }` call site has **no** lambda allocation (inlined) and the `typedNote<P>` site has the concrete class baked in.
- [ ] `DSL.md` explains, in 3–5 sentences in your own words, the three mechanisms: receiver function types (`Tag.() -> Unit` lets the block call members unqualified), `@DslMarker` (hides outer implicit receivers to prevent cross-scope leaks), and reified (concrete type at the call site).
- [ ] Build with **0 warnings**.

## What "great" looks like

A weak submission says "I made a builder DSL." A great submission says:

> The `html { }` builder uses a receiver function type `Html.() -> Unit`, so the block body calls `body`/`head` without qualification — `this` is the `Html`. Because `html` is `inline`, `javap` shows the top-level lambda is spliced in with no `Function0` allocation; removing `inline` made a `new Function0` appear at the call site. `@DslMarker` on the `Tag` supertype hid the outer `Body`'s implicit receiver inside a nested `P` block, so calling `body`'s `h1()` from inside a `<p>` went from a silent bug to a compile error — exactly the protection Compose's `@LayoutScopeMarker` and Gradle's DSL markers give you. The reified `typedNote<B>` read `B::class.simpleName` with the concrete type substituted at the call site, visible in the bytecode as a literal class reference.

Mechanism-level, with the bytecode as evidence. That's the senior answer.

## Where this reappears

This is *the* foundational pattern for the rest of the track. Compose's entire UI is receiver-lambda builders with scope markers (`Column { }`, `Row { }`, `Modifier` chains) — Week 07 onward. Gradle Kotlin DSL (`android { }`, `dependencies { }`) is the same shape — Week 06. Ktor, Hilt test builders, and Navigation 3's graph DSL all reuse it. Build it once here by hand and you will never again treat a Kotlin builder as magic — you will see the receiver function type, the `@DslMarker`, and the `inline` underneath.
