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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.monflabs.filesystem.AbstractFileSystem;
import org.monflabs.filesystem.AbstractPath;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.StringUtil;
import org.monflabs.util.path.FileSystemRuntimeException;

/**
 * Read-only FileSystem implementation backed by classpath resources.
 * Uses a manifest file to enumerate available resources.
 */
public class ResourceFileSystem extends AbstractFileSystem {

	public static class Builder extends ObjectBuilder<ResourceFileSystem> {
		
		private ResourceFileSystemProvider provider;
		private URI uri;
		private String manifest;
		
		private ClassLoader classLoader;
		private String root;
		
		public Builder provider(ResourceFileSystemProvider provider) {
			this.provider = provider;
			return this;
		}
		public Builder uri(URI uri) {
			this.uri = uri;
			return this;
		}
		public Builder classLoader(ClassLoader classLoader) {
			this.classLoader = classLoader;
			return this;
		}
		public Builder root(String root) {
			this.root = root;
			return this;
		}
		public Builder manifest(String manifest) {
			this.manifest = manifest;
			return this;
		}
		
		@Override
		protected ResourceFileSystem _build() {
			try {
				ResourceFileSystemProvider p = provider!=null ? provider : DEFAULT_PROVIDER;
				URI u = uri!=null ? uri : DEFAULT_URI;
				HashMap<String,Object> env = new HashMap<>();
		        env.put(ResourceFileSystemProvider.CLASSLOADER_PARAM, classLoader);
		        env.put(ResourceFileSystemProvider.BASE_PATH_PARAM, root);
		        env.put(ResourceFileSystemProvider.MANIFEST_FILE_PARAM, manifest);
				return (ResourceFileSystem)p.newFileSystem(u, env);
			} catch(IOException ex) {
				throw new FileSystemRuntimeException(ex);
			}
		}
	}
	
	public static Builder newBuilder() {
		return new Builder();
	}
	
	public static final ResourceFileSystemProvider DEFAULT_PROVIDER = new ResourceFileSystemProvider(false);
	public static final URI DEFAULT_URI = URI.create(ResourceFileSystemProvider.SCHEME + ":///");
    
    private final ClassLoader classLoader;
    private final String basePath;
    private final Set<String> resourcePaths;
    // Resource path -> URL prefix of the classpath entry whose manifest listed it
    private final Map<String, String> origins = new HashMap<>();
    // Indexed once at load: directory ("" for the root) -> names of its direct children
    private final Map<String, Set<String>> directories = new HashMap<>();
    // Resolved lazily, then cached: resources don't change while the filesystem is open
    private final Map<String, URL> urls = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> sizes = new java.util.concurrent.ConcurrentHashMap<>();
    private final ResourceFileStore fileStore;
    
    public ResourceFileSystem(ResourceFileSystemProvider provider, URI uri, 
                             ClassLoader classLoader, String basePath, String manifestFile) 
            throws IOException {
        super(provider, uri, "/");
        this.classLoader = classLoader;
        this.basePath = basePath;
        this.resourcePaths = loadManifest(manifestFile);
        this.fileStore = new ResourceFileStore(this);
        indexDirectories();
    }

    private void indexDirectories() {
        directories.put("", new LinkedHashSet<>());
        for (String resource : resourcePaths) {
            // Register the resource with its parent, and the implicit ancestors
            String child = resource;
            int slash = child.lastIndexOf('/');
            while (true) {
                String parent = slash < 0 ? "" : child.substring(0, slash);
                String name = child.substring(slash + 1);
                Set<String> siblings = directories.get(parent);
                boolean known = siblings != null;
                if (!known) {
                    siblings = new LinkedHashSet<>();
                    directories.put(parent, siblings);
                }
                if (!name.isEmpty()) {
                    siblings.add(name);
                }
                if (known || parent.isEmpty()) {
                    break;
                }
                child = parent;
                slash = child.lastIndexOf('/');
            }
        }
    }
    
