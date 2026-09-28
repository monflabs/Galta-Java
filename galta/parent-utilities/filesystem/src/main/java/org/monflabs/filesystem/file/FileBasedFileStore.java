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
import java.nio.file.FileStore;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

/**
 * A FileStore implementation that delegates to java.io.File API.
 */
public class FileBasedFileStore extends FileStore {
    
    private final File root;
    
    public FileBasedFileStore(File file) {
        // Find the root for this file
        File current = file.getAbsoluteFile();
        while (current.getParentFile() != null) {
            current = current.getParentFile();
        }
        this.root = current;
    }
    
    @Override
    public String name() {
        return root.getAbsolutePath();
    }
    
    @Override
    public String type() {
        // Return a generic type since File API doesn't provide this
        return "file";
    }
    
    @Override
    public boolean isReadOnly() {
        // Check if the root can be written to
        return !root.canWrite();
    }
    
    @Override
    public long getTotalSpace() throws IOException {
        return root.getTotalSpace();
    }
    
    @Override
    public long getUsableSpace() throws IOException {
        return root.getUsableSpace();
    }
    
    @Override
    public long getUnallocatedSpace() throws IOException {
        return root.getFreeSpace();
    }
    
    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        // Basic support only
        return false;
    }
    
    @Override
    public boolean supportsFileAttributeView(String name) {
        // Basic support only
        return "basic".equals(name);
    }
    
    @Override
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        // Not supported
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
            default:
                throw new UnsupportedOperationException("Attribute not supported: " + attribute);
        }
    }
}
