# Decimal

GaltaJS implements a `Decimal` type, an arbitrary-precision decimal number backed by `java.math.BigDecimal`, in the spirit of the TC39 Decimal proposal. Decimals are written with an `m` suffix (`0.1m`), report `typeof` as `decimal`, compute with a configurable `MathContext` (`DECIMAL128` by default) and, unlike `double`, give exact results for base-10 arithmetic: `0.1m + 0.2m` is `0.3`.

## Literals and the `Decimal` function

Sample: `doc_examples/DecimalExamples.java` (`testDecimalLiterals`, `testDecimalFunction`)

```java
JSEnvironment env = GaltaJSEnvironment.create();
Object v = env.evaluateExpression("123456.789m");
assertEquals(BigDecimal.class, v.getClass());
assertEquals(new BigDecimal("123456.789"), v);
assertEquals("decimal", env.evaluateExpression("typeof 1m"));
// Exact arithmetic where doubles are not
assertEquals(0.30000000000000004, env.evaluateExpression("0.1 + 0.2"));
assertEquals(new BigDecimal("0.3"), env.evaluateExpression("0.1m + 0.2m"));
```

The global `Decimal` converts strings and numbers (`Decimal('123456789123456789.25689')`); it is callable but not a constructor.

```java
assertEquals(new BigDecimal("123456789123456789.25689"), env.evaluateExpression("Decimal('123456789123456789.25689')"));
assertEquals(true, env.evaluateExpression("Decimal('1.5') === 1.5m"));
assertEquals("TypeError", env.evaluateScript("let r; try { new Decimal('1') } catch(e) { r = e.name } r"));
```

`Decimal` is registered unconditionally by `JSEnvironment`; the flags control the literal and the accessor:

| Flag | Effect |
|---|---|
| `supportBigDecimalLiteral` | Accepts the `m` suffix (otherwise a `JSParseException`) |
| `supportBigDecimal` | A `BigDecimal` value gets the `BigDecimalAccessor` (`typeof 'decimal'`, arithmetic, `toString`); without it the value is left to the Java library or is opaque |
| `supportMixedBigNumber` | Allows `Decimal` and regular numbers in one operation |
| `supportBigNumberMath` | `Math` works on `Decimal` |
| `forceBigDecimalOperations` | Every floating-point value becomes a `Decimal` |

All but `forceBigDecimalOperations` are set by `enableGaltaJSExtensions()`. On a plain environment enable the first two together:

```java
JSEnvironment env = JavaScriptEnvironment.newBuilder().supportBigDecimalLiteral(true).supportBigDecimal(true).build();
assertEquals(new BigDecimal("2.5"), env.evaluateExpression("1m + 1.5m"));
```

Sample: `doc_examples/DecimalExamples.java` (`testDecimalLiteralsRequireTheExtension`)

## Precision: `MathContext`

Division and transcendental functions need a precision. The builder's `mathContext(MathContext ctx, String transpilerExpression)` sets it; the default is `MathContext.DECIMAL128` (34 significant digits). The second argument is the Java expression the transpiler emits for the same context (for example `"java.math.MathContext.DECIMAL64"`), because transpiled code carries its configuration as generated Java source.

```java
// DECIMAL128 (34 digits) by default
assertEquals(new BigDecimal("0.3333333333333333333333333333333333"), GaltaJSEnvironment.create().evaluateExpression("1m / 3m"));
JSEnvironment env64 = GaltaJSEnvironment.newBuilder().mathContext(MathContext.DECIMAL64, "java.math.MathContext.DECIMAL64").build();
assertEquals(new BigDecimal("0.3333333333333333"), env64.evaluateExpression("1m / 3m"));
```

Sample: `doc_examples/DecimalExamples.java` (`testPrecisionIsAMathContext`)

## `forceBigDecimalOperations`

