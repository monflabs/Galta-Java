# Galta JSON

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/json?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/json)

The core GaltaJSON library. A JSON value is a plain Java value (`String`,
`Boolean`, `Number`, `null`, `JsonObject` or `JsonArray`); `JsonObject` is a
`Map<String,Object>` and `JsonArray` a `List<Object>`, whose default
implementations extend `LinkedHashMap` and `ArrayList`. Typed getters,
conversions, fluent setters and stream-like operations live on the containers
themselves.

The module also holds a lenient parser (with a strict mode), a stringifier, a
JSON Path engine, JSON Pointer (RFC 6901), `$ref` resolution and a read-only
view over JSON Schema documents. Its only dependency is the Galta `utilities`
module. It is the data model of the GaltaJS engine.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>json</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare a `<version>` directly.

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
```

## Contents

Under `org.monflabs.json`:

- `JsonObject`, `JsonArray`, `JsonContainer`, `JsonType`, `JsonUtil`, `JsonException` - the value model and its helpers
- `JsonFactory` - parses, stringifies and creates containers; `JsonFactory.get()` is the default factory
- `java` - the default implementations (`JavaJsonFactory`, `JsonObjectAsLinkedMap`, `JsonArrayAsArrayList`), their checked variants that only accept JSON values, and `AbstractJsonObject`
- `parser`, `stringifier` - `JsonParser`, `ParseException`, `JsonStringifier`
- `jsonpath` - the JSON Path engine (`JsonPathFactory`, `JsonPath`) and `JsonValues`, the result and navigation API
- `jsonpointer`, `jsonreference` - `JsonPointer` and `JsonReference` (`$ref` resolution, external documents included)
- `jsonschema` - `SchemaNode` and `SchemaType`, type information read from a JSON Schema (no validation)
- `stream`, `util` - `CsvMapping` (CSV lines to and from JSON), `JsonCollectors` and `Reducers` for streams
- `serializers` - `JsonSerializer` and the `java.time` string serializers of `Serializers`
- `wrappers`, `model` - typed wrappers over containers, and `JsonModelAccessor`

## Documentation

- [Values & Containers](../../../docs/GaltaJSON/Values.md) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Values>
- [Parsing & Stringifying](../../../docs/GaltaJSON/Parsing.md) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Parsing>
- [JSON Path](../../../docs/GaltaJSON/JsonPath.md) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/JsonPath>
- [JSON Pointers & References](../../../docs/GaltaJSON/Pointers.md) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Pointers>
- [Collections & Streams](../../../docs/GaltaJSON/Collections.md) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Collections>
- [Schema Metadata](../../../docs/GaltaJSON/Schema.md) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Schema>
- [API reference](https://monflabs.github.io/Galta-Java/#/API)

The tests of this module live in [json-tests](../json-tests/README.md).
