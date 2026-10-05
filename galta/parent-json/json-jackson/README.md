# Galta JSON - Jackson

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/json-jackson?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/json-jackson)

Makes the GaltaJSON values regular [Jackson](https://github.com/FasterXML/jackson)
types. With `GaltaJsonModule` registered in an `ObjectMapper`, `JsonObject`,
`JsonArray` and `JsonContainer` are read and written by Jackson, can be fields of
Java objects, and convert to and from any Java object or `JsonNode`. Java objects
are mapped by Jackson, with its rules and annotations.

The containers are created by the factory given to the module, with its number
rules: with the factory of a GaltaJS environment they are JavaScript objects, and
writing a script value gives what `JSON.stringify()` gives.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>json-jackson</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see [Modules](../../../README.md#modules)),
or declare a `<version>` directly. The module brings `jackson-databind` 2.22.3; an
application's own Jackson version takes precedence.

```java
ObjectMapper mapper = new ObjectMapper().registerModule(new GaltaJsonModule());
JsonObject o = mapper.readValue(text, JsonObject.class);        // parse with Jackson
JsonObject p = mapper.convertValue(order, JsonObject.class);    // a Java object as JSON values
Order back = mapper.convertValue(p, Order.class);               // and back
JsonNode n = mapper.valueToTree(o);                             // to the Jackson tree model
```

## Documentation

- [Jackson](../../../docs/GaltaJSON/Modules/Jackson.md) - <https://monflabs.github.io/Galta-Java/#/GaltaJSON/Modules/Jackson>
