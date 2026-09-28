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
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.monflabs.filesystem.AbstractFileSystem;
import org.monflabs.filesystem.AbstractFileSystemProvider;
import org.monflabs.filesystem.AbstractPath;

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
    
    public ResourceFileSystemProvider(boolean registered) {
    	super(registered);
    }
    
    @Override
    public String getScheme() {
        return SCHEME;
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
        
        return new ResourceFileSystem(this, uri, classLoader, basePath, manifestFile);
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
        
        // Check for write options - Resource filesystem is read-only
        for (OpenOption option : options) {
            if (option == StandardOpenOption.WRITE ||
                option == StandardOpenOption.APPEND ||
                option == StandardOpenOption.CREATE ||
                option == StandardOpenOption.CREATE_NEW ||
                option == StandardOpenOption.DELETE_ON_CLOSE ||
                option == StandardOpenOption.TRUNCATE_EXISTING) {
                throw new ReadOnlyFileSystemException();
            }
        }
        
        if (!fs.exists(resourcePath)) {
            throw new NoSuchFileException(path.toString());
        }
        
        if (fs.isDirectory(resourcePath)) {
            throw new IOException("Cannot open directory as byte channel: " + path);
        }
        
        // Read entire resource into memory
        byte[] content = fs.readResource(resourcePath);
        
        return new ReadOnlyByteChannel(content);
    }
    
    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        checkPath(dir);
        dir = toAbsolutePath(dir);
        ResourcePath resourceDir = (ResourcePath) dir;
        ResourceFileSystem fs = (ResourceFileSystem) resourceDir.getFileSystem();
        
        if (!fs.exists(resourceDir)) {
            // A missing directory used to list as empty
            throw new NoSuchFileException(dir.toString());
        }
        if (!fs.isDirectory(resourceDir)) {
            throw new NotDirectoryException(dir.toString());
        }
        
        // Get all entries in this directory
        List<Path> entries = fs.listDirectory(resourceDir);
        
        // Apply filter
        List<Path> filtered = new ArrayList<>();
        for (Path p : entries) {
            // An IOException from the filter is the caller's error to see, not a reason to skip
            if (filter.accept(p)) {
                filtered.add(p);
            }
        }
        
        return new DirectoryStream<Path>() {
            private boolean closed = false;
            
            @Override
            public Iterator<Path> iterator() {
                if (closed) {
                    throw new IllegalStateException("DirectoryStream is closed");
                }
                return filtered.iterator();
            }
            
            @Override
            public void close() {
                closed = true;
            }
        };
    }
    
    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        throw new ReadOnlyFileSystemException();
    }
    
    @Override
    public void delete(Path path) throws IOException {
        throw new ReadOnlyFileSystemException();
    }
    
    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        checkPath(source);
        source = toAbsolutePath(source);
        
        // Allow copying FROM resource to another filesystem
        if (target.getFileSystem() != source.getFileSystem()) {
            // Copy to different filesystem using InputStream
            try (InputStream in = Files.newInputStream(source)) {
                Files.copy(in, target, options);
            }
            return;
        }
        
        // Copying within Resource filesystem not allowed (read-only)
        throw new ReadOnlyFileSystemException();
    }
    
    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        throw new ReadOnlyFileSystemException();
    }
    
    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        if (path.getFileSystem() != path2.getFileSystem()) {
            return false;
        }
        return path.toAbsolutePath().equals(path2.toAbsolutePath());
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
        throw new ReadOnlyFileSystemException();
    }
    
    @Override
    protected void setTimes(Path path, FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        throw new ReadOnlyFileSystemException();
    }
    
    /**
     * Read-only byte channel that wraps a byte array.
     */
    private static class ReadOnlyByteChannel implements SeekableByteChannel {
        private final byte[] content;
        private int position;
        private boolean open = true;
        
        public ReadOnlyByteChannel(byte[] content) {
            this.content = content;
            this.position = 0;
        }
        
        @Override
        public int read(ByteBuffer dst) throws IOException {
            checkOpen();
            
            if (position >= content.length) {
                return -1;
            }
            
            int length = Math.min(dst.remaining(), content.length - position);
            dst.put(content, position, length);
            position += length;
            return length;
        }
        
        @Override
        public int write(ByteBuffer src) throws IOException {
            throw new java.nio.channels.NonWritableChannelException();
        }
        
        @Override
        public long position() throws IOException {
            checkOpen();
            return position;
        }
        
        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            checkOpen();
            if (newPosition < 0) {
                throw new IllegalArgumentException("Negative position");
            }
            this.position = (int) newPosition;
            return this;
        }
        
        @Override
        public long size() throws IOException {
            checkOpen();
            return content.length;
        }
        
        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            throw new java.nio.channels.NonWritableChannelException();
        }
        
        @Override
        public boolean isOpen() {
            return open;
        }
        
        @Override
        public void close() {
            open = false;
        }
        
        private void checkOpen() throws IOException {
            if (!open) {
                throw new IOException("Channel is closed");
            }
        }
    }
    
    /**
     * BasicFileAttributes implementation for resources.
     */
    private static class ResourceFileAttributes implements BasicFileAttributes {
        private final String key;
        private final boolean directory;
        private final long size;
        
        // A snapshot, like the JDK's attributes: size() used to read the whole resource on every call
        public ResourceFileAttributes(ResourceFileSystem fs, ResourcePath path) {
            this.key = path.toString();
            this.directory = fs.isDirectory(path);
            this.size = directory ? 0 : fs.resourceSize(path);
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
            return size;
        }
        
        @Override
        public Object fileKey() {
            return key;
        }
    }
}
