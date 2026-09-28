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

import org.monflabs.filesystem.AbstractFileSystem;
import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.filesystem.memory.MemoryFileSystemProvider;
import org.monflabs.util.path.FileSystemRuntimeException;

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
	
	//private static final String PROP_FILE = "file";
	
	private Map<String,Path> physicalFiles = new HashMap<>();

	
	private MemoryFileSystemSnippet(MemoryFileSystemProvider provider, URI uri) {
		super(provider, uri);
	}
	
//	public Path getPhysicalFile(Path path) {
//		MemoryFileNode node = getNode((MemoryPath)path);
//		return node!=null ? (Path)node.getProperty(PROP_FILE) : null;
//	}
//	public void setPhysicalFile(Path path, Path file) {
//		MemoryFileNode node = getNode((MemoryPath)path);
//		if(node!=null) {
//			node.putProperty(PROP_FILE, file);
//		}
//	}
	
	
	// Keyed by the absolute form so a lookup finds an entry regardless of
	// whether the given Path was constructed as "main.js" (as
	// Snippet.createSnippetFs() does) or resolved from the root as
	// "/main.js" (as Files.list(root) does, e.g. in PlaygroundFrame.saveSnippet())
	// - both must map to the SAME physical file, but Path.toString() alone
	// reflects how the Path was built, not where it points, so the two
	// otherwise never match and every save silently no-ops.
	public Path getPhysicalFile(Path file) {
		return physicalFiles.get(file.toAbsolutePath().toString());
	}
	public void setPhysicalFile(Path file, Path path) {
		physicalFiles.put(file.toAbsolutePath().toString(),path);
	}
}
