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
package org.monflabs.filesystem.resources;
import java.io.IOException;
import java.io.InputStream;
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
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardOpenOption;
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
import org.monflabs.filesystem.ReadOnlyByteChannel;
import org.monflabs.filesystem.zip.ZipFileSystemProvider;

/**
 * FileSystemProvider for read-only access to classpath resources.
 * Uses a manifest file to enumerate available resources.
 */
public class ResourceFileSystemProvider extends AbstractFileSystemProvider {
	
    public static final String SCHEME = "resource";
    public static final String CLASSLOADER_PARAM = "classLoader";
    public static final String BASE_PATH_PARAM = "basePath";
    public static final String MANIFEST_FILE_PARAM = "manifestFile";
    public static final String DEFAULT_MANIFEST_FILE = "resources.manifest";
    /**
     * Environment parameter (a Number or a String): the largest resource a random-access
     * channel reads into memory. Files.newInputStream() streams resources of any size.
     */
    public static final String MAX_ENTRY_SIZE_PARAM = ZipFileSystemProvider.MAX_ENTRY_SIZE_PARAM;
    /** The default {@link #MAX_ENTRY_SIZE_PARAM}: 256 MB. */
    public static final long DEFAULT_MAX_ENTRY_SIZE = ZipFileSystemProvider.DEFAULT_MAX_ENTRY_SIZE;
    
    public ResourceFileSystemProvider(boolean registered) {
    	super(registered);
    }
    
    @Override
    public String getScheme() {
        return SCHEME;
    }

    // "scheme:///<filesystem>!/<path>": see toUri()
    @Override
    protected boolean isIdentityInPath() {
        return true;
    }
    
    @Override
    protected AbstractFileSystem createFileSystem(URI uri, Map<String, ?> env) throws IOException {
        // Get ClassLoader (default to context classloader)
        ClassLoader classLoader = null;
        if (env != null && env.containsKey(CLASSLOADER_PARAM)) {
            Object cl = env.get(CLASSLOADER_PARAM);
            if (cl instanceof ClassLoader) {
                classLoader = (ClassLoader) cl;
            }
        }
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        if (classLoader == null) {
            classLoader = ResourceFileSystemProvider.class.getClassLoader();
        }
        
        // Get base path (default to empty)
        String basePath = "";
        if (env != null && env.containsKey(BASE_PATH_PARAM)) {
            Object bp = env.get(BASE_PATH_PARAM);
            if (bp instanceof String) {
                basePath = (String) bp;
                // Normalize base path
                if (!basePath.isEmpty() && !basePath.endsWith("/")) {
                    basePath = basePath + "/";
                }
            }
        }
        
        // Get manifest file name (default to resources.manifest)
        String manifestFile = DEFAULT_MANIFEST_FILE;
        if (env != null && env.containsKey(MANIFEST_FILE_PARAM)) {
            Object mf = env.get(MANIFEST_FILE_PARAM);
            if (mf instanceof String) {
                manifestFile = (String) mf;
            }
        }
        
        return new ResourceFileSystem(this, uri, classLoader, basePath, manifestFile, ZipFileSystemProvider.maxEntrySize(env));
    }
    
