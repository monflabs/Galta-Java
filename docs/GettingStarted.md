# Getting Started

The Galta libraries are published to **Maven Central**, under the group id `org.monflabs.galta`. All the artifacts of a release share the same version, currently `0.8.0`, and need **Java 21** or later.

?> Galta is a prototyping library, provided as is: see [A prototyping library](/?id=a-prototyping-library) before building on it.

## Adding a library

Declare the library you use: its own dependencies come with it. For the JSON library:

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>json</artifactId>
  <version>0.8.0</version>
</dependency>
```

For the JavaScript engine, which brings the JSON library with it:

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>js</artifactId>
  <version>0.8.0</version>
</dependency>
```

No repository needs to be declared: Maven and Gradle read Maven Central by default.

## Using the BOM

When a project uses several Galta artifacts, import `galta-bom` and declare them without a version: they then always come from the same release. The BOM only manages Galta's own artifacts, so it doesn't change the versions of the other libraries of the project.

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.monflabs.galta</groupId>
      <artifactId>galta-bom</artifactId>
      <version>0.8.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>org.monflabs.galta</groupId>
    <artifactId>js</artifactId>
  </dependency>
  <dependency>
    <groupId>org.monflabs.galta</groupId>
    <artifactId>json-jackson</artifactId>
  </dependency>
