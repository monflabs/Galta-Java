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
package org.monflabs.filesystem.path;

import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import org.monflabs.filesystem.AbstractPath;

/**
 * Path implementation for PathFileSystem.
 * Always uses "/" as separator, regardless of OS.
 */
public class PathPath extends AbstractPath {
    
    public PathPath(PathFileSystem fileSystem, String path) {
        super(fileSystem, path);
    }
    
    @Override
    public PathFileSystem getFileSystem() {
        return (PathFileSystem) fileSystem;
    }
    
    @Override
    protected String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        
        // Always use / as separator (force cross-platform)
        path = path.replace('\\', '/');
        
        // Collapse repeated separators ("a//b" is "a/b")
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        
        // Remove trailing separators (except for root)
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        
        return path;
    }
    
    @Override
    public URI toUri() {
        PathFileSystem pfs = getFileSystem();
        // Convert virtual path to OS path, then to URI
        java.nio.file.Path osPath = pfs.toOSPath(toString());
        return osPath.toUri();
    }
    
    @Override
    public Path toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }
        // Resolve relative paths against root
        return getFileSystem().getPath("/").resolve(this);
    }
    
    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        PathFileSystem pfs = getFileSystem();
        java.nio.file.Path osPath = pfs.toOSPath(toString());
        java.nio.file.Path realPath = osPath.toRealPath(options);
        java.nio.file.Path root = pfs.getRootPath();
        if (root != null) {
            // Relativize against the root's real path too: under a root reached through a link
            // (/tmp on macOS is /private/tmp) the result was "/../../private/tmp/..."
            java.nio.file.Path realRoot = root.toRealPath();
            if (!realPath.startsWith(realRoot)) {
                throw new java.nio.file.AccessDeniedException("Path escapes filesystem root: " + path);
            }
            String relative = realRoot.relativize(realPath).toString();
            if (!"/".equals(realPath.getFileSystem().getSeparator())) {
                relative = relative.replace(realPath.getFileSystem().getSeparator(), "/");
            }
            return new PathPath(pfs, "/" + relative);
        }
        return new PathPath(pfs, pfs.toVirtualPath(realPath));
    }
    
    @Override
    protected AbstractPath createPath(String path) {
        return new PathPath(getFileSystem(), path);
    }
    
    /**
     * Get the underlying OS path for this virtual path.
     */
    public java.nio.file.Path toOSPath() {
        return getFileSystem().toOSPath(toString());
    }
    
    /**
     * The underlying OS path, verified to stay inside the sandbox root (symbolic links included).
     */
    public java.nio.file.Path toOSPathChecked() throws IOException {
        java.nio.file.Path osPath = toOSPath();
        getFileSystem().checkSandbox(osPath);
        return osPath;
    }
}
