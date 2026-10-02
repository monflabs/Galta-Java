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
import java.io.OutputStream;

/**
 * Outputstrean that caches some of its content based on a size value.
 */
public class LRUCachedOutputStream extends OutputStream {
	
	public static final LRUCachedOutputStream of(LRUCharBuffer buffer ) {
		return new LRUCachedOutputStream(null,buffer);
	}

	public static final LRUCachedOutputStream of(OutputStream os, LRUCharBuffer buffer ) {
		return new LRUCachedOutputStream(os,buffer);
	}

    private static class CacheWriter extends WriterOutputStream {
        private LRUCharBuffer buffer;
    	CacheWriter(LRUCharBuffer buffer) {
    		// Auto flush: the cache can be read at any time, so the decoded text must reach it
    		// with every (bulk) write
    		super(null, true);
    		this.buffer = buffer;
    	}
        @Override
		protected void write(char[] chars, int pos, int len) throws IOException {
            if(buffer!=null) {
            	buffer.write(chars,pos,len);
            }
        }
    }

    private OutputStream os;
    private CacheWriter cw;
    // Single bytes are collected here and decoded into the cache by runs (at a new line,
    // a bulk write, a flush, or when full): decoding and flushing the cache for every
    // byte written with write(int) was very slow
    private final byte[] pending = new byte[256];
    private int pendingCount;

    private LRUCachedOutputStream( OutputStream os, LRUCharBuffer buffer ) {
        this.os = os;
        this.cw = new CacheWriter(buffer);
    }
    
    public OutputStream getOutputStream() {
    	return os;
    }
    
    public synchronized LRUCharBuffer getCharBuffer() {
    	try {
    		flushPending();
    	} catch(IOException ex) {
    		// The cache is in memory: nothing to report
    	}
    	return cw.buffer;
    }

    private void flushPending() throws IOException {
    	if(pendingCount>0) {
    		int n = pendingCount;
    		pendingCount = 0;
    		cw.write(pending,0,n);
    	}
    }

    @Override
	public synchronized void write(int b) throws IOException {
    	if(cw!=null) {
    		pending[pendingCount++] = (byte)b;
    		if(b=='\n' || pendingCount==pending.length) {
    			flushPending();
    		}
    	}
    	if(os!=null) {
    		os.write(b);
    	}
    }

    @Override
	public void write(byte b[]) throws IOException {
    	write(b,0,b.length);
    }

    @Override
	public synchronized void write(byte b[], int off, int len) throws IOException {
    	if(cw!=null) {
    		flushPending();
        	cw.write(b,off,len);
    	}
    	if(os!=null) {
        	os.write(b,off,len);
    	}
    }

    @Override
	public synchronized void flush() throws IOException {
    	if(cw!=null) {
    		flushPending();
            cw.flush();
    	}
    	if(os!=null) {
            os.flush();
    	}
    }

    @Override
	public synchronized void close() throws IOException {
    	if(cw!=null) {
    		flushPending();
            cw.close();
    	}
    	if(os!=null) {
            os.close();
    	}
    }
}
