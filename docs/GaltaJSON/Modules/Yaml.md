# YAML

The `json-yaml-snakeyaml` module reads and writes YAML with [SnakeYAML Engine](https://bitbucket.org/snakeyaml/snakeyaml-engine), the YAML 1.2 implementation of SnakeYAML. Parsing produces the regular GaltaJSON values described in [Values](/GaltaJSON/Values): mappings become `JsonObject`s and sequences `JsonArray`s, so a YAML file can be queried, validated or serialized like any JSON document.

```xml
<dependency>
    <groupId>org.monflabs.galta</groupId>
    <artifactId>json-yaml-snakeyaml</artifactId>
</dependency>
```

Everything goes through the static methods of `org.monflabs.json.yaml.SnakeYaml`:

| Method | Does |
|---|---|
| `parse(String)`, `parse(Reader)`, `parse(InputStream)` | Parses one YAML document and returns its root value. |
| `parse(JsonFactory, ...)` | Same, creating the objects and arrays with a given factory. |
| `stringify(Object)` | Writes a value as block-style YAML. |
| `stringify(Object, DumpSettings)` | Writes with SnakeYAML Engine settings. |

## Parsing

Sample: `doc_examples/yaml/YamlExamples.java` (`testParse`)

```java
Object value = SnakeYaml.parse("""
        server:
          host: example.com
          ports: [80, 443]
          tls: true
        owner: null
        """);
JsonObject o = (JsonObject)value;                         // a regular GaltaJSON object
o.getObject("server").getString("host");                  // -> "example.com"
o.getObject("server").getArray("ports").getInt(1);        // -> 443
o.get("owner");                                           // -> null, the key is present
```

The root can be any value: `parse("- a\n- b")` returns a `JsonArray`, `parse("hello")` the string `"hello"`, and an empty input `null`. A stream holding several documents (`---`) is rejected with an exception.

## Stringifying

Sample: `doc_examples/yaml/YamlExamples.java` (`testStringify`, `testDumpSettings`)

```java
JsonObject o = JsonObject.parse("""
        { "name": "Ada", "age": 36, "tags": ["math", "code"],
          "address": { "city": "London" }, "zip": "75001", "notes": "line 1\\nline 2" }
        """);
SnakeYaml.stringify(o);
// name: Ada
// age: 36
// tags:
// - math
// - code
// address:
//   city: London
// zip: '75001'
// notes: |-
//   line 1
//   line 2

SnakeYaml.parse(SnakeYaml.stringify(o));                  // equals o
```

Strings that would read back as another type are quoted, and multi-line strings use a literal block. For a compact output, pass `DumpSettings`:

```java
DumpSettings flow = DumpSettings.builder()
        .setDefaultFlowStyle(FlowStyle.FLOW)
        .build();
SnakeYaml.stringify(JsonObject.of("a", 1, "b", JsonArray.of("x", "y")), flow);
// {a: 1, b: [x, y]}
```

## How YAML maps to JSON

### Keys

YAML keys can be any scalar, while a `JsonObject` has string keys. Non-string keys are converted with `String.valueOf`, the way JSON and JavaScript treat property names:

Sample: `doc_examples/yaml/YamlExamples.java` (`testNonStringKeys`)

```java
JsonObject o = (JsonObject)SnakeYaml.parse("""
        1: one
        true: yes
        2.5: float
        null: nil
        """);
o.get("1");       // -> "one"
o.get("true");    // -> "yes"
o.get("2.5");     // -> "float"
o.get("null");    // -> "nil"
```

### Scalars

Plain scalars are resolved with the JSON schema of YAML 1.2: only the JSON spellings of `null`, booleans and numbers get a type; every other plain scalar is a string. In particular `~`, `True`, `yes`, `on`, hexadecimal numbers and dates stay strings.

Sample: `doc_examples/yaml/YamlExamples.java` (`testScalars`)

| YAML | Java value |
|---|---|
| `12` | `Integer` |
| `12345678901` | `Long` |
| `123456789012345678901234567890` | `BigInteger` |
| `1.5`, `1e3` | `Double` |
| `.inf`, `-.inf`, `.nan` | Rejected with a `JsonException`: JSON has no infinity or NaN. |
| `true`, `false` | `Boolean` |
| `null` | `null` |
| `~`, `True`, `yes`, `0x1F`, `2024-01-15` | `String` |
| `'012'`, `"012"` | `String` |

### Other features

| YAML feature | Result |
|---|---|
| Literal (`\|`) and folded (`>`) blocks | Strings, with the usual line handling. |
| Anchors and aliases (`&a`, `*a`) | The alias is the *same* Java instance as the anchored value: changing one changes the other. A recursive alias (a collection containing itself) is rejected with a `JsonException`. |
| Merge keys (`<<: *a`) | Not supported (YAML 1.2): `<<` is kept as a regular key. |
| Multiple documents (`---`) | Rejected: `expected a single document in the stream`. |
| Explicit tag `!!set` | A JSON array of the set members, in order. |
| Explicit tag `!!binary`, other non JSON values | Rejected with a `JsonException`. |

Sample: `doc_examples/yaml/YamlExamples.java` (`testDocumentFeatures`)

```java
JsonObject o = (JsonObject)SnakeYaml.parse("""
        text: |
          line 1
          line 2
        folded: >
          a
          b
        base: &base { x: 1 }
        copy: *base
        merged:
          <<: *base
          y: 2
        """);
o.get("text");                          // -> "line 1\nline 2\n"
o.get("folded");                        // -> "a b\n"
o.get("base") == o.get("copy");         // -> true
o.get("merged");                        // -> {"<<":{"x":1},"y":2}
```
