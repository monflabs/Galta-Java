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
package org.monflabs.util.io;

import java.io.IOException;
import java.io.Reader;

/**
 * Fast buffered reader.
 */
public class FastStringReader extends Reader {

	private String s;
	private int pos;
	private int length;
	private int mark;

    public FastStringReader(String s) {
    	this.s = s;
    	this.length = s.length();
    }
    public FastStringReader(String s, int pos) {
    	this.s = s;
    	this.pos = pos;
    	this.length = s.length();
    }

    @Override
	public int read() throws IOException {
    	if(pos<length) {
    		return s.charAt(pos++);
    	}
    	return -1;
    }

    @Override
	public int read(char cbuf[], int off, int len) throws IOException {
    	if(len==0) {
    		return 0; // Reader contract: reading nothing is not the end of the stream
    	}
    	if(pos<length) {
    		int count = Math.min(len,length-pos);
    		s.getChars(pos, pos+count, cbuf, off);
    		pos += count;
    		return count;
    	}
    	return -1;
    }

    @Override
	public long skip(long n) throws IOException {
    	if(n<=0) {
    		return 0;
    	}
    	int skip = (int)Math.min(n, (long)(length-pos));
    	pos += skip;
    	return skip;
    }

    @Override
	public boolean ready() throws IOException {
    	return true;
    }

    @Override
	public void close() throws java.io.IOException {
    	// Nothing here...
    }
    
    @Override
    public boolean markSupported() {
        return true;
    }
    @Override
    public void mark(int readAheadLimit) throws IOException {
    	this.mark = pos;
    }
    @Override
	public void reset() throws IOException {
    	this.pos = mark;
    }    
}
