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
import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.monflabs.filesystem.AbstractFileSystem;
import org.monflabs.filesystem.AbstractFileSystemProvider;
import org.monflabs.filesystem.AbstractPath;
import org.monflabs.filesystem.ListDirectoryStream;
import org.monflabs.filesystem.Sandbox;

/**
 * FileSystemProvider that delegates to another Path as root.
 * This allows creating a sandboxed view of any filesystem.
 */
public class PathDelegatingFileSystemProvider extends AbstractFileSystemProvider {
    
    public static final String SCHEME = "path-delegate";
    
    public static final String ROOT_PATH_PARAM = "rootPath";
    
    public PathDelegatingFileSystemProvider(boolean registered) {
    	super(registered);
    }
    
    @Override
    public String getScheme() {
        return SCHEME;
    }
    
    @Override
    protected AbstractFileSystem createFileSystem(URI uri, Map<String, ?> env) throws IOException {
        // Root path is required
        if (env == null || !env.containsKey(ROOT_PATH_PARAM)) {
            throw new IllegalArgumentException("Root path must be specified via '" + ROOT_PATH_PARAM + "' parameter");
        }
        
        Object rootObj = env.get(ROOT_PATH_PARAM);
        if (!(rootObj instanceof Path)) {
            throw new IllegalArgumentException("Root path must be a Path instance");
        }
        
        Path rootPath = (Path) rootObj;
        
        // Verify root exists and is a directory
        if (!Files.exists(rootPath)) {
            throw new NoSuchFileException(rootPath.toString());
        }
        if (!Files.isDirectory(rootPath)) {
            throw new NotDirectoryException(rootPath.toString());
        }
        
        // Get real path for security
        rootPath = rootPath.toRealPath();
        
        return new PathDelegatingFileSystem(this, uri, rootPath);
    }
    
    @Override
    protected AbstractPath createPath(FileSystem fs, String path) {
        return new PathDelegatingPath((PathDelegatingFileSystem) fs, path);
    }
    
