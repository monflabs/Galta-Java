# Galta Tools

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta.tools/tools?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta.tools/tools)

Build tooling shared by the Monflabs projects, under the
`org.monflabs.galta.tools` group id: the parent pom of every Monflabs Maven
project and the Galta Maven plugins. The repository root `pom.xml` aggregates
this folder with `galta/`; a first build from the root installs it, after which
the libraries can be built from `galta/`.

Galta's own artifacts are managed by [`galta-bom`](../galta/galta-bom/README.md),
not here.

## Modules

- [`monflabs-parent`](monflabs-parent/README.md) - the shared parent pom: Java
  level, third-party dependency and plugin versions, build rules, release profiles
- [`parent-tools-maven`](parent-tools-maven/README.md) - the parent of the Galta
  Maven plugins, currently [`filesystem-resources-manifest`](parent-tools-maven/filesystem-resources-manifest/README.md)

`eea/` is not a module: it holds the Eclipse external null annotations for the
JDK (`jdk-eea-2.4.0.jar`), used by the IDE only.

## Documentation

- [Building and Releasing](../docs/BuildAndRelease.md)
  ([online](https://monflabs.github.io/Galta-Java/#/BuildAndRelease))
