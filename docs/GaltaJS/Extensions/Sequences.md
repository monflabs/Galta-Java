# Sequences

Standard JavaScript expressions produce one value. With `supportSequenceExtensions` (on in `GaltaJSEnvironment`) an expression can produce a *sequence* of values, and operators, member accesses and calls apply to every item. Sequences are what the [JSON Path](/GaltaJS/Extensions/JsonPath) operators are built on. A sequence only lives inside an expression: when it is assigned, passed as an argument or returned, it collapses back to a plain value or an `Array`.

## From values to sequences and back

A value becomes a sequence when a sequence operator is applied to it. The two basic ones are `.*` and `[*]` (equivalent), which *flatten* an array into a sequence of its items; applied to a non-array value they produce a one-item sequence, and applied to a plain object they produce the sequence of its property values.

Once an expression holds a sequence, every operator broadcasts. At the end of the expression the sequence collapses:

| Items | Collapses to |
|---|---|
| 0 | `undefined` |
| 1 | the item |
| 2 or more | an `Array` of the items |

Sample: `doc_examples/SequencesExamples.java` (`testFlattenAndCollapse`)

```java
JSEnvironment env = GaltaJSEnvironment.create();
// Without a sequence, + on an array is string concatenation
assertEquals("2,310", env.evaluateExpression("[2,3] + 10"));
// .* turns the array into a sequence; operators then apply to every item
assertEquals(List.of(12, 13), list(env.evaluateExpression("[2,3].* + 10")));
assertEquals(List.of(12, 13), list(env.evaluateExpression("[2,3][*] + 10")));

// A sequence collapses when it leaves the expression: 0 items -> undefined, 1 -> the item, more -> an Array
assertEquals(List.of(1, 3), list(env.evaluateExpression("[{a:1,b:2},{a:3}].*.a")));
assertEquals(2, env.evaluateExpression("[{a:1,b:2},{a:3}].*.b"));
assertSame(RuntimeUtil.UNDEFINED, env.evaluateExpression("[{a:1,b:2},{a:3}].*.c"));
```

Internally an expression result is a `rt/JSResult`, which is either a plain value or a sequence in one of the states `SEQ_EMPTY`, `SEQ_ONE`, `SEQ_MULTI` (the last backed by a `JSArray`); `deref` performs the collapse above.

## Member access and calls

On a sequence, `.name` reads the member of every item and skips items that are `null` or `undefined` instead of throwing. A method call applies to every item and the results form a new sequence.

```java
assertEquals(List.of(1, 2), list(env.evaluateExpression("[{a:1}, null, {a:2}].*.a")));
assertEquals(List.of("A", "B"), list(env.evaluateExpression("['a','b'].*.toUpperCase()")));
```

Sample: `doc_examples/SequencesExamples.java` (`testMemberAccessIsForgiving`)

Items that lack the member contribute nothing, which is why `[{a:1,b:2},{a:3}].*.b` is `2` (one hit) and `.*.c` is `undefined` (no hit).

## Materializing with `[]`

The empty-brackets operator turns the current sequence into an `Array` with exactly its items, whatever their count. It is the way to get `[]` for an empty result and `[x]` for a single one. On a plain value it wraps the value.

```java
assertEquals(List.of(), list(env.evaluateExpression("[{a:1}].*.c[]")));
assertEquals(List.of(1), list(env.evaluateExpression("[{a:1}].*.a[]")));
assertEquals(1, env.evaluateExpression("[{a:1},{a:2}].*.a[][0]"));
assertEquals(List.of(1), list(env.evaluateExpression("1[]")));
assertEquals(List.of("xyz"), list(env.evaluateExpression("'xyz'[]")));
```

Sample: `doc_examples/SequencesExamples.java` (`testMaterializingWithBrackets`)

The result of `[]` is an `Array`, not a sequence: `matches[].length` on an array that already collapsed gives `1` (the array wrapped once more). Use `[]` on sequences, `.length` on arrays.

## Operator broadcasting

| Situation | Rule |
|---|---|
| unary operator on a sequence (`-`, `!`, `typeof`, `++`, `delete`...) | applied to each item |
| sequence `op` scalar (or scalar `op` sequence) | applied between each item and the scalar |
| sequence `op` sequence | applied pairwise; when one sequence is shorter, its last item is reused for the remaining items |
| empty sequence | the result is an empty sequence, i.e. `undefined` |

A plain `Array` operand is a scalar (`[1,2].* + [1]` concatenates strings), only `.*`/`[*]` create sequences.

