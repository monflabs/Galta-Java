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
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.monflabs.filesystem.AbstractPath;

/**
 * Path implementation for in-memory filesystem.
 */
public class MemoryPath extends AbstractPath {
    
    public MemoryPath(MemoryFileSystem fileSystem, String path) {
        super(fileSystem, path);
    }
    
    @Override
    public URI toUri() {
        // With the authority of the filesystem's URI: "memory://tenant1/a.txt" used to come
        // out as "memory:///a.txt", the URI of a file of another filesystem
        return buildUri(toAbsolutePath().toString());
    }
    
    @Override
    public Path toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }
        // In memory filesystem, make all paths absolute with /
        return new MemoryPath((MemoryFileSystem) fileSystem, separator + path);
    }
    
    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        MemoryFileNode node = ((MemoryFileSystem) fileSystem).getNode(this);
        if (node == null) {
            throw new java.nio.file.NoSuchFileException(path);
        }
        return toAbsolutePath().normalize();
    }
    
    @Override
    public File toFile() {
        throw new UnsupportedOperationException("In-memory paths cannot be converted to File");
    }
    
    @Override
    protected AbstractPath createPath(String path) {
        return new MemoryPath((MemoryFileSystem) fileSystem, path);
    }
    
    @Override
    public boolean isAbsolute() {
        return path.startsWith(separator);
    }
}
