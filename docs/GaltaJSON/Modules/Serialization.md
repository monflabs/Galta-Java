# Serialization

The `json-serialization` module converts plain Java objects to GaltaJSON values and back. It is reflection-based, has no annotations and no third-party dependency: a *registry* holds one *class adapter* per Java class, and each adapter turns an instance into a `JsonObject` (or any other JSON value) and back. The JSON side is made of the regular GaltaJSON values described in [Values](/GaltaJSON/Values), so the result can be stringified, queried with [JSON Path](/GaltaJSON/JsonPath) or handed to the JavaScript engine as is.

```xml
<dependency>
    <groupId>org.monflabs.galta</groupId>
    <artifactId>json-serialization</artifactId>
</dependency>
```

## Getting started

A `SimpleRegistry` is built once, with the classes it has to handle, and is then used for both directions. `add(Class)` creates an adapter that reads the class fields by reflection (an enum class gets an adapter writing the constant names).

Sample: `doc_examples/serialization/SerializationExamples.java` (`testSerializeDeserialize`)

```java
public static class Person {
    String name;
    int age;
    List<String> tags;
    public Person() {
    }
}

SimpleRegistry registry = SimpleRegistry.newBuilder()
        .add(Person.class)
        .build();

Person p = new Person();
p.name = "Ada";
p.age = 36;
p.tags = List.of("math", "code");

JsonObject json = registry.serialize(p);
// -> {"name":"Ada","age":36,"tags":["math","code"]}

Person copy = registry.deserialize(Person.class, json);
// copy.tags -> ["math", "code"]
```

The registry is thread safe: build it once and share it. Its methods are:

