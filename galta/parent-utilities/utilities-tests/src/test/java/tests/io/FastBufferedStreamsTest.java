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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.monflabs.util.io.FastBufferedInputStream;
import org.monflabs.util.io.FastBufferedOutputStream;
import org.monflabs.util.io.FastBufferedReader;
import org.monflabs.util.io.FastBufferedWriter;
import org.monflabs.util.io.FastStringReader;

import tests.ProjectTestCase;

/**
 * FastBufferedReader/InputStream/Writer/OutputStream against the java.io contracts.
 * A tiny buffer size is used so every test crosses buffer boundaries.
 */
public class FastBufferedStreamsTest extends ProjectTestCase {

	private static final String TEXT = "abcdefghijklmnopqrstuvwxyz0123456789";
	private static final byte[] BYTES = TEXT.getBytes(StandardCharsets.US_ASCII);

	//
	// Reader
	//

	public void testReaderReadCharByChar() throws Exception {
		try(FastBufferedReader r = new FastBufferedReader(new StringReader(TEXT), 4)) {
			StringBuilder b = new StringBuilder();
			int c;
			while((c=r.read())>=0) {
				b.append((char)c);
			}
			assertEquals(TEXT, b.toString());
			assertEquals(-1, r.read());   // stays at EOF
			assertTrue(r.isEOF());
		}
	}

	public void testReaderReadArrayAcrossBuffers() throws Exception {
		try(FastBufferedReader r = new FastBufferedReader(new StringReader(TEXT), 4)) {
			char[] buf = new char[TEXT.length()+10];
			int total = 0;
			int n;
			while((n=r.read(buf, total, 7))>0) {
				assertTrue(n<=7);
				total += n;
			}
			assertEquals(-1, n);
			assertEquals(TEXT, new String(buf, 0, total));
		}
	}

	public void testReaderZeroLengthRead() throws Exception {
		try(FastBufferedReader r = new FastBufferedReader(new StringReader("ab"), 4)) {
			assertEquals(0, r.read(new char[4], 0, 0));
			assertEquals('a', r.read());
			r.read(); // 'b'
			// Reader contract: reading nothing is never the end of the stream
			assertEquals(0, r.read(new char[4], 0, 0));
			assertEquals(-1, r.read(new char[4], 0, 4));
		}
	}

	public void testReaderSkip() throws Exception {
		try(FastBufferedReader r = new FastBufferedReader(new StringReader(TEXT), 4)) {
			assertEquals('a', r.read());
			assertEquals(2, r.skip(2));         // within the buffer
			assertEquals('d', r.read());
			assertEquals(0, r.skip(0));
			// A negative skip used to move backwards in the buffer
			assertThrows(IllegalArgumentException.class, () -> r.skip(-3));
			assertEquals('e', r.read());
		}
	}

	public void testReaderMarkResetNotSupported() throws Exception {
		try(FastBufferedReader r = new FastBufferedReader(new StringReader(TEXT), 4)) {
			assertFalse(r.markSupported());
			// Reader contract: both throw when not supported (they used to be silent no-ops)
			assertThrows(IOException.class, () -> r.mark(10));
			r.read();
			assertThrows(IOException.class, () -> r.reset());
		}
	}

	public void testReaderIsEOFDoesNotConsume() throws Exception {
		try(FastBufferedReader r = new FastBufferedReader(new StringReader("xy"), 1)) {
			assertFalse(r.isEOF());
			assertEquals('x', r.read());
			assertFalse(r.isEOF());
			assertEquals('y', r.read());
			assertTrue(r.isEOF());
		}
	}

	//
	// InputStream
	//

	public void testInputStreamReadAll() throws Exception {
		try(FastBufferedInputStream is = new FastBufferedInputStream(new ByteArrayInputStream(BYTES), 5)) {
			assertArrayEquals(BYTES, is.readAllBytes());
			assertEquals(-1, is.read());
			assertTrue(is.isEOF());
		}
	}

