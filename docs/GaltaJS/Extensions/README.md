# GaltaJS Extensions

GaltaJS implements ECMAScript and, on top of it, a set of extensions aimed at scripting the JVM and working with JSON data: Java number types and arbitrary-precision decimals, JSON-path-style sequence operators, small syntax additions, TypeScript-style type hints, and direct use of Java values. Every extension is a configuration flag on the `JSEnvironment.Builder` and is off in a bare builder or in `JavaScriptEnvironment`; `enableGaltaJSExtensions()` (used by `GaltaJSEnvironment`) turns most of them on at once.

| Page | Extension |
|---|---|
| [Numbers](/GaltaJS/Extensions/Numbers) | Literal suffixes for every Java number type, overflow promotion to `Long` and `BigInt`, mixing big and regular numbers |
| [Decimal](/GaltaJS/Extensions/Decimal) | The `Decimal` type (`java.math.BigDecimal`), `m` literals, precision, `Math` on big numbers |
| [Sequences](/GaltaJS/Extensions/Sequences) | Multi-valued expression results and operator broadcasting |
| [JSON Path](/GaltaJS/Extensions/JsonPath) | The path operators (`..`, `[*]`, `[?()]`, slices...) built on sequences |
| [Type Hints](/GaltaJS/Extensions/TypeHints) | TypeScript-style annotations, parsed and ignored: type-system information only, no language feature that is not native to JavaScript |
| [Syntax](/GaltaJS/Extensions/Syntax) | `?:`, `|>`, `synchronized`, top-level `return`, `@`, member names, `global` |
| [Java Types](/GaltaJS/Extensions/JavaTypes) | How Java dates, longs, big numbers and collections appear in scripts |

Regular expression engines are not an extension but a configuration choice; see [Regular Expressions](/GaltaJS/UserGuide/RegularExpressions). Reflection-based Java access is the `JavaLibrary`; see [Java Interop](/GaltaJS/UserGuide/JavaInterop).

## Configuration flags

Defaults come from `ConfigurationImpl`; the third column is what `enableGaltaJSExtensions()` sets (a dash means it leaves the default). Each flag has a same-named builder method taking a `boolean`.

| Flag | Default | `enableGaltaJSExtensions()` | Effect | Page |
|---|---|---|---|---|
| `strictMode` | `false` | `true` | Scripts run as if they started with `"use strict"`; also sets `mustDeclareAllVariables` and clears `deprecatedApis` when called directly | [Syntax](/GaltaJS/Extensions/Syntax) |
| `deprecatedApis` | `true` | `true` | `escape`/`unescape` globals | [Bundled libraries](/GaltaJS/Extending/BundledLibraries) |
| `mustDeclareAllVariables` | `false` | `true` | Assigning an undeclared name is a `ReferenceError` | [Syntax](/GaltaJS/Extensions/Syntax) |
| `supportIdentifierAtSign` | `false` | `true` | The `@` current-item reference in filters and maps | [Syntax](/GaltaJS/Extensions/Syntax) |
| `supportReturnOutsideFunction` | `false` | `true` | `return` at the top level of a script | [Syntax](/GaltaJS/Extensions/Syntax) |
| `supportSequenceExtensions` | `false` | `true` | Sequence and JSON-path operators | [Sequences](/GaltaJS/Extensions/Sequences) |
| `supportTypeHints` | `false` | `true` | TypeScript-style annotations are parsed and discarded | [Type Hints](/GaltaJS/Extensions/TypeHints) |
| `supportLongPromotion` | `false` | `true` | `int` overflow gives a `Long` instead of a `Double` | [Numbers](/GaltaJS/Extensions/Numbers) |
| `supportBigIntPromotion` | `false` | `false` | `Long` overflow gives a `BigInt` | [Numbers](/GaltaJS/Extensions/Numbers) |
| `supportBigDecimal` | `false` | `true` | `BigDecimal` values get the `Decimal` accessor | [Decimal](/GaltaJS/Extensions/Decimal) |
| `supportBigDecimalLiteral` | `false` | `true` | The `m` literal suffix | [Decimal](/GaltaJS/Extensions/Decimal) |
| `supportBigDecimalPromotion` | `false` | - | JSON numbers that do not fit a `double` parse as `Decimal` | [Numbers](/GaltaJS/Extensions/Numbers) |
| `supportMixedBigNumber` | `false` | `true` | `BigInt`/`Decimal` and regular numbers can be mixed in one operation | [Numbers](/GaltaJS/Extensions/Numbers) |
| `supportBigNumberMath` | `false` | `true` | `Math` accepts big numbers and exposes `Decimal` constants | [Decimal](/GaltaJS/Extensions/Decimal) |
| `forceBigDecimalOperations` | `false` | - | Every floating-point literal and operation is a `Decimal` | [Decimal](/GaltaJS/Extensions/Decimal) |
| `supportJavaNative` | `false` | `true` | `$`-prefixed access to the Java members of any value; set by `JavaLibrary` too | [Java Interop](/GaltaJS/UserGuide/JavaInterop) |
| `supportGlobalAlias` | `false` | - | `global` as an alias of `globalThis` | [Syntax](/GaltaJS/Extensions/Syntax) |
| `supportFloat16Array` | `false` | - | Registers the `Float16Array` constructor | - |
| `optimizedSetAndMapCtor` | `false` | `true` | Fast paths in the `Map`/`Set`/`WeakMap`/`WeakSet` constructors (marked for removal in the code) | - |
| `supportParseIntOctal` | `true` | `false` | Inert: the only reference in `BuiltinNumberConstructor` is commented out | - |
| `mathContext(MathContext, String)` | `DECIMAL128` | - | Precision of `Decimal` arithmetic | [Decimal](/GaltaJS/Extensions/Decimal) |

Other builder options (`regexpEngineFactory`, `scriptCacheSize`, `evalCacheSize`, `regexpCacheSize`, `debug`, `classLoader`, `scriptOptimizer`, `putProperty`, `registerLibrary`, `addModuleResolver`) are not language extensions; they are listed in [Configuration](/GaltaJS/UserGuide/Configuration).

The flags are read from `JSEnvironment` (which implements `JSConfiguration` by delegating to `ConfigurationImpl`) at parse time for syntax (`ASTLiteral`, `ASTNode.init`, `ASTIdentifier`, `ASTReturn`, the JavaCC grammar for type hints) and at run time for numeric behaviour (`RuntimeUtil`, `MathObject`, `GaltaJsJsonFactory`).

## Source

`JSConfiguration.java`, `ConfigurationImpl.java`, `JSEnvironment.java` (`Builder`, `enableGaltaJSExtensions`), `environments/GaltaJSEnvironment.java`.
