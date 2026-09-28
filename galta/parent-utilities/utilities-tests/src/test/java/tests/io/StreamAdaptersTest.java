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
}
