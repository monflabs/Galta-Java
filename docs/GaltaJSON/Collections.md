# Collections & Streams

`JsonArray` has a set of stream-like operations (`filter`, `map`, `sorted`, `slice`, ...) that work directly on the array and return new arrays. Containers can also search their whole tree with `find`, and, being a `List` and a `Map`, they work with Java streams, for which `JsonCollectors` and `CsvMapping` provide JSON-specific collectors and mappers.

## Operations return copies

Every operation that returns an array returns a *new* array, even when nothing changed, so the source is never modified and the result can be modified freely. The exception is `peek(action)`, which returns the array itself. The predicates and functions receive the raw item (`Object`); `JsonUtil.asInt(v)` and friends (see [Values](/GaltaJSON/Values#the-as-conversions)) convert it without casts.

Sample: `doc_examples/json/CollectionsExamples.java` (`testCopies`)

```java
JsonArray a = JsonArray.of(5, 3, 8, 1, 3);

JsonArray big = a.filter(v -> JsonUtil.asInt(v) > 2);      // [5,3,8,3]
JsonArray tens = a.map(v -> JsonUtil.asInt(v) * 10);       // [50,30,80,10,30]
JsonArray sorted = a.sorted();                             // [1,3,3,5,8]
a.stringify();                                             // [5,3,8,1,3]: the source is untouched
a.filter(v -> true) != a;                                  // true: always a new array
a.peek(v -> {}) == a;                                      // true: except peek()

a.removeIf(v -> JsonUtil.asInt(v) == 3);                   // Collection.removeIf modifies the array
a.stringify();                                             // [5,8,1]
```

| Operation | Result |
|---|---|
| `filter(predicate)` | The items that match |
| `reject(predicate)` | The items that do *not* match |
| `map(function)` | The function applied to each item |
| `takeWhile(predicate)`, `dropWhile(predicate)` | The leading matching items, or everything after them |
| `slice(start, end)`, `slice(start, end, step)` | Python-style slice: `end` exclusive, negative values from the end, a negative step walks backwards; pass `null` (the `Integer` overloads) for a default bound |
| `skip(n)`, `limit(n)`, `skipLimit(skip, limit)` | Leading items dropped or kept |
| `sorted()`, `sorted(false)`, `sorted(comparator)` | Sorted with `JsonUtil.compare`, descending, or a custom order |
| `distinct()`, `distinct(comparator)`, `distinct(keyFunction)` | The first item of each group of equal items |
| `flat()` | Nested arrays and objects replaced by their items, one level deep |
| `peek(action)` | Calls the action on each item and returns the array itself |

Sample: `doc_examples/json/CollectionsExamples.java` (`testSlicing`)

```java
JsonArray a = JsonArray.of(0, 1, 2, 3, 4, 5);

a.slice(1, 3);                // [1,2]
a.slice(-2, null);            // [4,5]
a.slice(0, 6, 2);             // [0,2,4]
a.slice(null, null, -1);      // [5,4,3,2,1,0]
a.skip(2);                    // [2,3,4,5]
a.limit(2);                   // [0,1]
a.skipLimit(1, 3);            // [1,2,3]
a.takeWhile(v -> JsonUtil.asInt(v) < 3);        // [0,1,2]
a.dropWhile(v -> JsonUtil.asInt(v) < 3);        // [3,4,5]
a.reject(v -> JsonUtil.asInt(v) % 2 == 0);      // [1,3,5]: a copy without the matches
a.size();                     // 6
```

### Sorting and distinct values

`sorted()`, `min()` and `max()` use the JSON order of [`JsonUtil.compare`](/GaltaJSON/Values#ordering): numbers by value across types, and across types `null` < array < object < string < number < boolean. `distinct()` uses JSON equality, so `1` and `1.0` are duplicates. `distinct(keyFunction)` puts the keys in a `HashSet`, so the keys use the Java `equals()`: the keys `1` and `1.0` (an `Integer` and a `Double`) are different, while two equal containers are the same key. `JsonUtil.objectComparator()` builds a comparator on object members, each ascending or descending.

Sample: `doc_examples/json/CollectionsExamples.java` (`testSortingAndDistinct`)

```java
JsonArray a = JsonArray.of(3, 1.0, "b", 1, 2L, "a");
a.sorted(false);        // [3,2,1,1,"b","a"]
a.distinct();           // [3,1,"b",2,"a"]: 1.0 and 1 are the same number
a.min();                // "a": strings sort before numbers
a.max();                // 3

JsonArray n = JsonArray.of(3, 1.0, 1, 2L);
n.min();                // 1.0: the first of the smallest
n.max();                // 3

JsonArray books = JsonArray.parse("""
    [ {"cat":"fiction","price":12}, {"cat":"reference","price":9},
      {"cat":"fiction","price":8} ]
    """);
JsonArray byCatThenPrice = books.sorted(JsonUtil.objectComparator().add("cat").add("price", false));
byCatThenPrice.map(b -> ((JsonObject)b).get("price"));     // [12,8,9]
books.distinct(b -> ((JsonObject)b).get("cat")).size();    // 2
```

### Aggregates

`reduce(function, initial)` folds the items; `reduce(reducer)` takes a ready-made `Reducer` from `Reducers` (`sumInt()`, `sumLong()`, `sumDouble()`, `sumBigInteger()`, `sumBigDecimal()`, which throw on a non-number). `anyMatch`, `allMatch` and `noneMatch` test the items, `min()` / `max()` return the smallest and largest (or `null` for an empty array), `join(separator)` concatenates the items as text, and `process(function)` applies a function to the array itself, to continue a chain.

Sample: `doc_examples/json/CollectionsExamples.java` (`testAggregates`)

```java
JsonArray a = JsonArray.of(1, 2, 3, 4);

Integer sum = a.reduce((Integer acc, Object v) -> acc + JsonUtil.asInt(v), 0);   // 10
a.reduce(Reducers.sumInt());                   // 10
a.reduce(Reducers.sumDouble());                // 10.0
a.anyMatch(v -> JsonUtil.asInt(v) > 3);        // true
a.allMatch(v -> v instanceof Integer);         // true
a.noneMatch(v -> v == null);                   // true
a.join(',');                                   // "1,2,3,4"
a.process(arr -> arr.size());                  // 4
JsonArray.create().min();                      // null
```

### Flattening

`flat()` replaces each nested array by its items and each nested object by its values, one level deep. On an object, `flat()` returns its values.

Sample: `doc_examples/json/CollectionsExamples.java` (`testFlat`)

```java
JsonArray nested = JsonArray.parse("[[1,2],3,{\"a\":4,\"b\":[5]}]");
nested.flat();                                         // [1,2,3,4,[5]]: one level only
JsonObject.parse("{\"a\":4,\"b\":[5]}").flat();        // [4,[5]]
```

## Find and findAndSet

On any container, `find(key)` collects, in document order, the value of every member named `key` anywhere in the tree, and `find(index)` the item at that index (negative from the end) of every array in the tree. With `deep` set to `false` (`find(key, false)`), the search does not descend into a value that matched. `find(null)` collects every value of the tree, like `$..*` in [JSON Path](/GaltaJSON/JsonPath).

`findAndSet(key, value)` and `findAndSet(index, value)` replace those values in place; they do not descend into a value they replaced.

Sample: `doc_examples/json/CollectionsExamples.java` (`testFind`)

```java
JsonObject o = JsonObject.parse("""
    { "id": 1, "child": { "id": 2, "child": { "id": 3 } }, "list": [ { "id": 4 } ] }
    """);
o.find("id");                    // [1,2,3,4]

// Not deep: stop descending into a value once it matched
JsonObject nested = JsonObject.parse("{\"a\":{\"a\":{\"a\":1}}}");
nested.find("a").size();         // 3
nested.find("a", false);         // [{"a":{"a":1}}]

// By index, in every array
JsonObject matrix = JsonObject.parse("{\"m\":[[1,2],[3,4]]}");
matrix.find(0);                  // [[1,2],1,3]
matrix.find(-1, false);          // [[3,4],2]

// A null key matches every value, like $..*
JsonObject.parse("{\"a\":1,\"b\":{\"c\":2}}").find(null);   // [1,{"c":2},2]
```

Sample: `doc_examples/json/CollectionsExamples.java` (`testFindAndSet`)

```java
JsonObject o = JsonObject.parse("{\"id\":1,\"child\":{\"id\":2},\"list\":[{\"id\":3}]}");
o.findAndSet("id", 0);      // in place
// {"id":0,"child":{"id":0},"list":[{"id":0}]}
```

[`JsonValues`](/GaltaJSON/JsonPath#stream-like-operations) has the same `find` and `findAndSet`, and its own versions of the stream-like operations.

## Java streams

`stream()` and `parallelStream()` work as on any `List`. `JsonCollectors.toJsonArray()` collects into a new `JsonArray` of the default factory; `toJsonArray(factory)` uses another factory and `toJsonArray(array)` appends to an existing array. `JsonCollectors.toJsonArrayValues()` collects a stream of `JsonValues` into a `JsonValues` holding a new array. The collectors support parallel streams. The collectors that create their array can be kept and reused, each collection giving a new array; the ones given a target array append to that same array each time they are used, so they are meant for a single collection.

Sample: `doc_examples/json/CollectionsExamples.java` (`testJavaStreams`)

```java
JsonArray a = JsonArray.of(5, 3, 8, 1);

JsonArray evens = a.stream()
    .filter(v -> JsonUtil.asInt(v) % 2 == 0)
    .collect(JsonCollectors.toJsonArray());          // [8]

JsonArray target = JsonArray.of("first");
a.stream().map(v -> "n" + v).collect(JsonCollectors.toJsonArray(target));   // appends
// target: ["first","n5","n3","n8","n1"]

List<Object> list = a.parallelStream().sorted().collect(Collectors.toList());   // [1, 3, 5, 8]
a.parallelStream().collect(JsonCollectors.toJsonArray());                     // [5,3,8,1]: order kept

JsonValues titles = JsonValues.of(JsonArray.parse("[{\"t\":\"a\"},{\"t\":\"b\"}]")).flat().get("t");
JsonValues collected = java.util.stream.StreamSupport.stream(titles.spliterator(), false)
    .collect(JsonCollectors.toJsonArrayValues());   // ["a","b"]
```

## CSV lines

`CsvMapping.toCsvStrings()` returns a `Function` that turns a row into one CSV line, following RFC 4180: a field that contains the separator, a double quote, a carriage return or a line feed is enclosed in double quotes, and its double quotes are doubled. A field that starts or ends with a space or a tab is quoted as well. A row is a `List` (such as a `JsonArray`) or another `Collection`, a Java array, or a `Map` such as a `JsonObject` (its values, in order); a `null` row gives an empty line and any other row throws a `JsonException`. Each cell is written with `toString()`, except the numbers that are written as in JSON (a `Double` `4.0` gives `4`, `1e10` gives `10000000000`) and the JSON objects and arrays that are written as their compact JSON text; `null` gives an empty field.

`toCsvStrings(separator, quoteStrategy)` sets the field separator and the `QuoteStrategy`:

| `QuoteStrategy` | Quoted fields |
|---|---|
| `REQUIRED` (default) | Only the fields that need it |
| `EMPTY` | Also empty strings, to tell them from `null` (as PostgreSQL's CSV import expects) |
| `ALWAYS` | Every field, including numbers, booleans and `null` |

`toCsvStrings(separator, quoteStrategy, true)` also protects against CSV injection: a text cell starting with `=`, `+`, `-`, `@`, a tab or a carriage return is prefixed with a single quote, so a spreadsheet doesn't evaluate it as a formula (numbers are left alone). It is off by default.

The function reuses internal buffers: create one per stream and do not share it between threads.

Sample: `doc_examples/json/CollectionsExamples.java` (`testCsv`)

```java
JsonArray rows = JsonArray.parse("""
    [ ["id", "name", "note"],
      [1, "Ada", "says \\"hi\\""],
      [2, "Grace", "a,b"],
      [3, "", null],
      [4.0, " padded", true] ]
    """);

JsonArray lines = rows.stream()
    .map(CsvMapping.toCsvStrings())
    .collect(JsonCollectors.toJsonArray());
// id,name,note
// 1,Ada,"says ""hi"""
// 2,Grace,"a,b"
// 3,,
// 4," padded",true

JsonArray semicolons = rows.stream()
    .map(CsvMapping.toCsvStrings(';', QuoteStrategy.EMPTY))
    .collect(JsonCollectors.toJsonArray());
semicolons.get(2);          // 2;Grace;a,b
semicolons.get(3);          // 3;"";

JsonArray quoted = rows.stream()
    .map(CsvMapping.toCsvStrings(',', QuoteStrategy.ALWAYS))
    .collect(JsonCollectors.toJsonArray());
quoted.get(3);              // "3","",""
```

Objects are mapped to rows first, choosing the columns:

Sample: `doc_examples/json/CollectionsExamples.java` (`testCsvFromObjects`)

```java
JsonArray people = JsonArray.parse("[{\"id\":1,\"name\":\"Ada\"},{\"id\":2,\"name\":\"Grace\"}]");
List<String> lines = people.stream()
    .map(p -> List.of(((JsonObject)p).get("id"), ((JsonObject)p).get("name")))
    .map(CsvMapping.toCsvStrings())
    .collect(Collectors.toList());
// [1,Ada, 2,Grace]

// An object is a row of its values, in order
CsvMapping.toCsvStrings().apply(people.get(0));    // "1,Ada"
// A Java array is a row
CsvMapping.toCsvStrings().apply(new Object[] {1, "a"});   // "1,a"
// A container cell is compact JSON, quoted as needed
CsvMapping.toCsvStrings().apply(List.of(1, JsonArray.of(1, 2)));    // 1,"[1,2]"
// Formula injection protection (off by default)
CsvMapping.toCsvStrings(',', QuoteStrategy.REQUIRED, true).apply(List.of("=SUM(A1)", -1));   // '=SUM(A1),-1
```

For reading and writing CSV files, see [Import & Export](/GaltaJSON/Modules/ImportExport).

## Gotchas

- Like `filter()`, `reject()` returns a new array and leaves the array unchanged. Use `removeIf` (from `Collection`) to remove the matching items from the array itself.
- `sorted()` orders mixed types by type first; strings come before numbers. A non-JSON value (a Java object stored with `addValue`) sorts after the booleans, and two of them can't be compared (`IllegalStateException`).
- `contains(v)`, `indexOf(v)` and `remove(Object)` are the `List` methods and use the Java `equals()` on the items: `JsonArray.of(1).contains(1.0)` is `false`, while `JsonArray.of(1).equals(JsonArray.of(1.0))` (JSON equality) is `true`. Use `anyMatch(x -> JsonUtil.eq(x, v))` for a JSON comparison.
- `factory()` on a `JsonObjectAsLinkedMap`/`JsonArrayAsArrayList` is `JavaJsonFactory.instance` (`JavaJsonFactoryChecked.instance` for the checked ones), even when the container was created by a subclass of these factories: the containers created from it (`getOrCreateObject`, `deepClone`, `filter`...) come from that factory.
- `find(key, false)` still searches the whole tree, only not below a match.
- Numbers in CSV use the JSON text, not Java's `toString()`: `4.0` is written `4`.

## Source

`JsonArray.java`, `JsonContainer.java` (`find`, `findAndSet`), `java/JsonArrayAsArrayList.java`, `JsonUtil.java` (`compare`, `objectComparator`), `util/Reducers.java`, `util/JsonCollectors.java`, `stream/CsvMapping.java`, under `parent-json/json/src/main/java/org/monflabs/json/`.
