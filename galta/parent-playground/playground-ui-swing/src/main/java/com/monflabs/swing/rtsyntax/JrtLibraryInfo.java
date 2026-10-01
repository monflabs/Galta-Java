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
package com.monflabs.swing.rtsyntax;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.fife.rsta.ac.java.PackageMapNode;
import org.fife.rsta.ac.java.buildpath.LibraryInfo;
import org.fife.rsta.ac.java.classreader.ClassFile;

/**
 * The runtime classes of the running JVM, read from its own module image
 * through the {@code jrt:/} file system - for a JDK without a {@code jmods}
 * folder (a JRE, a jlink image, a JDK 24+ built with JEP 493). Only the
 * {@code java.*} and {@code jdk.*} modules are read, like {@link Jdk9LibraryInfo}.
 */
public class JrtLibraryInfo extends LibraryInfo {

	/**
	 * The package index, shared by the clones of this library info and
	 * guarded by itself.
	 */
	private static final class Index {
		Map<String,Path> packages;	// "java/lang" -> /modules/java.base
		PackageMapNode packageMap;
	}

	private final FileSystem jrt;
	private final Index index = new Index();

	public JrtLibraryInfo() {
		this.jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
	}

	private void index() throws IOException {
		if(index.packages!=null) {
			return;
		}
		PackageMapNode root = new PackageMapNode();
		Map<String,Path> packages = new HashMap<>();
		try (DirectoryStream<Path> modules = Files.newDirectoryStream(jrt.getPath("/modules"))) {
			for(Path module: modules) {
				String name = module.getFileName().toString();
				if(!name.startsWith("java.") && !name.startsWith("jdk.")) {
					continue;
				}
				try (Stream<Path> files = Files.walk(module)) {
					files.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
						String entryName = module.relativize(p).toString();
						root.add(entryName);
						packages.putIfAbsent(Jdk9LibraryInfo.packageOf(entryName), module);
					});
				}
			}
		}
		index.packageMap = root;
		index.packages = packages;
	}

	@Override
	public PackageMapNode createPackageMap() throws IOException {
		synchronized(index) {
			index();
			return index.packageMap;
		}
	}

	@Override
	public ClassFile createClassFile(String entryName) throws IOException {
		Path module;
		synchronized(index) {
			index();
			module = index.packages.get(Jdk9LibraryInfo.packageOf(entryName));
		}
		if(module==null) {
			return null;
		}
		Path file = module.resolve(entryName);
		if(!Files.isRegularFile(file)) {
			return null;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
			return new ClassFile(in);
		}
	}

	@Override
	public ClassFile createClassFileBulk(String entryName) throws IOException {
		return createClassFile(entryName);
	}

	@Override
	public void bulkClassFileCreationStart() {
	}

	@Override
	public void bulkClassFileCreationEnd() {
	}

	@Override
	public int compareTo(LibraryInfo info) {
		if(info instanceof JrtLibraryInfo) {
			return 0;	// one runtime image
		}
		// A total order with the other kinds (it used to answer -1 both ways)
		return LibraryInfo2.compare(this, info);
	}

	@Override
	public long getLastModified() {
		return 0;
	}

	@Override
	public String getLocationAsString() {
		return "jrt:/";
	}

	@Override
	public int hashCodeImpl() {
		return JrtLibraryInfo.class.hashCode();
	}

	@Override
	public String toString() {
		return "[JrtLibraryInfo: jrt:/; source=" + getSourceLocation() + "]";
	}
}
