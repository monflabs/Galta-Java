# Galta Java Parent

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/parent-java?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/parent-java)

The parent of the Galta modules for the Java language. This pom only aggregates them.

- [playground-java](playground-java/README.md) - the Java playground: `Main.java` snippets
  compiled in memory, `Main.jshell` snippets run by JShell, and a snippet library showcasing
  GaltaJSON and the utilities. It is built on the engine-agnostic playground of
  [`parent-playground`](../parent-playground/README.md).
- [playground-java-cheerpj](playground-java-cheerpj/README.md) - the browser (CheerpJ) build of
  the Java playground (not published to Maven Central), run by the documentation site.