| Method | Does |
|---|---|
| `serialize(obj)` | Returns the JSON value for `obj` (`null` for `null`). The adapter is found from `obj.getClass()`. The result type is inferred from the call site, see below. |
| `toJson(obj)` | Same, returning an `Object`. |
| `toJson(obj, type)` | Serializes `obj` as a given type, generic or not (a `java.lang.reflect.Type` or a `TypeRef`). |
| `serialize(obj, genericParams)` | Same as `serialize(obj)`, binding the type parameters of a generic class (see [Generics](#generics)). |
| `serialize(writer, obj, genericParams, compact)` | Serializes and writes the JSON text (`null` for `null`). |
| `deserialize(clazz, jsonValue[, genericParams])`, `fromJson(clazz, jsonValue)` | Creates a `clazz` instance from a JSON value (`null` for `null`). A primitive class uses its boxed type: `deserialize(int.class, 5)` is `5`. |
| `deserialize(type, jsonValue)`, `fromJson(typeRef, jsonValue)` | Same, for a generic type: `new TypeRef<List<Item>>() {}`. |
| `deserialize(clazz, reader)` | Parses the text, then deserializes it. |
| `findAdapter(clazz)` | Returns the adapter for a class, or throws a `JsonException` when there is none. |
| `findAdapterOrNull(clazz)` | Same, returning `null` when there is none. |
| `findAdapter(type)` | Returns the adapter of a generic type, bound to the adapters of its type arguments. |

Sample: `doc_examples/serialization/SerializationExamples.java` (`testReaderWriter`)

```java
Person p = registry.deserialize(Person.class,
        new StringReader("{\"name\":\"Alan\",\"age\":41}"));
// p.tags -> null: absent keys leave the field untouched

StringWriter w = new StringWriter();
registry.serialize(w, p, null, true);             // compact
// -> {"name":"Alan","age":41,"tags":null}: null fields are written as null
```

When reading, a JSON key that has no matching field is an error (`...Person doesn't have a field named email`), unless its value is `null`.

Sample: `doc_examples/serialization/SerializationExamples.java` (`testUnknownKeyFails`)

`serialize()` is generic in its return type (`<T> T serialize(Object)`). Nested directly inside `deserialize()`, Java infers the `Reader` overload and the call fails at runtime with a `ClassCastException`. Assign the result to a variable first, or use `toJson()`, which returns an `Object`:

Sample: `doc_examples/serialization/SerializationExamples.java` (`testTypedResultPitfall`)

```java
// registry.deserialize(Person.class, registry.serialize(p));  -> ClassCastException
Object json = registry.serialize(p);
Person back = registry.deserialize(Person.class, json);
Person again = registry.fromJson(Person.class, registry.toJson(p));
```

### Null values

A JSON `null` is assigned as is: it replaces the value set by the constructor (the field default), and an `Optional` field becomes `Optional.empty()`. A primitive field cannot hold `null`: the value is rejected with a `JsonException` naming the field, instead of silently keeping the previous value. An absent key leaves the field untouched. For a record, an absent component is `null` (`0`/`false` for a primitive, `Optional.empty()` for an `Optional`), and a `null` for a primitive component is an error.

Sample: `doc_examples/serialization/SerializationExamples.java` (`testNullValues`)

```java
public static class Settings {
    String theme = "dark";
    int fontSize = 12;
    Optional<String> locale = Optional.of("en");
}

Settings s = registry.deserialize(Settings.class, JsonObject.of("theme", null, "locale", null));
// s.theme -> null, s.locale -> Optional.empty(), s.fontSize -> 12 (absent)

registry.deserialize(Settings.class, JsonObject.of("fontSize", null));
// -> JsonException: A null JSON value cannot be assigned to the int field fontSize (at $.fontSize)
```

## Which fields are serialized

Reflection walks the class and its superclasses and keeps every instance field, whatever its visibility (`private` and `final` fields included). It skips:

- `static` fields (constants, `serialVersionUID`...),
- `transient` fields,
- synthetic fields, like the `this$0` reference an inner class holds to its outer instance,
- the fields of the JDK superclasses: the walk stops at the first class whose package is not open to reflection (`RuntimeException`, `AbstractList`...). A class extending `RuntimeException` serializes its own fields only. Registering a JDK class itself that has fields (`StringBuilder`...) by reflection fails with a `JsonException` asking for an adapter.

A field declared in a subclass hides a superclass field with the same name, even when the field filter (below) rejects it. The JSON key is the Java field name, and the keys follow the declaration order, the superclass fields first.

Sample: `doc_examples/serialization/SerializationExamples.java` (`testFieldsPickedUp`)

```java
public static class Base {
    String id;
}
public static class Account extends Base {
    static final String KIND = "account";           // static: skipped
    transient String password;                       // transient: skipped
    private BigDecimal balance;                      // private is fine
}

Account a = new Account();
a.id = "A-1";
a.password = "secret";
a.balance = new BigDecimal("12.50");

JsonObject json = registry.serialize(a);
// json.keySet() -> [id, balance]: inherited 'id' included
```

To choose the fields, build the adapter yourself with `SimpleClassAdapter.newBuilder(clazz)` and give `reflection()` a `Predicate<Field>`:

Sample: `doc_examples/serialization/SerializationExamples.java` (`testFieldFilter`)

```java
SimpleRegistry registry = SimpleRegistry.newBuilder()
        .add(SimpleClassAdapter.newBuilder(Account.class)
                .reflection(f -> !f.getName().equals("balance"))
                .build())
        .build();
```

Reading assigns the fields directly, bypassing the setters. A `final` field is overwritten too, with one exception: a `final` instance field initialized with a constant expression (`final String kind = "a";`) is inlined by the compiler, so the class code keeps reading the constant whatever the field holds. Exclude such fields, or make them `static`.

## Supported types

A new registry already contains adapters for:

| Java type | JSON |
|---|---|
| `boolean`, `byte`, `short`, `int`, `long`, `float`, `double` and their boxed types | boolean / number; reading a number into an integral type is exact: `1.5` or a value out of range throws a `JsonException` instead of being truncated. A number is checked before it is expanded, so `1e10000000` is rejected at once; a `BigInteger` accepts up to 10,000 digits |
| `Number` | number, read back as the parser produced it (`Integer`, `Long`, `Double`, `BigDecimal`...) |
| `String` | string |
| `char`, `Character` | one character string |
| `BigInteger`, `BigDecimal` | number (the instance is kept as is) |
| `boolean[]`, `byte[]`, `short[]`, `int[]`, `long[]`, `float[]`, `double[]` | array of numbers / booleans |
| `char[]` | string (also read from an array of one character strings) |
| Any other array, multi-dimensional included (`String[][]`, `Item[]`) | array; the component type is preserved when reading back |
| Enums | string: the constant name |
| `LocalDate`, `LocalDateTime`, `LocalTime`, `OffsetTime`, `OffsetDateTime`, `ZonedDateTime` | ISO-8601 string (`2026-09-26`, `2026-09-26T10:15:30+02:00`...), the formats of `JsonUtil.checkLocalDate()` and its siblings |
| `Instant`, `java.util.Date` | ISO-8601 instant in UTC (`2026-09-26T10:15:30Z`), the way the stringifier writes a `Date` |
| `Duration`, `Period` | ISO-8601 duration (`PT45M`, `P3D`) |
| `ZoneId` | zone id (`Europe/Paris`, `+02:00`) |
| `UUID`, `URI` | string |
| `Optional<T>` | the value, or `null` when empty; `null` reads back as `Optional.empty()` |
| `List<T>`, `Set<T>`, `Collection<T>`, `Iterable<T>`, `Queue<T>`, `Deque<T>`... | array, read back as the declared type (see below) |
| `Map<K,V>`... | object, read back as the declared type. String keys are kept; number, boolean, character and enum keys use their string form and are parsed back to the key type; other keys must serialize to strings (a `UUID`, a `LocalDate`...). Two keys with the same string form (`"1"` and `1`) throw a `JsonException` |
| `JsonObject`, `JsonArray` | the same JSON value, deep copied in both directions (the JSON produced and the object read share no mutable state); another JSON type throws a `JsonException` |
| `Object` | a JSON value is kept as is (JSON objects and arrays deep copied); another value (a POJO, a Java collection...) is serialized with the adapter of its class when the registry has one, and kept as is otherwise |
| Registered classes | object |
| Registered records | object, through the accessors; read back with the canonical constructor |

Type arguments are followed at any depth, so `Map<String, List<Item>>` or `List<Map<String,Foo>>` work. A raw `List` or a wildcard `List<?>` behaves like `List<Object>`: the values are kept as they are, and `List<? extends Foo>` uses the `Foo` adapter.

Sample: `doc_examples/serialization/SerializationExamples.java` (`testSupportedTypes`)

```java
public static class Order {
    long number;
    Double discount;
    int[] quantities;
    String[][] grid;
    Item[] items;
    Set<String> labels;
    Map<String, List<Item>> byWarehouse;
}

JsonObject json = registry.serialize(order);
// {"number":42,"discount":0.1,"quantities":[1,2],"grid":[["a","b"],["c"]],
//  "items":[{"sku":"X"}],"labels":["urgent"],
//  "byWarehouse":{"north":[{"sku":"Y"},{"sku":"Z"}]}}

Order back = registry.deserialize(Order.class, json);
// back.grid.getClass() -> String[][].class
```

Sample: `doc_examples/serialization/SerializationExamples.java` (`testValueTypes`)

```java
public static class Meeting {
    UUID id;
    LocalDate day;
    LocalTime start;
    Duration length;
    ZoneId zone;
    Optional<URI> link;
}

JsonObject json = registry.serialize(m);
// {"id":"123e4567-e89b-12d3-a456-426614174000","day":"2026-09-26","start":"09:30:00",
//  "length":"PT45M","zone":"Europe/Paris","link":null}
```

### Collection types

A collection or a map is read back as its declared type when that type can be instantiated (a concrete class with a no-arg constructor, package-private classes included), otherwise as the first compatible standard implementation:

| Declared type | Read back as |
|---|---|
| `List`, `Collection`, `Iterable`, `SequencedCollection` | `ArrayList` |
| `Set`, `SequencedSet` | `LinkedHashSet` (the array order is kept) |
| `SortedSet`, `NavigableSet` | `TreeSet` |
| `Queue`, `Deque` | `ArrayDeque` (no `null` element) |
| `EnumSet<E>` | `EnumSet.noneOf(E)` |
| `Map`, `SequencedMap` | `LinkedHashMap` (the key order is kept) |
| `SortedMap`, `NavigableMap` | `TreeMap` |
| `ConcurrentMap`, `ConcurrentNavigableMap` | `ConcurrentHashMap`, `ConcurrentSkipListMap` (no `null` value) |
| `EnumMap<K,V>` | `new EnumMap<>(K)` |

A declared type with no compatible implementation (a custom `interface Tags extends List<String>`) throws a `JsonException` naming the field when it is read; register an adapter for it. A subclass with its own type parameters is supported: `class Props<V> extends HashMap<String,V>` gives `Props<Item>` `String` keys and `Item` values.

Sample: `doc_examples/serialization/SerializationExamples.java` (`testDeclaredCollectionTypes`)

```java
public static class Index {
    SortedMap<String, Integer> counts;               // read back as a TreeMap
    Deque<String> history;                           // read back as an ArrayDeque
    EnumSet<Color> colors;                           // EnumSet.noneOf(Color.class)
}
```

### NaN and infinities

NaN and the infinities are not JSON numbers: by default a `float`/`double` holding one is written as is, and the stringifier writes it as `null`, which a primitive field then rejects. With the `nonFiniteNumbersAsStrings(true)` option they are written as the strings `"NaN"`, `"Infinity"` and `"-Infinity"`. These strings are always accepted when reading a `float` or a `double` (field, boxed value, array item), with or without the option.

Sample: `doc_examples/serialization/SerializationExamples.java` (`testNonFiniteNumbers`)

```java
SimpleRegistry registry = SimpleRegistry.newBuilder()
        .nonFiniteNumbersAsStrings(true)
        .add(Sample.class)
        .build();

s.value = Double.NaN;
registry.serialize(s);                            // -> {"value":"NaN"}
```

## Declared types and subclasses

A value whose class is a subclass of its declared type (a field declared `Shape` holding a `Circle`, an item of a `List<Shape>`, a `Map` value, an array item, an `Optional`) is serialized with the adapter of its own class when the registry has one (registered, or created by the class factory), so the data of the subclass is kept; otherwise with the adapter of the declared type, and its extra fields are lost. The JSON carries no type: it is always read back as the declared type. For a closed hierarchy, sealed types can carry their class, see below.

At the top level, registered adapters are looked up by the exact class: an instance of an unregistered subclass is not found under its parent, register it. Collections, maps, enums, value types (`ZoneOffset` as a `ZoneId`...) are found from their runtime class.

### Sealed types

`sealedTypes()` enables a discriminator for the sealed classes and interfaces: the JSON object of a value gets an extra `"@type"` property (`sealedTypes("kind")` chooses another name) holding the simple name of its class, and is read back as this class. The candidates are the concrete classes of the sealed hierarchy (`getPermittedSubclasses()`, recursively), a closed set: the JSON cannot name any other class. They do not need to be registered: a class of a sealed hierarchy gets a reflection adapter. Two candidates with the same simple name are an error, as is a candidate serialized to something else than a JSON object (an enum).

Sample: `doc_examples/serialization/SerializationExamples.java` (`testSealedTypes`)

```java
public sealed interface Shape permits Circle, Square {}
public record Circle(double radius) implements Shape {}
public record Square(double side) implements Shape {}
public static class Drawing {
    List<Shape> shapes;
}

SimpleRegistry registry = SimpleRegistry.newBuilder()
        .sealedTypes()
        .add(Drawing.class)
        .build();
// {"shapes":[{"@type":"Circle","radius":1.0},{"@type":"Square","side":2.0}]}
```

The discriminator applies where the sealed type is the declared type (a field, a collection item, `deserialize(Shape.class, json)`); `serialize(circle)` at the top level writes a plain `Circle`. A sealed type with a registered adapter uses that adapter.

## Generics

Type variables are resolved from wherever they are bound:

- by the declared type of a field (`Page<Item> items`),
- by a subclass, directly or through intermediate generic classes (`class ItemPage extends Page<Item>`),
- by the enclosing class of an inner class (`Outer<Item>.Inner`), when serializing,
- for a top-level generic object, by passing the adapters of the type arguments, in declaration order, to `serialize`/`deserialize`, or by giving the generic type to `toJson`/`fromJson`.

A type variable bound nowhere uses its bound: `T extends Item` reads an `Item`, an unbounded `T` keeps the JSON value. Generic arrays (`T[]`, `List<String>[]`) are resolved like the other types. Passing a wrong number of type arguments to a collection or a map adapter throws a `JsonException`.

Sample: `doc_examples/serialization/SerializationExamples.java` (`testGenerics`)

```java
public static class Page<T> {
    int total;
    List<T> rows;
}
public static class ItemPage extends Page<Item> {
}

// A top-level generic object: pass the type arguments explicitly
Page<Item> page = new Page<>();
page.rows = List.of(new Item("C"));
ClassAdapter[] itemParam = { registry.findAdapter(Item.class) };
JsonObject json = registry.serialize(page, itemParam);
Page<Item> page2 = registry.deserialize(Page.class, json, itemParam);
```

A `TypeRef` (an anonymous subclass capturing a generic type) does the same for any type:

Sample: `doc_examples/serialization/SerializationExamples.java` (`testTypeRef`)

```java
Object json = registry.toJson(List.of(new Item("A"), new Item("B")));
List<Item> items = registry.fromJson(new TypeRef<List<Item>>() {}, json);

Map<String, Item> byId = registry.fromJson(new TypeRef<Map<String, Item>>() {},
        JsonObject.of("a", JsonObject.of("sku", "A")));
```

## Custom adapters

### Class adapters

A class that should not be serialized field by field gets its own `ClassAdapter`. Extending `BaseClassAdapter` provides `getAdaptedClazz()`; register the instance with `add(adapter)`. It then applies wherever the class is used: top-level objects, fields, array items or collection values. `LocalDate` is built in; this adapter replaces the built-in one:

Sample: `doc_examples/serialization/SerializationExamples.java` (`testCustomClassAdapter`)

```java
public static class LocalDateAdapter extends BaseClassAdapter {
    public LocalDateAdapter() {
        super(LocalDate.class);
    }
    @Override
    public Object serialize(Object value, ClassAdapter[] genericParams) {
        return value != null ? value.toString() : null;
    }
    @Override
    public Object deserialize(Object jsonValue, ClassAdapter[] genericParams) {
        return jsonValue != null ? LocalDate.parse((String)jsonValue) : null;
    }
}

SimpleRegistry registry = SimpleRegistry.newBuilder()
        .add(new LocalDateAdapter())
        .add(Event.class)
        .build();
// {"title":"Launch","date":"2026-09-26"}
```

Registering an adapter for a class replaces any other adapter of this class, built-in adapters included, whatever the order of the calls (`addDefaultAdapters()` does not undo it). The built-in adapters are created by each `build()`; `defaultAdapters(false)` builds a registry without them. A collection adapter for a concrete class (`new ListClassAdapter(MyList.class)`) applies to that class only.

A reflection adapter (`SimpleClassAdapter`, created by `add(Class)` or `newBuilder(clazz)...build()`) is bound to the first registry that uses it: using the same instance in another registry throws a `JsonException` the first time the class is used there. Build one adapter per registry, and so one registry per `SimpleRegistry.Builder` that holds such adapters. A stateless adapter, like the one above, can be shared.

### Field adapters

`add(Class, builder -> ...)` defines the JSON properties one by one with a reader and a writer lambda. A property does not have to map to a field: it can be computed, and its writer can ignore the value. It can be combined with `reflection()` in the same builder.

Untyped (`add(name, reader, writer)`), the reader returns a JSON value, or a value the registry knows how to serialize (converted like a value declared as `Object`: a POJO with an adapter becomes an object); the writer receives the raw JSON value, numbers as `Number`, `null` included. Java infers the value type from the reader: when the reader does not return the JSON type, declare it as `Object` (`(Card c) -> (Object)c.item`), or the writer fails with a `ClassCastException`.

Sample: `doc_examples/serialization/SerializationExamples.java` (`testLambdaFields`)

```java
SimpleRegistry registry = SimpleRegistry.newBuilder()
        .add(MutablePoint.class, b -> b
                .add("x", (MutablePoint p) -> p.x, (p, v) -> p.x = ((Number)v).intValue())
                .add("y", (MutablePoint p) -> p.y, (p, v) -> p.y = ((Number)v).intValue())
                .add("sum", (MutablePoint p) -> p.x + p.y, (p, v) -> { /* computed: ignored */ }))
        .build();
// -> {"x":2,"y":3,"sum":5}
```

Typed (`add(name, type, reader, writer)`), the reader returns a Java value of the type, serialized with its registry adapter, and the writer receives the Java value read with this adapter (`null` for a JSON `null`):

Sample: `doc_examples/serialization/SerializationExamples.java` (`testTypedLambdaFields`)

```java
SimpleRegistry registry = SimpleRegistry.newBuilder()
        .add(Item.class)
        .add(Ticket.class, b -> b
                .add("item", Item.class, (Ticket t) -> t.item, (t, v) -> t.item = v)
                .add("due", LocalDate.class, (Ticket t) -> t.due, (t, v) -> t.due = v))
        .build();
// {"item":{"sku":"X"},"due":"2026-10-01"}
```

`SimpleClassAdapter.Builder` also accepts `add(name, FieldAdapter)` for a reusable implementation of the `FieldAdapter` interface (`readProperty` / `writeProperty`).

### Instance factory

Reading creates the instance with the class's no-argument constructor, which may be private. A class without one needs a `factory`; reflection then overwrites the fields, `final` ones included. A record is always created with its canonical constructor: `factory()` on a record is rejected when the adapter is built.

Sample: `doc_examples/serialization/SerializationExamples.java` (`testFactory`)

```java
SimpleRegistry registry = SimpleRegistry.newBuilder()
        .add(SimpleClassAdapter.newBuilder(Point.class)
                .factory(() -> new Point(0, 0))
                .reflection(f -> !f.getName().equals("cache"))
                .build())
        .build();

Point p = registry.deserialize(Point.class, JsonObject.of("x", 4, "y", 5));
// p.x -> 4, p.y -> 5
```

## Class factory

Rather than listing every class, a registry can create adapters on demand. `classFactory` is called for a class that has no adapter yet; it returns an adapter, or `null` to refuse the class. The result is cached, refusals included, so the factory is called once per class, and a self-referencing class (`Node next`) finds its own adapter while it is being built. An exception thrown by the factory, or by the initialization of the adapter, is not cached: the next lookup tries again.

Sample: `doc_examples/serialization/SerializationExamples.java` (`testClassFactory`)

```java
SimpleRegistry registry = SimpleRegistry.newBuilder()
        .classFactory(clazz -> clazz.getName().startsWith("doc_examples.")
                ? SimpleClassAdapter.newBuilder(clazz).reflection().build()
                : null)
        .build();

Node n = new Node();
n.name = "a";
n.next = new Node();
n.next.name = "b";

JsonObject json = registry.serialize(n);         // no explicit add(Node.class)
// -> {"name":"a","next":{"name":"b","next":null}}
```

Restricting the factory to your own packages keeps the registry from reflecting over classes it was never meant to handle; returning `null` makes such a class fail with `Missing adapter`.

## Errors

The errors are `JsonException`s. An error inside an object graph is a `SerializationException` (a `JsonException`) whose message ends with the location of the value, `getPath()` returning it alone: a field or a map key is `.name` (`['a b']` when it is not an identifier), an item `[index]`. The original exception is kept as the cause.

Sample: `doc_examples/serialization/SerializationExamples.java` (`testErrorPaths`)

```java
registry.deserialize(Basket.class, JsonObject.of("lines",
        JsonArray.of(JsonObject.of("quantity", 1), JsonObject.of("quantity", 1.5))));
// -> SerializationException: Number 1.5 has a fraction, cannot convert it to int (at $.lines[1].quantity)
```

A JSON value of the wrong type names the expected and the actual types: `Cannot deserialize a JSON string into a ...Line: a JSON object is expected`.

## Limits

| Limit | Details |
|---|---|
| No-arg constructor | Reading needs a no-arg constructor or a `factory` (a record uses its canonical constructor); otherwise a `JsonException` wraps the `NoSuchMethodException`. Serializing does not need one. |
| No cycles | An object graph with a cycle cannot be serialized: it throws a `JsonException`. Shared references, without a cycle, are serialized once per occurrence and read back as distinct objects. |
| Depth | A graph nested deeper than 1000 levels, in either direction, throws a `JsonException` instead of overflowing the stack; `maxDepth(n)` changes the limit. |
| Default registry | `defaultRegistry(true)` publishes the registry as `SimpleRegistry.get()`; only one such registry may be defined at a time, `SimpleRegistry.clearDefault()` forgets it. |

Sample: `doc_examples/serialization/SerializationExamples.java` (`testExactClassLookup`, `testCollectionsAndEnums`, `testDeclaredTypesAndSharedReferences`, `testCyclesDetected`, `testNoArgConstructorRequired`, `testMaxDepth`)

```java
registry.serialize(new ArrayList<>(List.of("a")));        // -> ["a"]
registry.serialize(Color.RED);                            // -> "RED"
try {
    registry.serialize(account);                          // Account extends the registered Base
} catch(JsonException e) {
    // Missing adapter for Java class ...Account
}
// Typed elements for a top-level collection: pass the parameters
ClassAdapter[] strings = { registry.findAdapter(String.class) };
Object json = registry.findAdapter(List.class).serialize(new ArrayList<>(List.of("a")), strings);
// -> ["a"]
```

## Security

Deserializing a JSON text builds Java objects from data that may come from outside, so:

- The registry only creates the classes it knows: the registered ones, the built-in types, the classes accepted by the class factory and, with `sealedTypes()`, the classes of the sealed hierarchies in use. The JSON never names an arbitrary class. Keep the class factory restricted to your own packages.
- Reading bypasses the invariants of a class: after the no-arg constructor (or the factory), the fields are assigned directly by reflection, `private` and `final` fields included, without going through the setters or any validation. A record goes through its canonical constructor, so its checks run. Validate the objects read, or use records for the types that need invariants.
- The size of the work is bounded: a number is checked before it is expanded (a `BigInteger` holds up to 10,000 digits), the nesting depth is limited (`maxDepth`), and the JSON parser has its own depth limit.
- `JsonObject`, `JsonArray` and `Object` values are deep copied, so an object read does not share mutable state with the JSON document it came from.
