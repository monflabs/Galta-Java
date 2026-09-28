# Import & Export

The `json-impexp` module moves JSON documents between stores: folders of files, zip files, in-memory JSON containers, Java streams, CSV files (with `json-impexp-fastcsv`) or the in-memory database of [Memory Database](/GaltaJSON/Modules/MemoryDb). Any source can be exported to any target, and targets that can read back what they hold can also *replicate*: receive only the changes made since the last run, with conflict detection.

```xml
<dependency>
    <groupId>org.monflabs.galta</groupId>
    <artifactId>json-impexp</artifactId>
</dependency>
<!-- CSV source and target -->
<dependency>
    <groupId>org.monflabs.galta</groupId>
    <artifactId>json-impexp-fastcsv</artifactId>
</dependency>
```

## The model

| Concept | Type | Role |
|---|---|---|
| Document | `JsonContent` | A key, a JSON value (any value, not only objects), an optional timestamp, and a type: `RECORD` or `DELETION`. |
| Key | `JsonKey` | A *collection* name and an *id*. The collection groups documents, like a table or a folder; it may be empty. |
| Source | `JsonSource` | Streams documents: `stream()`, `estimatedCount()` (`-1` when unknown), `exportTo(target)`. |
| Target | `JsonTarget` | Receives them: `importFrom(source)` returns an `ImportResult` (inserted, deleted, duration). |

`StaticContent(key, json, timestamp)` and `StaticDeletedContent(key)` are ready-made documents.

Sample: `doc_examples/impexp/ImportExportExamples.java` (`testJsonKey`)

```java
JsonKey key = JsonKey.of("customers", "c-42");
key.keyString();                        // -> "customers!!c-42"
JsonKey.parse("customers!!c-42");       // the same key
JsonKey.of(null, "c-42").keyString();   // -> "c-42": no collection
```

## A first export

Sample: `doc_examples/impexp/ImportExportExamples.java` (`testContainerToFiles`)

```java
JsonObject data = JsonObject.parse("""
        {
          "customers": { "c1": { "name": "Ada" }, "c2": { "name": "Alan" } },
          "orders":    { "o1": { "customer": "c1", "total": 12.5 } }
        }
        """);
JsonContainerSource source = JsonContainerSource.newBuilder()
        .format(JsonInMemoryFormat.RECORDSBYCOLKEY)
        .container(data)
        .build();

FileTarget target = FileTarget.newBuilder()
        .root(root.toFile())                     // cleared first, by default!
        .build();

ImportResult result = source.exportTo(target);   // same as target.importFrom(source)
// result.getInserted() -> 3
// files: @customers/c1.json, @customers/c2.json, @orders/o1.json

// And back
FileSource files = FileSource.newBuilder().root(root.toFile()).build();
JsonContainerTarget memory = JsonContainerTarget.newBuilder()
        .format(JsonInMemoryFormat.RECORDSBYCOLKEY)
        .build();
memory.importFrom(files);
// memory.getContainer() equals data
```

A source can be exported several times: each export opens it again.

## Sources and targets

