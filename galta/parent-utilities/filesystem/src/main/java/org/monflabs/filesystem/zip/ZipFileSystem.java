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
import java.util.Map;
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
    // Indexed once when the archive is opened: entry name (without a trailing "/") ->
    // entry, and directory name ("" for the root) -> names of its direct children
    private final Map<String, ZipEntry> entries = new HashMap<>();
    private final Map<String, Set<String>> directories = new HashMap<>();

    public ZipFileSystem(ZipFileSystemProvider provider, URI uri, ZipFile zipFile) {
    	super(provider, uri, "/");
        this.zipFile = zipFile;
        this.fileStore = new ZipFileStore(this);
        index();
    }

    private void index() {
        directories.put("", new LinkedHashSet<>());
        Enumeration<? extends ZipEntry> en = zipFile.entries();
        while (en.hasMoreElements()) {
            ZipEntry entry = en.nextElement();
            String name = entry.getName();
            boolean dir = name.endsWith("/");
            if (dir) {
                name = name.substring(0, name.length() - 1);
            }
            if (!isSafeEntryName(name)) {
                // "/abs", "../x", "a/./b", "a//b": a ".." child made Files.walk() loop
                // forever and let paths climb out of the archive (zip-slip)
                continue;
            }
            // The first entry wins, as with ZipFile.getEntry()
            entries.putIfAbsent(name, entry);
            if (dir) {
                directories.computeIfAbsent(name, k -> new LinkedHashSet<>());
            }
            // Register the entry with its parent, and the (possibly implicit) ancestors
            String child = name;
            int slash = child.lastIndexOf('/');
            while (true) {
                String parent = slash < 0 ? "" : child.substring(0, slash);
                Set<String> siblings = directories.get(parent);
                boolean known = siblings != null;
                if (!known) {
                    siblings = new LinkedHashSet<>();
                    directories.put(parent, siblings);
                }
                siblings.add(child.substring(slash + 1));
                if (known || parent.isEmpty()) {
                    break;
                }
                child = parent;
                slash = child.lastIndexOf('/');
            }
        }
    }

    // A relative name made of non empty segments, none of them "." or ".."
    private static boolean isSafeEntryName(String name) {
        if (name.isEmpty() || name.startsWith("/") || name.indexOf('\\') >= 0) {
            return false;
        }
        for (String segment : name.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
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

        ZipEntry entry = entries.get(pathStr);
        if (entry != null) {
            return entry;
        }

        // An implicit directory (has children but no entry)
        if (directories.containsKey(pathStr)) {
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
        Set<String> children = directories.get(entryName(dir));
        List<Path> result = new ArrayList<>();
        if (children != null) {
            for (String child : children) {
                result.add(dir.resolve(child));
            }
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
