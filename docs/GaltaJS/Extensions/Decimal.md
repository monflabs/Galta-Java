# Decimal

GaltaJS implements a `Decimal` type following the TC39 Decimal proposal. A decimal is an `org.monflabs.util.number.Decimal128` (IEEE 754 decimal128: at most 34 significant digits, `NaN`, `+Infinity`, `-Infinity` and `-0` are values, results round half-even to 34 digits), or, with the GaltaJS extensions (`GaltaJSEnvironment`), a `java.math.BigDecimal`. Decimals are written with an `m` suffix (`0.1m`), report `typeof` as `decimal` and, unlike `double`, give exact results for base-10 arithmetic: `0.1m + 0.2m` is `0.3`. See [Decimal128 vs BigDecimal](/Decimal128-Design) for the reasoning.

## `Decimal128` and `BigDecimal`: the `useBigDecimal` flag

The builder's `useBigDecimal(boolean)` chooses the Java type of the decimals the engine creates:

| `useBigDecimal` | Decimal type | When |
|---|---|---|
| `false` | `org.monflabs.util.number.Decimal128` | The default (`JSEnvironment.newBuilder()`, `JavaScriptEnvironment`) |
| `true` | `java.math.BigDecimal` | Set by `enableGaltaJSExtensions()`, so the default of a `GaltaJSEnvironment` |

Call `useBigDecimal(...)` after `enableGaltaJSExtensions()` to override it. The flag covers the `m` literals, the results of `forceBigDecimalOperations`, the numbers the JSON factory parses as decimals and the `Math` constants (`Math.PIm`...). It only selects the type the engine creates: both types are always supported as values.

```java
JSEnvironment galta = GaltaJSEnvironment.create();                                  // the GaltaJS extensions: useBigDecimal(true)
assertEquals(new BigDecimal("1.5"), galta.evaluateExpression("1.5m"));
JSEnvironment d128 = GaltaJSEnvironment.newBuilder().useBigDecimal(false).build();
assertEquals(Decimal128.of("1.5"), d128.evaluateExpression("1.5m"));
JSEnvironment js = JavaScriptEnvironment.newBuilder().supportBigDecimalLiteral(true).supportBigDecimal(true).build();
assertEquals(Decimal128.of("1.5"), js.evaluateExpression("1.5m"));                    // no extensions: Decimal128
JSEnvironment big = JavaScriptEnvironment.newBuilder().supportBigDecimalLiteral(true).supportBigDecimal(true).useBigDecimal(true).build();
assertEquals(new BigDecimal("1.5"), big.evaluateExpression("1.5m"));
```

Sample: `doc_examples/DecimalExamples.java` (`testChoosingTheDecimalType`)

### How the two types behave

These behaviors belong to the type of the value, not to the flag: a `Decimal128` created with the `Decimal128()` function behaves as one in a `BigDecimal` environment, and the other way around.

| | `Decimal128` | `BigDecimal` |
|---|---|---|
| Precision | 34 significant digits, rounded half-even | The environment's `MathContext` (`DECIMAL128`, 34 digits, by default) |
| Exponent range | Overflow beyond `9.99...e6144`, underflow below `1e-6176` | The range of the `MathContext` precision: 7, 16 or 34 digits as `DECIMAL32`/`DECIMAL64`/`DECIMAL128`, unbounded for other precisions |
| `NaN`, infinities, `-0` | `Decimal128` values (`typeof` is `decimal`) | The matching Numbers (`typeof` is `number`) |
| Scale | None: `String(1.50m)` is `1.5`, `6.0m / 2` is `3` | Kept: `String(1.50m)` is `1.50`, `6.0m / 2` is `3.0`. Equality ignores it (`1.50m === 1.5m`) |
| The global `Decimal` | The `Decimal128` function | The `BigDecimal` function |
| Prototype methods | The proposal's: `add()`, `round()`, `toFixed()`... (listed below) | `toString()`, `toLocaleString()`, `valueOf()` and `[Symbol.toPrimitive]` only |
| Mixed with a Number (`supportMixedBigNumber`) | A `Decimal128` (`1m + 0.1`), with `NaN`, infinities and `-0` as `Decimal128` values | A `BigDecimal`, or a Number for a special result |
| A Number `NaN`/`Infinity`/`-0` operand without `supportMixedBigNumber` | A `TypeError`, like any Number | Accepted: it is how a `BigDecimal` result carries these values |
| `Math.max(1m, 2)` | `2m`, a `Decimal128` | `2`, the winning operand as it is (an `Integer`) |

