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
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
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
 * FileSystemProvider for PathFileSystem.
 * 
 * Delegates to underlying NIO.2 filesystem but normalizes paths to always use "/".
 * With a root, every path is confined to it (see {@link Sandbox} for what the sandbox
 * does and does not protect against). Without one, a relative path is resolved against
 * "/", not against the working directory (unlike an unsandboxed FileFileSystem).
 * 
 * URI format: pathfs:///[identifier]
 * 
 * Environment parameters:
 * - "rootPath" (java.nio.file.Path): Root path for sandboxing (optional)
 * 
 * Example:
 * <pre>
 * Map&lt;String, Object&gt; env = new HashMap&lt;&gt;();
 * env.put("rootPath", Paths.get("/home/user/sandbox"));
 * 
 * PathFileSystemProvider provider = new PathFileSystemProvider();
 * FileSystem fs = provider.newFileSystem(URI.create("pathfs:///myfs"), env);
 * 
 * // Use filesystem with "/" separator on all platforms
 * Path file = fs.getPath("/docs/file.txt");
 * Files.write(file, "data".getBytes());
 * </pre>
 */
public class PathFileSystemProvider extends AbstractFileSystemProvider {
    
    public static final String SCHEME = "pathfs";
    public static final String ROOT_PARAM = "rootPath";
    
    public PathFileSystemProvider(boolean registered) {
    	super(registered);
    }

    @Override
    public String getScheme() {
        return SCHEME;
    }
    
    @Override
    protected AbstractFileSystem createFileSystem(URI uri, Map<String, ?> env) throws IOException {
        java.nio.file.Path rootPath = null;
        
        if (env != null) {
            Object rootObj = env.get(ROOT_PARAM);
            if (rootObj instanceof java.nio.file.Path) {
                rootPath = (java.nio.file.Path) rootObj;
            } else if (rootObj instanceof String) {
                rootPath = Paths.get((String) rootObj);
            }
        }
        
        // Verify root path exists if specified, and is a directory: a regular file as the root
        // made every path "/x" a child of a file
        if (rootPath != null && !Files.exists(rootPath)) {
            throw new IOException("Root path does not exist: " + rootPath);
        }
        if (rootPath != null && !Files.isDirectory(rootPath)) {
            throw new IOException("Root must be a directory: " + rootPath);
        }
        
        return new PathFileSystem(this, uri, rootPath);
    }
    
