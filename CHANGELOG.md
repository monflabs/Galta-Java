# Changelog

All notable changes to Galta. Versions follow the `<revision>` of the root
`pom.xml`; the release notes of a GitHub release are the matching section below
(`buildtools/release.sh` extracts it).

## 0.8.0 (2026-10-02)

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
- Hardening against hostile input: number literals are limited to 1000
  characters (`setMaxNumberLength`), nesting deeper than 1000 levels fails
  cleanly when parsing and stringifying, error messages clip the source
  excerpt, YAML alias expansion has a budget, and huge `BigDecimal`s are
  checked before any integral conversion.
- Text: `stringify` writes non-ASCII characters as they are, like
  `JSON.stringify` (`setEscapeNonAscii(true)` restores ASCII output); `""`
  parses to `null` (strict mode rejects it); a leading BOM is skipped in
  lenient mode only; strict parsing of byte streams rejects malformed UTF-8.
- Model: byte/short getters saturate instead of wrapping, the `as*`
  conversions of strings follow one rule, `hashCode()` agrees with number
  equality, cyclic containers report a `CircularReference` and `toString()`
  writes it as `"[circular]"`.
- JSONPath: the RFC 9535 functions (`length`, `count`, `match`, `search`,
  `value`), exact number literals, compile-time rejection of non-singular
  comparisons and an opt-in strict mode. `$ref` resolution loads each document
  once, resolves relative references against their own document and supports
  `$id`/`$anchor` in schemas; JSON Schema validation fails closed on an
  unresolvable reference.
- Serialization: JSON nulls are assigned to reference fields, subclass items in
  collections keep their data, declared collection types (`TreeMap`,
  `EnumSet`...) are honoured, built-in adapters for `java.time`, `UUID`, `URI`,
  `Date` and `Optional`, sealed hierarchies (`sealedTypes()`), `TypeRef`
  generic tokens and error messages with the path of the failing value.
- Import/export and memory database: exports are written atomically, CSV
  edge cases (single column, unclosed quotes, formula injection option) are
  handled, the range filter applies to every source, and transactions are
  overlays that only conflict on the records they write (a record committed
  after a replication started is no longer skipped).
- Configuration: encrypted values are bound to their key path (`[[v3:...]]`,
  600,000 PBKDF2 iterations; older values are re-encrypted on load), `$ref`
  fragments are written back and encrypted, a resource referenced twice is
  shared, symlinks can't escape the folder, and a failed save leaves the
  configuration unchanged.
- JSON Pointer is faster: reading through a parsed pointer about 25%,
  parsing a new pointer string 1.4x, parsing and reading 1.6x (now faster than
  Jackson for both reading and parse-and-read), parsing the same string again
  almost free (the last pointers are kept), printing 2x, hashing and comparing
  without allocating. A part such as `"-"`
  or `"-1"` built with `getChild()`/`ofParts()` now behaves on arrays like the
  same token parsed (equal pointers behave the same), and a `setValue()` that
  fails no longer leaves the containers it created. `$ref` resolution is
  1.3x to 2x faster: each target is located once, and URLs are normalized once.
- `JsonArray` indexes follow the `java.util.List` contract in every method: a
  negative index is out of range for the typed getters, setters, `add`, `has`,
  the `is*` tests and the `as*` conversions too. Negative indexes, counted from
  the end, move to `at` counterparts that call the regular methods: `at()`,
  `atString()`, `atInt()`..., `hasAt()`, `isNullAt()`..., `asIntAt()`...,
  `addAt()`, `setAt()` and `deleteAt()`. `actualIndex()` is removed.
- The layer that let `JsonFactory` sit on top of other JSON libraries is
  removed, as Galta's own containers are the only ones: the `toNative*()`,
  `isNative*()`, `as*(nativeValue)`, `toJavaPrimitive()`, `nativeEquals()` and
  `JsonContainer.toNativeJsonPrimitive()` methods, the `supportsNaN()`,
  `supportsInfinity()`, `supportsReferences()` and `supportsNullKeys()` flags
  (NaN, infinities and references are always supported, null keys never), and
  `AbstractJsonObject`. `JsonFactoryService` stays: GaltaJS plugs its factory in
  with it.
- `JsonObject` and `JsonArray` hold every helper as a `default` method built on
  the `Map`/`List` methods: the typed getters and setters, the `is*` tests and
  the array functions (`filter`, `map`, `slice`...). The implementations only
  provide the storage. The JavaScript objects now share the Java behavior:
  `getByte()`/`getInt()` saturate instead of wrapping (300 is 127 as a byte),
  and their errors name the key.
- New `json-jackson` module, replacing `json-serialization` (removed): register
  its `GaltaJsonModule` in a Jackson `ObjectMapper`, and `JsonObject`/`JsonArray`
  are read and written by Jackson, can be fields of Java objects, and convert to
  and from Java objects (`convertValue`) and `JsonNode`. The containers come from
  the module's factory, with its number rules; with a GaltaJS environment's factory
  they are JavaScript objects, and writing a script value gives what
  `JSON.stringify()` gives (new `JsonFactory.exportValue()`). To migrate from
  `SimpleRegistry`: `mapper.convertValue(value, MyClass.class)`.

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
- `ClassMetadata` no longer keeps its instances (and GaltaJS environments)
  alive forever, ranks numeric overloads by Java widening, matches a String to
  a `char` only when it has one character, ignores bridge methods and supports
  varargs.
