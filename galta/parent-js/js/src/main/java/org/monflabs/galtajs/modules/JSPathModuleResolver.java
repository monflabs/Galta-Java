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
package org.monflabs.galtajs.modules;

import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.monflabs.galtajs.JSException;
import org.monflabs.galtajs.JSModuleDescriptor;
import org.monflabs.util.path.FilesUtil;

/**
 * 
 */
public class JSPathModuleResolver extends JSSourceModuleResolver {

	private class Descriptor extends BaseDescriptor {
		
		private Path path;
		private Charset cs;

		private Descriptor(String name, Path path, Charset cs) {
			super(name);
			this.path = path;
			this.cs = cs;
		}
	
		@Override
		public boolean isScript() {
			return true;
		}
	
		@Override
		public String getScript() {
			return FilesUtil.readString(path,cs);
		}

		// `with { type: "bytes" }` - the file's bytes, untouched (no charset
		// decoding: the file may well be binary).
		@Override
		public byte[] getBytes() {
			try {
				return Files.readAllBytes(path);
			} catch(java.io.IOException ex) {
				throw new JSException(ex);
			}
		}
	}

	private Path rootPath;
	private Charset cs;
	
	public JSPathModuleResolver(FileSystem fs) {
		this.rootPath = FilesUtil.getRoot(fs);
	}
	public JSPathModuleResolver(FileSystem fs, Charset cs) {
		this.rootPath = FilesUtil.getRoot(fs);
		this.cs = cs;
	}
	public JSPathModuleResolver(Path root) {
		this.rootPath = root;
	}
	public JSPathModuleResolver(Path root, Charset cs) {
		this.rootPath = root;
		this.cs = cs;
	}
	
	public Path getRootPath() {
		return rootPath;
	}
	
	@Override
	protected JSModuleDescriptor findModule(String name) {
		// Only files inside the root: "../x.js" (or "..\\x.js" on Windows) must
		// not reach outside it, and a directory is not a module (so that the
		// ".js" fallback gets its chance for "./lib" next to "lib.js")
		Path root = rootPath.toAbsolutePath().normalize();
		Path f = root.resolve(name).normalize();
		if(!f.startsWith(root) || !Files.isRegularFile(f)) {
			return null;
		}
		return new Descriptor(name,f,cs);
	}
	
	@Override
	public Stream<JSModuleDescriptor> getModules() {
		try {
			String sep = rootPath.getFileSystem().getSeparator();
			int maxDepth = 1;
			// Collected while the directory stream is open: a lazily returned
			// walk would keep the directory handle open until the caller closes it
			try(Stream<Path> walk = Files.walk(rootPath, maxDepth)) {
				List<JSModuleDescriptor> modules = walk
						.filter(Files::isRegularFile)
						.filter((f) -> f.toString().endsWith(EXTENSION))
						.map( (f) -> (JSModuleDescriptor)new Descriptor(moduleNameFromPath(f,sep),f,cs) )
						.toList();
				return modules.stream();
			}
		} catch(Exception ex) {
			throw new JSException(ex);
		}
	}
	private String moduleNameFromPath(Path p, String sep) {
		String relPath = rootPath.relativize(p).toString();
		if(relPath.startsWith(sep)) {
			relPath = relPath.substring(sep.length());
		}
		return relPath;
	}
}
