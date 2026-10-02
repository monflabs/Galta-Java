# Galta JSON Import/Export

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/json-impexp?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/json-impexp)

Moves JSON documents between stores: folders of files, zip files, in-memory
JSON containers and Java streams. A document (`JsonContent`) is a key
(`JsonKey`: a collection and an id), a JSON value, an optional timestamp and a
type (record or deletion). Any `JsonSource` can be exported to any
`JsonTarget`, optionally restricted to a range of dates.

Targets that can read back what they hold can also *replicate*: receive only
the changes made since the last run, deletions included, with conflict
detection. The CSV source and target are in
[json-impexp-fastcsv](../json-impexp-fastcsv/README.md), and the in-memory
database that implements both ends of a replication in
[json-memdb](../json-memdb/README.md). The module has no third-party
dependency.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>json-impexp</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare a `<version>` directly.

```java
// data: { "customers": { "c1": {...}, "c2": {...} }, "orders": { "o1": {...} } }
JsonContainerSource source = JsonContainerSource.newBuilder()
        .format(JsonInMemoryFormat.RECORDSBYCOLKEY)
        .container(data)
        .build();

FileTarget target = FileTarget.newBuilder()
        .root(root.toFile())                     // cleared first, by default!
        .build();

ImportResult result = source.exportTo(target);   // same as target.importFrom(source)
// files: @customers/c1.json, @customers/c2.json, @orders/o1.json
```

## Contents

Under `org.monflabs.json.impexp`:

- `JsonContent`, `JsonKey`, `JsonSource`, `JsonTarget`, `ImportResult` - the model
- `container` - `JsonContainerSource`/`JsonContainerTarget` (a `JsonObject` or `JsonArray` in one of the `JsonInMemoryFormat` layouts), `StreamSource`, `JsonContentStreamSource`
- `file` - `FileSource`/`FileTarget` (one `.json` file per document), `ZipFileSource`, `ZipInputStreamSource`, `ZipTarget`
- `pojo` - `PojoTarget`, which hands each document to your callback
- `impl` - `JsonSourceImpl`, `JsonTargetImpl`, the bases of every source and target (document transforms, progress notifications, transactions)
- `replication` - `ReplicationSource`, `ReplicationTarget`, `ReplicationTable` (with the file based `FileReplicationTable`), `ConflictResolver`, `RangeFilter`
- `util` - `StaticContent`, `StaticDeletedContent`, `StringDumpTarget` (a text dump, handy in tests)

## Documentation

- [Import & Export](../../../docs/GaltaJSON/Modules/ImportExport.md) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Modules/ImportExport>
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
