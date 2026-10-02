# Galta Java Compiler

[![Maven Central](https://img.shields.io/maven-central/v/org.monflabs.galta/javacompiler?label=Maven%20Central)](https://central.sonatype.com/artifact/org.monflabs.galta/javacompiler)

Runtime compilation of Java source code, held in memory or in any `java.nio`
file system, with the JDK's `javac`, and a class loader for the result. It
needs a JDK at runtime: the compiler comes from
`ToolProvider.getSystemJavaCompiler()`. GaltaJS uses it to compile the Java
code its transpiler generates.

## Usage

```xml
<dependency>
  <groupId>org.monflabs.galta</groupId>
  <artifactId>javacompiler</artifactId>
</dependency>
```

The version comes from the `galta-bom` (see the [root README](../../../README.md#modules)),
or declare `<version>` directly. The module only depends on `utilities`.

```java
MapSourceFactory sources = new MapSourceFactory()
        .put("com/acme/Greeter.java", """
                package com.acme;
                public class Greeter implements java.util.function.Supplier<String> {
                    public String get() { return "Hello from " + getClass().getName(); }
                }
                """);
MapTargetFactory classes = new MapTargetFactory();

try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
        .classLoader(getClass().getClassLoader())
        .sourceFactory(sources)
        .targetFactory(classes)
        .build()) {
    compiler.compile("com.acme.Greeter");   // a class name, not a file name
    Class<?> c = compiler.getClassLoader().loadClass("com.acme.Greeter");
    @SuppressWarnings("unchecked")
    Supplier<String> greeter = (Supplier<String>) c.getConstructor().newInstance();
    greeter.get();   // "Hello from com.acme.Greeter"
}
```

## Contents

Package `org.monflabs.javacompiler`:

- `JavaCompilerFactory` - the builder: parent class loader, sources
  (`sourceFactory`, or `sourceFolder` over a `Path` or a file system), targets
  (`targetFactory` or `targetFolder`), `javac` options, `failOnWarnings`
- `JavaCompiler` - compiles class names; outputs are written to the target only
  when the compilation succeeds, and stale nested-class outputs are removed;
  `getClassLoader()` returns a fresh loader only when a recompilation rewrote an
  already loaded class
- `FactoryClassLoader` - child-first class loader over a `TargetFactory`
  (platform packages stay parent-first); the other target files are its resources
- `SourceFactory`, `TargetFactory` - the storage interfaces; `PathFileFactory`
  implements both over a `java.nio` folder, `factory.MapSourceFactory` and
  `factory.MapTargetFactory` over a `Map`
- `javac` - the `javax.tools`-based implementation (`JavaCompilerJavac`, its file manager and file objects)

Annotation processing is off (`-proc:none`) unless the options configure it.

## Documentation

- [Java Compiler](../../../docs/Utilities/JavaCompiler.md)
  ([online](https://monflabs.github.io/Galta-Java/#/Utilities/JavaCompiler))
- [API reference](https://monflabs.github.io/Galta-Java/#/API)

The doc samples are tests in `src/test/java/doc_examples/javacompiler`. GaltaJS
depends on this module: after a change, run `mvn test -pl parent-js/js -am` from
`galta/` as well.
