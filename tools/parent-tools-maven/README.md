# Galta Tools - Maven Plugins Parent

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta.tools/parent-tools-maven?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta.tools/parent-tools-maven)

The parent of the Galta Maven plugins (group id `org.monflabs.galta.tools`).
The plugin versions are managed by [`monflabs-parent`](../monflabs-parent/README.md).

## Modules

- [`filesystem-resources-manifest`](filesystem-resources-manifest/README.md) -
  generates the resource manifest the classpath `ResourceFileSystem` uses to list directories

The GaltaJS transpiler plugin, `js-transpiler-maven`, is not here: it lives with
the engine in [`galta/parent-js`](../../galta/parent-js/README.md).
