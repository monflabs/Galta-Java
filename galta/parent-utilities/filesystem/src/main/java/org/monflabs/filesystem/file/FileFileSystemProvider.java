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
 * FileSystemProvider that delegates to java.io.File API.
 * Supports optional root directory for sandboxing (see {@link Sandbox} for what the
 * sandbox does and does not protect against).
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
                // Expand "~" and "~/..." to the user home. "~root" is a file name ("~root"
                // used to expand to "<home>root")
                String rootPath = root.getPath();
                if (rootPath.equals("~") || rootPath.startsWith("~/") || rootPath.startsWith("~" + File.separator)) {
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

    @Override
    public Path getPath(URI uri) {
        String path = uri.getPath();
        // An unsandboxed Windows path has a URI path of "/C:/dir/file"
        if (path != null && "\\".equals(File.separator) && path.length() >= 3 && path.charAt(0) == '/'
                && Character.isLetter(path.charAt(1)) && path.charAt(2) == ':') {
            checkUri(uri);
            return createPath(getFileSystem(uri), path.substring(1));
        }
        return super.getPath(uri);
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
            Sandbox.checkInside(path.toFile().toPath(), fs.getRoot().toPath(), fs.getRealRootPath(), path);
        }
    }

    /**
     * Validate a path whose last element is not followed (NOFOLLOW_LINKS, delete, move): only
     * its parent must stay within the root.
     */
    private void validateParent(FilePath path) throws IOException {
        FileFileSystem fs = (FileFileSystem) path.getFileSystem();
        if (fs.getRoot() != null) {
            Sandbox.checkParentInside(path.toFile().toPath(), fs.getRoot().toPath(), fs.getRealRootPath(), path);
        }
    }

    private void validate(FilePath path, boolean followLinks) throws IOException {
        if (followLinks) {
            validatePath(path);
        } else {
            validateParent(path);
        }
    }

    /**
     * The exception with the paths of this filesystem, not the host paths of a sandbox.
     */
    private static IOException translate(FileSystemException e, Path file, Path other) {
        FileFileSystem fs = (FileFileSystem) file.getFileSystem();
        if (fs.getRoot() == null) {
            return e;
        }
        return Sandbox.withPaths(e, file, other);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                                               FileAttribute<?>... attrs) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        validatePath((FilePath) path);
        File file = ((FilePath) path).toFile();
        try {
            return Files.newByteChannel(file.toPath(), options, attrs);
        } catch (FileSystemException e) {
            throw translate(e, path, null);
        }
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        checkPath(dir);
        Path absolute = toAbsolutePath(dir);
        validatePath((FilePath) absolute);
        File file = ((FilePath) absolute).toFile();

        if (!file.exists()) {
            throw new NoSuchFileException(dir.toString());
        }
        if (!file.isDirectory()) {
            throw new NotDirectoryException(dir.toString());
        }

        String[] names = file.list();
        if (names == null) {
            throw new IOException("Cannot list directory: " + dir);
        }

        List<Path> paths = new ArrayList<>();
        for (String name : names) {
            // dir.resolve(name), as Files.newDirectoryStream() specifies: a relative directory
            // lists relative entries
            Path p = dir.resolve(name);
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
        // Files.createDirectory() reports why it failed (NoSuchFileException for a missing
        // parent, AccessDeniedException...), where File.mkdir() only returned false
        try {
            Files.createDirectory(file.toPath(), attrs);
        } catch (FileSystemException e) {
            throw translate(e, dir, null);
        }
    }

    @Override
    public void delete(Path path) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        // Deleting a symbolic link deletes the link, never its target: only the parent has
        // to be inside the root, so a link pointing outside can be deleted
        validateParent((FilePath) path);
        File file = ((FilePath) path).toFile();
        // NoSuchFileException, DirectoryNotEmptyException, AccessDeniedException...
        try {
            Files.delete(file.toPath());
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
        // With NOFOLLOW_LINKS, a link is copied as a link: it is not followed
        validate((FilePath) source, !Sandbox.noFollow((Object[]) options));
        validatePath((FilePath) target);
        File sourceFile = ((FilePath) source).toFile();
        File targetFile = ((FilePath) target).toFile();

        // Files.copy() implements the spec: REPLACE_EXISTING, a non-empty target directory, ...
        try {
            Files.copy(sourceFile.toPath(), targetFile.toPath(), options);
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
        // A move renames the link itself, never its target, and replaces a target link
        // without following it
        validateParent((FilePath) source);
        validateParent((FilePath) target);
        File sourceFile = ((FilePath) source).toFile();
        File targetFile = ((FilePath) target).toFile();

        // Files.move() honours ATOMIC_MOVE and reports a failed cross-device move of a
        // non-empty directory; renameTo() + copy + an unchecked delete() did neither
        try {
            Files.move(sourceFile.toPath(), targetFile.toPath(), options);
        } catch (FileSystemException e) {
            throw translate(e, source, target);
        }
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
        checkPath(path2);
        path = toAbsolutePath(path);
        path2 = toAbsolutePath(path2);
        validatePath((FilePath) path);
        validatePath((FilePath) path2);
        try {
            return Files.isSameFile(((FilePath) path).toFile().toPath(), ((FilePath) path2).toFile().toPath());
        } catch (FileSystemException e) {
            throw translate(e, path, path2);
        }
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
        try {
            return new FileBasedFileStore(Files.getFileStore(file.toPath()));
        } catch (FileSystemException e) {
            throw translate(e, path, null);
        }
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

    /**
     * The attributes of the host file, of any type the host supports (basic, posix...). With
     * NOFOLLOW_LINKS, a link is not followed: its own attributes are read, even for a link
     * pointing outside the root (Files.isSymbolicLink(), Files.walk()...).
     */
    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type,
                                                             LinkOption... options) throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        validate((FilePath) path, !Sandbox.noFollow((Object[]) options));
        try {
            return Files.readAttributes(((FilePath) path).toFile().toPath(), type, options);
        } catch (FileSystemException e) {
            throw translate(e, path, null);
        }
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options)
            throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        validate((FilePath) path, !Sandbox.noFollow((Object[]) options));
        try {
            return Files.readAttributes(((FilePath) path).toFile().toPath(), attributes, options);
        } catch (FileSystemException e) {
            throw translate(e, path, null);
        }
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options)
            throws IOException {
        checkPath(path);
        path = toAbsolutePath(path);
        validate((FilePath) path, !Sandbox.noFollow((Object[]) options));
        try {
            Files.setAttribute(((FilePath) path).toFile().toPath(), attribute, value, options);
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
        FilePath p = (FilePath) toAbsolutePath(path);
        boolean follow = !Sandbox.noFollow((Object[]) options);
        // Any view of the host (posix, dos, owner...), the sandbox checked on every call
        return Sandbox.delegatedView(type, p.toFile().toPath(), () -> {
            validate(p, follow);
            return p.toFile().toPath();
        }, options);
    }
}