    /**
     * Load the manifest file and parse resource paths.
     */
    private Set<String> loadManifest(String manifestFile) throws IOException {
        String manifestPath = basePath + manifestFile;

        Set<String> paths = new LinkedHashSet<>();
		boolean found = false;
        for(Enumeration<URL> e = classLoader.getResources(manifestPath); e.hasMoreElements(); ) {
        	URL u = e.nextElement();
			found = true;
			// The classpath entry this manifest comes from: its resources are read from there
			String manifestUrl = u.toExternalForm();
			String origin = manifestUrl.endsWith(manifestFile)
				? manifestUrl.substring(0, manifestUrl.length() - manifestFile.length())
				: null;
	        try (InputStream is = u.openStream()) {
				BufferedReader r = new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8));
				for(String line=r.readLine(); line!=null; line=r.readLine()) {
					String s = line.trim();
					// A comment is a whole line starting with '#': a '#' elsewhere is part of the
					// file name ("file#1.txt" used to be truncated to "file")
					if(s.isEmpty() || s.startsWith("#")) {
						continue;
					}
					String[] parts = StringUtil.splitString(s,'\t',true);
                    // Normalize path
					String fileName = parts[0].trim();
					//long length = parts.length>=2 ? Long.parseLong(parts[1].trim()) : -1L;
					//long date = parts.length>=3 ? Instant.parse(parts[2].trim()).toEpochMilli() : -1L;
                    if (fileName.startsWith("/")) {
                    	fileName = fileName.substring(1);
                    }
                    if (paths.add(fileName) && origin != null) {
                    	origins.put(fileName, origin);
                    }
				}
	        }
        }
        if (!found) {
            throw new NoSuchFileException("Manifest file not found: " + manifestPath);
        }
        return Collections.unmodifiableSet(paths);
    }
    
    @Override
    public boolean isReadOnly() {
        return true; // Resource filesystem is always read-only
    }
    
    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singletonList(new ResourcePath(this, "/"));
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
    
    @Override
    protected AbstractPath createPath(String path) {
        return new ResourcePath(this, path);
    }
    
    /**
     * Check if a path exists (either as a file or implicit directory).
     */
    public boolean exists(ResourcePath path) {
        String pathStr = normalizePath(path.toString());
        
        // Root always exists
        if (pathStr.isEmpty()) {
            return true;
        }
        
        // Check if it's in the manifest
        if (resourcePaths.contains(pathStr)) {
            return true;
        }
        
        // Check if it's an implicit directory
        return isImplicitDirectory(pathStr);
    }
    
    /**
     * Check if a path is a directory.
     */
    public boolean isDirectory(ResourcePath path) {
        String pathStr = normalizePath(path.toString());
        
        // Root is a directory
        if (pathStr.isEmpty()) {
            return true;
        }
        
        // If it's in manifest, it's a file (not directory)
        if (resourcePaths.contains(pathStr)) {
            return false;
        }
        
        // Check if it's an implicit directory
        return isImplicitDirectory(pathStr);
    }
    
    /**
     * Check if a path represents an implicit directory.
     */
    private boolean isImplicitDirectory(String pathStr) {
        if (pathStr.endsWith("/")) {
            pathStr = pathStr.substring(0, pathStr.length() - 1);
        }
        return directories.containsKey(pathStr);
    }
    
    /**
     * Read a resource file.
     */
    public byte[] readResource(ResourcePath path) throws IOException {
        try (InputStream is = openResource(path)) {
            return is.readAllBytes();
        }
    }
    
    /**
     * The size of a resource file, read from its URL connection when available.
     */
    long resourceSize(ResourcePath path) {
        String pathStr = normalizePath(path.toString());
        Long cached = sizes.get(pathStr);
        if (cached != null) {
            return cached;
        }
        long size = computeResourceSize(path, pathStr);
        sizes.put(pathStr, size);
        return size;
    }

    private long computeResourceSize(ResourcePath path, String pathStr) {
        try {
            URL url = findResource(pathStr);
            if (url != null) {
                java.net.URLConnection c = url.openConnection();
                long length = c.getContentLengthLong();
                if (length >= 0) {
                    try {
                        c.getInputStream().close();
                    } catch (IOException e) {
                        // Only closing
                    }
                    return length;
                }
            }
            return readResource(path).length;
        } catch (IOException e) {
            return 0;
        }
    }
    
    private InputStream openResource(ResourcePath path) throws IOException {
        String pathStr = normalizePath(path.toString());
        
        if (!resourcePaths.contains(pathStr)) {
            throw new NoSuchFileException(path.toString());
        }
        
        URL url = findResource(pathStr);
        if (url == null) {
            throw new NoSuchFileException("Resource not found: " + basePath + pathStr);
        }
        return url.openStream();
    }
    
    /**
     * The URL of a resource, taken from the classpath entry whose manifest listed it.
     * getResourceAsStream() returned the first match on the classpath, so with two jars
     * holding the same name, one jar's manifest could serve the other jar's bytes.
     */
    private URL findResource(String pathStr) throws IOException {
        URL cached = urls.get(pathStr);
        if (cached != null) {
            return cached;
        }
        URL url = lookupResource(pathStr);
        if (url != null) {
            urls.put(pathStr, url);
        }
        return url;
    }

    private URL lookupResource(String pathStr) throws IOException {
        String resourcePath = basePath + pathStr;
        String origin = origins.get(pathStr);
        URL first = null;
        for (Enumeration<URL> e = classLoader.getResources(resourcePath); e.hasMoreElements(); ) {
            URL u = e.nextElement();
            if (origin == null || u.toExternalForm().startsWith(origin)) {
                return u;
            }
            if (first == null) {
                first = u;
            }
        }
        return first;
    }
    
    /**
     * List all direct children of a directory.
     */
    public List<Path> listDirectory(ResourcePath dir) {
        String dirPath = normalizePath(dir.toString());
        if (dirPath.endsWith("/")) {
            dirPath = dirPath.substring(0, dirPath.length() - 1);
        }
        Set<String> children = directories.get(dirPath);
        List<Path> result = new ArrayList<>();
        if (children != null) {
            for (String child : children) {
                result.add(dir.resolve(child));
            }
        }
        return result;
    }

    /**
     * Normalize a path string (remove leading slash).
     */
    private String normalizePath(String path) {
        // "." and ".." resolved: "/a/../a/b.txt" is "a/b.txt"
        path = createPath(path.startsWith("/") ? path : "/" + path).normalize().toString();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }
    
    /**
     * Get all resource paths (for debugging/testing).
     */
    public Set<String> getResourcePaths() {
        return resourcePaths;
    }
    
    /**
     * Get the base path.
     */
    public String getBasePath() {
        return basePath;
    }
    
    /**
     * Get the ClassLoader.
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }
    
    /**
     * FileStore implementation for Resource filesystem.
     */
    private static class ResourceFileStore extends FileStore {
        private final ResourceFileSystem fs;
        
        public ResourceFileStore(ResourceFileSystem fs) {
            this.fs = fs;
        }
        
        @Override
        public String name() {
            return "resource-store";
        }
        
        @Override
        public String type() {
            return "resource";
        }
        
        @Override
        public boolean isReadOnly() {
            return true;
        }
        
        @Override
        public long getTotalSpace() throws IOException {
            // Sum up all resource sizes
            long total = 0;
            for (String resource : fs.resourcePaths) {
                total += fs.resourceSize(new ResourcePath(fs, "/" + resource));
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
