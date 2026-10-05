# Add-on Modules

The core `json` artifact has no third-party dependency. Everything else is packaged as optional modules, each a separate Maven artifact (`groupId` `org.monflabs.galta`, same version as `json`, e.g. `0.8.0`) that brings its own dependencies only when you add it. Import `galta-bom` to use them without declaring versions (see [Building and Releasing](/BuildAndRelease)).

| Module (`artifactId`) | Adds | Third-party dependency |
|---|---|---|
| [`json-jackson`](/GaltaJSON/Modules/Jackson) | Jackson interoperability: Jackson reads and writes GaltaJSON values, and maps Java objects to and from them. | `com.fasterxml.jackson.core:jackson-databind` 2.22.3 |
| [`json-config`](/GaltaJSON/Modules/Config) | Application settings in JSON files: typed reads, updates with auto-save, `$ref`, encrypted values. | none |
| [`json-impexp`](/GaltaJSON/Modules/ImportExport) | Import, export and replication of JSON documents between files, zips, in-memory containers and streams. | none |
| [`json-impexp-fastcsv`](/GaltaJSON/Modules/ImportExport#csv) | CSV source and target for `json-impexp`. | `de.siegmar:fastcsv` 4.4.0 |
| [`json-memdb`](/GaltaJSON/Modules/MemoryDb) | `MemoryJsonDb`, an in-memory document store with selects, transactions and replication. | none (uses `json-impexp`) |
| [`json-yaml-snakeyaml`](/GaltaJSON/Modules/Yaml) | YAML parsing and writing. | `org.snakeyaml:snakeyaml-engine` 3.1.1 |
| [`json-jsonpath-jayway`](/GaltaJSON/Modules/JsonPathJayway) | Jayway JsonPath over GaltaJSON values. | `com.jayway.jsonpath:json-path` 3.0.0 |
| [`json-jsonschema-jsonschemafriend`](/GaltaJSON/Modules/JsonSchema) | JSON Schema validation. | `org.metaeffekt.bundle.jsonschemafriend:ae-jsonschemafriend-core` 0.12.5-1 |

All of them work on the values described in [Values](/GaltaJSON/Values): a YAML file, a CSV row, a configuration or a Java object mapped by Jackson is a regular `JsonObject` that the rest of the library, and the GaltaJS engine, can use as is.

## Samples

Every code sample in these pages is a JUnit test, run with the module's test suite on every build. The tests live in each module, under `galta/parent-json/<module>/src/test/java/doc_examples/`:

| Page | Test class |
|---|---|
| [Jackson](/GaltaJSON/Modules/Jackson) | `json-jackson`: `doc_examples/jackson/JacksonExamples.java` |
| [Configuration](/GaltaJSON/Modules/Config) | `json-config-test`: `doc_examples/config/ConfigExamples.java` |
| [Import & Export](/GaltaJSON/Modules/ImportExport) | `json-impexp`: `doc_examples/impexp/ImportExportExamples.java`; `json-impexp-fastcsv`: `doc_examples/csv/CsvExamples.java` |
| [Memory Database](/GaltaJSON/Modules/MemoryDb) | `json-memdb`: `doc_examples/memdb/MemoryDbExamples.java` |
| [YAML](/GaltaJSON/Modules/Yaml) | `json-yaml-snakeyaml`: `doc_examples/yaml/YamlExamples.java` |
| [JSON Path with Jayway](/GaltaJSON/Modules/JsonPathJayway) | `json-jsonpath-jayway`: `doc_examples/jsonpath/JaywayExamples.java` |
| [JSON Schema Validation](/GaltaJSON/Modules/JsonSchema) | `json-jsonschema-jsonschemafriend`: `doc_examples/jsonschema/JsonSchemaExamples.java` |

A page shows the essential lines of a test, with assertions written as comments (`// -> value`); the `Sample:` line under a snippet names the test method holding the complete, executable version.
