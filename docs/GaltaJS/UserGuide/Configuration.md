# Environments & Configuration

A `JSEnvironment` is the realm in which scripts run: it owns the standard objects, the configuration flags, the registered libraries, the module resolvers and the caches. It is created once with a builder and then shared; individual executions get their own runtime context (see [Executing Code](/GaltaJS/UserGuide/ExecutingCode)). This page lists every builder option and what the two prebuilt environments configure.

## Prebuilt environments

Both classes live in `org.monflabs.galtajs.environments` and expose `newBuilder()` (a pre-configured `JSEnvironment.Builder` you can keep customizing) and `create()` (builds it immediately).

| Class | Extensions | Libraries registered |
|---|---|---|
| `JavaScriptEnvironment` | none (plain ECMAScript) | `StandardLibrary` |
| `GaltaJSEnvironment` | `enableGaltaJSExtensions()` | `StandardLibrary`, `JavaLibrary` |

Sample: `doc_examples/ConfigurationExamples.java` (`testPrebuiltEnvironments`)

```java
// Plain ECMAScript: no GaltaJS extension, so a Decimal literal is a syntax error
JSEnvironment js = JavaScriptEnvironment.create();
assertFalse(js.supportBigDecimalLiteral());
try {
    js.evaluateExpression("1m");
    fail();
} catch(JSParseException e) {
    // 'm' is not a valid suffix in plain ECMAScript
}

// GaltaJS: extensions + StandardLibrary + JavaLibrary
JSEnvironment galta = GaltaJSEnvironment.create();
assertTrue(galta.supportBigDecimalLiteral());
assertTrue(galta.supportSequenceExtensions());
assertTrue(galta.supportJavaNative());
assertEquals(new BigDecimal("1"), galta.evaluateExpression("1m"));
assertEquals("function", galta.evaluateExpression("typeof Java.type"));
```

The environment implements `JSConfiguration`, so every flag can be read back (`env.isStrictMode()`, `env.supportLongPromotion()`, ...).

## The builder

`JSEnvironment.newBuilder()` returns a `JSEnvironment.Builder`. Setters are fluent; `build()` creates the environment. `configure(Consumer<Builder>)` applies a block of settings, which is convenient for shared configuration code.

Sample: `doc_examples/ConfigurationExamples.java` (`testBuilderOptions`, `testConfigureWithAConsumer`)

```java
JSEnvironment env = JavaScriptEnvironment.newBuilder()
        .strictMode(true)             // scripts run as if they started with "use strict"
        .supportGlobalAlias(true)     // adds `global` as an alias of globalThis
        .supportLongPromotion(true)   // int overflow promotes to Long instead of Double
        .build();
assertEquals("ReferenceError", env.evaluateScript("let r; try { undeclared = 1 } catch(e) { r = e.name } r"));
assertEquals("object", env.evaluateExpression("typeof global"));
assertEquals(2147483648L, env.evaluateExpression("2147483647 + 1"));
```

```java
JSEnvironment env = JSEnvironment.newBuilder()
        .configure(b -> {
            b.registerLibrary(new StandardLibrary());
            b.supportBigDecimalLiteral(true);
            b.supportBigDecimal(true);
        })
        .build();
assertEquals("decimal", env.evaluateExpression("typeof 1.5m"));
```

A builder can be used once. After `build()`, every setter throws `IllegalStateException("Builder has been used and cannot be updated")` (`testBuilderIsSingleUse`).

## Option reference

Defaults are those of a bare `JSEnvironment.newBuilder()` (from `ConfigurationImpl`). The last column shows the value set by `enableGaltaJSExtensions()`; a dash means the option is left untouched.

### Syntax and strictness

| Builder method | Default | Effect | GaltaJS |
|---|---|---|---|
| `strictMode(boolean)` | `false` | Scripts run in strict mode. Also sets `mustDeclareAllVariables` to the same value and `deprecatedApis` to its opposite. | `true` |
| `mustDeclareAllVariables(boolean)` | `false` | Assigning an undeclared variable is an error. | `true` |
| `deprecatedApis(boolean)` | `true` | Registers `escape()` / `unescape()`. | `true` |
| `supportIdentifierAtSign(boolean)` | `false` | Allows the bare `@` reference (current item in `[?()]` filters and `.()` maps). | `true` |
| `supportReturnOutsideFunction(boolean)` | `false` | Allows a top-level `return` statement. | `true` |
| `supportSequenceExtensions(boolean)` | `false` | Enables sequences and the JSON path operators (`.*`, `..`, `[?()]`, slices, ...). | `true` |
| `supportTypeHints(boolean)` | `false` | Accepts and discards TypeScript-style type annotations. | `true` |
| `supportTopLevelObjectLiteral(boolean)` | `false` | A script that is entirely an object literal (`{a: 1}`) evaluates to the object instead of a block/labeled statement. | `true` |
| `supportGlobalAlias(boolean)` | `false` | Defines `global` as an alias of `globalThis`. | - |

### Numbers