```java
JSEnvironment big = GaltaJSEnvironment.create();
JSEnvironment d128 = GaltaJSEnvironment.newBuilder().useBigDecimal(false).build();
// Scale: a BigDecimal keeps it, a Decimal128 has none; equality ignores it in both cases
assertEquals("1.50", big.evaluateExpression("String(1.50m)"));
assertEquals("1.5", d128.evaluateExpression("String(1.50m)"));
assertEquals(true, big.evaluateExpression("1.50m === 1.5m"));
// The global Decimal is the function of the selected type
assertEquals(false, big.evaluateExpression("Decimal === Decimal128"));
assertEquals(true, d128.evaluateExpression("Decimal === Decimal128"));
// The proposal's prototype methods are on Decimal128 only
assertEquals("2.00", d128.evaluateExpression("(2m).toFixed(2)"));
assertEquals("TypeError", big.evaluateScript("let r; try { (2m).toFixed(2) } catch(e) { r = e.name } r"));
// Mixed with a Number, the result has the type of the decimal operand
assertEquals(new BigDecimal("1.1"), big.evaluateExpression("1m + 0.1"));
assertEquals(Decimal128.of("1.1"), d128.evaluateExpression("1m + 0.1"));
```

Sample: `doc_examples/DecimalExamples.java` (`testComparingTheTypes`)

### Creating a value of a specific type

Whatever the flag, a script can create either type:

- The global `Decimal128(value)` is always registered and returns a `Decimal128`; `Decimal(value)` returns the selected type.
- From Java, through `Java.type()` (`JavaLibrary`): `Decimal128` has no public constructor, only factory methods (`Decimal128.of(...)`). `new BigDecimal(...)` calls the Java constructor and, since `new` returns an object, gives a boxed decimal like `new Number(1)`: its `typeof` is `object` and it is not `===` to a decimal. Arithmetic unboxes it, and `valueOf()` returns the plain decimal.
- A `BigDecimal` or a `Decimal128` passed from Java keeps its type.

```java
JSEnvironment env = GaltaJSEnvironment.create();
// The Decimal128 function is always registered
assertEquals(Decimal128.of("0.1"), env.evaluateExpression("Decimal128('0.1')"));
// From Java: Decimal128 has factory methods, BigDecimal a constructor
String types = "const D128 = Java.type('org.monflabs.util.number.Decimal128'), BigDec = Java.type('java.math.BigDecimal');";
assertEquals(Decimal128.of("0.1"), env.evaluateScript(types + "D128.of('0.1')"));
assertEquals("object", env.evaluateScript(types + "typeof new BigDec('0.1')"));        // new boxes it
assertEquals("decimal", env.evaluateScript(types + "typeof new BigDec('0.1').valueOf()"));
assertEquals(new BigDecimal("0.3"), env.evaluateScript(types + "new BigDec('0.1') + 0.2m")); // arithmetic unboxes it
```

Sample: `doc_examples/DecimalExamples.java` (`testCreatingASpecificType`). The playground example *GaltaJS Extensions / Data Types / Decimal Types* compares the two types side by side.

### Mixing `Decimal128` and `BigDecimal`

When a `BigDecimal` and a `Decimal128` meet in an operation, the result is a `BigDecimal`, whatever the order of the operands and the `useBigDecimal` flag:

- The `Decimal128` operand is converted to a `BigDecimal`, exactly, and the operation runs as a `BigDecimal` one: the result is rounded to the environment's `MathContext` and follows its exponent range. With the default `DECIMAL128` context this matches the `Decimal128` precision; with `DECIMAL64` a mixed result has 16 digits.
- A `Decimal128` `NaN`, infinity or `-0` becomes the matching Number, so the result is a Number, as for any `BigDecimal` special result.
- Comparisons are by value: `1.5m === Decimal128('1.5')` is true.