    /**
     * Validate that the resolved path is within the root.
     */
    private void validatePath(PathDelegatingPath path) throws IOException {
        PathDelegatingFileSystem fs = (PathDelegatingFileSystem) path.getFileSystem();
        // Lexical check, then symbolic links followed (dangling ones included)
        Sandbox.checkInside(path.toDelegatePath(), fs.getRootPath(), path);
    }
    
    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                                               FileAttribute<?>... attrs) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        PathDelegatingPath delPath = (PathDelegatingPath) path;
        
        // Validate BEFORE any side effect: opening with CREATE/TRUNCATE_EXISTING would
        // otherwise create or truncate a file outside the sandbox before the check ran
        validatePath(delPath);
        
        // CREATE creates the file, never its missing parents: that is NoSuchFileException
        Path delegatePath = delPath.toDelegatePath();
        return Files.newByteChannel(delegatePath, options, attrs);
    }
    
    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        checkPath(dir);
        dir = toAbsolutePath(dir);
        PathDelegatingPath delPath = (PathDelegatingPath) dir;
        validatePath(delPath);
        
        Path delegatePath = delPath.toDelegatePath();
        
        if (!Files.exists(delegatePath)) {
            throw new NoSuchFileException(dir.toString());
        }
        if (!Files.isDirectory(delegatePath)) {
            throw new NotDirectoryException(dir.toString());
        }
        
        List<Path> paths = new ArrayList<>();
        try (DirectoryStream<Path> delegateStream = Files.newDirectoryStream(delegatePath)) {
            for (Path delegateEntry : delegateStream) {
                // Convert back to our path
                String fileName = delegateEntry.getFileName().toString();
                Path entry = dir.resolve(fileName);
                
                // An IOException from the filter is the caller's error to see, not a reason to skip
                if (filter.accept(entry)) {
                    paths.add(entry);
                }
            }
        }
        
        return new ListDirectoryStream(paths);
    }
    
    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        checkPath(dir);
        dir = toAbsolutePath(dir);
        PathDelegatingPath delPath = (PathDelegatingPath) dir;
        validatePath(delPath);
        Path delegatePath = delPath.toDelegatePath();
        
        if (Files.exists(delegatePath)) {
            throw new FileAlreadyExistsException(dir.toString());
        }
        
        Files.createDirectory(delegatePath, attrs);
    }
    
    @Override
    public void delete(Path path) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        PathDelegatingPath delPath = (PathDelegatingPath) path;
        validatePath(delPath);
        
        Path delegatePath = delPath.toDelegatePath();
        
        if (!Files.exists(delegatePath)) {
            throw new NoSuchFileException(path.toString());
        }
        
        Files.delete(delegatePath);
    }
    
    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        checkPath(source);
        checkPath(target);
        source = toAbsolutePath(source);
        target = toAbsolutePath(target);
        PathDelegatingPath srcPath = (PathDelegatingPath) source;
        PathDelegatingPath tgtPath = (PathDelegatingPath) target;
        
        validatePath(srcPath);
        validatePath(tgtPath);
        
        Path delegateSrc = srcPath.toDelegatePath();
        Path delegateTgt = tgtPath.toDelegatePath();
        
        if (!Files.exists(delegateSrc)) {
            throw new NoSuchFileException(source.toString());
        }
        
        Files.copy(delegateSrc, delegateTgt, options);
    }
    
    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        checkPath(source);
        checkPath(target);
        source = toAbsolutePath(source);
        target = toAbsolutePath(target);
        PathDelegatingPath srcPath = (PathDelegatingPath) source;
        PathDelegatingPath tgtPath = (PathDelegatingPath) target;
        
        validatePath(srcPath);
        validatePath(tgtPath);
        
        Path delegateSrc = srcPath.toDelegatePath();
        Path delegateTgt = tgtPath.toDelegatePath();
        
        if (!Files.exists(delegateSrc)) {
            throw new NoSuchFileException(source.toString());
        }
        
        Files.move(delegateSrc, delegateTgt, options);
    }
    
    @Override
    public boolean isHidden(Path path) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        PathDelegatingPath delPath = (PathDelegatingPath) path;
        validatePath(delPath);
        
        return Files.isHidden(delPath.toDelegatePath());
    }
    
    /**
     * Two paths locate the same file when the delegate files are the same, which a
     * lexical comparison misses (symbolic links, case-insensitive volumes...).
     */
    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        checkPath(path);
        if (path.equals(path2)) {
            return true;
        }
        if (!(path2 instanceof PathDelegatingPath) || path2.getFileSystem().provider() != this) {
            return false;
        }
        PathDelegatingPath p1 = (PathDelegatingPath) toAbsolutePath(path);
        PathDelegatingPath p2 = (PathDelegatingPath) toAbsolutePath(path2);
        validatePath(p1);
        validatePath(p2);
        return Files.isSameFile(p1.toDelegatePath(), p2.toDelegatePath());
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        PathDelegatingPath delPath = (PathDelegatingPath) path;
        validatePath(delPath);
        
        return Files.getFileStore(delPath.toDelegatePath());
    }
    
    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        PathDelegatingPath delPath = (PathDelegatingPath) path;
        validatePath(delPath);
        
        Path delegatePath = delPath.toDelegatePath();
        
        if (!Files.exists(delegatePath)) {
            throw new NoSuchFileException(path.toString());
        }
        
        // Check each mode
        for (AccessMode mode : modes) {
            switch (mode) {
                case READ:
                    if (!Files.isReadable(delegatePath)) {
                        throw new AccessDeniedException(path.toString());
                    }
                    break;
                case WRITE:
                    if (!Files.isWritable(delegatePath)) {
                        throw new AccessDeniedException(path.toString());
                    }
                    break;
                case EXECUTE:
                    if (!Files.isExecutable(delegatePath)) {
                        throw new AccessDeniedException(path.toString());
                    }
                    break;
            }
        }
    }
    
    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type,
                                                             LinkOption... options) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        PathDelegatingPath delPath = (PathDelegatingPath) path;
        validatePath(delPath);
        
        return Files.readAttributes(delPath.toDelegatePath(), type, options);
    }
    
    @Override
    protected void setTimes(Path path, FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        PathDelegatingPath delPath = (PathDelegatingPath) path;
        validatePath(delPath);
        BasicFileAttributeView view = Files.getFileAttributeView(delPath.toDelegatePath(), BasicFileAttributeView.class);
        if (view == null) {
            throw new UnsupportedOperationException("Setting file times is not supported by the delegate filesystem");
        }
        view.setTimes(lastModifiedTime, lastAccessTime, createTime);
    }
}
