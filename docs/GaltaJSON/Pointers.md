# JSON Pointers & References

A `JsonPointer` addresses one location in a document, as defined by [RFC 6901](https://www.rfc-editor.org/rfc/rfc6901), with a small extension for negative array indexes. It reads, tests, adds, replaces and removes values. `JsonReference` builds on it to resolve `$ref` objects, inside a document or across documents.

## Pointer syntax

`JsonPointer.of("/a/b/0")` parses a pointer. Each `/`-separated part is a key or an index:

| Part | Meaning |
|---|---|
| `name` | An object key. `~1` stands for `/` and `~0` for `~` |
| `0`, `12` | An array index, or the key `"0"`, `"12"` when the value is an object. `01` and `-0` are keys only |
| `-1`, `-2` | Extension: an array index counted from the end |
| `-` | The (nonexistent) item after the last one: appends in `add()` and `setValue()`, never exists otherwise |
| empty | The key `""`: `"/"` is the member `""` of the root, `"/a/"` is `a` then `""` |

`""` is the whole document. A leading `/` is optional (an extension: `"a/b"` is `"/a/b"`). A `~` not followed by `0` or `1` is an error.

Sample: `doc_examples/json/PointersExamples.java` (`testRead`)

```java
JsonObject doc = JsonObject.parse("{\"a\":{\"b\":[10,20,30]},\"x/y\":1,\"m~n\":2,\"0\":\"zero\"}");

JsonPointer.of("/a/b/0").read(doc);      // 10
JsonPointer.of("/a/b/-1").read(doc);     // 30: extension, from the end
JsonPointer.of("/x~1y").read(doc);       // 1: ~1 is '/'
JsonPointer.of("/m~0n").read(doc);       // 2: ~0 is '~'
JsonPointer.of("/0").read(doc);          // "zero": a number is a key on an object
JsonPointer.of("").read(doc);            // doc itself

JsonPointer.of("/a/c").read(doc);        // null: missing
JsonPointer.of("/a/c").exists(doc);      // false
JsonPointer.of("/a/b/2").exists(doc);    // true
JsonPointer.of("/a/b/3").exists(doc);    // false
```

`read()` returns `null` both for a missing location and for a `null` value; `exists()` tells them apart.

## Operations

| Method | Missing parents | Last part missing | Array index | Returns |
|---|---|---|---|---|
| `read(doc)` | `null` | `null` | reads | the value |
| `exists(doc)` | `false` | `false` | tests | `boolean` |
| `add(doc, value)` | fails | creates | inserts (index `size` or `-` appends) | `true` if done |
| `setValue(doc, value)` | creates | creates | overwrites an item; index `size` or `-` appends; beyond: fails | `true` if done |
| `replace(doc, value)` | fails | fails | overwrites | `true` if done |
| `remove(doc)` | fails | fails | removes | `true` if done |

The update methods never throw for a missing location: they return `false`.

### Adding and setting values

`add()` follows JSON Patch: it inserts into an array and never creates intermediate containers. `setValue()` builds the whole path when needed: for each missing parent it creates an array if the next part is an index, an object otherwise, and it overwrites an array item instead of inserting. An index can address an existing item or the one right after the last (which appends); a larger index fails rather than padding the array with `null`s. When `setValue()` fails, the document is left unchanged: the containers it would create are checked first.

Sample: `doc_examples/json/PointersExamples.java` (`testAddAndSetValue`)

```java
JsonObject doc = JsonObject.parse("{\"list\":[1,2,3]}");

JsonPointer.of("/list/1").add(doc, 9);          // true: inserts
JsonPointer.of("/list/-").add(doc, 4);          // true: '-' appends
JsonPointer.of("/list/9").add(doc, 0);          // false: beyond the end
doc.getArray("list");                           // [1,9,2,3,4]

// add() does not create missing parents...
JsonPointer.of("/config/server/port").add(doc, 8080);      // false
doc.has("config");                                         // false
// ...setValue() does: an array before a numeric part, an object otherwise
JsonPointer.of("/config/server/port").setValue(doc, 8080); // true
JsonPointer.of("/config/hosts/0").setValue(doc, "a");      // true
JsonPointer.of("/config/hosts/-").setValue(doc, "b");      // true: '-' (or the size) appends
JsonPointer.of("/config/hosts/5").setValue(doc, "c");      // false: no padding with nulls
doc.getObject("config");            // {"server":{"port":8080},"hosts":["a","b"]}

// setValue() overwrites an array item instead of inserting
JsonPointer.of("/list/0").setValue(doc, 0);
doc.getArray("list");               // [0,9,2,3,4]
```

### Replacing and removing

Sample: `doc_examples/json/PointersExamples.java` (`testReplaceAndRemove`)

```java
JsonObject doc = JsonObject.parse("{\"a\":1,\"list\":[1,2,3]}");

JsonPointer.of("/a").replace(doc, 2);       // true
JsonPointer.of("/b").replace(doc, 2);       // false: must exist
doc.has("b");                               // false

JsonPointer.of("/list/-1").remove(doc);     // true
JsonPointer.of("/list/5").remove(doc);      // false
doc.stringify();                            // {"a":2,"list":[1,2]}
```

As in RFC 6901, `-` designates the (nonexistent) item after the last one: it never exists for `read()`, `exists()`, `replace()` and `remove()`, and appends in `add()` and `setValue()`. The last item is `-1`.

Sample: `doc_examples/json/PointersExamples.java` (`testLastItemMarker`)

```java
JsonObject doc = JsonObject.parse("{\"list\":[1,2,3]}");
JsonPointer.of("/list/-").read(doc);          // null: nonexistent
JsonPointer.of("/list/-").replace(doc, 4);    // false
JsonPointer.of("/list/-").add(doc, 4);        // true: it appends
JsonPointer.of("/list/-1").read(doc);         // 4: the last item (extension)
JsonPointer.of("/list/-1").replace(doc, 5);   // true
doc.getArray("list");                         // [1,2,3,5]
```

## Building and printing pointers

`getChild(key)` and `getChild(index)` extend a pointer (a key stays a key, even `"7"`) in constant time, `JsonPointer.ofParts(...)` builds one from `String` keys and `Integer` indexes, `getParent()` shortens it, and `size()`, `getPart(i)`, `getParts()` and `getLastPart()` expose the parts. Pointers are immutable: `getParts()` returns a copy. A key made of digits still addresses an array item, as the RFC defines it. `toJsonPointerString()` (also `toString()`) escapes the keys, and `toJsonPathString()` gives the equivalent [JSON Path](/GaltaJSON/JsonPath) (`$['7']` for a key, `$[7]` for an index); a pointer holding the `-` token has no JSON Path equivalent and throws a `JsonException`. `JsonPointer.ofJsonPath(path)` converts back from a definite JSON Path, and keeps its meaning: there, as with `ofJsonPathParts(...)`, a key never addresses an array item (`$.list['0']` designates nothing in an array). `JsonPointer.ofFragment("#/a%20b")` parses the URI fragment form of a pointer (RFC 6901 section 6): the fragment is percent-decoded first, and a malformed escape is a `JsonException`. Pointers are values: `equals()` and `hashCode()` compare their parts in order, the index `7` and the key `"7"` being the same reference token. Equal pointers behave the same: on an array, a part is used as an index according to its text only, so `getChild("-")` appends like `/-` and `ofParts("-1")` reads the last item like `/-1`. A pointer from a JSON Path keeps its own rule: a key never addresses an array item.

`JsonPointer.of()` keeps the last pointers it parsed and returns the same instance for the same string, which is safe since pointers are immutable. A parsed pointer resolves its array indexes once, so reading through it again doesn't parse anything.

Sample: `doc_examples/json/PointersExamples.java` (`testBuildingPointers`)

```java
JsonPointer p = JsonPointer.of("/store/book").getChild(0).getChild("a/b");
p.toJsonPointerString();       // "/store/book/0/a~1b"
p.toJsonPathString();          // "$.store.book[0]['a/b']"
p.getParent().toString();      // "/store/book/0"
p.size();                      // 4
p.getPart(2);                  // 0: indexes are Integers
p.equals(JsonPointer.of("/store/book/0/a~1b"));   // true

JsonPointer.ofJsonPath("$.store.book[0]").toJsonPointerString();   // "/store/book/0"
JsonPointer.of("").isEmpty();                     // true: the whole document
JsonPointer.of("/").getPart(0);                   // "": the "" member
JsonPointer.of("/a/").size();                     // 2: "a", then ""
JsonPointer.EMPTY.getChild("7").getPart(0);                  // "7": a member name stays a name
JsonPointer.EMPTY.getChild("7").equals(JsonPointer.of("/7")); // true: the same token
```

## JSON references

`JsonReference.resolve(factory, json, resolver, keepReferences)` replaces, anywhere in `json`, every object with a string `"$ref": "<url>#<pointer>"` property by the value it refers to. Its other properties are ignored, as the specification requires. The `Resolver` loads the document named by the URL part: the base `JsonReference.Resolver(root)` answers the empty URL (a `#/...` reference inside the document) with `root` and throws a `JsonException` for any other URL. The fragment is either empty (`other.json`, `#`: the whole document) or a percent-encoded JSON Pointer starting with `/`, read in that document; a location that doesn't exist, a malformed percent escape, or a plain name fragment (`#foo`, an anchor, which only schemas support) is a `JsonException`, not a `null`. A pointer may go through other references (`#/b/c` where `/b` is itself a `$ref`): they are followed on the way. A reference to a reference is followed, and a cycle made only of references (`#/a` -> `#/b` -> `#/a`) is a `JsonException`. A container referring to one of its parents (a recursive schema) is legal: the result is a cyclic graph, and `findReferences()` reports each container once.

`resolve()` returns the resolved value: `json` itself, updated in place, unless `json` is a reference.

Sample: `doc_examples/json/PointersExamples.java` (`testResolveLocalReferences`)

```java
JsonObject schema = JsonObject.parse("""
    {
      "definitions": { "User": { "type": "object" } },
      "properties": { "author": { "$ref": "#/definitions/User" } }
    }
    """);
JsonReference.resolve(JsonFactory.get(), schema, new JsonReference.Resolver(schema), false);

JsonObject author = schema.getObject("properties").getObject("author");
author.getString("type");     // "object"
author == schema.getObject("definitions").getObject("User");   // true: shared, not copied

// The other properties of a reference object are ignored: the object is replaced
JsonObject mixed = JsonObject.parse("{\"a\":{\"$ref\":\"#/b\",\"x\":1},\"b\":2}");
JsonReference.resolve(JsonFactory.get(), mixed, new JsonReference.Resolver(mixed), false);
mixed.get("a");                       // 2

// A reference that cannot be resolved is an error, not a null
JsonObject broken = JsonObject.parse("{\"a\":{\"$ref\":\"#/nope\"}}");
JsonReference.resolve(JsonFactory.get(), broken, new JsonReference.Resolver(broken), false);
                                      // throws JsonException
```

The referenced value is inserted as is, not copied: after resolution the same container appears at each place that referred to it, and a change made through one place is visible from the others.

### External documents and keeping references

Subclass `Resolver` and override `apply(factory, url)` to load other documents. With `keepReferences` set to `true`, each resolved container remembers its reference (`getReference()`), `JsonReference.findReferences(container, callback)` lists them, and a stringifier with `setOutputReferences(true)` writes them back as `{"$ref": ...}` instead of their content. A container that is the target of several references keeps the first one met, in document order.

How the documents are scoped:

- A reference is relative to the document holding it. The URL is resolved against that document's URL by `Resolver.resolveUrl(documentUrl, url)` (RFC 3986 for URIs, the parent path otherwise), so `c.json` inside `dir/b.json` is `dir/c.json`, and `#/x` inside `dir/b.json` is in `dir/b.json`. The root document's URL is the optional second argument of `Resolver(root, baseUrl)`; without one, the references of the root are passed to `apply()` as written.
- `apply()` receives that resolved URL, and is called at most once per document during a `resolve()` call: the documents are cached by normalized URL (`./d.json` and `d.json` are the same). All the references to a document share its content, and two documents referring to each other give a cyclic graph instead of an endless expansion.
- `resolve(factory, json, resolver, keepReferences, true)` processes `json` as a JSON Schema: the data keywords (`const`, `enum`, `default`, `examples`) are left untouched, the names under `properties`, `$defs`... are not taken for keywords, a reference to the `$id` of a loaded (sub)schema resolves to it without loading anything, and `#name` designates a `$anchor` (or a draft-07 `"$id": "#name"`). Relative references are still resolved against the document URL, not against `$id`.

Sample: `doc_examples/json/PointersExamples.java` (`testResolveExternalReferences`)

```java
JsonObject doc = JsonObject.parse("{\"title\":\"Notes\",\"author\":{\"$ref\":\"people.json#/ada\"}}");
Map<String,String> files = Map.of("people.json", "{\"ada\":{\"name\":\"Ada\"}}");

JsonReference.Resolver resolver = new JsonReference.Resolver(doc) {
    @Override
    public Object apply(JsonFactory factory, String url) {
        if(files.containsKey(url)) {
            return factory.parse(files.get(url));
        }
        return super.apply(factory, url);   // "" is the root, anything else throws
    }
};
Object resolved = JsonReference.resolve(JsonFactory.get(), doc, resolver, true);

resolved == doc;                                 // true
doc.stringify();                                 // {"title":"Notes","author":{"name":"Ada"}}
doc.getObject("author").getReference();          // "people.json#/ada"

// Stringify the references back instead of their content
JsonStringifier.StringSerializer s = new JsonStringifier.StringSerializer();
s.setOutputReferences(true);
s.stringify(doc);                    // {"title":"Notes","author":{"$ref":"people.json#/ada"}}

List<String> refs = new ArrayList<>();
JsonReference.findReferences(doc, (container, ref) -> refs.add(ref));
// refs: [people.json#/ada]
```

Sample: `doc_examples/json/PointersExamples.java` (`testUnresolvableReference`)

```java
JsonObject doc = JsonObject.parse("{\"a\":{\"$ref\":\"other.json\"}}");
JsonReference.resolve(JsonFactory.get(), doc, new JsonReference.Resolver(doc), false);
                                     // throws JsonException: no resolver for other.json
```

Every container can carry its reference (`getReference()`/`setReference()`). With `keepReferences`, a local reference marks the *target* container itself, since it is shared. Stringifying with `setOutputReferences(true)` writes the reference wherever the target is referenced, but the target's own location (the one the `#/...` pointer designates) gets the definition's content, so the output resolves back to the same structure. A recursive structure (a node referring to one of its parents) is written with references, not reported as a cycle.

Sample: `doc_examples/json/PointersExamples.java` (`testKeepLocalReferences`)

```java
JsonObject schema = JsonObject.parse("{\"defs\":{\"u\":{\"t\":1}},\"p\":{\"$ref\":\"#/defs/u\"}}");
JsonReference.resolve(JsonFactory.get(), schema, new JsonReference.Resolver(schema), true);
JsonStringifier.StringSerializer s = new JsonStringifier.StringSerializer();
s.setOutputReferences(true);
// The reference is written where it was, the definition keeps its content
s.stringify(schema);    // {"defs":{"u":{"t":1}},"p":{"$ref":"#/defs/u"}}
```

## Gotchas

- `read()` cannot tell a `null` value from a missing one; use `exists()`.
- `add()` inserts into arrays and `setValue()` overwrites: pick the one that matches the intent.
- `-` never exists outside of `add()`/`setValue()`: use `-1` for the last item.
- `"/"` is the `""` member, not the whole document (that is `""`).
- Resolved references share their target, and a recursive document resolves to a cyclic graph. `deepClone()`, `equals()`, `hashCode()` and a plain `stringify()` recurse into the values and do not terminate on such a graph; clone the document *before* resolving it when an untouched copy is needed, and stringify a cyclic result with `setOutputReferences(true)` after resolving with `keepReferences`.

## Source

`jsonpointer/JsonPointer.java`, `jsonreference/JsonReference.java`, `stringifier/JsonStringifier.java` (`setOutputReferences`), under `parent-json/json/src/main/java/org/monflabs/json/`.