```java
JSEnvironment env = GaltaJSEnvironment.create();
// A BigDecimal and a Decimal128 give a BigDecimal, whatever the order and the useBigDecimal flag
assertEquals(new BigDecimal("0.3"), env.evaluateExpression("0.1m + Decimal128('0.2')"));
assertEquals(new BigDecimal("0.3"), env.evaluateExpression("Decimal128('0.1') + 0.2m"));
assertEquals(true, env.evaluateExpression("1.5m === Decimal128('1.5')"));
// The Decimal128 special values become Numbers
assertEquals(Double.POSITIVE_INFINITY, env.evaluateExpression("Decimal128('Infinity') + 1m"));
assertEquals(Double.NaN, env.evaluateExpression("Decimal128('NaN') + 1m"));
assertEquals(-0.0, env.evaluateExpression("Decimal128('-0') * 1m"));
// The precision is the MathContext's
JSEnvironment env64 = GaltaJSEnvironment.newBuilder().mathContext(MathContext.DECIMAL64, "java.math.MathContext.DECIMAL64").build();
assertEquals(new BigDecimal("0.3333333333333333"), env64.evaluateExpression("1m / Decimal128('3')"));
```

Sample: `doc_examples/DecimalExamples.java` (`testMixingDecimalTypes`)

### `Decimal128` values

A `Decimal128` has no scale: `6.0m / 2` and `6m / 2` are both `3`. Division by zero is `Infinity` / `NaN` (no exception), `-0m` is a real negative zero (`Object.is(-0m, 0m)` is false, `-0m === 0m` is true), `Decimal128('NaN')` is not `===` to itself. As Map and Set keys, `NaN` equals `NaN` and `-0m` equals `0m`.

The prototype has, on top of `toString()`, `toLocaleString()`, `toJSON()`, `valueOf()` and `[Symbol.toPrimitive]`: `isNaN()`, `isFinite()`, `abs()`, `negate()`, `add()`, `subtract()`, `multiply()`, `divide()`, `remainder()`, `compare()`, `equals()`, `notEquals()`, `lessThan()`, `lessThanOrEqual()`, `greaterThan()`, `greaterThanOrEqual()`, `round(digits, mode)`, `scale10()`, `exponent()`, `mantissa()`, `toFixed()`, `toExponential()` and `toPrecision()`. `Decimal128(value)` converts a string, a number or a BigInt (a `SyntaxError` for a bad string); it cannot be called with `new`.

## Literals and the `Decimal` function

Sample: `doc_examples/DecimalExamples.java` (`testDecimalLiterals`, `testDecimalFunction`)

```java
// With the GaltaJS extensions, decimals are BigDecimal
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
| `useBigDecimal` | The decimals the engine creates are `BigDecimal` instead of `Decimal128` (see above) |
| `mathContext(ctx, expr)` | The precision and range of `BigDecimal` decimals (see below) |

`enableGaltaJSExtensions()` sets all but `forceBigDecimalOperations` and `mathContext`, including `useBigDecimal(true)`. On a plain environment enable the first two together; its decimals are `Decimal128`:

```java
JSEnvironment env = JavaScriptEnvironment.newBuilder().supportBigDecimalLiteral(true).supportBigDecimal(true).build();
assertEquals(Decimal128.of("2.5"), env.evaluateExpression("1m + 1.5m"));
```

Sample: `doc_examples/DecimalExamples.java` (`testDecimalLiteralsRequireTheExtension`)

## Precision: `MathContext`

A `Decimal128` always has 34 digits; the `MathContext` below only applies to `BigDecimal` decimals (the GaltaJS extensions, or `useBigDecimal(true)`). Division and transcendental functions need a precision. The builder's `mathContext(MathContext ctx, String transpilerExpression)` sets it; the default is `MathContext.DECIMAL128` (34 significant digits). The second argument is the Java expression the transpiler emits for the same context (for example `"java.math.MathContext.DECIMAL64"`), because transpiled code carries its configuration as generated Java source.

```java
// BigDecimal decimals: DECIMAL128 (34 digits) by default
assertEquals(new BigDecimal("0.3333333333333333333333333333333333"), GaltaJSEnvironment.create().evaluateExpression("1m / 3m"));
JSEnvironment env64 = GaltaJSEnvironment.newBuilder().mathContext(MathContext.DECIMAL64, "java.math.MathContext.DECIMAL64").build();
assertEquals(new BigDecimal("0.3333333333333333"), env64.evaluateExpression("1m / 3m"));
// A Decimal128 always has 34 digits
JSEnvironment d128 = GaltaJSEnvironment.newBuilder().useBigDecimal(false).mathContext(MathContext.DECIMAL64, "java.math.MathContext.DECIMAL64").build();
assertEquals(Decimal128.of("0.3333333333333333333333333333333333"), d128.evaluateExpression("1m / 3m"));
```

Sample: `doc_examples/DecimalExamples.java` (`testPrecisionIsAMathContext`)

## Special values: NaN, infinities and -0

Decimal arithmetic and `Math` follow IEEE 754 decimal128, as in the TC39 proposal, with both decimal types. A `Decimal128` carries its own `NaN`, `Infinity`, `-Infinity` and `-0`. A `BigDecimal` has none, so these values are the matching Numbers. Either way the results are the same values:

```java
// Decimal128 (the default without the GaltaJS extensions) carries its own NaN, infinities and -0
JSEnvironment env = JavaScriptEnvironment.newBuilder().supportBigDecimalLiteral(true).supportBigDecimal(true).supportMixedBigNumber(true).supportBigNumberMath(true).build();
assertEquals(Decimal128.POSITIVE_INFINITY, env.evaluateExpression("1m / 0m"));
assertEquals(Decimal128.NaN, env.evaluateExpression("0m / 0m"));
assertEquals(Decimal128.NEGATIVE_ZERO, env.evaluateExpression("-1m * 0m"));
assertEquals(Decimal128.POSITIVE_INFINITY, env.evaluateExpression("9e6144m * 10m"));  // overflow
assertEquals(Decimal128.NEGATIVE_ZERO, env.evaluateExpression("Math.ceil(-0.5m)"));
assertEquals(Decimal128.NEGATIVE_INFINITY, env.evaluateExpression("Math.log(0m)"));

