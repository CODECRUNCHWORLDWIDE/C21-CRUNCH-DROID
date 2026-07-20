# Mini-Project — A typed JSON parser with a sealed `JsonNode` tree

This week you build a **typed JSON parser**: a small recursive-descent parser that consumes a JSON dialect and returns a sealed `JsonNode` tree, with **no external libraries**. Parsing is the canonical place algebraic data types shine — the result is a sum type (a JSON value is *one of* six shapes), errors are modelled as a typed sealed `ParseResult` rather than thrown, and consuming the tree is an exhaustive `when` the compiler enforces. By the end you will have built a real ADT, a hand-rolled lexer and parser, and a clean boundary between "untrusted text" and "well-typed tree" — the "parse, don't validate" principle made concrete.

This is the same lesson the Swift track learns building a sealed parse tree, adapted to Kotlin's `sealed interface` and exhaustive `when`. It is deliberately not an Android project — it runs on the plain JVM exactly like `kt-stat`, so the focus stays on the modelling.

---

## What you're building

A library (and a tiny CLI) that turns a JSON string into a `JsonNode`:

```kotlin
val result = parseJson("""{"name": "Ada", "active": true, "scores": [90, 85, null]}""")
when (result) {
    is ParseResult.Ok  -> println(render(result.value))   // pretty-print the tree
    is ParseResult.Err -> println("parse error: ${result.error}")
}
```

You support the JSON essentials: objects, arrays, strings (with escapes), numbers (int and decimal), `true`/`false`/`null`, and nested combinations. You return a **typed error** on malformed input, never an exception that escapes the parser. The `JsonNode` tree that comes *out* is clean, well-typed Kotlin with no nulls-where-they-shouldn't-be and no stringly-typed surprises.

---

## The algebraic model (the heart of the project)

Two sealed hierarchies carry the whole design. Put them in `src/main/kotlin/com/crunch/json/Model.kt`:

```kotlin
package com.crunch.json

/** A parsed JSON value: a sum type of exactly six shapes. */
sealed interface JsonNode {
    data class JsonObject(val entries: Map<String, JsonNode>) : JsonNode
    data class JsonArray(val elements: List<JsonNode>) : JsonNode
    data class JsonString(val value: String) : JsonNode
    data class JsonNumber(val value: Double) : JsonNode
    data class JsonBool(val value: Boolean) : JsonNode
    data object JsonNull : JsonNode
}

/** The outcome of a parse: the tree, or a typed error. (out T: a ParseResult<X>
 *  is usable where a ParseResult of a supertype is expected — variance, Week 3.) */
sealed interface ParseResult<out T> {
    data class Ok<T>(val value: T) : ParseResult<T>
    data class Err(val error: ParseError) : ParseResult<Nothing>
}

/** Every way a parse can fail — enumerated, so callers handle each exhaustively. */
sealed interface ParseError {
    data class UnexpectedChar(val char: Char, val position: Int) : ParseError
    data class UnexpectedToken(val description: String, val position: Int) : ParseError
    data object UnexpectedEnd : ParseError
    data class InvalidNumber(val text: String, val position: Int) : ParseError
    data class InvalidEscape(val sequence: String, val position: Int) : ParseError
}
```

Decisions you must be able to defend in review:

- **Why a `sealed interface JsonNode` and not, say, a class with a `type` enum and nullable fields?** Because a JSON value *is exactly one* of six shapes, and a sealed sum makes the illegal "object that's also a number" unrepresentable. An exhaustive `when` over it can't forget a case. This is the Week 2 thesis: model the alternatives as a sum.
- **Why `data object JsonNull` and not `null`?** Because `JsonNull` (a JSON null) is a *distinct, meaningful value in the tree* — "the key exists and its value is JSON null" is different from "the key is absent." Using Kotlin's `null` would conflate them. A `data object` case is the right model.
- **Why a typed `ParseError` sum instead of throwing?** So callers handle failures with an exhaustive `when` — every error mode is enumerated and the compiler forces you to handle it. (This is the "typed sealed result" from lecture 2, §6, chosen over `Result<T>`/exceptions precisely because the failures are knowable and worth distinguishing.)

---

## Milestone 1 — The lexer (tokenizer) (≈ 2 h)

A parser is easier to write in two passes: a **lexer** that turns the character stream into a flat list of tokens, then a **parser** that turns tokens into the tree. Model the tokens as — what else — a sealed type. In `src/main/kotlin/com/crunch/json/Lexer.kt`:

```kotlin
package com.crunch.json

sealed interface Token {
    val position: Int
    data class BeginObject(override val position: Int) : Token   // {
    data class EndObject(override val position: Int) : Token     // }
    data class BeginArray(override val position: Int) : Token    // [
    data class EndArray(override val position: Int) : Token      // ]
    data class Colon(override val position: Int) : Token         // :
    data class Comma(override val position: Int) : Token         // ,
    data class StringLit(val value: String, override val position: Int) : Token
    data class NumberLit(val value: Double, override val position: Int) : Token
    data class BoolLit(val value: Boolean, override val position: Int) : Token
    data class NullLit(override val position: Int) : Token
}
```