- Generators: an interrupted consumer gets a `CancellationException`; a body
  that keeps yielding after being abandoned gets a `GeneratorAbandonedError`
  instead of spinning; `GeneratorScheduler.createPlatformExecutor()` for bodies
  that yield inside `synchronized` code.
- File systems follow the JDK `java.nio` behaviour, checked by a conformance
  test against the default file system: closed file systems, `relativize`,
  foreign paths, NOFOLLOW operations in sandboxes, memory moves that keep the
  file, zip entries streamed (random access capped at 256 MB). A backslash is
  no longer a separator on `/` file systems.
- The Java compiler writes its outputs only when the compilation succeeds,
  doesn't run annotation processors unless asked, keeps its class loader when
  nothing it loaded changed and serves generated resources.
- Strict `Properties` parsing, `@Required` always checked by `ObjectBuilder`,
  `WriterOutputStream` flushes only on `flush()` (or with `autoFlush`), ISO
  dates before 1582 use the proleptic Gregorian calendar.
- Removed unused API: `SimpleDtoA`, `SingletonSupplier`,
  `CloseableSingletonSupplier`, `QuickSort`, `ToBeRemovedException`,
  `TriConsumer`, `QuadConsumer`, `QuadFunction`, `NullInputStream`,
  `NullReader`, `NullWriter`, `CharIterator`, `IntIterable`, `LongIterable`,
  `HighResolutionTimer`, `JsonNumberType`, `JavaJsonContainer` and a few
  `JsonUtil`/`IOStreamUtil` helpers.

### UI and playground

- The playground cancels a superseded run, saves snippets atomically, handles
  binary snippet files, doesn't record a loaded snippet as an undoable edit and
  no longer leaks its editors; the console stream can't deadlock with the
  Swing event thread. The GaltaJS playground no longer bundles Rhino.
- Removed unused UI API (`JTreeTable` and a few lookups and converters) and the
  `demo-airline` and `demo-retail` modules.

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
- A decorated private class member (`@dec #x`, `@dec accessor #x`) no longer
  fails the class definition: the decorator context names it `"#x"`.
- Faster transpiled code (the transpiled TypeScript compiler runs about 40%
  faster): calls to functions of large scopes go straight to their dispatch
  block instead of through a chain of classes, functions that need no per-call
  context skip it there too, and large functions have statement lists moved to
  regions so that every method stays small enough for the JIT
  (`methodBudget` transpiler option). Transpiling is also much faster on large
  sources (4 s instead of 4 minutes for TypeScript), and unchanged generated
  files are not rewritten.
- Java interop: overload resolution fixes. `Math.max(1, 2.5)`, `Math.clamp()`
  and other mixed int/double calls are no longer ambiguous; a function can be
  passed to an `Object` parameter, is only adapted to functional interfaces,
  and prefers `Callable` over `Runnable` (`executor.submit(fn)`); `undefined`
  reaches Java as `null`; `f(char)`/`f(Object)` calls `f(Object)` for `'a'`;
  `f(int)` wins over `f(Integer)`; a number is no longer accepted for an
  `AtomicInteger`; unrelated parameter types are an ambiguity, as in Java; a
  method wins over a getter property of the same name (`shutdown()` next to
  `isShutdown()`). Member access and calls are 20-40% faster.
- `Java.to(value, type)` converts a JavaScript array or array-like value to a
  Java array (with the JavaScript conversions, nested for `int[][]`) or to a
  `List`, `Deque` or `Set`; `Java.from(value)` copies a Java array or
  collection into a JavaScript array. `Java.type()` accepts array type names
  (`'int[]'`), and `new` on an array type creates an array of that length.
- `JSON.rawJSON` uses the strict parser, `JSON.parse` errors give the position,
  a too deeply nested `JSON.stringify` throws a `RangeError`, and non-ASCII
  characters are written as they are.
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
- Every jar carries `META-INF/LICENSE`, `META-INF/NOTICE` and the texts of the
  third-party licenses of the code it bundles (`META-INF/licenses/`); NOTICE
  lists every bundled component. The release script checks the jars, the
  CHANGELOG date and the list of unpublished modules before anything is staged.
- The JSON Schema module gets jsonschemafriend from Maven Central (the
  `org.metaeffekt.bundle.jsonschemafriend` build of the same code): no JitPack
  repository is needed any more.
- Transpiled code keeps the license comment of its JavaScript source instead of
  a Monflabs copyright line.
- The unused character-set transcoders of the regular expression engine are
  removed (1.4 MB less in `js.jar`).

- A shared parent, `monflabs-parent`, manages every plugin and library version,
  with enforcer rules against version drift; plugins and libraries upgraded
  (GraalVM 25.0.1 on the `org.graalvm.polyglot` coordinates, json-path 3,
  snakeyaml-engine 3, Rhino 1.9.1, ...).
- Every test class runs exactly once; the build works on Windows and on JDK 25.
- Every library test suite checks that it registers all the test classes of its
  module; coverage is reported for the json and utilities libraries
  (`target/site/jacoco-aggregate` of their test modules); resource leak
  detection runs in the json and utilities test suites.
- The in-repository `js-test-performance` module is replaced by the separate
  JavascriptPerformance project.
