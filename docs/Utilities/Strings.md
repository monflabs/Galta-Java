# Strings

`org.monflabs.util` has four small string helpers that the rest of Galta is built on: `StringUtil` (null-safe static helpers), `StringFormat` (the `{0}` message formatter used by every Galta exception), `StringMatcher` (the hand-written scanner behind the path and expression parsers) and `TextBuilder` (an indenting `StringBuilder` for code generation). All of them are in the `utilities` artifact.

## StringUtil

Every method is static and accepts `null` where it makes sense. A theme runs through the class: `null` and `""` are treated as the same "empty" value.

| Group | Methods | Notes |
|---|---|---|
| Emptiness | `isEmpty`, `isNotEmpty`, `nonNull` | `nonNull(null)` is `""` |
| Comparison | `equals`, `equalsIgnoreCase`, `compareTo`, `compareToIgnoreCase` | `null` equals `""`; empty values sort first |
| Search | `containsIgnoreCase`, `indexIgnoreCase` | no regular expressions, no allocation |
| Split / join | `splitString(s, sep[, trim])`, `join(array, sep[, index, length][, ignoreNulls])`, `concatStrings(array, sep, trim)`, `toString(Object)` | single-character separators; `join` prints `null` as `""` and expands arrays as `[a,b]` |
| Trim / pad | `trim`, `trimLeft`, `padLeft`, `padRight`, `truncate` | `truncate` appends `...` within the maximum length |
| Replace | `replaceFirst`, `replaceAll` (`String` or `char` arguments), `normalizeLineBreaks` | literal text, never a regular expression |
| Case | `toCamelCase`, `toKebabCase`, `capitalizeFirstCharacter` | |
| Misc | `redacted`, `toUnsignedHex2`, `toUnsignedHex4` | |

### Splitting and joining

`splitString` keeps empty parts and never returns `null`; the three-argument form trims every part. `join` converts each element with `StringUtil.toString`, so a `null` element becomes an empty string (or disappears with `ignoreNulls`) and a nested array is printed element by element. The `index`/`length` form clamps the length to the end of the array; a negative or out-of-range index returns `""`.

Sample: `doc_examples/util/StringsExamples.java` (`testSplitAndJoin`)

```java
assertArrayEquals(new String[] {"a", "b", "", "c"}, StringUtil.splitString("a,b,,c", ','));
assertArrayEquals(new String[] {"a", "b"}, StringUtil.splitString(" a , b ", ',', true));   // trim each part
assertArrayEquals(new String[] {""}, StringUtil.splitString("", ','));
assertEquals(0, StringUtil.splitString(null, ',').length);

Object[] values = {"a", null, 42, new int[] {1, 2}};
assertEquals("a,,42,[1,2]", StringUtil.join(values, ','));        // null -> "", arrays expanded
assertEquals("a,42,[1,2]", StringUtil.join(values, ',', true));   // skip nulls
assertEquals("42,[1,2]", StringUtil.join(values, ',', 2, 5));     // from index 2, length clamped
```

### Null-safe helpers

Sample: `doc_examples/util/StringsExamples.java` (`testNullSafeHelpers`)

```java
assertTrue(StringUtil.isEmpty(null));
assertTrue(StringUtil.equals(null, ""));             // null and "" are both "empty"
assertEquals(-1, StringUtil.compareTo(null, "a"));    // empty sorts first
assertNull(StringUtil.trim(null));
assertEquals("x  ", StringUtil.trimLeft("  x  "));
assertEquals("", StringUtil.nonNull(null));
assertTrue(StringUtil.containsIgnoreCase("Content-Type", "TYPE"));
```

### Padding, truncation and case

`padLeft`/`padRight` never shorten a string that is already long enough. `truncate(s, max)` keeps `max-3` characters and appends `...`; when `max` is 3 or less there is no room for the ellipsis and the string is simply cut to `max` characters. `toCamelCase` lower-cases everything that does not follow a dash, and `toKebabCase` puts a dash before each word, keeping an acronym together (`URLValue` gives `url-value`).