Turns every floating-point literal and every non-integer result into a `Decimal`, as if all literals carried the `m` suffix and every `Double`/`Float` operand were converted first. Integers are untouched; a non-exact integer division becomes a `Decimal`. It trades speed for exactness and is meant for calculation-heavy business scripts.

```java
JSEnvironment env = GaltaJSEnvironment.newBuilder().forceBigDecimalOperations(true).build();
assertEquals(new BigDecimal("0.3"), env.evaluateExpression("0.1 + 0.2"));
assertEquals(BigDecimal.class, env.evaluateExpression("1.0").getClass());
assertEquals(Integer.class, env.evaluateExpression("1 + 2").getClass());          // integers are untouched
assertEquals(BigDecimal.class, env.evaluateExpression("1 / 2").getClass());        // non-exact division
```

Sample: `doc_examples/DecimalExamples.java` (`testForceBigDecimalOperations`)

## `Math` with big numbers

With `supportBigNumberMath`, `Math` functions accept `BigInt` and `Decimal` arguments and compute them with `BigDecimalMath` (a vendored copy of Eric Obermühlner's big-math library under `external/ch_obermuhlner_math_big`) at the environment's `MathContext`. `Math` also exposes `Decimal` versions of its constants: `Math.LN10m`, `LN2m`, `LOG10Em`, `LOG2Em`, `PIm`, `SQRT1_2m`, `SQRT2m`. Under `forceBigDecimalOperations`, `Double` and `Float` arguments are converted to `Decimal` first.

```java
JSEnvironment env = GaltaJSEnvironment.create();
assertEquals(new BigDecimal("1.414213562373095048801688724209698"), env.evaluateExpression("Math.sqrt(2m)"));
assertEquals(new BigDecimal("2"), env.evaluateExpression("Math.abs(-2m)"));
assertEquals("decimal", env.evaluateExpression("typeof Math.PIm"));
assertEquals(true, env.evaluateExpression("['LN10m','LN2m','LOG10Em','LOG2Em','PIm','SQRT1_2m','SQRT2m'].every(n => typeof Math[n] === 'decimal')"));
```

Sample: `doc_examples/DecimalExamples.java` (`testMathWithBigNumbers`)

`Math.max(1m, 2)` returns the winning operand as-is (`2`, an `Integer`), the same way `Math.max` returns its argument for doubles.

## What a Decimal can do

`BuiltinBigDecimalPrototype` defines `toString()`, `toLocaleString()`, `valueOf()` and `[Symbol.toPrimitive]`. There is no `toFixed`, `toPrecision` or other `Number.prototype` method on a `Decimal` (`(2m).toFixed(2)` is a `TypeError`); use string formatting, or reach the Java `BigDecimal` API through the `$` escape hatch when `JavaLibrary` is registered (see [Java Interop](/GaltaJS/UserGuide/JavaInterop)). Comparison operators, `===` (value equality between decimals, `Decimal('1.5') === 1.5m` is true), arithmetic, `**`, unary minus and `typeof` all work.

## Gotchas

- `BigDecimal.equals` is scale-sensitive: `6.0m / 2` is `3.0`, `6m / 2` is `3`. In Java use `compareTo` when the scale does not matter.
- Without `supportMixedBigNumber`, `price * 2` on a `Decimal` is a `TypeError`, the same rule JavaScript applies to `BigInt`.
- `Decimal` division is rounded to the `MathContext`; `1m / 3m` is not exact.
- The string form of `mathContext` must name the same context, or transpiled and interpreted code will disagree.

## Source

`rt/builtins/standard/bigdecimal/BuiltinBigDecimalConstructor.java`, `BuiltinBigDecimalPrototype.java`, `BigDecimalAccessor.java`, `rt/builtins/standard/math/MathObject.java`, `external/ch_obermuhlner_math_big/BigDecimalMath.java`, `node/literal/ASTLiteral.java` (`parseDecimal`), `JSEnvironment.java` (`getMathContext`, `findAccessor`), `ConfigurationImpl.java`.
