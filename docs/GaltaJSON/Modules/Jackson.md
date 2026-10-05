# Jackson

The `json-jackson` module makes the GaltaJSON values regular [Jackson](https://github.com/FasterXML/jackson) types. With its `GaltaJsonModule` registered in an `ObjectMapper`, `JsonObject`, `JsonArray` and `JsonContainer` can be read and written by Jackson, used as fields of Java objects, and converted to and from any Java object or Jackson tree. Java objects are mapped by Jackson, with its rules and annotations, and GaltaJSON keeps doing what it is for: the values themselves, JSON Path, JSON Pointer, schemas and the GaltaJS engine.

```xml
<dependency>
    <groupId>org.monflabs.galta</groupId>
    <artifactId>json-jackson</artifactId>
</dependency>
```

The module brings `com.fasterxml.jackson.core:jackson-databind` 2.22.3. An application's own Jackson version takes precedence (Maven's nearest-wins rule), Spring Boot's included.

## Reading and writing

Sample: `doc_examples/jackson/JacksonExamples.java` (`testReadAndWrite`)

```java
ObjectMapper mapper = new ObjectMapper().registerModule(new GaltaJsonModule());

JsonObject o = mapper.readValue("{\"name\":\"Ann\",\"tags\":[\"a\",\"b\"],\"score\":1.5}", JsonObject.class);
o.getArray("tags").getString(1);        // "b": regular GaltaJSON values
o.put("active", true);
mapper.writeValueAsString(o);           // {"name":"Ann","tags":["a","b"],"score":1.5,"active":true}
```

The containers are created by the factory given to the module (`new GaltaJsonModule(factory)`, `JsonFactory.get()` by default), and the numbers follow its rules, as when GaltaJSON parses the text: an `Integer` when it fits, then a `Long` or a `BigInteger`, a `Double`, or a `BigDecimal` for a decimal a double can't hold exactly (see [Values](/GaltaJSON/Values)). A `BigDecimal` coming from a Java object stays a `BigDecimal`. Asking for a `JsonObject` when the JSON is an array (or the reverse) is a `MismatchedInputException`; `JsonContainer` accepts both.

When a container is written, the factory of the container decides how each value is written (`JsonFactory.exportValue()`): a value JSON can't represent is left out of an object and written as `null` in an array. NaN and the infinities are written as `null` and a `java.util.Date` as its ISO-8601 instant, as GaltaJSON's own stringifier does. A value GaltaJSON doesn't know, like a Java object put in a container, is written by Jackson. A container that contains itself is a `JsonMappingException`; the same container referenced twice is written twice.

## Java objects

`convertValue()` turns a Java object into GaltaJSON values and back, with Jackson's mapping: records, getters and fields, `@JsonProperty`, `@JsonIgnore`, custom serializers, and the modules registered in the mapper.

Sample: `doc_examples/jackson/JacksonExamples.java` (`testJavaObjects`)

```java
public record Line(String product, int quantity) {}
public static class Order {
    public String id;
    public List<Line> lines;
    public BigDecimal total;
}

Order order = new Order();
order.id = "A1";
order.lines = List.of(new Line("pen", 3));
order.total = new BigDecimal("4.50");

JsonObject o = mapper.convertValue(order, JsonObject.class);
o.getArray("lines").getObject(0).getString("product");   // "pen"
o.get("total");                                          // BigDecimal 4.50

o.getArray("lines").getObject(0).put("quantity", 5);
Order changed = mapper.convertValue(o, Order.class);      // changed.lines.get(0).quantity() == 5
```

A `JsonObject` or `JsonArray` field holds any JSON content: what Jackson reads into it, at any depth, is GaltaJSON values.

Sample: `doc_examples/jackson/JacksonExamples.java` (`testGaltaValuesInJavaObjects`)

```java
public static class Event {
    public String type;
    public JsonObject payload;
}

Event e = mapper.readValue("{\"type\":\"login\",\"payload\":{\"user\":\"ann\",\"roles\":[\"admin\"]}}", Event.class);
e.payload.getArray("roles").getString(0);   // "admin"
```

## Jackson trees

Sample: `doc_examples/jackson/JacksonExamples.java` (`testJsonNode`)

```java
JsonArray a = JsonArray.of(1, "x", JsonObject.of("k", true));
JsonNode node = mapper.valueToTree(a);                          // GaltaJSON -> JsonNode
node.get(2).get("k").asBoolean();                               // true
JsonArray back = mapper.convertValue(node, JsonArray.class);    // JsonNode -> GaltaJSON
```

## With GaltaJS

The JavaScript objects and arrays of GaltaJS are `JsonObject`s and `JsonArray`s. Give the module the factory of the environment, and what Jackson reads are JavaScript objects a script can use as its own. Writing a script value gives what `JSON.stringify()` gives: `undefined`, the functions and the symbols are left out.

```java
ObjectMapper mapper = new ObjectMapper().registerModule(new GaltaJsonModule(env.getJsonFactory()));
JsonObject item = mapper.convertValue(new Item("pen", 3), JsonObject.class);   // a JavaScript object
// ... a script changes it ...
Item changed = mapper.convertValue(item, Item.class);
```

A script can use the mapper itself like any Java object, for example when the application exposes it as a global (see [Java Interop](/GaltaJS/UserGuide/JavaInterop)).

## Gotchas

- Register the module with the factory of the environment for GaltaJS: containers created by the default factory are Java containers, which can't be put inside a JavaScript object.
- `writeValueAsString()` writes numbers the Jackson way, which is valid JSON but not always the same text as GaltaJSON's stringifier (`1.0E21`, not `1e+21`). Use `stringify()` for GaltaJSON's output.
- Only the interface types are handled (`JsonObject`, `JsonArray`, `JsonContainer`): for a field declared with an implementation class (`JsonObjectAsLinkedMap`), Jackson reads the objects nested in it as plain `Map`s. Declare the interface.

## Source

`galta/parent-json/json-jackson/src/main/java/org/monflabs/json/jackson/GaltaJsonModule.java`, `JsonFactory.exportValue()` in the `json` module.
