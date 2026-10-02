# Galta JSON - YAML

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/json-yaml-snakeyaml?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/json-yaml-snakeyaml)

Reads and writes YAML with [SnakeYAML Engine](https://bitbucket.org/snakeyaml/snakeyaml-engine)
(`org.snakeyaml:snakeyaml-engine`), the YAML 1.2 implementation of SnakeYAML.
Parsing produces regular GaltaJSON values: mappings become `JsonObject`s and
sequences `JsonArray`s, so a YAML file can be queried, validated or serialized
like any JSON document.

Values that JSON cannot represent (`.nan`/`.inf`, recursive aliases, colliding
or non-string keys, unknown tags, binary data) are rejected with a
`JsonException`, as is a stream of several documents. Stringifying quotes the
strings that would read back as another type, YAML 1.1 forms such as `yes` or
`0x1F` included. The input size, the aliases and the expanded size of a
document are limited, against "billion laughs" documents.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>json-yaml-snakeyaml</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare a `<version>` directly.

```java
Object value = SnakeYaml.parse("""
        server:
          host: example.com
          ports: [80, 443]
          tls: true
        owner: null
        """);
JsonObject o = (JsonObject)value;                         // a regular GaltaJSON object
o.getObject("server").getString("host");                  // -> "example.com"
o.getObject("server").getArray("ports").getInt(1);        // -> 443
```

## Contents

Everything is in one class, `org.monflabs.json.yaml.SnakeYaml`:

- `parse(String | Reader | InputStream)` - parses one YAML document, optionally with a `JsonFactory` and `SnakeYaml.Options`
- `stringify(Object[, DumpSettings])` - writes a value as YAML, block style by default
- `SnakeYaml.Options` - the parse limits: code points, aliases for collections, expanded size

## Documentation

- [YAML](../../../docs/GaltaJSON/Modules/Yaml.md) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Modules/Yaml>
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
