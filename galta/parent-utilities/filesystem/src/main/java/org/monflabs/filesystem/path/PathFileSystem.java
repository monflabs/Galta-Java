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
package org.monflabs.filesystem.path;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.monflabs.filesystem.AbstractFileSystem;
import org.monflabs.filesystem.AbstractPath;
import org.monflabs.filesystem.Sandbox;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.path.FileSystemRuntimeException;

/**
 * FileSystem implementation that delegates to java.nio.file.Path API.
 * 
 * Key features:
 * - Uses Path instead of File for modern NIO.2 support
 * - Always uses "/" as separator (cross-platform normalization)
 * - Translates between "/" and OS separator transparently
 * - Supports optional root path for sandboxing
 * 
 * Example usage:
 * <pre>
 * // Sandbox to specific directory
 * Path rootPath = Paths.get("/home/user/data");
 * PathFileSystemProvider provider = new PathFileSystemProvider();
 * FileSystem fs = provider.newFileSystem(URI.create("pathfs:///"), rootPath);
 * 
 * // All paths use "/" even on Windows
 * Path file = fs.getPath("/docs/file.txt");
 * // Translates to: /home/user/data/docs/file.txt (Unix) or C:\home\\user\data\docs\file.txt (Windows)
 * </pre>
 */
public class PathFileSystem extends AbstractFileSystem {
	
	public static class Builder extends ObjectBuilder<PathFileSystem> {
		
		private PathFileSystemProvider provider;
		private URI uri;
		private Path root;
		
		public Builder provider(PathFileSystemProvider provider) {
			this.provider = provider;
			return this;
		}
		public Builder uri(URI uri) {
			this.uri = uri;
			return this;
		}
		public Builder root(Path root) {
			this.root = root;
			return this;
		}
		
		@Override
		protected PathFileSystem _build() {
			try {
				PathFileSystemProvider p = provider!=null ? provider : DEFAUT_PROVIDER;
				URI u = uri!=null ? uri : DEFAULT_URI;
				HashMap<String,Object> env = new HashMap<>();
		        env.put(PathFileSystemProvider.ROOT_PARAM, root);
				return (PathFileSystem)p.newFileSystem(u, env);
			} catch(IOException ex) {
				throw new FileSystemRuntimeException(ex);
			}
		}
	}
	
	public static Builder newBuilder() {
		return new Builder();
	}
	
	public static final PathFileSystemProvider DEFAUT_PROVIDER = new PathFileSystemProvider(false);
	public static final URI DEFAULT_URI = URI.create(PathFileSystemProvider.SCHEME + ":///");

	
    private final java.nio.file.Path rootPath;
    
    /**
     * Create a PathFileSystem with a root path for sandboxing.
     * 
     * @param provider The filesystem provider
     * @param uri The filesystem URI
     * @param rootPath The root path for sandboxing (null for unrestricted access)
     */
    public PathFileSystem(PathFileSystemProvider provider, URI uri, java.nio.file.Path rootPath) {
        super(provider, uri, "/");  // Always use "/" as separator
        // Absolute, so that relativizing an absolute OS path against it works
        this.rootPath = rootPath != null ? rootPath.toAbsolutePath().normalize() : null;
    }
    
    /**
     * Get the root path of this filesystem (may be null for unrestricted).
     */
    public java.nio.file.Path getRootPath() {
        return rootPath;
    }
    
