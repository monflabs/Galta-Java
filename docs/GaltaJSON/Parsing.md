# Parsing & Stringifying

`JsonFactory` turns JSON text into [values](/GaltaJSON/Values) and back. The parser is lenient by default (comments, unquoted keys, trailing commas, JSON5-like numbers...), with a strict mode for standard JSON only. This page covers the parse entry points, the accepted syntax, how numbers are mapped to Java types, errors, stringifying and its options, and how to choose or customize the factory.

## Parsing

`JsonFactory.get()` returns the default factory. Its `parse` methods accept a `String`, a `Reader` or an `InputStream` (UTF-8 unless a `Charset` is given) and return an `Object`: any JSON value can be at the top level. `JsonObject.parse(...)`, `JsonArray.parse(...)` and `JsonContainer.parse(...)` are shortcuts that cast the result.

Sample: `doc_examples/json/ParsingExamples.java` (`testParseSources`)

```java
JsonFactory factory = JsonFactory.get();

Object fromString = factory.parse("{\"name\":\"Ada\"}");
Object fromReader;
try(Reader r = new StringReader("[1,2,3]")) {
    fromReader = factory.parse(r);
}
Object fromStream;
try(InputStream is = new ByteArrayInputStream("\"Zoë\"".getBytes(StandardCharsets.UTF_8))) {
    fromStream = factory.parse(is);                         // UTF-8 unless a Charset is passed
}
// fromString is a JsonObject, fromReader a JsonArray, fromStream "Zoë"

// Any JSON value can be at the top level
factory.parse("42");       // 42
factory.parse("null");     // null

// The typed shortcuts check the type of the result
JsonObject o = JsonObject.parse("{}");
JsonArray a = JsonArray.parse("[]");
JsonObject.parse("[]");    // throws JsonException: "The JSON text is not an object but array"
JsonObject.parse("null");  // null
```

## Lenient syntax

On top of standard JSON, the parser accepts:

| Extension | Example |
|---|---|
| Line and block comments, anywhere whitespace is allowed | `// ...`, `/* ... */` |
| Unquoted keys (Java identifier characters, plus `@`) | `{name: 1, $id: 2}` |
| Single-quoted strings and keys | `{'a': 'b'}` |
| Trailing commas in objects and arrays | `[1, 2, ]` |
| Hexadecimal integers | `0x1F` |
| A leading `+`, a leading or trailing `.`, leading zeros | `+5`, `.5`, `5.`, `007` |
| `NaN`, `Infinity`, `+Infinity`, `-Infinity` | `[NaN, -Infinity]` |
| `\'` and `\xHH` escapes, raw control characters inside strings | `"\x41"` |

An unquoted key spelled `null` becomes the `null` key.

Sample: `doc_examples/json/ParsingExamples.java` (`testLenientSyntax`)

```java
JsonObject o = JsonObject.parse("""
    {
      // line comment
      /* block comment */
      unquoted: 1,
      $dollar_key: 2,
      'single': 'quoted',
      "hex": 0x1F,
      "plus": +5,
      "dot": .5,
      "trailingDot": 5.,
      "leadingZeros": 007,
      "nan": NaN,
      "inf": -Infinity,
      "escape": "\\x41",
      "trailing": [1, 2, ],
    }
    """);
o.get("unquoted");        // 1
o.get("$dollar_key");     // 2
o.get("single");          // "quoted"
o.get("hex");             // 31
o.get("plus");            // 5
o.get("dot");             // 0.5
o.get("trailingDot");     // 5.0
o.get("leadingZeros");    // 7
o.getDouble("nan");       // NaN
o.get("inf");             // -Infinity
o.get("escape");          // "A"
o.getArray("trailing");   // [1,2]

// Raw control characters are kept in strings
JsonFactory.get().parse("\"a\nb\"");   // "a\nb"

JsonObject.parse("{@type: 1}").get("@type");          // 1
JsonObject.parse("{null: 1}").containsKey("null");    // true: an unquoted name, never a null key
JsonFactory.get().parse("+Infinity");                 // Infinity
JsonFactory.get().parse("\"it\\'s\"");                // "it's"
```

### Strict mode

Strict mode rejects every extension above. It is a property of the parser, not of the factory: create a `JsonParser.StringParser` (or `JsonParser.ReaderParser` for a `Reader`) on a factory and call `setStrict(true)`. A parser instance is not thread-safe.