```java
assertEquals(List.of(3, 6, 9), list(env.evaluateExpression("[1,2,3].* * 3")));
assertEquals(List.of(51, 62, 63, 64), list(env.evaluateExpression("[1,2,3,4].* + [50,60].*")));
assertEquals(List.of("number", "string", "boolean"), list(env.evaluateExpression("typeof [1,'s',true].*")));
assertSame(RuntimeUtil.UNDEFINED, env.evaluateExpression("[].* + 1"));
assertEquals(List.of("11", "21"), list(env.evaluateExpression("[1,2].* + [1]")));
```

Sample: `doc_examples/SequencesExamples.java` (`testOperatorBroadcasting`)

## Any versus all: comparisons

Comparison, equality, `in` and `instanceof` do not return a sequence of booleans; they reduce to one boolean. The standard operator is true when *any* item matches, and a `*`-prefixed variant requires *all* items:

| Any | All |
|---|---|
| `==` `!=` `===` `!==` | `*==` `*!=` `*===` `*!==` |
| `<` `>` `<=` `>=` | `*<` `*>` `*<=` `*>=` |
| `in` | `*in` |
| `instanceof` | `*instanceof` |

```java
assertEquals(true, env.evaluateExpression("[10,12].* == 10"));
assertEquals(false, env.evaluateExpression("[10,12].* *== 10"));
assertEquals(true, env.evaluateExpression("[10,10].* *== 10"));
assertEquals(true, env.evaluateExpression("[1,5].* > 4"));
assertEquals(false, env.evaluateExpression("[1,5].* *> 4"));
assertEquals(true, env.evaluateExpression("'a' in [{a:1},{b:2}].*"));
assertEquals(false, env.evaluateExpression("'a' *in [{a:1},{b:2}].*"));
```

Sample: `doc_examples/SequencesExamples.java` (`testAnyVersusAllComparisons`)

The lexer resolves `a*index` as multiplication and `*in` / `*instanceof` as the all-variants by looking at what follows the star; `1 *in x` is never a syntax ambiguity in practice.

## Assignment through sequences

Assignment, compound assignment, `++`/`--` and `delete` accept a sequence on the left and update every target. Missing members are created by `=`; with `+=` a missing member yields `NaN`, as `undefined + n` does in JavaScript.

```java
Object r = env.evaluateScript("""
	const items = [{a: 1}, {a: 1}, {b: 2}];
	items.*.a = 20;
	items.map(i => i.a)
	""");
assertEquals(List.of(20, 20, 20), list(r));
Object r2 = env.evaluateScript("""
	const items = [{a: 1}, {a: 5}];
	items.*.a += 10;
	items.*.a
	""");
assertEquals(List.of(11, 15), list(r2));
```

Sample: `doc_examples/SequencesExamples.java` (`testAssignmentThroughSequences`)

## Enabling the extension

The grammar always parses the sequence syntax; the flag is checked when the AST is initialized (`ASTNode.init`), so a plain environment fails at compile time with `SyntaxError: Operator .[*] requires Galta extensions to be enabled`. One construct degrades instead of failing: `a[i, j]`, which is a multi-index with the extension, is the standard comma operator (`a[j]`) without it, so `[1,2,3][0,2]` is `3` in plain JavaScript and `[1, 3]` in GaltaJS.

```java
try {
	JavaScriptEnvironment.create().evaluateExpression("[{a:1}].*.a");
	fail();
} catch(JSException e) {
	assertTrue(e.getMessage().contains("requires Galta extensions to be enabled"));
}
JSEnvironment seq = JavaScriptEnvironment.newBuilder().supportSequenceExtensions(true).build();
assertEquals(List.of(2, 3), list(seq.evaluateExpression("[1,2].* + 1")));
```

Sample: `doc_examples/SequencesExamples.java` (`testSequencesMustBeEnabled`)

The `@` reference used inside filters and maps is a separate flag, `supportIdentifierAtSign` (see [Syntax](/GaltaJS/Extensions/Syntax)); `enableGaltaJSExtensions()` sets both.

## Gotchas

- A sequence never escapes an expression: `const s = a.*.b` stores `undefined`, a value, or an `Array`; `s.*` re-creates a sequence from the array.
- `[]` on an already collapsed `Array` wraps it; check whether you still have a sequence.
- Equality on a sequence is "any"; `*==` is "all"; neither returns an array of booleans.
- `.length` on a sequence is broadcast (one length per item), use `[].length` for the count of items.
- `a[i, j]` means something different with and without the extension.

## Source

`rt/JSResult.java` (`SEQ_TYPE`, `deref`), `node/ASTNode.java` (`sequenceEnabled`, `init`), `node/ASTArrayMember.java` and the `ASTArrayMember*` nodes (flatten, filter, map, deep scan), `node/binaryop/*` (`MODE.ONE` / `MODE.ALL`), `parser/JSParser.jj` (`CallExpressionPart`, `LTALL`/`EQALL`/`INALL` tokens).