| Source | Reads | Key |
|---|---|---|
| `JsonContainerSource` | A `JsonArray` or `JsonObject` in one of the in-memory formats below. | From the format, or `keyFunction` / `collectionFunction`. |
| `StreamSource` | A `Supplier<Stream<Object>>`; `valueFunction` can transform each item. | `keyFunction` / `collectionFunction`; the index by default. |
| `JsonContentStreamSource` | A `Supplier<Stream<JsonContent>>` of ready-made documents. | The documents' keys. |
| `FileSource` | `.json` files under a folder, sub-folders included. | File name; collection from a top-level `@name` folder. |
| `ZipFileSource` | `.json` entries of a zip file (`__MACOSX/` is skipped). | Same layout as `FileSource`. |
| `ZipInputStreamSource` | Same, from a `Supplier<InputStream>`. | Same. |
| `CsvSource` | CSV rows, see [CSV](#csv). | `keyFunction` / `collectionFunction`; the row index by default. |
| `JsonDbSource` | A `MemoryJsonDb`, see [Memory Database](/GaltaJSON/Modules/MemoryDb). | The records' keys. |

| Target | Writes | Deletions |
|---|---|---|
| `JsonContainerTarget` | A `JsonArray` or `JsonObject` (new, or given with `container(...)`). | Yes, except the `RECORDS` and `RECORDSBYCOL` formats, which have no key. |
| `FileTarget` | One `.json` file per document. | Yes, deletes the file. |
| `ZipTarget` | One entry per document in a new zip file. | Ignored. |
| `PojoTarget` | Calls your `writer(Consumer<JsonContent>)`, with optional `init` / `close` callbacks. | Passed to the writer. |
| `StringDumpTarget` | A text dump (`getString()`), handy in tests. | Yes. |
| `CsvTarget` | CSV rows, see [CSV](#csv). | Ignored. |
| `JsonDbTarget` | A `MemoryJsonDb`, with transactions and replication. | Yes. |

### In-memory formats

`JsonContainerSource` and `JsonContainerTarget` share the `JsonInMemoryFormat` enum:

| Format | Shape | Key |
|---|---|---|
| `RECORDS` (default) | `[ v1, v2, ... ]` | Functions, or the index (`"0"`, `"1"`...). |
| `RECORDSWITHKEYS` | `[ { "collection": "c", "id": "k", "value": v }, ... ]` | In the entries. A target replaces the value of an entry that already has the key. |
| `RECORDSBYCOL` | `{ "c1": [ v1, v2 ], "c2": [ ... ] }` | Collection from the property; id from `keyFunction`, or the index. |
| `RECORDSBYKEY` | `{ "k1": v1, "k2": v2 }` | Id from the property; collection from `collectionFunction`. |
| `RECORDSBYCOLKEY` | `{ "c1": { "k1": v1 }, "c2": { ... } }` | Both from the properties. |

A target drops what its format cannot hold: exporting to `RECORDSBYKEY` loses the collection, exporting to `RECORDS` loses both.

Sample: `doc_examples/impexp/ImportExportExamples.java` (`testInMemoryFormats`, `testDefaultKeys`)

```java
JsonArray people = JsonArray.parse("""
        [ { "id": "p1", "team": "red", "name": "Ada" },
          { "id": "p2", "team": "blue", "name": "Alan" } ]
        """);
JsonContainerSource source = JsonContainerSource.newBuilder()
        .format(JsonInMemoryFormat.RECORDS)
        .container(people)
        .collectionFunction(o -> ((JsonObject)o).getString("team"))
        .keyFunction(o -> ((JsonObject)o).getString("id"))
        .build();

JsonContainerTarget byCol = JsonContainerTarget.newBuilder()
        .format(JsonInMemoryFormat.RECORDSBYCOL)
        .build();
source.exportTo(byCol);
// { "red": [ {"id":"p1",...} ], "blue": [ {"id":"p2",...} ] }

JsonContainerTarget withKeys = JsonContainerTarget.newBuilder()
        .format(JsonInMemoryFormat.RECORDSWITHKEYS)
        .build();
source.exportTo(withKeys);
// [ { "collection": "red", "id": "p1", "value": {...} }, ... ]
```

### Files and zips

On disk, a document is a file named after its id plus `.json`, inside a `@<collection>` folder when it has a collection. Characters that are not valid in file names (`/ \ : * ? " < > |`, `%` and control characters), as well as `@`, are encoded as `%XX`, and decoded when reading. The same encoding applies to collection names, so a collection name such as `a/b` or `../x` stays a single folder under the root.

| `FileTarget` option | Default | Meaning |
|---|---|---|
| `root(File)` | | The folder. |
| `clearOnStart(boolean)` | `true` | **Delete everything under the root** before writing. Set it to `false` to add to an existing folder. |
| `subdir(int levels)` | `0` | Spread the files in 1 to 4 levels of sub-folders named after a hash of the id (`@docs/3F/A0/id.json`), for large sets. |
| `keepTimestamp(boolean)` | `false` | Set the file's modification date to the document timestamp. |
| `ignoreCollection(boolean)` | `false` | Write every document at the root, without `@collection` folders. |

`ZipTarget` has `root(File)` (the zip file, overwritten), `subdir` and `ignoreCollection`. `FileSource`, `ZipFileSource` and `ZipInputStreamSource` take `ignoreCollection` and `estimateCount` (count the files up front, for progress reporting). `FileSource` reads the files of a folder in name order. A file document's timestamp is the file's modification date, or the zip entry's date (none when the entry has no date).

Sample: `doc_examples/impexp/ImportExportExamples.java` (`testFileTargetOptions`, `testZip`)

```java
FileTarget target = FileTarget.newBuilder()
        .root(root.toFile())
        .clearOnStart(false)          // keep what is there
        .subdir(2)                    // 2 levels of hashed folders
        .keepTimestamp(true)          // file date = content timestamp
        .build();
// JsonKey.of("docs", "a/b:c") -> @docs/<h1>/<h2>/a%2Fb%3Ac.json

FileSource source = FileSource.newBuilder().root(root.toFile()).build();
// reads it back as JsonKey.of("docs", "a/b:c"), with the file date as timestamp
```

```java
ZipTarget target = ZipTarget.newBuilder()
        .root(zip.toFile())
        .build();
source.exportTo(target);                  // entries @c/k1.json, @c/k2.json

ZipFileSource zipSource = ZipFileSource.newBuilder()
        .zipFile(zip.toFile())
        .estimateCount(true)
        .build();
zipSource.estimatedCount();               // -> 2
```

## Controlling an import

Every target built on `JsonTargetImpl` (all of the above) accepts these builder options:

| Option | Meaning |
|---|---|
| `beforeProcessing(Function<JsonContent,JsonContent>)` | Transform a document before it is written; return `null` to skip it. |
| `afterProcessing(Consumer<JsonContent>)` | Called after a document is written. Throwing aborts the import. |
| `notification(Notification)` | Progress events: `START`, `PROCESS` (at most every `notificationDelay` ms, 2000 by default), `CANCEL`, `END`. `JsonTargetImpl.consoleLogger` logs them; extend `TextNotification` to route the messages elsewhere. |
| `estimatedCount(LongSupplier)` | The expected count, for the notifications (usually `source::estimatedCount`). |
| `transactionThreshold(int)` | For transactional targets: commit every N documents, see [Transactions](#transactions). |
| `replicationTable(ReplicationTable)` | Enables replication, see below. |

The counts of the `ImportResult` include the documents skipped by `beforeProcessing`.

`JsonTargetImpl.cancel()` stops a running import or replication, from any thread or from a callback: the engine stops before the next document, rolls back the pending transaction, closes the target, and the import throws a `CancelException` (the `CANCEL` notification is sent first).

Sample: `doc_examples/impexp/ImportExportExamples.java` (`testStreamSourceAndPojoTarget`)

```java
List<Object> rows = List.of(JsonObject.of("sku", "A", "qty", 1), JsonObject.of("sku", "B", "qty", 0));
StreamSource source = StreamSource.newBuilder()
        .streamFactory(rows::stream)
        .keyFunction(o -> ((JsonObject)o).getString("sku"))
        .valueFunction(o -> JsonObject.of("quantity", ((JsonObject)o).getInt("qty")))
        .build();

List<String> seen = new ArrayList<>();
PojoTarget<Void> target = PojoTarget.<Void>newBuilder()
        .writer(c -> seen.add(c.getKey().getId() + "=" + ((JsonObject)c.getJson()).getInt("quantity")))
        .beforeProcessing(c -> ((JsonObject)c.getJson()).getInt("quantity") > 0 ? c : null)   // null skips it
        .build();
ImportResult r = source.exportTo(target);
// seen -> ["A=1"], r.getInserted() -> 2
```

A source of `JsonContent` can carry deletions, which the targets that support them apply:

Sample: `doc_examples/impexp/ImportExportExamples.java` (`testDeletions`)

```java
JsonObject store = JsonObject.parse("{ \"k1\": 1, \"k2\": 2 }");
JsonContainerTarget target = JsonContainerTarget.newBuilder()
        .format(JsonInMemoryFormat.RECORDSBYKEY)
        .container(store)                         // update an existing container
        .build();

List<JsonContent> changes = List.of(
        new StaticContent(JsonKey.of(null, "k3"), 3, null),
        new StaticDeletedContent(JsonKey.of(null, "k1")));
target.importFrom(JsonContentStreamSource.newBuilder().streamFactory(changes::stream).build());
// store -> {"k2":2,"k3":3}
```

When anything fails, the import stops, the pending transaction is rolled back, the target is closed, and the error is rethrown as a `JsonException`.

Sample: `doc_examples/impexp/ImportExportExamples.java` (`testNotificationAndFailure`)

## Transactions

A target can be transactional; among the provided ones, only `JsonDbTarget` is. By default the whole import runs in one transaction: if it fails, nothing is written. `transactionThreshold(n)` commits every `n` documents instead, so a failure only rolls back the current chunk; `0` disables transactions.

Sample: `doc_examples/memdb/MemoryDbExamples.java` (`testImportIsTransactional`)

```java
JsonDbTarget target = JsonDbTarget.newBuilder()
        .db(db)
        .afterProcessing(c -> {
            if(c.getKey().getId().equals("2")) {
                throw new IllegalStateException("boom");
            }
        })
        .build();
// importing ["a","b","c","d"] fails on the 3rd document: db.count() -> 0

JsonDbTarget chunked = JsonDbTarget.newBuilder()
        .db(db)
        .transactionThreshold(2)
        .afterProcessing(/* same */)
        .build();
// the same import keeps the first chunk: db.count() -> 2
```

A custom target becomes transactional by overriding `supportsTransaction()`, `startTransaction()`, `commitTransaction()` and `rollbackTransaction()` of `JsonTargetImpl`.

## Replication

Replication is an incremental import between a `ReplicationSource` and a `ReplicationTarget`, each identified by a replication id. A `ReplicationTable` records when the pair was last replicated; the next run only streams what the source changed since then, deletions included. The in-memory database provides both ends (`JsonDbSource`, `JsonDbTarget`) and `MemoryReplicationTable` keeps the history in memory; another store can implement the same interfaces.

Sample: `doc_examples/memdb/MemoryDbExamples.java` (`testReplication`)

```java
JsonDbSource source = JsonDbSource.newBuilder().db(master).build();
JsonDbTarget target = JsonDbTarget.newBuilder()
        .db(replica)
        .replicationTable(new MemoryReplicationTable())   // remembers the last replication
        .build();

ReplicationResult r1 = target.replicate(source, ConflictResolver.FAIL_EXCEPTION);
// r1.getInserted() -> 2: everything, the first time

master.update(JsonKey.of("c", "k1"), JsonObject.of("v", 10));
master.delete(JsonKey.of("c", "k2"));
ReplicationResult r2 = target.replicate(source, ConflictResolver.FAIL_EXCEPTION);
// r2.getInserted() -> 1, r2.getDeleted() -> 1
```

### Conflicts

For each incoming document, the target reads its own version. There is a conflict when it has one (a record or a deletion) that was changed at or after the last replication, or when the pair was never replicated. Two cases are not conflicts and are counted as *ignored*: both sides deleted the document, or both hold equal values. A real conflict is counted in `getConflicts()` and handed to the `ConflictResolver`. Each incoming document is counted once: the documents the resolver returns are saved, but not counted again as inserted or deleted, so `getProcessed()` is the number of incoming documents:

| Resolver | Result |
|---|---|
| `FAIL_EXCEPTION` (also used for `null`) | Throw a `JsonException`: the replication is rolled back and the last replication date is not updated. |
| `NO_ACTION` | Keep the target version, skip the incoming one. |
| `SOURCE_WINS` / `TARGET_WINS` | Keep one side. |
| `NEWER_WINS` / `OLDER_WINS` | Compare the timestamps, a missing one being the oldest; ties go to the source. |
| A lambda `(source, target) -> JsonContent[]` | Return the documents to save, possibly several, or `null` / an empty array to save nothing. |

Sample: `doc_examples/memdb/MemoryDbExamples.java` (`testConflicts`, `testCustomResolver`)

```java
// Both sides changed c:k since the last replication
target.replicate(source, null);                       // JsonException: Replication conflict on c:k

ReplicationResult r = target.replicate(source, ConflictResolver.SOURCE_WINS);
// r.getConflicts() -> 1, the replica now holds the master's version

// Keep both versions
target.replicate(source, (src, tgt) -> new JsonContent[] {
        new StaticContent(JsonKey.of("c", "k"), src.getJson(), src.getTimestamp()),
        new StaticContent(JsonKey.of("conflicts", "k"), tgt.getJson(), tgt.getTimestamp())
});
```

The changes are selected by time, with millisecond resolution. The range includes its lower bound, so a document changed in the same millisecond as the last replication can be sent twice rather than missed.

## CSV

`json-impexp-fastcsv` reads and writes CSV with [FastCSV](https://github.com/osiegmar/FastCSV). A CSV row is a flat `JsonObject`, one property per column.

### `CsvSource`

With a header row (the default), every header column becomes a property, in order. Columns declared with `column(name, cellReader)` only add a conversion: without one, cells are strings. The reader supplier is called for each import.

Sample: `doc_examples/csv/CsvExamples.java` (`testCsvWithHeader`)

```java
String csv = """
        id,name,price,since
        p1,Pen,1.20,2024-01-15
        p2,Paper,3.5,2023-06-01
        """;
CsvSource source = CsvSource.newBuilder()
        .reader(() -> new StringReader(csv))                // called for each import
        .column("price", s -> new BigDecimal(s))            // cell readers for some columns
        .keyFunction(row -> (String)row.get("id"))
        .collectionFunction(row -> "products")
        .timestampFunction(row -> LocalDate.parse((String)row.get("since"))
                .atStartOfDay().toInstant(ZoneOffset.UTC))
        .build();
// products/p1 -> {"id":"p1","name":"Pen","price":1.20,"since":"2024-01-15"}
```

The key, collection and timestamp functions receive a `Row` whose `get(name)` returns the converted cell. Without a key function, the key is the row index, from `0`.

Without a header, `firstRowAsHeader(false)`, the declared columns are the columns, by position; declaring none is an error. Rows may be shorter than the declared columns, the missing cells being `null`, and cells beyond them are ignored.

Sample: `doc_examples/csv/CsvExamples.java` (`testCsvWithoutHeader`, `testNoColumnsFails`)

```java
String csv = """
        p1;Pen;12
        p2;Paper
        p3;Ink;7;extra
        """;
CsvSource source = CsvSource.newBuilder()
        .reader(() -> new StringReader(csv))
        .firstRowAsHeader(false)
        .fieldSeparator(';')
        .column("id")                                       // declared columns, by position
        .column("name")
        .column("stock", Integer::valueOf)
        .keyFunction(row -> (String)row.get("id"))
        .build();
// p1 -> {"id":"p1","name":"Pen","stock":12}
// p2 -> {"id":"p2","name":"Paper","stock":null}            short row
// p3 -> {"id":"p3","name":"Ink","stock":7}                 extra cell ignored
```

A leading UTF-8 byte order mark is skipped, so it does not end up in the first column name. Empty rows are skipped. An empty cell is an empty string, and a cell reader receives it too; `emptyAsNull(true)` reads it as `null` instead (the cell reader is then not called). A header that repeats a column name is an error:

Sample: `doc_examples/csv/CsvExamples.java` (`testByteOrderMarkAndEmptyCells`)

```java
String csv = "﻿id,qty\nA,\nB,5\n";
CsvSource source = CsvSource.newBuilder()
        .reader(() -> new StringReader(csv))
        .column("qty", s -> s.isEmpty() ? null : Integer.valueOf(s))   // readers see "" for an empty cell
        .keyFunction(row -> (String)row.get("id"))
        .build();
// A -> {"id":"A","qty":null}
```

`estimateCount(true)` counts the data rows up front (parsing the input once more, so the header, empty rows and line breaks inside quoted cells are not counted) for progress reporting.

### `CsvTarget`

Without declared columns, the columns are the properties of the first document; later documents fill them and missing values become empty cells, but a later document with a property the first one does not have is an error (the header is already written): declare the columns to export heterogeneous documents. Declared columns are a projection, other properties are ignored. Values are converted to text, objects and arrays as JSON; `null` is an empty cell. The writer is closed at the end of the export, unless `closeWriter(false)` (it is then flushed). Deletions are ignored.

Sample: `doc_examples/csv/CsvExamples.java` (`testCsvTargetInferredColumns`)

```java
// [ { "id": "p1", "name": "Pen", "price": 1.2, "tags": ["a","b"] },
//   { "id": "p2", "name": "Paper, A4" } ]
StringWriter out = new StringWriter();
CsvTarget target = CsvTarget.newBuilder()
        .writer(() -> out)                                  // closed at the end of the export
        .build();
source.exportTo(target);
// id,name,price,tags
// p1,Pen,1.2,"[""a"",""b""]"
// p2,"Paper, A4",,
```

Lines end with `\r\n`. Declared columns fix the list and order; a column with a cell writer computes its text from the whole `JsonContent`, key included, and does not need a matching property. `columnsFromSchema(schema)` declares one column per property of a JSON Schema object.

Sample: `doc_examples/csv/CsvExamples.java` (`testCsvTargetDeclaredColumns`, `testCsvTargetFromSchema`)

```java
CsvTarget target = CsvTarget.newBuilder()
        .writer(() -> out)
        .fieldSeparator(';')
        .column("key", c -> c.getKey().getId())            // cell writers receive the JsonContent
        .column("price")
        .column("total", c -> {
            JsonObject o = (JsonObject)c.getJson();
            return String.valueOf(o.getDouble("price") * o.getInt("qty"));
        })
        .build();
// key;price;total
// p1;1.2;3.5999999999999996
```

| Option | Source | Target | Default |
|---|---|---|---|
| `reader(Supplier<Reader>)` / `writer(Supplier<Writer>)` | yes | yes | |
| `firstRowAsHeader(boolean)` | yes | yes | `true` |
| `fieldSeparator(char)` | yes | yes | `,` |
| `column(name[, cellReader / cellWriter])` | yes | yes | |
| `columnsFromSchema(JsonObject)` | | yes | |
| `keyFunction`, `collectionFunction`, `timestampFunction` | yes | | row index, none, none |
| `estimateCount(boolean)` | yes | | `false` |
| `emptyAsNull(boolean)` | yes | | `false` |
| `closeWriter(boolean)` | | yes | `true` |
