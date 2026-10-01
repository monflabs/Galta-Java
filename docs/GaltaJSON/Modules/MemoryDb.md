# Memory Database

The `json-memdb` module provides `MemoryJsonDb`, a small in-memory document store keyed by `JsonKey` (collection + id). It is thread-safe, supports optimistic transactions, keeps deletion tombstones, and plugs into [Import & Export](/GaltaJSON/Modules/ImportExport) as a source, a transactional target and both ends of a replication. It is well suited to tests, caches and prototypes; nothing is persisted, but its content can be exported to files at any time.

```xml
<dependency>
    <groupId>org.monflabs.galta</groupId>
    <artifactId>json-memdb</artifactId>
</dependency>
```

The classes live in `org.monflabs.json.impexp.db`.

## Insert, update, delete

Sample: `doc_examples/memdb/MemoryDbExamples.java` (`testCrud`)

```java
MemoryJsonDb db = new MemoryJsonDb();
JsonKey ada = JsonKey.of("people", "ada");

db.insert(ada, JsonObject.of("name", "Ada", "born", 1815));
db.insert(JsonKey.of("people", "alan"), JsonObject.of("name", "Alan", "born", 1912));
db.insert(JsonKey.of("teams", "t1"), JsonObject.of("name", "Engines"));
db.count();                         // -> 3
db.getCollections();                // -> [people, teams]

JsonDbRecord r = db.select(ada);    // null when missing
r.getJson();                        // {"name":"Ada","born":1815}

db.update(ada, JsonObject.of("name", "Ada Lovelace", "born", 1815));
db.upsert(JsonKey.of("people", "grace"), JsonObject.of("name", "Grace", "born", 1906));
db.delete(JsonKey.of("teams", "t1"));
db.isDeleted(JsonKey.of("teams", "t1"));   // -> true: a tombstone is kept
```

| Method | Behaviour |
|---|---|
| `insert(key, value)` | Fails with a `JsonException` if the key exists. |
| `update(key, value)` | Fails if the key does not exist. |
| `upsert(key, value)` | Inserts or replaces. |
| `delete(key)`, `delete(Collection<JsonKey>)` | Fails if a key does not exist (for a collection, before deleting anything: the call is all or nothing, and a key present twice is deleted once). Leaves a tombstone, used by replication. |
| `delete(Predicate<JsonDbRecord>)` | Deletes the matching records. |
| `exists(key)`, `isDeleted(key)` | Presence of a record / of a tombstone. |
| `count()`, `deletedCount()` | Number of records / tombstones. |
| `clear()` | Removes everything, tombstones included. The removed records are not replicated (there is no tombstone left), and a transaction started before can no longer commit. |
| `clear(true)` | Deletes every record like `delete(key)`, leaving tombstones: the clear is replicated. |
| `serialize([sorted])` | The records as one `JsonObject`, keyed by `JsonKey.keyString()`. |
| `execute(Function<MemoryJsonDb,T>)` | Runs several operations under the database lock. |

A value can be any JSON value. A record (`JsonDbRecord`) exposes `getKey()`, `getJson()`, `getTimestamp()` (the time of the last write, or the source timestamp for an imported document) and `getDbAdded()` (when it was written to this database). Re-inserting a deleted key removes its tombstone.

Values are stored as given, not copied: changing a `JsonObject` after inserting it (or after reading it with `select()`) changes the stored record, without updating its timestamps (so a replication does not see the change) and outside of any transaction (a rollback does not undo it). Treat stored values as read-only, and clone them (`deepClone()`) before changing them.

Sample: `doc_examples/memdb/MemoryDbExamples.java` (`testValuesAreNotCopied`, `testSerialize`, `testExecute`)

```java
db.insert(JsonKey.of("people", "ada"), JsonObject.of("name", "Ada"));
db.insert(JsonKey.of(null, "settings"), JsonObject.of("theme", "dark"));
db.serialize();
// {"people!!ada":{"name":"Ada"},"settings":{"theme":"dark"}}

int n = db.execute(d -> {            // atomic read-modify-write
    if(!d.exists(JsonKey.of("c", "counter"))) {
        d.insert(JsonKey.of("c", "counter"), 0);
    }
    int v = (Integer)d.select(JsonKey.of("c", "counter")).getJson() + 1;
    d.update(JsonKey.of("c", "counter"), v);
    return v;
});
```

## Selecting

`select()` starts a `JsonSelect`, narrowed with `collection(name)` and `filter(Predicate<JsonDbRecord>)`, and consumed with `stream()`, `forEach()`, `collect()`, `first()`, `count()`, `keys()` or `records()`. Records come in insertion order (an update keeps the position). A select iterates over a snapshot, so the database can be modified while iterating. The records are indexed by collection: a select of a collection only copies (and scans) the records of that collection. `count()` and `first()` take no snapshot: they scan the records while the database is locked (`first()` stops at the first match), so their filter must not wait on another thread using the database.

Sample: `doc_examples/memdb/MemoryDbExamples.java` (`testSelect`)

```java
List<String> modern = db.select()
        .collection("people")
        .filter(r -> ((JsonObject)r.getJson()).getInt("born") > 1900)
        .stream()
        .map(r -> ((JsonObject)r.getJson()).getString("name"))
        .collect(Collectors.toList());
// -> [Alan, Grace]

db.select().collection("people").count();      // -> 3

// The DB can be changed while iterating
db.select().collection("people").forEach(r -> db.delete(r.getKey()));

db.delete(r -> r.getKey().getCollection().equals("teams"));
```

A select is a scan of its collection (or of the whole database): there are no other indexes.

## Transactions