| Builder method | Default | Effect | GaltaJS |
|---|---|---|---|
| `supportLongPromotion(boolean)` | `false` | An `int` overflow produces a `Long` instead of a `Double`. | `true` |
| `supportBigIntPromotion(boolean)` | `false` | A `Long` overflow produces a `BigInteger` (BigInt). | `false` |
| `supportBigDecimal(boolean)` | `false` | `BigDecimal` values get the Decimal accessor (methods, arithmetic). | `true` |
| `supportBigDecimalLiteral(boolean)` | `false` | Accepts the `1.5m` literal syntax. | `true` |
| `supportBigDecimalPromotion(boolean)` | `false` | JSON numbers that do not fit a `double` are parsed as `BigDecimal`. | - |
| `supportMixedBigNumber(boolean)` | `false` | Allows mixing BigInt/Decimal with regular numbers in one operation. | `true` |
| `supportBigNumberMath(boolean)` | `false` | `Math` accepts BigInt/Decimal arguments and exposes `Math.PIm`, `Math.SQRT2m`, ... | `true` |
| `forceBigDecimalOperations(boolean)` | `false` | Every floating point literal and operation uses `BigDecimal`. | - |
| `supportParseIntOctal(boolean)` | `true` | Reserved: the flag is not read by the runtime today. | `false` |
| `mathContext(MathContext, String)` | `DECIMAL128`, `"java.math.MathContext.DECIMAL128"` | Precision of Decimal arithmetic; the string is the Java expression emitted by the transpiler. | - |

### Standard objects and Java

| Builder method | Default | Effect | GaltaJS |
|---|---|---|---|
| `supportJavaNative(boolean)` | `false` | Enables the `$`-prefixed access to the Java members of any value. Registering `JavaLibrary` turns it on. | `true` |
| `supportFloat16Array(boolean)` | `false` | Registers the `Float16Array` constructor. | - |
| `optimizedSetAndMapCtor(boolean)` | `false` | Fast path in the `Map`/`Set`/`WeakMap`/`WeakSet` constructors. | `true` |
| `regexpEngineFactory(BiFunction<JSEnvironment,RegExp,RegExpEngine>)` | Joni if available, else JDK | Selects the regular expression engine (see [Regular Expressions](/GaltaJS/UserGuide/RegularExpressions)). | - |

### Libraries, modules, caches and misc

| Builder method | Default | Effect |
|---|---|---|
| `registerLibrary(JSLibrary)` | none | Adds a library (globals, accessors, module resolvers). See [Libraries](/GaltaJS/Extending/Libraries). |
| `addModuleResolver(JSModuleResolver)` | none | Adds a module resolver. See [Modules](/GaltaJS/UserGuide/Modules). |
| `scriptCacheSize(int)` | `0` (disabled) | LRU cache of parsed programs used by `createScript()`. |
| `evalCacheSize(int)` | `0` (disabled) | LRU cache for `eval()` programs. |
| `regexpCacheSize(int)` | `0` (disabled) | LRU cache of compiled regular expressions. |
| `scriptOptimizer(ScriptOptimizer)` | `ScriptOptimizer.defaultOptimizer()` | AST optimizer. See [Execution Modes](/GaltaJS/UserGuide/ExecutionModes). |
| `classLoader(ClassLoader)` | the engine's class loader | Used by `Java.type()` and the transpiler. |
| `debug(boolean)` | `false` | Instruments parsed scripts with debug hooks. See [Debugging](/GaltaJS/UserGuide/Debugging). |
| `putProperty(String, Object)` | none | Free-form host properties, read back with `getProperty`, `getPropertyString`, `getPropertyBoolean`, `getPropertyInt`. |

`enableGaltaJSExtensions()` sets exactly the values in the "GaltaJS" columns above; it registers no library.

## Properties

Sample: `doc_examples/ConfigurationExamples.java` (`testCustomProperties`)

```java
JSEnvironment env = JavaScriptEnvironment.newBuilder()
        .putProperty("app.name", "demo")
        .putProperty("app.debug", true)
        .build();
assertEquals("demo", env.getPropertyString("app.name"));
assertTrue(env.getPropertyBoolean("app.debug"));
assertNull(env.getProperty("missing"));
```

## Decimal precision

Sample: `doc_examples/ConfigurationExamples.java` (`testDecimalPrecision`)

```java
JSEnvironment env = GaltaJSEnvironment.newBuilder()
        .mathContext(MathContext.DECIMAL64, "java.math.MathContext.DECIMAL64")
        .build();
assertEquals(new BigDecimal("0.3333333333333333"), env.evaluateExpression("1m/3m"));
```

## Caches

All caches are off unless a size is given. With a script cache, `createScript()` returns a new unit on every call but shares the parsed program when the text, the name and the parsing flags match.

Sample: `doc_examples/ConfigurationExamples.java` (`testScriptCache`)

```java
JSEnvironment cached = JavaScriptEnvironment.newBuilder().scriptCacheSize(64).build();
JSInterpretedUnit c = cached.createScript("1 + 1", "cached.js");
JSInterpretedUnit d = cached.createScript("1 + 1", "cached.js");
assertSame(c.getProgram(), d.getProgram());   // the parsed program is shared
assertNotSame(c, d);                          // each call still gets its own unit
assertEquals(2, d.execute());
```

A program is cached only when it is created with the `SCRIPT_ADDTOCACHE` flag (the default of `createScript`) and is not a CommonJS unit, not force-strict and not an `eval` with caller-specific restrictions. `evaluateScript()` and `evaluateExpression()` bypass the cache.

## Gotchas

- `enableGaltaJSExtensions()` only sets flags. Without `StandardLibrary`, `Math` is an unknown identifier (`testEnableGaltaJSExtensionsRegistersNoLibrary`).
- The builder is single-use.
- `supportParseIntOctal` exists in the configuration but nothing reads it at runtime.
- `ConfigurationImpl` has an `isSharedStandardObjects` flag but the builder exposes no setter for it; every `build()` returns a distinct environment.
- Registering `JavaLibrary` implies `supportJavaNative(true)` because the library's `configureEnvironment()` sets it.

## Source

`JSEnvironment.java` (`Builder`), `JSConfiguration.java`, `ConfigurationImpl.java`, `environments/JavaScriptEnvironment.java`, `environments/GaltaJSEnvironment.java`
