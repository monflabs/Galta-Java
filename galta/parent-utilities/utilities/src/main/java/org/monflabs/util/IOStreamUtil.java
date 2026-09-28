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
package org.monflabs.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 
 */ 
public class IOStreamUtil {
	
	public static void close(AutoCloseable c) {
		try {
			if(c!=null) {
				c.close();
			}
		} catch(Exception ex) {
			throw new ForwardRuntimeException(ex, "Error while closing stream");
		}
	}
	
	
	// ---- Remove - or rename!!
	public static String readString(Reader reader) throws IOException {
		return readContent(reader).toString();
	}
	// ---- Remove!!
	
	// renamne methods here!!
    
    public static byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
	
	
	public static String readContent(InputStream is) throws IOException {
		return readContent(is,null);
	}
	public static String readContent(InputStream is, Charset cs) throws IOException {
		Reader r = new InputStreamReader(is,cs!=null ? cs : StandardCharsets.UTF_8);
		return readContent(r);
	}
	public static String readContent(Reader r) throws IOException {
		StringBuilder sb  = new StringBuilder(2048);
		char[] c = new char[8192];
		int count;
		while((count=r.read(c))>=0) {
			sb.append(c, 0, count);
		}
		return sb.toString();
	}
	
	
	public static void setContent(OutputStream os, String content) throws IOException {
		setContent(os, content, null);
	}
	public static void setContent(OutputStream os, String content, Charset cs) throws IOException {
		Writer w = new OutputStreamWriter(os,cs!=null ? cs : StandardCharsets.UTF_8);
		setContent(w, content);
		w.flush();
	}
	public static void setContent(Writer w, String content) throws IOException {
		w.write(content);
	}
}