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
package org.monflabs.filesystem.zip;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.monflabs.filesystem.AbstractPath;

/**
 * Path implementation for ZIP filesystem.
 * Represents a path within a ZIP file.
 */
public class ZipPath extends AbstractPath {
    
    public ZipPath(ZipFileSystem fileSystem, String path) {
        super(fileSystem, path);
    }
    
    @Override
    public ZipFileSystem getFileSystem() {
        return (ZipFileSystem) super.getFileSystem();
    }
    
    @Override
    protected AbstractPath createPath(String path) {
        return new ZipPath(getFileSystem(), path);
    }
    
    @Override
    public URI toUri() {
        // zip:///dir/archive.zip!/path/to/entry: the archive identifies the filesystem
        // (a filesystem built without a URI used to give "zip:///!/entry")
        ZipFileSystem fs = getFileSystem();
        return buildUri(fs.getIdentityPath() + "!" + toAbsolutePath().toString());
    }
    
    @Override
    public Path toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }
        // Relative paths in ZIP are made absolute by prepending "/"
        return new ZipPath(getFileSystem(), separator + path);
    }
    
    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        // ZIP filesystem has no symlinks, so real path is just the normalized absolute path
        Path absolute = toAbsolutePath().normalize();
        
        // Verify the path exists
        if (getFileSystem().getEntry((ZipPath) absolute) == null) {
            throw new java.nio.file.NoSuchFileException(absolute.toString());
        }
        
        return absolute;
    }
    
    @Override
    public File toFile() {
        // Cannot convert ZIP entry to File
        throw new UnsupportedOperationException("ZIP paths cannot be converted to File");
    }
}
