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
package org.monflabs.filesystem.file;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

/**
 * The FileStore of a file, delegating to the platform's own FileStore
 * ({@link Files#getFileStore(Path)}). Walking up to the filesystem root made the store
 * report the root volume - read-only and with the wrong sizes for a file on another mount.
 */
public class FileBasedFileStore extends FileStore {

    private final FileStore delegate;

    /**
     * The store of a file, or of its nearest existing ancestor when it does not exist.
     * @throws UncheckedIOException if no store can be determined
     */
    public FileBasedFileStore(File file) {
        this(storeOf(file));
    }

    public FileBasedFileStore(FileStore delegate) {
        this.delegate = delegate;
    }

    private static FileStore storeOf(File file) {
        Path p = file.getAbsoluteFile().toPath();
        while (p != null && !Files.exists(p)) {
            p = p.getParent();
        }
        if (p == null) {
            p = file.getAbsoluteFile().toPath().getRoot();
        }
        try {
            return Files.getFileStore(p);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String type() {
        return delegate.type();
    }

    @Override
    public boolean isReadOnly() {
        return delegate.isReadOnly();
    }

    @Override
    public long getTotalSpace() throws IOException {
        return delegate.getTotalSpace();
    }

    @Override
    public long getUsableSpace() throws IOException {
        return delegate.getUsableSpace();
    }

    @Override
    public long getUnallocatedSpace() throws IOException {
        return delegate.getUnallocatedSpace();
    }

    @Override
    public long getBlockSize() throws IOException {
        return delegate.getBlockSize();
    }

    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        return delegate.supportsFileAttributeView(type);
    }

    @Override
    public boolean supportsFileAttributeView(String name) {
        return delegate.supportsFileAttributeView(name);
    }

    @Override
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        return delegate.getFileStoreAttributeView(type);
    }

    @Override
    public Object getAttribute(String attribute) throws IOException {
        return delegate.getAttribute(attribute);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
