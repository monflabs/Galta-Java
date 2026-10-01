# Changelog

All notable changes to Galta. Versions follow the `<revision>` of the root
`pom.xml`; the release notes of a GitHub release are the matching section below
(`buildtools/release.sh` extracts it).

## 0.8.0 (unreleased)

First public release: Galta is published to Maven Central under the
`org.monflabs.galta` group id and licensed under the Apache License 2.0.

### Using Galta

- New `galta-bom`: import `org.monflabs.galta:galta-bom:0.8.0` and declare the
  Galta modules without a version. It manages Galta's own artifacts only.
- Documentation site with the user guides, the javadoc of every published module
  and the GaltaJS playground running in the browser (CheerpJ).
- Java 21 or later is required.

### JSON library

- Standards compliance: JSON Pointer follows RFC 6901 (`/` is the empty key,
  `-` never reads an element, `-0`/`01` are keys), JSONPath literals, missing
  operands and slice steps follow RFC 9535, `$ref` resolution reports missing
  targets, follows chains and detects cycles.
- Numbers compare exactly (no truncation, `-0.0` equals `0`); number parsing
  picks `BigDecimal` by value, not by literal length; `NaN`/`Infinity` are
  written as `null`; number output matches JavaScript exactly.
- The strict parser rejects control characters and `\'` on every path and limits
  nesting depth; `{null:1}` gives the key `"null"`; error line/column positions
  are fixed.
- `JsonObject`/`JsonArray` follow the `Map`/`List` equality contracts; the
  checked containers guard every mutator.
- Faster: parsing 1.1-1.4x (25-50% less allocation), stringifying 1.2-2.3x,
  pretty printing of deep documents up to 5.5x. JMH benchmarks against Jackson
  and Gson are in `json-performance`.
- Add-on modules: `KeyEncryptor` uses AES-GCM with a random IV and a salted
  PBKDF2 key (older values still decrypt); import/export encodes collection
  names (no path traversal) and is idempotent; memory database exports read a
  snapshot; serialization rejects lossy number conversions, keeps field order,
  supports enums and top-level collections and reports cycles; YAML sets,
  `NaN` and recursive aliases are handled; FastCSV 4 (a CSV writer flush bug was
  fixed on the way).

### Utilities

- File systems: sandbox escapes through dangling symlinks and drive-letter-like
  names are closed; the memory file system follows the `java.nio` contracts
  (shallow directory copy, no data loss on move); attribute views work.
- `FileUtil` no longer deletes inside symlinked directories; buffered streams
  follow the `skip`/`mark`/`reset` contracts; I/O defaults to UTF-8.
- Reflection access works on JDK collections and picks the most specific
  overload; the profiler is thread-safe; the Java compiler returns recompiled
  classes and sees earlier compilation results.
- Golden-file test templates no longer create themselves silently; saving them
  is opt-in (`-Dmonflabs.tests.saveTemplates=missing|all`).

### GaltaJS

- Engine fixes: unreachable-code optimizer, `-0` in integer arithmetic,
  hoisting on re-execution, `"use strict"` in debug mode, computed class keys,
  template literals in unbraced loops, Date parsing and setters, typed array
  sizes, `Symbol.for`, `Math` edge cases, Promise arguments, Map/Set keys,
  `FinalizationRegistry`, Atomics timeouts, async executor leaks and interrupt
  handling.
- Early errors are reported at parse time, as the specification requires:
  invalid identifier escapes (`var\u000Ax`), invalid assignment targets, rest
  element and parameter rules, RegExp literal syntax, `super`/`new.target`/
  `arguments` contexts, redeclarations, declarations in statement position,
  class element rules, malformed string escapes and more. Code that GaltaJS
  used to accept (or reject only when it ran) is now a `SyntaxError`. The
  test262 harness checks negative tests strictly (expected error type and
  phase), and every test262 file in scope now passes with none filtered:
  module-only rules (HTML-like comments, duplicate exports, unpaired
  surrogates in export names), `await`/`yield` restrictions, class heritage
  and static block rules, `in` in a `for (;;)` head, the ASI restrictions
  (no semicolon inserted before `else` on the same line, nor after `throw`,
  nor before `=>`), tagged templates in optional chains and more.
