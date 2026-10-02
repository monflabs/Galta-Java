# Galta JSON - Jayway JsonPath

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/json-jsonpath-jayway?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/json-jsonpath-jayway)

Plugs GaltaJSON values into [Jayway JsonPath](https://github.com/json-path/JsonPath)
(`com.jayway.jsonpath:json-path`) as a *JSON provider*, so Jayway reads and
modifies `JsonObject`/`JsonArray` documents directly, without converting them.
The results are GaltaJSON values: the objects of the document themselves, and a
`JsonArray` for the list returned by an indefinite path.

GaltaJSON has its own JSON Path engine in the core `json` module, which needs
no extra dependency and no global configuration. Use this module to run
existing Jayway expressions and code unchanged, or when you rely on
Jayway-specific behaviour.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>json-jsonpath-jayway</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare a `<version>` directly.

```java
Configuration conf = Configuration.builder()
        .jsonProvider(new MonfLabsJsonProvider())
        .mappingProvider(new MonfLabsMappingProvider())
        .build();

JsonObject doc = JsonObject.parse(STORE);
List<Object> titles = JsonPath.using(conf).parse(doc).read("$.store.book[?(@.price < 10)].title");
// -> ["Sayings", "Moby Dick"], a JsonArray
```

## Contents

Under `org.monflabs.json.jsonpath`:

- `MonfLabsJsonProvider` - Jayway `JsonProvider` over GaltaJSON values: parsing, reading, creating and updating objects and arrays
- `MonfLabsMappingProvider` - Jayway `MappingProvider` for typed reads; a lossy number conversion throws instead of truncating
- `MonfLabsJsonPathConfiguration` - `configuration()` returns a Jayway `Configuration` using both; `initialize()` installs them as Jayway's JVM-wide defaults
- `JaywayJsonPath` - wraps a compiled Jayway path and returns `JsonValues`, like the built-in engine

## Documentation

- [JSON Path with Jayway](../../../docs/GaltaJSON/Modules/JsonPathJayway.md) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Modules/JsonPathJayway>
- [JSON Path](../../../docs/GaltaJSON/JsonPath.md), the built-in engine - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/JsonPath>
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
