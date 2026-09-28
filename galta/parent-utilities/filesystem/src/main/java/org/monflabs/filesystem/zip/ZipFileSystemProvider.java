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
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
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
import java.nio.file.Paths;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.monflabs.filesystem.AbstractFileSystem;
import org.monflabs.filesystem.AbstractFileSystemProvider;
import org.monflabs.filesystem.AbstractPath;

/**
 * FileSystemProvider for read-only ZIP file access.
 * Exposes ZIP contents as a navigable filesystem.
 */
public class ZipFileSystemProvider extends AbstractFileSystemProvider {

    public static final String SCHEME = "zip";
    public static final String ZIP_FILE_PARAM = "zipFile";
    
    public ZipFileSystemProvider(boolean registered) {
    	super(registered);
    }
    
    @Override
    public String getScheme() {
        return SCHEME;
    }

    @Override
    protected AbstractFileSystem createFileSystem(URI uri, Map<String, ?> env) throws IOException {
        // Get the ZIP file path
        Path zipPath = null;
        if (env != null && env.containsKey(ZIP_FILE_PARAM)) {
            Object zipObj = env.get(ZIP_FILE_PARAM);
            if (zipObj instanceof Path) {
                zipPath = (Path) zipObj;
            } else if (zipObj instanceof String) {
                zipPath = Paths.get((String) zipObj);
            }
        }
        
        if (zipPath == null) {
            throw new IllegalArgumentException("ZIP file path required: " + ZIP_FILE_PARAM);
        }
        
        if (!Files.exists(zipPath)) {
            throw new NoSuchFileException("ZIP file not found: " + zipPath);
        }
        
        if (!Files.isRegularFile(zipPath)) {
            throw new IOException("Not a regular file: " + zipPath);
        }
        
        // Open the ZIP file
        ZipFile zipFile = new ZipFile(zipPath.toFile());
        
        return new ZipFileSystem(this, uri, zipFile);
    }
    
    @Override
    protected AbstractPath createPath(FileSystem fs, String path) {
        return new ZipPath((ZipFileSystem) fs, path);
    }
    
    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                                               FileAttribute<?>... attrs) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        ZipPath zipPath = (ZipPath) path;
        ZipFileSystem fs = (ZipFileSystem) path.getFileSystem();
        
        // Check for write options - ZIP filesystem is read-only
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
        
        ZipEntry entry = fs.getEntry(zipPath);
        if (entry == null) {
            throw new NoSuchFileException(path.toString());
        }
        
        if (entry.isDirectory()) {
            throw new IOException("Cannot open directory as byte channel: " + path);
        }
        
        // Read entire entry into memory (ZIP entries must be fully read)
        byte[] content = fs.readEntry(entry);
        
        return new ReadOnlyByteChannel(content);
    }
    
    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        checkPath(dir);
        dir = toAbsolutePath(dir);
        ZipPath zipDir = (ZipPath) dir;
        ZipFileSystem fs = (ZipFileSystem) zipDir.getFileSystem();
        
        ZipEntry entry = fs.getEntry(zipDir);
        if (entry == null) {
            throw new NoSuchFileException(dir.toString());
        }
        if (!entry.isDirectory()) {
            throw new NotDirectoryException(dir.toString());
        }
        
        // Get all entries in this directory
        List<Path> entries = fs.listDirectory(zipDir);
        
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
        
        // Allow copying FROM zip to another filesystem
        if (target.getFileSystem() != source.getFileSystem()) {
            // Copy to different filesystem using InputStream
            try (InputStream in = Files.newInputStream(source)) {
                Files.copy(in, target, options);
            }
            return;
        }
        
        // Copying within ZIP filesystem not allowed (read-only)
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
        return false; // ZIP entries are not hidden
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
        ZipPath zipPath = (ZipPath) path;
        ZipFileSystem fs = (ZipFileSystem) path.getFileSystem();
        
        ZipEntry entry = fs.getEntry(zipPath);
        if (entry == null) {
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
        
        ZipPath zipPath = (ZipPath) path;
        ZipFileSystem fs = (ZipFileSystem) path.getFileSystem();
        
        ZipEntry entry = fs.getEntry(zipPath);
        if (entry == null) {
            throw new NoSuchFileException(path.toString());
        }
        
        return type.cast(new ZipFileAttributes(entry));
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
            throw new NonWritableChannelException();
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
            throw new NonWritableChannelException();
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
     * BasicFileAttributes implementation for ZIP entries.
     */
    private static class ZipFileAttributes implements BasicFileAttributes {
        private final ZipEntry entry;
        
        public ZipFileAttributes(ZipEntry entry) {
            this.entry = entry;
        }
        
        // Synthetic entries (the root, implicit directories) carry no times and a size of -1
        private static FileTime timeOrEpoch(FileTime t) {
            return t != null ? t : FileTime.fromMillis(0);
        }
        
        @Override
        public FileTime lastModifiedTime() {
            return timeOrEpoch(entry.getLastModifiedTime());
        }
        
        @Override
        public FileTime lastAccessTime() {
            return timeOrEpoch(entry.getLastAccessTime());
        }
        
        @Override
        public FileTime creationTime() {
            return timeOrEpoch(entry.getCreationTime());
        }
        
        @Override
        public boolean isRegularFile() {
            return !entry.isDirectory();
        }
        
        @Override
        public boolean isDirectory() {
            return entry.isDirectory();
        }
        
        @Override
        public boolean isSymbolicLink() {
            return false; // ZIP doesn't support symlinks
        }
        
        @Override
        public boolean isOther() {
            return false;
        }
        
        @Override
        public long size() {
            // Synthetic entries (the root, implicit directories) report -1
            return entry.isDirectory() ? 0 : Math.max(0, entry.getSize());
        }
        
        @Override
        public Object fileKey() {
            return entry.getName();
        }
    }
}