- Module graphs are linked before they run: every import and re-export is
  resolved first, so a missing, ambiguous or circular export, or a syntax
  error in any dependency, is a `SyntaxError` before any module code runs.
  `import`/`export` at the top level of a script stays allowed by default;
  the new `supportImportExportInScripts(false)` builder option rejects it.
- `new` accepts every MemberExpression target: `new a[i]()`, `new this.#C()`
  and `new async function () {}` (a `TypeError` at run time) now parse as the
  specification says. `new X[n]` still creates a Java array when `X` is a Java
  class. The JavaCC grammar generates without warnings.
- `Reflect.defineProperty` returns `false` instead of throwing when the
  property cannot be defined, and a proxy `set` trap returning false throws
  a `TypeError` in strict code.
- Proper tail calls: a call in tail position in strict code runs in constant
  stack space, in both execution modes.
- Multiple realms: a built-in function runs in the realm it belongs to (its
  errors, prototypes and new objects come from that realm), and values of one
  realm used from another keep their own intrinsics.
- `Temporal`: the whole namespace (`Instant`, `ZonedDateTime`, `PlainDate`,
  `PlainTime`, `PlainDateTime`, `PlainYearMonth`, `PlainMonthDay`, `Duration`,
  `Now`) with the ISO 8601 calendar and the IANA time zones of `java.time`,
  plus `Date.prototype.toTemporalInstant()`. Other calendars and
  `toLocaleString()` formatting need ECMA-402, which GaltaJS does not provide.
- `ShadowRealm`: code evaluated in a separate realm, which only exchanges
  primitives and wrapped functions with its caller. With these, test262 runs
  with no feature skipped and passes in all three execution modes.
- Regular expressions: the customized Joni engine is merged into the core `js`
  module and remains the default (`RegExpEngineJoni`); the separate
  `js-regexp-joni-custom` module and `RegExpEngineJoniCustom` are removed, and
  the stock `org.jruby.joni:joni` artifact is no longer referenced. Non-unicode
  patterns now use the fixed-width fast path (char reads, `String.indexOf`
  searches) and `test()` no longer builds capture regions.
- Regular expressions now report every pattern early error test262 checks:
  exact Unicode property names (`\p{lu}`, `\p{Greek}`, `\p{Other_Alphabetic}`
  are `SyntaxError`s), class escapes as `u`-mode range endpoints, the `v`-mode
  reserved class characters, quantified lookbehinds and a bare `\k` in a
  pattern with named groups. The legacy static properties (`RegExp.$1`-`$9`,
  `lastMatch`, `leftContext`, ...) now track the last match.
- The `js-mod-node` module is removed: the core `NodeLibrary` provides `fs`
  and `fs/promises`, and `new NodeModuleResolver(fileSystem)` resolves their
  paths in a given `java.nio.file.FileSystem`.
- Modules: a missing export is a `SyntaxError`, a rejected top-level `await` is
  reported, and modules waiting on an async dependency run in the
  specification's order.
- Security: the `AccessManager` can no longer be bypassed through reflection.
- Debugger/CDP: no hang when no client is attached, no evaluation races,
  conditional breakpoints and logpoints, `Debugger.resumed` sent before the
  next `Debugger.paused`.
- The playground runs its snippets off the Swing event thread, and the
  `js-transpiler-maven` plugin honours its options (and works with Maven 3.9).

### Build

- A shared parent, `monflabs-parent`, manages every plugin and library version,
  with enforcer rules against version drift; plugins and libraries upgraded
  (GraalVM 25.0.1 on the `org.graalvm.polyglot` coordinates, json-path 3,
  snakeyaml-engine 3, Rhino 1.9.1, ...).
- Every test class runs exactly once; the build works on Windows and on JDK 25.
- The in-repository `js-test-performance` module is replaced by the separate
  JavascriptPerformance project.
