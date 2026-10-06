# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Galta is a Java mono repo of reusable libraries organized as Maven modules under `galta/` (the build tooling - the shared `monflabs-parent` pom and the Maven plugins - lives in `tools/`, and the repository root `pom.xml` aggregates both). It includes:

- **parent-utilities** — general-purpose Java utilities (strings, I/O, caching, filesystem, runtime compilation)
- **parent-json** — comprehensive JSON library with path queries, schema, serialization, and third-party adapters
- **parent-ui** — Swing UI framework with common components and IDE-grade editing support
- **parent-playground** — interactive scripting playground (Swing-based IDE for running code snippets)
- **parent-js** — **GaltaJS**, a JavaScript-inspired scripting engine for the JVM
- **parent-java** — modules for the Java language: the Java playground (Java and JShell snippets)

Most parent modules have their own `CLAUDE.md` with details (for GaltaJS see `galta/parent-js/js/CLAUDE.md` and the test-suite modules next to it). This file covers only repo-wide concerns.

## Build Commands

Maven commands run from `galta/` once `tools/` has been installed (a first build from the repository root installs both):

```bash
# Full build
mvn clean install

# Release build (with javadoc, sources, signing)
mvn clean install -P javadoc,sources,codesigning

# Release - from the repository root: stages to Maven Central, smoke-tests,
# tags and creates the GitHub release (rehearse with RELEASE_DRY_RUN=1).
# Never run it without being asked. See docs/BuildAndRelease.md.
buildtools/release.sh

# Documentation site: javadoc of the published modules -> docs/api/, CheerpJ
# playgrounds -> docs/playground/ (GaltaJS) and docs/java-playground/ (all
# gitignored), then preview it. The
# publish-docs workflow deploys docs/ to GitHub Pages on every push to master.
./buildtools/build-site.sh && ./serve-docs.sh

# Same, plus the full test262 suite (all 3 execution modes - hours, not
# minutes; skipped by default even on a plain full build - see
# parent-js/js-test-test262/CLAUDE.md)
mvn clean install -P javadoc,sources,codesigning,test262

# Build a single module and its dependencies
mvn clean install -pl parent-js/js --also-make

# Run tests for a specific module
mvn test -pl parent-js/js-test-rhino
```

Requires **Java 21** (`maven.compiler.release` in `tools/monflabs-parent`) and Maven 3.8.1 or later; the `maven-enforcer-plugin` validates both.

## Version Management

Version is managed via Maven CI-friendly `${revision}` (root `pom.xml`, plus `galta/galta-bom`; `tools/monflabs-parent` carries it as a literal) and resolved by `flatten-maven-plugin`. `tools/monflabs-parent` is the parent of everything (here and in the peer Monflabs repositories): it holds the Java level, every third-party dependency version (`dependencyManagement`) and every plugin version (`pluginManagement`). Galta's own artifacts are managed by `galta/galta-bom`, imported by `galta/pom.xml`. Module poms declare dependencies and plugins without versions; see `docs/BuildAndRelease.md`.

## Module Dependency Order

```
utilities  →  json  →  ui  →  playground  →  js (playground)
```

The `js` core engine depends on `json` and `javacompiler` (from utilities). The `js-playground` module ties everything together.
