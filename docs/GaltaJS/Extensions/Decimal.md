# Decimal

GaltaJS implements a `Decimal` type following the TC39 Decimal proposal. By default a decimal is an `org.monflabs.util.number.Decimal128` (IEEE 754 decimal128: at most 34 significant digits, `NaN`, `+Infinity`, `-Infinity` and `-0` are values, results round half-even to 34 digits). Decimals are written with an `m` suffix (`0.1m`), report `typeof` as `decimal` and, unlike `double`, give exact results for base-10 arithmetic: `0.1m + 0.2m` is `0.3`. See [Decimal128 vs BigDecimal](/Decimal128-Design) for the reasoning.

## `Decimal128` and `BigDecimal`

The builder's `useDecimal128(boolean)` (default `true`) chooses the Java type of the decimals the engine creates: `m` literals, the results of `forceBigDecimalOperations`, the numbers the JSON factory parses as decimals, and the `Math` constants (`Math.PIm`...). With `useDecimal128(false)` they are `java.math.BigDecimal` (unlimited precision, computed with the environment's `MathContext`, no `NaN`/`Infinity`/`-0`). In both cases the other type is still supported as a value, and a global `Decimal128` is always registered; the global `Decimal` is the `Decimal128` function when the flag is on, and the `BigDecimal` one otherwise. When a `BigDecimal` and a `Decimal128` meet in an operation the result is a `BigDecimal`.

```java
JSEnvironment env = GaltaJSEnvironment.create();
assertEquals(Decimal128.of("1.5"), env.evaluateExpression("1.5m"));
JSEnvironment legacy = GaltaJSEnvironment.newBuilder().useDecimal128(false).build();
assertEquals(Decimal128.of("1.5"), legacy.evaluateExpression("1.5m"));
```

A `Decimal128` has no scale: `6.0m / 2` and `6m / 2` are both `3`. Division by zero is `Infinity` / `NaN` (no exception), `-0m` is a real negative zero (`Object.is(-0m, 0m)` is false, `-0m === 0m` is true), `Decimal128('NaN')` is not `===` to itself. As Map and Set keys, `NaN` equals `NaN` and `-0m` equals `0m`.

The prototype has, on top of `toString()`, `toLocaleString()`, `toJSON()`, `valueOf()` and `[Symbol.toPrimitive]`: `isNaN()`, `isFinite()`, `abs()`, `negate()`, `add()`, `subtract()`, `multiply()`, `divide()`, `remainder()`, `compare()`, `equals()`, `notEquals()`, `lessThan()`, `lessThanOrEqual()`, `greaterThan()`, `greaterThanOrEqual()`, `round(digits, mode)`, `scale10()`, `exponent()`, `mantissa()`, `toFixed()`, `toExponential()` and `toPrecision()`. `Decimal128(value)` converts a string, a number or a BigInt (a `SyntaxError` for a bad string); it cannot be called with `new`.

## Literals and the `Decimal` function

Sample: `doc_examples/DecimalExamples.java` (`testDecimalLiterals`, `testDecimalFunction`)

```java
JSEnvironment env = GaltaJSEnvironment.create();
Object v = env.evaluateExpression("123456.789m");
assertEquals(Decimal128.class, v.getClass());
assertEquals(Decimal128.of("123456.789"), v);
assertEquals("decimal", env.evaluateExpression("typeof 1m"));
// Exact arithmetic where doubles are not
assertEquals(0.30000000000000004, env.evaluateExpression("0.1 + 0.2"));
assertEquals(Decimal128.of("0.3"), env.evaluateExpression("0.1m + 0.2m"));
```

The global `Decimal` converts strings and numbers (`Decimal('123456789123456789.25689')`); it is callable but not a constructor.

```java
assertEquals(Decimal128.of("123456789123456789.25689"), env.evaluateExpression("Decimal('123456789123456789.25689')"));
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
assertEquals(Decimal128.of("2.5"), env.evaluateExpression("1m + 1.5m"));
```

Sample: `doc_examples/DecimalExamples.java` (`testDecimalLiteralsRequireTheExtension`)

## Precision: `MathContext`

A `Decimal128` always has 34 digits; the `MathContext` below only applies to `BigDecimal` decimals (`useDecimal128(false)`). Division and transcendental functions need a precision. The builder's `mathContext(MathContext ctx, String transpilerExpression)` sets it; the default is `MathContext.DECIMAL128` (34 significant digits). The second argument is the Java expression the transpiler emits for the same context (for example `"java.math.MathContext.DECIMAL64"`), because transpiled code carries its configuration as generated Java source.

```java
// A Decimal128 always has 34 digits
assertEquals(Decimal128.of("0.3333333333333333333333333333333333"), GaltaJSEnvironment.create().evaluateExpression("1m / 3m"));
JSEnvironment env64 = GaltaJSEnvironment.newBuilder().useDecimal128(false).mathContext(MathContext.DECIMAL64, "java.math.MathContext.DECIMAL64").build();
assertEquals(new BigDecimal("0.3333333333333333"), env64.evaluateExpression("1m / 3m"));
```

