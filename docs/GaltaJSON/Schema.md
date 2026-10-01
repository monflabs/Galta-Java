# Schema Metadata

`SchemaNode` is a read-only view over a JSON Schema object that answers type questions: what is the declared type, is it nullable, what are the properties, and which Java (`SchemaType`) or JDBC (`java.sql.JDBCType`) type best represents a value. Generators and import/export code use it to map JSON data to columns and Java types. It does not validate documents: validation is provided by the [JSON Schema module](/GaltaJSON/Modules/JsonSchema).

## Reading a schema

`new SchemaNode(jsonObject)` wraps a schema object; `wrapped()` returns it for anything the view does not cover.

| Method | Result |
|---|---|
| `getId()`, `getSchema()`, `getTitle()`, `getDescription()` | `$id`, `$schema`, `title`, `description`, or `null` |
| `getType()` | `type` when it is a string; for an array of types, the main type (see below) |
| `getMainType()` | The single type, or for `[type, "null"]` / `["null", type]` the non-null one; `null` when missing or ambiguous |
| `isNullable()` | `type` is `"null"` or an array containing `"null"` |
| `isNull()`, `isBoolean()`, `isNumber()`, `isString()` | Compare the main type |
| `isObject()`, `isArray()` | Compare the main type; both are `true` when there is no type |
| `getProperties()` | `properties` as a `Map<String,SchemaNode>` in document order, or `null` |
| `schemaType()` | The `SchemaType` (below); throws a `JsonException` for a missing, ambiguous or `"null"` type |
| `jdbcType()` | The `JDBCType` (below); throws a `JsonException` for a missing or ambiguous type, and for `object`, `array` and `null` |

Note that `isNumber()` is true only for `"number"`, not for `"integer"`.

Sample: `doc_examples/json/SchemaExamples.java` (`testSchemaNode`)

```java
SchemaNode person = new SchemaNode(JsonObject.parse(PERSON));

person.getId();         // "person.json"
person.getSchema();     // "https://json-schema.org/draft/2020-12/schema"
person.getTitle();      // "Person"
person.getType();       // "object"
person.isObject();      // true
person.schemaType();    // SchemaType.OBJECT

Map<String,SchemaNode> props = person.getProperties();
props.keySet();                                   // [name, born, updated, age, salary, tags]
props.get("name").getDescription();               // "Full name"
props.get("tags").wrapped().getObject("items").getString("type");   // "string"
```

with this schema as `PERSON`:

```json
{
  "$id": "person.json",
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "Person",
  "type": "object",
  "properties": {
    "name":    { "type": "string", "description": "Full name" },
    "born":    { "type": "string", "format": "date" },
    "updated": { "type": "string", "format": "date-time", "mf-format-ex": "date-time-tz" },
    "age":     { "type": ["integer", "null"], "mf-jdbc": { "jdbc-type": "SMALLINT" } },
    "salary":  { "type": "number", "mf-jdbc": { "jdbc-type": "DECIMAL" } },
    "tags":    { "type": "array", "items": { "type": "string" } }
  }
}
```

### Nullable and ambiguous types

Sample: `doc_examples/json/SchemaExamples.java` (`testNullableTypes`)

```java
SchemaNode age = new SchemaNode(JsonObject.parse("{\"type\":[\"integer\",\"null\"]}"));
age.getType();          // "integer": the main type of a [type, "null"] pair
age.getMainType();      // "integer"
age.isNullable();       // true

SchemaNode mixed = new SchemaNode(JsonObject.parse("{\"type\":[\"integer\",\"string\"]}"));
mixed.getMainType();    // null: ambiguous
mixed.schemaType();     // throws JsonException

SchemaNode untyped = new SchemaNode(JsonObject.create());
untyped.isObject() || untyped.isArray();   // false: no type, neither
new SchemaNode(JsonObject.parse("{\"properties\":{}}")).isObject();   // true: no type, but properties
mixed.isObject() || mixed.isArray();       // false: an ambiguous type is neither
untyped.getProperties();                   // null
```

## Java and JDBC types

`schemaType()` and `jdbcType()` map the main type, its `format`, and two extension attributes:

| Attribute | Content | Used for |
|---|---|---|
| `mf-format-ex` | `"time-tz"` or `"date-time-tz"` | A time or date-time *with* an offset |
| `mf-jdbc` | An object whose `jdbc-type` is a `java.sql.JDBCType` name (other members, such as `TYPE_NAME` or `COLUMN_SIZE`, are ignored here) | The exact numeric type |

`SchemaNode.FORMAT_EX`, `SchemaNode.JDBC_EXTENSIONS` and `SchemaNode.JDBC_TYPE` hold these names; the `mf-` prefix marks them as vendor extensions (JSON Schema validators ignore unknown keywords).

| Schema | `schemaType()` | `jdbcType()` |
|---|---|---|
| `string` | `STRING` | `VARCHAR` |
| `string`, format `date` | `LOCALDATE` | `DATE` |
| `string`, format `time` | `LOCALTIME` | `TIME` |
| `string`, format `time`, `mf-format-ex: time-tz` | `OFFSETTIME` | `TIME_WITH_TIMEZONE` |
| `string`, format `date-time` | `LOCALDATETIME` | `TIMESTAMP` |
| `string`, format `date-time`, `mf-format-ex: date-time-tz` | `OFFSETDATETIME` | `TIMESTAMP_WITH_TIMEZONE` |
| `integer` | `INTEGER` | `INTEGER` |
| `integer`, `jdbc-type` `TINYINT` / `SMALLINT` / `INTEGER` / `BIGINT` | `BYTE` / `SHORT` / `INTEGER` / `LONG` | the same `JDBCType` |
| `number` | `NUMBER` | `DOUBLE` |
| `number`, `jdbc-type` `REAL` | `FLOAT` | `REAL` |
| `number`, `jdbc-type` `FLOAT` / `DOUBLE` | `DOUBLE` | the same `JDBCType` |
| `number`, `jdbc-type` `NUMERIC` / `DECIMAL` | `BIGDECIMAL` | the same `JDBCType` |
| `boolean` | `BOOLEAN` | `BOOLEAN` |
| `object` / `array` | `OBJECT` / `ARRAY` | throws |

