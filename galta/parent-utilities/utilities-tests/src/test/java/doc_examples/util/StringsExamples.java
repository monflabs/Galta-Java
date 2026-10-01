package doc_examples.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.text.MessageFormat;
import java.util.Locale;

import org.monflabs.util.StringFormat;
import org.monflabs.util.StringMatcher;
import org.monflabs.util.StringUtil;
import org.monflabs.util.TextBuilder;
import org.monflabs.util.UtilException;

import tests.ProjectTestCase;

/**
 * Samples for docs/Utilities/Strings.md
 */
public class StringsExamples extends ProjectTestCase {

	public void testSplitAndJoin() throws Exception {
		assertArrayEquals(new String[] {"a", "b", "", "c"}, StringUtil.splitString("a,b,,c", ','));
		assertArrayEquals(new String[] {"a", "b"}, StringUtil.splitString(" a , b ", ',', true));   // trim each part
		assertArrayEquals(new String[] {""}, StringUtil.splitString("", ','));
		assertEquals(0, StringUtil.splitString(null, ',').length);

		Object[] values = {"a", null, 42, new int[] {1, 2}};
		assertEquals("a,,42,[1,2]", StringUtil.join(values, ','));        // null -> "", arrays expanded
		assertEquals("a,42,[1,2]", StringUtil.join(values, ',', true));   // skip nulls
		assertEquals("42,[1,2]", StringUtil.join(values, ',', 2, 5));     // from index 2, length clamped
	}

	public void testNullSafeHelpers() throws Exception {
		assertTrue(StringUtil.isEmpty(null));
		assertTrue(StringUtil.equals(null, ""));             // null and "" are both "empty"
		assertEquals(-1, StringUtil.compareTo(null, "a"));    // empty sorts first
		assertNull(StringUtil.trim(null));
		assertEquals("x  ", StringUtil.trimLeft("  x  "));
		assertEquals("", StringUtil.nonNull(null));
		assertTrue(StringUtil.containsIgnoreCase("Content-Type", "TYPE"));
	}

	public void testPadTruncateCase() throws Exception {
		assertEquals("007", StringUtil.padLeft("7", 3, '0'));
		assertEquals("ab..", StringUtil.padRight("ab", 4, '.'));
		assertEquals("12345", StringUtil.padLeft("12345", 3, '0'));   // never truncates
		assertEquals("Hello...", StringUtil.truncate("Hello world", 8));
		assertEquals("00ff", StringUtil.toUnsignedHex4(255));

		assertEquals("backgroundColor", StringUtil.toCamelCase("background-color"));
		assertEquals("innerHTML", StringUtil.toCamelCase("innerHTML"));     // other characters are kept
		assertEquals("background-color", StringUtil.toKebabCase("backgroundColor"));
		assertEquals("url-value", StringUtil.toKebabCase("URLValue"));     // an acronym stays one word
		assertEquals("Hello", StringUtil.capitalizeFirstCharacter("hello"));
	}

	public void testReplace() throws Exception {
		assertEquals("a/b/c", StringUtil.replaceAll("a.b.c", ".", "/"));   // literal, not a regex
		assertEquals("a/b.c", StringUtil.replaceFirst("a.b.c", ".", "/"));
		assertEquals("abc", StringUtil.replaceAll("abc", "", "x"));        // empty search: source unchanged
		assertEquals("ac", StringUtil.replaceAll("abc", "b", null));       // null replacement removes
		assertEquals("", StringUtil.replaceAll(null, "a", "b"));           // null source gives ""
		assertEquals("a-b-c", StringUtil.replaceAll("a b c", ' ', '-'));
		assertEquals("a\nb\nc\n\nd", StringUtil.normalizeLineBreaks("a\r\nb\rc\n\rd"));   // "\n\r" is two breaks
	}

