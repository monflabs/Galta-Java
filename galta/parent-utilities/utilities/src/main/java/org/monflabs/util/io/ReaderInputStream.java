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
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import org.monflabs.util.StringUtil;

/**
 * 
 */
public class ReaderInputStream extends InputStream {

    // The characters read at once from the reader
    private static final int CHUNK = 1024;

    private Reader _in;
    private Charset _charset;
    // A stateful encoder so a surrogate pair split across two reads is encoded correctly
    private CharsetEncoder _encoder;
    // Both buffers are allocated once and reused by every fill(), which used to allocate
    // new char and byte arrays for each chunk.
    // The characters to encode, in write mode: a trailing high surrogate stays for the next chunk
    private CharBuffer _chars;
    // The encoded bytes not read yet, in read mode
    private ByteBuffer _bytes;
    private boolean _eof;


    public ReaderInputStream(Reader reader) {
        _in = reader;
        _charset = Charset.defaultCharset();
    }

    public ReaderInputStream(Reader reader, Charset charset) {
        _in = reader;
        if(charset!=null) {
        	_charset = charset;
        } else {
        	_charset = Charset.defaultCharset();
        }
    }

    public ReaderInputStream(Reader reader, String encoding) {
        this(reader, StringUtil.isNotEmpty(encoding) ? Charset.forName(encoding) : null);
    }

    @Override
	public int read() throws IOException {
        ensureOpen();
        while (_bytes == null || !_bytes.hasRemaining()) {
            if (!fill()) {
                return -1;
            }
        }
        // Bytes are unsigned: 0xFF must not be mistaken for the -1 end-of-stream marker
        return _bytes.get() & 0xFF;
    }

    private CharsetEncoder encoder() {
        if (_encoder == null) {
            _encoder = _charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
            _chars = CharBuffer.allocate(CHUNK);
            // Room for a whole chunk, and what flush() may still emit
            _bytes = ByteBuffer.allocate((int) Math.ceil(CHUNK * (double) _encoder.maxBytesPerChar()) + 16);
            _bytes.limit(0);
        }
        return _encoder;
    }

    // Reads the next chunk of characters and encodes it; returns false at end of stream
    private boolean fill() throws IOException {
        if (_eof) {
            return false;
        }
        CharsetEncoder enc = encoder();
        CharBuffer in = _chars;
        ByteBuffer out = _bytes;
        out.clear();
        int n = _in.read(in.array(), in.arrayOffset() + in.position(), in.remaining());
        if (n == -1) {
            _eof = true;
            // Flush whatever the encoder still holds (e.g. a dangling high surrogate)
            in.flip();
            enc.encode(in, out, true);
            enc.flush(out);
            in.clear();
            out.flip();
            return out.hasRemaining();
        }
        if (n > 0) {
            in.position(in.position() + n);
            in.flip();
            CoderResult cr = enc.encode(in, out, false);
            if (cr.isOverflow()) {
                throw new IOException("Encoder buffer overflow");
            }
            // A trailing high surrogate is kept for the next chunk
            in.compact();
        }
        out.flip();
        return true;
    }

    @Override
	public int read(byte[] b, int off, int len)
        throws IOException {
        ensureOpen();
        if (len == 0) {
            return 0;
        }
        while (_bytes == null || !_bytes.hasRemaining()) {
            if (!fill()) {
                return -1;
            }
        }
        if (len > _bytes.remaining()) {
            len = _bytes.remaining();
        }
        _bytes.get(b, off, len);
        return len;
    }

    @Override
	public void mark(final int limit) {
        // mark/reset is not supported (see markSupported()): this is a no-op, per the InputStream contract
    }

    @Override
	public int available() throws IOException {
        ensureOpen();
        if (_bytes != null && _bytes.hasRemaining()) {
            return _bytes.remaining();
        }
        if (_in.ready()) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
	public boolean markSupported () {
        return false;   // would be imprecise
    }

    @Override
	public void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    private void ensureOpen() throws IOException {
        if (_in == null) {
            throw new IOException("Stream closed");
        }
    }

    @Override
	public void close() throws IOException {
        if (_in == null) {
            return;
        }
        _in.close();
        _bytes = null;
        _chars = null;
        _in = null;
    }
}
