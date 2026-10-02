# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`parent-json` is a comprehensive JSON library. The core module (`json`) defines the primary API; the other modules add adapters for third-party libraries, schema validation, import/export formats, and configuration support.

## Sub-modules

| Module | Artifact | Purpose |
|---|---|---|
| `json` | `json` | Core JSON API and default implementation |
| `json-tests` | `json-tests` | Tests for the core library |
| `json-performance` | `json-performance` | Micro-benchmarks (not part of the library surface) |
| `json-jsonpath-jayway` | `json-jsonpath-jayway` | JSONPath via Jayway |
| `json-yaml-snakeyaml` | `json-yaml-snakeyaml` | YAML support via SnakeYAML |
| `json-jsonschema-jsonschemafriend` | `json-jsonschema-jsonschemafriend` | JSON Schema validation |
| `json-serialization` | `json-serialization` | Object ↔ JSON serialization |
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

- `JsonObject` and `JsonArray` are **interfaces**, not classes. The default implementation is in `json/java/` (`JsonObjectAsLinkedMap`, `JsonArrayAsArrayList`, created by `JavaJsonFactory`). Third-party adapters would wrap foreign types behind the same interfaces (`AbstractJsonObject` is the base for that; no such adapter module exists today).
- The GaltaJS engine uses `JsonObject`/`JsonArray` as its native Object/Array types, so changes to these interfaces affect the JS engine.

## `json-serialization`

Serializes plain Java objects and records to/from `JsonObject`. Constraints: classes need a no-arg constructor (records use their canonical constructor); circular references are not supported. Uses reflection; field (or record component) names map directly to JSON keys. The scalar types share `ScalarClassAdapter`/`ScalarFieldAdapter` and the primitive arrays `PrimitiveArrayClassAdapter`. `SimpleRegistry` is thread safe (lock-free lookups, creation under a lock, adapters published only when their initialization succeeds) and creates its built-in adapters (scalars, `java.time`/`UUID`/`URI`/`Date` via `StringValueClassAdapter`, `Optional`, `Number`, collections) on each `build()`. A JSON null assigns null (an error for a primitive); errors inside a graph are `SerializationException`s carrying a `$.a[1].b` path; `CycleGuard` also enforces the max depth. User docs: `docs/GaltaJSON/Modules/Serialization.md`, samples in `doc_examples/serialization/SerializationExamples.java`.

## `json-config`

Configuration backed by JSON files (`JsonFileConfig`) or held in memory: typed reads with defaults, updates saved back atomically, `$ref` to split it across resource files (fragment references are written back), encrypted values (`KeyEncryptor`, bound to their key path) and read-only configurations. User docs: `docs/GaltaJSON/Modules/Config.md`.

## Adapter Pattern

A third-party adapter would implement `JsonObject`/`JsonArray` by wrapping the underlying library's types, so callers work with a single API regardless of the JSON backend. The modules that integrate third-party libraries today (`json-jsonpath-jayway`, `json-yaml-snakeyaml`, `json-jsonschema-jsonschemafriend`) instead adapt the library to the core types.
