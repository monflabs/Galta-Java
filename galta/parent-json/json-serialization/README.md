# Galta JSON Serialization

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/json-serialization?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/json-serialization)

Converts plain Java objects and records to GaltaJSON values and back. It is
reflection based, uses no annotations and has no third-party dependency: a
*registry* holds one *class adapter* per Java class, and each adapter turns an
instance into a JSON value and back. The JSON side is made of regular
`JsonObject`/`JsonArray` values, ready to be stringified, queried or handed to
the GaltaJS engine.

The registry only creates the classes it knows (registered classes, built-in
types, classes accepted by a class factory, sealed hierarchies when enabled).
Reading needs a no-arg constructor (a record uses its canonical constructor),
and an object graph with a cycle cannot be serialized.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>json-serialization</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare a `<version>` directly.

```java
// Person: a class with String name, int age, List<String> tags and a no-arg constructor
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
```

## Contents

Under `org.monflabs.json.serialization`:

- `JsonRegistry`, `SimpleRegistry` - the thread-safe registry, built once with its classes, a class factory, sealed type discriminators and a max depth
- `ClassAdapter`, `FieldAdapter`, `LambdaFieldAdapter` - the adapter contracts, for custom class and field mappings
- `TypeRef` - captures a generic type (`new TypeRef<List<Item>>() {}`)
- `SerializationException` - an error located in the object graph (`$.lines[1].quantity`)
- `classes` - the built-in class adapters: scalars, numbers, enums, `java.time`/`UUID`/`URI`/`Date` as strings, arrays, collections, maps, `Optional`, JSON values, sealed types
- `fields` - the field adapters: reflection fields, record components, generic type resolution

## Documentation

- [Serialization](../../../docs/GaltaJSON/Modules/Serialization.md) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Modules/Serialization>
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