	public void testInputStreamUnsignedBytes() throws Exception {
		byte[] data = {(byte)0xFF, 0, (byte)0x80};
		try(FastBufferedInputStream is = new FastBufferedInputStream(new ByteArrayInputStream(data), 2)) {
			assertEquals(0xFF, is.read());   // not -1
			assertEquals(0, is.read());
			assertEquals(0x80, is.read());
			assertEquals(-1, is.read());
		}
	}

	public void testInputStreamReadArrayAndAvailable() throws Exception {
		try(FastBufferedInputStream is = new FastBufferedInputStream(new ByteArrayInputStream(BYTES), 4)) {
			assertEquals(0, is.read(new byte[3], 0, 0));
			byte[] b = new byte[3];
			assertEquals(3, is.read(b, 0, 3));
			assertArrayEquals(new byte[] {'a','b','c'}, b);
			assertEquals(BYTES.length-3, is.available());
		}
	}

	public void testInputStreamSkip() throws Exception {
		try(FastBufferedInputStream is = new FastBufferedInputStream(new ByteArrayInputStream(BYTES), 4)) {
			assertEquals('a', is.read());
			assertEquals(2, is.skip(2));
			assertEquals('d', is.read());
			// InputStream contract: a negative skip skips nothing (it used to move backwards)
			assertEquals(0, is.skip(-3));
			assertEquals('e', is.read());
			assertEquals(0, is.skip(0));
		}
	}

	public void testInputStreamMarkResetNotSupported() throws Exception {
		try(FastBufferedInputStream is = new FastBufferedInputStream(new ByteArrayInputStream(BYTES), 4)) {
			assertFalse(is.markSupported());
			is.mark(10);           // InputStream contract: a no-op
			is.read();
			is.read();
			// InputStream contract: reset() throws when mark isn't supported (it used to be silent)
			assertThrows(IOException.class, () -> is.reset());
			assertEquals('c', is.read());
		}
	}

	public void testInputStreamGet() throws Exception {
		assertNull(FastBufferedInputStream.get(null));
		InputStream raw = new ByteArrayInputStream(BYTES);
		FastBufferedInputStream fb = FastBufferedInputStream.get(raw);
		assertNotSame(raw, fb);
		assertSame(fb, FastBufferedInputStream.get(fb));
	}

	//
	// Writer
	//

	public void testWriterBuffersAndFlushes() throws Exception {
		StringWriter sw = new StringWriter();
		FastBufferedWriter w = new FastBufferedWriter(sw, 4);
		w.write('a');
		w.write("bc");
		assertEquals("", sw.toString());     // still in the buffer
		w.write("defghij".toCharArray(), 1, 5);  // crosses the buffer boundary
		w.flush();
		assertEquals("abcefghi", sw.toString());
		w.write(TEXT);
		w.close();
		assertEquals("abcefghi"+TEXT, sw.toString());
	}

	public void testWriterGet() throws Exception {
		assertNull(FastBufferedWriter.get(null));
		FastBufferedWriter w = FastBufferedWriter.get(new StringWriter());
		assertSame(w, FastBufferedWriter.get(w));
	}

	//
	// OutputStream
	//

	public void testOutputStreamBuffersAndFlushes() throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		FastBufferedOutputStream os = new FastBufferedOutputStream(bos, 4);
		os.write(0xFF);
		os.write(BYTES, 0, 2);
		assertEquals(0, bos.size());
		os.write(BYTES, 2, 10);
		os.flush();
		assertEquals(13, bos.size());
		assertEquals((byte)0xFF, bos.toByteArray()[0]);
		os.write(BYTES);
		os.close();
		assertEquals(13+BYTES.length, bos.size());
	}

	public void testOutputStreamGet() throws Exception {
		assertNull(FastBufferedOutputStream.get(null));
		FastBufferedOutputStream os = FastBufferedOutputStream.get(new ByteArrayOutputStream());
		assertSame(os, FastBufferedOutputStream.get(os));
	}

	//
	// FastStringReader
	//

	public void testFastStringReaderZeroLengthReadAtEOF() throws Exception {
		FastStringReader r = new FastStringReader("a");
		assertEquals('a', r.read());
		// Reader contract: 0, not -1, when nothing is requested
		assertEquals(0, r.read(new char[2], 0, 0));
		assertEquals(-1, r.read(new char[2], 0, 2));
	}
}
