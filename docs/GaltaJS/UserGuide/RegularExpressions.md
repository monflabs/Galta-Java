# Regular Expressions

GaltaJS does not ship its own matcher. The `RegExp` built-in delegates to a pluggable engine selected once per environment: the JDK's `java.util.regex` with a JavaScript-to-Java pattern translation, or Joni (a Java port of Oniguruma, embedded and customized for GaltaJS) with JavaScript syntax rules. Joni is the default and the most compliant; the JDK engine avoids any non-JDK code but has known gaps.

## Selecting an engine

`JSEnvironment.Builder.regexpEngineFactory(BiFunction<JSEnvironment, RegExp, RegExpEngine>)` sets the factory used for every `RegExp` created in the environment. Each engine class provides a `factory()` helper.

| Class | Ships in | Backend | External dependency |
|---|---|---|---|
| `RegExpEngineJoni` (default) | `js` (core) | customized Joni sources embedded under `org.monflabs.galtajs.external.org_joni` | none |
| `RegExpEngineJdkJavascript` | `js` (core) | `java.util.regex` | none |

When no factory is set, `ConfigurationImpl` uses `RegExpEngineJoni`. The embedded Joni is not the stock `org.jruby.joni` library, which GaltaJS does not use: it carries ECMAScript-semantics fixes and a fast path for non-unicode patterns.

Sample: `doc_examples/RegExpExamples.java` (`testSelectingTheEngine`)

```java
JSEnvironment joni = JavaScriptEnvironment.newBuilder().regexpEngineFactory(RegExpEngineJoni.factory()).build();
JSEnvironment jdk  = JavaScriptEnvironment.newBuilder().regexpEngineFactory(RegExpEngineJdkJavascript.factory()).build();

String script = "const m = /(?<year>\\d{4})-(?<month>\\d{2})/.exec('Released 2026-09'); m.groups.year + '/' + m.groups.month";
assertEquals("2026/09", joni.evaluateExpression(script));
assertEquals("2026/09", jdk.evaluateExpression(script));

// Unmatched alternatives are undefined (serialized as null) with both engines
assertEquals("[\"b\",null,\"b\"]", joni.evaluateExpression("JSON.stringify(/(a)|(b)/.exec('b'))"));
assertEquals("[\"b\",null,\"b\"]", jdk.evaluateExpression("JSON.stringify(/(a)|(b)/.exec('b'))"));
```

Compiled patterns can be cached with `regexpCacheSize(int)` (off by default).

## Unicode Sets mode (`v` flag)

The Joni engine implements the `v` flag character class grammar (`VClassParser`): set operations such as `[\p{L}--[a-z]]` work. The JDK engine rejects the flag with a `SyntaxError`.

Sample: `doc_examples/RegExpExamples.java` (`testUnicodeSetsFlagIsJoniOnly`)

```java
JSEnvironment joni = JavaScriptEnvironment.newBuilder().regexpEngineFactory(RegExpEngineJoni.factory()).build();
assertEquals(true, joni.evaluateExpression("/[\\p{L}--[a-z]]/v.test('A')"));

JSEnvironment jdk = JavaScriptEnvironment.newBuilder().regexpEngineFactory(RegExpEngineJdkJavascript.factory()).build();
try {
    jdk.evaluateExpression("/[\\p{L}--[a-z]]/v.test('A')");
    fail();
} catch(JSException e) {
    assertTrue(e.getMessage().contains("SyntaxError: Unicode Sets mode (v flag) is not yet fully implemented"));
}
```

Both engines handle supplementary characters as single code points in `u` mode (`/^.$/u.test('\u{1F4A9}')` is `true`; `testDefaultEngine`).

## Compliance and performance (measured snapshot)

The numbers below come from an earlier run of every test262 file whose path contains `regexp` (excluding `intl402/`, `staging/`, the harness and fixtures) in interpreted mode, one engine at a time, when a separate customized Joni engine still existed (it has since been merged into `RegExpEngineJoni`). Reproduce with `mvn test -pl parent-js/js-test-test262 -Dtest=Test262RegexpReportTest`; the per-file matrix is written to `target/regexp-test262-report.md`. The absolute counts drift as gaps get fixed (the `v` flag, for instance, has been implemented on Joni since), so treat them as an order of magnitude.

| Engine | Passed | Failed |
|---|---:|---:|
| JDK | 1936 | 365 |
| Joni | 1991 | 310 |

Joni passes noticeably more tests and is markedly faster on the regexp corpus, mostly because `java.util.regex` is prone to catastrophic backtracking on pathological patterns.

## Known gaps of the JDK engine

`java.util.regex` differs from ECMAScript in places that the translation layer cannot hide:

| Gap | Real-world impact |
|---|---|
| The `v` flag (Unicode Sets) is rejected with a `SyntaxError`. | Low (recent feature). |
| Capture groups inside a repeated atom are not cleared between iterations: when a group does not participate in the last iteration, `java.util.regex` keeps its stale value where the spec requires `undefined`. | Low to medium. |
| Named-group forward references (`/\k<a>(?<a>x)/`) throw; named groups with astral identifier characters fail to compile. | Low. |
| Unicode simple case folding differs in `iu` mode (`/K/iu.test("k")`, KELVIN SIGN). | Negligible. |
| Sticky `y` combined with multiline `^` at a non-zero `lastIndex` mismatches the spec. | Negligible. |

For typical validation, parsing and extraction code the JDK engine behaves identically to Joni. Pick it only when you need `java.util.regex` interop or must avoid any vendored code.

## Known gaps of the Joni engine

Joni has no native variable-length lookbehind (`(?<=a|abc)`); GaltaJS emulates it by reverse-scanning a separately compiled fragment (`LookbehindReversal`), so the feature works. The RegExp "modifiers" proposal (`(?i:...)` inline flag groups) is not implemented on any engine. Remaining deviations are tracked in [Known ECMAScript Gaps](/GaltaJS/KnownGaps).

## Choosing

- Default to Joni: best correctness and performance, no extra dependency.
- Use the JDK engine only under a hard constraint against vendored code.

## Gotchas

- `regexpEngineFactory()` is a builder option: it cannot be changed after `build()`.
- The regexp cache is disabled unless `regexpCacheSize()` is set.
- Internals of the translation (what each engine rewrites) are described in [RegExp Engines](/GaltaJS/Architecture/RegExpEngines).

## Source

`rt/builtins/standard/regexp/RegExpEngine.java`, `rt/builtins/standard/regexp/joni/RegExpEngineJoni.java`, `rt/builtins/standard/regexp/jdk/RegExpEngineJdkJavascript.java`, `external/org_joni/`, `ConfigurationImpl.java` (default selection)
