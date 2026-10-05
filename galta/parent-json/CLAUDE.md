# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`parent-json` is a comprehensive JSON library. The core module (`json`) defines the primary API; the other modules add adapters for third-party libraries, schema validation, import/export formats, and configuration support.

## Sub-modules

| Module | Artifact | Purpose |
|---|---|---|
| `json` | `json` | Core JSON API and default implementation |
| `json-tests` | `json-tests` | Tests for the core library |
| `json-performance` | `json-performance` | JMH benchmarks (not published) |
| `json-jsonpath-jayway` | `json-jsonpath-jayway` | JSONPath via Jayway |
| `json-yaml-snakeyaml` | `json-yaml-snakeyaml` | YAML support via SnakeYAML |
| `json-jsonschema-jsonschemafriend` | `json-jsonschema-jsonschemafriend` | JSON Schema validation |
| `json-jackson` | `json-jackson` | Jackson module: Jackson reads/writes `JsonObject`/`JsonArray`, maps Java objects to/from them |
| `json-impexp` | `json-impexp` | Generic import/export framework |
| `json-impexp-fastcsv` | `json-impexp-fastcsv` | CSV import/export via FastCSV |
| `json-memdb` | `json-memdb` | In-memory JSON-backed store |
| `json-config` | `json-config` | JSON-based configuration management |
| `json-config-test` | `json-config-test` | Tests for json-config |

## Core API (`json` module)

Package root: `org.monflabs.json`

### Primary Interfaces

- **`JsonObject`** — ordered map of string → JSON value; typed getters (`getString`, `getInt`, `getArray`, …), stream-based iteration, path-based access
- **`JsonArray`** — ordered list of JSON values; supports filter, map, distinct, slice, and aggregation operations
- **`JsonFactory`** — creates `JsonObject` and `JsonArray` instances; obtained via `JsonFactoryService`
- **`JsonType`** — enum: `OBJECT`, `ARRAY`, `STRING`, `NUMBER`, `BOOLEAN`, `NULL`
- **`JsonUtil`** — utility methods for deep copy, merging, type conversion, and comparison

### Design Notes

- `JsonObject` and `JsonArray` are **interfaces**, not classes. Every helper (typed getters and setters, `is*` tests, conversions, the `at*()` negative-index methods, the stream-like functions of arrays) is a `default` method built on the `Map`/`List` methods (`get`, `put`, `add`, `set`, `size`...): an implementation only provides the storage. The default implementation is in `json/java/` (`JsonObjectAsLinkedMap`, `JsonArrayAsArrayList` and their `Checked` variants, created by `JavaJsonFactory`); GaltaJS has its own (`JsonObjectAsScriptMap`, `JSArrayImpl`). There is no adapter over other JSON libraries: the values are always plain Java ones (`String`, `Number`, `Boolean`, `null`, `JsonObject`, `JsonArray`).
- The GaltaJS engine uses `JsonObject`/`JsonArray` as its native Object/Array types, so changes to these interfaces affect the JS engine.

## `json-jackson`

`GaltaJsonModule` (a Jackson `SimpleModule`) registers a deserializer for `JsonContainer`/`JsonObject`/`JsonArray` that builds the containers with the module's `JsonFactory` (the environment's factory for GaltaJS, so they are JavaScript objects) and applies the factory's number rules (`parseInteger`/`parseDecimal` on the literal text; a `BigDecimal` from a Java object is kept), and a serializer that asks each container's own `factory().exportValue()` how to write a value (`JsonFactory.NO_VALUE`: left out of an object, `null` in an array; GaltaJS's factory returns it for undefined, functions and symbols, so the output equals `JSON.stringify()`). Cycles are a `JsonMappingException`. Java objects are mapped by Jackson (`convertValue`): this replaced the former bespoke `json-serialization` module. Jackson's version is pinned in the module's pom, not in monflabs-parent (it would override the Jackson of Spring Boot based peers). User docs: `docs/GaltaJSON/Modules/Jackson.md`, samples in `doc_examples/jackson/JacksonExamples.java`.

## `json-config`

Configuration backed by JSON files (`JsonFileConfig`) or held in memory: typed reads with defaults, updates saved back atomically, `$ref` to split it across resource files (fragment references are written back), encrypted values (`KeyEncryptor`, bound to their key path) and read-only configurations. User docs: `docs/GaltaJSON/Modules/Config.md`.
