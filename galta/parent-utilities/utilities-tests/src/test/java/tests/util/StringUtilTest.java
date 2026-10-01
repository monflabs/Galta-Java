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

import org.monflabs.util.StringUtil;

import tests.ProjectTestCase;

public class StringUtilTest extends ProjectTestCase {
	
	public void testContainsIgnoreCase() throws Exception {
		assertFalse( StringUtil.containsIgnoreCase(null, null) );
		assertFalse( StringUtil.containsIgnoreCase("", null) );
		assertFalse( StringUtil.containsIgnoreCase(null, "") );

		assertTrue ( StringUtil.containsIgnoreCase("", "") );
		assertTrue ( StringUtil.containsIgnoreCase("ABC", "b") );
		assertFalse( StringUtil.containsIgnoreCase("ABC", "d") );
	}
	
	public void testIndexIgnoreCase() throws Exception {
		assertEquals( -1, StringUtil.indexIgnoreCase(null, null) );
		assertEquals( -1, StringUtil.indexIgnoreCase("", null) );
		assertEquals( -1, StringUtil.indexIgnoreCase(null, "") );
		
		assertEquals( 0, StringUtil.indexIgnoreCase("", "") );
		
		assertEquals( 0, StringUtil.indexIgnoreCase("ABC", "") );
		assertEquals( 0, StringUtil.indexIgnoreCase("ABC", "a") );
		assertEquals( 1, StringUtil.indexIgnoreCase("ABC", "b") );
		assertEquals( 2, StringUtil.indexIgnoreCase("ABC", "c") );
		assertEquals( -1, StringUtil.indexIgnoreCase("ABC", "d") );

		assertEquals( 1, StringUtil.indexIgnoreCase("ABC", "BC") );
		assertEquals( -1, StringUtil.indexIgnoreCase("ABC", "AC") );
	}
	
	public void testJoin() throws Exception {
		assertEquals( null, StringUtil.join(null, ',') );
		assertEquals( "", StringUtil.join(new String[] {}, ',') );
		assertEquals( "1", StringUtil.join(new String[] {"1"}, ',') );
		assertEquals( "1,2", StringUtil.join(new String[] {"1","2"}, ',') );
		assertEquals( "1,2,3", StringUtil.join(new String[] {"1","2","3"}, ',') );
		assertEquals( "1,,3", StringUtil.join(new String[] {"1",null,"3"}, ',') );

		assertEquals( null, StringUtil.join(null, ',', 0, 1) );
		assertEquals( "", StringUtil.join(new String[] {"1","2","3","4","5"}, ',', -1, 2) );
		assertEquals( "1", StringUtil.join(new String[] {"1","2","3","4","5"}, ',', 0, 1) );
		assertEquals( "1,2", StringUtil.join(new String[] {"1","2","3","4","5"}, ',', 0, 2) );
		assertEquals( "2,3", StringUtil.join(new String[] {"1","2","3","4","5"}, ',', 1, 2) );
		assertEquals( "4,5", StringUtil.join(new String[] {"1","2","3","4","5"}, ',', 3, 8) );
		
		assertEquals( "", StringUtil.join(new String[] {null}, ',', true) );
		assertEquals( "", StringUtil.join(new String[] {null,null}, ',', true) );
		assertEquals( "1", StringUtil.join(new String[] {null,"1",null}, ',', true) );
		assertEquals( "1,2", StringUtil.join(new String[] {null,"1",null,"2",null}, ',', true) );
		// Separators are kept after empty and null elements
		assertEquals( ",a", StringUtil.join(new String[] {"","a"}, ',') );
		assertEquals( ",1", StringUtil.join(new String[] {null,"1"}, ',', false) );
		assertEquals( "a,,b", StringUtil.join(new String[] {"a","","b"}, ',') );
	}
	
	public void testTrim() {
		assertEquals( null, StringUtil.trim(null) );
		assertEquals( "", StringUtil.trim("") );
		assertEquals( "", StringUtil.trim(" ") );
		assertEquals( "A", StringUtil.trim("A") );
		assertEquals( "A", StringUtil.trim(" A ") );
		assertEquals( "A", StringUtil.trim("A ") );
		assertEquals( "A", StringUtil.trim(" A") );
		// The same white spaces as trimLeft()/trimRight(): String.strip(), not String.trim()
		assertEquals( "A", StringUtil.trim("\u2003A\u2003") );
		assertEquals( "\u0001A", StringUtil.trim("\u0001A") );
	}
	
