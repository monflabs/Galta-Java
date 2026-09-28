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
    		super(null);
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
    
    private LRUCachedOutputStream( OutputStream os, LRUCharBuffer buffer ) {
        this.os = os;
        this.cw = new CacheWriter(buffer);
    }
    
    public OutputStream getOutputStream() {
    	return os;
    }
    
    public LRUCharBuffer getCharBuffer() {
    	return cw.buffer;
    }

    @Override
	public void write(int b) throws IOException {
    	if(cw!=null) {
    		cw.write(b);
    	}
    	if(os!=null) {
    		os.write(b);
    	}
    }

    @Override
	public void write(byte b[]) throws IOException {
    	if(cw!=null) {
    		cw.write(b);
    	}
    	if(os!=null) {
    		os.write(b);
    	}
    }

    @Override
	public void write(byte b[], int off, int len) throws IOException {
    	if(cw!=null) {
        	cw.write(b,off,len);
    	}
    	if(os!=null) {
        	os.write(b,off,len);
    	}
    }

    @Override
	public void flush() throws IOException {
    	if(cw!=null) {
            cw.flush();
    	}
    	if(os!=null) {
            os.flush();
    	}
    }

    @Override
	public void close() throws IOException {
    	if(cw!=null) {
            cw.close();
    	}
    	if(os!=null) {
            os.close();
    	}
    }
}
