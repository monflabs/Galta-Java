# Galta FileSystem

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/filesystem?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/filesystem)

`java.nio.file.FileSystem` implementations: in-memory, sandboxed, delegating,
ZIP and classpath-resource file systems. Once built, a file system is used
through the standard `Files` and `Path` APIs, so the same code can run against
a real folder in production and a memory file system in a test. GaltaJS uses
them to resolve module paths, the playground to manage its snippets.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>filesystem</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see the [root README](../../../README.md#modules)),
or declare `<version>` directly. The module only depends on `utilities`; it
also publishes a test jar (`<type>test-jar</type>`).

```java
try (FileSystem fs = MemoryFileSystem.newBuilder().build()) {
    Path docs = fs.getPath("/docs");
    Files.createDirectories(docs);
    Files.writeString(docs.resolve("a.txt"), "Hello", StandardCharsets.UTF_8);
    Files.readString(fs.getPath("/docs/a.txt"));   // "Hello"
}
```

## Contents

Every file system is created with its `newBuilder()`; each `build()` returns a
new, independent file system. Packages are under `org.monflabs.filesystem`:

- `memory` - `MemoryFileSystem`, a writable tree in memory
- `file` - `FileFileSystem`, over `java.io.File`, optionally sandboxed to a root folder
- `path` - `PathFileSystem`, over the default `java.nio` file system, optionally
  sandboxed to a root folder, always with `/` as separator
- `delegate` - `PathDelegatingFileSystem`, any existing `Path` (on any file system) used as the root
- `zip` - `ZipFileSystem`, a read-only view of a `.zip` file
- `resources` - `ResourceFileSystem`, read-only classpath resources listed in a
  `resources.manifest` file (generated at build time by the
  [`filesystem-resources-manifest`](../../../tools/parent-tools-maven/filesystem-resources-manifest/README.md)
  Maven plugin)

The sandboxes confine paths to their root (`..` never climbs above it, symbolic
links pointing outside are refused), but they are **not a security boundary**:
see the `Sandbox` class and the guide.

## Documentation

- [File Systems](../../../docs/Utilities/FileSystems.md)
  ([online](https://monflabs.github.io/Galta-Java/#/Utilities/FileSystems))
- [API reference](https://monflabs.github.io/Galta-Java/#/API)

The doc samples are tests in `src/test/java/doc_examples/filesystem`; runnable
demos are in `src/test/java/demo`. Run the tests from `galta/` with
`mvn test -pl parent-utilities/filesystem --also-make`.
