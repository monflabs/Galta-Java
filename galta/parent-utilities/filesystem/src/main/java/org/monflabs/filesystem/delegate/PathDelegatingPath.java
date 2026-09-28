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
package org.monflabs.filesystem.delegate;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.monflabs.filesystem.AbstractPath;

/**
 * Path implementation that delegates to another Path.
 */
public class PathDelegatingPath extends AbstractPath {
    
    public PathDelegatingPath(PathDelegatingFileSystem fileSystem, String path) {
        super(fileSystem, path);
    }
    
    @Override
    public PathDelegatingFileSystem getFileSystem() {
        return (PathDelegatingFileSystem) super.getFileSystem();
    }
    
    /**
     * Convert this path to the delegate path in the underlying filesystem.
     */
    public Path toDelegatePath() {
        Path rootPath = getFileSystem().getRootPath();
        
        // Resolve "." and ".." lexically first, so that ".." can never climb above the
        // sandbox root (like a chroot, "/../x" is "/x")
        String normalized = (isAbsolute() ? this : toAbsolutePath()).normalize().toString();
        
        if (normalized.isEmpty() || normalized.equals(separator)) {
            return rootPath;
        }
        
        // Remove leading separator for relative resolution
        String relativePath = normalized;
        if (relativePath.startsWith(separator)) {
            relativePath = relativePath.substring(separator.length());
        }
        
        // Handle empty path after removing separator
        if (relativePath.isEmpty()) {
            return rootPath;
        }
        
        return rootPath.resolve(relativePath);
    }
    
    @Override
    public URI toUri() {
        // Return URI with our scheme; the multi-argument constructor encodes spaces, '#', '%'...
        try {
            return new URI(getFileSystem().provider().getScheme(), "", toUriPath(toAbsolutePath()), null, null);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    @Override
    public Path toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }
        // In delegated filesystem, make relative paths absolute
        return new PathDelegatingPath(getFileSystem(), separator + path);
    }
    
    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        Path delegatePath = toDelegatePath();
        Path rootPath = getFileSystem().getRootPath();
        
        // Get real paths
        Path realDelegate = delegatePath.toRealPath(options);
        Path realRoot = rootPath.toRealPath(options);
        
        // Validate we're within the sandbox
        if (!realDelegate.startsWith(realRoot)) {
            throw new IOException("Path escapes filesystem root: " + path);
        }
        
        // Return path relative to sandbox root
        Path relativePath = realRoot.relativize(realDelegate);
        String relativeStr = relativePath.toString();
        
        if (relativeStr.isEmpty()) {
            return new PathDelegatingPath(getFileSystem(), separator);
        }
        
        // Normalize separators
        relativeStr = relativeStr.replace(
            realRoot.getFileSystem().getSeparator(), 
            separator
        );
        
        return new PathDelegatingPath(getFileSystem(), separator + relativeStr);
    }
    
    @Override
    public File toFile() {
        throw new UnsupportedOperationException(
            "Path-delegating paths cannot be converted to File. " +
            "Use toDelegatePath() to get the underlying path."
        );
    }
    
    @Override
    protected AbstractPath createPath(String path) {
        return new PathDelegatingPath(getFileSystem(), path);
    }
    
    @Override
    public String toString() {
        return path;
    }
}
