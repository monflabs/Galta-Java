# Java Compiler

The `javacompiler` module (`org.monflabs.galta:javacompiler`, package `org.monflabs.javacompiler`) compiles Java source held in memory, or in any `java.nio` filesystem, with the JDK's `javac`, and loads the resulting classes. It needs a JDK at runtime: the compiler comes from `ToolProvider.getSystemJavaCompiler()`.

## Compiling and loading a class

A compiler reads sources from a `SourceFactory` and writes `.class` files to a `TargetFactory`. `MapSourceFactory` and `MapTargetFactory` (package `javacompiler.factory`) keep both in a `Map`, keyed by file name.

Sample: `doc_examples/javacompiler/JavaCompilerExamples.java` (`testCompileAndLoad`)

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

    classes.getFiles().containsKey("com/acme/Greeter.class");   // true

    Class<?> c = compiler.getClassLoader().loadClass("com.acme.Greeter");
    @SuppressWarnings("unchecked")
    Supplier<String> greeter = (Supplier<String>) c.getConstructor().newInstance();
    greeter.get();   // "Hello from com.acme.Greeter"
}
```

`compile()` takes class names: `com.acme.Greeter` is read from `com/acme/Greeter.java` and written to `com/acme/Greeter.class`.

`getClassLoader()` returns the compiler's `FactoryClassLoader`: a class loader whose parent is the one given to the builder and which defines classes from the bytes in the target factory. A `FactoryClassLoader` can also be created directly over any `TargetFactory`, for example to load classes compiled earlier.

## The builder

| Method | Purpose |
|---|---|
| `classLoader(ClassLoader)` | Parent of the class loader that loads the compiled classes |
| `sourceFactory(SourceFactory)` | Where sources are read |
| `sourceFolder(Path, Charset)`, `sourceFolder(FileSystem, Charset)` | Read sources from a folder, or from the root of a filesystem |
| `targetFactory(TargetFactory)` | Where `.class` files are written |
| `targetFolder(Path)`, `targetFolder(FileSystem)` | Write `.class` files to a folder, or to the root of a filesystem |
| `options(List<String>)` | `javac` command-line options, such as `-Xlint:unchecked` or `-Xdiags:verbose` |
| `failOnWarnings(boolean)` | Treat warnings as errors (default `false`) |

The folder variants use `PathFileFactory`, which works on any `java.nio` filesystem, including the [memory filesystem](/Utilities/FileSystems):

Sample: `doc_examples/javacompiler/JavaCompilerExamples.java` (`testCompileBetweenFolders`)

```java
try (FileSystem fs = MemoryFileSystem.newBuilder().build()) {
    Path src = Files.createDirectories(fs.getPath("/src/demo"));
    Path out = Files.createDirectories(fs.getPath("/classes"));
    Files.writeString(src.resolve("Answer.java"),
            "package demo; public class Answer { public static int value() { return 42; } }");

    try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
            .classLoader(getClass().getClassLoader())
            .sourceFolder(fs.getPath("/src"), StandardCharsets.UTF_8)
            .targetFolder(out)
            .build()) {
        compiler.compile("demo.Answer");
        Files.exists(fs.getPath("/classes/demo/Answer.class"));   // true
        compiler.getClassLoader().loadClass("demo.Answer").getMethod("value").invoke(null);   // 42
    }
}
```

To plug in another storage, implement the two interfaces: `SourceFactory.readString(fileName)` returns a source, and `TargetFactory` has `openOutputStream(fileName)` for the compiler, `readBytes(fileName)` (return `null` when the file does not exist) for the class loader, and optionally `listClassFiles(packageFolder)` so later compilations can reference the compiled classes.

## What the sources can see

Sources compiled in the same `compile()` call can reference each other:

Sample: `doc_examples/javacompiler/JavaCompilerExamples.java` (`testSeveralUnitsTogether`)

```java
MapSourceFactory sources = new MapSourceFactory()
        .put("Main.java", "public class Main { public static int run() { return Helper.twice(21); } }")
        .put("Helper.java", "public class Helper { static int twice(int x) { return x * 2; } }");
