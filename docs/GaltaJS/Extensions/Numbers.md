# Number Extensions

Standard JavaScript has one number type, the IEEE double, plus `BigInt`. GaltaJS keeps JavaScript's `number` semantics but represents values with the natural Java class: `Integer` when the value fits, `Double` otherwise, and, through extensions, `Long`, `Float`, `BigInteger` (`BigInt`) and `BigDecimal` (`Decimal`). Literal suffixes choose the class explicitly, and promotion flags decide what happens on overflow. No wrapper class is involved: a script's `42` is a `java.lang.Integer` in Java.

## Literal suffixes

| Literal | Java class | `typeof` | Notes |
|---|---|---|---|
| `1` | `Integer` | `number` | Whole values that fit an `int` |
| `1i` / `1I` | `Integer` | `number` | Explicit |
| `1L` / `1l` | `Long` | `number` | |
| `1.5` | `Double` | `number` | |
| `1.0` | `Integer` | `number` | A whole floating literal is narrowed to `Integer` (and to `Long` under `supportLongPromotion` when it does not fit an `int`) |
| `1d` / `1.0D` | `Double` | `number` | Forces a `Double` |
| `1.5f` / `1F` | `Float` | `number` | |
| `1n` / `1N` | `BigInteger` | `bigint` | Standard `BigInt` |
| `1m` / `1M` | `BigDecimal` | `decimal` | Requires `supportBigDecimalLiteral`, see [Decimal](/GaltaJS/Extensions/Decimal) |

Suffixes are case-insensitive and work with every radix (`0xFFl`, `0b101L`). `-0` is a `Double` (`-0.0`) so the sign survives.

Sample: `doc_examples/NumbersExamples.java` (`testLiteralSuffixes`)

```java
JSEnvironment env = GaltaJSEnvironment.create();
assertEquals(Integer.class,    env.evaluateExpression("1").getClass());
assertEquals(Long.class,       env.evaluateExpression("1L").getClass());
assertEquals(Double.class,     env.evaluateExpression("1.5").getClass());
assertEquals(Integer.class,    env.evaluateExpression("1.0").getClass());   // whole values are narrowed
assertEquals(Double.class,     env.evaluateExpression("1d").getClass());    // unless the suffix says otherwise
assertEquals(Float.class,      env.evaluateExpression("1.5f").getClass());
assertEquals(BigInteger.class, env.evaluateExpression("1n").getClass());
assertEquals(BigDecimal.class, env.evaluateExpression("1m").getClass());
assertEquals(255L, env.evaluateExpression("0xFFl"));
assertEquals(List.of("number", "number", "bigint", "decimal"),
		list(env.evaluateExpression("[typeof 1L, typeof 1.5f, typeof 1n, typeof 1m]")));
```

The suffix parsing lives in `node/literal/ASTLiteral.parseInteger()` and `parseDecimal()`; the tokens are `INTEGER_SUFFIX` (`i l n`) and `DECIMAL_SUFFIX` (`f d m`) in the grammar.

## Standard overflow

With no extension enabled, GaltaJS follows the specification: integer results that no longer fit an `int` become doubles, and precision is lost beyond 2^53.

```java
JSEnvironment env = JavaScriptEnvironment.create();
assertEquals(2147483648.0, env.evaluateExpression("2147483647 + 1"));
assertEquals(1708494009298794800.0, env.evaluateExpression("1492553851 * 1144678303"));   // mathematically wrong
```

Sample: `doc_examples/NumbersExamples.java` (`testStandardJavaScriptOverflow`)

A `Long` operand always yields a `Long`, even without any flag: `1492553851L * 1144678303` is the exact `1708494009298794853L` in a plain environment.

## `supportLongPromotion`

On in `GaltaJSEnvironment`. An `int` operation that overflows produces a `Long` instead of a `Double`, so integer arithmetic stays exact up to 64 bits.

```java
JSEnvironment env = GaltaJSEnvironment.create();
assertEquals(2147483648L, env.evaluateExpression("2147483647 + 1"));
assertEquals(1708494009298794853L, env.evaluateExpression("1492553851 * 1144678303"));   // exact
// Results that fit an int stay ints; non-exact divisions are doubles
assertEquals(Integer.class, env.evaluateExpression("(2147483647 - 3) / 4").getClass());
assertEquals(Double.class, env.evaluateExpression("2147483647 / 3").getClass());
```