Write `fun tokenize(input: String): ParseResult<List<Token>>` that walks the input character by character:

- Skip whitespace (` `, `\t`, `\n`, `\r`).
- `{` `}` `[` `]` `:` `,` → the structural tokens.
- A `"` begins a string literal — consume until the closing `"`, handling escapes (`\"`, `\\`, `\/`, `\n`, `\t`, `\r`, `\b`, `\f`, and `\uXXXX`). An unterminated string or a bad escape returns a typed `ParseError`.
- A digit or `-` begins a number — consume the numeric run (integer part, optional fraction, optional exponent) and parse it with `toDoubleOrNull()`; a malformed number returns `InvalidNumber`.
- `t`/`f`/`n` begin `true`/`false`/`null` — match the keyword exactly or return `UnexpectedToken`.
- Any other character returns `UnexpectedChar`.

Test the lexer thoroughly *before* writing the parser: every token type, escapes, negative and decimal numbers, the error cases. A solid lexer makes the parser easy; a shaky one makes it miserable.

## Milestone 2 — The recursive-descent parser (≈ 3 h)

Now turn the token list into a `JsonNode` tree. Recursive descent means: one function per grammar rule, calling each other to match the nested structure. In `src/main/kotlin/com/crunch/json/Parser.kt`, a small cursor-based parser:

```kotlin
package com.crunch.json

private class Cursor(val tokens: List<Token>) {
    var index = 0
    fun peek(): Token? = tokens.getOrNull(index)
    fun next(): Token? = tokens.getOrNull(index)?.also { index++ }
}

/** Public entry point: text -> tree, or a typed error. */
fun parseJson(input: String): ParseResult<JsonNode> {
    val tokens = when (val lexed = tokenize(input)) {
        is ParseResult.Ok  -> lexed.value
        is ParseResult.Err -> return lexed     // propagate the lexer error
    }
    val cursor = Cursor(tokens)
    val node = when (val parsed = parseValue(cursor)) {
        is ParseResult.Ok  -> parsed.value
        is ParseResult.Err -> return parsed
    }
    // After the top value, there must be no leftover tokens.
    val leftover = cursor.peek()
    return if (leftover == null) ParseResult.Ok(node)
    else ParseResult.Err(ParseError.UnexpectedToken("trailing tokens", leftover.position))
}
```

Then `parseValue`, which dispatches on the next token with an exhaustive `when`, and the recursive `parseObject` / `parseArray`:

```kotlin
private fun parseValue(cursor: Cursor): ParseResult<JsonNode> {
    val token = cursor.peek() ?: return ParseResult.Err(ParseError.UnexpectedEnd)
    return when (token) {
        is Token.BeginObject -> parseObject(cursor)
        is Token.BeginArray  -> parseArray(cursor)
        is Token.StringLit   -> { cursor.next(); ParseResult.Ok(JsonNode.JsonString(token.value)) }
        is Token.NumberLit   -> { cursor.next(); ParseResult.Ok(JsonNode.JsonNumber(token.value)) }
        is Token.BoolLit     -> { cursor.next(); ParseResult.Ok(JsonNode.JsonBool(token.value)) }
        is Token.NullLit     -> { cursor.next(); ParseResult.Ok(JsonNode.JsonNull) }
        else -> ParseResult.Err(ParseError.UnexpectedToken("expected a value", token.position))
    }
}
```

`parseObject` consumes `{`, then zero-or-more `"key": value` pairs separated by `,`, then `}`, building a `Map<String, JsonNode>`. `parseArray` consumes `[`, then zero-or-more values separated by `,`, then `]`, building a `List<JsonNode>`. Both recurse into `parseValue` for the nested values — that recursion is what makes a parser handle arbitrary nesting.

Decisions to defend:

