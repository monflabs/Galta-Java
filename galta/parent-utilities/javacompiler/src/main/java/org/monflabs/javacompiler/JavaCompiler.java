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
package org.monflabs.javacompiler;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * Java compiler that works out a FileSystem.
 * 
 * @author priand
 *
 */
public abstract class JavaCompiler implements Closeable {
	
	private SourceFactory sourceFactory;
	private TargetFactory targetFactory;
	private ClassLoader parentClassLoader;
	private FactoryClassLoader classLoader;
	private boolean compiledSinceLoader;
	private List<String> options;
	private List<String> warnings = Collections.emptyList();
	
	public JavaCompiler(ClassLoader parentClassLoader, SourceFactory sourceFactory, TargetFactory targetFactory, List<String> options) {
		this.sourceFactory = sourceFactory;
		this.targetFactory = targetFactory;
		this.parentClassLoader = parentClassLoader;
		this.classLoader = new FactoryClassLoader(parentClassLoader, targetFactory);
		this.options = options;
	}
	
	
	@Override
	public void close() {
		// Just to declare no exception
	}
	
	public SourceFactory getSourceFactory() {
		return sourceFactory;
	}
	
	public TargetFactory getTargetFactory() {
		return targetFactory;
	}
	
	/**
	 * The parent class loader, as passed to the builder.
	 * @return the class loader
	 */
	public ClassLoader getParentClassLoader() {
		return parentClassLoader;
	}
	
	/**
	 * Return a class loader for the compiled classes.
	 * <p>
	 * A class loader defines a class only once. When a compilation happened after the current
	 * class loader defined some classes, a new class loader is returned, so the recompiled
	 * classes are loaded in their latest version. Classes loaded through a previously returned
	 * class loader are not affected.
	 * @return the class loader
	 */
	public synchronized FactoryClassLoader getClassLoader() {
		if(compiledSinceLoader && classLoader.hasDefinedClasses()) {
			classLoader = new FactoryClassLoader(parentClassLoader, targetFactory);
		}
		compiledSinceLoader = false;
		return classLoader;
	}
	
	public List<String> getOptions() {
		return options;
	}
	
	/**
	 * The warnings reported by the last compilation, formatted like the error messages.
	 * <p>
	 * They are only available when the compilation does not fail on warnings.
	 * @return the list of warnings, empty if none
	 */
	public synchronized List<String> getWarnings() {
		return warnings;
	}

	public void compile(String sourceFile) {
		List<String> files = Collections.singletonList(sourceFile);
		compile(files);
	}
	public void compile(String...sourceFiles) {
		List<String> files = Arrays.asList(sourceFiles);
		compile(files);
	}

	/**
	 * Compile a set of sources.
	 * <p>
	 * The compilations of a compiler instance are serialized: calls from different threads
	 * are executed one after the other.
	 * @param sources the source names, as class names ("com.acme.Greeter")
	 */
	public final synchronized void compile(List<String> sources) {
		warnings = Collections.emptyList();
		try {
			List<String> w = doCompile(sources);
			if(w!=null && !w.isEmpty()) {
				warnings = Collections.unmodifiableList(w);
			}
		} finally {
			// Even a failed compilation may have written some classes
			compiledSinceLoader = true;
		}
	}

	/**
	 * Compile a set of sources.
	 * @param sources the source names, as class names
	 * @return the warnings, if any
	 */
	protected abstract List<String> doCompile(List<String> sources);
}
