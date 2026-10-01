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
package org.monflabs.filesystem.delegate;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;

import org.monflabs.filesystem.AbstractFileSystem;
import org.monflabs.filesystem.Sandbox;
import org.monflabs.filesystem.AbstractPath;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.path.FileSystemRuntimeException;

/**
 * FileSystem implementation that delegates to another Path as root.
 */
public class PathDelegatingFileSystem extends AbstractFileSystem {

	public static class Builder extends ObjectBuilder<PathDelegatingFileSystem> {
		
		private PathDelegatingFileSystemProvider provider;
		private URI uri;
		private Path root;
		
		public Builder provider(PathDelegatingFileSystemProvider provider) {
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
		protected PathDelegatingFileSystem _build() {
			try {
				PathDelegatingFileSystemProvider p = provider!=null ? provider : DEFAULT_PROVIDER;
				URI u = uri!=null ? uri : DEFAULT_URI;
				HashMap<String,Object> env = new HashMap<>();
		        env.put(PathDelegatingFileSystemProvider.ROOT_PATH_PARAM, root);
				return (PathDelegatingFileSystem)p.newFileSystem(u, env);
			} catch(IOException ex) {
				throw new FileSystemRuntimeException(ex);
			}
		}
	}
	
	public static Builder newBuilder() {
		return new Builder();
	}

	public static final PathDelegatingFileSystemProvider DEFAULT_PROVIDER = new PathDelegatingFileSystemProvider(false);
	/** @deprecated misspelled: use {@link #DEFAULT_PROVIDER} */
	@Deprecated
	public static final PathDelegatingFileSystemProvider DEFAUT_PROVIDER = DEFAULT_PROVIDER;
	public static final URI DEFAULT_URI = URI.create(PathDelegatingFileSystemProvider.SCHEME + ":///");
    
    private final Path rootPath;
    private final Sandbox.RealRoot realRoot;
    
    public PathDelegatingFileSystem(PathDelegatingFileSystemProvider provider, URI uri, Path rootPath) {
        super(provider, uri, rootPath.getFileSystem().getSeparator());
        this.rootPath = rootPath;
        this.realRoot = new Sandbox.RealRoot(rootPath);
    }

    /**
     * The real path of the root (symbolic links resolved), resolved once.
     */
    public Path getRealRootPath() throws java.io.IOException {
        return realRoot.get();
    }
    
    /**
     * Get the root path that this filesystem delegates to.
     */
    public Path getRootPath() {
        return rootPath;
    }
    
    @Override
    public Iterable<Path> getRootDirectories() {
        checkOpen();
        // Return our virtual root "/"
        return Collections.singletonList(createPath("/"));
    }
    
    @Override
    public Iterable<FileStore> getFileStores() {
        checkOpen();
        try {
            // Return the file store of the root path
            return Collections.singletonList(Files.getFileStore(rootPath));
        } catch (IOException e) {
            // Return empty list if we can't get the file store
            return Collections.emptyList();
        }
    }
    
    /**
     * The views of the delegate filesystem: attributes are read and written on its files.
     */
    @Override
    public java.util.Set<String> supportedFileAttributeViews() {
        return rootPath.getFileSystem().supportedFileAttributeViews();
    }

    @Override
    protected AbstractPath createPath(String path) {
        return new PathDelegatingPath(this, path);
    }
    
    /**
     * Get information about the delegate filesystem.
     */
    public String getDelegateInfo() {
        return "Delegating to: " + rootPath + " on " + rootPath.getFileSystem().getClass().getSimpleName();
    }
}
