# Java Types and Dates

Because GaltaJS represents JavaScript values with plain Java objects, Java values flow into scripts without conversion: a `java.util.Date` is a JavaScript `Date`, a `Long` is a number, a `BigInteger` is a `BigInt`, a `BigDecimal` is a `Decimal`, a `java.util.List` behaves like an array and a `java.util.Map` like a `Map`. The reverse is also true: what a script returns is directly usable from Java. This page lists the mappings and their edge cases.

## Mapping table

| Java value | In JavaScript | `typeof` | Notes |
|---|---|---|---|
| `Integer`, `Long`, `Short`, `Byte`, `Float`, `Double` | number | `number` | Class is preserved through arithmetic where possible (`Long + 1` is a `Long`) |
| `java.math.BigInteger` | `BigInt` | `bigint` | |
| `java.math.BigDecimal` | `Decimal` | `decimal` | Arithmetic with regular numbers needs `supportMixedBigNumber` |
| `String`, any `CharSequence` | string | `string` | |
| `Boolean` | boolean | `boolean` | |
| `java.util.Date` | `Date` | `object` | Same instance |
| `java.util.List` | array-like | `object` | `length`, indexes, `push`, `map`... but `Array.isArray()` is false |
| `java.util.Map` | `Map` | `object` | `get`, `set`, `has`, `size`, iteration |
| `java.util.Set` | `Set` | `object` | |
| `JSObject`, `JSArray` | object, array | `object` | The engine's own objects, also `JsonObject`/`JsonArray` |
| `null` | `null` | `object` | |
| other classes | opaque, or a Java object with `JavaLibrary` | `object` | see [Java Interop](/GaltaJS/UserGuide/JavaInterop) and [Accessors](/GaltaJS/Extending/Accessors) |

And in the other direction, JavaScript `Map` and `Set` instances are `java.util.Map` and `java.util.Set` implementations (`BuiltinMap`, `BuiltinSet`), arrays are `java.util.List` implementations, and `Date` values are `java.util.Date`.

## Dates

`Date` values are `java.util.Date` instances in both directions. The engine implements the ECMAScript date algorithms (proleptic Gregorian calendar, year zero, ISO formatting) itself, on top of the millisecond timestamp the Java object carries, so the results match the specification even for dates far from the epoch.

Sample: `doc_examples/JavaTypesExamples.java` (`testDatesAreJavaDates`)

```java
JSEnvironment env = JavaScriptEnvironment.create();
Object d = env.evaluateExpression("new Date(0)");
assertTrue(d instanceof Date);
assertEquals(0L, ((Date)d).getTime());

JSGlobalContext ctx = new InterpretedGlobalRuntimeContext(env, env.createProgramExecutor());
ctx.getGlobalThis().setOwnProperty("when", new Date(86_400_000L));
assertEquals("1970-01-02T00:00:00.000Z", env.createScript("when.toISOString()", "d.js").executeWithContext(ctx));
assertEquals(true, env.createScript("when instanceof Date", "d.js").executeWithContext(ctx));
// Year zero is handled the ECMAScript way
assertEquals("0000-01-01T00:00:00.000Z", env.evaluateExpression("new Date(-62167219200000).toISOString()"));
```

`Date` getters (`getTime()`, `getUTCFullYear()`...) return JavaScript numbers as `Double` values, per the specification. When exchanging dates between systems, serialize them as ISO strings rather than as raw millisecond integers; the string form is unambiguous across calendars and time zones.

## Integers, longs and big integers

Sample: `doc_examples/JavaTypesExamples.java` (`testIntegersLongsAndBigIntegers`)

```java
StaticLibrary globals = new StaticLibrary();
globals.addStaticGlobal("big", 1L << 40);
globals.addStaticGlobal("huge", new BigInteger("123456789012345678901234567890"));
JSEnvironment env = GaltaJSEnvironment.newBuilder().registerLibrary(globals).build();

assertEquals(Long.class, env.evaluateExpression("big + 1").getClass());
assertEquals("number", env.evaluateExpression("typeof big"));
assertEquals("bigint", env.evaluateExpression("typeof huge"));           // BigInteger is BigInt
assertEquals(new BigInteger("123456789012345678901234567891"), env.evaluateExpression("huge + 1n"));
```

