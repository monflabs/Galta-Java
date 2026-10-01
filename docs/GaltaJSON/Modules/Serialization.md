# Serialization

The `json-serialization` module converts plain Java objects to GaltaJSON values and back. It is reflection-based, has no annotations and no third-party dependency: a *registry* holds one *class adapter* per Java class, and each adapter turns an instance into a `JsonObject` (or any other JSON value) and back. The JSON side is made of the regular GaltaJSON values described in [Values](/GaltaJSON/Values), so the result can be stringified, queried with [JSON Path](/GaltaJSON/JsonPath) or handed to the JavaScript engine as is.

```xml
<dependency>
    <groupId>org.monflabs.galta</groupId>
    <artifactId>json-serialization</artifactId>
</dependency>
```

## Getting started

A `SimpleRegistry` is built once, with the classes it has to handle, and is then used for both directions. `add(Class)` creates an adapter that reads the class fields by reflection.

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

The registry methods are:

| Method | Does |
|---|---|
| `serialize(obj)` | Returns the JSON value for `obj` (`null` for `null`). The adapter is found from `obj.getClass()`. |
| `serialize(obj, genericParams)` | Same, binding the type parameters of a generic class (see [Generics](#generics)). |
| `serialize(writer, obj, genericParams, compact)` | Serializes and writes the JSON text. |
| `deserialize(clazz, jsonValue[, genericParams])` | Creates a `clazz` instance from a JSON value (`null` for `null`). |
| `deserialize(clazz, reader)` | Parses the text, then deserializes it. |
| `findAdapter(clazz)` | Returns the adapter for a class, or throws a `JsonException` when there is none. |

Sample: `doc_examples/serialization/SerializationExamples.java` (`testReaderWriter`)

```java
Person p = registry.deserialize(Person.class,
        new StringReader("{\"name\":\"Alan\",\"age\":41}"));
// p.tags -> null: absent keys leave the field untouched

StringWriter w = new StringWriter();
registry.serialize(w, p, null, true);             // compact
// -> {"name":"Alan","age":41,"tags":null}: null fields are written as null
```

When reading, a JSON key that has no matching field is an error (`Object doesn't have a field named email`), while a key whose value is `null` is skipped, whatever its name.

Sample: `doc_examples/serialization/SerializationExamples.java` (`testUnknownKeyFails`)

`serialize()` is generic in its return type (`<T> T serialize(Object)`). Nested directly inside `deserialize()`, Java infers the `Reader` overload and the call fails at runtime with a `ClassCastException`. Assign the result to a variable first:

Sample: `doc_examples/serialization/SerializationExamples.java` (`testTypedResultPitfall`)

```java
// registry.deserialize(Person.class, registry.serialize(p));  -> ClassCastException
Object json = registry.serialize(p);
Person back = registry.deserialize(Person.class, json);
```

## Which fields are serialized

Reflection walks the class and all its superclasses and keeps every instance field, whatever its visibility (`private` and `final` fields included). It skips:

- `static` fields (constants, `serialVersionUID`...),
- `transient` fields,
- synthetic fields, like the `this$0` reference an inner class holds to its outer instance.

A field declared in a subclass hides a superclass field with the same name. The JSON key is the Java field name, and the keys follow the declaration order, the superclass fields first.

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

## Supported types

A new registry already contains adapters for:

| Java type | JSON |
|---|---|
| `boolean`, `byte`, `short`, `int`, `long`, `float`, `double` and their boxed types | boolean / number; reading a number into an integral type is exact: `1.5` or a value out of range throws a `JsonException` instead of being truncated |
| `String` | string |
| `char`, `Character` | one character string |
| `BigInteger`, `BigDecimal` | number (the instance is kept as is) |
| `boolean[]`, `byte[]`, `short[]`, `int[]`, `long[]`, `float[]`, `double[]` | array of numbers / booleans |
| `char[]` | string (also read from an array of one character strings) |
| Any other array, multi-dimensional included (`String[][]`, `Item[]`) | array; the component type is preserved when reading back |
| Enums | string: the constant name |
| `List<T>`, `Set<T>`, and their implementations (`ArrayList<T>`...) | array (read back as `ArrayList` / `LinkedHashSet`, or as the declared class when it has a public no-arg constructor) |
| `Collection<T>`, `Iterable<T>` | array (read back as an `ArrayList`) |
| `Map<K,V>`, and its implementations | object (read back as `LinkedHashMap`, or as the declared class). String keys are kept; number, boolean, character and enum keys use their string form and are parsed back to the key type; other keys must serialize to strings |
| `JsonObject`, `JsonArray`, `Object` | passed through unchanged; a value declared as `Object` (in a raw `List`, a `Map<String,Object>`...) that is not a JSON value is serialized with the adapter of its class when the registry has one |
| Registered classes | object |
| Registered records | object, through the accessors; read back with the canonical constructor (a missing component is `null`, or `0`/`false` for a primitive) |

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

## Generics

Type variables are resolved from wherever they are bound:

- by the declared type of a field (`Page<Item> items`),
- by a subclass, directly or through intermediate generic classes (`class ItemPage extends Page<Item>`),
- for a top-level generic object, by passing the adapters of the type arguments, in declaration order, to `serialize`/`deserialize`.

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

## Custom adapters

### Class adapters

A class that should not be serialized field by field gets its own `ClassAdapter`. Extending `BaseClassAdapter` provides `getAdaptedClazz()`; register the instance with `add(adapter)`. It then applies wherever the class is used: top-level objects, fields, array items or collection values.

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

Registering an adapter for a class replaces the previous one, built-in adapters included.

### Field adapters

`add(Class, builder -> ...)` defines the JSON properties one by one with a reader and a writer lambda. The writer receives the raw JSON value, so numbers arrive as `Number`. A property does not have to map to a field: it can be computed, and its writer can ignore the value. It can be combined with `reflection()` in the same builder.

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

`SimpleClassAdapter.Builder` also accepts `add(name, FieldAdapter)` for a reusable implementation of the `FieldAdapter` interface (`readProperty` / `writeProperty`).

### Instance factory

Reading creates the instance with the class's no-argument constructor, which may be private. A class without one needs a `factory`; reflection then overwrites the fields, `final` ones included.

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

Rather than listing every class, a registry can create adapters on demand. `classFactory` is called for a class that has no adapter yet; it returns an adapter, or `null` to refuse the class. The result is cached, so the factory is called once per class, and a self-referencing class (`Node next`) finds its own adapter while it is being built.

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

## Limitations

| Limitation | Details |
|---|---|
| No-arg constructor | Reading needs a no-arg constructor or a `factory` (a record uses its canonical constructor); otherwise a `JsonException` wraps the `NoSuchMethodException`. Serializing does not need one. |
| No cycles | An object graph with a cycle cannot be serialized: it throws a `JsonException`. Shared references, without a cycle, are serialized once per occurrence and read back as distinct objects. |
| Exact class lookup | Registered adapters are looked up by the exact class: an instance of an unregistered subclass is not found under its parent, register it. Collections, maps and enums are found under their interface; at the top level, pass the adapters of the type arguments to get typed elements, e.g. `registry.findAdapter(List.class).serialize(list, params)`. |
| Declared types | A subclass instance stored in a field typed with its parent is serialized with the adapter of its own class when the registry has one (registered, or created by the class factory); otherwise with the adapter of the declared type, and its extra fields are lost. The JSON carries no type: it is always read back as the declared type. |
| No dates | No built-in adapter for the `java.time` types: provide a class adapter, or exclude the field. |
| Default registry | `defaultRegistry(true)` publishes the registry as `SimpleRegistry.get()`; only one such registry may be defined at a time, `SimpleRegistry.clearDefault()` forgets it. |
| One registry per adapter | A class adapter is bound to the first registry that uses it; adding the same adapter instance to another registry throws a `JsonException`. Build one adapter per registry. |

Sample: `doc_examples/serialization/SerializationExamples.java` (`testExactClassLookup`, `testCollectionsAndEnums`, `testDeclaredTypesAndSharedReferences`, `testCyclesDetected`, `testNoArgConstructorRequired`)

```java
registry.serialize(new ArrayList<>(List.of("a")));        // -> ["a"]
registry.serialize(Color.RED);                            // -> "RED"
try {
    registry.serialize(account);                          // Account extends the registered Base
} catch(JsonException e) {
    // Missing adapter for Java class class ...Account
}
// Typed elements for a top-level collection: pass the parameters
ClassAdapter[] strings = { registry.findAdapter(String.class) };
Object json = registry.findAdapter(List.class).serialize(new ArrayList<>(List.of("a")), strings);
// -> ["a"]
```
