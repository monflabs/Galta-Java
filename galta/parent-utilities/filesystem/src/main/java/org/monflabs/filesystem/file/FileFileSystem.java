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
import java.nio.file.FileStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.monflabs.filesystem.AbstractFileSystem;
import org.monflabs.filesystem.AbstractPath;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.path.FileSystemRuntimeException;

/**
 * FileSystem implementation that delegates to java.io.File API.
 * Supports optional root directory for sandboxing.
 */
public class FileFileSystem extends AbstractFileSystem {

	public static class Builder extends ObjectBuilder<FileFileSystem> {
		
		private FileFileSystemProvider provider;
		private URI uri;
		private File root;
		
		public Builder provider(FileFileSystemProvider provider) {
			this.provider = provider;
			return this;
		}
		public Builder uri(URI uri) {
			this.uri = uri;
			return this;
		}
		public Builder root(File root) {
			this.root = root;
			return this;
		}
		
		@Override
		protected FileFileSystem _build() {
			try {
				FileFileSystemProvider p = provider!=null ? provider : DEFAUT_PROVIDER;
				URI u = uri!=null ? uri : DEFAULT_URI;
				HashMap<String,Object> env = new HashMap<>();
		        env.put(FileFileSystemProvider.ROOT_PARAM, root);
				return (FileFileSystem)p.newFileSystem(u, env);
			} catch(IOException ex) {
				throw new FileSystemRuntimeException(ex);
			}
		}
	}
	
	public static Builder newBuilder() {
		return new Builder();
	}

	public static final FileFileSystemProvider DEFAUT_PROVIDER = new FileFileSystemProvider(false);
	public static final URI DEFAULT_URI = URI.create(FileFileSystemProvider.SCHEME + ":///");
    
    private final File root;
    
    public FileFileSystem(FileFileSystemProvider provider, URI uri, File root) {
        super(provider, uri, File.separator);
        this.root = root;
    }
    
    /**
     * Get the root directory of this filesystem (may be null for unrestricted).
     */
	public File getRoot() {
        return root;
    }
    
    @Override
    public Iterable<Path> getRootDirectories() {
        checkOpen();
        if (root != null) {
            // When sandboxed, return the sandbox root as "/"
            return Collections.singletonList(new FilePath(this, "/"));
        } else {
            // No sandbox, return all system roots
            File[] roots = File.listRoots();
            List<Path> rootPaths = new ArrayList<>();
            for (File root : roots) {
                rootPaths.add(new FilePath(this, root.getAbsolutePath()));
            }
            return rootPaths;
        }
    }
    
    @Override
    public Iterable<FileStore> getFileStores() {
        checkOpen();
        if (root != null) {
            // When sandboxed, return store for the root directory
            return Collections.singletonList(new FileBasedFileStore(root));
        } else {
            // No sandbox, return all system file stores
            File[] roots = File.listRoots();
            List<FileStore> stores = new ArrayList<>();
            for (File root : roots) {
                stores.add(new FileBasedFileStore(root));
            }
            return stores;
        }
    }
    
    @Override
    protected AbstractPath createPath(String path) {
        return new FilePath(this, path);
    }
}
