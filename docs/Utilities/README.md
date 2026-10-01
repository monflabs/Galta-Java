# Utilities

?> Galta is a prototyping library provided as is, without any production support. See [A prototyping library](/?id=a-prototyping-library).

The `parent-utilities` modules are the foundation every other Galta library
builds on. The `utilities` module has no Galta dependency, and its only
third-party dependency is the Eclipse null-analysis annotations
(`org.eclipse.jdt.annotation`), which keeps it usable in constrained runtimes.
`filesystem` and `javacompiler` add nothing beyond `utilities`. The `test`
support module is the exception: it depends on the GaltaJSON `json` and
`json-config` modules for its golden-file assertions and on Byte Buddy for
resource-leak detection, so use it only as a test dependency.

## Modules

All artifacts use the group `org.monflabs.galta`.

| Artifact | Package | Contents |
|---|---|---|
| `utilities` | `org.monflabs.util` | Strings, number formatting, types and versions, date/time, iterators and caches, I/O helpers, reflection, generators, profiling |
| `filesystem` | `org.monflabs.filesystem` | `java.nio.file` filesystems over memory, sandboxed folders, ZIP files and classpath resources |
| `javacompiler` | `org.monflabs.javacompiler` | Compiling Java source strings in memory and loading the result |
| `test` | `org.monflabs.tests` | Test support: base test case, golden-file assertions, resource-leak detection |

`utilities-tests` holds the tests of `utilities`; it is not a library.

## Guide

| Page | Covers |
|---|---|
| [Strings](/Utilities/Strings) | `StringUtil`, `StringFormat`, `StringMatcher`, `TextBuilder` |
| [Numbers, Types & Versions](/Utilities/NumbersAndTypes) | `DtoA` number formatting, `TypeUtil` conversions, `Version` |
| [Date & Time](/Utilities/DateTime) | ISO 8601 parsing and formatting |
| [Iterators, Caches & Sorting](/Utilities/Collections) | `Iterators`, `Iterables`, `LRUCache`, dependency ordering |
| [I/O & Paths](/Utilities/IO) | Stream and file helpers, `PathUtil`, readers and writers |
| [File Systems](/Utilities/FileSystems) | The memory, sandboxed, ZIP and resource filesystems |
| [Reflection](/Utilities/Reflection) | `ClassMetadata` and `PojoAccessor` |
| [Generators, Profiling & Runtime](/Utilities/Runtime) | Generators, the profiler, performance watches, console output |
| [Java Compiler](/Utilities/JavaCompiler) | In-memory Java compilation |
| [Test Support](/Utilities/Testing) | The `test` module |

## Samples

Every sample on these pages is a JUnit test, run on every build. A `Sample:`
line under each snippet names its test:

- `utilities` samples live in `galta/parent-utilities/utilities-tests/src/test/java/doc_examples/util/`;
- `filesystem`, `javacompiler` and `test` samples live in each module's own
  `src/test/java/doc_examples/` folder.
