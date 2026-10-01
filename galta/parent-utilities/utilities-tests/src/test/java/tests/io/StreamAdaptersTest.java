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

import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.monflabs.util.io.ReaderInputStream;
import org.monflabs.util.io.WriterOutputStream;

import tests.ProjectTestCase;

/**
 * ReaderInputStream and WriterOutputStream contract checks.
 */
public class StreamAdaptersTest extends ProjectTestCase {

	public void testReaderInputStreamDefaultCharset() throws Exception {
		// Charset.defaultCharset(), not Charset.forName(file.encoding): "COMPAT" is a valid
		// file.encoding value on recent JDKs but not a charset name
		String old = System.getProperty("file.encoding");
		System.setProperty("file.encoding", "COMPAT");
		try(ReaderInputStream is = new ReaderInputStream(new StringReader("é"))) {
			assertEquals("é", new String(is.readAllBytes(), Charset.defaultCharset()));
		} finally {
			if(old!=null) {
				System.setProperty("file.encoding", old);
			} else {
				System.clearProperty("file.encoding");
			}
		}
	}

	public void testReaderInputStreamMarkReset() throws Exception {
		try(ReaderInputStream is = new ReaderInputStream(new StringReader("abc"), StandardCharsets.UTF_8)) {
			assertFalse(is.markSupported());
			is.mark(10);    // a no-op
			assertEquals('a', is.read());
			assertThrows(IOException.class, () -> is.reset());
			assertEquals('b', is.read());
		}
	}

	public void testReaderInputStreamClosed() throws Exception {
		ReaderInputStream is = new ReaderInputStream(new StringReader("abc"), StandardCharsets.UTF_8);
		is.close();
		is.close();   // idempotent
		// An IOException, not a NullPointerException
		assertThrows(IOException.class, () -> is.read());
		assertThrows(IOException.class, () -> is.read(new byte[2], 0, 2));
		assertThrows(IOException.class, () -> is.available());
	}

	public void testWriterOutputStreamDefaultUtf8() throws Exception {
		StringWriter sw = new StringWriter();
		try(WriterOutputStream os = new WriterOutputStream(sw)) {
			os.write("é€".getBytes(StandardCharsets.UTF_8));
		}
		assertEquals("é€", sw.toString());
	}

	public void testWriterOutputStreamSplitSequence() throws Exception {
		// A multi-byte sequence split across two writes
		byte[] euro = "€".getBytes(StandardCharsets.UTF_8);
		StringWriter sw = new StringWriter();
		try(WriterOutputStream os = new WriterOutputStream(sw, StandardCharsets.UTF_8)) {
			os.write(euro, 0, 1);
			os.write(euro, 1, 2);
		}
		assertEquals("€", sw.toString());
	}

	public void testLRUCachedOutputStreamSingleBytes() throws Exception {
		org.monflabs.util.io.LRUCharBuffer buf = new org.monflabs.util.io.LRUCharBuffer.MemoryCharBuffer(100, 0);
		java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream();
		org.monflabs.util.io.LRUCachedOutputStream os = org.monflabs.util.io.LRUCachedOutputStream.of(all, buf);
		// Byte by byte, a multi-byte character included: buffered, then decoded by runs
		for(byte b: "\u00e9t\u00e9\nab".getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
			os.write(b);
		}
		// What is still pending is visible from the char buffer too
		assertEquals("\u00e9t\u00e9\nab", os.getCharBuffer().toString());
		os.write("cd".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		os.write('e');
		os.flush();
		assertEquals("\u00e9t\u00e9\nabcde", buf.toString());
		assertEquals("\u00e9t\u00e9\nabcde", all.toString(java.nio.charset.StandardCharsets.UTF_8));
	}

	public void testFastBufferedLargeWritesGoThrough() throws Exception {
		java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
		org.monflabs.util.io.FastBufferedOutputStream fos = new org.monflabs.util.io.FastBufferedOutputStream(bos, 8);
		fos.write(new byte[] {1,2,3});
		fos.write(new byte[20]);
		// The small pending write is flushed first, then the large one goes straight through
		assertEquals(23, bos.size());
		fos.write(4);
		fos.flush();
		assertEquals(24, bos.size());
		assertEquals(1, bos.toByteArray()[0]);
		assertEquals(4, bos.toByteArray()[23]);

		java.io.StringWriter sw = new java.io.StringWriter();
		org.monflabs.util.io.FastBufferedWriter fw = new org.monflabs.util.io.FastBufferedWriter(sw, 8);
		fw.write("abc");
		fw.write("0123456789ABCDEF");
		assertEquals("abc0123456789ABCDEF", sw.toString());
		fw.write("xyz", 1, 2);
		fw.write(new char[] {'!'});
		fw.flush();
		assertEquals("abc0123456789ABCDEFyz!", sw.toString());
	}

	public void testReaderInputStreamReusesBuffers() throws Exception {
		StringBuilder sb = new StringBuilder();
		for(int i=0; i<5000; i++) {
			sb.append("x\u00e9\ud83d\ude00");
		}
		String s = sb.toString();
		// Read in odd sizes so that chunks split characters and surrogate pairs
		try(java.io.InputStream in = new org.monflabs.util.io.ReaderInputStream(new java.io.StringReader(s), java.nio.charset.StandardCharsets.UTF_8)) {
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			byte[] b = new byte[7];
			int n;
			while((n=in.read(b,0,b.length))>0) {
				out.write(b,0,n);
				int c = in.read();
				if(c<0) {
					break;
				}
				out.write(c);
			}
			assertEquals(s, out.toString(java.nio.charset.StandardCharsets.UTF_8));
		}
	}
}
