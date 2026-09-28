# Preprocessor

`preprocessor/ScriptPreProcessor` implements a small C-style conditional compilation step for JavaScript sources: `#if` / `#elif` / `#else` / `#endif` directives written as line comments select which lines stay active. It is a standalone utility; the engine does not run it by itself, so the host calls it before compiling the text.

## Usage

`ScriptPreProcessor.preprocess(String source, Map<String,Object> symbols)` returns the processed source. Directives are line comments of the form `// #if SYMBOL`; suppressed lines are commented out (prefixed with `//`) rather than removed, so line numbers in error messages and stack traces still match the original file.

Sample: `doc_examples/PreprocessorExamples.java` (`testConditionalBlocks`)

```java
private static final String SOURCE = """
    let mode;
    // #if DEBUG
    mode = 'debug';
    // #else
    mode = 'prod';
    // #endif
    mode
    """;
```

```java
// The preprocessor is a separate step: the host runs it before compiling
String debug = ScriptPreProcessor.preprocess(SOURCE, Map.of("DEBUG", true));
String prod  = ScriptPreProcessor.preprocess(SOURCE, Map.of("DEBUG", false));

JSEnvironment env = JavaScriptEnvironment.create();
assertEquals("debug", env.evaluateScript(debug));
assertEquals("prod", env.evaluateScript(prod));

// Suppressed lines are commented out, not removed, so line numbers are preserved
assertTrue(debug.contains("//mode = 'prod';"));
assertEquals(SOURCE.lines().count(), debug.lines().count());
```

## Directives and symbols

| Directive | Meaning |
|---|---|
| `// #if SYMBOL` | Starts a block that stays active when `SYMBOL` is truthy. |
| `// #elif SYMBOL` | Alternative branch. |
| `// #else` | Fallback branch. |
| `// #endif` | Ends the block. |
| `// #define`, `// #undef` | Recognized but not implemented: they throw. |

Whitespace between `//` and `#` is allowed. The condition is a single symbol name looked up in the map; expressions (`&&`, `!`, comparisons) are not supported. Truthiness follows JavaScript rules: a `Boolean` is itself, a `Number` is true when non-zero, a `String` when non-empty, `null` or an unknown symbol is false.

Sample: `doc_examples/PreprocessorExamples.java` (`testSymbolsAreTruthyValues`)

```java
String r = ScriptPreProcessor.preprocess("// #if LEVEL\nx\n// #endif", Map.of("LEVEL", 0));
assertTrue(r.contains("//x"));
r = ScriptPreProcessor.preprocess("// #if NAME\nx\n// #endif", Map.of("NAME", "prod"));
assertFalse(r.contains("//x"));
// An unknown symbol is false
r = ScriptPreProcessor.preprocess("// #if OTHER\nx\n// #endif", Map.of());
assertTrue(r.contains("//x"));
```

## Limitations

- `#if` blocks cannot be nested: `PreProcessorException("#if cannot be nested")` (`testNestedIfIsNotSupported`).
- Conditions are single symbols.
- `#define` and `#undef` throw "not yet implemented".
- A source without any `// #` directive is returned unchanged (a quick regular-expression check short-circuits the whole pass).

## How the engine's tests use it

The engine's own test suite runs every test script in interpreted, optimized, transpiled and decompiled mode from one `.js` file. `tests.BaseProjectTestCase` preprocesses each file with the symbols `INTERPRETER`, `TRANSPILER`, `REGEXP_JDK` and `REGEXP_JONI`, so a test can keep mode-specific assertions in one place:

```js
// #if TRANSPILER
assertEquals("transpiled", mode)
// #else
assertEquals("interpreted", mode)
// #endif
```

## Source

`preprocessor/ScriptPreProcessor.java`, `preprocessor/PreProcessorException.java`, `galta/parent-js/js/src/test/java/tests/BaseProjectTestCase.java`
