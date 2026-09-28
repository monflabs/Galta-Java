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
import java.nio.file.FileStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.monflabs.filesystem.AbstractFileSystem;
import org.monflabs.filesystem.AbstractPath;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.path.FileSystemRuntimeException;

/**
 * Read-only FileSystem implementation backed by a ZIP file.
 */
public class ZipFileSystem extends AbstractFileSystem {

	public static class Builder extends ObjectBuilder<ZipFileSystem> {
		
		private ZipFileSystemProvider provider;
		private URI uri;
		private Path zipFile;
		
		public Builder provider(ZipFileSystemProvider provider) {
			this.provider = provider;
			return this;
		}
		public Builder uri(URI uri) {
			this.uri = uri;
			return this;
		}
		public Builder zipFile(Path zipFile) {
			this.zipFile = zipFile;
			return this;
		}
		
		@Override
		protected ZipFileSystem _build() {
			try {
				ZipFileSystemProvider p = provider!=null ? provider : DEFAULT_PROVIDER;
				URI u = uri!=null ? uri : DEFAULT_URI;
				HashMap<String,Object> env = new HashMap<>();
				env.put(ZipFileSystemProvider.ZIP_FILE_PARAM, zipFile);
				return (ZipFileSystem)p.newFileSystem(u, env);
			} catch(IOException ex) {
				throw new FileSystemRuntimeException(ex);
			}
		}
	}
	
	public static Builder newBuilder() {
		return new Builder();
	}

	public static final ZipFileSystemProvider DEFAULT_PROVIDER = new ZipFileSystemProvider(false);
	public static final URI DEFAULT_URI = URI.create(ZipFileSystemProvider.SCHEME + ":///");
	
    
    private final ZipFile zipFile;
    private final ZipFileStore fileStore;
    
    public ZipFileSystem(ZipFileSystemProvider provider, URI uri, ZipFile zipFile) {
    	super(provider, uri, "/");
        this.zipFile = zipFile;
        this.fileStore = new ZipFileStore(this);
    }
    
    @Override
    public void close() throws IOException {
        if (isOpen()) {
            super.close();
            zipFile.close();
        }
    }
    
    @Override
    protected AbstractPath createPath(String path) {
        return new ZipPath(this, path);
    }
    
