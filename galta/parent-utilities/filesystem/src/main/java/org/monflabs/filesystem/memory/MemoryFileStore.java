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
package org.monflabs.filesystem.memory;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

/**
 * FileStore implementation for in-memory filesystem.
 */
public class MemoryFileStore extends FileStore {
    
    private final MemoryFileSystem fileSystem;
    
    public MemoryFileStore(MemoryFileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }
    
    @Override
    public String name() {
        return "memory-store";
    }
    
    @Override
    public String type() {
        return "memory";
    }
    
    @Override
    public boolean isReadOnly() {
        return false;
    }
    
    /**
     * The files live in the Java heap: its maximum size bounds them (an arbitrary 1 GB was
     * reported, with a negative usable space past it).
     */
    @Override
    public long getTotalSpace() throws IOException {
        return Runtime.getRuntime().maxMemory();
    }

    /**
     * What the heap can still give to files, never negative.
     */
    @Override
    public long getUsableSpace() throws IOException {
        return Math.max(0, getTotalSpace() - fileSystem.getTotalUsedSpace());
    }
    
    @Override
    public long getUnallocatedSpace() throws IOException {
        return getUsableSpace();
    }
    
    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        // Same answer as supportsFileAttributeView("basic")
        return type == java.nio.file.attribute.BasicFileAttributeView.class;
    }
    
    @Override
    public boolean supportsFileAttributeView(String name) {
        return "basic".equals(name);
    }
    
    @Override
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        return null;
    }
    
    @Override
    public Object getAttribute(String attribute) throws IOException {
        switch (attribute) {
            case "totalSpace":
                return getTotalSpace();
            case "usableSpace":
                return getUsableSpace();
            case "unallocatedSpace":
                return getUnallocatedSpace();
            case "nodeCount":
                return fileSystem.getTotalNodeCount();
            case "usedSpace":
                return fileSystem.getTotalUsedSpace();
            default:
                throw new UnsupportedOperationException("Attribute not supported: " + attribute);
        }
    }
}
