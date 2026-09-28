# GaltaJSON

?> Galta is a prototyping library provided as is, without any production support. See [A prototyping library](/?id=a-prototyping-library).

GaltaJSON is a JSON library for Java. It represents JSON with plain Java
values instead of wrapper nodes, and adds the tools most JSON-heavy code ends
up needing: JSON Path, JSON Pointer, `$ref` resolution, stream-like
collection operations, and add-on modules for serialization, configuration,
import/export, YAML and JSON Schema.

It is also the data model of the [GaltaJS](/GaltaJS/) JavaScript engine: a
JavaScript object *is* a `JsonObject`, so data moves between Java and
JavaScript without conversion.

## Design

- **Plain Java values.** A JSON value is a `String`, a `Boolean`, a `Number`,
  `null`, a `JsonObject` or a `JsonArray`. There is no node hierarchy to
  unwrap, which keeps the API small and the memory footprint low.
- **Maps and lists.** `JsonObject` is a `Map<String,Object>` and `JsonArray`
  is a `List<Object>`. The default implementations extend `LinkedHashMap`
  and `ArrayList`, so key order is preserved and any code that accepts a
  `Map` or a `List` accepts JSON directly.
- **Helpers on the containers.** Typed getters, never-failing conversions,
  fluent setters, date/time accessors and stream-like operations live on
  `JsonObject` and `JsonArray` themselves, not in a separate utility class.
- **One factory.** `JsonFactory` parses, stringifies and creates containers.
  Its options control things such as how number literals are mapped to Java
  types; a checked variant rejects values that are not valid JSON.
- **Few dependencies.** The core module depends only on the Galta
  [utilities](/Utilities/), which in turn only need the Eclipse null-analysis
  annotations. Every third-party integration is an optional module.

## Getting it

All artifacts use the group `org.monflabs.galta`. The core library:

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>json</artifactId>
  <version>0.8.0</version>
</dependency>
```

Add-on modules are separate artifacts, listed in [Add-on Modules](/GaltaJSON/Modules/).

## Guide

| Page | Covers |
|---|---|
| [Values & Containers](/GaltaJSON/Values) | The data model, `JsonObject`/`JsonArray`, typed getters and conversions, equality and ordering |
| [Parsing & Stringifying](/GaltaJSON/Parsing) | `JsonFactory`, what the parser accepts, number mapping, errors, stringify options |
| [JSON Path](/GaltaJSON/JsonPath) | The built-in JSON Path engine and `JsonValues` |
| [JSON Pointers & References](/GaltaJSON/Pointers) | `JsonPointer` (RFC 6901) and `$ref` resolution |
| [Collections & Streams](/GaltaJSON/Collections) | Stream-like array operations, Java streams, CSV mapping |
| [Schema Metadata](/GaltaJSON/Schema) | `SchemaNode`, reading type information from a JSON Schema |
| [Add-on Modules](/GaltaJSON/Modules/) | Serialization, configuration, import/export, in-memory database, YAML, Jayway JsonPath, JSON Schema validation |

## Samples

Every sample on these pages is a JUnit test, run on every build, so the
documented behaviour cannot drift from the code. A `Sample:` line under each
snippet names its test:

- core samples live in `galta/parent-json/json-tests/src/test/java/doc_examples/json/`, run by `AllJsonDocExamplesTests`;
- add-on samples live in each module's own `src/test/java/doc_examples/` folder.
