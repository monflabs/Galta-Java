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
 * Fast buffered output stream.
 */
public class FastBufferedOutputStream extends OutputStream {

    private static final int DEFAULT_BUFFER_SIZE = 16384;

    private OutputStream os;
    private byte[] buffer;
    private int bufferLength;
    private int pos;

    public static FastBufferedOutputStream get(OutputStream source) {
    	if(source!=null) {
    		if(source instanceof FastBufferedOutputStream fb) {
            	return fb;
    		}
    		return new FastBufferedOutputStream(source);
    	}
    	return null;
    }

    public FastBufferedOutputStream( OutputStream os, int size ) {
        this.os = os;
        this.bufferLength = size;
        this.buffer = new byte[size];
    }

    public FastBufferedOutputStream( OutputStream os ) {
        this(os,DEFAULT_BUFFER_SIZE);
    }
    
    public void flushBuffer() throws IOException {
        os.write(buffer,0,pos);
        pos = 0;
    }

    @Override
	public void write(int b) throws IOException {
        if( pos==bufferLength ) {
        	flushBuffer();
        }
        buffer[pos++] = (byte)(b&0xFF);
    }

    @Override
	public void write(byte b[]) throws IOException {
        write(b, 0, b.length);
    }

    @Override
	public void write(byte b[], int off, int len) throws IOException {
        if( len>=bufferLength ) {
        	// Larger than the buffer: written through, rather than copied through the buffer
        	if( pos>0 ) {
        		flushBuffer();
        	}
        	os.write(b,off,len);
        	return;
        }
        while(len>0) {
            if( pos==bufferLength ) {
            	flushBuffer();
            }
            int avail = bufferLength-pos;
            int toWrite = len>avail ? avail : len;
            System.arraycopy(b,off,buffer,pos,toWrite);
            pos += toWrite;
            off += toWrite;
            len -= toWrite;
        }
    }

    @Override
	public void flush() throws IOException {
        if( pos>0 ) {
        	flushBuffer();
        }
        os.flush();
    }

    @Override
	public void close() throws IOException {
        if( pos>0 ) {
        	flushBuffer();
        }
        os.close();
    }
}