A `Long` coming from Java stays a `Long` through arithmetic even without `supportLongPromotion`; the flag only decides what an `int` overflow produces (see [Numbers](/GaltaJS/Extensions/Numbers)).

## BigDecimal

Sample: `doc_examples/JavaTypesExamples.java` (`testBigDecimalFromJava`)

```java
StaticLibrary globals = new StaticLibrary();
globals.addStaticGlobal("price", new BigDecimal("19.99"));

JSEnvironment galta = GaltaJSEnvironment.newBuilder().registerLibrary(globals).build();
assertEquals("decimal", galta.evaluateExpression("typeof price"));
assertEquals(new BigDecimal("39.98"), galta.evaluateExpression("price * 2"));

// Without supportMixedBigNumber, mixing a Decimal with a number is a TypeError (like BigInt)
JSEnvironment js = JavaScriptEnvironment.newBuilder().registerLibrary(globals).build();
assertEquals("decimal", js.evaluateExpression("typeof price"));
assertEquals("TypeError", js.evaluateScript("let r; try { price * 2 } catch(e) { r = e.name } r"));
```

See [Decimal](/GaltaJS/Extensions/Decimal) for the flags.

## Collections

Sample: `doc_examples/JavaTypesExamples.java` (`testJavaCollectionsBehaveLikeBuiltIns`, `testJavaScriptMapsAndSetsAreJavaCollections`)

```java
StaticLibrary globals = new StaticLibrary();
globals.addStaticGlobal("names", List.of("ada", "linus"));
globals.addStaticGlobal("scores", Map.of("ada", 10));
globals.addStaticGlobal("tags", Set.of("x"));
JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(globals).build();

assertEquals(2L, env.evaluateExpression("names.length"));
assertEquals("ADA,LINUS", env.evaluateExpression("names.map(n => n.toUpperCase()).join(',')"));
assertEquals(false, env.evaluateExpression("Array.isArray(names)"));   // it is a List, not a JS Array
assertEquals(10, env.evaluateExpression("scores.get('ada')"));
assertEquals(true, env.evaluateExpression("scores instanceof Map && scores.has('ada')"));
assertEquals(true, env.evaluateExpression("tags instanceof Set && tags.has('x')"));
```

```java
JSEnvironment env = JavaScriptEnvironment.create();
Object map = env.evaluateExpression("new Map([['k', 1]])");
assertTrue(map instanceof Map);
assertEquals(1, ((Map<?,?>)map).get("k"));
Object set = env.evaluateExpression("new Set([1, 2])");
assertTrue(set instanceof Set);
assertEquals(2, ((Set<?>)set).size());
```

A Java `List` receives the array methods (`push`, `map`, `filter`, iteration with `for...of`, `length`), but it is not an `Array`: `Array.isArray` is false and `instanceof Array` too. Immutable lists (`List.of`) throw from Java when a script tries to `push` into them. Java `Map` keys are compared with the JavaScript `Map` rules (SameValueZero) and, with `supportMixedBigNumber`, `1`, `1n` and `1m` are the same key.

## Gotchas

- Array `length` is a `Long`, string `length` an `Integer`, `Date` getters return `Double`.
- `Array.isArray(javaList)` is false; use `javaList.length !== undefined` or `Symbol.iterator` checks when both must be accepted.
- Mixing a Java `BigDecimal` with numbers requires `supportMixedBigNumber` (on in `GaltaJSEnvironment`).
- A Java object of any other class is opaque unless `JavaLibrary` or a custom accessor is registered.

## Source

`JSEnvironment.java` (`findAccessor`), `rt/builtins/standard/date/DateAccessor.java`, `DateUtil.java`, `rt/builtins/standard/map/BuiltinMap.java`, `rt/builtins/standard/set/BuiltinSet.java`, `rt/builtins/primitives/array/JavaListAccessor.java`, `rt/builtins/standard/bigdecimal/BigDecimalAccessor.java`, `rt/builtins/primitives/number/*`.