Sample: `doc_examples/util/StringsExamples.java` (`testPadTruncateCase`)

```java
assertEquals("007", StringUtil.padLeft("7", 3, '0'));
assertEquals("ab..", StringUtil.padRight("ab", 4, '.'));
assertEquals("12345", StringUtil.padLeft("12345", 3, '0'));   // never truncates
assertEquals("Hello...", StringUtil.truncate("Hello world", 8));
assertEquals("00ff", StringUtil.toUnsignedHex4(255));

assertEquals("backgroundColor", StringUtil.toCamelCase("background-color"));
assertEquals("fooBar", StringUtil.toCamelCase("Foo-BAR"));          // other letters are lower-cased
assertEquals("background-color", StringUtil.toKebabCase("backgroundColor"));
assertEquals("url-value", StringUtil.toKebabCase("URLValue"));     // an acronym stays one word
assertEquals("Hello", StringUtil.capitalizeFirstCharacter("hello"));
```

### Replacing

`replaceAll` and `replaceFirst` work on literal text, unlike `String.replaceAll`, which takes a regular expression. An empty search string returns the source unchanged, a `null` replacement removes the matches, and a `null` or empty source returns `""` (not `null`). `normalizeLineBreaks` turns `\r\n` and a lone `\r` into `\n` (`\n\r` is two line breaks).

Sample: `doc_examples/util/StringsExamples.java` (`testReplace`)

```java
assertEquals("a/b/c", StringUtil.replaceAll("a.b.c", ".", "/"));   // literal, not a regex
assertEquals("a/b.c", StringUtil.replaceFirst("a.b.c", ".", "/"));
assertEquals("abc", StringUtil.replaceAll("abc", "", "x"));        // empty search: source unchanged
assertEquals("ac", StringUtil.replaceAll("abc", "b", null));       // null replacement removes
assertEquals("", StringUtil.replaceAll(null, "a", "b"));           // null source gives ""
assertEquals("a-b-c", StringUtil.replaceAll("a b c", ' ', '-'));
assertEquals("a\nb\nc\n\nd", StringUtil.normalizeLineBreaks("a\r\nb\rc\n\rd"));   // "\n\r" is two breaks
```

### Redacting secrets

`redacted(s)` keeps the first 5 characters (or `max` with the two-argument form) and appends `...REDACTED`, so a token can be logged without leaking it. A `null` value prints as `<null>`.

Sample: `doc_examples/util/StringsExamples.java` (`testRedacted`)

```java
assertEquals("sk-12...REDACTED", StringUtil.redacted("sk-1234567890"));
assertEquals("sk...REDACTED", StringUtil.redacted("sk-1234567890", 2));
assertEquals("<null>", StringUtil.redacted(null));
```

## StringFormat

`StringFormat.format(pattern, args...)` replaces `{n}` with `String.valueOf(args[n])`. It is what `BaseException`, `Console.log` and `TextBuilder` use for their messages. A second overload appends to an existing `StringBuilder`.

Sample: `doc_examples/util/StringsExamples.java` (`testStringFormat`)

```java
assertEquals("1 + 1 = 2", StringFormat.format("{0} + {0} = {1}", 1, 2));
assertEquals("Hello !", StringFormat.format("Hello {1}!", "a"));        // missing argument: empty
assertEquals("null", StringFormat.format("{0}", (Object) null));
assertEquals("{name} {} x", StringFormat.format("{name} {} {0}", "x")); // non-numeric braces are kept
assertEquals("", StringFormat.format(null));

StringBuilder b = new StringBuilder("> ");
StringFormat.format(b, "{0}/{1}", "a", "b");
assertEquals("> a/b", b.toString());
```

It is deliberately simpler than `java.text.MessageFormat`:

| | `StringFormat` | `MessageFormat` |
|---|---|---|
| Placeholder | `{0}`, `{12}`: digits only | `{0}`, `{0,number,#.##}`, ... |
| Missing argument | replaced by nothing | left as `{1}` |
| `null` argument | `null` | `null` |
| Single quote | literal text | starts a quoted section (`It's {0}` loses the quote and the placeholder) |
| Numbers and dates | `toString()` | formatted for the locale (`1,234,567`) |
| Anything else in braces (`{}`, `{name}`) | kept as is | `IllegalArgumentException` |

Sample: `doc_examples/util/StringsExamples.java` (`testStringFormatVersusMessageFormat`)

```java
// No quoting rules and no locale formatting
assertEquals("It's 1234567", StringFormat.format("It's {0}", 1234567));
assertEquals("Its {0}", new MessageFormat("It's {0}", Locale.US).format(new Object[] {1234567}));
assertEquals("1,234,567", new MessageFormat("{0}", Locale.US).format(new Object[] {1234567}));
assertEquals("Hello {1}!", new MessageFormat("Hello {1}!", Locale.US).format(new Object[] {"a"}));
assertThrows(IllegalArgumentException.class, () -> new MessageFormat("{name}", Locale.US));
```

A `{` that is not closed by `}` before the end of the pattern is literal text:

Sample: `doc_examples/util/StringsExamples.java` (`testStringFormatUnterminatedPlaceholder`)

```java
// A '{' without its closing '}' is literal text
assertEquals("a{0", StringFormat.format("a{0", "X"));
assertEquals("{1 x", StringFormat.format("{1 {0}", "x"));
```

## StringMatcher

`StringMatcher` is a cursor over a string: it holds the string, a current position (`getPtr()`) and an end (`getLength()`). It is the tokenizer behind Galta's small parsers, and it is handy for any ad-hoc syntax that does not deserve a grammar.

| Group | Methods | Moves the cursor |
|---|---|---|
| Position | `getPtr`, `getLength`, `getRemaining`, `isEmpty`, `set(...)` | `set` resets it |
| Look ahead | `startsWith(char / String / IntPredicate / Pattern)`, `startsWithSpace`, `startsWithInteger`, `startsWithNumber`, `lookupChar` | no |
| Match | `match(char / String / IntPredicate / Pattern)`, `matchAndSkipSpaces(...)`, `skipIf(char)` | only on success |
| Skip | `skip()`, `skip(n)`, `skipSpaces()` | yes, clamped to the end |
| Read | `readChar`, `upto(char)`, `upto(IntPredicate)`, `readRegExp(Pattern)` | yes |
| Parse | `readIdentifier`, `readInteger`, `readNumber`, `readQuotedString`, `readEscapedString(quote)` | yes, or throw |

Spaces are `' '`, `\t`, `\n` and `\r`. `lookupChar()` and `readChar()` return `-1` at the end instead of throwing. The `match` methods return `false` and leave the cursor where it was; the `read` methods throw a `UtilException` whose message shows the input and a `^^^` marker under the error.

A key/value list parser:

Sample: `doc_examples/util/StringsExamples.java` (`testStringMatcher`)

```java
StringMatcher m = new StringMatcher("name = 'O\\'Hara', size=42, ratio=-1.5e2");
StringBuilder b = new StringBuilder();
while (!m.isEmpty()) {
    String key = m.readIdentifier();
    m.skipSpaces();
    if (!m.matchAndSkipSpaces('=')) {
        throw new IllegalStateException("'=' expected at " + m.getPtr());
    }
    Object value = m.startsWith('\'') ? m.readQuotedString() : m.readNumber();
    b.append(key).append(':').append(value).append(' ');
    m.skipSpaces();
    m.matchAndSkipSpaces(',');
}
assertEquals("name:O'Hara size:42 ratio:-150 ", b.toString());
```