MapTargetFactory classes = new MapTargetFactory();
try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
        .classLoader(getClass().getClassLoader())
        .sourceFactory(sources)
        .targetFactory(classes)
        .build()) {
    compiler.compile("Main", "Helper");   // units referencing each other go in one call
    Object result = compiler.getClassLoader().loadClass("Main").getMethod("run").invoke(null);   // 42
}
```

They can also use every class on the JVM's class path:

Sample: `doc_examples/javacompiler/JavaCompilerExamples.java` (`testUsesApplicationClasspath`)

```java
// Sources can use classes on the application class path (here: the utilities jar)
MapSourceFactory sources = MapSourceFactory.of("UsesUtil.java",
        "public class UsesUtil { public static String run() { return org.monflabs.util.StringUtil.class.getSimpleName(); } }");
try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
        .classLoader(getClass().getClassLoader())
        .sourceFactory(sources)
        .targetFactory(new MapTargetFactory())
        .build()) {
    compiler.compile("UsesUtil");
    compiler.getClassLoader().loadClass("UsesUtil").getMethod("run").invoke(null);   // "StringUtil"
}
```

Classes compiled by an earlier `compile()` call are visible to later ones: `javac` also looks into the target factory.

Sample: `doc_examples/javacompiler/JavaCompilerExamples.java` (`testSeparateCallsSeeEarlierClasses`)

```java
MapSourceFactory sources = new MapSourceFactory()
        .put("Main.java", "public class Main { public static int run() { return Helper.twice(21); } }")
        .put("Helper.java", "public class Helper { static int twice(int x) { return x * 2; } }");
MapTargetFactory classes = new MapTargetFactory();
try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
        .classLoader(getClass().getClassLoader())
        .sourceFactory(sources)
        .targetFactory(classes)
        .build()) {
    compiler.compile("Helper");
    // Helper.class is in the target: javac finds it there
    compiler.compile("Main");
    compiler.getClassLoader().loadClass("Main").getMethod("run").invoke(null);   // 42
}
```

The class loader given to the builder contributes to the compile class path too: the classes of the target factories of the `FactoryClassLoader`s in its parent chain, and the `file:` URLs of the `URLClassLoader`s. So a compiler can build on the classes of another one:

Sample: `doc_examples/javacompiler/JavaCompilerExamples.java` (`testCompileAgainstAnotherCompiler`)

```java
MapTargetFactory first = new MapTargetFactory();
try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
        .classLoader(getClass().getClassLoader())
        .sourceFactory(MapSourceFactory.of("Helper.java", "public class Helper { public static int one() { return 1; } }"))
        .targetFactory(first)
        .build()) {
    compiler.compile("Helper");
    ClassLoader withHelper = compiler.getClassLoader();

    // A compiler using that class loader as parent compiles against Helper
    try (JavaCompiler second = JavaCompilerFactory.newBuilder()
            .classLoader(withHelper)
            .sourceFactory(MapSourceFactory.of("Main.java", "public class Main { public static int run() { return Helper.one() + 1; } }"))
            .targetFactory(new MapTargetFactory())
            .build()) {
        second.compile("Main");
        second.getClassLoader().loadClass("Main").getMethod("run").invoke(null);   // 2
    }
}
```

Listing the classes of a target factory relies on `TargetFactory.listClassFiles(packageFolder)`, which `MapTargetFactory` and `PathFileFactory` implement. A custom factory that does not override it (the default returns an empty list) can still load its classes, but later compilations cannot reference them. The class path is left as is when the options set it explicitly (`-classpath`, `-cp` or `--class-path`).

Generated sources (annotation processors) are compiled but kept in memory, not written to the target factory; resources created in `CLASS_OUTPUT` are written to the target factory.

## Recompiling

A class loader defines a class only once. After a compilation, if the current class loader already defined some classes, `getClassLoader()` returns a new `FactoryClassLoader`, so recompiled classes are loaded in their latest version. Classes already loaded through a previous class loader are not affected. The classes found in the target factory take precedence over the parent class loader (child-first), except for the `java.*` packages.

Sample: `doc_examples/javacompiler/JavaCompilerExamples.java` (`testRecompile`)

```java
MapSourceFactory sources = MapSourceFactory.of("Version.java", "public class Version { public static int get() { return 1; } }");
try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
        .classLoader(getClass().getClassLoader())
        .sourceFactory(sources)
        .targetFactory(new MapTargetFactory())
        .build()) {
    compiler.compile("Version");
    compiler.getClassLoader().loadClass("Version").getMethod("get").invoke(null);   // 1

    sources.put("Version.java", "public class Version { public static int get() { return 2; } }");
    compiler.compile("Version");
    // getClassLoader() returns a new class loader after a recompilation
    compiler.getClassLoader().loadClass("Version").getMethod("get").invoke(null);   // 2
}
```

`compile()` calls on one compiler are serialized: calls from several threads run one after the other.

## Errors and warnings

A failed compilation throws `JavaCompilerException` (an unchecked exception). Its message starts with `Unable to compile the source`, followed by one line per `javac` diagnostic with its kind, line, column and message:

Sample: `doc_examples/javacompiler/JavaCompilerExamples.java` (`testCompileError`)

```java
MapSourceFactory sources = MapSourceFactory.of("Broken.java",
        "public class Broken {\n  int f() { return \"text\"; }\n}");
