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
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
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
 * FileSystemProvider that delegates to java.io.File API.
 * Supports optional root directory for sandboxing.
 */
public class FileFileSystemProvider extends AbstractFileSystemProvider {
    
    public static final String SCHEME = "file-impl";
    public static final String ROOT_PARAM = "root";
    
    public FileFileSystemProvider(boolean registered) {
    	super(registered);
    }
    
    @Override
    public String getScheme() {
        return SCHEME;
    }
    
    @Override
    protected AbstractFileSystem createFileSystem(URI uri, Map<String, ?> env) throws IOException {
        // Check if a root directory is specified
        File root = null;
        if (env != null && env.containsKey(ROOT_PARAM)) {
            Object rootObj = env.get(ROOT_PARAM);
            if (rootObj instanceof File) {
                root = (File) rootObj;
            } else if (rootObj instanceof String) {
                root = new File((String) rootObj);
            }
            
            if (root != null) {
                // Expand ~ to user home
                String rootPath = root.getPath();
                if (rootPath.startsWith("~")) {
                    rootPath = System.getProperty("user.home") + rootPath.substring(1);
                    root = new File(rootPath);
                }
                
                // Ensure root exists and is a directory
                if (!root.exists()) {
                    throw new IOException("Root directory does not exist: " + root);
                }
                if (!root.isDirectory()) {
                    throw new IOException("Root must be a directory: " + root);
                }
                
                // Get canonical path for security
                root = root.getCanonicalFile();
            }
        }
        
        return new FileFileSystem(this, uri, root);
    }
    
    @Override
    protected AbstractPath createPath(FileSystem fs, String path) {
        return new FilePath((FileFileSystem) fs, path);
    }
    
    /**
     * Validate that the resolved file is within the root (if a root is set).
     */
    private void validatePath(FilePath path) throws IOException {
        FileFileSystem fs = (FileFileSystem) path.getFileSystem();
        if (fs.getRoot() != null) {
            // Symbolic links are followed, dangling ones included: getCanonicalFile() returns a
            // dangling link's own path, so a link to a missing file outside passed the check and
            // CREATE then created that file outside the root
            Sandbox.checkInside(path.toFile().toPath(), fs.getRoot().toPath(), path);
        }
    }
    
