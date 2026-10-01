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
package org.monflabs.filesystem.memory;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.monflabs.filesystem.AbstractFileSystem;
import org.monflabs.filesystem.AbstractPath;
import org.monflabs.util.ObjectBuilder;
import org.monflabs.util.path.FileSystemRuntimeException;

/**
 * In-memory FileSystem implementation.
 */
public class MemoryFileSystem extends AbstractFileSystem {
	
	public static class Builder extends ObjectBuilder<MemoryFileSystem> {
		
		private MemoryFileSystemProvider provider;
		private URI uri;
		
		public Builder provider(MemoryFileSystemProvider provider) {
			this.provider = provider;
			return this;
		}
		public Builder uri(URI uri) {
			this.uri = uri;
			return this;
		}
		
		@Override
		protected MemoryFileSystem _build() {
			try {
				MemoryFileSystemProvider p = provider!=null ? provider : DEFAULT_PROVIDER;
				URI u = uri!=null ? uri : DEFAULT_URI;
				HashMap<String,Object> env = new HashMap<>();
				return (MemoryFileSystem)p.newFileSystem(u, env);
			} catch(IOException ex) {
				throw new FileSystemRuntimeException(ex);
			}
		}
	}
	
	public static Builder newBuilder() {
		return new Builder();
	}
	
	public static final MemoryFileSystemProvider DEFAULT_PROVIDER = new MemoryFileSystemProvider(false);
	/** @deprecated misspelled: use {@link #DEFAULT_PROVIDER} */
	@Deprecated
	public static final MemoryFileSystemProvider DEFAUT_PROVIDER = DEFAULT_PROVIDER;
	public static final URI DEFAULT_URI = URI.create(MemoryFileSystemProvider.SCHEME + ":///");
    
    private final Map<String, MemoryFileNode> nodes = new ConcurrentHashMap<>();
    // Bytes held by the files of the index, kept up to date by the nodes themselves
    private final AtomicLong usedSpace = new AtomicLong();
    // Guards structural changes (create/delete/move): a check-then-create sequence in
    // the provider must hold it, or two CREATE_NEW opens of the same path both succeed
    final Object structureLock = new Object();
    private final MemoryFileNode root;
    
    public MemoryFileSystem(MemoryFileSystemProvider provider, URI uri) {
        super(provider, uri, "/");
        
        // Create root directory
        this.root = new MemoryFileNode("/", true);
        nodes.put("/", root);
    }
    
    @Override
    public Iterable<Path> getRootDirectories() {
        checkOpen();
        return Collections.singletonList(createPath("/"));
    }
    
    @Override
    public Iterable<FileStore> getFileStores() {
        checkOpen();
        return Collections.singletonList(new MemoryFileStore(this));
    }
    
    @Override
    protected AbstractPath createPath(String path) {
        return new MemoryPath(this, path);
    }
    
    /**
     * Get the node at the given path.
     */
    public MemoryFileNode getNode(MemoryPath path) {
        String p = path.toString();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        if (needsNormalization(p)) {
            try {
                p = resolveDots(p);
            } catch (IOException e) {
                // "/d/x/.." with /d/x a file, or missing: no such node
                return null;
            }
        }
        return nodes.get(normalizePath(p));
    }

    /**
     * The path of the node a path names: absolute, with "." and ".." resolved against the
     * actual nodes, as an operating system does. "/d/x/../y" is "/d/y" only when /d/x is an
     * existing directory: a lexical normalization used to accept it with /d/x a file, or
     * missing (so delete("/d/x/..") deleted /d).
     *
     * @throws NoSuchFileException if a directory followed by "." or ".." does not exist
     * @throws FileSystemException ("Not a directory") if it is a file
     */
    public MemoryPath resolveNodePath(MemoryPath path) throws IOException {
        MemoryPath absolute = (MemoryPath) path.toAbsolutePath();
        String p = absolute.toString();
        if (!needsNormalization(p)) {
            return absolute;
        }
        return (MemoryPath) createPath(resolveDots(p));
    }

    private String resolveDots(String absolutePath) throws IOException {
        List<String> names = new ArrayList<>();
        int len = absolutePath.length();
        int start = 0;
        for (int i = 0; i <= len; i++) {
            if (i == len || absolutePath.charAt(i) == '/') {
                String segment = absolutePath.substring(start, i);
                start = i + 1;
                if (segment.isEmpty()) {
                    continue;
                }
                boolean dot = segment.equals(".");
                boolean dotDot = segment.equals("..");
                if (dot || dotDot) {
                    // What precedes "." or ".." must be an existing directory
                    String current = "/" + String.join("/", names);
                    MemoryFileNode node = nodes.get(current);
                    if (node == null) {
                        throw new NoSuchFileException(absolutePath);
                    }
                    if (!node.isDirectory()) {
                        throw new FileSystemException(absolutePath, null, "Not a directory");
                    }
                    if (dotDot && !names.isEmpty()) {
                        names.remove(names.size() - 1);
                    }
                } else {
                    names.add(segment);
                }
            }
        }
        return "/" + String.join("/", names);
    }
    
