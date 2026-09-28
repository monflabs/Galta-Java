# Values & Containers

GaltaJSON has no value classes of its own: a JSON value is a plain Java value, and the two containers are ordinary `java.util.Map` and `java.util.List` implementations with extra helpers. This page covers the value model, creating and filling objects and arrays, the three families of getters, dates, equality and ordering, and cloning.

## The value model

| JSON | Java | `JsonType` |
|---|---|---|
| string | `String` | `STRING` |
| `true` / `false` | `Boolean` | `BOOLEAN` |
| number | any `Number`: `Integer`, `Long`, `Double`, `BigInteger`, `BigDecimal`, ... | `NUMBER` |
| `null` | `null` | `NULL` |
| object | `JsonObject` (a `Map<String,Object>`) | `OBJECT` |
| array | `JsonArray` (a `List<Object>`) | `ARRAY` |
| anything else | any other Java object | `UNKNOWN` |

`JsonType.typeOf(value)` classifies a value. Which `Number` subclass the parser produces is described in [Parsing](/GaltaJSON/Parsing#numbers).

Sample: `doc_examples/json/ValuesExamples.java` (`testValueModel`)

```java
JsonObject o = JsonObject.parse("""
    { "s": "text", "b": true, "i": 42, "l": 12345678901, "d": 1.5,
      "n": null, "o": {}, "a": [] }
    """);
o.get("s").getClass();   // String
o.get("b").getClass();   // Boolean
o.get("i").getClass();   // Integer
o.get("l").getClass();   // Long
o.get("d").getClass();   // Double
o.get("n");              // null
o.get("o");              // a JsonObject
o.get("a");              // a JsonArray

JsonType.typeOf(o.get("i"));              // NUMBER
JsonType.typeOf(o.get("n"));              // NULL
JsonType.typeOf(new java.util.Date());    // UNKNOWN
```

### The default implementations

`JsonObject` and `JsonArray` are interfaces. The default factory implements them with `JsonObjectAsLinkedMap`, a `LinkedHashMap` (keys keep their insertion order), and `JsonArrayAsArrayList`, an `ArrayList`. Every `Map` and `List` method works as usual.

Sample: `doc_examples/json/ValuesExamples.java` (`testDefaultImplementations`)

```java
JsonObject o = JsonObject.create();      // a JsonObjectAsLinkedMap, i.e. a LinkedHashMap
JsonArray a = JsonArray.create();        // a JsonArrayAsArrayList, i.e. an ArrayList

// They are plain Java collections
Map<String,Object> map = o;
List<Object> list = a;
map.put("z", 1);
map.put("a", 2);
list.add("x");
o.keySet();     // [z, a]: insertion order is kept
a.size();       // 1
```

A container accepts any Java object; only the [checked factory](/GaltaJSON/Parsing#checked-and-custom-factories) restricts it to JSON values. Other factories exist (the GaltaJS engine has its own, where a JavaScript object *is* a `JsonObject`), so code that creates containers should go through `JsonObject.create()` / `JsonArray.create()` or `factory().createObject()` rather than `new`.

## Creating objects and arrays

| Method | Result |
|---|---|
| `JsonObject.create()`, `JsonArray.create()` | An empty container from the default factory |
| `JsonObject.of(key, value, key, value, ...)` | An object from key/value pairs |
| `JsonArray.of(values...)` | An array of the values |
| `JsonObject.parse(json)`, `JsonArray.parse(json)` | Parse a `String` or a `Reader` (see [Parsing](/GaltaJSON/Parsing)) |

Sample: `doc_examples/json/ValuesExamples.java` (`testCreating`)

```java
JsonObject empty = JsonObject.create();
JsonObject person = JsonObject.of("name", "Ada", "born", 1815);
JsonArray numbers = JsonArray.of(1, 2, 3);
JsonObject parsed = JsonObject.parse("{\"name\":\"Ada\",\"born\":1815}");

person.stringify();          // {"name":"Ada","born":1815}
numbers.stringify();         // [1,2,3]
person.equals(parsed);       // true
```

### Fluent put and add

The typed overloads of `put` (object) and `add` (array) return the container, so calls chain. `putNull()` / `addNull()` store a `null`. Passing a lambda creates a nested container and hands it to the lambda; declare the parameter type (`JsonObject` or `JsonArray`) to pick which one.

Sample: `doc_examples/json/ValuesExamples.java` (`testFluentPut`)

```java
JsonObject o = JsonObject.create()
    .put("name", "Ada")
    .put("born", 1815)
    .put("active", true)
    .putNull("died")
    .put("langs", (JsonArray a) -> a.add("en").add("fr"))
    .put("address", (JsonObject a) -> a.put("city", "London"));
// {"name":"Ada","born":1815,"active":true,"died":null,"langs":["en","fr"],"address":{"city":"London"}}

JsonArray a = JsonArray.create()
    .add(1)
    .add("two")
    .addNull()
    .add((JsonObject item) -> item.put("id", 4));
// [1,"two",null,{"id":4}]
```

Arrays also have the index forms `add(index, value)` (insert) and `set(index, value)` with the same overloads, and `addAllValues(iterable)`.

`put(String, Object)` is the `Map` method: with a value whose static type is `Object` it returns the previous value, not the container. `putValue(key, value)` (and `addValue(value)` on arrays) is the fluent form for untyped values.

Sample: `doc_examples/json/ValuesExamples.java` (`testPutObjectOverload`)

```java
JsonObject o = JsonObject.create();
Object value = "x";
// Map.put(String,Object) returns the previous value, like any Map
Object previous = o.put("k", value);        // null
// putValue() is the fluent form for an untyped value
o.putValue("k", value).putValue("k2", 2);
```

## Reading values

There are three families of accessors, from strictest to most forgiving:

| Family | Wrong type | `null` value | Missing key / index |
|---|---|---|---|
| `getInt(key)`, `getString(key)`, ... | throws `JsonException` | throws | throws |
| `getInt(key, default)`, ... | throws `JsonException` | the default | the default |
| `asInt(key)`, `asString(key)`, ... | converts, or the default | the default | the default |

The same methods exist on `JsonArray` with an `int` index, and on [`JsonValues`](/GaltaJSON/JsonPath#navigating-with-jsonvalues).

### Typed getters

`getBoolean`, `getByte`, `getShort`, `getInt`, `getLong`, `getFloat`, `getDouble`, `getBigInteger`, `getBigDecimal`, `getNumber`, `getString`, `getObject`, `getArray`, the boxed `getIntObject`-style variants, and the date getters below. A numeric getter accepts any `Number` and converts it the Java way (`Number.intValue()`, so `9.99` reads as `9`), but never parses a string.

Sample: `doc_examples/json/ValuesExamples.java` (`testTypedGetters`)

```java
JsonObject o = JsonObject.parse("{\"count\":42,\"price\":9.99,\"name\":\"Ada\",\"ok\":true,\"nothing\":null}");

o.getInt("count");            // 42
o.getLong("count");           // 42L
o.getDouble("count");         // 42.0
o.getInt("price");            // 9: Number.intValue(), truncated
o.getBigDecimal("price");     // 9.99
o.getString("name");          // "Ada"
o.getBoolean("ok");           // true
o.getIntObject("count");      // Integer 42

// A wrong type, null or a missing key throws a JsonException
o.getInt("name");
o.getString("count");
o.getInt("nothing");
o.getInt("missing");
```

### Getters with a default

The default covers only absence: a missing key, an out-of-range index or a `null` value. A value of the wrong type still throws.

Sample: `doc_examples/json/ValuesExamples.java` (`testGettersWithDefault`)

```java
JsonObject o = JsonObject.parse("{\"count\":42,\"name\":\"Ada\",\"nothing\":null}");

o.getInt("count", -1);         // 42
o.getInt("missing", -1);       // -1: missing key
o.getInt("nothing", -1);       // -1: null value
o.getString("nothing", "?");   // "?"
o.getInt("name", -1);          // throws JsonException: wrong type
```

### The `as*` conversions

`asNumber`, `asInt`, `asLong`, `asDouble`, `asBigInteger`, `asBigDecimal`, `asBoolean` and `asString` never throw. Without an explicit default they return `0` (`null` for `asNumber`), `false` or `""`. The rules, exactly:

| Target | From a number | From a string | From a boolean | Anything else (`null`, missing, object, array) |
|---|---|---|---|---|
| `asInt`, `asLong` | `intValue()` / `longValue()` | `Integer.parseInt` / `Long.parseLong`, else the default | `1` / `0` | the default |
| `asDouble` | `doubleValue()` | `Double.parseDouble`, else the default | `1` / `0` | the default |
| `asBigInteger`, `asBigDecimal` | converted | `new BigInteger(s)` / `new BigDecimal(s)`, else the default | `1` / `0` | the default |
| `asNumber` | the number itself | parsed as a JSON number (`"12.7"` gives `12.7`), else the default | `1` / `0` | the default |
| `asBoolean` | `false` for `0` and `NaN`, else `true` | `false` for `""`, `"0"` and `"false"` (any case), else `true` | itself | the default |
| `asString` | the JSON text of the number | itself | `"true"` / `"false"` | the default |

Note that `asInt("12.7")` is the default, not `12`: the string must be an integer literal. Containers are never converted, not even by `asString`.

Sample: `doc_examples/json/ValuesExamples.java` (`testAsConversions`)

```java
JsonObject o = JsonObject.parse("""
    { "int": 42, "dec": 12.7, "sint": "42", "sdec": "12.7", "text": "abc",
      "t": true, "f": false, "szero": "0", "sfalse": "FALSE", "empty": "",
      "nothing": null, "obj": {} }
    """);

// Numbers from numbers, numeric strings and booleans
o.asInt("int");            // 42
o.asInt("dec");            // 12: truncated
o.asInt("sint");           // 42
o.asInt("sdec");           // 12: parsed as a number, then truncated
o.asDouble("sdec");        // 12.7
o.asNumber("sdec");        // 12.7: parsed as a JSON number
o.asInt("t");              // 1
o.asInt("f");              // 0
o.asInt("text");           // 0
o.asInt("text", -1);       // -1
o.asInt("nothing");        // 0
o.asInt("missing");        // 0
o.asInt("obj");            // 0
o.asNumber("text");        // null

// Booleans: 0, NaN, "", "0" and "false" (any case) are false
o.asBoolean("int");            // true
o.asBoolean("text");           // true
o.asBoolean("szero");          // false
o.asBoolean("sfalse");         // false
o.asBoolean("empty");          // false
o.asBoolean("nothing");        // false
o.asBoolean("missing", true);  // true
o.asBoolean("obj", true);      // true: not a primitive, so the default

// Strings
o.asString("int");             // "42"
o.asString("dec");             // "12.7"
o.asString("t");               // "true"
o.asString("nothing");         // ""
o.asString("obj");             // "": containers are not converted
o.asString("missing", "n/a");  // "n/a"

o.asBigDecimal("t");           // 1
o.asBigInteger("sint");        // 42
o.asBigDecimal("text");        // 0
JsonUtil.asInt("42");          // 42
```

The same conversions are available on any value through `JsonUtil.asInt(value)`, `JsonUtil.asBoolean(value)`, and so on (shown at the end of the sample).

## Dates and times

`java.time` values are stored as ISO-8601 strings. The `put`/`add`/`set` overloads accept `LocalDate`, `LocalTime`, `LocalDateTime`, `OffsetTime`, `OffsetDateTime` and `ZonedDateTime`, and the matching getters (`getLocalDate`, `getOffsetDateTime`, ...) parse the string back with the corresponding `DateTimeFormatter.ISO_*` formatter. A string in another format throws a `JsonException`.

Sample: `doc_examples/json/ValuesExamples.java` (`testDateTime`)

```java
JsonObject o = JsonObject.create()
    .put("day", LocalDate.of(2026, 9, 26))
    .put("at", OffsetDateTime.of(2026, 9, 26, 14, 30, 0, 0, ZoneOffset.ofHours(2)));

// Stored as ISO-8601 strings
o.get("day");                     // "2026-09-26"
o.get("at");                      // "2026-09-26T14:30:00+02:00"

o.getLocalDate("day");                                  // 2026-09-26
o.getOffsetDateTime("at").getHour();                    // 14
o.getLocalDate("missing", LocalDate.of(2000, 1, 1));    // 2000-01-01
o.getLocalDate("at");             // throws JsonException: not an ISO local date
```

## Get or create

`getOrCreateObject(key)` and `getOrCreateArray(key)` return the container stored under the key, creating and storing an empty one first when the key is absent. The optional consumer initializes a container only when it is created.

Sample: `doc_examples/json/ValuesExamples.java` (`testGetOrCreate`)

```java
JsonObject config = JsonObject.create();
config.getOrCreateObject("server").put("port", 8080);
config.getOrCreateObject("server").put("host", "localhost");   // the same object
config.getOrCreateArray("users").add("ada");
// {"server":{"port":8080,"host":"localhost"},"users":["ada"]}

// The consumer only runs when the object is created
config.getOrCreateObject("limits", (JsonObject l) -> l.put("max", 10));
config.getOrCreateObject("limits", (JsonObject l) -> l.put("max", 99));
config.getObject("limits").getInt("max");                       // 10
```

## Array indexes

A negative index counts from the end in the JSON accessors: the typed getters (`getString(-1)`...), `set(index, value)`/`setValue`, `add(index, value)`/`addValue`, `has`, `getOrDefault` and `jsonValues(index)`. The `java.util.List` methods (`get(int)`, `set(int, Object)`, `add(int, Object)`, `remove(int)`) keep the `List` contract and throw `IndexOutOfBoundsException` for a negative index; `actualIndex(i)` converts one. `has(index)` tells whether an index is in range; the getters with a default return it for an out-of-range index. `firstValue()` and `lastValue()` throw on an empty array; `firstValueOrDefault()` / `lastValueOrDefault()` do not.

Sample: `doc_examples/json/ValuesExamples.java` (`testArrayIndexes`)

```java
JsonArray a = JsonArray.of("a", "b", "c", "d");

a.getString(-1);    // "d"
a.get(-1);          // throws IndexOutOfBoundsException: the List method
a.getString(-2);    // "c"
a.has(-4);          // true
a.has(4);           // false
a.has(-5);          // false

a.set(-1, "D");
a.remove(a.actualIndex(-2));   // removes "c"
a.add(-1, "x");     // inserts before the last item
// ["a","b","x","D"]

a.getString(10, "z");   // "z": out of range, so the default
a.firstValue();         // "a"
a.lastValue();          // "D"
JsonArray.create().firstValueOrDefault("none");   // "none"
JsonArray.create().firstValue();                  // throws JsonException
```

## Presence and null

`has(key)` is `containsKey`: a key holding `null` is present. `isNull(key)` is true both for a `null` value and a missing key; `isString(key)`, `isNumber(key)`, `isObject(key)`, ... test the type.

Sample: `doc_examples/json/ValuesExamples.java` (`testHas`)

```java
JsonObject o = JsonObject.parse("{\"a\":null}");
o.has("a");        // true: present, even with a null value
o.has("b");        // false
o.isNull("a");     // true
o.isNull("b");     // true: a missing key reads as null
```

## Equality and hashing

Two containers are equal when they hold equal values: objects regardless of key order, arrays item by item. Numbers compare by exact value across types, so `1`, `1L`, `1.0` and `new BigDecimal("1.00")` are equal inside containers (and `-0.0` is `0`), and `hashCode()` is consistent with that, so containers work as `HashMap` keys and `HashSet` members. Compared with another `java.util.Map` or `List`, a container follows the `Map`/`List` contract, so the equality is symmetric, and its `hashCode()` is the JDK one for contents made of strings, booleans, `null`, `int`-range integers and non integral doubles. `JsonUtil.eq(a, b)` applies the same rule to any two values. Values of different JSON types are never equal (`1` is not `"1"`), and the plain Java values themselves keep Java semantics (`Integer.valueOf(1).equals(1.0)` is `false`).

Sample: `doc_examples/json/ValuesExamples.java` (`testNumberEquality`)

```java
JsonArray a1 = JsonArray.of(1, 2.5);
JsonArray a2 = JsonArray.of(1L, new BigDecimal("2.50"));
a1.equals(a2);                           // true
a1.hashCode() == a2.hashCode();          // true

JsonObject o1 = JsonObject.of("n", 1, "big", new BigInteger("10"));
JsonObject o2 = JsonObject.of("big", 10.0, "n", 1.0);   // key order does not matter
o1.equals(o2);                           // true
o1.hashCode() == o2.hashCode();          // true

Set<Object> set = new HashSet<>();
set.add(JsonArray.of(1));
set.contains(JsonArray.of(1.0));         // true

JsonUtil.eq(1, 1.0);                     // true
JsonUtil.eq(1, "1");                     // false
Integer.valueOf(1).equals(1.0);          // false: plain Java values keep Java semantics
```

## Ordering

`JsonUtil.compare(a, b)` is a total order over JSON values, used by `sorted()`, `min()` and `max()` (see [Collections](/GaltaJSON/Collections)); `JsonUtil.jsonComparator` wraps it as a `Comparator`. Numbers compare by value across types, strings with `String.compareTo`, `false` before `true`, arrays item by item then by length, objects key by key in sorted key order. Across types the order is `null` < array < object < string < number < boolean.

Sample: `doc_examples/json/ValuesExamples.java` (`testCompare`)

```java
JsonUtil.compare(1, 2.5);             // < 0
JsonUtil.compare("b", "a");           // > 0
JsonUtil.compare(false, true);        // < 0
JsonUtil.compare(null, 0);            // < 0: null first
JsonUtil.compare(JsonArray.of(1, 2), JsonArray.of(1L, 2.0));   // 0
JsonUtil.compare(JsonArray.of(1), JsonArray.of(1, 2));         // < 0: then by length

// Across types: null < array < object < string < number < boolean
JsonArray mixed = JsonArray.of(true, 1, "a", JsonObject.create(), JsonArray.create(), null);
mixed.sorted();                       // [null,[],{},"a",1,true]
```

## Cloning

`clone()` is the shallow `Map`/`List` copy: nested containers are shared. `deepClone()` copies the whole tree (primitives are immutable and shared). Deep cloning a container that holds a non-JSON Java object throws a `JsonException`.

Sample: `doc_examples/json/ValuesExamples.java` (`testClone`)

```java
JsonObject o = JsonObject.parse("{\"user\":{\"name\":\"Ada\"}}");
JsonObject shallow = o.clone();
JsonObject deep = o.deepClone();

o.getObject("user").put("name", "Grace");
shallow.getObject("user").getString("name");   // "Grace": shares the children
deep.getObject("user").getString("name");      // "Ada": full copy

JsonObject withDate = JsonObject.create();
withDate.putValue("d", new java.util.Date());
withDate.deepClone();                           // throws JsonException: not a JSON value
```

## Gotchas

- `getInt(key)` never parses a string; use `asInt(key)` for loosely typed input.
- `getInt(key, default)` is not forgiving about types, only about absence.
- `put(key, value)` with an `Object`-typed value is `Map.put` and does not chain; use `putValue`.
- `toString()` on a container is the *pretty* JSON text, while `stringify()` is compact.

## Source

`JsonObject.java`, `JsonArray.java`, `JsonContainer.java`, `JsonType.java`, `JsonUtil.java` (`as*`, `eq`, `hashCode`, `compare`), `java/JsonObjectAsLinkedMap.java`, `java/JsonArrayAsArrayList.java`, `java/JavaJsonFactory.java` (`deepClone`), under `parent-json/json/src/main/java/org/monflabs/json/`.
