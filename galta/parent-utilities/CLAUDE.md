# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`parent-utilities` provides low-level, general-purpose Java utilities used by all other Galta modules. It has no Galta-specific dependencies.

## Sub-modules

| Module | Artifact | Purpose |
|---|---|---|
| `utilities` | `utilities` | Core utility classes |
| `filesystem` | `filesystem` | Virtual filesystem abstraction |
| `javacompiler` | `javacompiler` | Runtime Java compilation |
| `test` | `test` | Test support infrastructure |
| `utilities-tests` | `utilities-tests` | Tests for utilities |

## `utilities` — Core Utilities

Package root: `org.monflabs.util`

Key classes:
- `StringUtil` — string manipulation (split, join, trim, case conversion, padding, redaction)
- `StringFormat` — Java-style `{0}` message formatting (used throughout the codebase instead of `String.format`)
- `IOStreamUtil` — stream reading/writing helpers
- `ExceptionUtil` — wrapping and unwrapping checked exceptions
- `Console` — ANSI-colored console output
- `LRUCache` — simple bounded LRU cache
- `CacheProvider` — cache abstraction interface

Numeric formatting uses embedded ports of the Ryu (`double`→string) and DtoA algorithms for accurate and fast number-to-string conversion.

## `filesystem` — Virtual Filesystem

Package root: `org.monflabs.filesystem`

Provides `java.nio.file.FileSystem` implementations (package `org.monflabs.filesystem`: `memory`, `file`, `path`, `delegate`, `zip`, `resources`) that work uniformly over real files, in-memory trees, classpath resources, and ZIP archives. Used by the JS engine to resolve module paths and by the playground to manage snippets.

## `javacompiler` — Runtime Java Compilation

Package root: `org.monflabs.javacompiler`

Wraps the Java compiler API (`javax.tools`) to compile Java source strings in-memory and load the resulting classes. Used by the GaltaJS transpiler to compile generated Java code without writing to disk.

Key interface: `JavaCompilerFactory` / `JavaCompiler`. `FactoryClassLoader` is child-first for compiled classes and resources (platform packages `java.`/`javax.`/`jdk.`/`sun.`/`com.sun.` are parent-first), and `JavaCompiler.getClassLoader()` returns a fresh loader only when a compilation rewrote a class the current loader already loaded (a class loader defines a class once). Outputs are staged in memory and written to the `TargetFactory` only when the compilation succeeds (stale nested-class outputs are deleted); annotation processing is off (`-proc:none`) unless the options configure it. javac's class path includes earlier outputs via `TargetFactory.listClassFiles()` and the builder's parent loader chain (`file:` URLs only). Used by GaltaJS (`JSSourceModuleResolver`, transpiler tests): run `mvn test -pl parent-js/js -am` after changing it.

## `test` — Test Support

Package root: `org.monflabs.tests`

- `__BaseTestCase` — the JUnit 3 base class of the test cases (modules extend it through a `ProjectTestCase`): sets/restores the `America/New_York` default time zone around each test, checks resource leaks when the agent is installed (`-Dmonflabs.tests.trackLeaks=true`, on for the json/utilities modules via the `monflabs.tests.trackLeaks` Maven property)
- `UnitTestSupport` — the helper behind `__BaseTestCase.support` (not a base class): project-directory resolution, golden-file templates (`tests/<class path>/<name>`, failure message with a compact diff, `-Dmonflabs.tests.saveTemplates=missing|all` refused when `CI` is set), JSON comparisons
- `SuiteGuard` — added to each library module's main `All*Tests` suite: fails when a test class is not registered in the module's suites (`@SuiteGuard.NotInSuite("reason")` to exclude one)
- `leaks/` — `ResourceLeakAgent` (ByteBuddy, dynamic attach: surefire runs with `-XX:+EnableDynamicAgentLoading`), `ResourceTracker`, `ResourceLeakRule` (JUnit 4); design notes in `leaks/README.md`
