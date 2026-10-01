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
package org.monflabs.filesystem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

/**
 * A read-only SeekableByteChannel over a byte array, used by the read-only
 * filesystems (zip, resources).
 */
public final class ReadOnlyByteChannel implements SeekableByteChannel {

    private final byte[] content;
    // A long: a position set beyond 2GB used to be cast to int and wrap around
    private long position;
    private volatile boolean open = true;

    public ReadOnlyByteChannel(byte[] content) {
        this.content = content;
    }

    @Override
    public synchronized int read(ByteBuffer dst) throws IOException {
        checkOpen();
        if (position >= content.length) {
            return -1;
        }
        int length = (int) Math.min(dst.remaining(), content.length - position);
        dst.put(content, (int) position, length);
        position += length;
        return length;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        checkOpen();
        throw new NonWritableChannelException();
    }

    @Override
    public synchronized long position() throws IOException {
        checkOpen();
        return position;
    }

    @Override
    public synchronized SeekableByteChannel position(long newPosition) throws IOException {
        checkOpen();
        if (newPosition < 0) {
            throw new IllegalArgumentException("Negative position");
        }
        this.position = newPosition;
        return this;
    }

    @Override
    public long size() throws IOException {
        checkOpen();
        return content.length;
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        checkOpen();
        throw new NonWritableChannelException();
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        open = false;
    }

    // The SeekableByteChannel contract: a closed channel throws ClosedChannelException
    private void checkOpen() throws ClosedChannelException {
        if (!open) {
            throw new ClosedChannelException();
        }
    }
}
