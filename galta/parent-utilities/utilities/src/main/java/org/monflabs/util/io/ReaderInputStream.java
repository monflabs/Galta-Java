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

    private Reader _in;
    private Charset _charset;
    private byte[] _slack;
    private int _begin;
    // A stateful encoder so a surrogate pair split across two reads is encoded correctly
    private CharsetEncoder _encoder;
    private CharBuffer _pending;
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
        if (_slack != null && _begin < _slack.length) {
            int result = _slack[_begin] & 0xFF;
            if (++_begin == _slack.length) {
                _slack = null;
            }
            return result;
        }
        byte[] buf = new byte[1];
        int n = read(buf, 0, 1);
        // Bytes are unsigned: 0xFF must not be mistaken for the -1 end-of-stream marker
        return n <= 0 ? -1 : (buf[0] & 0xFF);
    }

    private CharsetEncoder encoder() {
        if (_encoder == null) {
            _encoder = _charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        }
        return _encoder;
    }

    // Reads the next chunk of characters and encodes it; returns false at end of stream
    private boolean fill(int hint) throws IOException {
        if (_eof) {
            return false;
        }
        char[] buf = new char[Math.max(hint, 64)];
        int n = _in.read(buf);
        CharsetEncoder enc = encoder();
        if (n == -1) {
            _eof = true;
            // Flush whatever the encoder still holds (e.g. a dangling high surrogate)
            CharBuffer in = _pending != null ? _pending : CharBuffer.allocate(0);
            ByteBuffer out = ByteBuffer.allocate(16);
            enc.encode(in, out, true);
            enc.flush(out);
            _pending = null;
            if (out.position() == 0) {
                return false;
            }
            _slack = new byte[out.position()];
            out.flip();
            out.get(_slack);
            _begin = 0;
            return true;
        }
        if (n == 0) {
            return true;
        }
        CharBuffer in;
        if (_pending != null && _pending.hasRemaining()) {
            in = CharBuffer.allocate(_pending.remaining() + n);
            in.put(_pending).put(buf, 0, n).flip();
        } else {
            in = CharBuffer.wrap(buf, 0, n);
        }
        ByteBuffer out = ByteBuffer.allocate((int) Math.ceil(in.remaining() * (double) enc.maxBytesPerChar()) + 16);
        CoderResult cr = enc.encode(in, out, false);
        if (cr.isOverflow()) {
            throw new IOException("Encoder buffer overflow");
        }
        // A trailing high surrogate is kept for the next chunk
        _pending = in.hasRemaining() ? CharBuffer.wrap(in.toString()) : null;
        _slack = new byte[out.position()];
        out.flip();
        out.get(_slack);
        _begin = 0;
        return true;
    }

    @Override
	public int read(byte[] b, int off, int len)
        throws IOException {
        ensureOpen();
        if (len == 0) {
            return 0;
        }
        while (_slack == null || _begin >= _slack.length) {
            _slack = null;
            if (!fill(len)) {
                return -1;
            }
        }

        if (len > _slack.length - _begin) {
            len = _slack.length - _begin;
        }

        System.arraycopy(_slack, _begin, b, off, len);

        if ((_begin += len) >= _slack.length) {
            _slack = null;
        }

        return len;
    }

    @Override
	public void mark(final int limit) {
        // mark/reset is not supported (see markSupported()): this is a no-op, per the InputStream contract
    }

    @Override
	public int available() throws IOException {
        ensureOpen();
        if (_slack != null) {
            return _slack.length - _begin;
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
        _slack = null;
        _in = null;
    }
}