	public void testTrimLeft() {
		assertEquals( null, StringUtil.trimLeft(null) );
		assertEquals( "", StringUtil.trimLeft("") );
		assertEquals( "", StringUtil.trimLeft(" ") );
		assertEquals( "A", StringUtil.trimLeft("A") );
		assertEquals( "A ", StringUtil.trimLeft(" A ") );
		assertEquals( "A ", StringUtil.trimLeft("A ") );
		assertEquals( "A", StringUtil.trimLeft(" A") );
		assertEquals( "A\u2003", StringUtil.trimLeft("\u2003A\u2003") );
		assertEquals( null, StringUtil.trimRight(null) );
		assertEquals( " A", StringUtil.trimRight(" A \t\n") );
		assertEquals( "", StringUtil.trimRight("  ") );
	}
	
	public void testReplaceFirst() {
		assertEquals( "", StringUtil.replaceFirst(null,null,null) );
		assertEquals( "", StringUtil.replaceFirst("",null,null) );
		assertEquals( "Abcabca", StringUtil.replaceFirst("abcabca","a","A") );
		assertEquals( "aBcabca", StringUtil.replaceFirst("abcabca","b","B") );
		assertEquals( "aaBBccbbbdbb", StringUtil.replaceFirst("aabbbccbbbdbb","bbb","BB") );
	}
	
	public void testReplaceAll() {
		assertEquals( "", StringUtil.replaceAll(null,null,null) );
		assertEquals( "", StringUtil.replaceAll("",null,null) );
		assertEquals( "AbcAbcA", StringUtil.replaceAll("abcabca","a","A") );
		assertEquals( "aBcaBca", StringUtil.replaceAll("abcabca","b","B") );
		assertEquals( "aaBBccBBdBB", StringUtil.replaceAll("aabbbccBBdbbb","bbb","BB") );
		// An empty or null search string matches nothing (it used to loop forever)
		assertEquals( "abc", StringUtil.replaceAll("abc","","x") );
		assertEquals( "abc", StringUtil.replaceAll("abc",null,"x") );
		assertEquals( "abc", StringUtil.replaceFirst("abc","","x") );
	}

	public void testReplaceFirstChar() {
		assertEquals( "", StringUtil.replaceFirst(null,(char)0,(char)0) );
		assertEquals( "", StringUtil.replaceFirst("",(char)0,(char)0) );
		assertEquals( "Abcabca", StringUtil.replaceFirst("abcabca",'a','A') );
		assertEquals( "aBcabca", StringUtil.replaceFirst("abcabca",'b','B') );
	}
	
	public void testReplaceAllChar() {
		assertEquals( "", StringUtil.replaceAll(null,(char)0,(char)0) );
		assertEquals( "", StringUtil.replaceAll("",(char)0,(char)0) );
		assertEquals( "AbcAbcA", StringUtil.replaceAll("abcabca",'a','A') );
		assertEquals( "aBcaBca", StringUtil.replaceAll("abcabca",'b','B') );
	}
	
	
	public void testPadLeft() {
		assertEquals( "", StringUtil.padLeft(null,0,' ') );
		assertEquals( "  ", StringUtil.padLeft(null,2,' ') );
		assertEquals( "", StringUtil.padLeft("",0,' ') );
		assertEquals( "  ", StringUtil.padLeft("",2,' ') );
		assertEquals( " 1", StringUtil.padLeft("1",2,' ') );
		assertEquals( "123", StringUtil.padLeft("123",2,' ') );
	}
	public void testPadRight() {
		assertEquals( "", StringUtil.padRight(null,0,' ') );
		assertEquals( "  ", StringUtil.padRight(null,2,' ') );
		assertEquals( "", StringUtil.padRight("",0,' ') );
		assertEquals( "  ", StringUtil.padRight("",2,' ') );
		assertEquals( "1 ", StringUtil.padRight("1",2,' ') );
		assertEquals( "123", StringUtil.padRight("123",2,' ') );
	}
	
	public void testTruncate() {
		assertEquals( "", StringUtil.truncate("",4) );
		assertEquals( "1", StringUtil.truncate("1",4) );
		assertEquals( "12", StringUtil.truncate("12",4) );
		assertEquals( "1234", StringUtil.truncate("1234",4) );
		assertEquals( "1...", StringUtil.truncate("12345",4) );
		assertEquals( "12...", StringUtil.truncate("123456",5) );
	}	
	
	public void testCamelCase() throws Exception {
		assertEquals("", StringUtil.toCamelCase(""));
		assertEquals("a", StringUtil.toCamelCase("a"));
		assertEquals("ab", StringUtil.toCamelCase("ab"));
		assertEquals("aB", StringUtil.toCamelCase("a-b"));
		assertEquals("abCd", StringUtil.toCamelCase("ab-cd"));
		// Only the character after a dash changes: camel case is kept
		assertEquals("ABCD", StringUtil.toCamelCase("AB-CD"));
		assertEquals("innerHTML", StringUtil.toCamelCase("innerHTML"));
		assertEquals("innerHtml", StringUtil.toCamelCase("inner-html"));
		assertNull(StringUtil.toCamelCase(null));
	}
	