`beginTransaction()` returns a `MemoryJsonDb.Transaction`, with the same API as the database. A transaction only holds its own changes, and reads through to the database for the other records: it sees the committed state of the database plus its own changes (a record committed by another writer after the transaction began is visible to it). Its changes become visible at `commit()`, or are dropped by `rollback()`.

Transactions are optimistic: a commit fails with a `JsonException`, and changes nothing, when one of the records the transaction writes (inserts, updates or deletes) was changed in the database after the transaction began, or when the database was cleared (`clear()`). A change to another record is not a conflict. A failed commit leaves the transaction open, to be rolled back; a committed or rolled back transaction cannot be used any more.

Sample: `doc_examples/memdb/MemoryDbExamples.java` (`testTransactions`)

```java
MemoryJsonDb.Transaction tx = db.beginTransaction();
tx.insert(JsonKey.of("c", "k2"), JsonObject.of("v", 2));
tx.delete(JsonKey.of("c", "k1"));
db.exists(JsonKey.of("c", "k1"));   // -> true: not visible until committed
tx.commit();

MemoryJsonDb.Transaction tx3 = db.beginTransaction();
tx3.upsert(JsonKey.of("c", "k4"), JsonObject.of("v", 4));
db.insert(JsonKey.of("c", "k4"), JsonObject.of("v", 40));
tx3.commit();                       // JsonException: Database has been modified since the transaction started (record c:k4)
tx3.rollback();
```

A transaction costs nothing when it begins, and its commit is proportional to the number of records it changed, whatever the size of the database. The changes of a transaction are stamped with the commit date (`getDbAdded()`), so a replication from this database that ran while the transaction was pending still picks them up.

The isolation only covers the records, not their values: as values are not copied, changing a value read from a transaction (or from the database) changes it everywhere, and a rollback does not undo it. Write a new value (`update()`/`upsert()`) instead.

## Import and export

`JsonDbTarget` imports into a database and `JsonDbSource` exports from it, optionally limited to one collection. Both work with every source and target of [Import & Export](/GaltaJSON/Modules/ImportExport).

Sample: `doc_examples/memdb/MemoryDbExamples.java` (`testImportExport`)

```java
JsonDbTarget target = JsonDbTarget.newBuilder()
        .db(db)
        .build();
target.importFrom(JsonContainerSource.newBuilder()
        .format(JsonInMemoryFormat.RECORDSBYCOLKEY)
        .container(JsonObject.parse("""
                { "people": { "ada": { "name": "Ada" }, "alan": { "name": "Alan" } },
                  "teams":  { "t1": { "name": "Engines" } } }
                """))
        .build());

JsonDbSource people = JsonDbSource.newBuilder()
        .db(db)
        .collection("people")
        .build();
JsonContainerTarget out = JsonContainerTarget.newBuilder()
        .format(JsonInMemoryFormat.RECORDSBYKEY)
        .build();
people.exportTo(out);
// {"ada":{"name":"Ada"},"alan":{"name":"Alan"}}
```

`JsonDbTarget` is transactional: an import runs in a transaction and is rolled back if it fails, or is committed in chunks with `transactionThreshold(n)` (see [Transactions](/GaltaJSON/Modules/ImportExport#transactions)). A `JsonDbSource` also streams the tombstones, as deletions. It streams a snapshot taken when the stream starts, so the database can be changed while it is exported, even by the target of the same export. Each stream has its own snapshot and range filter, so several exports or replications can stream from the same source at the same time. Its `estimatedCount()` is the number of records and tombstones it streams (of its collection, if any). Its range filter applies to the date the records were stored in the database (`getDbAdded()`), not to their timestamp.

Sample: `doc_examples/memdb/MemoryDbExamples.java` (`testImportIsTransactional`)

## Replication

Two databases can be kept in sync with a `JsonDbSource`, a `JsonDbTarget` configured with a `replicationTable`, and `target.replicate(source, resolver)`. Each database has a unique replication id, and `MemoryReplicationTable` records, in memory, when each source/target pair was last replicated; `FileReplicationTable` (in `json-impexp`) does the same in a JSON file. Each run sends the records and tombstones written to the source since the previous run. See [Replication](/GaltaJSON/Modules/ImportExport#replication) for the conflict rules and resolvers.

Sample: `doc_examples/memdb/MemoryDbExamples.java` (`testReplication`, `testConflicts`, `testCustomResolver`)

```java
MemoryJsonDb master = new MemoryJsonDb();
MemoryJsonDb replica = new MemoryJsonDb();
master.insert(JsonKey.of("c", "k1"), JsonObject.of("v", 1));
master.insert(JsonKey.of("c", "k2"), JsonObject.of("v", 2));

JsonDbSource source = JsonDbSource.newBuilder().db(master).build();
JsonDbTarget target = JsonDbTarget.newBuilder()
        .db(replica)
        .replicationTable(new MemoryReplicationTable())   // remembers the last replication
        .build();

ReplicationResult r1 = target.replicate(source, ConflictResolver.FAIL_EXCEPTION);
// r1.getInserted() -> 2, replica.count() -> 2

master.update(JsonKey.of("c", "k1"), JsonObject.of("v", 10));
master.delete(JsonKey.of("c", "k2"));
ReplicationResult r2 = target.replicate(source, ConflictResolver.FAIL_EXCEPTION);
// r2.getInserted() -> 1, r2.getDeleted() -> 1
// replica: c:k1 -> {"v":10}, c:k2 is gone
```

A replication runs in a transaction on the target: a conflict that the resolver rejects rolls it back entirely. As the records committed by a transaction are stamped with the commit date, a replication never misses them, including in a chain (A to B to C) where B receives its records in transactions while it is replicated to C. A `clear()` is not replicated: use `clear(true)` to replicate it as deletions. `ReplicationResult.toJson()` summarizes a run (duration, time range in UTC, counts).
