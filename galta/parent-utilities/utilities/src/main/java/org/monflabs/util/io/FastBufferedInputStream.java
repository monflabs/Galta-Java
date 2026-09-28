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
import java.io.InputStream;

/**
 * Fast buffered input stream.
 */
public class FastBufferedInputStream extends InputStream {

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private InputStream is;
    private byte[] buffer;
    private int count;
    private int pos;

    public static FastBufferedInputStream get(InputStream source) {
    	if(source!=null) {
    		if(source instanceof FastBufferedInputStream fb) {
            	return fb;
    		}
    		return new FastBufferedInputStream(source);
    	}
    	return null;
    }

    public FastBufferedInputStream(InputStream is, int size) {
        this.is = is;
        this.buffer = new byte[size];
    }
    public FastBufferedInputStream(InputStream is) {
        this(is,DEFAULT_BUFFER_SIZE);
    }

    public boolean isEOF() throws IOException {
        if( pos==count ) {
            count = is.read( buffer, 0, buffer.length );
            pos = 0;
            if( count<0 ) {
                count = 0;
                return true;
            }
        }
        return false;
    }

    int avail() {
    	return count-pos;
    }
    
    @Override
	public int read() throws IOException {
        if( pos==count ) {
            count = is.read( buffer, 0, buffer.length );
            pos = 0;
            if( count<0 ) {
                count = 0;
                return -1;
            }
        }
        return buffer[pos++] & 0xFF;
    }

    @Override
	public int read(byte[] array) throws IOException {
        return read(array,0,array.length);
    }

    @Override
	public int read(byte[] array, int off, int length) throws IOException {
        if( length==0 ) {
            return 0;
        }
        int avail = count-pos;
        if( avail==0 ) {
            avail = count = is.read( buffer, 0, buffer.length );
            pos = 0;
            if( count<0 ) {
                count = 0;
                return -1;
            }
        }
        int toRead = length<avail ? length : avail;
        System.arraycopy(buffer,pos,array,off,toRead);
        pos += toRead;
        return toRead;
    }

    @Override
	public long skip(long n) throws IOException {
        if( n<=0 ) {
            return 0;
        }
        int avail = count-pos;
        if( avail>0 ) {
            if( n<avail ) {
                pos += n;
                return n;
            }
            pos = count;
            return avail;
        }
        return is.skip(n);
    }

    @Override
	public int available() throws IOException {
        return is.available() + (count-pos);
    }

    @Override
	public void close() throws IOException {
        is.close();
    }

    @Override
	public void mark(int int0) {
        // Not supported (see markSupported()): a no-op, per the InputStream contract
    }

    @Override
	public void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    @Override
	public boolean markSupported() {
        return false;
    }
}
