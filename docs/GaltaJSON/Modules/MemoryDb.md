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
| `delete(key)`, `delete(Collection<JsonKey>)` | Fails if a key does not exist (for a collection, before deleting anything). Leaves a tombstone, used by replication. |
| `delete(Predicate<JsonDbRecord>)` | Deletes the matching records. |
| `exists(key)`, `isDeleted(key)` | Presence of a record / of a tombstone. |
| `count()`, `deletedCount()` | Number of records / tombstones. |
| `clear()` | Removes everything, tombstones included. |
| `serialize([sorted])` | The records as one `JsonObject`, keyed by `JsonKey.keyString()`. |
| `execute(Function<MemoryJsonDb,T>)` | Runs several operations under the database lock. |

A value can be any JSON value. A record (`JsonDbRecord`) exposes `getKey()`, `getJson()`, `getTimestamp()` (the time of the last write, or the source timestamp for an imported document) and `getDbAdded()` (when it was written to this database). Re-inserting a deleted key removes its tombstone.

Values are stored as given, not copied: changing a `JsonObject` after inserting it changes the stored record. Clone it first (`deepClone()`) when that matters.

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

`select()` starts a `JsonSelect`, narrowed with `collection(name)` and `filter(Predicate<JsonDbRecord>)`, and consumed with `stream()`, `forEach()`, `collect()`, `first()`, `count()`, `keys()` or `records()`. Records come in insertion order (an update keeps the position). A select iterates over a snapshot, so the database can be modified while iterating.

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

A select is a scan: there are no indexes.

## Transactions

`beginTransaction()` returns a `MemoryJsonDb.Transaction`, a private copy of the database with the same API. Its changes become visible at `commit()`, or are dropped by `rollback()`. Transactions are optimistic: a commit fails with a `JsonException` when the database was modified after the transaction began. A failed commit leaves the transaction open, to be rolled back; a committed or rolled back transaction cannot be used any more.

Sample: `doc_examples/memdb/MemoryDbExamples.java` (`testTransactions`)

```java
MemoryJsonDb.Transaction tx = db.beginTransaction();
tx.insert(JsonKey.of("c", "k2"), JsonObject.of("v", 2));
tx.delete(JsonKey.of("c", "k1"));
db.exists(JsonKey.of("c", "k1"));   // -> true: not visible until committed
tx.commit();

MemoryJsonDb.Transaction tx3 = db.beginTransaction();
tx3.insert(JsonKey.of("c", "k4"), JsonObject.of("v", 4));
db.insert(JsonKey.of("c", "k5"), JsonObject.of("v", 5));
tx3.commit();                       // JsonException: Database has been modified since the transaction started
tx3.rollback();
```

The transaction copies the record maps when it begins, so its cost grows with the size of the database. Any change to the database after the transaction began, `clear()` included, makes its `commit()` fail.

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

`JsonDbTarget` is transactional: an import runs in a transaction and is rolled back if it fails, or is committed in chunks with `transactionThreshold(n)` (see [Transactions](/GaltaJSON/Modules/ImportExport#transactions)). A `JsonDbSource` also streams the tombstones, as deletions. It streams a snapshot taken when the stream starts, so the database can be changed while it is exported, even by the target of the same export. Its `estimatedCount()` is the number of records and tombstones it streams (of its collection, if any).

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

A replication runs in a transaction on the target: a conflict that the resolver rejects rolls it back entirely. `ReplicationResult.toJson()` summarizes a run (duration, time range, counts).