Sample: `doc_examples/NumbersExamples.java` (`testLongPromotion`)

## `supportBigIntPromotion`

Off even in `GaltaJSEnvironment`. When on, a `Long` operation that overflows produces an exact `BigInt` (`BigInteger`); otherwise the result is a `Double`.

```java
JSEnvironment env = GaltaJSEnvironment.create();
assertEquals(9.223372036854776E18, env.evaluateExpression("9223372036854775807L + 1"));

JSEnvironment promoting = GaltaJSEnvironment.newBuilder().supportBigIntPromotion(true).build();
assertEquals(new BigInteger("9223372036854775808"), promoting.evaluateExpression("9223372036854775807L + 1"));
assertEquals("bigint", promoting.evaluateExpression("typeof (9223372036854775807L + 1)"));
```

Sample: `doc_examples/NumbersExamples.java` (`testBigIntPromotion`)

## `supportMixedBigNumber`

On in `GaltaJSEnvironment`. Standard JavaScript throws `TypeError: Cannot mix BigInt and other types`; with the flag, `BigInt`, `Decimal` and regular numbers combine freely and the result takes the "bigger" type.

```java
JSEnvironment env = GaltaJSEnvironment.create();
assertEquals(new BigInteger("3"), env.evaluateExpression("6 / 2n"));
assertEquals(new BigInteger("36"), env.evaluateExpression("6 ** 2n"));
assertEquals(new BigDecimal("3.0"), env.evaluateExpression("6.0m / 2"));
assertEquals(new BigDecimal("3.3"), env.evaluateExpression("1.1m + 2.2m"));

JSEnvironment js = JavaScriptEnvironment.create();
assertEquals("TypeError", js.evaluateScript("let r; try { 6 / 2n } catch(e) { r = e.name } r"));
```

Sample: `doc_examples/NumbersExamples.java` (`testMixingBigNumbers`)

`BigDecimal` results keep their scale (`6.0m / 2` is `3.0`, not `3`), which matters for `equals()` in Java; compare with `compareTo` when the scale is irrelevant.

## `supportBigDecimalPromotion`

Off by default and not set by `enableGaltaJSExtensions()`. It affects JSON parsing (`GaltaJsJsonFactory.overflowDecimal()`): a JSON number with more digits than a `double` can hold is parsed as a `Decimal` instead of a rounded `Double`. Useful when reading financial data.

```java
JSEnvironment env = GaltaJSEnvironment.newBuilder().supportBigDecimalPromotion(true).build();
Object v = env.evaluateExpression("JSON.parse('{\"v\": 1234567890.12345678901234567890}').v");
assertEquals(new BigDecimal("1234567890.12345678901234567890"), v);

Object plain = GaltaJSEnvironment.create().evaluateExpression("JSON.parse('{\"v\": 1234567890.12345678901234567890}').v");
assertEquals(1.2345678901234567E9, plain);
```

Sample: `doc_examples/NumbersExamples.java` (`testBigDecimalPromotionWhenParsingJson`)

Until this documentation round the builder method `supportBigDecimalPromotion(boolean)` wrote the `supportBigIntPromotion` field by mistake; it now sets the right flag.

## Gotchas

- `typeof 1L` is `number`: `Long` and `Float` are numbers, not new types; only `bigint` and `decimal` are distinct.
- `1.0` is an `Integer`, so `(1.0).$getClass()` is `java.lang.Integer`; use `1d` when a `Double` is required by a Java overload.
- Array `length` is a `Long`, string `length` is an `Integer`, `Date` getters return `Double` (see [Values](/GaltaJS/UserGuide/Values)).
- `supportParseIntOctal` exists but is not read anywhere at run time.
- JUnit's `assertEquals(int, Object)` is ambiguous with the generic `evaluateExpression`; cast the result to `Object` in tests.

## Source

`node/literal/ASTLiteral.java` (`parseInteger`, `parseDecimal`, `astMinus`), `parser/JSParser.jj` (`INTEGER_SUFFIX`, `DECIMAL_SUFFIX`), `rt/RuntimeUtil.java` (arithmetic and promotion), `jsonfactory/GaltaJsJsonFactory.java` (`overflowInteger`, `overflowDecimal`, `useLongIntegers`), `ConfigurationImpl.java`.