    /**
     * Register a node and all of its descendants in the flat path index.
     * The tree (children maps) and the index must stay in sync: a copied directory
     * used to be indexed without its children, so they were listed but "did not exist".
     */
    private void indexSubtree(String nodePath, MemoryFileNode node) {
        nodes.put(nodePath, node);
        node.attach(usedSpace);
        if (node.isDirectory()) {
            String prefix = nodePath.equals("/") ? "/" : nodePath + "/";
            for (Map.Entry<String, MemoryFileNode> e : node.getChildren().entrySet()) {
                indexSubtree(prefix + e.getKey(), e.getValue());
            }
        }
    }
    
    private void unindexSubtree(String nodePath, MemoryFileNode node) {
        nodes.remove(nodePath);
        node.detach();
        if (node.isDirectory()) {
            String prefix = nodePath.equals("/") ? "/" : nodePath + "/";
            for (Map.Entry<String, MemoryFileNode> e : node.getChildren().entrySet()) {
                unindexSubtree(prefix + e.getKey(), e.getValue());
            }
        }
    }
    
    /**
     * Create a new node at the given path.
     */
    public void createNode(MemoryPath path, MemoryFileNode node) {
        synchronized (structureLock) {
            _createNode(path, node);
        }
    }
    
    private void _createNode(MemoryPath path, MemoryFileNode node) {
        path = (MemoryPath) path.toAbsolutePath().normalize();
        String normalizedPath = normalizePath(path.toString());
        
        // Add to parent's children
        MemoryPath parentPath = (MemoryPath) path.getParent();
        if (parentPath != null) {
            String parentNormalized = normalizePath(parentPath.toString());
            MemoryFileNode parent = nodes.get(parentNormalized);
            if (parent != null && parent.isDirectory()) {
                parent.addChild(path.getFileName().toString(), node);
            }
        }
        
        indexSubtree(normalizedPath, node);
    }
    
    /**
     * Move a node within this filesystem: the node itself is unlinked from its parent and
     * linked under the new one with the target's name, and its subtree re-indexed. Nothing
     * is copied, so the move is O(number of nodes) whatever the size of the files, open
     * channels keep reading and writing the moved file, and its fileKey is unchanged.
     * The target must not exist and its parent must be an existing directory.
     */
    void moveNode(MemoryPath source, MemoryPath target) {
        synchronized (structureLock) {
            source = (MemoryPath) source.toAbsolutePath().normalize();
            target = (MemoryPath) target.toAbsolutePath().normalize();
            String sourcePath = normalizePath(source.toString());
            String targetPath = normalizePath(target.toString());
            MemoryFileNode node = nodes.get(sourcePath);
            if (node == null) {
                return;
            }
            MemoryFileNode sourceParent = nodes.get(normalizePath(source.getParent().toString()));
            MemoryFileNode targetParent = nodes.get(normalizePath(target.getParent().toString()));
            unindexSubtree(sourcePath, node);
            if (sourceParent != null) {
                sourceParent.removeChild(source.getFileName().toString());
            }
            String name = target.getFileName().toString();
            node.setName(name);
            targetParent.addChild(name, node);
            indexSubtree(targetPath, node);
        }
    }

    /**
     * Delete the node at the given path.
     */
    public void deleteNode(MemoryPath path) {
        synchronized (structureLock) {
            _deleteNode(path);
        }
    }
    
    private void _deleteNode(MemoryPath path) {
        path = (MemoryPath) path.toAbsolutePath().normalize();
        String normalizedPath = normalizePath(path.toString());
        
        // Remove from parent's children
        MemoryPath parentPath = (MemoryPath) path.getParent();
        if (parentPath != null) {
            String parentNormalized = normalizePath(parentPath.toString());
            MemoryFileNode parent = nodes.get(parentNormalized);
            if (parent != null && parent.isDirectory()) {
                parent.removeChild(path.getFileName().toString());
            }
        }
        
        MemoryFileNode node = nodes.get(normalizedPath);
        if (node != null) {
            // Drop the descendants from the index too, or they linger as ghost entries
            unindexSubtree(normalizedPath, node);
        }
    }
    
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        
        // Resolve "." and ".." (and collapse "//") so "/a/../b" is looked up as "/b"
        if (needsNormalization(path)) {
            path = createPath(path.startsWith("/") ? path : "/" + path).normalize().toString();
        }
        
        // Remove trailing slashes except for root
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        
        // Ensure path starts with /
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        
        return path;
    }
    
    // True for a "//", or a "." or ".." segment - not for a dot in a file name
    private static boolean needsNormalization(String path) {
        int len = path.length();
        int segStart = 0;
        for (int i = 0; i <= len; i++) {
            if (i == len || path.charAt(i) == '/') {
                int segLen = i - segStart;
                if (segLen == 0) {
                    // "//" (an empty segment that is not the leading or trailing slash)
                    if (i > 0 && i < len) {
                        return true;
                    }
                } else if (path.charAt(segStart) == '.' && (segLen == 1 || (segLen == 2 && path.charAt(segStart + 1) == '.'))) {
                    return true;
                }
                segStart = i + 1;
            }
        }
        return false;
    }
    
    /**
     * Get total number of bytes used by all files.
     */
    public long getTotalUsedSpace() {
        // A running total: summing the sizes of all the nodes made every
        // FileStore.getUsableSpace() call O(number of files)
        return usedSpace.get();
    }
    
    /**
     * Get total number of nodes (files and directories).
     */
    public int getTotalNodeCount() {
        return nodes.size();
    }
}