Sample: `doc_examples/DecimalExamples.java` (`testPrecisionIsAMathContext`)

## `forceBigDecimalOperations`

Turns every floating-point literal and every non-integer result into a `Decimal`, as if all literals carried the `m` suffix and every `Double`/`Float` operand were converted first. Integers are untouched; a non-exact integer division becomes a `Decimal`. It trades speed for exactness and is meant for calculation-heavy business scripts.

```java
JSEnvironment env = GaltaJSEnvironment.newBuilder().forceBigDecimalOperations(true).build();
assertEquals(Decimal128.of("0.3"), env.evaluateExpression("0.1 + 0.2"));
assertEquals(Decimal128.class, env.evaluateExpression("1.0").getClass());
assertEquals(Integer.class, env.evaluateExpression("1 + 2").getClass());          // integers are untouched
assertEquals(Decimal128.class, env.evaluateExpression("1 / 2").getClass());        // non-exact division
```

Sample: `doc_examples/DecimalExamples.java` (`testForceBigDecimalOperations`)

## `Math` with big numbers

With `supportBigNumberMath`, `Math` functions accept `BigInt` and `Decimal` arguments and compute them with `BigDecimalMath` (a `Decimal128` result is rounded to 34 digits; `Math.max`/`min` order `-0m` before `0m` and propagate `NaN`) (a vendored copy of Eric Obermühlner's big-math library under `external/ch_obermuhlner_math_big`) at the environment's `MathContext`. `Math` also exposes `Decimal128` (or `BigDecimal`) versions of its constants: `Math.LN10m`, `LN2m`, `LOG10Em`, `LOG2Em`, `PIm`, `SQRT1_2m`, `SQRT2m`. Under `forceBigDecimalOperations`, `Double` and `Float` arguments are converted to `Decimal` first.

```java
JSEnvironment env = GaltaJSEnvironment.create();
assertEquals(Decimal128.of("1.414213562373095048801688724209698"), env.evaluateExpression("Math.sqrt(2m)"));
assertEquals(Decimal128.of("2"), env.evaluateExpression("Math.abs(-2m)"));
assertEquals("decimal", env.evaluateExpression("typeof Math.PIm"));
assertEquals(true, env.evaluateExpression("['LN10m','LN2m','LOG10Em','LOG2Em','PIm','SQRT1_2m','SQRT2m'].every(n => typeof Math[n] === 'decimal')"));
```

Sample: `doc_examples/DecimalExamples.java` (`testMathWithBigNumbers`)

`Math.max(1m, 2)` returns the winning operand as-is (`2`, an `Integer`), the same way `Math.max` returns its argument for doubles.

## What a Decimal can do

A `BigDecimal` decimal (`useDecimal128(false)`) only has `toString()`, `toLocaleString()`, `valueOf()` and `[Symbol.toPrimitive]`; a `Decimal128` has the methods listed above. There is no `toFixed`, `toPrecision` or other `Number.prototype` method on a `Decimal` (`(2m).toFixed(2)` is a `TypeError`); use string formatting, or reach the Java `BigDecimal` API through the `$` escape hatch when `JavaLibrary` is registered (see [Java Interop](/GaltaJS/UserGuide/JavaInterop)). Comparison operators, `===` (value equality between decimals, `Decimal('1.5') === 1.5m` is true), arithmetic, `**`, unary minus and `typeof` all work.

## Gotchas

- `BigDecimal.equals` is scale-sensitive: with `useDecimal128(false)`, `6.0m / 2` is `3.0` and `6m / 2` is `3`. A `Decimal128` has no scale.
- A `BigDecimal` passed from Java is not converted: it stays a `BigDecimal`. Use the `Decimal128(...)` function or `Decimal128.of` to convert it (rounded to 34 digits).
- Without `supportMixedBigNumber`, `price * 2` on a `Decimal` is a `TypeError`, the same rule JavaScript applies to `BigInt`.
- `Decimal` division is rounded to the `MathContext`; `1m / 3m` is not exact.
- The string form of `mathContext` must name the same context, or transpiled and interpreted code will disagree.

## Source

`rt/builtins/standard/decimal128/` (`BuiltinDecimal128Constructor`, `BuiltinDecimal128Prototype`, `Decimal128Accessor`), `rt/util/Decimal128Ops.java`, `rt/builtins/standard/bigdecimal/BuiltinBigDecimalConstructor.java`, `BuiltinBigDecimalPrototype.java`, `BigDecimalAccessor.java`, `rt/builtins/standard/math/MathObject.java`, `external/ch_obermuhlner_math_big/BigDecimalMath.java`, `node/literal/ASTLiteral.java` (`parseDecimal`), `JSEnvironment.java` (`getMathContext`, `findAccessor`), `ConfigurationImpl.java`.
