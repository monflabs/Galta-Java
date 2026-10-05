# GaltaJSON

?> Galta is a prototyping library provided as is, without any production support. See [A prototyping library](/?id=a-prototyping-library).

GaltaJSON is a JSON library for Java. It represents JSON with plain Java
values instead of wrapper nodes, and adds the tools most JSON-heavy code ends
up needing: JSON Path, JSON Pointer, `$ref` resolution, stream-like
collection operations, and add-on modules for Jackson interoperability,
configuration, import/export, YAML and JSON Schema.

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
  They are `default` methods of the interfaces, built on the `Map` and `List`
  methods (`get`, `put`, `add`, `set`, `size`), so every implementation (the
  Java containers, the JavaScript objects of GaltaJS) behaves the same, and an
  implementation only provides the storage.
- **One factory.** `JsonFactory` parses, stringifies and creates containers.
  Its options control things such as how number literals are mapped to Java
  types; a checked variant rejects values that are not valid JSON.
- **Few dependencies.** The core module depends only on the Galta
  [utilities](/Utilities/), which in turn only need the Eclipse null-analysis
  annotations. Every third-party integration is an optional module.

## Why its own JSON library

Jackson is the reference JSON library for Java, and GaltaJSON doesn't try to
replace it: object mapping, binary formats and the Jackson ecosystem are better
served by Jackson, and the [`json-jackson`](/GaltaJSON/Modules/Jackson) module
lets the two work together (Jackson maps Java objects to and from GaltaJSON
values). GaltaJSON exists for what Jackson can't provide:

- **It is the object model of a JavaScript engine.** A GaltaJS object *is* a
  `JsonObject` and an array a `JsonArray`, so values cross between Java and
  JavaScript with no conversion, in both directions. This needs mutable `Map`s
  and `List`s of plain Java values, which Jackson's `JsonNode` tree is not.
- **JavaScript's exact JSON semantics.** GaltaJS's `JSON.parse()` and
  `JSON.stringify()` are this library, checked by the TC39 test262 suite: the
  number text of `Number::toString` (`1e+21`, not `1.0E21`), the escaping of lone
  surrogates, `NaN` and the infinities written as `null`, `-0`, the property
  order, revivers and replacers. Configuring Jackson close to this still leaves
  most of a stringifier to write.
- **A lenient syntax for hand-written JSON.** Comments, unquoted keys, single
  quotes, trailing commas, hexadecimal numbers and `NaN`/`Infinity` (see
  [Lenient syntax](/GaltaJSON/Parsing#lenient-syntax)), with a strict mode for
  standard JSON.
- **Plain values with the tools around them.** JSON Path, JSON Pointer, `$ref`
  resolution, schema metadata and stream-like operations work on `Map`s and
  `List`s, so any code that accepts a `Map` or a `List` accepts JSON. Numbers
  keep their natural Java type, and a decimal a `double` can't hold exactly
  stays a `BigDecimal` instead of being rounded.
- **No dependency.** The core module needs only the Galta utilities, which
  matters for an embedded engine and for the browser playground.

It is not slower for this. The JMH benchmarks of the `json-performance` module
compare it with Jackson's tree model on the same data (microseconds per
operation, JDK 21, one machine: the ratios matter, not the absolute values):

| Benchmark | GaltaJSON | Jackson |
|---|---|---|
| Parse, records (~50 KB) | 148 | 231 |
| Parse, records (~5 MB) | 14,654 | 21,107 |
| Parse, numbers | 1,285 | 1,355 |
| Parse, strings with escapes | 203 | 240 |
| Stringify, records (~50 KB) | 123 | 108 |
| Stringify, records (~5 MB) | 13,369 | 11,577 |
| Stringify, numbers | 955 | 721 |
| Stringify, strings with escapes | 304 | 209 |

Parsing is faster than Jackson; stringifying is close on usual documents and
behind on numeric and string heavy content, where it writes JavaScript's exact
number text. The price of a library of its own is its maintenance, which is
why its behavior is pinned by tests: each documented sample is a test, the
parser and the stringifier have differential tests against reference
implementations, and test262 checks the JavaScript side.

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
| [Add-on Modules](/GaltaJSON/Modules/) | Jackson interoperability, configuration, import/export, in-memory database, YAML, Jayway JsonPath, JSON Schema validation |

## Samples

Every sample on these pages is a JUnit test, run on every build, so the
documented behaviour cannot drift from the code. A `Sample:` line under each
snippet names its test:

- core samples live in `galta/parent-json/json-tests/src/test/java/doc_examples/json/`, run by `AllJsonDocExamplesTests`;
- add-on samples live in each module's own `src/test/java/doc_examples/` folder.