	public void testKebabCase() throws Exception {
		assertEquals("", StringUtil.toKebabCase(""));
		assertEquals("a", StringUtil.toKebabCase("a"));
		assertEquals("a", StringUtil.toKebabCase("A"));
		assertEquals("a-b", StringUtil.toKebabCase("aB"));
		assertEquals("ab", StringUtil.toKebabCase("ab"));
		assertEquals("ab", StringUtil.toKebabCase("Ab"));
		assertEquals("a-bcd-ef", StringUtil.toKebabCase("aBcdEf"));
		assertEquals("ab-cd", StringUtil.toKebabCase("abCd"));
	}
	

	public void testRedacted() {
		assertEquals( "<null>", StringUtil.redacted(null) );
		// At most a quarter of the value, nothing for a short one: the first 5 characters
		// used to be shown whatever the length (all of "abc")
		assertEquals( "...REDACTED", StringUtil.redacted("abc") );
		assertEquals( "...REDACTED", StringUtil.redacted("abcdefg") );
		assertEquals( "ab...REDACTED", StringUtil.redacted("abcdefgh") );
		assertEquals( "abc...REDACTED", StringUtil.redacted("abcdefghijkl", 5) );
		assertEquals( "abcde...REDACTED", StringUtil.redacted("abcdefghijklmnopqrstuvwxyz", 5) );
		assertEquals( "ab...REDACTED", StringUtil.redacted("abcdefghijklmnopqrstuvwxyz", 2) );
		// A surrogate pair is not split
		assertEquals( "a...REDACTED", StringUtil.redacted("a\uD83D\uDE00cdefgh") );
		assertEquals( "...REDACTED", StringUtil.redacted("") );
	}

	public void testSplitLongString() {
		// Was recursive, one frame per separator
		StringBuilder b = new StringBuilder();
		for(int i=0; i<200_000; i++) {
			if(i>0) b.append(',');
			b.append(i);
		}
		String[] parts = StringUtil.splitString(b.toString(), ',');
		assertEquals(200_000, parts.length);
		assertEquals("0", parts[0]);
		assertEquals("199999", parts[199_999]);
		assertEquals(3, StringUtil.splitString("a, b ,c", ',', true).length);
		assertEquals("b", StringUtil.splitString("a, b ,c", ',', true)[1]);
		assertEquals(2, StringUtil.splitString("a,", ',').length);
		assertEquals("", StringUtil.splitString("a,", ',')[1]);
	}

	public void testNormalizeLineBreaks() {
		assertEquals("a\nb", StringUtil.normalizeLineBreaks("a\r\nb"));
		assertEquals("a\nb", StringUtil.normalizeLineBreaks("a\rb"));
		// LF CR is two line breaks (it used to be merged into one)
		assertEquals("a\n\nb", StringUtil.normalizeLineBreaks("a\n\rb"));
		assertEquals("a\n\n\nb", StringUtil.normalizeLineBreaks("a\r\n\r\rb"));
		assertEquals("a\nb", StringUtil.normalizeLineBreaks("a\nb"));
		assertNull(StringUtil.normalizeLineBreaks(null));
	}

	public void testJoinHugeLength() {
		String[] a = {"a","b","c"};
		// index+length used to overflow and return ""
		assertEquals("b,c", StringUtil.join(a, ',', 1, Integer.MAX_VALUE));
		assertEquals("a,b,c", StringUtil.join(a, ',', 0, Integer.MAX_VALUE));
		assertEquals("", StringUtil.join(a, ',', 3, 1));
		assertEquals("", StringUtil.join(a, ',', -1, 1));
	}

	public void testTruncateSmallMax() {
		// No room for the ellipsis: the string is cut (it used to be returned whole)
		assertEquals("123", StringUtil.truncate("123456", 3));
		assertEquals("1", StringUtil.truncate("123456", 1));
		assertEquals("", StringUtil.truncate("123456", 0));
		assertEquals("", StringUtil.truncate("123456", -1));
		assertEquals("12", StringUtil.truncate("12", 3));
		assertNull(StringUtil.truncate(null, 3));
		// A surrogate pair is not split
		assertEquals("a...", StringUtil.truncate("a\uD83D\uDE00cdefgh", 5));
		assertEquals("a", StringUtil.truncate("a\uD83D\uDE00", 2));
		assertEquals("a\uD83D\uDE00...", StringUtil.truncate("a\uD83D\uDE00cdefgh", 6));
	}

