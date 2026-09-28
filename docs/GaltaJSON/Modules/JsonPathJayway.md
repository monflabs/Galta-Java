# JSON Path with Jayway

GaltaJSON has its own JSON Path engine, described in [JSON Path](/GaltaJSON/JsonPath). The `json-jsonpath-jayway` module is for code that already uses [Jayway JsonPath](https://github.com/json-path/JsonPath), or needs its exact dialect: it plugs GaltaJSON values into Jayway as a *JSON provider*, so Jayway reads and modifies `JsonObject`/`JsonArray` directly, without converting the document.

```xml
<dependency>
    <groupId>org.monflabs.galta</groupId>
    <artifactId>json-jsonpath-jayway</artifactId>
</dependency>
```

| Class | Role |
|---|---|
| `MonfLabsJsonProvider` | Jayway `JsonProvider` over GaltaJSON values: parsing, reading, creating and updating objects and arrays. |
| `MonfLabsMappingProvider` | Jayway `MappingProvider`, used for typed reads. |
| `MonfLabsJsonPathConfiguration` | `configuration()` returns a Jayway `Configuration` using both; `initialize()` installs them as Jayway's JVM-wide defaults. |
| `JaywayJsonPath` | Wraps a compiled Jayway path and returns `JsonValues`, like the built-in engine. |

## Configuration

Build a Jayway `Configuration` with the two providers and use it explicitly. The results are GaltaJSON values: the objects of the document themselves (not copies), and a `JsonArray` for the list returned by an indefinite path.

Sample: `doc_examples/jsonpath/JaywayExamples.java` (`testConfiguration`)

```java
Configuration conf = Configuration.builder()
        .jsonProvider(new MonfLabsJsonProvider())
        .mappingProvider(new MonfLabsMappingProvider())
        .build();

JsonObject doc = JsonObject.parse(STORE);
List<Object> titles = JsonPath.using(conf).parse(doc).read("$.store.book[?(@.price < 10)].title");
// -> ["Sayings", "Moby Dick"], a JsonArray

Object bicycle = JsonPath.using(conf).parse(doc).read("$.store.bicycle");
// the very JsonObject held by doc

Object parsed = JsonPath.using(conf).parse(STORE).json();   // text is parsed into a JsonObject
```

where `STORE` is:

```json
{ "store": {
    "book": [
      { "title": "Sayings", "author": "Nigel Rees", "price": 8.95, "isbn": null },
      { "title": "Sword", "author": "Evelyn Waugh", "price": 12.99 },
      { "title": "Moby Dick", "author": "Herman Melville", "price": 8.99, "isbn": "0-553-21311-3" }
    ],
    "bicycle": { "color": "red", "price": 19.95 }
} }
```

Alternatively, `MonfLabsJsonPathConfiguration.initialize()` makes these providers Jayway's defaults, so the static shortcuts like `JsonPath.read(doc, path)` use them. This changes Jayway's configuration for the whole JVM, including other libraries that use Jayway; `Configuration.setDefaults(null)` restores Jayway's own defaults.

Sample: `doc_examples/jsonpath/JaywayExamples.java` (`testGlobalDefaults`)

```java
MonfLabsJsonPathConfiguration.initialize();
Double price = JsonPath.read(doc, "$.store.bicycle.price");       // -> 19.95
int count = JsonPath.read(doc, "$.store.book.length()");          // -> 3
```

## Null versus missing

The provider distinguishes a property holding `null` from a missing property, as the reference Jayway provider does. A definite path to a missing property throws `PathNotFoundException`, while one to a `null` returns `null`. Indefinite paths skip what is missing, and Jayway's options apply as usual.

Sample: `doc_examples/jsonpath/JaywayExamples.java` (`testNullVersusMissing`)

```java
JsonPath.using(conf).parse(doc).read("$.store.book[0].isbn");    // -> null: present
JsonPath.using(conf).parse(doc).read("$.store.book[1].isbn");    // PathNotFoundException: missing
JsonPath.using(conf).parse(doc).read("$.store.book[*].isbn");    // -> [null, "0-553-21311-3"]

Configuration lenient = conf.addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);
JsonPath.using(lenient).parse(doc).read("$.store.book[1].isbn"); // -> null

// In filters, an existence test counts a present null
JsonPath.using(conf).parse(doc).read("$.store.book[?(@.isbn)].title");          // -> ["Sayings", "Moby Dick"]
JsonPath.using(conf).parse(doc).read("$.store.book[?(@.isbn == null)].title");  // -> ["Sayings"]
```

## Updating a document

Jayway's `set`, `put`, `add`, `delete` and `map` work on the GaltaJSON document in place. Setting an array element replaces it; an index equal to the array size appends.

Sample: `doc_examples/jsonpath/JaywayExamples.java` (`testUpdates`)

```java
JsonPath.using(conf).parse(doc)
        .set("$.store.bicycle.color", "blue")
        .put("$.store.bicycle", "gears", 21)
        .add("$.store.book", JsonObject.of("title", "New"))
        .delete("$.store.book[0]")
        .map("$.store.book[*].price", (v, c) -> v == null ? null : ((Number)v).doubleValue() * 2);

doc.getObject("store").getObject("bicycle");   // {"color":"blue","price":19.95,"gears":21}
```

## Typed reads

`read(path, type)` goes through the mapping provider:

- For `Map`, `List` and `Object`, it *copies* the result into plain `LinkedHashMap` (key order kept) / `ArrayList` instances. `JsonObject` / `JsonArray` return the GaltaJSON value itself.
- Numbers convert between numeric types (`Integer`, `Long`, `Double`, `BigDecimal`...), and a numeric string converts to a number. A conversion that would lose information, a fraction or an out-of-range value, throws a Jayway `MappingException` instead of truncating.
- `String` gives the string form of any value; `Boolean` accepts a boolean or `"true"`/`"false"`.
- Any other incompatible conversion throws a `MappingException`.
- A `TypeRef` maps to its raw type (`new TypeRef<List<String>>(){}` maps like `List.class`); the elements are not converted.

To get GaltaJSON values, use `read(path)` without a type.

Sample: `doc_examples/jsonpath/JaywayExamples.java` (`testTypedReads`)

```java
Object copy = JsonPath.using(conf).parse(doc).read("$.store.bicycle", Map.class);   // a LinkedHashMap copy
String color = JsonPath.using(conf).parse(doc).read("$.store.bicycle.color", String.class);
```

## `JaywayJsonPath`

`JaywayJsonPath` wraps a compiled Jayway path and returns its results as `JsonValues`, the result type of the built-in engine, so both can be used interchangeably. A path that matches nothing gives an empty `JsonValues` rather than an exception. It always reads with `MonfLabsJsonPathConfiguration.configuration()`, whatever Jayway's JVM-wide defaults are, so no `initialize()` call is needed. JSON Pointers are not supported (`read(json, true)` throws a `JsonException`).

Sample: `doc_examples/jsonpath/JaywayExamples.java` (`testJaywayJsonPath`)

```java
JaywayJsonPath path = new JaywayJsonPath(JsonPath.compile("$.store.book[*].author"));
JsonValues authors = path.read(doc);
authors._size();        // -> 3
authors._get(2);        // -> "Herman Melville"

new JaywayJsonPath(JsonPath.compile("$.store.missing")).read(doc).isEmpty();   // -> true
```

## Which engine?

Prefer the built-in engine ([JSON Path](/GaltaJSON/JsonPath)) for new code: it needs no extra dependency and no global configuration, and it is designed around GaltaJSON values. Use this module to run existing Jayway expressions and code unchanged, or when you rely on Jayway-specific behaviour such as its functions, filter syntax and options.