// A BigDecimal (the default with the GaltaJS extensions) has none: the same results are the matching Numbers
JSEnvironment big = GaltaJSEnvironment.create();
assertEquals(Double.POSITIVE_INFINITY, big.evaluateExpression("1m / 0m"));
assertEquals(Double.NaN, big.evaluateExpression("0m / 0m"));
assertEquals(-0.0, big.evaluateExpression("-1m * 0m"));
assertEquals(Double.POSITIVE_INFINITY, big.evaluateExpression("9e6144m * 10m"));  // overflow
assertEquals(-0.0, big.evaluateExpression("Math.ceil(-0.5m)"));
assertEquals(Double.NEGATIVE_INFINITY, big.evaluateExpression("Math.log(0m)"));
assertEquals(new BigDecimal("1"), big.evaluateExpression("-0 + 1m"));    // a Number special value is a Decimal operand
```

Sample: `doc_examples/DecimalExamples.java` (`testSpecialValues`)

- Division by zero gives a signed infinity; `0m / 0m` and `Infinity - Infinity` give NaN. `%` keeps the sign of the dividend and `x % 0m` is NaN, like Number.
- A zero result carries the IEEE sign: `-1m * 0m` and `-0m + -0m` are `-0`, `5m - 5m` is `+0`; `-0m === 0m` is true and `Object.is(-0m, 0m)` false. A Number `-0` operand stays `-0`; only the `Decimal()` function turns the Number `-0` into `+0`, as the proposal's constructor does (the string `'-0'` gives `-0`).
- A result beyond the exponent range overflows to a signed infinity, and one below the smallest subnormal (`1e-6176`) underflows to a signed zero. For a `BigDecimal` the range is the one of the `MathContext` precision: 7, 16 or 34 digits (`DECIMAL32`, `DECIMAL64`, `DECIMAL128`); other precisions are unbounded.
- `Math` returns the IEEE result outside of a function's domain (`Math.sqrt(-1m)` is NaN, `Math.log(0m)` is `-Infinity`, `Math.atanh(1m)` is `Infinity`), and handles special arguments as for Numbers (`Math.exp(-Infinity)` is `+0`, `Math.atan2(-0m, 1m)` is `-0`). `Math.floor`, `ceil`, `round` and `trunc` return a decimal, and `-0` for a negative value that rounds to zero; the proposal's `Decimal128.prototype.round()` returns `+0` there.
- With `BigDecimal` decimals, a special result is a Number: `typeof (1m / 0m)` is `number`, and `-(-0m)` is the Number `0`. The Number special values combine with `BigDecimal` decimals even without `supportMixedBigNumber`. With `Decimal128` every result is a `Decimal128`, and a Number operand needs `supportMixedBigNumber` as usual.

The expectations are shared by both engines in `tests/galtajs/builtin/decimal/DecimalSpecialValues.js` (every operator and `Math` function).

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

With `supportBigNumberMath`, `Math` functions accept `BigInt` and `Decimal` arguments and compute them with `BigDecimalMath` (a vendored copy of Eric Obermühlner's big-math library under `external/ch_obermuhlner_math_big`): at the environment's `MathContext` for a `BigDecimal`, rounded to 34 digits for a `Decimal128`. `Math.max` and `min` order `-0m` before `0m` and propagate `NaN`. `Math` also exposes `Decimal128` (or `BigDecimal`) versions of its constants: `Math.LN10m`, `LN2m`, `LOG10Em`, `LOG2Em`, `PIm`, `SQRT1_2m`, `SQRT2m`. Under `forceBigDecimalOperations`, `Double` and `Float` arguments are converted to `Decimal` first.

```java
JSEnvironment env = GaltaJSEnvironment.create();
assertEquals(new BigDecimal("1.414213562373095048801688724209698"), env.evaluateExpression("Math.sqrt(2m)"));
assertEquals(new BigDecimal("2"), env.evaluateExpression("Math.abs(-2m)"));
assertEquals("decimal", env.evaluateExpression("typeof Math.PIm"));
assertEquals(true, env.evaluateExpression("['LN10m','LN2m','LOG10Em','LOG2Em','PIm','SQRT1_2m','SQRT2m'].every(n => typeof Math[n] === 'decimal')"));
```

Sample: `doc_examples/DecimalExamples.java` (`testMathWithBigNumbers`)

With `BigDecimal` decimals, `Math.max(1m, 2)` returns the winning operand as it is (`2`, an `Integer`), the same way `Math.max` returns its argument for doubles. With `Decimal128` decimals it returns `2m`, a `Decimal128`.

## What a Decimal can do

A `BigDecimal` decimal only has `toString()`, `toLocaleString()`, `valueOf()` and `[Symbol.toPrimitive]`; a `Decimal128` has the methods listed above. There is no `toFixed`, `toPrecision` or other `Number.prototype` method on a `BigDecimal` decimal (`(2m).toFixed(2)` is a `TypeError`); use string formatting, or reach the Java `BigDecimal` API through the `$` escape hatch when `JavaLibrary` is registered (see [Java Interop](/GaltaJS/UserGuide/JavaInterop)). Comparison operators, `===` (value equality between decimals, `Decimal('1.5') === 1.5m` is true), arithmetic, `**`, unary minus and `typeof` all work.

## Gotchas

- A `BigDecimal` keeps its scale: `6.0m / 2` is `3.0` and `6m / 2` is `3` (equal for `===`, but different strings and different for Java's `BigDecimal.equals`). A `Decimal128` has no scale.
- A value passed from Java keeps its type, whatever `useBigDecimal` says. Use the `Decimal128(...)` function or `Decimal128.of` to convert a `BigDecimal` (rounded to 34 digits). Mixing the two types gives a `BigDecimal` (see above).
- Without `supportMixedBigNumber`, `price * 2` on a `Decimal` is a `TypeError`, the same rule JavaScript applies to `BigInt`.
- `new` on a Java number class gives a boxed value: `new (Java.type('java.math.BigDecimal'))('1')` is an `object`; use `valueOf()` to get the decimal.
- Division is rounded, to the `MathContext` for a `BigDecimal` and to 34 digits for a `Decimal128`: `1m / 3m` is not exact.
- The string form of `mathContext` must name the same context, or transpiled and interpreted code will disagree.

## Source

`rt/builtins/standard/decimal128/` (`BuiltinDecimal128Constructor`, `BuiltinDecimal128Prototype`, `Decimal128Accessor`), `rt/util/Decimal128Ops.java`, `rt/builtins/standard/bigdecimal/BuiltinBigDecimalConstructor.java`, `BuiltinBigDecimalPrototype.java`, `BigDecimalAccessor.java`, `rt/builtins/standard/math/MathObject.java`, `external/ch_obermuhlner_math_big/BigDecimalMath.java`, `node/literal/ASTLiteral.java` (`parseDecimal`), `JSEnvironment.java` (`getMathContext`, `findAccessor`), `ConfigurationImpl.java`.