    @Override
    public boolean isReadOnly() {
        return true; // ZIP filesystem is always read-only
    }
    
    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singletonList(new ZipPath(this, "/"));
    }
    
    @Override
    public Iterable<FileStore> getFileStores() {
        return Collections.singletonList(fileStore);
    }
    
    @Override
    public Path getPath(String first, String... more) {
        checkOpen();
        
        StringBuilder pathBuilder = new StringBuilder(first);
        for (String segment : more) {
            if (!segment.isEmpty()) {
                if (pathBuilder.length() > 0 && pathBuilder.charAt(pathBuilder.length() - 1) != '/') {
                    pathBuilder.append('/');
                }
                pathBuilder.append(segment);
            }
        }
        
        return new ZipPath(this, pathBuilder.toString());
    }
    
    @Override
    protected boolean matchRelativeToRoot() {
        // Patterns are matched against "a/b.txt", not "/a/b.txt"
        return true;
    }
    
    /**
     * Get the ZipEntry for a given path, or null if not found.
     */
    public ZipEntry getEntry(ZipPath path) {
        checkOpen();
        String pathStr = entryName(path);
        
        // Root directory
        if (pathStr.isEmpty()) {
            return createRootEntry();
        }
        
        // Try exact match
        ZipEntry entry = zipFile.getEntry(pathStr);
        if (entry != null) {
            return entry;
        }
        
        // Try with trailing slash (for directories)
        if (!pathStr.endsWith("/")) {
            entry = zipFile.getEntry(pathStr + "/");
            if (entry != null) {
                return entry;
            }
        }
        
        // Check if it's an implicit directory (has children but no entry)
        if (hasChildren(pathStr)) {
            return createImplicitDirectoryEntry(pathStr);
        }
        
        return null;
    }
    
    /**
     * The entry name of a path: absolute, "." and ".." resolved ("/a/../a/b.txt" is "a/b.txt"),
     * without the leading slash.
     */
    private static String entryName(ZipPath path) {
        String pathStr = path.toAbsolutePath().normalize().toString();
        return pathStr.startsWith("/") ? pathStr.substring(1) : pathStr;
    }
    
    /**
     * Check if a path has children (to detect implicit directories).
     */
    private boolean hasChildren(String pathStr) {
        String prefix = pathStr.endsWith("/") ? pathStr : pathStr + "/";
        
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().startsWith(prefix)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Create a virtual entry for the root directory.
     */
    private ZipEntry createRootEntry() {
        ZipEntry entry = new ZipEntry("/");
        return entry;
    }
    
    /**
     * Create a virtual entry for an implicit directory.
     */
    private ZipEntry createImplicitDirectoryEntry(String name) {
        if (!name.endsWith("/")) {
            name = name + "/";
        }
        return new ZipEntry(name);
    }
    
    /**
     * Read the entire content of a ZIP entry.
     */
    public byte[] readEntry(ZipEntry entry) throws IOException {
        checkOpen();
        try (InputStream is = zipFile.getInputStream(entry)) {
            return is.readAllBytes();
        }
    }
    
    /**
     * List all direct children of a directory.
     */
    public List<Path> listDirectory(ZipPath dir) {
        checkOpen();
        String dirPath = entryName(dir);
        
        // Normalize directory path
        if (!dirPath.isEmpty() && !dirPath.endsWith("/")) {
            dirPath = dirPath + "/";
        }
        
        Set<String> children = new LinkedHashSet<>();
        
        // Scan all ZIP entries
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();
            
            // Check if entry is under this directory
            if (dirPath.isEmpty() || entryName.startsWith(dirPath)) {
                // Get relative path
                String relativePath = dirPath.isEmpty() ? entryName : entryName.substring(dirPath.length());
                
                // Skip if empty (shouldn't happen)
                if (relativePath.isEmpty()) {
                    continue;
                }
                
                // Get first component (direct child)
                int slashIndex = relativePath.indexOf('/');
                String childName;
                if (slashIndex > 0) {
                    // Directory entry
                    childName = relativePath.substring(0, slashIndex);
                } else if (slashIndex == 0) {
                    // Skip entries that start with slash
                    continue;
                } else {
                    // File entry
                    childName = relativePath;
                }
                
                children.add(childName);
            }
        }
        
        // Convert to paths
        List<Path> result = new ArrayList<>();
        for (String child : children) {
            result.add(dir.resolve(child));
        }
        
        return result;
    }
    
    /**
     * Get the underlying ZipFile (for debugging/testing).
     */
    public ZipFile getZipFile() {
        return zipFile;
    }
    
    /**
     * FileStore implementation for ZIP filesystem.
     */
    private static class ZipFileStore extends FileStore {
        private final ZipFileSystem fs;
        
        public ZipFileStore(ZipFileSystem fs) {
            this.fs = fs;
        }
        
        @Override
        public String name() {
            return "zip-store";
        }
        
        @Override
        public String type() {
            return "zip";
        }
        
        @Override
        public boolean isReadOnly() {
            return true;
        }
        
        @Override
        public long getTotalSpace() throws IOException {
            // Approximate based on compressed size of all entries
            long total = 0;
            Enumeration<? extends ZipEntry> entries = fs.zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                total += entry.getCompressedSize();
            }
            return total;
        }
        
        @Override
        public long getUsableSpace() throws IOException {
            return 0; // Read-only
        }
        
        @Override
        public long getUnallocatedSpace() throws IOException {
            return 0; // Read-only
        }
        
        @Override
        public boolean supportsFileAttributeView(Class<? extends java.nio.file.attribute.FileAttributeView> type) {
            return type == java.nio.file.attribute.BasicFileAttributeView.class;
        }
        
        @Override
        public boolean supportsFileAttributeView(String name) {
            return "basic".equals(name);
        }
        
        @Override
        public <V extends java.nio.file.attribute.FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
            return null;
        }
        
        @Override
        public Object getAttribute(String attribute) throws IOException {
            throw new UnsupportedOperationException("getAttribute not supported");
        }
    }
}
