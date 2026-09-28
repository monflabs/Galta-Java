# RegExp engines

`RegExp` objects delegate matching to a pluggable engine chosen once per environment. Two engines ship in the `js` module: a translation layer over `java.util.regex` and a port of Joni (Oniguruma) vendored under `external/org_joni`, with a customized fork also available as the separate `js-regexp-joni-custom` module. Choosing an engine and the compatibility trade-offs are covered in [Regular expressions](/GaltaJS/UserGuide/RegularExpressions); this page describes the machinery.

## Interface and selection

`rt/builtins/standard/regexp/RegExpEngine`:

```java
JSArray exec(JSRuntimeContext context, String str);
boolean test(JSRuntimeContext context, String str);
JSArray split(JSRuntimeContext context, String str, int limit);
JSArray match(JSRuntimeContext context, String str);
Iterator<JSArray> matchAll(JSRuntimeContext context, String str);
int search(JSRuntimeContext context, String str);
String replace(JSRuntimeContext context, String str, Object replace);
```

There is no factory type: the factory is a `BiFunction<JSEnvironment,RegExp,RegExpEngine>` set with `JSEnvironment.Builder.regexpEngineFactory(...)` and invoked by `JSEnvironment.createRegExpEngine(RegExp)`. `RegExpEngineJdkJavascript.factory()` and `RegExpEngineJoni.factory()` return one. The default is picked in a static initializer of `ConfigurationImpl`: if `org.monflabs.galtajs.external.org_joni.Regex` can be loaded (it always can, since `js` vendors it) the default is Joni, otherwise the JDK engine. Compiled patterns are LRU-cached through `JSEnvironment.getRegExp(expr, factory)` when `regexpCacheSize` is greater than 0.

## JDK engine

`jdk/RegExpEngineJdk` and its subclass `jdk/RegExpEngineJdkJavascript` rewrite a JavaScript pattern into a `java.util.regex` pattern in `preProcessRegExp(source, flags)`:

- octal escapes become `\u00hh` (octal is forbidden in unicode mode), `\v` becomes `\x0b`;
- the whitespace class is extended with the BOM (`\ufeff`) and the JavaScript-specific set (`\u0085 \u00a0 \u1680 \u180e \u2000-\u200a \u2028 \u2029 \u202f \u205f \u3000`);
- translations are context sensitive (plain character, inside a positive class, inside a negated class);
- capture groups inside a negative lookahead are tracked in a `long negLookCapBs` bitset, the beginning of the JavaScript rule that repeated groups are cleared on each iteration; `isValidGroup()` currently returns `true` unconditionally, with the old logic commented out after it broke on Java 17, so stale captures are a known deviation;
- Unicode property escapes are served from `jdk/UnicodeProperties`, `UnicodePropertyData`, `UnicodeStringPropertyData`;
- the `v` flag (Unicode sets) throws `SyntaxError: Unicode Sets mode (v flag) is not yet fully implemented`.

## Joni engine

`joni/RegExpEngineJoni` (about 4,800 lines) drives the vendored Joni:

- it defines its own `Syntax JS_SYNTAX`, and `translatePattern()` rewrites what Joni's options cannot express locally: `.` becomes `[^\n\r\u2028\u2029]` (or `[\s\S]` under a local `(?s:...)`), and `^`/`$` under `m` become `(?:\A|(?<=[...]))` / `(?=\r\n|[...]|\z)`;
- two bespoke encodings decide surrogate handling: `LenientUTF16BEEncoding` for `u`/`v` mode (a valid pair is one character, so a match cannot start between halves) and `LenientUTF16BECodeUnitEncoding` for non-unicode mode (every code unit is a character, so `/\udf06/` can match the low half of a pair);
- `LookbehindReversal` implements lookbehind, `VClassParser` parses the `v`-flag character-class grammar (set operations, `\q{...}`, string properties).

`js-regexp-joni-custom` holds the customized fork under the same package name for consumers who want to replace the vendored copy; the module's own `README.md` is a stale stub.

## Source

`rt/builtins/standard/regexp/RegExpEngine.java`, `rt/builtins/standard/regexp/jdk/RegExpEngineJdk.java`, `.../jdk/RegExpEngineJdkJavascript.java`, `.../jdk/UnicodeProperties.java`, `.../joni/RegExpEngineJoni.java`, `.../joni/LenientUTF16BEEncoding.java`, `.../joni/LenientUTF16BECodeUnitEncoding.java`, `.../joni/LookbehindReversal.java`, `.../joni/VClassParser.java`, `external/org_joni/`, `ConfigurationImpl.java` (default selection), `JSEnvironment.java` (`createRegExpEngine`, `getRegExp`).