### Reading numbers

| Method | Accepts | Returns |
|---|---|---|
| `readInteger()` | optional `-`, then digits; no leading zero (`007` fails); stops at the first non-digit | `int`; throws when the value is outside the `int` range (`Integer.MIN_VALUE` is accepted) |
| `readNumber()` | JSON-like numbers: `-`, integer part or a bare `.5`, fraction, exponent | an `Integer` when the value is integral and fits, a `Double` otherwise |

`readNumber()` returns `150` (an `Integer`) for `1.5e2`, and `-0` comes back as the `Double` `-0.0`, so the sign of zero is kept.

Sample: `doc_examples/util/StringsExamples.java` (`testStringMatcherNumbers`)

```java
assertEquals(Integer.valueOf(150), new StringMatcher("1.5e2").readNumber());  // integral -> Integer
assertEquals(Double.valueOf(2.5), new StringMatcher("2.5").readNumber());
assertEquals(-12, new StringMatcher("-12px").readInteger());                 // stops at 'p'
assertThrows(UtilException.class, () -> new StringMatcher("007").readInteger()); // no leading zeros
assertThrows(UtilException.class, () -> new StringMatcher("010").readNumber());
assertThrows(UtilException.class, () -> new StringMatcher("99999999999").readInteger()); // overflow
assertEquals(Integer.MIN_VALUE, new StringMatcher("-2147483648").readInteger());         // MIN_VALUE fits
assertEquals(Double.valueOf(-0.0), new StringMatcher("-0").readNumber());                // negative zero kept
```

### Bounds

The `(string, start, end)` constructor restricts the matcher to a slice of the string, which avoids a `substring` when scanning part of a larger text. Nothing reads past the end.

Sample: `doc_examples/util/StringsExamples.java` (`testStringMatcherBounds`)

```java
// Scan only [4,9) of the string
StringMatcher m = new StringMatcher("key=value;rest", 4, 9);
assertEquals("value", m.upto(';'));
assertTrue(m.isEmpty());
assertEquals(-1, m.lookupChar());   // -1 at the end, never an exception
m.skip(100);                        // clamped to the end
assertEquals(9, m.getPtr());
```

### Strings and errors

`readQuotedString()` reads the opening quote itself (either `'` or `"`) and returns the unescaped content. Supported escapes are `\t \n \r \f \b \\ \/ \' \"`, `\uXXXX` and `\xXX`; a raw tab, newline, carriage return, form feed or backspace inside the string, an unknown escape or a missing closing quote throws.

Sample: `doc_examples/util/StringsExamples.java` (`testStringMatcherError`)

```java
UtilException e = assertThrows(UtilException.class, () -> new StringMatcher("'abc").readQuotedString());
assertTrue(e.getMessage().startsWith("Unterminated string\n'abc\n"));
```

`readIdentifier()` uses the Java identifier rules (`Character.isJavaIdentifierStart/Part`). Both it and the space test are `protected` hooks (`isIdentifierStart`, `isIdentifierPart`, `isSpace`) that a subclass can override for another syntax, and `_createException` lets a subclass throw its own exception type.

## TextBuilder

`TextBuilder` is an append-only text buffer that indents new lines. `incIndent()`/`decIndent()` change the level, and each level adds two spaces at the start of the next line written. `print`/`println`/`append` accept a `StringFormat` pattern with arguments, `\r` characters are dropped, and `getCurrentLine()` counts the lines written so far (starting at 1). It implements `CharSequence`.

Sample: `doc_examples/util/StringsExamples.java` (`testTextBuilder`)

```java
TextBuilder t = new TextBuilder();
t.println("class {0} {", "Point");
t.incIndent();
t.println("int x;").println("int y;");
t.decIndent();
t.print("}");
assertEquals("class Point {\n  int x;\n  int y;\n}", t.toString());
assertEquals(4, t.getCurrentLine());
```
