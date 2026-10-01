# GaltaJS JavaScript Engine

?> Galta is a prototyping library provided as is, without any production support. See [A prototyping library](/?id=a-prototyping-library).

GaltaJS is an implementation of JavaScript in Java. It follows the ECMAScript specification as closely as it can (its compliance is measured continuously against the TC39 [test262](https://github.com/tc39/test262) suite, see [Known ECMAScript Gaps](/GaltaJS/KnownGaps)) and it is designed to integrate tightly with Java and the JVM, which makes it a natural choice for scripting Java applications. On top of the standard language it offers a set of [extensions](/GaltaJS/Extensions/) that make it even better at working with Java values and JSON data.

?> **Try it in your browser:** the [GaltaJS playground](playground/ ':ignore') runs the engine, its extensions and a set of samples directly in the page, through [CheerpJ](https://cheerpj.com) - nothing to install.

## Highlights

- **No wrapper types.** A JavaScript number is a `java.lang.Integer` or `Double`, a string is a `java.lang.String`, a Date is a `java.util.Date`, an object is a `JSObject` that also implements the GaltaJSON `JsonObject` interface. Values cross the Java boundary as they are. See [Values & the Java API](/GaltaJS/UserGuide/Values).
- **Two execution modes.** Scripts are either interpreted directly from their syntax tree, for fast start-up, or transpiled to Java source code and compiled, for the best runtime performance and to ship JavaScript libraries as Java classes. See [Execution Modes](/GaltaJS/UserGuide/ExecutionModes).
- **Java interop.** Any Java class can be loaded with `Java.type()`, constructed, and used with bean-style properties, overloaded methods, arrays and functional interfaces. See [Java Interop](/GaltaJS/UserGuide/JavaInterop).
- **Full async model.** Promises, `async`/`await`, generators, async generators, top-level `await`, timers and an event loop that Java can drive. See [Async & the Event Loop](/GaltaJS/UserGuide/Async).
- **ES modules.** Static and dynamic imports, import attributes, `import defer`, CommonJS `require`, and pluggable module resolvers, including modules implemented in Java. See [Modules](/GaltaJS/UserGuide/Modules).
- **Extensible.** Libraries contribute globals and functions, accessors teach the engine how to expose any Java class, resolvers provide modules. See [Extending the Engine](/GaltaJS/Extending/).
- **Extensions.** Decimal (`BigDecimal`) arithmetic, integer promotion to `Long`/`BigInt`, sequences and JSON Path operators, TypeScript-style type hints, and a few syntax additions. See [Extensions](/GaltaJS/Extensions/).
- **Debuggable.** A Chrome DevTools Protocol server, an in-process debugger API and a Swing debugger UI. See [Debugging](/GaltaJS/UserGuide/Debugging).

## A first look

```java
JSEnvironment env = GaltaJSEnvironment.create();
Object value = env.evaluateScript("""
    const prices = [{ item: 'pen', price: 1.5m }, { item: 'book', price: 12m }];
    prices.*.price[].reduce((a, b) => a + b, 0m)
    """);
// value is the java.math.BigDecimal 13.5
```

`prices.*.price` is a GaltaJS [sequence](/GaltaJS/Extensions/Sequences) expression (`[]` turns the sequence into an Array), `1.5m` is a [Decimal](/GaltaJS/Extensions/Decimal) literal, and the result is a plain Java `BigDecimal`. With `JavaScriptEnvironment.create()` instead, the same engine runs plain ECMAScript with none of the extensions enabled.

## Maven artifacts

All artifacts use the `org.monflabs.galta` group id and share the project version. They are published to Maven Central, except the ones marked *not published*, which are built from source.

| Artifact | Purpose |
|---|---|
| `js` | The engine. This is the only dependency most applications need; `json` and `javacompiler` come transitively. |
| `js-all` | A single fat jar bundling `js` and its dependencies (*not published*: built locally as `target/galtajs-all.jar`). |
| `js-transpiler-maven` | Maven plugin transpiling `.js` files to Java sources at build time. |
| `js-template`, `js-vb` | Text templates and `${...}` value bindings evaluated by the engine. |
| `js-debugger` | Swing debugger front-end speaking the Chrome DevTools Protocol. |
| `js-playground` | Interactive playground application. |
| `js-precompiled-beautify-js`, `-css`, `-html` | js-beautify formatters transpiled to Java at build time. |

See [Companion Modules](/GaltaJS/UserGuide/CompanionModules) for details on each of them.

## How this documentation is organized

| Section | Audience | Content |
|---|---|---|
| [User's Guide](/GaltaJS/UserGuide/) | Application developers embedding the engine | Environments, configuration, executing code, values, Java interop, async, modules, errors, regular expressions, debugging. |
| [Extending the Engine](/GaltaJS/Extending/) | Developers adding Java capabilities to scripts | Libraries, accessors, native modules and resolvers, the bundled libraries. |
| [Extensions](/GaltaJS/Extensions/) | Script authors | Every GaltaJS addition to ECMAScript, with the configuration flag that enables it. |
| [Architecture](/GaltaJS/Architecture/) | Engine contributors | Parser and AST, optimizer, interpreter, transpiler, async runtime, modules, object model, regexp engines, debugger, tests. |
| [Known ECMAScript Gaps](/GaltaJS/KnownGaps) | Everyone | The living list of deviations from the specification. |

Every Java and JavaScript sample shown in these pages comes from a JUnit test under `galta/parent-js/js/src/test/java/doc_examples` (suite `AllDocExamplesTests`), which runs with every build of the `js` module. When a page shows `Sample: doc_examples/XxxExamples.java (testYyy)`, that test is the executable version of the snippet.

## Requirements

- Java 21 or later (the transpiler needs a JDK, not just a JRE, to compile the generated Java code at runtime; generators and async functions run on virtual threads).
- Maven 3.8+ to build from source. See [Building and Releasing](/BuildAndRelease) for the build commands and the release profiles.
