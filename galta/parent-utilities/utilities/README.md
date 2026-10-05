# Galta Utilities

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/utilities?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/utilities)

General-purpose Java utilities: strings, numbers, date/time, I/O, caching,
paths, reflection-based model access, profiling and generators. It is the base
of every other Galta library. It has no Galta dependency, and its only
third-party dependency is the Eclipse null-analysis annotations
(`org.eclipse.jdt.annotation`, optional).

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>utilities</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see the [root README](../../../README.md#modules)),
or declare `<version>` directly.

```java
assertEquals("1 + 1 = 2", StringFormat.format("{0} + {0} = {1}", 1, 2));
assertEquals("Hello {1}!", StringFormat.format("Hello {1}!", "a"));     // missing argument: kept
```

## Contents

All packages are under `org.monflabs.util`.

- `util` - `StringUtil` (null-safe string helpers), `StringFormat` (the `{0}`
  message formatter used by every Galta exception), `StringMatcher` (a
  hand-written scanner), `TextBuilder` (indenting builder for code generation),
  `DtoA` (double-to-string formatting, with embedded Ryu ports in `impl.ryu`),
  `TypeUtil`, `Version`, `EnumUtil`, `ArrayUtil`, `IOStreamUtil`, `FileUtil`,
  `PathUtil`, `Console`/`ConsoleColors`, `ObjectBuilder`, `ExceptionUtil`
- `datetime` - ISO 8601 parsing and formatting (`ISO8601`, `DateTimeParts`,
  `PeriodFormatter`)
- `iterators` - `Iterators` and `Iterables` pipelines, primitive iterators
- `cache` - `LRUCache` and the `CacheProvider` abstraction
- `dependencies` - `DependencyEngine`, sorting items after their dependencies
- `io` - fast buffered streams, readers and writers, reader/writer to stream
  adapters, `LRUCachedOutputStream` (keeps the tail of an output), `ZipUtil`
- `path` - `FilesUtil` and `PathClassLoader` (a class loader over a `java.nio` path)
- `model` - `ModelAccessor`, `PojoAccessor` and `ClassMetadata`: name-based
  access to Java objects, method calls and construction with argument
  conversion (the reflection layer under GaltaJS's Java interop)
- `generators` - `Generator`/`Yielder`: Java generators (the body yields values to an `Iterator`), each body on its own thread, virtual by default
- `profiler` - `Profiler`, `JavaProfiler`
- `config`, `http`, `function`, `scoped`, `builder` - small helpers (a `Config`
  interface, HTTP form and query-string encoding, `TriFunction`/`TriPredicate`,
  a temporary `_ScopedValue`, the `@Required` builder annotation)

## Documentation

Guide pages (in [`docs/Utilities`](../../../docs/Utilities/README.md), online at
<https://monflabs.github.io/Galta-Java/#/Utilities/>):
[Strings](https://monflabs.github.io/Galta-Java/#/Utilities/Strings),
[Numbers, Types & Versions](https://monflabs.github.io/Galta-Java/#/Utilities/NumbersAndTypes),
[Date & Time](https://monflabs.github.io/Galta-Java/#/Utilities/DateTime),
[Iterators, Caches & Sorting](https://monflabs.github.io/Galta-Java/#/Utilities/Collections),
[I/O & Paths](https://monflabs.github.io/Galta-Java/#/Utilities/IO),
[Reflection](https://monflabs.github.io/Galta-Java/#/Utilities/Reflection),
[Generators, Profiling & Runtime](https://monflabs.github.io/Galta-Java/#/Utilities/Runtime).
The [API reference](https://monflabs.github.io/Galta-Java/#/API) has the javadoc.
The tests and the doc samples live in [`utilities-tests`](../utilities-tests/README.md).
