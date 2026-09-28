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
package tests.io;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.monflabs.util.io.ReaderInputStream;

import tests.ProjectTestCase;

public class ReaderInputStreamTest extends ProjectTestCase {

	public void testSingleByteReads() throws Exception {
		// 0xFF (\u00FF in ISO-8859-1) must come back as 255, not -1; EOF must be -1, not 0
		InputStream in = new ReaderInputStream(new StringReader("A\u00FF"), StandardCharsets.ISO_8859_1);
		assertEquals( 'A', in.read() );
		assertEquals( 255, in.read() );
		assertEquals( -1, in.read() );
		assertEquals( -1, in.read() );
	}

	public void testUtf8() throws Exception {
		String s = "h\u00e9llo \u4e16\u754c \ud83d\ude00";
		InputStream in = new ReaderInputStream(new StringReader(s), StandardCharsets.UTF_8);
		byte[] all = in.readAllBytes();
		assertEquals( s, new String(all, StandardCharsets.UTF_8) );
		// Byte-by-byte
		in = new ReaderInputStream(new StringReader(s), "UTF-8");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for(int b=in.read(); b>=0; b=in.read()) {
			assertTrue(b>=0 && b<=255);
			out.write(b);
		}
		assertEquals( s, new String(out.toByteArray(), StandardCharsets.UTF_8) );
	}

	public void testSurrogatePairAcrossChunks() throws Exception {
		// A reader that hands out one char at a time splits every surrogate pair across two reads
		String s = "a\ud83d\ude00b";
		Reader oneChar = new StringReader(s) {
			@Override
			public int read(char[] cbuf, int off, int len) throws java.io.IOException {
				return super.read(cbuf, off, Math.min(len, 1));
			}
		};
		InputStream in = new ReaderInputStream(oneChar, StandardCharsets.UTF_8);
		assertEquals( s, new String(in.readAllBytes(), StandardCharsets.UTF_8) );
	}

	public void testZeroLengthRead() throws Exception {
		InputStream in = new ReaderInputStream(new StringReader("abc"), StandardCharsets.UTF_8);
		assertEquals( 0, in.read(new byte[4], 0, 0) );
		assertEquals( 3, in.read(new byte[4], 0, 4) );
		assertEquals( -1, in.read(new byte[4], 0, 4) );
	}
}