    @Override
    protected AbstractPath createPath(FileSystem fs, String path) {
        return new PathPath((PathFileSystem) fs, path);
    }
    
    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                                               FileAttribute<?>... attrs) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        
        PathPath pathPath = (PathPath) path;
        java.nio.file.Path osPath = pathPath.toOSPathChecked();

        try {
            return Files.newByteChannel(osPath, options, attrs);
        } catch (FileSystemException e) {
            throw translate(e, path, null);
        }
    }

    /**
     * The exception with the paths of this filesystem, not the host paths of a sandbox.
     */
    private static IOException translate(FileSystemException e, Path file, Path other) {
        if (((PathPath) file).getFileSystem().getRootPath() == null) {
            return e;
        }
        return Sandbox.withPaths(e, file, other);
    }
    
    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        checkPath(dir);
        PathPath pathPath = (PathPath) toAbsolutePath(dir);
        java.nio.file.Path osPath = pathPath.toOSPathChecked();

        // The entries are dir.resolve(name), as Files.newDirectoryStream() specifies (a
        // relative directory lists relative entries). The OS stream is closed even when the
        // filter throws, and an IOException from the filter reaches the caller
        List<Path> virtualPaths = new ArrayList<>();
        try (DirectoryStream<java.nio.file.Path> osStream = Files.newDirectoryStream(osPath)) {
            for (java.nio.file.Path osEntry : osStream) {
                Path virtualPathObj = dir.resolve(osEntry.getFileName().toString());
                if (filter.accept(virtualPathObj)) {
                    virtualPaths.add(virtualPathObj);
                }
            }
        } catch (FileSystemException e) {
            throw translate(e, dir, null);
        }
        
        return new ListDirectoryStream(virtualPaths);
    }
    
    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        checkPath(dir);
        dir = toAbsolutePath(dir);
        
        PathPath pathPath = (PathPath) dir;
        java.nio.file.Path osPath = pathPath.toOSPathChecked();

        try {
            Files.createDirectory(osPath, attrs);
        } catch (FileSystemException e) {
            throw translate(e, dir, null);
        }
    }
    
    @Override
    public void delete(Path path) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        
        PathPath pathPath = (PathPath) path;
        // Deleting a link deletes the link, never its target: only the parent is checked
        java.nio.file.Path osPath = pathPath.toOSPathChecked(false);

        try {
            Files.delete(osPath);
        } catch (FileSystemException e) {
            throw translate(e, path, null);
        }
    }
    
    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        checkPath(source);
        checkPath(target);
        source = toAbsolutePath(source);
        target = toAbsolutePath(target);
        
        PathPath sourcePath = (PathPath) source;
        PathPath targetPath = (PathPath) target;
        
        // With NOFOLLOW_LINKS, a link is copied as a link: it is not followed
        java.nio.file.Path osSource = sourcePath.toOSPathChecked(!Sandbox.noFollow((Object[]) options));
        java.nio.file.Path osTarget = targetPath.toOSPathChecked();

        try {
            Files.copy(osSource, osTarget, options);
        } catch (FileSystemException e) {
            throw translate(e, source, target);
        }
    }
    
    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        checkPath(source);
        checkPath(target);
        source = toAbsolutePath(source);
        target = toAbsolutePath(target);
        
        PathPath sourcePath = (PathPath) source;
        PathPath targetPath = (PathPath) target;
        
        // A move renames a link itself and replaces a target link without following it
        java.nio.file.Path osSource = sourcePath.toOSPathChecked(false);
        java.nio.file.Path osTarget = targetPath.toOSPathChecked(false);

        try {
            Files.move(osSource, osTarget, options);
        } catch (FileSystemException e) {
            throw translate(e, source, target);
        }
    }
    
    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        checkPath(path);
        if (path.equals(path2)) {
            return true;
        }
        // A path of another provider is another file (not a ProviderMismatchException)
        if (!(path2 instanceof PathPath) || path2.getFileSystem().provider() != this) {
            return false;
        }
        checkPath(path2);
        path = toAbsolutePath(path);
        path2 = toAbsolutePath(path2);
        
        PathPath pathPath1 = (PathPath) path;
        PathPath pathPath2 = (PathPath) path2;
        
        java.nio.file.Path osPath1 = pathPath1.toOSPathChecked();
        java.nio.file.Path osPath2 = pathPath2.toOSPathChecked();

        try {
            return Files.isSameFile(osPath1, osPath2);
        } catch (FileSystemException e) {
            throw translate(e, path, path2);
        }
    }
    
    @Override
    public boolean isHidden(Path path) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        
        PathPath pathPath = (PathPath) path;
        java.nio.file.Path osPath = pathPath.toOSPathChecked();
        
        return Files.isHidden(osPath);
    }
    
    @Override
    public FileStore getFileStore(Path path) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        
        PathPath pathPath = (PathPath) path;
        java.nio.file.Path osPath = pathPath.toOSPathChecked();

        try {
            return Files.getFileStore(osPath);
        } catch (FileSystemException e) {
            throw translate(e, path, null);
        }
    }
    
    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        
        PathPath pathPath = (PathPath) path;
        java.nio.file.Path osPath = pathPath.toOSPathChecked();
        
        // Check if file exists and is accessible
        if (!Files.exists(osPath)) {
            throw new NoSuchFileException(path.toString());
        }
        
        for (AccessMode mode : modes) {
            switch (mode) {
                case READ:
                    if (!Files.isReadable(osPath)) {
                        throw new AccessDeniedException(path.toString());
                    }
                    break;
                case WRITE:
                    if (!Files.isWritable(osPath)) {
                        throw new AccessDeniedException(path.toString());
                    }
                    break;
                case EXECUTE:
                    if (!Files.isExecutable(osPath)) {
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
        
        PathPath pathPath = (PathPath) path;
        // With NOFOLLOW_LINKS the link's own attributes are read, even for a link pointing
        // outside the root (Files.isSymbolicLink(), Files.walk()...)
        java.nio.file.Path osPath = pathPath.toOSPathChecked(!Sandbox.noFollow((Object[]) options));

        try {
            return Files.readAttributes(osPath, type, options);
        } catch (FileSystemException e) {
            throw translate(e, path, null);
        }
    }
    
    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options)
            throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        
        PathPath pathPath = (PathPath) path;
        java.nio.file.Path osPath = pathPath.toOSPathChecked(!Sandbox.noFollow((Object[]) options));

        try {
            return Files.readAttributes(osPath, attributes, options);
        } catch (FileSystemException e) {
            throw translate(e, path, null);
        }
    }
    
    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options)
            throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        
        PathPath pathPath = (PathPath) path;
        java.nio.file.Path osPath = pathPath.toOSPathChecked(!Sandbox.noFollow((Object[]) options));

        try {
            Files.setAttribute(osPath, attribute, value, options);
        } catch (FileSystemException e) {
            throw translate(e, path, null);
        }
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type,
                                                                  LinkOption... options) {
        if (type == BasicFileAttributeView.class) {
            return super.getFileAttributeView(path, type, options);
        }
        checkPath(path);
        PathPath p = (PathPath) toAbsolutePath(path);
        boolean follow = !Sandbox.noFollow((Object[]) options);
        // Any view of the host (posix, dos, owner...), the sandbox checked on every call
        return Sandbox.delegatedView(type, p.toOSPath(), () -> p.toOSPathChecked(follow), options);
    }
    
    @Override
    protected void setTimes(Path path, FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        java.nio.file.Path osPath = ((PathPath) path).toOSPathChecked();
        Files.getFileAttributeView(osPath, BasicFileAttributeView.class).setTimes(lastModifiedTime, lastAccessTime, createTime);
    }
}
