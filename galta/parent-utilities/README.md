# Galta Utilities Parent

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/parent-utilities?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/parent-utilities)

The parent of the Galta general-purpose utility modules, the foundation every
other Galta library builds on. `utilities` has no Galta dependency;
`filesystem` and `javacompiler` add nothing beyond it. `test` is the exception:
it depends on the GaltaJSON `json` and `json-config` modules and on Byte Buddy,
so use it only as a test dependency. All artifacts use the `org.monflabs.galta`
group id.

## Modules

- [`utilities`](utilities/README.md) - strings, number formatting, date/time, I/O, caches, iterators, reflection-based model access, generators, profiling
- [`filesystem`](filesystem/README.md) - `java.nio.file` file systems: in-memory, sandboxed, delegating, ZIP and classpath resources
- [`javacompiler`](javacompiler/README.md) - runtime compilation of Java source with the JDK's `javac`, and a class loader for the result
- [`test`](test/README.md) - JUnit support: base test case, golden-file assertions, resource-leak detection, suite completeness
- [`utilities-tests`](utilities-tests/README.md) - the test suites of `utilities` (not published)
- [`utilities-performance`](utilities-performance/README.md) - JMH benchmarks of `utilities` (not published)

This pom turns the resource-leak detector of the `test` module on for the tests
of its modules (`monflabs.tests.trackLeaks=true`).

## Documentation

- [Utilities guide](../../docs/Utilities/README.md)
  ([online](https://monflabs.github.io/Galta-Java/#/Utilities/))
- [API reference](https://monflabs.github.io/Galta-Java/#/API)
