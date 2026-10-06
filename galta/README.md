# Galta Libraries

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/galta?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/galta)

The parent of the Galta library modules: utilities, JSON, UI, playground and the
GaltaJS scripting engine. Plugin and third-party dependency versions, the Java
level and the release profiles come from
[`monflabs-parent`](../tools/monflabs-parent/README.md) (through the repository
root pom); Galta's own artifacts come from [`galta-bom`](galta-bom/README.md),
imported here, so the modules declare them without a version.

To use Galta in a project, import `galta-bom` and declare the modules you need -
see the [root README](../README.md#modules).

## Modules

- [`galta-bom`](galta-bom/README.md) - the bill of materials of the published Galta artifacts
- [`parent-utilities`](parent-utilities/README.md) - general-purpose utilities, `java.nio` file systems, runtime Java compilation, test support
- [`parent-json`](parent-json/README.md) - GaltaJSON, the JSON library and its add-on modules
- [`parent-js`](parent-js/README.md) - GaltaJS, the JavaScript-inspired scripting engine, its tools and test suites
- [`parent-ui`](parent-ui/README.md) - the Swing user interface modules
- [`parent-playground`](parent-playground/README.md) - the interactive scripting playground
- [`parent-java`](parent-java/README.md) - modules for the Java language: the Java playground

## Building

From this folder, once `tools/` has been installed by a first build from the
repository root:

```sh
mvn clean install
mvn clean install -pl parent-js/js --also-make   # one module and what it depends on
```

See [Building and Releasing](../docs/BuildAndRelease.md)
([online](https://monflabs.github.io/Galta-Java/#/BuildAndRelease)).

## Why Galta?

People in the French Alps used to speak their own language, called "patois". In
Haute Savoie, a "galta" means an attic, or a storeroom under the roof (see
<https://www.hautesavoiephotos.com/lexique.htm>). This is what this repository
is about: a place where to find several Java libraries.
