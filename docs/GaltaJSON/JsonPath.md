# JSON Path

GaltaJSON includes a JSON Path engine for Java: a path is compiled once into a `JsonPath` and evaluated against any value, giving a `JsonValues` result that can be navigated, converted and transformed. The syntax follows the common Goessner / RFC 9535 subset: members, indexes, slices, wildcards, deep scan, unions and filters. Scripts in GaltaJS have their own path operators, built into the language; see [JSON Path in GaltaJS](/GaltaJS/Extensions/JsonPath).

## Compiling and reading

`JsonPathFactory.get().getJsonPath(path)` compiles a path; compiled paths are cached (an LRU cache of 512 entries per factory; `new JsonPathFactory(0)` has none, and `createJsonPath(path)` always compiles). `read(json)` evaluates it. `JsonValues.of(json).path(path)` does both.

The samples on this page use the classic bookstore document, with an extra top-level `"expensive": 10`:

```json
{
  "store": {
    "book": [
      { "category": "reference", "author": "Nigel Rees", "title": "Sayings of the Century", "price": 8.95 },
      { "category": "fiction", "author": "Evelyn Waugh", "title": "Sword of Honour", "price": 12.99 },
      { "category": "fiction", "author": "Herman Melville", "title": "Moby Dick", "isbn": "0-553-21311-3", "price": 8.99 },
      { "category": "fiction", "author": "J. R. R. Tolkien", "title": "The Lord of the Rings", "isbn": "0-395-19395-8", "price": 22.99 }
    ],
    "bicycle": { "color": "red", "price": 19.95 }
  },
  "expensive": 10
}
```

Sample: `doc_examples/json/JsonPathExamples.java` (`testCompileAndRead`)

```java
JsonPath path = JsonPathFactory.get().getJsonPath("$.store.book[*].author");
JsonValues authors = path.read(json);

authors.isList();                   // true
authors._size();                    // 4
authors._get(0);                    // "Nigel Rees"
authors.toJsonArray().stringify();  // ["Nigel Rees","Evelyn Waugh","Herman Melville","J. R. R. Tolkien"]

// The same, starting from a JsonValues
JsonValues.of(json).path("$.store.book[*].author");
```

The following samples use a small helper that returns the matches as a `JsonArray`:

```java
private JsonArray read(String path) {
    return JsonPathFactory.get().getJsonPath(path).read(json).toJsonArray();
}
```

## Syntax

A path starts with `$`, the value it is evaluated on.

| Syntax | Meaning | Example |
|---|---|---|
| `.name`, `['name']`, `["name"]` | Member of an object | `$.store.bicycle`, `$['store']` |
| `.*`, `[*]` | Every item of an array, or every member value of an object | `$.store.book[*]` |
| `..name`, `..*`, `..[...]` | Deep scan: the selector applied to the value and all its descendants | `$..author` |
| `[i]` | Array index, negative from the end | `$..book[-1]` |
| `[start:end:step]` | Slice: `end` exclusive, every part optional, negative values from the end, a negative step walks backwards, a step of `0` selects nothing | `$..book[:2]`, `$..book[::-1]` |
| `[a, b, ...]` | Union of indexes, slices, names, wildcards and filters | `$..book[0,1]`, `$.store.bicycle['color','price']` |
| `[?(expr)]`, `[?expr]` | Filter the array items or object member values | `$..book[?(@.price < 10)]` |

An unquoted member name starts with a letter, `_`, `$` or a non-ASCII character, followed by those, digits or `-` (so `$.a-b` is the key `"a-b"`, but `$.-a` is an error); anything else needs the bracket form. A dotted number such as `.0` is a *member name*: it selects the key `"0"` of an object and nothing in an array; use `[0]` for an index.

### Children and descendants

Sample: `doc_examples/json/JsonPathExamples.java` (`testChildrenAndDescendants`)