try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
        .classLoader(getClass().getClassLoader())
        .sourceFactory(sources)
        .targetFactory(new MapTargetFactory())
        .build()) {
    compiler.compile("Broken");   // throws JavaCompilerException:
    // Unable to compile the source
    // [kind=ERROR, line=2, col=20, message=incompatible types: String cannot be converted to int]
}
```

| Diagnostic kind | Fails the compilation |
|---|---|
| `ERROR` | Always |
| `WARNING`, `MANDATORY_WARNING` | Only with `failOnWarnings(true)` |
| `NOTE`, `OTHER` | Never |

The message lists every diagnostic, warnings and notes included, when it is thrown. When the compilation succeeds, `getWarnings()` returns the warnings of the last `compile()` call, formatted the same way. Which warnings `javac` reports depends on the `-Xlint` options: an unchecked call is only a `NOTE` ("uses unchecked or unsafe operations") by default, and a `MANDATORY_WARNING` with `-Xlint:unchecked`.

Sample: `doc_examples/javacompiler/JavaCompilerExamples.java` (`testWarnings`, `testNotesNeverFail`)

```java
private static final String RAW_LIST = "public class Raw { java.util.List list = new java.util.ArrayList(); "
        + "void add() { list.add(\"x\"); } }";

// Warnings are tolerated by default...
try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
        .classLoader(getClass().getClassLoader())
        .sourceFactory(MapSourceFactory.of("Raw.java", RAW_LIST))
        .targetFactory(new MapTargetFactory())
        .options(List.of("-Xlint:unchecked"))
        .build()) {
    compiler.compile("Raw");
}
// ...and fail the compilation with failOnWarnings(true)
try (JavaCompiler compiler = JavaCompilerFactory.newBuilder()
        .classLoader(getClass().getClassLoader())
        .sourceFactory(MapSourceFactory.of("Raw.java", RAW_LIST))
        .targetFactory(new MapTargetFactory())
        .options(List.of("-Xlint:unchecked"))
        .failOnWarnings(true)
        .build()) {
    compiler.compile("Raw");   // throws JavaCompilerException
    // [kind=MANDATORY_WARNING, line=1, col=90, message=unchecked call to add(E) as a member of the raw type java.util.List]
}
```

A `JavaCompiler` is `Closeable`; `close()` releases the `javac` file manager, and `compile()` on a closed compiler throws `JavaCompilerException("Compiler is closed")`. Building a compiler on a JRE (no `jdk.compiler` module) throws a `JavaCompilerException` saying so.

Sample: `doc_examples/javacompiler/JavaCompilerExamples.java` (`testClosedCompiler`)