    /**
     * Convert an absolute File path to a filesystem-relative path string.
     * For sandboxed filesystems, strips the root prefix.
     * For non-sandboxed filesystems, returns the absolute path.
     */
    private String toFileSystemPath(FileFileSystem fs, File file) {
        File root = fs.getRoot();
        if (root == null) {
            // Not sandboxed - use absolute path
            return file.getAbsolutePath();
        }
        
        // Sandboxed - convert to filesystem-relative path
        String absolutePath = file.getAbsolutePath();
        String rootPath = root.getAbsolutePath();
        
        if (absolutePath.equals(rootPath) || absolutePath.startsWith(rootPath + File.separator)) {
            String relativePath = absolutePath.substring(rootPath.length());
            
            // Ensure path starts with separator
            if (relativePath.isEmpty() || !relativePath.startsWith(fs.getSeparator())) {
                relativePath = fs.getSeparator() + relativePath;
            }
            
            return relativePath;
        }
        
        // Fallback: use absolute path (shouldn't happen if validation is correct)
        return absolutePath;
    }
    
    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                                               FileAttribute<?>... attrs) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        validatePath((FilePath) path);
        File file = ((FilePath) path).toFile();
        return Files.newByteChannel(file.toPath(), options, attrs);
    }
    
    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        checkPath(dir);
        dir = toAbsolutePath(dir);
        validatePath((FilePath) dir);
        File file = ((FilePath) dir).toFile();
        
        if (!file.exists()) {
            throw new NoSuchFileException(dir.toString());
        }
        if (!file.isDirectory()) {
            throw new NotDirectoryException(dir.toString());
        }
        
        File[] files = file.listFiles();
        if (files == null) {
            throw new IOException("Cannot list directory: " + dir);
        }
        
        FileFileSystem fs = (FileFileSystem) dir.getFileSystem();
        List<Path> paths = new ArrayList<>();
        for (File f : files) {
            // Convert absolute File path to filesystem-relative path
            String fsPath = toFileSystemPath(fs, f);
            Path p = new FilePath(fs, fsPath);
            // An IOException from the filter is the caller's error to see, not a reason to skip
            if (filter.accept(p)) {
                paths.add(p);
            }
        }
        
        return new ListDirectoryStream(paths);
    }
    
    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        checkPath(dir);
        dir = toAbsolutePath(dir);
        validatePath((FilePath) dir);
        File file = ((FilePath) dir).toFile();
        if (file.exists()) {
            throw new FileAlreadyExistsException(dir.toString());
        }
        if (!file.mkdir()) {
            throw new IOException("Failed to create directory: " + dir);
        }
    }
    
    @Override
    public void delete(Path path) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        validatePath((FilePath) path);
        File file = ((FilePath) path).toFile();
        if (!file.exists()) {
            throw new NoSuchFileException(path.toString());
        }
        
        // Check if it's a non-empty directory
        if (file.isDirectory()) {
            String[] contents = file.list();
            if (contents != null && contents.length > 0) {
                throw new DirectoryNotEmptyException(path.toString());
            }
        }
        
        if (!file.delete()) {
            throw new IOException("Failed to delete: " + path);
        }
    }
    
    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        checkPath(source);
        checkPath(target);
        source = toAbsolutePath(source);
        target = toAbsolutePath(target);
        validatePath((FilePath) source);
        validatePath((FilePath) target);
        File sourceFile = ((FilePath) source).toFile();
        File targetFile = ((FilePath) target).toFile();
        
        if (!sourceFile.exists()) {
            throw new NoSuchFileException(source.toString());
        }
        
        // Files.copy() implements the spec: REPLACE_EXISTING, a non-empty target directory, ...
        Files.copy(sourceFile.toPath(), targetFile.toPath(), options);
    }
    
    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        checkPath(source);
        checkPath(target);
        source = toAbsolutePath(source);
        target = toAbsolutePath(target);
        validatePath((FilePath) source);
        validatePath((FilePath) target);
        File sourceFile = ((FilePath) source).toFile();
        File targetFile = ((FilePath) target).toFile();
        
        if (!sourceFile.exists()) {
            throw new NoSuchFileException(source.toString());
        }
        
        // Files.move() honours ATOMIC_MOVE and reports a failed cross-device move of a
        // non-empty directory; renameTo() + copy + an unchecked delete() did neither
        Files.move(sourceFile.toPath(), targetFile.toPath(), options);
    }
    
    @Override
    protected void setTimes(Path path, FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        validatePath((FilePath) path);
        Files.getFileAttributeView(((FilePath) path).toFile().toPath(), BasicFileAttributeView.class)
            .setTimes(lastModifiedTime, lastAccessTime, createTime);
    }
    
    /**
     * Two paths locate the same file when the underlying files are the same, which a
     * lexical comparison misses (symbolic links, case-insensitive volumes, two
     * filesystems with different roots over the same directory).
     */
    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        checkPath(path);
        if (path.equals(path2)) {
            return true;
        }
        if (!(path2 instanceof FilePath) || path2.getFileSystem().provider() != this) {
            return false;
        }
        path = toAbsolutePath(path);
        path2 = toAbsolutePath(path2);
        validatePath((FilePath) path);
        validatePath((FilePath) path2);
        return Files.isSameFile(((FilePath) path).toFile().toPath(), ((FilePath) path2).toFile().toPath());
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        validatePath((FilePath) path);
        File file = ((FilePath) path).toFile();
        return file.isHidden();
    }
    
    @Override
    public FileStore getFileStore(Path path) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        validatePath((FilePath) path);
        File file = ((FilePath) path).toFile();
        // The store of the file itself (NoSuchFileException if it does not exist, as
        // Files.getFileStore() specifies), not of the root of the host filesystem
        return new FileBasedFileStore(Files.getFileStore(file.toPath()));
    }
    
    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        validatePath((FilePath) path);
        File file = ((FilePath) path).toFile();
        
        if (!file.exists()) {
            throw new NoSuchFileException(path.toString());
        }
        
        for (AccessMode mode : modes) {
            switch (mode) {
                case READ:
                    if (!file.canRead()) {
                        throw new AccessDeniedException(path.toString());
                    }
                    break;
                case WRITE:
                    if (!file.canWrite()) {
                        throw new AccessDeniedException(path.toString());
                    }
                    break;
                case EXECUTE:
                    if (!file.canExecute()) {
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
        validatePath((FilePath) path);
        if (type == BasicFileAttributes.class) {
            File file = ((FilePath) path).toFile();
            return Files.readAttributes(file.toPath(), type, options);
        }
        throw new UnsupportedOperationException("Attribute type not supported: " + type);
    }
}
