# Galta

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/galta-bom?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/galta-bom)
[![Documentation](https://img.shields.io/badge/docs-monflabs.github.io-blue)](https://monflabs.github.io/Galta-Java/)

**Documentation: <https://monflabs.github.io/Galta-Java/>** - the guides, the API
reference, and the [Java](https://monflabs.github.io/Galta-Java/java-playground/index.html)
and [GaltaJS](https://monflabs.github.io/Galta-Java/playground/index.html) playgrounds
running in your browser.

Galta is a set of Java libraries, mostly focused on a JSON library,
**GaltaJSON**, and a JavaScript-inspired scripting engine for the JVM,
**GaltaJS**. The engine is built on the JSON library: a JavaScript object *is*
a `JsonObject`, so data flows between Java and JavaScript without conversion.

> Galta is a **prototyping library**, provided **as is, without any production
> support**: no support commitment, no service level, no guarantee of fixes or
> of compatibility between versions.

## Modules

All artifacts use the `org.monflabs.galta` group id and are published to Maven
Central. Dependencies are transitive: reference the library you need and the
rest follows.

| Area | Artifacts |
|---|---|
| Utilities | `utilities`, `filesystem` (java.nio file systems: memory, sandboxed, ZIP, classpath), `javacompiler` (runtime Java compilation), `test` (JUnit support) |
| JSON | `json` (parser, values, JSONPath, JSON Pointer, schema), `json-jackson`, `json-config`, `json-impexp`, `json-impexp-fastcsv`, `json-memdb`, `json-yaml-snakeyaml`, `json-jsonpath-jayway`, `json-jsonschema-jsonschemafriend` |
| GaltaJS | `js` (the engine), `js-transpiler-maven` (Maven plugin), `js-template`, `js-vb`, `js-debugger`, `js-playground`, `js-precompiled-beautify-js`, `-css`, `-html` and `js-precompiled-typescript` (libraries transpiled to Java) |
| UI | `ui-commons`, `ui-swing`, `ui-swing-ide`, `playground-core`, `playground-ui-swing` |
| Java | `playground-java` (the Java playground: Java and JShell snippets) |

Import the `galta-bom` bill of materials once, then declare the modules you use
without a version - for example the JSON library and the JavaScript engine:

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
    <artifactId>json</artifactId>
  </dependency>
  <dependency>
    <groupId>org.monflabs.galta</groupId>
    <artifactId>js</artifactId>
  </dependency>
</dependencies>
```

A single module can also be declared directly with `<version>0.8.0</version>`.

Galta requires **Java 21** or later.

## Documentation

The documentation is online at **<https://monflabs.github.io/Galta-Java/>**,
with the [API reference](https://monflabs.github.io/Galta-Java/#/API) (the
javadoc of every published module) and the
[Java](https://monflabs.github.io/Galta-Java/java-playground/index.html) and
[GaltaJS](https://monflabs.github.io/Galta-Java/playground/index.html) playgrounds running
in your browser.

Its sources live in [`docs/`](docs/README.md), a docsify site (see
[Generating the Documentation](docs/Documentation.md) to view it locally):

- [GaltaJSON](docs/GaltaJSON/README.md) - the JSON library and its add-on modules
- [GaltaJS](docs/GaltaJS/README.md) - the JavaScript engine
- [Utilities](docs/Utilities/README.md) - the general-purpose libraries
- [Building and Releasing](docs/BuildAndRelease.md)

## Building

```sh
mvn clean install        # from the repository root; Java 21, Maven 3.8.1+
```

See [Building and Releasing](docs/BuildAndRelease.md) for the profiles, the
test262 compliance sweep and publishing. Releases are cut with
`buildtools/release.sh` (rehearse with `RELEASE_DRY_RUN=1`); what changed in each
version is in [CHANGELOG.md](CHANGELOG.md).

## License

Galta is licensed under the Apache License, Version 2.0; see [LICENSE](LICENSE).
Bundled third-party code keeps its original license, listed in [NOTICE](NOTICE).
