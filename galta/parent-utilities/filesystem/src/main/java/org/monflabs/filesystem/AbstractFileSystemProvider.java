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
    // Every access goes through synchronized(fileSystems), including removeFileSystem()
    protected final Map<URI, AbstractFileSystem> fileSystems = new HashMap<>();
    
    protected AbstractFileSystemProvider(boolean registered) {
    	this.registered = registered;
    }
    
    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		checkUri(uri);
		if(registered) {
			synchronized (fileSystems) {
		        if (fileSystems.containsKey(uri)) {
		            throw new FileSystemAlreadyExistsException();
		        }
		        AbstractFileSystem fs = createFileSystem(uri, env);
		        fileSystems.put(uri, fs);
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
    
    @Override
    public FileSystem getFileSystem(URI uri) {
        checkUri(uri);
        synchronized (fileSystems) {
            AbstractFileSystem fs = fileSystems.get(uri);
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
        return createPath(getFileSystem(uri), path);
    }
    
    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        Path normalized1 = path.toAbsolutePath().normalize();
        Path normalized2 = path2.toAbsolutePath().normalize();
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
