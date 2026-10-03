# Syntax Extensions

Besides numbers, sequences and type hints, GaltaJS adds a few small syntax and scoping extensions. Most are tied to a flag on the builder; the operators (`?:`, `|>`, `synchronized`, numeric and keyword member names) are always available.

Sample: `doc_examples/SyntaxExtensionsExamples.java`

## `?:` Elvis operator

`a ?: b` evaluates to `a` when `a` is truthy, otherwise `b`; it is `a ? a : b` with `a` evaluated once. It differs from `??` (which only tests `null`/`undefined`) by treating `''`, `0`, `false` and `NaN` as missing.

```js
null ?: 'dflt'      // => 'dflt'
'' ?: 'dflt'        // => 'dflt'
'value' ?: 'dflt'   // => 'value'
```

## `|>` pipeline operator

`value |> fn` calls `fn(value)`; pipelines chain left to right.

```js
[3,1,2] |> (a => a.sort())                              // => [1, 2, 3]
'hello' |> (s => s.toUpperCase()) |> (s => s + '!')     // => 'HELLO!'
```

## Numeric and keyword member names

A member name after `.` can be a number (an index) or a reserved word.

```js
({0: 'zero'}).0            // => 'zero'
['a','b'].1                // => 'b'
({for: 1, class: 2}).for   // => 1
```

## `return` outside a function

With `supportReturnOutsideFunction`, a top-level `return` ends the script and provides its value, which is convenient for script snippets embedded in Java.

```java
assertEquals(5, env.evaluateScript("if (true) { return 5 } 6"));   // GaltaJSEnvironment
try {
	JavaScriptEnvironment.create().evaluateScript("return 5");
	fail();
} catch(JSException e) {
	// standard JavaScript: "return" is only valid inside a function
}
```

## Top-level object literal

With `supportTopLevelObjectLiteral`, a script whose *entire* text is an object literal - `{}`, `{a: 1}`, `{"a": 1}` - evaluates to that object, where ECMA-262 reads a block or a labeled statement. A REPL-style convenience for object-shaped snippets handed to `evaluateScript`; it never applies to a `{` that is merely the first statement of a longer program, so blocks keep their meaning.

```java
JSObject o = (JSObject)env.evaluateScript("{a: 1}");               // GaltaJSEnvironment
assertEquals(1, o.getProperty("a"));
assertEquals(1, JavaScriptEnvironment.create().evaluateScript("{a: 1}"));   // standard: label `a`, expression 1
```

## `synchronized` statement

A Java-style block that holds the monitor of an object while it runs. It exists for scripts that share Java objects between threads; JavaScript code itself runs on one thread at a time.

```js
const lock = {};
let v = 0;
synchronized(lock) { v = 1 }
v   // => 1
```

## `global` alias

`supportGlobalAlias` adds `global` as another name for `globalThis`, as in Node.js. It is off by default and not set by `enableGaltaJSExtensions()`.

```java
JSEnvironment aliased = JavaScriptEnvironment.newBuilder().supportGlobalAlias(true).build();
assertEquals(true, aliased.evaluateExpression("global === globalThis"));
assertEquals("undefined", JavaScriptEnvironment.create().evaluateExpression("typeof global"));
```

## `@` current item

Inside a sequence filter `[?( )]` or map `.( )`, `@` refers to the item being examined. It is an identifier gated by `supportIdentifierAtSign`; the grammar reserves `@` as a token, so it cannot start a longer identifier (`@x` is not valid).

```js
[1,2,3][?(@ > 1)]      // => [2, 3]
[1,2][*].(@ * 10)      // => [10, 20]
```

See [JSON Path](/GaltaJS/Extensions/JsonPath).

## `$` in identifiers

`$` is a valid identifier character in standard JavaScript too; GaltaJS marks it as an extension in its grammar only because the JSON-path examples rely on `$` as the document root. Names such as `worldCup$` are a convention in the engine's tests for "a value that will be queried", not a language rule.

## Strict mode and declared variables

`enableGaltaJSExtensions()` sets `strictMode(true)` and `mustDeclareAllVariables(true)`: scripts run in strict mode and assigning an undeclared name is a `ReferenceError`. A plain `JavaScriptEnvironment` is sloppy by default.

```java
assertEquals("ReferenceError", env.evaluateScript("let r; try { undeclared = 1 } catch(e) { r = e.name } r"));
assertEquals(1, JavaScriptEnvironment.create().evaluateScript("sloppy = 1; sloppy"));
```

Calling `strictMode(true)` directly also sets `mustDeclareAllVariables` and clears `deprecatedApis` (`escape`/`unescape`); `enableGaltaJSExtensions()` turns `deprecatedApis` back on.

The flag gives strict-mode semantics: no `with`, `this` is `undefined` in a plain function call, writing to a frozen object throws, `delete` of a variable is an error. A few early errors are only raised by a real `"use strict"` directive, though:

- A number with a leading `0` (`01234`) reads as a decimal number, `1234`, instead of the legacy octal `668` of sloppy JavaScript. With `"use strict"`, it is a `SyntaxError`, as in JavaScript.
- Duplicate parameter names (`function f(a, a)`) and declaring a variable named `eval` or `arguments` are accepted.

## Other details

- A `#!` shebang line at the start of a script is skipped.
- Decorators (`@decorator class {}`) are parsed and only partially run (see [Known Gaps](/GaltaJS/KnownGaps)); auto-accessors (`accessor x`, `accessor #x`) are fully implemented.

## Gotchas

- `?:` is not `??`: `0 ?: 1` is `1`.
- With `strictMode`, `010` is `10`, while a plain `JavaScriptEnvironment` reads it as octal `8`.
- `return` at the top level is a parse error without `supportReturnOutsideFunction`.
- `global` only exists with `supportGlobalAlias`; `globalThis` always does.
- `@` requires `supportIdentifierAtSign`; a filter can still use a named function or arrow without it.

## Source

`parser/JSParser.jj` (`ELVIS`, `PIPELINE`, `AT`, `ConditionalExpression`, `PrimaryExpression`, the `synchronized` statement), `node/ternaryop/*`, `node/call/ASTPipelineCall.java`, `node/control/ASTReturn.java`, `node/control/ASTSynchronized.java`, `node/ASTIdentifier.java`, `rt/builtins/GlobalThis.java` (`GLOBAL_ALIAS`), `ConfigurationImpl.java`.
