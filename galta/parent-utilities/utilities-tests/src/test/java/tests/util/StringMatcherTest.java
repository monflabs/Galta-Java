/*
 * Copyright (c) 2019-2026 Philippe Riand
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tests.util;

import static org.junit.Assert.assertThrows;

import java.util.regex.Pattern;

import org.monflabs.util.StringMatcher;

import tests.ProjectTestCase;

public class StringMatcherTest extends ProjectTestCase {
	
	public void testMatcher() throws Exception {
		StringMatcher m = new StringMatcher("a.bcd['xyz']");
		
		assertFalse(m.isEmpty());
		assertEquals(12, m.getLength());
		assertEquals(0, m.getPtr());
		assertEquals(12, m.getRemaining());
		
		assertFalse(m.startsWith('b'));
		assertTrue(m.startsWith('a'));
		assertFalse(m.match('b'));
		assertTrue(m.match('a'));
		assertEquals(11, m.getRemaining());
		
		assertTrue(m.startsWith('.'));
		assertTrue(m.match('.'));
		
		assertFalse(m.startsWith("bbb"));
		assertTrue(m.startsWith("bcd"));
		assertFalse(m.match("bbb"));
		assertTrue(m.match("bcd"));
		assertEquals(7, m.getRemaining());

		assertTrue(m.match('['));
		assertTrue(m.match('\''));
		assertEquals("xyz",m.upto('\''));
		assertTrue(m.match('\''));
		assertTrue(m.match(']'));

		assertTrue(m.isEmpty());
	}
	
	public void testRegExpMatcher() throws Exception {
		StringMatcher m = new StringMatcher("abc123xyz");
		
		assertTrue(m.startsWith(Pattern.compile("[a-z]+")));
		assertFalse(m.startsWith(Pattern.compile("[0-9]+")));
		
		assertFalse(m.match(Pattern.compile("[0-9]+")));
		assertEquals(0, m.getPtr());
		assertTrue(m.match(Pattern.compile("[a-z]+")));
		assertEquals(3, m.getPtr());
		
		assertFalse(m.match(Pattern.compile("[a-z]+")));
		assertEquals(3, m.getPtr());
		assertTrue(m.match(Pattern.compile("[0-9]+")));
		assertEquals(6, m.getPtr());
		
		assertFalse(m.match(Pattern.compile("[a-c]+")));
		assertFalse(m.match(Pattern.compile("[0-9]+")));
		assertTrue(m.match(Pattern.compile("[x-z]+")));
		assertEquals(9, m.getPtr());
	}

	public void testSkip() throws Exception {
		StringMatcher m = new StringMatcher("123456789");
		
		assertEquals(0, m.getPtr());
		m.skip(0);
		assertEquals(0, m.getPtr());

		m.skip();
		assertEquals(1, m.getPtr());

		m.skip(2);
		assertEquals(3, m.getPtr());

		m.skip(-1);
		assertEquals(3, m.getPtr());

		m.skip(1000);
		assertEquals(9, m.getPtr());
		assertTrue(m.isEmpty());
	}

	public void testSkipSpaces() throws Exception {
		StringMatcher m = new StringMatcher(" 1  23 ");
		
		assertEquals(0, m.getPtr());
		m.skipSpaces();
		assertTrue(m.match('1'));
		m.skipSpaces();
		assertTrue(m.match('2'));
		m.skipSpaces();
		assertTrue(m.match('3'));
		m.skipSpaces();
		assertTrue(m.isEmpty());
	}

	public void testUpto() throws Exception {
		StringMatcher m = new StringMatcher("'''a''123'");

		assertTrue(m.match('\''));
		assertEquals("",m.upto('\''));
		assertTrue(m.match('\''));

		assertTrue(m.match('\''));
		assertEquals("a",m.upto('\''));
		assertTrue(m.match('\''));

		assertTrue(m.match('\''));
		assertEquals("123",m.upto('\''));
		assertTrue(m.match('\''));

		StringMatcher m2 = new StringMatcher("12345a");
		assertEquals("12345",m2.upto((c) -> Character.isAlphabetic(c)));
	}

	public void testReadChar() throws Exception {
		StringMatcher m = new StringMatcher("abc");

		assertEquals((int)'a',m.lookupChar());
		assertEquals((int)'a',m.readChar());
		assertEquals((int)'b',m.lookupChar());
		assertEquals((int)'b',m.readChar());
		assertEquals((int)'c',m.lookupChar());
		assertEquals((int)'c',m.readChar());
		assertTrue(m.lookupChar()<0);
		assertTrue(m.readChar()<0);
		
	}
	public void testReadRegExp() throws Exception {
		StringMatcher m = new StringMatcher("a123xyz");
		
		assertEquals("a", m.readRegExp(Pattern.compile("[a-z]+")));
		assertEquals(1, m.getPtr());
		assertEquals("123", m.readRegExp(Pattern.compile("[0-9]+")));
		assertEquals(4, m.getPtr());
		assertEquals("xyz", m.readRegExp(Pattern.compile("[a-z]+")));
		assertEquals(7, m.getPtr());
	}

	public void testEscapedString() throws Exception {
		{
			StringMatcher m = new StringMatcher("''");
			assertTrue(m.match('\''));
			assertEquals("",m.readEscapedString('\''));
			assertTrue(m.match('\''));
		}
		{
			StringMatcher m = new StringMatcher("\"abc\"");
			assertTrue(m.match('\"'));
			assertEquals("abc",m.readEscapedString('\"'));
			assertTrue(m.match('\"'));
		}
		{
			StringMatcher m = new StringMatcher("'abc'");
			assertTrue(m.match('\''));
			assertEquals("abc",m.readEscapedString('\''));
			assertTrue(m.match('\''));
		}
		{
			StringMatcher m = new StringMatcher("'a\\nb'");
			assertTrue(m.match('\''));
			assertEquals("a\nb",m.readEscapedString('\''));
			assertTrue(m.match('\''));
		}
		{
			StringMatcher m = new StringMatcher("'a\\xABb'");
			assertTrue(m.match('\''));
			assertEquals("a\u00ABb",m.readEscapedString('\''));
			assertTrue(m.match('\''));
		}
		{
			StringMatcher m = new StringMatcher("'a\\u1cdcb'");
			assertTrue(m.match('\''));
			assertEquals("a\u1cdcb",m.readEscapedString('\''));
			assertTrue(m.match('\''));
		}
		
		
		{
			// No line return within a string!
			StringMatcher m = new StringMatcher("'a\nb'");
			assertTrue(m.match('\''));
			assertThrows(Exception.class, () -> m.readEscapedString('\''));
		}
		{
			// invalid String
			StringMatcher m = new StringMatcher("'a\nb");
			assertTrue(m.match('\''));
			assertThrows(Exception.class, () -> m.readEscapedString('\''));
		}
		{
			// invalid escape
			StringMatcher m1 = new StringMatcher("'a\\ob'");
			assertTrue(m1.match('\''));
			assertThrows(Exception.class, () -> m1.readEscapedString('\''));
			
			StringMatcher m2 = new StringMatcher("'a\\xb'");
			assertTrue(m2.match('\''));
			assertThrows(Exception.class, () -> m2.readEscapedString('\''));
			
			StringMatcher m3 = new StringMatcher("'a\\ub'");
			assertTrue(m3.match('\''));
			assertThrows(Exception.class, () -> m3.readEscapedString('\''));
		}
	}
	
	public void testIdentifier() throws Exception {
		{
			StringMatcher m = new StringMatcher("a");
			assertEquals("a",m.readIdentifier());
			assertTrue(m.isEmpty());
		}
		{
			StringMatcher m = new StringMatcher("ab");
			assertEquals("ab",m.readIdentifier());
			assertTrue(m.isEmpty());
		}
		{
			StringMatcher m = new StringMatcher("ab.cd");
			assertEquals("ab",m.readIdentifier());
			assertTrue(m.match('.'));
			assertEquals("cd",m.readIdentifier());
		}
		{
			StringMatcher m = new StringMatcher("ab@");
			assertEquals("ab",m.readIdentifier());
			assertTrue(m.match('@'));
			assertTrue(m.isEmpty());
		}

		{
			// invalid identifier
			StringMatcher m1 = new StringMatcher("");
			assertThrows(Exception.class, () -> m1.readIdentifier());
			
			StringMatcher m2 = new StringMatcher("1ab");
			assertThrows(Exception.class, () -> m2.readIdentifier());
			
			StringMatcher m3 = new StringMatcher("&a'");
			assertThrows(Exception.class, () -> m3.readIdentifier());
		}
	}
	
	public void testInteger() throws Exception {
		{
			StringMatcher m = new StringMatcher("1");
			assertEquals(1,m.readInteger());
			assertTrue(m.isEmpty());
		}
		{
			StringMatcher m = new StringMatcher("-1");
			assertEquals(-1,m.readInteger());
			assertTrue(m.isEmpty());
		}
		{
			StringMatcher m = new StringMatcher("-0");
			assertEquals(0,m.readInteger());
			assertTrue(m.isEmpty());
		}
		{
			StringMatcher m = new StringMatcher("101");
			assertEquals(101,m.readInteger());
			assertTrue(m.isEmpty());
		}
		{
			StringMatcher m = new StringMatcher("-101");
			assertEquals(-101,m.readInteger());
			assertTrue(m.isEmpty());
		}
		
		{
			StringMatcher m = new StringMatcher("1a");
			assertEquals(1,m.readInteger());
			assertTrue(m.match('a'));
			assertTrue(m.isEmpty());
		}

		{
			// invalid integer
			StringMatcher m1 = new StringMatcher("");
			assertThrows(Exception.class, () -> m1.readInteger());

			StringMatcher m2 = new StringMatcher("ab");
			assertThrows(Exception.class, () -> m2.readInteger());

			StringMatcher m3 = new StringMatcher("-");
			assertThrows(Exception.class, () -> m3.readInteger());

			StringMatcher m4 = new StringMatcher("002");
			assertThrows(Exception.class, () -> m4.readInteger());
		}
	}
	
	public void testNumber() throws Exception {
		{
			StringMatcher m = new StringMatcher("1");
			assertEquals(1,m.readNumber());
			assertTrue(m.isEmpty());
		}
		{
			StringMatcher m = new StringMatcher("1.45");
			assertEquals(1.45,m.readNumber());
			assertTrue(m.isEmpty());
		}
		{
			StringMatcher m = new StringMatcher("-3.0");
			assertEquals(-3,m.readNumber());
			assertTrue(m.isEmpty());
		}
		{
			StringMatcher m = new StringMatcher(".25");
			assertEquals(0.25,m.readNumber());
			assertTrue(m.isEmpty());
		}
		{
			StringMatcher m = new StringMatcher("0.25");
			assertEquals(0.25,m.readNumber());
			assertTrue(m.isEmpty());
		}
		{
			StringMatcher m = new StringMatcher("0.25e4");
			assertEquals(2500,m.readNumber());
			assertTrue(m.isEmpty());
		}

		{
			// invalid number
			StringMatcher m1 = new StringMatcher("");
			assertThrows(Exception.class, () -> m1.readNumber());
			
			StringMatcher m2 = new StringMatcher("ab");
			assertThrows(Exception.class, () -> m2.readNumber());

			StringMatcher m3 = new StringMatcher("-");
			assertThrows(Exception.class, () -> m3.readNumber());

			StringMatcher m4 = new StringMatcher("010");
			assertThrows(Exception.class, () -> m4.readNumber());

			StringMatcher m5 = new StringMatcher("01.56");
			assertThrows(Exception.class, () -> m5.readNumber());
		}
	}

	public void testNumberEdgeCases() throws Exception {
		assertEquals(0, new StringMatcher("0").readNumber());
		// Negative zero is preserved as a double (it used to collapse to Integer 0)
		assertEquals(-0.0, new StringMatcher("-0").readNumber());
		assertEquals(-0.0, new StringMatcher("-0.0").readNumber());
		assertEquals(10, new StringMatcher("10").readNumber());
		assertEquals(1e-5, new StringMatcher("1e-5").readNumber());
		assertEquals(1000, new StringMatcher("1E+3").readNumber());
		assertEquals(0.5, new StringMatcher("0.5").readNumber());
		assertTrue(new StringMatcher("0").startsWithNumber());
		assertTrue(new StringMatcher("-0.5e3").startsWithNumber());
		assertFalse(new StringMatcher("abc").startsWithNumber());
		assertThrows(Exception.class, () -> new StringMatcher("abc").readNumber());
	}

	public void testUnicodeEscapeTruncated() throws Exception {
		assertEquals("A", new StringMatcher("\\u0041'").readEscapedString('\''));
		// A truncated escape is a parse error, not a StringIndexOutOfBoundsException
		assertThrows(org.monflabs.util.BaseException.class, () -> new StringMatcher("\\u12").readEscapedString('\''));
		assertThrows(org.monflabs.util.BaseException.class, () -> new StringMatcher("\\u").readEscapedString('\''));
	}

	public void testIntegerOverflow() throws Exception {
		assertEquals(2147483647, new StringMatcher("2147483647").readInteger());
		assertThrows(org.monflabs.util.BaseException.class, () -> new StringMatcher("2147483648").readInteger());
		assertThrows(org.monflabs.util.BaseException.class, () -> new StringMatcher("12345678901").readInteger());
	}

	public void testEndBound() throws Exception {
		// The end bound given to the constructor applies to string and regexp matching too
		StringMatcher m = new StringMatcher("abcdef", 0, 3);
		assertTrue(m.startsWith("abc"));
		assertFalse(m.startsWith("abcd"));
		assertFalse(m.match("abcd"));
		assertEquals("abc", m.readRegExp(java.util.regex.Pattern.compile("[a-z]+")));
		assertTrue(m.isEmpty());
	}

	public void testIntegerBounds() throws Exception {
		// The magnitude of Integer.MIN_VALUE is one more than Integer.MAX_VALUE: it used to be
		// reported as an overflow
		assertEquals(Integer.MIN_VALUE, new StringMatcher("-2147483648").readInteger());
		assertThrows(org.monflabs.util.BaseException.class, () -> new StringMatcher("-2147483649").readInteger());
		assertEquals(-1, new StringMatcher("-1").readInteger());
	}

	public void testNegativeZeroNumber() throws Exception {
		Number n = new StringMatcher("-0").readNumber();
		assertTrue(n instanceof Double);
		assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(n.doubleValue()));
		n = new StringMatcher("-0e5").readNumber();
		assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(n.doubleValue()));
		// A positive zero stays an Integer
		assertEquals(Integer.valueOf(0), new StringMatcher("0.0").readNumber());
	}
}
