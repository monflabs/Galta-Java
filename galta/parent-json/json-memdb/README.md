# Galta JSON Memory DB

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/json-memdb?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/json-memdb)

`MemoryJsonDb`, a small in-memory document store keyed by `JsonKey`
(collection + id). It is thread safe, supports optimistic transactions and
keeps deletion tombstones. It plugs into
[json-impexp](../json-impexp/README.md) as a source, a transactional target
and both ends of a replication.

It suits tests, caches and prototypes: nothing is persisted, but the content
can be exported to files at any time. Values are stored as given, not copied,
so treat them as read-only once stored. Selects scan a collection (or the
whole database): there are no other indexes.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>json-memdb</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare a `<version>` directly.

```java
MemoryJsonDb db = new MemoryJsonDb();
JsonKey ada = JsonKey.of("people", "ada");

db.insert(ada, JsonObject.of("name", "Ada", "born", 1815));
db.insert(JsonKey.of("people", "alan"), JsonObject.of("name", "Alan", "born", 1912));
db.count();                         // -> 2

JsonDbRecord r = db.select(ada);    // null when missing
r.getJson();                        // {"name":"Ada","born":1815}
```

## Contents

Under `org.monflabs.json.impexp.db`:

- `MemoryJsonDb` - the database: insert, update, upsert, delete, selects and transactions (`MemoryJsonDb.Transaction`)
- `JsonDbRecord` - a stored record: key, value, timestamp and the date it was written to the database
- `JsonSelect` - a select narrowed by collection and filter, consumed as a stream, a list, a count...
- `JsonDbSource`, `JsonDbTarget` - the `json-impexp` source and (transactional, replication) target
- `MemoryReplicationTable` - a replication history kept in memory

## Documentation

- [Memory Database](../../../docs/GaltaJSON/Modules/MemoryDb.md) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Modules/MemoryDb>
- [Import & Export, Replication](../../../docs/GaltaJSON/Modules/ImportExport.md#replication) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Modules/ImportExport?id=replication>
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