    @Override
    protected AbstractPath createPath(FileSystem fs, String path) {
        return new ResourcePath((ResourceFileSystem) fs, path);
    }
    
    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                                               FileAttribute<?>... attrs) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        ResourcePath resourcePath = (ResourcePath) path;
        ResourceFileSystem fs = (ResourceFileSystem) path.getFileSystem();
        
        // Resource filesystem is read-only
        checkReadOnlyOptions(options);
        checkFile(fs, resourcePath);

        // A random-access channel holds the resource in memory, up to the filesystem's
        // maxEntrySize (FileSystemException beyond)
        byte[] content = fs.readResource(resourcePath);

        return new ReadOnlyByteChannel(content);
    }

    private static void checkReadOnlyOptions(Set<? extends OpenOption> options) {
        for (OpenOption option : options) {
            if (option == null) {
                throw new NullPointerException();
            }
            if (option == StandardOpenOption.WRITE ||
                option == StandardOpenOption.APPEND ||
                option == StandardOpenOption.CREATE ||
                option == StandardOpenOption.CREATE_NEW ||
                option == StandardOpenOption.DELETE_ON_CLOSE ||
                option == StandardOpenOption.TRUNCATE_EXISTING) {
                throw new ReadOnlyFileSystemException();
            }
            if (!(option instanceof StandardOpenOption) && option != LinkOption.NOFOLLOW_LINKS) {
                throw new UnsupportedOperationException(option + " not supported");
            }
        }
    }

    private static void checkFile(ResourceFileSystem fs, ResourcePath path) throws IOException {
        if (!fs.exists(path)) {
            throw new NoSuchFileException(path.toString());
        }
        if (fs.isDirectory(path)) {
            throw new FileSystemException(path.toString(), null, "Is a directory");
        }
    }

    /**
     * A stream over the resource as it is read: a resource of any size can be read this way
     * (Files.newInputStream(), Files.copy(), Files.lines()...), never held in memory.
     */
    @Override
    public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        checkPath(path);
        checkReadOnlyOptions(Set.of(options));
        ResourcePath resourcePath = (ResourcePath) toAbsolutePath(path);
        ResourceFileSystem fs = resourcePath.getFileSystem();
        checkFile(fs, resourcePath);
        return fs.openResource(resourcePath);
    }
    
    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        checkPath(dir);
        ResourcePath resourceDir = (ResourcePath) toAbsolutePath(dir);
        ResourceFileSystem fs = (ResourceFileSystem) resourceDir.getFileSystem();
        
        if (!fs.exists(resourceDir)) {
            // A missing directory used to list as empty
            throw new NoSuchFileException(dir.toString());
        }
        if (!fs.isDirectory(resourceDir)) {
            throw new NotDirectoryException(dir.toString());
        }
        
        // Get all entries in this directory, as dir.resolve(name) (Files.newDirectoryStream())
        List<Path> entries = fs.listDirectory((ResourcePath) dir);
        
        // Apply filter
        List<Path> filtered = new ArrayList<>();
        for (Path p : entries) {
            // An IOException from the filter is the caller's error to see, not a reason to skip
            if (filter.accept(p)) {
                filtered.add(p);
            }
        }
        
        return new ListDirectoryStream(filtered);
    }
    
    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        checkPath(dir);
        throw new ReadOnlyFileSystemException();
    }

    @Override
    public void delete(Path path) throws IOException {
        checkPath(path);
        throw new ReadOnlyFileSystemException();
    }
    
    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        checkPath(source);
        checkPath(target);
        // Out to another resource filesystem: ReadOnlyFileSystemException
        copyOutOfReadOnly(source, target, options);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        checkPath(source);
        throw new ReadOnlyFileSystemException();
    }
    
    @Override
    public boolean isHidden(Path path) throws IOException {
        checkPath(path);
        return false; // Resources are not hidden
    }
    
    @Override
    public FileStore getFileStore(Path path) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        return path.getFileSystem().getFileStores().iterator().next();
    }
    
    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        ResourcePath resourcePath = (ResourcePath) path;
        ResourceFileSystem fs = (ResourceFileSystem) path.getFileSystem();
        
        if (!fs.exists(resourcePath)) {
            throw new NoSuchFileException(path.toString());
        }
        
        // Check for write access - not allowed
        for (AccessMode mode : modes) {
            if (mode == AccessMode.WRITE) {
                throw new AccessDeniedException(path.toString(), null, "Read-only filesystem");
            }
        }
    }
    
    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type,
                                                             LinkOption... options) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);

        if (type != BasicFileAttributes.class) {
            throw new UnsupportedOperationException("Only BasicFileAttributes supported");
        }
        
        ResourcePath resourcePath = (ResourcePath) path;
        ResourceFileSystem fs = (ResourceFileSystem) path.getFileSystem();
        
        if (!fs.exists(resourcePath)) {
            throw new NoSuchFileException(path.toString());
        }
        
        return type.cast(new ResourceFileAttributes(fs, resourcePath));
    }
    
    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options)
            throws IOException {
        checkPath(path);
        throw new ReadOnlyFileSystemException();
    }

    @Override
    protected void setTimes(Path path, FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        checkPath(path);
        throw new ReadOnlyFileSystemException();
    }
    
    /**
     * BasicFileAttributes implementation for resources.
     */
    private static class ResourceFileAttributes implements BasicFileAttributes {
        private final ResourceFileSystem fs;
        private final ResourcePath path;
        private final String key;
        private final boolean directory;

        // The size is only computed when asked for (it may open the resource), and the
        // filesystem caches it: reading the attributes used to fetch it every time
        public ResourceFileAttributes(ResourceFileSystem fs, ResourcePath path) {
            this.fs = fs;
            this.path = path;
            // The normalized path: "/a/../b.txt" and "/b.txt" are the same file
            this.key = "/" + fs.normalizePath(path.toString());
            this.directory = fs.isDirectory(path);
        }
        
        @Override
        public FileTime lastModifiedTime() {
            return FileTime.fromMillis(0); // Not available for resources
        }
        
        @Override
        public FileTime lastAccessTime() {
            return FileTime.fromMillis(0); // Not available for resources
        }
        
        @Override
        public FileTime creationTime() {
            return FileTime.fromMillis(0); // Not available for resources
        }
        
        @Override
        public boolean isRegularFile() {
            return !directory;
        }
        
        @Override
        public boolean isDirectory() {
            return directory;
        }
        
        @Override
        public boolean isSymbolicLink() {
            return false; // Resources don't support symlinks
        }
        
        @Override
        public boolean isOther() {
            return false;
        }
        
        @Override
        public long size() {
            return directory ? 0 : fs.resourceSize(path);
        }
        
        @Override
        public Object fileKey() {
            return key;
        }
    }
}
