# Numbers, Types & Versions

Four small classes in `org.monflabs.util` deal with numbers and simple values: `DtoA` turns a `double` into text, `TypeUtil` converts between `Number` types, `Version` parses and orders version strings, and `EnumUtil`/`ArrayUtil` cover two recurring one-liners.

## DtoA: double to string

`DtoA.toStandard(double)` produces exactly what JavaScript's `Number.prototype.toString()` produces: the shortest digits that read back as the same value (the Ryu algorithm), laid out by the ECMA-262 rules. JSON output (`JsonUtil.toString(Number)` and the stringifier) uses it, so numbers are written the way `JSON.stringify` writes them.

| Value | Output | `Double.toString` |
|---|---|---|
| Integral, below 1e21 in magnitude | plain digits, no fraction: `1`, `1200000000000000000`; beyond 2^53 the shortest digits padded with zeros: `4611686018427388000` for 2^62 | `1.0`, `1.2E18`, `4.611686018427388E18` |
| Other values from 1e-6 to below 1e21 | plain decimal: `0.0001`, `12300000.5` | `1.0E-4`, `1.23000005E7` |
| Below 1e-6 or from 1e21 | exponent with an explicit sign, no `.0`: `1e-7`, `1e+21`, `1.5e+300` | `1.0E-7`, `1.0E21` |
| `-0.0` and `0.0` | `0` | `-0.0`, `0.0` |
| Infinities and NaN | `Infinity`, `-Infinity`, `NaN` | the same |

JavaScript has no `float`: the `float` overload uses the shortest digits that read back as the same *float* (`0.1f` gives `0.1`), with the same layout. `DtoA.toJavaLiteral(double)`/`(float)` keep the Java-style form (`1.0e21`, `1.0e-4`, all digits of integral values below 2^63), used where the text must be a valid Java literal, such as the GaltaJS transpiler's generated code.

Sample: `doc_examples/util/NumbersAndTypesExamples.java` (`testDtoA`)

```java
assertEquals("1", DtoA.toStandard(1.0));                 // integral values have no ".0"
assertEquals("0.30000000000000004", DtoA.toStandard(0.1 + 0.2));  // shortest round-trip digits
assertEquals("0", DtoA.toStandard(-0.0));
assertEquals("Infinity", DtoA.toStandard(Double.POSITIVE_INFINITY));
assertEquals("NaN", DtoA.toStandard(Double.NaN));
assertEquals("1200000000000000000", DtoA.toStandard(1.2e18));   // below 1e21: plain digits
assertEquals("4611686018427388000", DtoA.toStandard(0x1p62));   // shortest digits, padded with zeros
assertEquals("1e+21", DtoA.toStandard(1e21));                   // from 1e21: exponent with a sign
assertEquals("0.0001", DtoA.toStandard(0.0001));                // plain decimal down to 1e-6
assertEquals("1e-7", DtoA.toStandard(1e-7));
assertEquals("12300000.5", DtoA.toStandard(12300000.5));
assertEquals("1.5e-7", DtoA.toStandard(1.5e-7f));               // float overload: shortest float digits
assertEquals("1.0e21", DtoA.toJavaLiteral(1e21));               // Java-style, for generated Java source
```

## TypeUtil: number conversions

`TypeUtil` converts any `Number` to a given numeric type. `toByte`, `toShort`, `toInt`, `toLong`, `toFloat` and `toDouble` are plain Java narrowing (`Number.xxxValue()`): `NaN` becomes 0, an infinity saturates for `int`/`long`, and a value out of range for `byte`/`short` wraps. `toBigDecimal` and `toBigInteger` add rules of their own:

| Source | `toBigDecimal` | `toBigInteger` |
|---|---|---|
| `NaN`, `+Infinity`, `-Infinity` | throws `ArithmeticException` | throws `ArithmeticException` |
| `Double` / `Float` | from the shortest decimal form (`Double.toString`/`Float.toString`), so `0.1` and `0.1f` stay `0.1` | truncated toward zero |
| `BigDecimal` | itself | `toBigInteger()` (truncated) |
| `Integer`, `Long`, `Short`, `Byte` | exact | exact |
| Other `Number` classes (for example Gson's `LazilyParsedNumber`) | parsed from `toString()` | parsed from `toString()`, truncated; `longValue()` if the text is not a number |

Sample: `doc_examples/util/NumbersAndTypesExamples.java` (`testTypeUtil`)

```java
// Primitive targets follow Java's narrowing rules
assertEquals(0, TypeUtil.toInt(Double.NaN));
assertEquals(Integer.MAX_VALUE, TypeUtil.toInt(Double.POSITIVE_INFINITY));
assertEquals(44, TypeUtil.toByte(300));                          // wraps

// Big targets have no value for NaN and the infinities
assertThrows(ArithmeticException.class, () -> TypeUtil.toBigDecimal(Double.NaN));
assertThrows(ArithmeticException.class, () -> TypeUtil.toBigInteger(Double.NEGATIVE_INFINITY));

assertEquals(new BigDecimal("0.1"), TypeUtil.toBigDecimal(0.1));   // via Double.toString, not the binary value
assertEquals(BigInteger.valueOf(1900), TypeUtil.toBigInteger(1.9e3));
assertEquals(BigInteger.ONE, TypeUtil.toBigInteger(new BigDecimal("1.99")));  // truncates
```

## Version

`Version` holds up to three numbers and an optional qualifier: `major.minor.subversion-qualifier`. `Version.parse` accepts one to three numeric components (missing ones are 0) and a qualifier after the first `-`, whatever the number of components before it. A null or empty string returns `Version.EMPTY` (`0.0.0`); anything else that does not parse, such as a fourth component or a `v` prefix, throws `IllegalArgumentException`. `toString()` always writes the three numbers.

Sample: `doc_examples/util/NumbersAndTypesExamples.java` (`testVersionParse`)

```java
Version v = Version.parse("2.1-beta");
assertEquals(2, v.getMajor());
assertEquals(1, v.getMinor());
assertEquals(0, v.getSubversion());                   // missing parts are 0
assertEquals("beta", v.getQualifier());
assertEquals("2.1.0-beta", v.toString());

assertEquals(Version.EMPTY, Version.parse(""));        // empty or null
assertEquals(Version.parse("1.0.0"), Version.parse("1.0.0-"));   // an empty qualifier is no qualifier
assertThrows(IllegalArgumentException.class, () -> Version.parse("1.2.3.4"));
assertThrows(IllegalArgumentException.class, () -> Version.parse("v1"));
```

### Ordering

`Version` implements `Comparable`. Versions compare by major, minor and subversion, then by qualifier:

- a version without a qualifier (a release) is greater than the same numbers with a qualifier (a pre-release): `1.0.0-SNAPSHOT < 1.0.0`;
- two qualifiers compare as plain strings, case-sensitively and without any numeric awareness, so `rc10` sorts before `rc2` and `SNAPSHOT` before `alpha`;
- an empty qualifier (`1.0.0-`) is the same as none, for `equals`, `hashCode` and `compareTo`.

`lessThan`, `greaterThan`, `lessOrEqualThan` and `greaterOrEqualThan` use this order. `isAtLeast(...)` does not: it compares the three numbers only, so a snapshot of a version is "at least" that version.

Sample: `doc_examples/util/NumbersAndTypesExamples.java` (`testVersionOrdering`)

```java
List<Version> list = new ArrayList<>();
for (String s : new String[] {"1.10", "1.2", "1.0.0", "1.0.0-SNAPSHOT", "1.0.0-alpha", "1.0.0-rc10", "1.0.0-rc2"}) {
    list.add(Version.parse(s));
}
Collections.sort(list);
assertEquals("[1.0.0-SNAPSHOT, 1.0.0-alpha, 1.0.0-rc10, 1.0.0-rc2, 1.0.0, 1.2.0, 1.10.0]", list.toString());

Version snapshot = Version.parse("1.0.0-SNAPSHOT");
assertTrue(snapshot.lessThan(Version.parse("1.0.0")));  // pre-release < release
assertTrue(snapshot.isAtLeast(1, 0, 0));                 // isAtLeast ignores the qualifier
```

## EnumUtil and ArrayUtil

`EnumUtil.names(enumClass)` returns the constant names in declaration order. `ArrayUtil.contains(array, value)` compares with `equals()`, finds a `null` element when `value` is `null`, and returns `false` for a `null` array.

Sample: `doc_examples/util/NumbersAndTypesExamples.java` (`testEnumAndArray`)

```java
assertArrayEquals(new String[] {"NANOSECONDS", "MICROSECONDS", "MILLISECONDS", "SECONDS", "MINUTES", "HOURS", "DAYS"},
        EnumUtil.names(TimeUnit.class));
assertTrue(ArrayUtil.contains(new Object[] {"a", null, 3}, null));
assertTrue(ArrayUtil.contains(new Object[] {"a", null, 3}, 3));     // equals(), not ==
assertFalse(ArrayUtil.contains(null, "a"));
```