Sample: `doc_examples/json/ParsingExamples.java` (`testStrictMode`)

```java
JsonParser.StringParser parser = new JsonParser.StringParser(JsonFactory.get());
parser.setStrict(true);

parser.parse("[1, 2]");            // [1,2]
parser.parse("[1, 2, ]");          // throws ParseException
parser.parse("{a: 1}");            // throws ParseException
parser.parse("['a']");             // throws ParseException
parser.parse("[1] // comment");    // throws ParseException
parser.parse("[NaN]");             // throws ParseException
```

### Revivers

A `JsonParser.Reviver` sees every parsed value before it is stored, with its container, its key (the index as a string in an array, `""` for the top-level value) and, for a primitive, its exact source text. It returns the value to store, or `Reviver.IGNORE` to drop it. Parsing from a `String` is the only way to get the exact source text.

Sample: `doc_examples/json/ParsingExamples.java` (`testReviver`)

```java
JsonParser.StringParser parser = new JsonParser.StringParser(JsonFactory.get());
parser.setReviver((container, key, value, source) -> {
    if(key.startsWith("_")) {
        return JsonParser.Reviver.IGNORE;          // drop the property
    }
    if(value instanceof Double) {
        return new BigDecimal(source);             // source: the raw text of a primitive
    }
    return value;
});
JsonObject o = (JsonObject)parser.parse("{\"_id\":7,\"price\":0.1,\"qty\":3}");
// {"price":0.1,"qty":3}, with price a BigDecimal
```

## Numbers

A number literal becomes the smallest fitting type:

| Literal | Java type (default factory) |
|---|---|
| Integer in the `int` range (also hexadecimal) | `Integer` |
| Integer in the `long` range | `Long` |
| Larger integer | `BigInteger` |
| Decimal or exponent that a `double` holds exactly | `Double` |
| Decimal or exponent that a `double` would round, overflow to `Infinity` or underflow to `0` | `BigDecimal` |
| `-0` | `Double` (`-0.0`) |
| `NaN`, `Infinity` | `Double` |