	public void testRedacted() throws Exception {
		assertEquals("sk-...REDACTED", StringUtil.redacted("sk-1234567890"));     // at most a quarter
		assertEquals("sk...REDACTED", StringUtil.redacted("sk-1234567890", 2));
		assertEquals("...REDACTED", StringUtil.redacted("1234"));               // nothing of a short value
		assertEquals("<null>", StringUtil.redacted(null));
	}

	public void testStringFormat() throws Exception {
		assertEquals("1 + 1 = 2", StringFormat.format("{0} + {0} = {1}", 1, 2));
		assertEquals("Hello {1}!", StringFormat.format("Hello {1}!", "a"));     // missing argument: kept
		assertEquals("null", StringFormat.format("{0}", (Object) null));
		assertEquals("{name} {} x", StringFormat.format("{name} {} {0}", "x")); // non-numeric braces are kept
		assertEquals("", StringFormat.format(null));

		StringBuilder b = new StringBuilder("> ");
		StringFormat.format(b, "{0}/{1}", "a", "b");
		assertEquals("> a/b", b.toString());
	}

	public void testStringFormatVersusMessageFormat() throws Exception {
		// No quoting rules and no locale formatting
		assertEquals("It's 1234567", StringFormat.format("It's {0}", 1234567));
		assertEquals("Its {0}", new MessageFormat("It's {0}", Locale.US).format(new Object[] {1234567}));
		assertEquals("1,234,567", new MessageFormat("{0}", Locale.US).format(new Object[] {1234567}));
		assertEquals("Hello {1}!", new MessageFormat("Hello {1}!", Locale.US).format(new Object[] {"a"}));
		assertThrows(IllegalArgumentException.class, () -> new MessageFormat("{name}", Locale.US));
	}

	public void testStringFormatUnterminatedPlaceholder() throws Exception {
		// A '{' without its closing '}' is literal text
		assertEquals("a{0", StringFormat.format("a{0", "X"));
		assertEquals("{1 x", StringFormat.format("{1 {0}", "x"));
	}

	public void testStringMatcher() throws Exception {
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
	}

	public void testStringMatcherNumbers() throws Exception {
		assertEquals(Integer.valueOf(150), new StringMatcher("1.5e2").readNumber());  // integral -> Integer
		assertEquals(Double.valueOf(2.5), new StringMatcher("2.5").readNumber());
		assertEquals(-12, new StringMatcher("-12px").readInteger());                 // stops at 'p'
		assertThrows(UtilException.class, () -> new StringMatcher("007").readInteger()); // no leading zeros
		assertThrows(UtilException.class, () -> new StringMatcher("010").readNumber());
		assertThrows(UtilException.class, () -> new StringMatcher("99999999999").readInteger()); // overflow
		assertEquals(Integer.MIN_VALUE, new StringMatcher("-2147483648").readInteger());         // MIN_VALUE fits
		assertEquals(Double.valueOf(-0.0), new StringMatcher("-0").readNumber());                // negative zero kept
	}

	public void testStringMatcherBounds() throws Exception {
		// Scan only [4,9) of the string
		StringMatcher m = new StringMatcher("key=value;rest", 4, 9);
		assertEquals("value", m.upto(';'));
		assertTrue(m.isEmpty());
		assertEquals(-1, m.lookupChar());   // -1 at the end, never an exception
		m.skip(100);                        // clamped to the end
		assertEquals(9, m.getPtr());
	}

	public void testStringMatcherError() throws Exception {
		UtilException e = assertThrows(UtilException.class, () -> new StringMatcher("'abc").readQuotedString());
		assertTrue(e.getMessage().startsWith("Unterminated string\n'abc\n"));
	}

	public void testTextBuilder() throws Exception {
		TextBuilder t = new TextBuilder();
		t.println("class {0} {", "Point");
		t.incIndent();
		t.println("int x;").println("int y;");
		t.decIndent();
		t.print("}");
		assertEquals("class Point {\n  int x;\n  int y;\n}", t.toString());
		assertEquals(4, t.getCurrentLine());
	}
}
