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

import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A DirectoryStream over an already computed list of entries, following the
 * {@link DirectoryStream} contract: {@link #iterator()} can be called only once (a
 * second call, or a call after close, throws IllegalStateException), and once the
 * stream is closed its iterator behaves as if the end of the stream had been reached.
 */
public final class ListDirectoryStream implements DirectoryStream<Path> {

    private final List<Path> entries;
    private volatile boolean closed;
    private boolean iteratorReturned;

    public ListDirectoryStream(List<Path> entries) {
        this.entries = entries;
    }

    @Override
    public synchronized Iterator<Path> iterator() {
        if (closed) {
            throw new IllegalStateException("DirectoryStream is closed");
        }
        if (iteratorReturned) {
            throw new IllegalStateException("Iterator already obtained");
        }
        iteratorReturned = true;
        Iterator<Path> it = entries.iterator();
        return new Iterator<Path>() {
            @Override
            public boolean hasNext() {
                return !closed && it.hasNext();
            }

            @Override
            public Path next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return it.next();
            }
        };
    }

    @Override
    public void close() {
        closed = true;
    }
}