The decision depends on the value, not on the length of the literal: `3.141592653589793` (a double's shortest representation) is a `Double`, `3.14159265358979323846`, `1e400` and `1e-400` are `BigDecimal`s. Code that needs a `double` should read numbers with `getDouble()` or `asDouble()`, which accept any `Number`.

Sample: `doc_examples/json/ParsingExamples.java` (`testNumberMapping`)

```java
JsonArray a = JsonArray.parse("""
    [ 42, 3000000000, 123456789012345678901234567890,
      1.5, 1e3, 3.141592653589793, -0, 3.14159265358979323846, 1e400 ]
    """);
a.get(0);    // Integer 42
a.get(1);    // Long 3000000000
a.get(2);    // BigInteger 123456789012345678901234567890
a.get(3);    // Double 1.5
a.get(4);    // Double 1000.0
a.get(5);    // Double 3.141592653589793: a double holds it exactly
a.get(6);    // Double -0.0
a.get(7);    // BigDecimal 3.14159265358979323846: a double would lose digits
a.get(8);    // BigDecimal 1E+400: a double would overflow
```

### Number options

The mapping is controlled by methods of the factory, which a subclass of `JavaJsonFactory` overrides:

| Method | Values (default first) | Effect |
|---|---|---|
| `defaultInteger()` | `INT`, `LONG`, `BIGINT` | The type of an integer that fits |
| `useLongIntegers()` | `true`, `false` | With `INT`, whether an integer outside the `int` range becomes a `Long`; when `false` it goes straight to the overflow type |
| `overflowInteger()` | `BIGINT`, `DOUBLE` | The type of an integer too large for the previous rules |
| `defaultDecimal()` | `DOUBLE`, `BIGDEC` | The type of a decimal number |
| `overflowDecimal()` | `BIGDEC`, `DOUBLE` | The type of a decimal a `double` can't hold exactly; `DOUBLE` makes every decimal a `Double` |

Sample: `doc_examples/json/ParsingExamples.java` (`testNumberOptions`)

```java
JsonFactory exact = new JavaJsonFactory() {
    @Override
    public INTEGER defaultInteger() { return INTEGER.LONG; }
    @Override
    public DECIMAL defaultDecimal() { return DECIMAL.BIGDEC; }
};
JsonArray a = (JsonArray)exact.parse("[42, 1.5]");
// Long 42, BigDecimal 1.5

JsonFactory doubles = new JavaJsonFactory() {
    @Override
    public OVERFLOW_INTEGER overflowInteger() { return OVERFLOW_INTEGER.DOUBLE; }
    @Override
    public OVERFLOW_DECIMAL overflowDecimal() { return OVERFLOW_DECIMAL.DOUBLE; }
};
JsonArray b = (JsonArray)doubles.parse("[123456789012345678901234567890, 3.141592653589793]");
// Double 1.2345678901234568E29, Double 3.141592653589793

JsonFactory noLongs = new JavaJsonFactory() {
    @Override
    public boolean useLongIntegers() { return false; }
};
JsonArray c = (JsonArray)noLongs.parse("[42, 3000000000]");
// Integer 42, BigInteger 3000000000: int overflow goes to overflowInteger()

// The containers still belong to the shared default factory
c.factory();      // JavaJsonFactory.instance
```

Containers created by such a factory are the default ones, so their `factory()` is the shared `JavaJsonFactory.instance`: nested containers created later through `getOrCreateObject()` or `deepClone()` come from it, not from the subclass. The options only affect what the subclass parses.

## Errors

A syntax error raises a `ParseException` (a `JsonException`) with the zero-based character position, an error type (`ERROR_UNEXPECTED_CHAR`, `ERROR_UNEXPECTED_EOF`, `ERROR_UNEXPECTED_STRICT`, ...) and a message that quotes the offending line when the input can be re-read. `parse(String)` throws it directly; `parse(Reader)` and `parse(InputStream)` wrap it in a `JsonException` whose cause is the `ParseException`. An empty string is rejected; blank input (whitespace only) parses as `null`.

Sample: `doc_examples/json/ParsingExamples.java` (`testParseErrors`)

```java
ParseException e = assertThrows(ParseException.class,
        () -> JsonFactory.get().parse("{\"a\":}"));
e.getPosition();      // 5
e.getErrorType();     // ParseException.ERROR_UNEXPECTED_CHAR
e.getMessage();       // "JsonParser: Unexpected character '}' (125) at position 5." + the source line

// Reader and InputStream parsing wrap it in a JsonException
JsonException je = assertThrows(JsonException.class,
        () -> JsonFactory.get().parse(new StringReader("{\"a\":}")));
je.getCause();        // the ParseException

JsonFactory.get().parse("");          // throws JsonException
JsonFactory.get().parse("  \n ");     // null
```

## Stringifying

| Call | Output |
|---|---|
| `container.stringify()` | Compact JSON |
| `container.stringify(false)`, `container.toString()` | Pretty JSON, two-space indentation |
| `JsonFactory.get().stringify(value)` / `stringify(value, compact)` | Any value, compact by default |
| `JsonFactory.get().stringify(writer, value)` / `stringify(writer, value, compact)` | Writes to a `Writer` |
| `JsonFactory.get().stringifySorted(value)` | Compact, object keys sorted |
| `JsonFactory.get().stringifyDebug(value)` | Pretty, object keys sorted |

Sample: `doc_examples/json/ParsingExamples.java` (`testStringify`)

```java
JsonObject o = JsonObject.parse("{\"a\":1,\"b\":[1,2],\"c\":{}}");

o.stringify();          // {"a":1,"b":[1,2],"c":{}}
o.stringify(false);     // the pretty form below
o.toString();           // the same pretty form

JsonFactory f = JsonFactory.get();
f.stringify(o);                                    // {"a":1,"b":[1,2],"c":{}}
f.stringify("text");                               // "text" (with the quotes)
f.stringifySorted(JsonObject.of("b", 1, "a", 2));  // {"a":2,"b":1}

StringWriter w = new StringWriter();
f.stringify(w, o, false);                          // the pretty form, into the writer
```

The pretty form of `o`:

```json
{
  "a": 1,
  "b": [
    1,
    2
  ],
  "c": {}
}
```

A value that is not a JSON value (any other Java object) is written as the JSON string of its `toString()`. Numbers are written the JavaScript way, exactly as `JSON.stringify` writes them: `1.0` becomes `1`, `0.0001` stays `0.0001`, `1e21` becomes `1e+21` and 2^62 becomes `4611686018427388000` (shortest digits, see [DtoA](/Utilities/NumbersAndTypes)). A container that contains itself raises a `JsonException.CircularReference` instead of recursing forever.

Sample: `doc_examples/json/ParsingExamples.java` (`testCircularReference`)

```java
JsonArray a = JsonArray.create();
a.add(a);
a.stringify();          // throws JsonException.CircularReference
```

### Stringifier options

For more control, use a `JsonStringifier` directly: `JsonStringifier.StringSerializer` returns a `String`, `JsonStringifier.WriterSerializer` writes to a `Writer` (`stringify(writer, value)`), and `JsonStringifier.LimitedStringSerializer(max)` stops after `max` characters. Both `stringify` methods declare `IOException`.

| Setter | Default | Effect |
|---|---|---|
| `setCompact(boolean)` | `true` | Compact or indented output |
| `setIndentString(String)` | two spaces | One indentation level in pretty mode |
| `setInitialIndentLevel(int)` | `0` | Starting indentation, to embed the output in indented text |
| `setSortProperties(boolean)` | `false` | Object keys in sorted order |
| `setPropertyList(List<String>)` | none | Only these object keys, in this order, for every object (never for arrays) |
| `setSerializeNulls(boolean)` | `true` | When `false`, object properties holding `null` are skipped (array items are kept) |
| `setReplacer(Replacer)` | none | `(container, key, value) -> newValue`, called for every value, the top-level one with key `""`; return `Replacer.IGNORE` to skip the property or item |
| `setOutputReferences(boolean)` | `false` | Write a container that carries a [JSON reference](/GaltaJSON/Pointers#json-references) as `{"$ref": ...}` |

Sample: `doc_examples/json/ParsingExamples.java` (`testStringifierOptions`)

```java
JsonObject user = JsonObject.parse("{\"name\":\"Ada\",\"password\":\"secret\",\"email\":null,\"age\":36}");

JsonStringifier.StringSerializer s = new JsonStringifier.StringSerializer();
s.setSortProperties(true);
s.setSerializeNulls(false);
s.setReplacer((container, key, value) ->
    "password".equals(key) ? JsonStringifier.Replacer.IGNORE : value);
s.stringify(user);          // {"age":36,"name":"Ada"}

JsonStringifier.StringSerializer pretty = new JsonStringifier.StringSerializer();
pretty.setCompact(false);
pretty.setIndentString("\t");
pretty.stringify(JsonArray.of(1));      // "[\n\t1\n]"

JsonStringifier.StringSerializer ordered = new JsonStringifier.StringSerializer();
ordered.setPropertyList(List.of("age", "name"));       // which properties, in which order
ordered.stringify(user);    // {"age":36,"name":"Ada"}

JsonStringifier.LimitedStringSerializer limited = new JsonStringifier.LimitedStringSerializer(5);
limited.stringify(JsonArray.of(1, 2, 3, 4));   // "[1,2,"
limited.isTruncated();                          // true

JsonStringifier.StringSerializer nested = new JsonStringifier.StringSerializer();
nested.setCompact(false);
nested.setInitialIndentLevel(1);
nested.setSerializeNulls(false);
nested.stringify(JsonArray.of((Object)null));   // "  [\n    null\n  ]": array items are kept

JsonStringifier.StringSerializer root = new JsonStringifier.StringSerializer();
root.setReplacer((container, key, value) -> key.isEmpty() ? "root" : value);
root.stringify(JsonArray.of(1));                // "\"root\"": the top-level value has the key ""
```

### Escaping

Strings are written with `\"`, `\\`, `\b`, `\f`, `\n`, `\r` and `\t`; any other character below 32 or above 127 becomes a `\uXXXX` escape (lowercase hexadecimal), except complete surrogate pairs (emoji and other astral characters), which are written as is. `/` is not escaped. The output is therefore pure ASCII apart from astral characters.

Sample: `doc_examples/json/ParsingExamples.java` (`testEscaping`)

```java
String s = "Line\n\"q\" é / \u0001 😀";
JsonFactory.get().stringify(s);     // "Line\n\"q\" é / \u0001 😀"   (as JSON text)
JsonFactory.get().parse(JsonFactory.get().stringify(s)).equals(s);   // true
```

### NaN and Infinity

The default factory supports them (`supportsNaN()` and `supportsInfinity()` are `true`): they can be stored, and the lenient parser reads the bare words `NaN`, `Infinity` and `-Infinity`. They are not JSON numbers, so the stringifier writes them as `null`, like `JSON.stringify()`: the output is always valid JSON.

Sample: `doc_examples/json/ParsingExamples.java` (`testNaNAndInfinity`)

```java
JsonArray a = JsonArray.of(Double.NaN, Double.POSITIVE_INFINITY, 1.0);
a.stringify();                               // [null,null,1]
a.equals(JsonArray.parse("[NaN,Infinity,1]"));   // true
JsonFactory.get().supportsNaN();             // true
```

## The default factory

`JsonFactory.get()` is resolved on first use: the first `JsonFactoryService` registered with `java.util.ServiceLoader`, or else `JavaJsonFactory.instance`. `JsonFactory.set(factory)` replaces it for the whole JVM (and `JsonFactory.setFactory(service)` installs a `JsonFactoryService` that is asked every time). Every `JsonObject.create()`, `JsonArray.of(...)`, `JsonObject.parse(...)` and similar shortcut uses it.

### Checked and custom factories

`JavaJsonFactoryChecked.instance` creates the same containers, but every way of storing a value rejects one that is not a JSON value (`JsonType.UNKNOWN`) with a `JsonException`: `put`, `add` and `set`, and the `java.util.Map`/`List` methods too (`putAll`, `putIfAbsent`, `replace`, `replaceAll`, `compute*`, `merge`, `Map.Entry.setValue` on the `entrySet()`, `sequencedEntrySet()` and `reversed()` views, `putFirst`/`putLast`, `addAll`, `subList(...)` and `listIterator()` updates). Containers created from a checked one (`getOrCreateObject()`, the lambda forms of `put`/`add`, `deepClone()`) are checked too. With the default, unchecked factory any Java object can be stored and is stringified as a string (a `java.util.Date` as its ISO-8601 instant).

Sample: `doc_examples/json/ParsingExamples.java` (`testCheckedFactory`)

```java
JsonObject lenient = JsonObject.create();
lenient.putValue("text", new StringBuilder("abc"));   // accepted: any Java object...
lenient.stringify();                                  // {"text":"abc"}: ...written as a string

JsonFactory previous = JsonFactory.get();
JsonFactory.set(JavaJsonFactoryChecked.instance);
try {
    JsonObject checked = JsonObject.create();
    checked.put("when", (Object)new Date(0));        // throws JsonException
    checked.put("when", "1970-01-01");               // JSON values are fine
    JsonObject child = checked.getOrCreateObject("child");
    child.putValue("when", new Date(0));             // throws JsonException: children too
    checked.putAll(Map.of("when", new Date(0)));     // throws JsonException: bulk methods too
    checked.getOrCreateArray("list").addAll(List.of(new Date(0)));   // throws JsonException
} finally {
    JsonFactory.set(previous);
}
```

A custom factory usually extends `JavaJsonFactory` (its constructor is protected) to change the [number options](/GaltaJSON/Parsing#number-options), or overrides `configureJsonStringifier(JsonStringifier)` to apply options to every `stringify` call of the factory.

## Gotchas

- `stringify()` is compact and `toString()` is pretty.
- Long decimals parse as `BigDecimal`; read numbers through `getDouble()`, `getBigDecimal()` or `getNumber()` rather than casting.
- `parse(String)` throws `ParseException`, the `Reader` and `InputStream` variants a wrapping `JsonException`: catch `JsonException` to handle both.
- `NaN` and `Infinity` are written as `null`: they don't round trip.
- A JSON object key is never `null`: `put(null, ...)` throws a `NullPointerException`.
- Objects and arrays nest at most `JsonParser.DEFAULT_MAX_DEPTH` (1000) levels deep (`setMaxDepth()`); deeper content is a parse error.

## Source

`JsonFactory.java`, `java/JavaJsonFactory.java`, `java/JavaJsonFactoryChecked.java`, `parser/JsonParser.java`, `parser/ParseException.java`, `stringifier/JsonStringifier.java`, `JsonUtil.java` (`toString(Number)`), under `parent-json/json/src/main/java/org/monflabs/json/`.
