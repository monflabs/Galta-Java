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
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

/**
 * Inspired from jdk.internal.le
 * 
 * Redirects an {@link OutputStream} to a {@link Writer} by decoding the data
 * using the specified {@link Charset}.
 *
 * <p><b>Note:</b> This class should only be used if it is necessary to
 * redirect an {@link OutputStream} to a {@link Writer} for compatibility
 * purposes. It is much more efficient to write to the {@link Writer}
 * directly.</p>
 *
 * <p>The decoded characters are buffered: they reach the writer when the buffer is full,
 * and on {@link #flush()} and {@link #close()}. With <i>autoFlush</i>, every write is
 * followed by a {@link #flush()} (which also flushes the writer), so the text is published
 * immediately - e.g. for a console or text area sink.</p>
 */
public class WriterOutputStream extends OutputStream {

    private final Writer out;
    private final CharsetDecoder decoder;
    private final boolean autoFlush;
    private final ByteBuffer decoderIn = ByteBuffer.allocate(256);
    private final CharBuffer decoderOut = CharBuffer.allocate(128);

    public WriterOutputStream(Writer out) {
    	this(out, StandardCharsets.UTF_8);
    }

    /**
     * @param autoFlush whether every write is followed by a {@link #flush()}
     */
    public WriterOutputStream(Writer out, boolean autoFlush) {
    	this(out, StandardCharsets.UTF_8, autoFlush);
    }

    public WriterOutputStream(Writer out, Charset charset) {
        this(out, charset, false);
    }

    /**
     * @param autoFlush whether every write is followed by a {@link #flush()}
     */
    public WriterOutputStream(Writer out, Charset charset, boolean autoFlush) {
        this(out, charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE), autoFlush);
    }

    public WriterOutputStream(Writer out, CharsetDecoder decoder) {
        this(out, decoder, false);
    }

    /**
     * @param autoFlush whether every write is followed by a {@link #flush()}
     */
    public WriterOutputStream(Writer out, CharsetDecoder decoder, boolean autoFlush) {
        this.out = out;
        this.decoder = decoder;
        this.autoFlush = autoFlush;
    }

    public boolean isAutoFlush() {
    	return autoFlush;
    }

    @Override
    public void write(int b) throws IOException {
        // No byte[] per call: decoderIn always has room after processInput() compacts it
        decoderIn.put((byte)b);
        processInput(false);
        flush();
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            final int c = Math.min(len, decoderIn.remaining());
            decoderIn.put(b, off, c);
            processInput(false);
            len -= c;
            off += c;
        }
        // The writer used to be flushed on every write: it now only is with autoFlush, for
        // the sinks (console, text area) that must publish the text immediately
        if(autoFlush) {
        	flush();
        }
    }

    @Override
    public void flush() throws IOException {
        flushOutput();
        if(out!=null) {
        	out.flush();
        }
    }

    @Override
    public void close() throws IOException {
        processInput(true);
        flush();
        if(out!=null) {
        	out.close();
        }
    }

    /**
     * Decode the contents of the input ByteBuffer into a CharBuffer.
     *
     * @param endOfInput indicates end of input
     * @throws IOException if an I/O error occurs
     */
    private void processInput(final boolean endOfInput) throws IOException {
        // Prepare decoderIn for reading
        decoderIn.flip();
        CoderResult coderResult;
        while (true) {
            coderResult = decoder.decode(decoderIn, decoderOut, endOfInput);
            if (coderResult.isOverflow()) {
                flushOutput();
            } else if (coderResult.isUnderflow()) {
                break;
            } else {
                // The decoder is configured to replace malformed input and unmappable characters,
                // so we should not get here.
                throw new IOException("Unexpected coder result");
            }
        }
        // Discard the bytes that have been read
        decoderIn.compact();
    }

    /**
     * Flush the output.
     *
     * @throws IOException if an I/O error occurs
     */
    private void flushOutput() throws IOException {
        if (decoderOut.position() > 0) {
            write(decoderOut.array(), 0, decoderOut.position());
            decoderOut.rewind();
        }
    }
    
    protected void write(char[] chars, int pos, int len) throws IOException {
        if(out!=null) {
        	out.write(chars, pos, len);
        }
    }
}