    /**
     * Translate a virtual path (with /) to an actual OS path.
     * 
     * @param virtualPath The path in this filesystem (e.g., "/docs/file.txt")
     * @return The corresponding OS path
     */
    public java.nio.file.Path toOSPath(String virtualPath) {
        if (virtualPath == null || virtualPath.isEmpty()) {
            return rootPath != null ? rootPath : Paths.get("/");
        }
        
        if (rootPath != null) {
            // Sandboxed: resolve "." and ".." lexically first, so ".." can never climb
            // above the root (like a chroot, "/../x" is "/x")
            String abs = virtualPath.startsWith("/") ? virtualPath : "/" + virtualPath;
            virtualPath = createPath(abs).normalize().toString();
        }
        
        // Remove leading / for relative resolution
        String relativePath = virtualPath.startsWith("/") ? virtualPath.substring(1) : virtualPath;
        
        // Replace / with OS separator
        if (!"/".equals(FileSystems.getDefault().getSeparator())) {
            relativePath = relativePath.replace("/", FileSystems.getDefault().getSeparator());
        }
        
        if (rootPath != null) {
            // Sandboxed: resolve relative to root
            if (relativePath.isEmpty()) {
                return rootPath;
            }
            return rootPath.resolve(relativePath);
        } else {
            // Unrestricted: use as-is (but with OS separator)
            if (relativePath.isEmpty()) {
                return Paths.get("/");
            }
            return Paths.get(FileSystems.getDefault().getSeparator() + relativePath);
        }
    }
    
    /**
     * Check that an OS path obtained from {@link #toOSPath(String)} really stays inside the
     * sandbox root once symbolic links are followed. The path itself may not exist yet, so
     * the nearest existing ancestor is what gets resolved.
     */
    public void checkSandbox(java.nio.file.Path osPath) throws IOException {
        if (rootPath == null) {
            return;
        }
        Sandbox.checkInside(osPath, rootPath, osPath);
    }
    
    /**
     * Translate an OS path to a virtual path (with /).
     * 
     * @param osPath The OS path
     * @return The corresponding virtual path in this filesystem
     * @throws IllegalArgumentException if the filesystem is sandboxed and the path is outside its root
     */
    public String toVirtualPath(java.nio.file.Path osPath) {
        String pathString;
        
        if (rootPath != null) {
            // Sandboxed: make relative to root. A path outside the root has no virtual path:
            // it used to come back as the full OS path, or as "/../.." (another file once normalized)
            java.nio.file.Path normalized = osPath.toAbsolutePath().normalize();
            if (!normalized.startsWith(rootPath)) {
                throw new IllegalArgumentException("Path is outside the filesystem root: " + osPath);
            }
            pathString = rootPath.relativize(normalized).toString();
        } else {
            pathString = osPath.toString();
        }
        
        // Replace OS separator with /
        if (!"/".equals(FileSystems.getDefault().getSeparator())) {
            pathString = pathString.replace(FileSystems.getDefault().getSeparator(), "/");
        }
        
        // Ensure leading /
        if (!pathString.startsWith("/")) {
            pathString = "/" + pathString;
        }
        
        return pathString;
    }
    
    @Override
    public Iterable<Path> getRootDirectories() {
        checkOpen();
        if (rootPath != null) {
            // Sandboxed: return virtual root
            return Collections.singletonList(new PathPath(this, "/"));
        } else {
            // Unrestricted: return system roots
            List<Path> roots = new ArrayList<>();
            for (java.nio.file.Path root : FileSystems.getDefault().getRootDirectories()) {
                roots.add(new PathPath(this, toVirtualPath(root)));
            }
            return roots;
        }
    }
    
    @Override
    public Iterable<FileStore> getFileStores() {
        checkOpen();
        if (rootPath != null) {
            // Sandboxed: return store for root path
            try {
                return Collections.singletonList(Files.getFileStore(rootPath));
            } catch (IOException e) {
                return Collections.emptyList();
            }
        } else {
            // Unrestricted: return all system file stores
            List<FileStore> stores = new ArrayList<>();
            for (java.nio.file.Path root : FileSystems.getDefault().getRootDirectories()) {
                try {
                    stores.add(Files.getFileStore(root));
                } catch (IOException e) {
                    // Skip this store
                }
            }
            return stores;
        }
    }
    
    @Override
    protected AbstractPath createPath(String path) {
        return new PathPath(this, path);
    }
}
