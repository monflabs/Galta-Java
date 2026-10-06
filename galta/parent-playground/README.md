# Galta Playground Parent

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/parent-playground?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/parent-playground)

Parent of the playground modules: an interactive scripting environment, a
Swing mini-IDE where you pick a snippet from a library, edit its files, run it
and see its console and engine-specific results. The playground is
engine-agnostic; the GaltaJS playground
([`parent-js/js-playground`](../parent-js/js-playground/README.md)) plugs the
GaltaJS engine in, and the Java playground
([`parent-java/playground-java`](../parent-java/playground-java/README.md)) the Java
compiler and JShell.

- [playground-core](playground-core/README.md) - the engine-agnostic model, without any UI: snippets and their in-memory files, execution engines, the `ExecutionController` running them, and `SnippetStorage` saving the snippets
- [playground-ui-swing](playground-ui-swing/README.md) - the Swing window (`PlaygroundFrame`): snippet tree, editors, console and result tabs

The GaltaJS playground also [runs in the browser](https://monflabs.github.io/Galta-Java/playground/);
see the [API reference](https://monflabs.github.io/Galta-Java/#/API) of each module.
