# GaltaJS

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/js?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/js)

GaltaJS is an implementation of JavaScript in Java, with optional extensions
focused on Java integration, JSON data handling and number precision. It follows
the ECMAScript specification as closely as it can: its compliance is measured
against the TC39 [test262](https://github.com/tc39/test262) suite, in which every
file in scope passes in the interpreted, optimized and transpiled modes (see
[Known ECMAScript Gaps](../../../docs/GaltaJS/KnownGaps.md) for the few
deliberate deviations).

This module is the engine itself: parser, interpreter, transpiler to Java,
runtime, standard library, Java interop, module loaders, debugger API and Chrome
DevTools Protocol server. It is the only dependency most applications need.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>js</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare `<version>` directly. `json` and `javacompiler` (and through them
`utilities`) come transitively. Java 21 or later is required, and the transpiler
needs a JDK (not just a JRE) because it compiles the generated Java code at run
time.

```java
JSEnvironment env = JavaScriptEnvironment.create();
Object o = env.evaluateExpression("1+Math.abs(-2)");
// The result is a plain java.lang.Integer - no wrapper type
assertEquals(3, o);
```

`JavaScriptEnvironment.create()` runs plain ECMAScript; `GaltaJSEnvironment.create()`
enables the GaltaJS extensions and the Java interop library.

## Implementation details

To make the integration with Java efficient and seamless, all the values are
actual Java values, from primitives to objects. There are no wrappers like
`JSNumber` or `JSBoolean`: the engine deals directly with `Number` (`Integer`,
`Long`, `Double`, ...), `Boolean`, `String` and so on. A JavaScript `Date` is a
`java.util.Date`, a JavaScript object is a `JSObject` that also implements the
GaltaJSON `JsonObject` interface, and an array is a `JSArray` (a `JsonArray`).

Because these values have no common base class, the engine accesses their
properties through *accessors*. A `JSAccessor` implements the JavaScript object
protocol (prototype, properties, descriptors, extensibility) for a Java class,
and the environment maps every class to its accessor:

```java
JSAccessor acc = env.getAccessor(myObject);
Object prototype = acc.getPrototype(myObject);
Object value = acc.getProperty(myObject, "toString", null);
```

Objects implemented for the engine, like `JSObject` or `JSArray`, carry their
JavaScript data (properties, prototype) themselves. Objects defined outside the
engine, like `Date`, keep their JavaScript data in a weak identity map owned by
the environment. A primitive and its wrapper object share the same Java class
(`"abc"` and `new String("abc")` are both `java.lang.String`): the value is an
object exactly when that map has an entry for it.

```js
const s1 = "a string"              // a primitive: a Java String
const s2 = new String("a string")  // an object: a Java String with an entry in the map
```

## Execution modes

```
JS source -> ScriptPreProcessor -> JSParser (JavaCC) -> AST -> [optimizer] -> interpreter
                                                                           \-> JSTranspiler -> Java source -> javacompiler
```

- **Interpreted**: the syntax tree is executed directly, for a fast start-up;
  the optional `ScriptOptimizer` folds constants and resolves scopes ahead of time.
- **Transpiled**: the syntax tree is converted to Java source code, compiled in
  memory and loaded, for the best runtime performance. The
  [`js-transpiler-maven`](../js-transpiler-maven/README.md) plugin does the same
  at build time, to ship JavaScript libraries as Java classes.

## Contents

Packages under `org.monflabs.galtajs`:

- `JSEnvironment` - the realm: configuration flags (through its `Builder`), libraries, accessors, caches; `environments/` holds the prebuilt `JavaScriptEnvironment` and `GaltaJSEnvironment`
- `parser/` - the JavaCC grammar (`src/main/javacc/.../JSParser.jj`, generated into `target/generated-sources/javacc` at build time) and its support classes
- `node/` - the syntax tree (`ASTProgram` and the node hierarchy), evaluated by the interpreter
- `optimizer/` - AST optimizations (constant folding, scope resolution)
- `transpiler/` - AST to Java source generation (`JSTranspiler`, `JSTranspilerOptions`)
- `rt/` - the runtime: contexts, interpreter and transpiled runtimes, executors and event loop, `JSRuntimeException`; `rt/builtins/` holds the ECMAScript built-ins
- `jsonfactory/` - `JSObject`, `JSArray`, `JSValue`: the JavaScript values as GaltaJSON containers
- `modules/` - module resolvers (memory, file, path, transpiled) and compiled units
- `library/` - libraries contributing globals: `java/JavaLibrary` (Java interop), Node-style (`node/`, with an `fs` module) and Rhino shell shims, `StaticLibrary`, `UnitTestLibrary`
- `preprocessor/` - directive-based source pre-processing
- `debug/` and `cdp/` - the debugger API and the Chrome DevTools Protocol server (WebSocket or in-process)
- `external/` - vendored code: Joni (regular expressions), big-math functions, Rhino's number-to-string conversion (`DToA`)

## JavaScript extensions

Every extension is a flag on `JSEnvironment.Builder`, off in a plain
`JavaScriptEnvironment`; `enableGaltaJSExtensions()` (used by
`GaltaJSEnvironment`) turns most of them on at once. The
[Extensions](../../../docs/GaltaJS/Extensions/README.md) pages list every flag.

### Numbers

Numbers are native Java numbers. The engine computes with `Integer`, `Long`,
`Double`, `BigInteger` (the JavaScript `BigInt`) and `BigDecimal` (a `Decimal`
extension, computed with `MathContext.DECIMAL128` by default), and understands
`Byte`, `Short` and `Float` values as well. Literal suffixes (case insensitive)
choose the class explicitly:

- `const i = 0    // Integer`
- `const i = 0L   // Long`
- `const i = 0.5  // Double (a whole value such as 0.0 is narrowed to Integer)`
- `const i = 0d   // Double`
- `const i = 0.5f // Float`
- `const i = 0n   // BigInteger`
- `const i = 0m   // BigDecimal (supportBigDecimalLiteral)`

Following the specification, an integer operation whose result does not fit an
`int` produces a `Double`, which can lose precision. With `supportLongPromotion`
it produces an exact `Long` instead:

```js
const a = 1492553851;
const b = 1144678303;
a*b // standard: Double(1708494009298794800), mathematically wrong
    // supportLongPromotion: Long(1708494009298794853)
```

A `Long` operand always yields a `Long`, even without the flag
(`1492553851L * 1144678303` is exact). A division that is not exact gives a
`Double` (`4/3`), while `4/2` stays the `Integer` 2. `-0` is a `Double`, so the
sign survives.

Further flags: `supportBigIntPromotion` turns a `Long` overflow into an exact
`BigInt`; `supportMixedBigNumber` allows mixing `BigInt`, `Decimal` and regular
numbers in one operation (`1n + 2`); `forceBigDecimalOperations` makes every
floating-point value a `Decimal`, more precise at the expense of performance.

### Operators

- `a ?: b` (Elvis): `a` when it is truthy, otherwise `b`. Unlike `??`, it treats `''`, `0`, `false` and `NaN` as missing.
- `value |> fn` (pipeline): calls `fn(value)`; pipelines chain left to right.
- `synchronized(lock) { ... }`: holds the Java monitor of an object while the block runs.
- A member name after `.` can be a number or a reserved word (`['a','b'].1`, `o.for`).

Optional chaining (`?.`, `?.[]`, `?.()`) and `??` are the standard ECMAScript operators.

### Java integration

Java classes are available through `Java.type()` (with the Java library,
registered by `GaltaJSEnvironment`). Once loaded, a class can be instantiated
with `new`, Java arrays are created with the Java syntax, and bean properties,
overloaded methods and functional interfaces work as expected:

```js
const ArrayList = Java.type('java.util.ArrayList')
const list = new ArrayList()
const int = Java.type('int')
const ints = new int[3]     // a Java int[]
```

With `supportJavaNative`, a member name starting with `$` bypasses the
JavaScript semantics and reaches the Java member of the underlying object:

```js
const d = new Date()
d.toString()    // 'Thu May 07 2026 21:25:27 GMT-04:00', the JavaScript way
d.$toString()   // 'Thu May 07 21:25:27 EDT 2026', the Java way
```

### Scripts as expressions

With `supportTopLevelObjectLiteral`, a script whose entire text is an object
literal (`{a: 1}`) evaluates to that object, where ECMAScript reads a block or a
labeled statement; a `{` that merely starts a longer program keeps its standard
meaning. With `supportReturnOutsideFunction`, a top-level `return` ends the
script and provides its value.

### JSON data: sequences and JSON Path

With `supportSequenceExtensions`, an expression can produce a *sequence* of
values, and operators, member accesses and calls then apply to every item. `.*`
and `[*]` flatten an array into a sequence, `..name` searches the whole tree,
`[?( )]` filters (with `@` as the current item), `.( )` maps and `[]` turns the
sequence back into an `Array`. When the sequence leaves the expression, it
collapses: no item gives `undefined`, one item gives that item, more give an
`Array`.

```js
[{a:1,b:2},{a:3}].*.a      // [1, 3]
[{a:1,b:2},{a:3}].*.b      // 2
[2,3].* + 10               // [12, 13]
$..book[?(@.price < 10)]   // the books cheaper than 10
```

### Exceptions are Java exceptions

An error raised while running JavaScript code reaches Java as a
`JSRuntimeException`, which carries the thrown JavaScript value
(`getJavascriptException()`), the JavaScript stack (`getStackTraceMessage()`)
and the original Java exception as its cause, if any. Parse errors are
`JSParseException`s.

## Deviations from the specification

The current list is [Known ECMAScript Gaps](../../../docs/GaltaJS/KnownGaps.md):
decorators are only partially run, and `import`/`export` declarations are
accepted in scripts by default (`supportImportExportInScripts(false)` restores
the specification behavior).

`with` is supported in non-strict code. A function read through a `with`
binding is returned as a closure that wraps the function with the `with` object
as `this`, which keeps the transpiler simple but makes it a different object
than the original:

```js
const f1 = myObject.f
with(myObject) {
  const f2 = f;
  // f1 !== f2: f2 is a wrapper around the same function
}
```

`with` is deprecated anyway, and this has no impact on real scripts beyond
compatibility tests.

## Building and testing

From `galta/`:

```sh
mvn clean install -pl parent-js/js --also-make   # this module and its dependencies
mvn test -pl parent-js/js                        # AllGaltaJSTests + AllDocExamplesTests
```

`AllGaltaJSTests` runs the engine's JavaScript test suite in four modes
(interpreted, interpreted and optimized, transpiled, decompiled and re-parsed);
`AllDocExamplesTests` runs every sample shown in the documentation. The external
conformance suites are in [js-test-test262](../js-test-test262/README.md) and
[js-test-rhino](../js-test-rhino/README.md).

## Documentation

- [GaltaJS](../../../docs/GaltaJS/README.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/)) - overview
- [User's Guide](../../../docs/GaltaJS/UserGuide/README.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/UserGuide/)) - environments, executing code, values, Java interop, async, modules, errors, debugging
- [Extending the Engine](../../../docs/GaltaJS/Extending/README.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/Extending/)) - libraries, accessors, native modules
- [Extensions](../../../docs/GaltaJS/Extensions/README.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/Extensions/)) - every GaltaJS addition to ECMAScript
- [Architecture](../../../docs/GaltaJS/Architecture/README.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/Architecture/)) - parser, optimizer, interpreter, transpiler, runtime
- [Known ECMAScript Gaps](../../../docs/GaltaJS/KnownGaps.md) ([online](https://monflabs.github.io/Galta-Java/#/GaltaJS/KnownGaps))
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
- [Playground](https://monflabs.github.io/Galta-Java/playground/) - try the engine in the browser
