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
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.monflabs.filesystem.AbstractPath;

/**
 * Path implementation that delegates to java.io.File API.
 * Supports sandboxed root directory.
 */
public class FilePath extends AbstractPath {
    
    public FilePath(FileFileSystem fileSystem, String path) {
        super(fileSystem, path);
    }
    
    @Override
    public FileFileSystem getFileSystem() {
        return (FileFileSystem) super.getFileSystem();
    }
    
    @Override
    public URI toUri() {
        File root = getFileSystem().getRoot();
        if (root != null) {
            // For sandboxed filesystem, use the scheme with sandboxed path (encoded)
            try {
                // Normalized, so the URI never shows a ".." climbing above the root
                return new URI(getFileSystem().provider().getScheme(), "", toAbsolutePath().normalize().toString(), null, null);
            } catch (java.net.URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            return toFile().toURI();
        }
    }
    
    @Override
    public Path toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }
        File root = getFileSystem().getRoot();
        if (root != null) {
            // In sandboxed mode, make relative to root
            return new FilePath(getFileSystem(), separator + path);
        } else {
            // No sandbox, use File's absolute path
            return new FilePath(getFileSystem(), new File(path).getAbsolutePath());
        }
    }
    
    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        // Throws NoSuchFileException when the file does not exist, as the spec requires
        // (getCanonicalFile() did not)
        java.nio.file.Path real = toFile().toPath().toRealPath(options);
        
        File root = getFileSystem().getRoot();
        if (root != null) {
            java.nio.file.Path realRoot = root.toPath().toRealPath();
            if (!real.startsWith(realRoot)) {
                throw new java.nio.file.AccessDeniedException("Path escapes filesystem root: " + path);
            }
            // Return path relative to sandbox root
            java.nio.file.Path relative = realRoot.relativize(real);
            return new FilePath(getFileSystem(), separator + relative.toString());
        } else {
            return new FilePath(getFileSystem(), real.toString());
        }
    }
    
    @Override
    public File toFile() {
        File root = getFileSystem().getRoot();
        if (root != null) {
            // Sandboxed: resolve path relative to root. "." and ".." are resolved lexically
            // first so ".." can never climb above the root (like a chroot)
            String normalized = (isAbsolute() ? this : toAbsolutePath()).normalize().toString();
            if (normalized.isEmpty() || normalized.equals(separator)) {
                return root;
            }
            
            // Remove leading separator for relative resolution
            String relativePath = normalized;
            if (relativePath.startsWith(separator)) {
                relativePath = relativePath.substring(separator.length());
            }
            
            // Handle empty path after removing separator
            if (relativePath.isEmpty()) {
                return root;
            }
            
            return new File(root, relativePath);
        } else {
            // Not sandboxed: use path directly
            return new File(path);
        }
    }
    
    @Override
    protected AbstractPath createPath(String path) {
        return new FilePath(getFileSystem(), path);
    }
}
