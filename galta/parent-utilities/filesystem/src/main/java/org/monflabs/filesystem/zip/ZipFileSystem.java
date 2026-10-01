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
		private Long maxEntrySize;
		
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
		/**
		 * The largest entry a random-access channel (Files.newByteChannel(),
		 * Files.readAllBytes(), Files.readString()...) reads into memory; default
		 * {@link ZipFileSystemProvider#DEFAULT_MAX_ENTRY_SIZE}. Files.newInputStream() streams
		 * an entry of any size.
		 */
		public Builder maxEntrySize(long maxEntrySize) {
			this.maxEntrySize = maxEntrySize;
			return this;
		}
		
		@Override
		protected ZipFileSystem _build() {
			try {
				ZipFileSystemProvider p = provider!=null ? provider : DEFAULT_PROVIDER;
				// Without a URI, the URI of the archive ("zip:///dir/a.zip"): the URIs of the
				// entries then name the archive ("zip:///dir/a.zip!/entry")
				URI u = uri!=null ? uri : (zipFile!=null ? identityUri(zipFile) : DEFAULT_URI);
				HashMap<String,Object> env = new HashMap<>();
				env.put(ZipFileSystemProvider.ZIP_FILE_PARAM, zipFile);
				if (maxEntrySize != null) {
					env.put(ZipFileSystemProvider.MAX_ENTRY_SIZE_PARAM, maxEntrySize);
				}
				return (ZipFileSystem)p.newFileSystem(u, env);
			} catch(IOException ex) {
				throw new FileSystemRuntimeException(ex);
			}
		}
	}
	
	public static Builder newBuilder() {
		return new Builder();
	}

	/**
	 * The URI identifying the filesystem of an archive: "zip:" and the archive's absolute path.
	 */
	public static URI identityUri(Path zipFile) {
		try {
			return new URI(ZipFileSystemProvider.SCHEME, "", zipFile.toAbsolutePath().toUri().getPath(), null, null);
		} catch (java.net.URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static final ZipFileSystemProvider DEFAULT_PROVIDER = new ZipFileSystemProvider(false);
	public static final URI DEFAULT_URI = URI.create(ZipFileSystemProvider.SCHEME + ":///");
	
    
    private final ZipFile zipFile;
    private final ZipFileStore fileStore;
    private final long maxEntrySize;
    // Computed once: the archive does not change, and a closed ZipFile cannot be enumerated
    private long totalCompressedSize;
    // Indexed once when the archive is opened: entry name (without a trailing "/") ->
    // entry, and directory name ("" for the root) -> names of its direct children
    private final Map<String, ZipEntry> entries = new HashMap<>();
    private final Map<String, Set<String>> directories = new HashMap<>();

    public ZipFileSystem(ZipFileSystemProvider provider, URI uri, ZipFile zipFile) {
        this(provider, uri, zipFile, ZipFileSystemProvider.DEFAULT_MAX_ENTRY_SIZE);
    }

    public ZipFileSystem(ZipFileSystemProvider provider, URI uri, ZipFile zipFile, long maxEntrySize) {
    	super(provider, uri, "/");
        this.zipFile = zipFile;
        this.maxEntrySize = maxEntrySize;
        this.fileStore = new ZipFileStore(this);
        index();
    }

    /**
     * The largest entry a random-access channel reads into memory.
     */
    public long getMaxEntrySize() {
        return maxEntrySize;
    }

    private void index() {
        directories.put("", new LinkedHashSet<>());
        Enumeration<? extends ZipEntry> en = zipFile.entries();
        while (en.hasMoreElements()) {
            ZipEntry entry = en.nextElement();
            totalCompressedSize += Math.max(0, entry.getCompressedSize());
            String rawName = entry.getName();
            String name = rawName;
            boolean dir = name.endsWith("/");
            if (dir) {
                name = name.substring(0, name.length() - 1);
            }
            if (!isSafeEntryName(name)) {
                // "/abs", "../x", "a/./b", "a//b": a ".." child made Files.walk() loop
                // forever and let paths climb out of the archive (zip-slip)
                continue;
            }
            // With duplicate names, the entry ZipFile.getEntry() returns wins: it is the one
            // ZipFile.getInputStream() reads, so the attributes and the content always come
            // from the same entry (the first one's attributes used to go with another's bytes)
            if (!entries.containsKey(name)) {
                ZipEntry canonical = zipFile.getEntry(rawName);
                entries.put(name, canonical != null ? canonical : entry);
            }
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
        // A file entry "f" next to "f/child.txt": "f" is a directory (its children are
        // readable and listed), the file entry is hidden. It used to be a file whose
        // children could be read but not listed
        entries.entrySet().removeIf(e -> !e.getValue().isDirectory() && directories.containsKey(e.getKey()));
    }

    // A relative name made of non empty segments, none of them "." or ".."
    private static boolean isSafeEntryName(String name) {
        return isSafeRelativeName(name);
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
        checkOpen();
        return Collections.singletonList(new ZipPath(this, "/"));
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        checkOpen();
        return Collections.singletonList(fileStore);
    }

    /**
     * The path naming this filesystem in the URIs of its entries, before the "!": the path
     * of the filesystem's URI, or the archive's path when the filesystem has a bare
     * "zip:///" URI.
     */
    String getIdentityPath() {
        String p = uri != null ? uri.getPath() : null;
        if (p != null && !p.isEmpty() && !p.equals("/")) {
            while (p.length() > 1 && p.endsWith("/")) {
                p = p.substring(0, p.length() - 1);
            }
            return p;
        }
        return identityUri(java.nio.file.Paths.get(zipFile.getName())).getPath();
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
    static String entryName(ZipPath path) {
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
     * Read the entire content of a ZIP entry, up to {@link #getMaxEntrySize()} bytes: a larger
     * entry (or a "zip bomb" inflating far beyond its declared size) throws a
     * FileSystemException instead of exhausting the heap.
     */
    public byte[] readEntry(ZipEntry entry) throws IOException {
        checkOpen();
        String name = "/" + entry.getName();
        if (entry.getSize() > maxEntrySize) {
            throw tooLarge(name, entry.getSize());
        }
        try (InputStream is = zipFile.getInputStream(entry)) {
            byte[] content = is.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxEntrySize + 1));
            if (content.length > maxEntrySize) {
                throw tooLarge(name, -1);
            }
            return content;
        }
    }

    private java.nio.file.FileSystemException tooLarge(String name, long size) {
        return new java.nio.file.FileSystemException(name, null,
            "Entry larger than the " + maxEntrySize + " bytes a random-access channel reads into memory"
            + (size >= 0 ? " (" + size + " bytes)" : "") + ": read it with Files.newInputStream()");
    }

    /**
     * A stream over the content of a ZIP entry, inflated as it is read (no size limit).
     */
    public InputStream openEntry(ZipEntry entry) throws IOException {
        checkOpen();
        return zipFile.getInputStream(entry);
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
            // The compressed size of all entries, computed when the archive was indexed
            fs.checkOpen();
            return fs.totalCompressedSize;
        }

        @Override
        public long getUsableSpace() throws IOException {
            fs.checkOpen();
            return 0; // Read-only
        }

        @Override
        public long getUnallocatedSpace() throws IOException {
            fs.checkOpen();
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
