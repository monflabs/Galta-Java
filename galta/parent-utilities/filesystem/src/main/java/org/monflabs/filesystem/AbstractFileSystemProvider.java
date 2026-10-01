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
package org.monflabs.filesystem;
import java.io.IOException;
import java.net.URI;
import java.nio.file.CopyOption;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base FileSystemProvider with common functionality for different implementations.
 */
public abstract class AbstractFileSystemProvider extends FileSystemProvider {
    
	protected boolean registered;
    // Keyed by fileSystemKey(uri). Every access goes through synchronized(fileSystems),
    // including removeFileSystem()
    protected final Map<String, AbstractFileSystem> fileSystems = new HashMap<>();
    
    protected AbstractFileSystemProvider(boolean registered) {
    	this.registered = registered;
    }
    
    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		checkUri(uri);
		if(registered) {
			synchronized (fileSystems) {
		        String key = fileSystemKey(uri);
		        if (fileSystems.containsKey(key)) {
		            throw new FileSystemAlreadyExistsException();
		        }
		        AbstractFileSystem fs = createFileSystem(uri, env);
		        fileSystems.put(key, fs);
		        return fs;
			}
		} else {
	        return createFileSystem(uri, env);
		}
    }

    /**
     * Resolve a path to an absolute path, the way the path itself does: most filesystems
     * resolve a relative path against their root, an unsandboxed file filesystem against the
     * working directory. Resolving against "/" here made Files.exists(getPath("pom.xml"))
     * disagree with getPath("pom.xml").toAbsolutePath().
     */
    protected Path toAbsolutePath(Path path) {
        if (path.isAbsolute()) {
            return path;
        }
        return path.toAbsolutePath();
    }
    
    /**
     * The part of a URI that identifies a filesystem: the scheme and the authority. The
     * path of a URI names a file inside the filesystem, so "memory:///a.txt" and
     * "memory:///b.txt" belong to the same filesystem. A provider whose URIs carry the
     * filesystem identity in the path, before a "!" ("zip:///archive.zip!/entry"),
     * returns true from {@link #isIdentityInPath()}.
     */
    protected String fileSystemKey(URI uri) {
        StringBuilder b = new StringBuilder(uri.getScheme().toLowerCase(java.util.Locale.ROOT)).append("://");
        String authority = uri.getRawAuthority();
        if (authority != null) {
            b.append(authority);
        }
        if (isIdentityInPath()) {
            String path = uri.getPath();
            if (path != null) {
                int bang = path.indexOf('!');
                String id = bang >= 0 ? path.substring(0, bang) : path;
                // "zip:///a.zip" and "zip:///a.zip/" are the same archive
                while (id.length() > 1 && id.endsWith("/")) {
                    id = id.substring(0, id.length() - 1);
                }
                b.append(id);
            }
        }
        return b.toString();
    }

    /**
     * Whether this provider's URIs carry the filesystem identity in their path, up to a
     * "!" separating it from the path of the file inside the filesystem.
     */
    protected boolean isIdentityInPath() {
        return false;
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        checkUri(uri);
        synchronized (fileSystems) {
            AbstractFileSystem fs = fileSystems.get(fileSystemKey(uri));
            if (fs != null) {
            	return fs;
            }
        }
        throw new FileSystemNotFoundException();
    }

    @Override
    public Path getPath(URI uri) {
        checkUri(uri);
        String path = uri.getPath();
        if (path == null) {
            throw new IllegalArgumentException("URI path is null");
        }
        if (isIdentityInPath()) {
            // The file is what follows the "!"; a URI without one names the root
            int bang = path.indexOf('!');
            path = bang >= 0 ? path.substring(bang + 1) : "/";
            if (path.isEmpty()) {
                path = "/";
            }
        }
        return createPath(getFileSystem(uri), path);
    }

    /**
     * The Files.isSameFile() contract: two equal paths are the same file without any check;
     * otherwise both files must exist (NoSuchFileException), and two files of different
     * filesystems are different. The comparison is lexical, on the normalized absolute
     * paths: right for the filesystems without links (zip, resources); the others override it.
     */
    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        checkPath(path);
        if (path.equals(path2)) {
            return true;
        }
        if (path2 == null) {
            throw new NullPointerException();
        }
        if (!(path2 instanceof AbstractPath) || path2.getFileSystem().provider() != this) {
            return false;
        }
        checkPath(path2);
        checkAccess(path);
        checkAccess(path2);
        if (path.getFileSystem() != path2.getFileSystem()) {
            return false;
        }
        Path normalized1 = toAbsolutePath(path).normalize();
        Path normalized2 = toAbsolutePath(path2).normalize();
        return normalized1.equals(normalized2);
    }
    
    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type,
                                                                  LinkOption... options) {
        checkPath(path);
        // A null view made Files.setLastModifiedTime() throw a NullPointerException
        if (type == BasicFileAttributeView.class) {
            return type.cast(new BasicView(path, options));
        }
        return null;
    }
    
    /**
     * Update the times of a file; a null time is left unchanged.
     * The default refuses: subclasses that can store times override it.
     */
    protected void setTimes(Path path, FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        throw new UnsupportedOperationException("Setting file times is not supported by " + getScheme());
    }
    
    private class BasicView implements BasicFileAttributeView {
        private final Path path;
        private final LinkOption[] options;
        
        BasicView(Path path, LinkOption[] options) {
            this.path = path;
            this.options = options;
        }
        
        @Override
        public String name() {
            return "basic";
        }
        
        @Override
        public BasicFileAttributes readAttributes() throws IOException {
            return AbstractFileSystemProvider.this.readAttributes(path, BasicFileAttributes.class, options);
        }
        
        @Override
        public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
            AbstractFileSystemProvider.this.setTimes(path, lastModifiedTime, lastAccessTime, createTime);
        }
    }
    
    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options)
            throws IOException {
        // Parse attributes string (e.g., "basic:size,lastModifiedTime")
        int colonPos = attributes.indexOf(':');
        String viewName = colonPos == -1 ? "basic" : attributes.substring(0, colonPos);
        String attrList = colonPos == -1 ? attributes : attributes.substring(colonPos + 1);
        
        if (!"basic".equals(viewName)) {
            throw new UnsupportedOperationException("Only basic attributes supported");
        }
        
        BasicFileAttributes attrs = readAttributes(path, BasicFileAttributes.class, options);
        Map<String, Object> result = new HashMap<>();
        
        String[] attrNames = "*".equals(attrList) 
            ? new String[]{"size", "lastModifiedTime", "lastAccessTime", "creationTime", 
                          "isRegularFile", "isDirectory", "isSymbolicLink", "isOther", "fileKey"}
            : attrList.split(",");
        
        for (String attrName : attrNames) {
            attrName = attrName.trim();
            switch (attrName) {
                case "size":
                    result.put(attrName, attrs.size());
                    break;
                case "lastModifiedTime":
                    result.put(attrName, attrs.lastModifiedTime());
                    break;
                case "lastAccessTime":
                    result.put(attrName, attrs.lastAccessTime());
                    break;
                case "creationTime":
                    result.put(attrName, attrs.creationTime());
                    break;
                case "isRegularFile":
                    result.put(attrName, attrs.isRegularFile());
                    break;
                case "isDirectory":
                    result.put(attrName, attrs.isDirectory());
                    break;
                case "isSymbolicLink":
                    result.put(attrName, attrs.isSymbolicLink());
                    break;
                case "isOther":
                    result.put(attrName, attrs.isOther());
                    break;
                case "fileKey":
                    result.put(attrName, attrs.fileKey());
                    break;
                default:
                    throw new IllegalArgumentException("Attribute '" + attrName + "' not recognized");
            }
        }
        
        return result;
    }
    
    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options)
            throws IOException {
        int colonPos = attribute.indexOf(':');
        String viewName = colonPos == -1 ? "basic" : attribute.substring(0, colonPos);
        String attrName = colonPos == -1 ? attribute : attribute.substring(colonPos + 1);
        if (!"basic".equals(viewName)) {
            throw new UnsupportedOperationException("Only basic attributes supported");
        }
        switch (attrName) {
            case "lastModifiedTime":
                setTimes(path, (FileTime) value, null, null);
                break;
            case "lastAccessTime":
                setTimes(path, null, (FileTime) value, null);
                break;
            case "creationTime":
                setTimes(path, null, null, (FileTime) value);
                break;
            default:
                throw new IllegalArgumentException("Attribute '" + attrName + "' cannot be set");
        }
    }
    
    /**
     * Copy a file of a read-only filesystem to another filesystem of the same provider (a
     * copy to another provider never reaches the provider: Files.copy() streams it). A target
     * in a read-only filesystem is ReadOnlyFileSystemException, whatever the options
     * (COPY_ATTRIBUTES used to fail with UnsupportedOperationException first).
     */
    protected void copyOutOfReadOnly(Path source, Path target, CopyOption... options) throws IOException {
        boolean replace = false;
        boolean copyAttributes = false;
        for (CopyOption option : options) {
            if (option == null) {
                throw new NullPointerException();
            }
            if (option == java.nio.file.StandardCopyOption.REPLACE_EXISTING) {
                replace = true;
            } else if (option == java.nio.file.StandardCopyOption.COPY_ATTRIBUTES) {
                copyAttributes = true;
            } else if (option != LinkOption.NOFOLLOW_LINKS) {
                throw new UnsupportedOperationException("Unsupported copy option: " + option);
            }
        }
        if (target.getFileSystem().isReadOnly()) {
            throw new java.nio.file.ReadOnlyFileSystemException();
        }
        BasicFileAttributes attrs = readAttributes(source, BasicFileAttributes.class);
        if (attrs.isDirectory()) {
            // A directory copy creates the directory only
            if (replace) {
                java.nio.file.Files.deleteIfExists(target);
            }
            java.nio.file.Files.createDirectory(target);
        } else {
            try (java.io.InputStream in = newInputStream(source)) {
                if (replace) {
                    java.nio.file.Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } else {
                    java.nio.file.Files.copy(in, target);
                }
            }
        }
        if (copyAttributes) {
            java.nio.file.Files.setLastModifiedTime(target, attrs.lastModifiedTime());
        }
    }

    protected void checkUri(URI uri) {
        if (!uri.getScheme().equalsIgnoreCase(getScheme())) {
            throw new IllegalArgumentException("URI scheme must be " + getScheme());
        }
    }
    
    protected void checkPath(Path path) {
        if (path == null) {
            throw new NullPointerException("path is null");
        }
        if (!(path instanceof AbstractPath)) {
            throw new IllegalArgumentException("Path must be an instance of AbstractPath");
        }
        AbstractPath abstractPath = (AbstractPath) path;
        if (abstractPath.getFileSystem().provider() != this) {
            throw new ProviderMismatchException();
        }
        // A closed filesystem refuses every operation, also through paths obtained before
        // it was closed (ClosedFileSystemException)
        abstractPath.getFileSystem().checkOpen();
    }
    
    void removeFileSystem(AbstractFileSystem fs) {
        synchronized (fileSystems) {
            fileSystems.values().remove(fs);
        }
    }
    
    /**
     * Create a filesystem instance for this provider.
     */
    protected abstract AbstractFileSystem createFileSystem(URI uri, Map<String, ?> env) throws IOException;
    
    /**
     * Create a path instance for this provider.
     */
    protected abstract AbstractPath createPath(FileSystem fs, String path);
}
