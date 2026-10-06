# Galta Java Playground

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/playground-java?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/playground-java)

An interactive Swing application to write and run Java snippets, built on the
engine-agnostic playground framework
([`playground-core`, `playground-ui-swing`](../../parent-playground/README.md)). A snippet is run by
one of two engines, chosen by its main file:

- `Main.java` - compiled in memory with [javacompiler](../../parent-utilities/javacompiler/README.md),
  then its `main(String[])` method is called;
- `Main.jshell` - evaluated by JShell, statement by statement.

The console shows what the snippet prints. The snippet library showcases the Galta
libraries, by category:

- **Getting Started** - "Hello JShell", and "Hello Galta", a `Main.java` class using GaltaJSON
- **GaltaJSON** - objects, arrays (with negative indexes and stream-like functions),
  numbers, dates and times, parsing (and the number options), stringifying, JSON Path,
  JSON Pointer and configuration files
- **Utilities** - strings, numbers and versions, iterators, generators, caches, file
  systems, the in-memory Java compiler and the profiler

Each snippet has a `README.md`, shown next to its code, and prints its results with
comments giving the expected output. `tests.snippets.PackagedSnippetsTest` runs all of
them, so a change of the Galta API that breaks one fails the build.

## Running it

Run the `playground.JavaPlayground` class with this module and its dependencies on
the class path (from an IDE, for instance). Started from the module folder, it
uses the snippets of `src/main/resources/snippets` (editable); otherwise the ones
packaged in the jar (read-only).

## Contents

- `playground.JavaPlayground` - the entry point: configures the playground (title,
  snippets, engines, editor layout) and opens the window
- `playground.impl.engine.java.JavaExecutionEngine` - compiles and runs `Main.java`
- `playground.impl.engine.jshell.JShellExecutionEngine` - evaluates `Main.jshell`; on a
  runtime without JShell (CheerpJ), converts it to a `Main` class first
  (`JShellScriptConverter`: imports hoisted, methods and classes as static members, the
  statements in `main()`)
- `playground.impl.engine.util.StreamThreadDispatcher` - routes `System.out`/`System.err`
  of the snippet's thread to its console
- `tests.AllLangJavaTests` - runs Java and JShell snippets through both engines, and every
  packaged snippet (`PackagedSnippetsTest`)

## Documentation

- [API reference](https://monflabs.github.io/Galta-Java/#/API)
