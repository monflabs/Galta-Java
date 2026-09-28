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

Key interface: `JavaCompilerFactory` / `JavaCompiler`. `FactoryClassLoader` is child-first for compiled classes, and `JavaCompiler.getClassLoader()` returns a fresh loader after a recompilation (a class loader defines a class once). javac's class path includes earlier outputs via `TargetFactory.listClassFiles()` and the builder's parent loader chain.

## `test` — Test Support

Package root: `org.monflabs.tests`

- `UnitTestSupport` — base class for all test cases; provides project-directory resolution
- `ResourceLeakRule` — JUnit rule that detects unclosed resources via ByteBuddy instrumentation