```java
read("$.store.bicycle.color");              // ["red"]
read("$['store']['bicycle']['color']");     // ["red"]
read("$..author");                          // the 4 authors
read("$.store..price");                     // [8.95,12.99,8.99,22.99,19.95]
read("$.store.*");                          // 2 values: the book array and the bicycle
read("$.store.book.*");                     // the 4 books: .* flattens arrays too
read("$.store.bicycle..*");                 // ["red",19.95]
read("$..[0].title");                       // ["Sayings of the Century"]: [0] of every array
```

### Indexes and slices

Sample: `doc_examples/json/JsonPathExamples.java` (`testIndexesAndSlices`)

```java
read("$..book[0,1].title");     // ["Sayings of the Century","Sword of Honour"]
read("$..book[:2].title");      // ["Sayings of the Century","Sword of Honour"]
read("$..book[-1].title");      // ["The Lord of the Rings"]
read("$..book[-2:].title");     // ["Moby Dick","The Lord of the Rings"]
read("$..book[::2].title");     // ["Sayings of the Century","Moby Dick"]
read("$..book[::-1].price");    // [22.99,8.99,12.99,8.95]
read("$.store.bicycle['color','price']");   // ["red",19.95]
read("$..book[0,-1:].price");   // [8.95,22.99]: an index and a slice
```

Sample: `doc_examples/json/JsonPathExamples.java` (`testNumericMemberIsNotAnIndex`)

```java
// A dotted number is a member name, not an array index
read("$.store.book.0.title");      // []
read("$.store.book[0].title");     // ["Sayings of the Century"]
JsonObject map = JsonObject.parse("{\"0\":\"zero\"}");
JsonPathFactory.get().getJsonPath("$.0").read(map).stringValue();       // "zero"

JsonObject odd = JsonObject.parse("{\"a-b\":1,\"a b\":2}");
JsonPathFactory.get().getJsonPath("$.a-b").read(odd).intValue();        // 1
JsonPathFactory.get().getJsonPath("$['a b']").read(odd).intValue();     // 2
```

### Filters

A filter keeps the array items (or object member values) for which its expression is true. In the expression, `@` is the current item and `$` the root document, each optionally followed by a path.

| Expression | Meaning |
|---|---|
| `@.path`, `$.path` alone | True when the path matches something (even a `null` value) |
| `==`, `!=` | Equality; numbers compare by value, other values only equal values of the same type |
| `<`, `<=`, `>`, `>=` | Ordering of two numbers or two strings; false for any other pair |
| `&&`, `\|\|`, `!`, `( )` | Logic and grouping; `&&` binds tighter than `\|\|` |
| `'text'`, `"text"`, numbers, `true`, `false`, `null` | Literals. Used alone as a logical operand (`@.a && false`), a literal is true unless it is `false`, `null`, `0` or `""` |

A comparison operand must be a literal or a path (`(@.a == 1) < 2` is rejected), and a missing operand (`@.a ==`) is a parse error. Strings are ordered by Unicode code point.

A path compared in a filter must be definite (no wildcard, deep scan, slice or filter in it); an indefinite one throws a `JsonException` when the filter runs. A comparison involving a missing member is false, so `!=` is true for it.

Sample: `doc_examples/json/JsonPathExamples.java` (`testFilters`)

```java
read("$..book[?(@.isbn)].title");                // ["Moby Dick","The Lord of the Rings"]
read("$..book[?(!@.isbn)].title");               // ["Sayings of the Century","Sword of Honour"]
read("$..book[?(@.price < 10)].title");          // ["Sayings of the Century","Moby Dick"]
read("$..book[?(@.price > $.expensive)].title"); // ["Sword of Honour","The Lord of the Rings"]
read("$..book[?(@.category == 'fiction' && @.price > 20)].title");   // ["The Lord of the Rings"]
read("$..book[?(@.price < 9 && @.category == 'reference' || @.price > 20)].title");
                                                 // ["Sayings of the Century","The Lord of the Rings"]
// The parentheses are optional
read("$..book[?@.isbn].title");                  // 2 titles
// Filters apply to object members too
read("$.store[?(@.color == 'red')]");            // [{"color":"red","price":19.95}]
```

Sample: `doc_examples/json/JsonPathExamples.java` (`testFilterMissingMembers`)

