# Galta BOM

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/galta-bom?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/galta-bom)

The bill of materials of the published Galta libraries: import it once and
declare the Galta modules without a version.

It deliberately has **no parent**. Importing a BOM also imports the
`dependencyManagement` of its parents, so inheriting `monflabs-parent` would
push the Monflabs third-party version choices onto every consumer. Only Galta's
own artifacts are managed here; their dependencies come with them. Inside this
repository, `galta/pom.xml` imports it the same way.

## Usage

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
```

Then declare the modules you use, without a `<version>` - see the
[root README](../../README.md#modules).

## Contents

All entries use the `org.monflabs.galta` group id and the BOM's own version.

- Utilities: `utilities`, `filesystem`, `javacompiler`, `test`
- JSON: `json`, `json-config`, `json-impexp`, `json-impexp-fastcsv`,
  `json-jsonpath-jayway`, `json-jsonschema-jsonschemafriend`, `json-memdb`,
  `json-jackson`, `json-yaml-snakeyaml`
- UI and playground: `ui-commons`, `ui-swing`, `ui-swing-ide`,
  `playground-core`, `playground-ui-swing`
- GaltaJS: `js`, `js-debugger`, `js-template`, `js-vb`, `js-playground`
- Test jars (`<type>test-jar</type>`): `filesystem`, `js`

The `js-transpiler-maven` plugin is not a dependency, so it is not in the BOM:
declare it with its version in `<build><plugins>`.

When a module is published or unpublished, keep this list in sync with the
`excludeArtifacts` of the `central` profile in the root `pom.xml`. A version
bump changes `<revision>` here too, as the BOM has no parent to inherit it from.

## Documentation

- [Getting Started](../../docs/GettingStarted.md#using-the-bom)
  ([online](https://monflabs.github.io/Galta-Java/#/GettingStarted?id=using-the-bom))
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
