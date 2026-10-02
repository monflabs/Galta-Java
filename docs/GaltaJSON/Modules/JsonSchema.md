# JSON Schema Validation

The core library reads and navigates JSON Schema documents (see [Schema](/GaltaJSON/Schema)) but does not validate data. The `json-jsonschema-jsonschemafriend` module adds validation, backed by [jsonschemafriend](https://github.com/jimblackler/jsonschemafriend), which supports drafts 3, 4, 6, 7, 2019-09 and 2020-12. It validates GaltaJSON values directly, without converting them.

```xml
<dependency>
    <groupId>org.monflabs.galta</groupId>
    <artifactId>json-jsonschema-jsonschemafriend</artifactId>
</dependency>
```

jsonschemafriend comes from Maven Central, as `org.metaeffekt.bundle.jsonschemafriend:ae-jsonschemafriend-core`: the original project is only published on JitPack, and this is the same code, published on Central by metaeffekt. No extra repository is needed.

Two classes, in `org.monflabs.json.jsonschema`:

| Class | Role |
|---|---|
| `JsonSchemaFactory` | Builds a `JsonSchema` from a schema document or a URI. `JsonSchemaFactory.get()` is a shared instance; `new JsonSchemaFactory()` creates an independent one. |
| `JsonSchema` | `validate(value)` returns normally when the value is valid, and throws a `JsonException` otherwise. |

## Validating

Sample: `doc_examples/jsonschema/JsonSchemaExamples.java` (`testValidate`)

```java
JsonSchema schema = JsonSchemaFactory.get().getJsonSchema(JsonObject.parse("""
        {
          "$schema": "https://json-schema.org/draft/2020-12/schema",
          "type": "object",
          "properties": {
            "name": { "type": "string", "minLength": 2 },
            "age":  { "type": "integer", "minimum": 0 },
            "tags": { "type": "array", "items": { "$ref": "#/$defs/tag" } }
          },
          "required": ["name"],
          "$defs": { "tag": { "type": "string" } }
        }
        """));

schema.validate(JsonObject.parse("{ \"name\": \"Ada\", \"age\": 36, \"tags\": [\"math\"] }"));   // valid

schema.validate(JsonObject.parse("{ \"name\": \"A\", \"age\": -1 }"));
// JsonException:
// Validation errors: "A" at #/name failed against #/properties/name with "Shorter than minLength: 2"
// -1 at #/age failed against #/properties/age with "Less than minimum: 0"
```

Any JSON value can be validated, not only objects (`schema.validate("text")`). Local `$ref`s (`#/$defs/...`) resolve within the document.

## Error reporting

The exception message lists every error, one per line: the offending value, its location in the document as a JSON Pointer fragment (`#/age`), the schema location, and the reason. To process the errors one by one, use the cause, a jsonschemafriend `ListValidationException`:

Sample: `doc_examples/jsonschema/JsonSchemaExamples.java` (`testErrorDetails`, `testAnyValue`)

```java
try {
    schema.validate(JsonObject.parse("{ \"name\": \"A\", \"age\": -1, \"tags\": [1] }"));
} catch(JsonException e) {
    ListValidationException cause = (ListValidationException)e.getCause();
    for(ValidationError error: cause.getErrors()) {
        System.out.println(error.getUri() + ": " + error.getMessage());
    }
    // #/name: Shorter than minLength: 2
    // #/age: Less than minimum: 0
    // #/tags/0: Expected: [string] Found: [number, integer]
}
```

A `ValidationError` also exposes the offending value (`getObject()`) and the failing schema (`getSchema()`). A missing required property is reported at the object: `{"age":3} at root failed with "Missing property name"`.

## Ad-hoc schemas and URI-addressed schemas

| Method | Use |
|---|---|
| `getJsonSchema(JsonObject)` | A schema held in memory. Each call builds an independent schema: nothing is cached or retained by the factory. |
| `getJsonSchema(URI)`, `getJsonSchema(String uri)` | A schema loaded from a URI (`file:`, `http:`...). Relative `$ref`s resolve against it. The factory caches it by URI. |

Sample: `doc_examples/jsonschema/JsonSchemaExamples.java` (`testUriSchemas`)

```java
// address.json: { "type": "object", "properties": { "city": { "type": "string" } }, "required": ["city"] }
// person.json:  { "type": "object", "properties": { "address": { "$ref": "address.json" } } }
JsonSchema person = JsonSchemaFactory.get().getJsonSchema(dir.resolve("person.json").toUri());
person.validate(JsonObject.parse("{ \"address\": { \"city\": \"Paris\" } }"));
person.validate(JsonObject.parse("{ \"address\": { } }"));   // JsonException: ... "Missing property city"
```

Because of the cache, a factory never sees later changes to a URI-addressed schema, nor to the documents it references; use a new `JsonSchemaFactory` to reload them. An invalid URI string throws a `JsonException`.

## Things to know

| Topic | Behaviour |
|---|---|
| `$schema` | Declare it. Without `$schema` (and without `$id`), jsonschemafriend reads the schema as **draft-04**, where for instance `36.0` is not an `integer`; with draft 6 and later it is. |
| Missing schema | A URI that cannot be loaded, a `$ref` to one, or a `$ref` that designates nothing (`#/$defs/nope`) makes `getJsonSchema()` throw a `JsonException` (fail closed). jsonschemafriend itself would only log a warning and use a schema that **accepts everything**; `factory.setFailOnUnresolvedReferences(false)` restores that behavior. A failed URI load is not cached. |
| Loading | `new JsonSchemaFactory(loader)` takes a jsonschemafriend `Loader` for the documents designated by URIs; `JsonSchemaFactory.NO_LOADING` loads nothing (only in-memory schemas, what they embed and the bundled meta-schemas are available), so a remote `$ref` is an error. |
| Trusted schemas only | A schema URI or a `$ref` is loaded whatever its scheme: `http(s):` makes a network request, `file:`/`jar:` read local files. Only use schemas from a trusted source, or create the factory with a restricted jsonschemafriend `Loader`: `new JsonSchemaFactory(JsonSchemaFactory.NO_LOADING)` (see Loading). |
| The schema itself | Is not validated against its meta-schema: an invalid keyword value such as `"type": 12` is silently ignored. |
| Formats | `format` is asserted, e.g. `"format": "email"` rejects `not-an-email`. |
| Numbers | GaltaJSON number types (`Integer`, `Long`, `Double`, `BigDecimal`...) are all accepted. |

Sample: `doc_examples/jsonschema/JsonSchemaExamples.java` (`testDraftDetection`, `testMissingSchemaAcceptsEverything`, `testSchemaIsNotValidated`, `testFormatsAreAsserted`)

## Thread safety

A `JsonSchemaFactory` can be shared between threads: loads by URI are serialized on the factory's cache, and ad-hoc schemas use no shared state. A `JsonSchema` can be shared as well; each `validate()` call uses its own validator.

Sample: `doc_examples/jsonschema/JsonSchemaExamples.java` (`testSharedSchema`)