```java
// A comparison with a missing member is false, so != matches it
read("$..book[?(@.isbn != '0-553-21311-3')]");   // 3 books
// Values of different types are never equal, and only numbers and strings are ordered
read("$..book[?(@.price == '8.95')]");           // []
read("$..book[?(@.title > 10)]");                // []

// == null matches an explicit null, not a missing member
JsonObject items = JsonObject.parse("{\"items\":[{\"x\":null},{}]}");
JsonPathFactory.get().getJsonPath("$.items[?(@.x == null)]").read(items);   // [{"x":null}]
// An existence test is true for a null value
JsonPathFactory.get().getJsonPath("$.items[?(@.x)]").read(items);           // [{"x":null}]
```

Sample: `doc_examples/json/JsonPathExamples.java` (`testFilterPrimitives`, `testFilterIndefinitePath`)

```java
JsonArray numbers = JsonArray.of(1, 4, 2, 5);
JsonPathFactory.get().getJsonPath("$[?(@ >= 3)]").read(numbers);   // [4,5]

JsonPath p = JsonPathFactory.get().getJsonPath("$.store[?(@..price > 10)]");
p.read(json);        // throws JsonException: indefinite path in a filter
```

Not supported, and rejected with a `JsonException` when the path is compiled: the `in` operator, regular expression matching (`=~`), functions such as `length()`, and script expressions such as `[(@.length-1)]`.

Sample: `doc_examples/json/JsonPathExamples.java` (`testUnsupported`)

```java
JsonPathFactory.get().getJsonPath("$[?(@.d in [2, 3])]");                    // throws JsonException
JsonPathFactory.get().getJsonPath("$[?(@.a =~ /x/)]");                       // throws JsonException
JsonPathFactory.get().getJsonPath("$[(@.length-1)]");                        // throws JsonException
JsonPathFactory.get().getJsonPath("$..book[?(@.title.length() > 5)]");       // throws JsonException
```

## The result: `JsonValues`

`read()` returns a `JsonValues`, which holds zero, one or several values, as `getType()` tells: `EMPTY`, `VALUE` or `LIST`. A path that ends on an array yields that array as a single `VALUE`; `flat()` turns a container into a list of its items. `stringify()` writes a list as a JSON array and a single value as itself.

