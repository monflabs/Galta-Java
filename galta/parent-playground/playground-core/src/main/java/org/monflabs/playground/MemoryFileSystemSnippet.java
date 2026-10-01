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
package org.monflabs.playground;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.monflabs.filesystem.AbstractFileSystem;
import org.monflabs.filesystem.memory.MemoryFileNode;
import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.filesystem.memory.MemoryFileSystemProvider;
import org.monflabs.filesystem.memory.MemoryPath;
import org.monflabs.util.path.FileSystemRuntimeException;
import org.monflabs.util.path.FilesUtil;

/**
 * The in-memory copy of a snippet's files: each file remembers the physical
 * file it comes from, and the content it had when it was loaded (to detect a
 * modification made by someone else before saving).
 */
public class MemoryFileSystemSnippet extends MemoryFileSystem {

	public static MemoryFileSystemSnippet create() {
		try {
			return (MemoryFileSystemSnippet)provider.newFileSystem(MemoryFileSystem.DEFAULT_URI, new HashMap<>());
		} catch(IOException ex) {
			throw new FileSystemRuntimeException(ex);
		}
	}

	private static final MemoryFileSystemProvider provider = new MemoryFileSystemProvider(false) {
	    @Override
	    protected AbstractFileSystem createFileSystem(URI uri, Map<String, ?> env) throws IOException {
	        return new MemoryFileSystemSnippet(this, uri);
	    }
	};

	// Keyed by the absolute form so a lookup finds an entry regardless of
	// whether the given Path was constructed as "main.js" (as
	// Snippet.createSnippetFs() does) or resolved from the root as
	// "/main.js" (as Files.list(root) does, e.g. when saving) - both must map
	// to the SAME physical file, but Path.toString() alone reflects how the
	// Path was built, not where it points.
	private final Map<String,Path> physicalFiles = new ConcurrentHashMap<>();
	private final Map<String,byte[]> loadedContents = new ConcurrentHashMap<>();


	private MemoryFileSystemSnippet(MemoryFileSystemProvider provider, URI uri) {
		super(provider, uri);
	}

	private static String key(Path file) {
		return file.toAbsolutePath().normalize().toString();
	}

	/**
	 * The physical file an in-memory file was loaded from (or is saved to),
	 * or null when it only exists in memory.
	 */
	public Path getPhysicalFile(Path file) {
		return physicalFiles.get(key(file));
	}
	public void setPhysicalFile(Path file, Path path) {
		physicalFiles.put(key(file),path);
	}

	/**
	 * Records the physical file of an in-memory file, and its content as it
	 * was read from it.
	 */
	public void setPhysicalFile(Path file, Path path, byte[] loadedContent) {
		setPhysicalFile(file, path);
		setLoadedContent(file, loadedContent);
	}

	/**
	 * The content of the physical file when it was loaded or last saved, or
	 * null when unknown (a file created in memory).
	 */
	public byte[] getLoadedContent(Path file) {
		byte[] b = loadedContents.get(key(file));
		return b!=null ? b.clone() : null;
	}
	public void setLoadedContent(Path file, byte[] content) {
		if(content!=null) {
			loadedContents.put(key(file), content.clone());
		} else {
			loadedContents.remove(key(file));
		}
	}

	private MemoryFileNode fileNode(Path file) {
		if(file instanceof MemoryPath mp && mp.getFileSystem()==this) {
			MemoryFileNode node = getNode((MemoryPath)mp.toAbsolutePath().normalize());
			if(node!=null && node.isFile()) {
				return node;
			}
		}
		return null;
	}

	/**
	 * The content of an in-memory file, read in one step (never a partial
	 * content), or null when there is no such file.
	 */
	public byte[] readBytes(Path file) {
		MemoryFileNode node = fileNode(file);
		if(node!=null) {
			return node.getContent();
		}
		return null;
	}

	/**
	 * Replaces the content of an in-memory file in one step: a concurrent
	 * reader sees the old content or the new one, never an empty or partial
	 * file (writing through a stream truncates the file first). The file is
	 * created when it does not exist.
	 */
	public void writeBytes(Path file, byte[] content) {
		MemoryFileNode node = fileNode(file);
		if(node!=null) {
			node.setContent(content.clone());
		} else {
			FilesUtil.write(file, content);
		}
	}
}