</dependencies>
```

With Gradle (Kotlin DSL), the BOM is a platform:

```kotlin
dependencies {
    implementation(platform("org.monflabs.galta:galta-bom:0.8.0"))
    implementation("org.monflabs.galta:js")
    implementation("org.monflabs.galta:json-jackson")
}
```

## The artifacts

Galta keeps its dependencies minimal: the core libraries depend only on each other, and a third-party library comes only with the module that needs it. The last column lists the direct dependencies, Galta's in code style.

| Artifact | What it is | Depends on |
|---|---|---|
| **JSON** | | |
| `json` | The JSON library: parser and stringifier, containers, JSON Path, JSON Pointer, JSON Reference, schema metadata ([GaltaJSON](/GaltaJSON/)) | `utilities` |
| `json-jackson` | Jackson reads and writes `JsonObject` and `JsonArray`, and maps Java objects to them ([Jackson](/GaltaJSON/Modules/Jackson)) | `json`, Jackson Databind |
| `json-config` | Configuration in JSON files, with `$ref` and encrypted values ([Configuration](/GaltaJSON/Modules/Config)) | `json` |
| `json-impexp` | Import, export and replication of JSON documents ([Import & Export](/GaltaJSON/Modules/ImportExport)) | `json` |
| `json-impexp-fastcsv` | CSV sources and targets for `json-impexp` | `json-impexp`, FastCSV |
| `json-memdb` | In-memory JSON document database ([Memory Database](/GaltaJSON/Modules/MemoryDb)) | `json-impexp` |
| `json-yaml-snakeyaml` | YAML reading and writing ([YAML](/GaltaJSON/Modules/Yaml)) | `json`, SnakeYAML Engine |
| `json-jsonpath-jayway` | Jayway JsonPath queries on Galta values ([JSON Path with Jayway](/GaltaJSON/Modules/JsonPathJayway)) | `json`, Jayway JsonPath |
| `json-jsonschema-jsonschemafriend` | JSON Schema validation ([JSON Schema Validation](/GaltaJSON/Modules/JsonSchema)) | `json`, jsonschemafriend |
| **GaltaJS** | | |
| `js` | The JavaScript engine, interpreter and transpiler to Java ([GaltaJS](/GaltaJS/)) | `json`, `javacompiler` |
| `js-debugger` | Swing debugger speaking the Chrome DevTools Protocol ([Debugging](/GaltaJS/UserGuide/Debugging)) | `js`, `filesystem`, RSyntaxTextArea |
| `js-template` | JSP-like text templates evaluated with GaltaJS ([Companion Modules](/GaltaJS/UserGuide/CompanionModules)) | `js` |
| `js-vb` | Expression-language value bindings evaluated with GaltaJS ([Companion Modules](/GaltaJS/UserGuide/CompanionModules)) | `js` |
| `js-precompiled-beautify-js`, `-css`, `-html` | The js-beautify formatters (JavaScript, CSS, HTML), transpiled to Java ([Precompiled libraries](/GaltaJS/UserGuide/CompanionModules?id=precompiled-libraries)) | `js` |
| `js-precompiled-typescript` | The TypeScript compiler, transpiled to Java: TypeScript to JavaScript without Node.js (a 14 MB jar) | `js` |
| `js-playground` | The interactive GaltaJS playground application | `js`, `js-debugger`, `playground-ui-swing` |
| `js-transpiler-maven` | Maven plugin transpiling JavaScript to Java at build time ([Execution Modes](/GaltaJS/UserGuide/ExecutionModes)) | `js`, the Maven API |
| **Utilities** | | |
| `utilities` | General-purpose utilities: strings, numbers, dates, I/O, caching, reflection ([Utilities](/Utilities/)) | nothing |
| `filesystem` | `java.nio` file systems: in-memory, sandboxed, ZIP, classpath ([File Systems](/Utilities/FileSystems)) | `utilities` |
| `javacompiler` | Compiles Java source at runtime, in memory ([Java Compiler](/Utilities/JavaCompiler)) | `utilities` |
| `test` | Unit test support: golden files, leak tracking ([Test Support](/Utilities/Testing)) | `json-config`, Byte Buddy |
| **UI and playground** | | |
| `ui-commons` | Toolkit-independent UI building blocks | `json` |
| `ui-swing` | Swing components, layouts and themes | `ui-commons`, FlatLaf, Ikonli |
| `ui-swing-ide` | IDE-grade Swing editors and consoles | `ui-swing`, `json-config`, RSyntaxTextArea |
| `playground-core` | The engine-independent core of the scripting playground | `filesystem` |
| `playground-ui-swing` | The Swing user interface of the playground | `playground-core`, `ui-swing-ide`, CommonMark |

`js-transpiler-maven` is a Maven plugin, declared under `<build><plugins>` rather than as a dependency; it is not in the BOM, so give it its version.

The libraries also work in a fat jar, when a single jar file is preferred (the Maven Shade or Assembly plugin, or Gradle's Shadow plugin).

## First steps

Parse JSON, read and change it, and write it back:

Sample: `doc_examples/json/GettingStartedExamples.java` (`testFirstSteps`)

```java
JsonFactory factory = JsonFactory.get();

JsonObject order = (JsonObject)factory.parse("{\"id\":\"A1\",\"lines\":[{\"product\":\"pen\",\"quantity\":3}]}");
String product = order.getArray("lines").getObject(0).getString("product");   // "pen"
order.put("paid", true);
String text = factory.stringify(order);
// {"id":"A1","lines":[{"product":"pen","quantity":3}],"paid":true}
```

Evaluate JavaScript:

Sample: `doc_examples/CreateEnvironment.java` (`testJavaScriptExpression`)

```java
JSEnvironment env = JavaScriptEnvironment.create();
Object o = env.evaluateExpression("1+Math.abs(-2)");
// The result is a plain java.lang.Integer - no wrapper type
assertEquals(3, o);
```

## Next steps

- [GaltaJSON](/GaltaJSON/): values and containers, parsing and stringifying, JSON Path, pointers.
- [GaltaJS Getting Started](/GaltaJS/UserGuide/GettingStarted): environments, expressions and scripts, the event loop.
- [Utilities](/Utilities/): what the other libraries build on.
- [API Reference](/API): the javadoc of every published artifact.
- [Playground](playground/ ':ignore'): try GaltaJS in the browser, without installing anything.

To build Galta from its sources instead, see [Building and Releasing](/BuildAndRelease).
