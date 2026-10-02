# Galta JSON Parent

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/parent-json?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/parent-json)

Parent of the **GaltaJSON** modules: the core JSON library and its optional
add-on modules, each a separate artifact that brings its own third-party
dependency only when you add it. All artifacts use the `org.monflabs.galta`
group id; import the `galta-bom` to use them without versions (see
[Modules](../../README.md#modules) in the root README).

GaltaJSON is also the data model of the GaltaJS engine: a JavaScript object
*is* a `JsonObject`.

## Modules

Libraries, published to Maven Central:

- [json](json/README.md) - the core library: parser and stringifier, `JsonObject`/`JsonArray`, JSON Path, JSON Pointer, `$ref` resolution, schema metadata
- [json-serialization](json-serialization/README.md) - Java objects and records to and from JSON values
- [json-config](json-config/README.md) - application settings in JSON files, with `$ref` composition and encrypted values
- [json-impexp](json-impexp/README.md) - import, export and replication of JSON documents between files, zips, containers and streams
- [json-impexp-fastcsv](json-impexp-fastcsv/README.md) - CSV source and target for `json-impexp`, based on FastCSV
- [json-memdb](json-memdb/README.md) - `MemoryJsonDb`, an in-memory document store with transactions and replication
- [json-yaml-snakeyaml](json-yaml-snakeyaml/README.md) - YAML reading and writing, based on SnakeYAML Engine
- [json-jsonpath-jayway](json-jsonpath-jayway/README.md) - Jayway JsonPath over GaltaJSON values
- [json-jsonschema-jsonschemafriend](json-jsonschema-jsonschemafriend/README.md) - JSON Schema validation, based on jsonschemafriend

Not published:

- [json-tests](json-tests/README.md) - the test suite of the core library (and the core doc samples)
- [json-config-test](json-config-test/README.md) - the test suite of `json-config`
- [json-performance](json-performance/README.md) - JMH benchmarks, compared with Jackson and Gson

The parent turns on resource leak detection for the tests of every module
(`monflabs.tests.trackLeaks`, see
[Testing](../../docs/Utilities/Testing.md)).

## Documentation

- [GaltaJSON](../../docs/GaltaJSON/README.md) - online at <https://monflabs.github.io/Galta-Java/#/GaltaJSON/>
- [Add-on Modules](../../docs/GaltaJSON/Modules/README.md) - online at <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Modules/>
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
