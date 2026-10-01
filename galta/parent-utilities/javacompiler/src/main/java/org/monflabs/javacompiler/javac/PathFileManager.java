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
package org.monflabs.javacompiler.javac;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;

import org.monflabs.javacompiler.FactoryClassLoader;
import org.monflabs.javacompiler.TargetFactory;

/**
 * File manager connecting javac to the source and target factories.
 * <ul>
 * <li>The class files are written to the target factory. The generated sources (annotation
 * processors) are kept in memory, and other outputs go to the standard file manager.
 * <li>The class path seen by javac is extended with the classes of the target factory and of
 * the factories of the parent {@link FactoryClassLoader}s, so a compilation can reference
 * classes produced by earlier compilations. This requires factories that implement
 * {@link TargetFactory#listClassFiles(String)}.
 * </ul>
 */
public class PathFileManager extends ForwardingJavaFileManager<JavaFileManager> {

	private TargetFactory targetFactory;
	private ClassLoader cl;
	private List<TargetFactory> classPathFactories;

	protected PathFileManager(JavaFileManager fileManager, TargetFactory targetFactory, ClassLoader cl) {
		super(fileManager);
		this.targetFactory = targetFactory;
		this.cl = cl;
		this.classPathFactories = new ArrayList<>();
		classPathFactories.add(targetFactory);
		for(ClassLoader c=cl; c!=null; c=c.getParent()) {
			if(c instanceof FactoryClassLoader f && !classPathFactories.contains(f.getFileFactory())) {
				classPathFactories.add(f.getFileFactory());
			}
		}
	}

	@Override
	public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className,
			JavaFileObject.Kind kind, FileObject sibling) throws IOException {
		if(location==StandardLocation.CLASS_OUTPUT) {
			return new PathTargetFile(targetFactory, className, kind);
		}
		if(location==StandardLocation.SOURCE_OUTPUT) {
			return new MemoryOutputFile(className.replace('.', '/') + kind.extension, kind);
		}
		return super.getJavaFileForOutput(location, className, kind, sibling);
	}
	
	@Override
	public FileObject getFileForOutput(Location location, String packageName, String relativeName, FileObject sibling) throws IOException {
		String fileName = packageName.isEmpty() ? relativeName : packageName.replace('.', '/') + "/" + relativeName;
		if(location==StandardLocation.CLASS_OUTPUT) {
			return new PathTargetFile(targetFactory, fileName, JavaFileObject.Kind.OTHER, true);
		}
		if(location==StandardLocation.SOURCE_OUTPUT) {
			return new MemoryOutputFile(fileName, JavaFileObject.Kind.OTHER);
		}
		return super.getFileForOutput(location, packageName, relativeName, sibling);
	}

	@Override
	public ClassLoader getClassLoader(JavaFileManager.Location location) {
		return cl;
	}
	
	@Override
	public Iterable<JavaFileObject> list(Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
		Iterable<JavaFileObject> std = super.list(location, packageName, kinds, recurse);
		if(location!=StandardLocation.CLASS_PATH || !kinds.contains(JavaFileObject.Kind.CLASS)) {
			return std;
		}
		// The compiled classes come first: they take precedence, as in FactoryClassLoader
		List<JavaFileObject> result = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		String folder = packageName.replace('.', '/');
		for(TargetFactory f: classPathFactories) {
			for(String fileName: f.listClassFiles(folder, recurse)) {
				if(seen.add(fileName)) {
					result.add(new FactoryClassFile(f, fileName));
				}
			}
		}
		if(result.isEmpty()) {
			return std;
		}
		for(JavaFileObject o: std) {
			result.add(o);
		}
		return result;
	}
	
	@Override
	public String inferBinaryName(Location location, JavaFileObject file) {
		if(file instanceof FactoryClassFile f) {
			return f.binaryName;
		}
		return super.inferBinaryName(location, file);
	}
	
	@Override
	public boolean isSameFile(FileObject a, FileObject b) {
		if(a instanceof FactoryClassFile || b instanceof FactoryClassFile || a instanceof MemoryOutputFile || b instanceof MemoryOutputFile) {
			return a.toUri().equals(b.toUri());
		}
		return super.isSameFile(a, b);
	}
	
	/**
	 * A class file read from a target factory.
	 */
	private static class FactoryClassFile extends SimpleJavaFileObject {
		private final TargetFactory factory;
		private final String fileName;
		private final String binaryName;
		FactoryClassFile(TargetFactory factory, String fileName) {
			super(URI.create("factory:///" + fileName), Kind.CLASS);
			this.factory = factory;
			this.fileName = fileName;
			this.binaryName = fileName.substring(0, fileName.length()-Kind.CLASS.extension.length()).replace('/', '.');
		}
		@Override
		public InputStream openInputStream() throws IOException {
			byte[] bytes = factory.readBytes(fileName);
			if(bytes==null) {
				throw new IOException("Class file not found: "+fileName);
			}
			return new ByteArrayInputStream(bytes);
		}
	}
	
	/**
	 * A generated file kept in memory, readable back by javac (generated sources are compiled).
	 */
	private static class MemoryOutputFile extends SimpleJavaFileObject {
		private volatile byte[] content = new byte[0];
		MemoryOutputFile(String fileName, Kind kind) {
			super(URI.create("mem:///" + fileName), kind);
		}
		@Override
		public OutputStream openOutputStream() {
			return new ByteArrayOutputStream() {
				@Override
				public void close() throws IOException {
					content = toByteArray();
				}
			};
		}
		@Override
		public InputStream openInputStream() {
			return new ByteArrayInputStream(content);
		}
		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) {
			return new String(content, java.nio.charset.StandardCharsets.UTF_8);
		}
	}
}