| Method | Result |
|---|---|
| `getType()`, `isEmpty()`, `isValue()`, `isList()` | The shape |
| `_size()`, `_get(i)`, `rawIterator()`, `rawForEach(...)` | The raw values |
| `forEach(...)`, `iterator()` | Each value wrapped in a `JsonValues` |
| `toJsonArray()`, `toArray()` | Copies of the values |
| `value()` | The single value; throws unless `VALUE` |
| `stringValue()`, `intValue()`, `doubleValue()`, `objectValue()`, ... | The single value, typed; throws unless `VALUE` of that type |
| `stringValue(default)`, `intValue(default)`, ... | The default when `EMPTY` |
| `asInt()`, `asString()`, ... | The single value converted as described in [Values](/GaltaJSON/Values#the-as-conversions) |

Sample: `doc_examples/json/JsonPathExamples.java` (`testResultShapes`)

```java
JsonValues single = JsonValues.of(json).path("$.store.bicycle.color");
JsonValues many = JsonValues.of(json).path("$..price");
JsonValues none = JsonValues.of(json).path("$.store.car");
JsonValues array = JsonValues.of(json).path("$.store.book");

single.getType();       // VALUE
many.getType();         // LIST
none.getType();         // EMPTY
// A path ending on an array yields that array as a single value
array.isValue() && array.isArray();   // true
array.flat()._size();                 // 4

single.stringValue();           // "red"
none.stringValue("none");       // "none"
many.doubleValue();             // throws JsonException: not a single value
none.stringValue();             // throws JsonException
```

### Pointers to the matches

`read(json, true)` also records where each value was found, as a [`JsonPointer`](/GaltaJSON/Pointers): `getPointer()` for a single value, `getPointer(i)` and `getPointers()` for a list.

Sample: `doc_examples/json/JsonPathExamples.java` (`testPointers`)

```java
JsonValues r = JsonPathFactory.get().getJsonPath("$..book[?(@.price > 20)].title").read(json, true);
r.stringValue();                            // "The Lord of the Rings"
r.getPointer().toJsonPointerString();       // "/store/book/3/title"

JsonValues all = JsonPathFactory.get().getJsonPath("$.store..color").read(json, true);
all.getPointers();                          // [/store/bicycle/color]
```

## Writing through a path

`write(json, value)` sets the value at a *definite* path (only member names and single indexes), creating the missing parents like [`JsonPointer.setValue`](/GaltaJSON/Pointers#adding-and-setting-values): an array when the next part is an index, an object otherwise. `isDefinite()` tells whether a path qualifies; writing through any other path throws a `JsonException`.

Sample: `doc_examples/json/JsonPathExamples.java` (`testWrite`)

```java
JsonPath gears = JsonPathFactory.get().getJsonPath("$.store.bicycle.gears");
gears.write(json, 21);                                  // true
json.getObject("store").getObject("bicycle").get("gears");   // 21

// Missing parents are created: an object, or an array before an index
JsonPathFactory.get().getJsonPath("$.store.car.wheels[0].size").write(json, 17);
json.getObject("store").getObject("car").stringify();   // {"wheels":[{"size":17}]}

// Only definite paths can be written
JsonPath all = JsonPathFactory.get().getJsonPath("$..price");
all.isDefinite();          // false
all.write(json, 0);        // throws JsonException
```

To update every match of an indefinite path, iterate over `read(json, true)` and use each pointer.

Sample: `doc_examples/json/JsonPathExamples.java` (`testPathInfo`)

```java
JsonPath p = JsonPathFactory.get().getJsonPath("$['store'].book[0]");
p.isDefinite();                          // true
p.canonicalPath();                       // "$.store.book[0]"
p.toJsonPointer().toJsonPointerString(); // "/store/book/0"
JsonPathFactory.get().getJsonPath("$['store'].book[0]");   // the same, cached, instance

JsonPathFactory.get().getJsonPath("store.book");           // throws JsonException: no leading $
```

## Navigating with JsonValues

`JsonValues.of(value)` wraps any value, so navigation never needs casts or null checks: `get(key)` and `get(index)` return `EMPTY` when there is nothing. On a list, they apply to every item and keep the ones that have the member or index. `path(...)` evaluates a JSON Path from there, `keySet()` lists the member names, and `has(key)` / `has(index)` test a single object or array.

The `JsonObject`-style getters are also available: `getString(key)`, `getInt(key, default)`, `getDouble(index)`, ... apply to a single object or array value.

Sample: `doc_examples/json/JsonPathExamples.java` (`testNavigation`)

```java
JsonValues v = JsonValues.of(json);
v.get("store").get("book").get(2).get("title").stringValue();    // "Moby Dick"
v.get("store").get("book").get(-2).getString("title");           // "Moby Dick"

// On a list, get() applies to every item and skips the ones without the member
JsonValues books = v.get("store").get("book").flat();
books.get("isbn").stringify();                                   // ["0-553-21311-3","0-395-19395-8"]
v.path("$.store.*").get("color").toJsonArray().stringify();      // ["red"]
v.get("store").keySet().contains("bicycle");                     // true
v.get("nothing").isEmpty();                                      // true
```

### Typed accessors and comparisons

A single value can be tested with `eq`, `ne`, `lt`, `lte`, `gt`, `gte` (overloaded for `String`, the numeric types, `BigInteger` and `BigDecimal`), `in(values...)` and `matches(regex)`. These overloads, and `in(String...)` / `in(Integer...)`, return `false` instead of throwing when the value is missing or of another type; `eq(Object)` and `in(Object...)` compare with `JsonUtil.eq`, and are false too without a single value. Numbers always compare by exact value, whatever the argument type: `8.95` is not `eq(8)`, and is `gt(8)`. `NaN` compares with nothing. The `asInt(default)`... conversions return the default when there is no value.

Sample: `doc_examples/json/JsonPathExamples.java` (`testTypedAccessors`)

```java
JsonValues book = JsonValues.of(json).path("$.store.book[0]");
book.getDouble("price");                 // 8.95
book.getString("category");              // "reference"
book.getString("isbn", "n/a");           // "n/a"
book.get("price").intValue();            // 8
book.get("price").asInt();               // 8
JsonValues.of("x").asInt();              // 0

JsonValues price = book.get("price");
price.eq(8.95) && price.lt(9.0) && price.gt(8.9);   // true
// Numbers compare exactly: 8.95 is not 8
price.eq(8);                             // false
price.gt(8);                             // true
book.get("category").in("fiction", "reference");   // true
book.get("author").matches("N.* Rees");            // true

// All the comparisons are false on a missing value, eq(Object) included
book.get("isbn").eq("x");                // false
book.get("isbn").gt(1.0);                // false
book.get("isbn").eq((Object)"x");        // false
```

### Stream-like operations

`JsonValues` has the same copy-returning operations as [`JsonArray`](/GaltaJSON/Collections), with `JsonValues` items: `filter`, `remove`, `map`, `flatMap`, `flat`, `sorted`, `distinct`, `slice`, `skip`, `limit`, `skipLimit`, `takeWhile`, `dropWhile`, `peek`, `min`, `max`, `anyMatch`, `allMatch`, `noneMatch`, `reduce` and `process`. `rawReduce` and `rawSorted` work on the raw values, and `find` / `findAndSet` behave like the [container versions](/GaltaJSON/Collections#find-and-findandset). `flatMap` is `Stream.flatMap`: it maps each value, then flattens what the mapper returns (a `JsonValues` or a `JsonArray`). `distinct` uses the JSON equality (`1`, `1L` and `1.0` are one value), `limit` rejects a negative count, and `slice(i)` returns the single value at `i` (`slice(i, Integer.MAX_VALUE)` for the rest). `filter` and `remove` keep the pointers of a result read with pointers.

Sample: `doc_examples/json/JsonPathExamples.java` (`testStreamLike`)

```java
JsonValues prices = JsonValues.of(json).path("$..price");

prices.filter(p -> p.lt(9.0)).stringify();          // [8.95,8.99]
prices.sorted().limit(3).stringify();               // [8.95,8.99,12.99]
prices.max().doubleValue();                         // 22.99
prices.rawReduce(Reducers.sumDouble());             // 73.87
prices.reduce(0.0, (sum, p) -> sum + p.doubleValue());   // 73.87
prices.anyMatch(p -> p.gt(20));                     // true

JsonValues titles = JsonValues.of(json).path("$..book[*]")
    .map(b -> b.getString("title").toUpperCase())
    .skip(2);
titles.stringify();                                 // ["MOBY DICK","THE LORD OF THE RINGS"]

List<String> categories = new java.util.ArrayList<>();
JsonValues.of(json).path("$..category").distinct().forEach(c -> categories.add(c.stringValue()));
// categories: [reference, fiction]
```

Sample: `doc_examples/json/JsonPathExamples.java` (`testFind`)

```java
JsonValues v = JsonValues.of(json);
v.find("price")._size();                 // 5
v.findAndSet("price", 0);                // in place
v.path("$..price").stringify();          // [0,0,0,0,0]
```

## Gotchas

- `.0` is a member name, not an index: use `[0]`.
- A path that ends on an array returns *one* value, the array; add `[*]` or call `flat()` for its items.
- The numeric comparison helpers of `JsonValues` compare exact values: `eq(8)` is false for `8.95`.
- `_size()` and `_get(i)` are the raw accessors; `get(i)` navigates into an array.

## Source

`jsonpath/JsonPathFactory.java`, `jsonpath/JsonPath.java`, `jsonpath/JsonPathParser.java`, `jsonpath/PathIndex.java`, `jsonpath/PathDeepScan.java`, `jsonpath/ExprBinaryOp.java`, `jsonpath/JsonValues.java`, under `parent-json/json/src/main/java/org/monflabs/json/`.
