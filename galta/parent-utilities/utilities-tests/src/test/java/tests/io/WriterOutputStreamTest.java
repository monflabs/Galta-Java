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

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.monflabs.util.io.WriterOutputStream;

import tests.ProjectTestCase;

public class WriterOutputStreamTest extends ProjectTestCase {
	
    public void testWrite() throws Exception {
        final String content = "Hello, товарищ! How are you?";
        
        StringWriter sw = new StringWriter();
        
        WriterOutputStream os = new WriterOutputStream(sw, StandardCharsets.UTF_8);
        os.write(content.getBytes(StandardCharsets.UTF_8));
        os.close();
        
        assertEquals(content, sw.toString());
    }

    private static class CountingWriter extends StringWriter {
    	int flushes;
    	@Override
    	public void flush() {
    		flushes++;
    		super.flush();
    	}
    }

    public void testNoFlushPerWrite() throws Exception {
    	// The writer used to be flushed on every single write
    	CountingWriter w = new CountingWriter();
    	WriterOutputStream os = new WriterOutputStream(w);
    	assertFalse(os.isAutoFlush());
    	for (int i = 0; i < 10; i++) {
    		os.write('a');
    	}
    	assertEquals(0, w.flushes);
    	assertEquals("", w.toString());          // still buffered
    	os.flush();
    	assertEquals(1, w.flushes);
    	assertEquals("aaaaaaaaaa", w.toString());
    	os.write("b".getBytes(StandardCharsets.UTF_8));
    	os.close();
    	assertEquals("aaaaaaaaaab", w.toString());
    }

    public void testAutoFlush() throws Exception {
    	CountingWriter w = new CountingWriter();
    	WriterOutputStream os = new WriterOutputStream(w, true);
    	assertTrue(os.isAutoFlush());
    	os.write('a');
    	assertEquals("a", w.toString());          // published immediately
    	os.write("bc".getBytes(StandardCharsets.UTF_8));
    	assertEquals("abc", w.toString());
    	assertEquals(2, w.flushes);
    }
}
