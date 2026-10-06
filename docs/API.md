# API Reference

The javadoc of every module Galta publishes to Maven Central. Each link opens
the module's own javadoc; the pages are generated from the sources the site was
published from (see [Generating the Documentation](/Documentation)).

To use a module, import `galta-bom` and declare it without a version - see
[Using the BOM](/GettingStarted?id=using-the-bom).

## Utilities

| Module | Javadoc | Content |
|---|---|---|
| `utilities` | [utilities](api/utilities/index.html ':ignore') | General-purpose utilities: strings, numbers, date/time, I/O, caching, paths, reflection-based model access, profiling and generators |
| `filesystem` | [filesystem](api/filesystem/index.html ':ignore') | `java.nio` file systems: in-memory, sandboxed, delegating, ZIP and classpath resources |
| `javacompiler` | [javacompiler](api/javacompiler/index.html ':ignore') | Runtime compilation of Java source to class files, with a class loader for the result |
| `test` | [test](api/test/index.html ':ignore') | Unit test support: golden-file assertions, resource leak tracking, reflective access |

## JSON

| Module | Javadoc | Content |
|---|---|---|
| `json` | [json](api/json/index.html ':ignore') | Parser and stringifier, JSON containers, JSONPath, JSON Pointer, JSON Reference, schema metadata |
| `json-jackson` | [json-jackson](api/json-jackson/index.html ':ignore') | Jackson interoperability: reading, writing and mapping GaltaJSON values with Jackson |
| `json-config` | [json-config](api/json-config/index.html ':ignore') | JSON-file configuration with `$ref` composition and encrypted values |
| `json-impexp` | [json-impexp](api/json-impexp/index.html ':ignore') | Import, export and replication of JSON documents |
| `json-impexp-fastcsv` | [json-impexp-fastcsv](api/json-impexp-fastcsv/index.html ':ignore') | CSV source and target, based on FastCSV |
| `json-memdb` | [json-memdb](api/json-memdb/index.html ':ignore') | In-memory JSON document database with transactions, queries and replication |
| `json-yaml-snakeyaml` | [json-yaml-snakeyaml](api/json-yaml-snakeyaml/index.html ':ignore') | YAML reading and writing, based on SnakeYAML Engine |
| `json-jsonpath-jayway` | [json-jsonpath-jayway](api/json-jsonpath-jayway/index.html ':ignore') | Jayway JsonPath over Galta JSON values |
| `json-jsonschema-jsonschemafriend` | [json-jsonschema-jsonschemafriend](api/json-jsonschema-jsonschemafriend/index.html ':ignore') | JSON Schema validation, based on jsonschemafriend |

## GaltaJS

| Module | Javadoc | Content |
|---|---|---|
| `js` | [js](api/js/index.html ':ignore') | The GaltaJS engine: interpreter, transpiler to Java, standard library, Java interop |
| `js-debugger` | [js-debugger](api/js-debugger/index.html ':ignore') | Swing debugger speaking the Chrome DevTools Protocol |
| `js-template` | [js-template](api/js-template/index.html ':ignore') | JSP-like text templates |
| `js-vb` | [js-vb](api/js-vb/index.html ':ignore') | Expression-language value bindings |
| `js-precompiled-beautify-js` | [js-precompiled-beautify-js](api/js-precompiled-beautify-js/index.html ':ignore') | The js-beautify JavaScript formatter, transpiled to Java (`BeautifyJs`) |
| `js-precompiled-beautify-css` | [js-precompiled-beautify-css](api/js-precompiled-beautify-css/index.html ':ignore') | The js-beautify CSS formatter, transpiled to Java (`BeautifyCss`) |
| `js-precompiled-beautify-html` | [js-precompiled-beautify-html](api/js-precompiled-beautify-html/index.html ':ignore') | The js-beautify HTML formatter, transpiled to Java (`BeautifyHtml`) |
| `js-precompiled-typescript` | [js-precompiled-typescript](api/js-precompiled-typescript/index.html ':ignore') | The TypeScript compiler, transpiled to Java (`Typescript`) |

## UI & Playground

| Module | Javadoc | Content |
|---|---|---|
| `ui-commons` | [ui-commons](api/ui-commons/index.html ':ignore') | Toolkit-independent UI building blocks: converters, lookups, application support |
| `ui-swing` | [ui-swing](api/ui-swing/index.html ':ignore') | Swing components, layouts, themes and utilities |
| `ui-swing-ide` | [ui-swing-ide](api/ui-swing-ide/index.html ':ignore') | Syntax text areas, consoles and persisted UI settings |
| `playground-core` | [playground-core](api/playground-core/index.html ':ignore') | Engine-agnostic core of the scripting playground: snippets and their in-memory file systems, execution engines and their lifecycle (`ExecutionController`) |
| `playground-ui-swing` | [playground-ui-swing](api/playground-ui-swing/index.html ':ignore') | Swing user interface of the playground: editors, console, Markdown rendering (CommonMark) |
| `playground-java` | [playground-java](api/playground-java/index.html ':ignore') | The Java playground: Java snippets compiled in memory, JShell snippets |
| `js-playground` | [js-playground](api/js-playground/index.html ':ignore') | The GaltaJS playground application |

The playground also runs in the browser: [open the playground](playground/index.html ':ignore').
