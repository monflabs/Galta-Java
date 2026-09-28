# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.


## Principles

Always follow the following principles:  

1. Don’t assume. Don’t hide confusion. Surface tradeoffs.

2. Minimum code that solves the problem. Nothing speculative.

3. Touch only what you must. Clean up only your own mess.

4. Define success criteria. Loop until verified.



## Module Overview

This is the `js` core module of **GaltaJS** — a full JavaScript engine (ES6+) implemented in Java, targeting JVM. It lives within the `parent-js` multi-module Maven project. The working directory is `parent-js/js/`.

## Build & Test Commands

All commands run from `galta/` (the Maven root of the libraries), not from this module directory:

```bash
# Build only this module (and its dependencies: json, javacompiler)
mvn clean install -pl parent-js/js --also-make

# Run this module's tests
mvn test -pl parent-js/js

# Run a specific test class
mvn test -pl parent-js/js -Dtest=AllGaltaJSTests

# Run tests for a related module (e.g., Rhino compatibility)
mvn test -pl parent-js/js-test-rhino
```

The test runner entry point is `AllGaltaJSTests`, which runs four test passes over the same test suite: interpreted, interpreted+optimized, transpiled, and decompiled+re-parsed.

## Architecture

### Execution Pipeline

```
JS source → ScriptPreProcessor → JSParser (JavaCC) → ASTProgram → [optimizer] → execute
                                                                               ↓
                                                              InterpretedGlobalRuntimeContext (AST walk)
                                                                            OR
                                                              JSTranspiler → Java source → JavaCompiler → TranspiledGlobalRuntimeContext
```

### Key Entry Points

- **`JSEnvironment`** — the root object. Create one per application; it holds all configuration, registered libraries, accessor maps, and caches. Call `env.createScript(source, name)` to parse, then execute with a context.
- **`JSScriptExecutor`** — higher-level helper for running scripts/files without manually managing contexts.
- **`JSParser.jj`** (in `src/main/javacc/org/monflabs/galtajs/parser/`) — the JavaCC grammar (source of truth for the parser). `javacc-maven-plugin` regenerates `JSParser.java` (+ support classes) into `target/generated-sources/javacc/` automatically on the `generate-sources` phase — edit `.jj`, not the generated output; a plain `mvn install`/`mvn test` picks up grammar changes with no manual step. `TokenMgrError.java` (in `src/main/java/.../parser/`) is the one exception: it's hand-customized (adds `errorLine`/`errorColumn` fields `ScriptError.java` depends on), so it's a normal, hand-maintained source file instead — the build deletes javacc's own generated copy of it before compiling, so only the hand-maintained one exists.

### Package Structure under `org.monflabs.galtajs`

| Package | Purpose |
|---|---|
| `parser/` | JavaCC grammar (`src/main/javacc/.../parser/JSParser.jj`) + a few hand-maintained support classes (`TokenMgrError.java`, `ScriptError.java`, etc.) in `src/main/java/.../parser/`; the rest of the parser is generated into `target/generated-sources/javacc/` at build time |
| `node/` | AST node hierarchy rooted at `ASTNode` / `ASTProgram` |
| `rt/` | Runtime: contexts, interpreter, transpiler runtime, executors |
| `rt/builtins/` | All ECMAScript built-ins (primitives, standard objects, errors) |
| `transpiler/` | AST → Java source code generation (`JSTranspiler`) |
| `optimizer/` | AST-level constant folding and dead-code removal |
| `modules/` | Module resolution and `JSInterpretedUnit` (compiled script unit) |
| `library/` | Extension libraries, including `java/JSJavaLibrary` for Java interop |
| `jsonfactory/` | `JSObject` backed by `JsonObject` for native JSON integration |
| `preprocessor/` | Directive-based source pre-processing |
| `debug/` | Debugging infrastructure |

### Accessor Pattern (no wrapper objects)

GaltaJS avoids wrapper types (no `JSNumber`, `JSString`). Java primitives (`Integer`, `Double`, `String`, `Boolean`) are used directly. Per-class `JSAccessor` instances registered in `JSEnvironment` provide all JavaScript semantics (property lookup, method calls, coercions). This is the central design decision — when adding support for a new Java type, register an accessor rather than wrapping.

### Dual Execution Modes

- **Interpreted**: `InterpretedGlobalRuntimeContext` walks the AST directly. Default for development and testing.
- **Transpiled**: `JSTranspiler` converts the AST to Java source, which is compiled in-memory by `javacompiler` and loaded via `PathClassLoader`. The Maven plugin `js-transpiler-maven` can generate transpiled classes at build time.

Tests run both modes to ensure equivalence. Transpiled Java is saved under `src/test/java/compiled/` for inspection (excluded from normal compilation via `maven-compiler-plugin` config).

### GaltaJS Extensions (beyond ECMAScript)

- `BigDecimal` type via `0m` literal suffix; `DECIMAL64` precision
- Long integer promotion on overflow (`JSEnvironment.Builder.supportLongPromotion(true)`)
- JSON path operators: `..` (deep scan), `.*`, `?[]` (filter), `!.`/`![` (non-null required access), `?:` (null coalescing)
- Sequence type for multi-value results from path expressions
- Operator broadcasting over sequences (e.g., `seq + 1` applies to all elements)

These extensions are switched on by `JSEnvironment.Builder.enableGaltaJSExtensions()` (which also enables long promotion and sequences; `supportLongPromotion(...)` and `supportSequenceExtensions(...)` set them individually). Test environments leave them off by default; GaltaJS-specific tests use a subclass that enables them.

### Test Infrastructure

- Each test class corresponds to a `.js` file loaded from the classpath as a resource (same package, same base name).
- `BaseProjectTestCase.execute()` loads the co-located `.js` file by convention.
- `GlobalTestEnvironment` sets up the standard test environment with Java interop objects (`obj`, `javaEX`, `javaTest`).
- `GaltaJSTestSuite` lists the JavaScript test classes that run in every execution mode; suites in `AllGaltaJS*Tests.java` control which execution modes to run. Java-level tests that don't depend on the mode (debugger, CDP, type inference, optimizer internals, `*JavaTest` regressions) are registered once, directly in `AllGaltaJSTests`. A test class registered in neither never runs: surefire only includes `AllGaltaJSTests` and `AllDocExamplesTests`.
- `src/test/java/compiled/` holds transpiled output saved for inspection; it is excluded from both main and test compilation (`excludes`/`testExcludes` in the pom).
- `js-test-rhino/rhino` (Rhino's ECMA test suite) is fetched at a pinned commit, not a git submodule - `mvn test -pl parent-js/js-test-rhino` fetches it automatically the first time (the `fetch-externals` profile auto-activates when it's missing); no separate step needed

## Module Dependencies

This module depends on:
- `org.monflabs.galta:json` — JSON library (provides `JsonObject`, `JsonArray`)
- `org.monflabs.galta:javacompiler` — in-memory Java compilation (used by transpiler)

Test scope also pulls in `js-library-v8` and GraalVM JS for cross-engine comparison.
