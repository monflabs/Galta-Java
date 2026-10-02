# Galta JSON Config

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/json-config?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/json-config)

Application settings stored in JSON resources and read through slash-separated
keys (`server/port`). A configuration implements
`org.monflabs.util.config.Config`, the generic configuration interface of the
Galta `utilities` module, with typed reads and defaults, transactional updates
with automatic saving, `$ref` links between resources and the encryption of
selected values such as passwords.

`JsonFileConfig` keeps its files in a folder: `$ref` resources must stay
inside it, files are saved through a temporary file then moved, and a lock file
lets several configurations, in other processes too, share the folder. It has
no third-party dependency.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>json-config</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare a `<version>` directly.

```java
JsonFileConfig config = JsonFileConfig.newBuilder()
        .folder(folder)
        .fileName("app.json")
        .build();

config.getString("server/host");          // -> "localhost"
config.getInt("server/port");             // -> 8080
config.getInt("server/retries", 30);      // -> 30: default when missing

boolean changed = config.updateValues(u -> {
    u.put("server/host", "example.com");     // folders are created
    u.put("server/port", 443);
});
```

## Contents

Under `org.monflabs.json.config`:

- `JsonConfig` - a `Config` backed by a `JsonObject` (`getContent()`)
- `AbstractJsonConfig` - the shared implementation; a custom storage only provides the resource reads and writes
- `JsonFileConfig` - JSON files in a folder
- `CustomJsonConfig` - resources read and written by functions you provide; read-only without a writer
- `ValueEncryptor`, `KeyEncryptor` - encryption of the values selected by a key predicate (AES-GCM, key derived from a passphrase with PBKDF2)

The tests of this module live in [json-config-test](../json-config-test/README.md).

## Documentation

- [Configuration](../../../docs/GaltaJSON/Modules/Config.md) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Modules/Config>
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
