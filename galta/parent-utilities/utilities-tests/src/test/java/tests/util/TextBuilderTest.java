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

import org.monflabs.util.TextBuilder;

import tests.ProjectTestCase;

public class TextBuilderTest extends ProjectTestCase {

	public void testIndentation() throws Exception {
		TextBuilder b = new TextBuilder();
		b.println("a {");
		b.incIndent();
		b.println("b;");
		b.print("c").append(';').nl();
		b.decIndent();
		b.println("}");
		assertEquals("a {\n  b;\n  c;\n}\n", b.toString());
		assertEquals(5, b.getCurrentLine());
		assertEquals(0, b.getIndent());
	}

	public void testConstructorTracksLines() throws Exception {
		// The initial content used to bypass the line tracking: getCurrentLine() stayed 1 and
		// the next text was indented in the middle of a line
		TextBuilder b = new TextBuilder("abc");
		b.incIndent();
		b.append("x");
		assertEquals("abcx", b.toString());

		b = new TextBuilder("l1\nl2\n");
		assertEquals(3, b.getCurrentLine());
		b.incIndent();
		b.append("x");
		assertEquals("l1\nl2\n  x", b.toString());

		b = new TextBuilder("{0}-{1}\n", "a", "b");
		assertEquals(2, b.getCurrentLine());
		assertEquals("a-b\n", b.toString());
	}

	public void testCarriageReturns() throws Exception {
		// A '\r' is dropped and must not trigger the indentation of an empty line
		TextBuilder b = new TextBuilder();
		b.incIndent();
		b.append("a\r\n");
		b.append("\r\n");
		b.append("b");
		assertEquals("  a\n\n  b", b.toString());
		assertEquals(3, b.getCurrentLine());
	}

	public void testClearAndCharSequence() throws Exception {
		TextBuilder b = new TextBuilder();
		b.incIndent().append("xy").nl();
		assertEquals(5, b.length());
		assertEquals('x', b.charAt(2));
		assertEquals("xy", b.subSequence(2, 4).toString());
		b.clear();
		assertEquals("", b.toString());
		assertEquals(1, b.getCurrentLine());
		assertEquals(0, b.getIndent());
		b.append(12).append(' ').append(34L);
		assertEquals("12 34", b.toString());
	}

	public void testBulkAppendKeepsIndentationAndLines() throws Exception {
		org.monflabs.util.TextBuilder t = new org.monflabs.util.TextBuilder();
		t.incIndent();
		t.append("a\r\nbb\n\nccc");
		t.append("dd\n");
		t.append("");
		t.append("e");
		assertEquals("  a\n  bb\n\n  cccdd\n  e", t.toString());
		assertEquals(5, t.getCurrentLine());
	}
}