- **Why thread `ParseResult` through every function instead of throwing?** So errors are values the type system tracks, not control flow that can escape. Every parse function returns `ParseResult<...>`, and the `when (val r = ...) { Ok -> ...; Err -> return r }` pattern propagates failures cleanly. (If you find this verbose, you're feeling exactly why a `bind`/`flatMap` combinator is useful — and Week 3's inline functions are how you'd build one ergonomically. For now, the explicit propagation is good practice.)
- **Why check for trailing tokens?** Because `{"a":1}garbage` should be an *error*, not a silently-accepted object. A correct parser consumes the *whole* input or reports why it couldn't.

## Milestone 3 — The exhaustive consumer (≈ 1 h)

Prove the tree is well-modelled by writing consumers that `when` over `JsonNode` exhaustively, with no `else`. In `src/main/kotlin/com/crunch/json/Render.kt`:

```kotlin
package com.crunch.json

/** Re-serialize a JsonNode back to a compact JSON string. Exhaustive when, no else. */
fun render(node: JsonNode): String = when (node) {
    is JsonNode.JsonObject -> node.entries.entries
        .joinToString(",", "{", "}") { (k, v) -> "${quote(k)}:${render(v)}" }
    is JsonNode.JsonArray  -> node.elements.joinToString(",", "[", "]") { render(it) }
    is JsonNode.JsonString -> quote(node.value)
    is JsonNode.JsonNumber -> formatNumber(node.value)
    is JsonNode.JsonBool   -> node.value.toString()
    JsonNode.JsonNull      -> "null"
}

private fun quote(s: String): String = "\"" + s
    .replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r") + "\""

private fun formatNumber(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
```

Write a second consumer to flex the model — e.g. `fun countNodes(node: JsonNode): Int` (total nodes in the tree) or `fun depth(node: JsonNode): Int` (max nesting depth), each an exhaustive recursive `when`. Then do the experiment from exercise 2: temporarily add a seventh case to `JsonNode` and watch *every* consumer fail to compile until you handle it. That compile error across `render`, `countNodes`, and `depth` is the payoff of the sealed model — undo the seventh case afterward.

## Milestone 4 — The CLI and round-trip property (≈ 1 h)

Wire a tiny `main` in `Main.kt` that reads JSON from a file argument (or stdin), parses it, and pretty-prints the tree or the error:

```kotlin
package com.crunch.json

import java.io.File

fun main(args: Array<String>) {
    val input = args.firstOrNull()?.let { File(it).readText() }
        ?: System.`in`.readBytes().decodeToString()
    when (val result = parseJson(input)) {
        is ParseResult.Ok  -> println(render(result.value))
        is ParseResult.Err -> { System.err.println("parse error: ${result.error}"); kotlin.system.exitProcess(1) }
    }
}
```

Then prove a key property in tests: **parse-then-render is stable**. For valid input, `render(parseJson(input).value)` should itself re-parse to an equal tree. (It won't be byte-identical to the original — whitespace and key order can differ — but `parseJson(render(parseJson(x).value)) == parseJson(x)` for the tree should hold.) This round-trip test catches a whole class of parser/renderer bugs.

---

## Acceptance criteria

- [ ] `JsonNode` is a **sealed interface** with the six cases (`JsonObject`, `JsonArray`, `JsonString`, `JsonNumber`, `JsonBool`, `JsonNull` as a `data object`); no "type enum + nullable fields" design.
- [ ] Parsing returns a **typed `ParseResult`** with a sealed `ParseError`; no exception escapes the parser on malformed input.
- [ ] A hand-rolled **lexer** (`tokenize`) and **recursive-descent parser** (`parseValue`/`parseObject`/`parseArray`) — **no external JSON library**.
- [ ] Strings handle escapes (`\"`, `\\`, `\n`, `\t`, `\uXXXX`); numbers handle integers, decimals, negatives, and exponents; `true`/`false`/`null` are recognized.
- [ ] At least **two** consumers `when` over `JsonNode` **exhaustively with no `else`** (`render` plus one of `countNodes`/`depth`).
- [ ] Trailing-garbage input (`{"a":1}xyz`) is an **error**, not a silent accept.
- [ ] JUnit 5 tests cover: each node type, nested structures, every `ParseError` case, and the parse→render→parse round-trip on the tree.
- [ ] A `main` that parses a file/stdin and prints the tree or the error.
- [ ] Build with **0 warnings, 0 errors**.

## Stretch goals

- **A typed accessor DSL.** Add extension functions for ergonomic navigation: `JsonNode.get(key: String): JsonNode?` (object lookup), `JsonNode.get(index: Int): JsonNode?` (array index), `JsonNode.asString(): String?`, `asNumber(): Double?`. Now `tree["user"]?.get("name")?.asString()` reads cleanly — and each accessor `when`s over the sealed type internally.
- **Pretty-printing with indentation.** A `renderPretty(node, indent)` that produces human-readable, indented JSON. A good recursion exercise.
- **Better error messages with line/column.** Track line and column (not just absolute position) in the lexer so errors read "unexpected `}` at line 3, column 12."
- **Property-based round-trip test.** Generate random `JsonNode` trees, render them, re-parse, and assert equality. (Hand-roll a small generator; full property-testing libraries are later.)
- **Compare against kotlinx.serialization.** After you ship yours, parse the same input with `Json.parseToJsonElement(...)` from kotlinx.serialization (read-only — don't replace your parser) and compare the shapes. Seeing the production library's `JsonElement` mirror your `JsonNode` is the satisfying confirmation that you built the real thing.

## What this milestone earns you

You can now design an algebraic data type for a real domain (a sum of cases, each a product of its valid data), build a clean boundary that turns untrusted text into a well-typed tree, and consume that tree with exhaustive `when`s the compiler enforces. More than that: you internalized "parse, don't validate" — the parser is the boundary, and everything past it works with clean types and no platform-type leaks. This is the exact modelling skill Phase 2 uses for `UiState`, Phase 3 uses for typed `NetworkResult`, and the capstone uses for its gRPC wire types. The JSON parser is small, but the shape — sealed sum, typed errors, exhaustive consumer — is the shape of correct domain modelling for the rest of the track. Next week makes the parser *generic* (a `Parser<T>` with inline combinators), so the algebraic core you built here becomes the substrate for abstraction.