	public void testCapitalizeSurrogatePair() {
		// U+10428 DESERET SMALL LETTER LONG I -> U+10400
		String small = new String(Character.toChars(0x10428));
		String capital = new String(Character.toChars(0x10400));
		assertEquals(capital+"x", StringUtil.capitalizeFirstCharacter(small+"x"));
		assertEquals("Abc", StringUtil.capitalizeFirstCharacter("abc"));
		assertEquals("Abc", StringUtil.capitalizeFirstCharacter("Abc"));
		assertEquals("1a", StringUtil.capitalizeFirstCharacter("1a"));
		assertEquals("", StringUtil.capitalizeFirstCharacter(""));
		assertNull(StringUtil.capitalizeFirstCharacter(null));
	}

	public void testKebabCaseAcronyms() {
		// Acronyms used to come out one letter at a time: h-t-m-l-parser
		assertEquals("html-parser", StringUtil.toKebabCase("HTMLParser"));
		assertEquals("url-value", StringUtil.toKebabCase("URLValue"));
		assertEquals("my-url", StringUtil.toKebabCase("myURL"));
		assertEquals("ab", StringUtil.toKebabCase("AB"));
		assertEquals("get-http-response", StringUtil.toKebabCase("getHTTPResponse"));
		assertNull(StringUtil.toKebabCase(null));
	}

	public void testNullEmptySemantics() {
		// null and "" are the same for these helpers
		assertTrue(StringUtil.equals(null, ""));
		assertTrue(StringUtil.equals(null, null));
		assertFalse(StringUtil.equals(null, "a"));
		assertTrue(StringUtil.equalsIgnoreCase("", null));
		assertTrue(StringUtil.equalsIgnoreCase("aB", "Ab"));
		assertEquals(0, StringUtil.compareTo(null, ""));
		assertTrue(StringUtil.compareTo(null, "a")<0);
		assertTrue(StringUtil.compareTo("a", null)>0);
		assertTrue(StringUtil.compareTo("a", "b")<0);
		assertEquals(0, StringUtil.compareToIgnoreCase("ABC", "abc"));
		assertTrue(StringUtil.compareToIgnoreCase("", "a")<0);
	}

	public void testJdkBackedHelpersKeepTheirGuards() throws Exception {
		// Now backed by String.replace()/repeat()/stripLeading(): same results, same guards
		assertEquals("", org.monflabs.util.StringUtil.replaceAll(null, "a", "b"));
		assertEquals("abc", org.monflabs.util.StringUtil.replaceAll("abc", "", "x"));
		assertEquals("xbcxbc", org.monflabs.util.StringUtil.replaceAll("abcabc", "a", "x"));
		assertEquals("bcbc", org.monflabs.util.StringUtil.replaceAll("abcabc", "a", null));
		assertEquals("a.b", org.monflabs.util.StringUtil.replaceAll("a*b", "*", "."));   // literal, not a regex
		assertEquals("", org.monflabs.util.StringUtil.replaceAll(null, 'a', 'b'));
		assertEquals("bbc", org.monflabs.util.StringUtil.replaceAll("abc", 'a', 'b').replace('a', 'b'));
		assertEquals("00042", org.monflabs.util.StringUtil.padLeft("42", 5, '0'));
		assertEquals("42...", org.monflabs.util.StringUtil.padRight("42", 5, '.'));
		assertEquals("---", org.monflabs.util.StringUtil.padLeft(null, 3, '-'));
		assertEquals("toolong", org.monflabs.util.StringUtil.padRight("toolong", 3, '-'));
		assertEquals("x \t", org.monflabs.util.StringUtil.trimLeft(" \t\n\u2003x \t"));
		assertNull(org.monflabs.util.StringUtil.trimLeft(null));
		java.io.File f = java.io.File.createTempFile("fileutil", ".txt");
		try {
			// Malformed bytes are replaced, as with a Reader (Files.readString() would throw)
			java.nio.file.Files.write(f.toPath(), new byte[] {'a', (byte)0xC3, 'b'});
			assertEquals("a\ufffdb", org.monflabs.util.FileUtil.readContent(f));
			org.monflabs.util.FileUtil.setContent(f, "\u00e9t\u00e9");
			assertEquals("\u00e9t\u00e9", org.monflabs.util.FileUtil.readContent(f));
			java.io.File g = java.io.File.createTempFile("fileutil", ".txt");
			try {
				org.monflabs.util.FileUtil.copy(f, g);
				assertEquals("\u00e9t\u00e9", org.monflabs.util.FileUtil.readContent(g));
			} finally {
				g.delete();
			}
		} finally {
			f.delete();
		}
	}
}
