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
import java.nio.file.Paths;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.monflabs.filesystem.AbstractFileSystem;
import org.monflabs.filesystem.AbstractFileSystemProvider;
import org.monflabs.filesystem.AbstractPath;
import org.monflabs.filesystem.ListDirectoryStream;
import org.monflabs.filesystem.ReadOnlyByteChannel;

/**
 * FileSystemProvider for read-only ZIP file access.
 * Exposes ZIP contents as a navigable filesystem.
 */
public class ZipFileSystemProvider extends AbstractFileSystemProvider {

    public static final String SCHEME = "zip";
    public static final String ZIP_FILE_PARAM = "zipFile";
    /**
     * Environment parameter (a Number or a String): the largest entry a random-access
     * channel reads into memory. Files.newInputStream() streams entries of any size.
     */
    public static final String MAX_ENTRY_SIZE_PARAM = "maxEntrySize";
    /** The default {@link #MAX_ENTRY_SIZE_PARAM}: 256 MB. */
    public static final long DEFAULT_MAX_ENTRY_SIZE = 256L * 1024 * 1024;
    
    public ZipFileSystemProvider(boolean registered) {
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
        
        long maxEntrySize = maxEntrySize(env);

        // Open the ZIP file
        ZipFile zipFile = new ZipFile(zipPath.toFile());

        return new ZipFileSystem(this, uri, zipFile, maxEntrySize);
    }

    /**
     * The {@link #MAX_ENTRY_SIZE_PARAM} of an environment, or the default.
     */
    public static long maxEntrySize(Map<String, ?> env) {
        Object v = env != null ? env.get(MAX_ENTRY_SIZE_PARAM) : null;
        long max = DEFAULT_MAX_ENTRY_SIZE;
        if (v instanceof Number) {
            max = ((Number) v).longValue();
        } else if (v instanceof String) {
            max = Long.parseLong(((String) v).trim());
        }
        if (max < 0) {
            throw new IllegalArgumentException(MAX_ENTRY_SIZE_PARAM + " must not be negative: " + max);
        }
        return max;
    }

    /**
     * Refuse the options a read-only filesystem can't honour.
     */
    static void checkReadOnlyOptions(Set<? extends OpenOption> options) {
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
        
        // ZIP filesystem is read-only
        checkReadOnlyOptions(options);

        ZipEntry entry = fileEntry(fs, zipPath);

        // A random-access channel holds the entry in memory, up to the filesystem's
        // maxEntrySize (FileSystemException beyond)
        byte[] content = fs.readEntry(entry);

        return new ReadOnlyByteChannel(content);
    }

    private static ZipEntry fileEntry(ZipFileSystem fs, ZipPath path) throws IOException {
        ZipEntry entry = fs.getEntry(path);
        if (entry == null) {
            throw new NoSuchFileException(path.toString());
        }
        if (entry.isDirectory()) {
            throw new FileSystemException(path.toString(), null, "Is a directory");
        }
        return entry;
    }

    /**
     * A stream inflating the entry as it is read: an entry of any size can be read this way
     * (Files.newInputStream(), Files.copy(), Files.lines()...), never held in memory.
     */
    @Override
    public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        checkPath(path);
        checkReadOnlyOptions(Set.of(options));
        ZipPath zipPath = (ZipPath) toAbsolutePath(path);
        ZipFileSystem fs = zipPath.getFileSystem();
        return fs.openEntry(fileEntry(fs, zipPath));
    }
    
    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        checkPath(dir);
        ZipPath zipDir = (ZipPath) toAbsolutePath(dir);
        ZipFileSystem fs = (ZipFileSystem) zipDir.getFileSystem();
        
        ZipEntry entry = fs.getEntry(zipDir);
        if (entry == null) {
            throw new NoSuchFileException(dir.toString());
        }
        if (!entry.isDirectory()) {
            throw new NotDirectoryException(dir.toString());
        }
        
        // Get all entries in this directory, as dir.resolve(name) (Files.newDirectoryStream())
        List<Path> entries = fs.listDirectory((ZipPath) dir);
        
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

        // The key of the normalized path: "/a/../b.txt" and "/b.txt" are the same file, and
        // so are an implicit directory and its explicit entry
        return type.cast(new ZipFileAttributes(entry, "/" + ZipFileSystem.entryName(zipPath)));
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
     * BasicFileAttributes implementation for ZIP entries.
     */
    private static class ZipFileAttributes implements BasicFileAttributes {
        private final ZipEntry entry;
        private final String key;

        public ZipFileAttributes(ZipEntry entry, String key) {
            this.entry = entry;
            this.key = key;
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
            return key;
        }
    }
}