Other formats (`email`, `uri`, ...) are plain strings, and a `jdbc-type` that does not fit the JSON type is ignored. `SchemaType.getJsonType()` gives the underlying [`JsonType`](/GaltaJSON/Values#the-value-model), and `isNumber()`, `isString()`, ... test it.

Sample: `doc_examples/json/SchemaExamples.java` (`testSchemaAndJdbcTypes`)

```java
Map<String,SchemaNode> props = new SchemaNode(JsonObject.parse(PERSON)).getProperties();

props.get("name").schemaType();       // STRING
props.get("name").jdbcType();         // VARCHAR
props.get("born").schemaType();       // LOCALDATE
props.get("born").jdbcType();         // DATE
props.get("updated").schemaType();    // OFFSETDATETIME
props.get("updated").jdbcType();      // TIMESTAMP_WITH_TIMEZONE
props.get("age").schemaType();        // SHORT
props.get("age").jdbcType();          // SMALLINT
props.get("salary").schemaType();     // BIGDECIMAL
props.get("salary").jdbcType();       // DECIMAL
props.get("tags").schemaType();       // ARRAY
props.get("tags").jdbcType();         // throws JsonException: no JDBC type for containers

SchemaType.SHORT.getJsonType();       // JsonType.NUMBER
SchemaType.LOCALDATE.isString();      // true
```

Sample: `doc_examples/json/SchemaExamples.java` (`testDefaults`)

```java
schemaType("{\"type\":\"integer\"}");                                        // INTEGER
schemaType("{\"type\":\"number\"}");                                         // NUMBER
schemaType("{\"type\":\"string\",\"format\":\"time\"}");                     // LOCALTIME
schemaType("{\"type\":\"string\",\"format\":\"time\",\"mf-format-ex\":\"time-tz\"}");   // OFFSETTIME
schemaType("{\"type\":\"string\",\"format\":\"date-time\"}");                // LOCALDATETIME
schemaType("{\"type\":\"string\",\"format\":\"email\"}");                    // STRING
schemaType("{\"type\":\"integer\",\"mf-jdbc\":{\"jdbc-type\":\"BIGINT\"}}"); // LONG
schemaType("{\"type\":\"number\",\"mf-jdbc\":{\"jdbc-type\":\"REAL\"}}");    // FLOAT
new SchemaNode(JsonObject.parse("{\"type\":\"number\"}")).jdbcType();        // DOUBLE
// A jdbc-type that does not fit the JSON type is ignored
schemaType("{\"type\":\"integer\",\"mf-jdbc\":{\"jdbc-type\":\"DECIMAL\"}}");   // INTEGER
// isNumber() is only for "number"
new SchemaNode(JsonObject.parse("{\"type\":\"integer\"}")).isNumber();       // false

private static SchemaType schemaType(String json) {
    return new SchemaNode(JsonObject.parse(json)).schemaType();
}
```

`SchemaType` also has `BIGINTEGER` and `ZONEDDATETIME`, which `schemaType()` never returns; they are there for code that builds type information by other means.

## Loading schemas

`SchemaFactory` is the base for schema loaders: `getSchema(uri)` returns a `SchemaNode`, or `null` when the loader does not know the URI. `PathSchemaFactory(directory, baseUri)` loads the file `<directory>/<path>` for a URI `<baseUri>/<path>` (with a `null` base, for any relative URI). A file reached through a symbolic link pointing outside of the directory is not loaded either.

`getSchema(uri)` resolves the `$ref` references of the schema (`getSchema(uri, false)` doesn't), as [JSON References](/GaltaJSON/Pointers#json-references) in schema mode: a reference object is replaced by the schema it designates, so the `SchemaNode` tree never shows a `$ref` that could be resolved. A relative reference is resolved against the URI of the schema holding it and loaded with the same factory, each schema being loaded once; a reference to the `$id` of a loaded (sub)schema or to a `$anchor` (`#name`) needs no loading; the data keywords (`const`, `enum`, `default`, `examples`) are left as they are. A reference that cannot be resolved is a `JsonException`.

A recursive schema (a tree node whose `children` refer to the node itself, or two schemas referring to each other) resolves to a **cyclic** graph of nodes: walk it with a visited set, and don't `deepClone()`, compare, hash or plainly `stringify()` it, as these recurse without end.

Sample: `doc_examples/json/SchemaExamples.java` (`testPathSchemaFactory`)

```java
Path dir = Files.createTempDirectory("schemas");
Files.writeString(dir.resolve("person.json"), PERSON);
PathSchemaFactory factory = new PathSchemaFactory(dir, "https://example.com/schemas");

SchemaNode person = factory.getSchema("https://example.com/schemas/person.json");
person.getTitle();                                           // "Person"
factory.getSchema("https://example.com/schemas/other.json"); // null: no such file
factory.getSchema("https://elsewhere.com/person.json");      // null: not under the base URI
```

## Source

`jsonschema/SchemaNode.java`, `jsonschema/SchemaType.java`, `jsonschema/SchemaFactory.java`, `jsonschema/PathSchemaFactory.java`, under `parent-json/json/src/main/java/org/monflabs/json/`.
