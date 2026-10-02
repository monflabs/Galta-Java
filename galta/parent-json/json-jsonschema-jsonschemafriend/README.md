# Galta JSON - JSON Schema Validation

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/json-jsonschema-jsonschemafriend?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/json-jsonschema-jsonschemafriend)

JSON Schema validation of GaltaJSON values, backed by
[jsonschemafriend](https://github.com/jimblackler/jsonschemafriend), which
supports drafts 3, 4, 6, 7, 2019-09 and 2020-12. It validates `JsonObject`,
`JsonArray` and any other JSON value directly, without converting them. The
core `json` module only reads schemas (`SchemaNode`); this module adds the
validation.

jsonschemafriend comes from Maven Central as
`org.metaeffekt.bundle.jsonschemafriend:ae-jsonschemafriend-core`: the original
project is only published on JitPack, and this is the same code, published by
metaeffekt. Unlike jsonschemafriend itself, the factory fails closed: a schema
or `$ref` that cannot be loaded throws a `JsonException` instead of accepting
everything. Schemas must be trusted, as a schema URI or `$ref` is loaded
whatever its scheme (`http:`, `file:`...), unless the factory is created with a
restricted loader (`JsonSchemaFactory.NO_LOADING`).

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>json-jsonschema-jsonschemafriend</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare a `<version>` directly.

```java
JsonSchema schema = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse("""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": { "name": { "type": "string", "minLength": 2 } },
          "required": ["name"]
        }
        """));
schema.validate(JsonObject.parse("{ \"name\": \"A\" }"));
// JsonException: Validation errors: "A" at #/name failed against #/properties/name with "Shorter than minLength: 2"
```

## Contents

Under `org.monflabs.json.jsonschema`:

- `JsonSchemaFactory` - builds a `JsonSchema` from an in-memory document or a URI (URI schemas are cached); `JsonSchemaFactory.get()` is a shared, thread-safe instance
- `JsonSchema` - `validate(value)` returns normally when the value is valid and throws a `JsonException` listing every error otherwise

## Documentation

- [JSON Schema Validation](../../../docs/GaltaJSON/Modules/JsonSchema.md) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Modules/JsonSchema>
- [Schema Metadata](../../../docs/GaltaJSON/Schema.md) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Schema>
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
