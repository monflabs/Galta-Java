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
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


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
	// The classes written (or deleted) by the compilations since the last getClassLoader() call
	private Set<String> rewrittenClasses = new HashSet<>();
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
	 * A class loader defines a class only once. When a compilation rewrote a class that the
	 * current class loader already loaded, a new class loader is returned, so the recompiled
	 * classes are loaded in their latest version. Otherwise the same class loader is kept, so
	 * the classes it already loaded stay compatible with the newly compiled ones. Classes
	 * loaded through a previously returned class loader are not affected.
	 * <p>
	 * A compiler built with another compiler's class loader as parent keeps that loader: when
	 * the other compiler later returns a new class loader, the classes of both are no longer
	 * compatible (a <code>ClassCastException</code> or <code>NoSuchMethodError</code> can
	 * follow). Rebuild the dependent compiler, with the new class loader, in that case.
	 * @return the class loader
	 */
	public synchronized FactoryClassLoader getClassLoader() {
		for(String name: rewrittenClasses) {
			if(classLoader.alreadyLoaded(name)!=null) {
				classLoader = new FactoryClassLoader(parentClassLoader, targetFactory);
				break;
			}
		}
		rewrittenClasses.clear();
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
		if(sources==null || sources.isEmpty()) {
			throw new JavaCompilerException(null,"No source to compile");
		}
		List<String> w = doCompile(sources);
		if(w!=null && !w.isEmpty()) {
			warnings = Collections.unmodifiableList(w);
		}
	}

	/**
	 * Compile a set of sources.
	 * <p>
	 * An implementation writes its outputs with {@link #writeOutputs(Map, Collection)}, once
	 * the compilation succeeded: a failed compilation leaves the target factory unchanged.
	 * @param sources the source names, as class names
	 * @return the warnings, if any
	 */
	protected abstract List<String> doCompile(List<String> sources);

	/**
	 * Write the outputs of a successful compilation to the target factory.
	 * <p>
	 * The previous class files of the compiled top-level classes (<code>Outer.class</code>,
	 * <code>Outer$*.class</code>) that are not part of the new outputs are deleted first, when
	 * the target factory supports listing and deleting files.
	 * @param outputs the outputs, by file name relative to the target root
	 * @param sources the compiled sources, as class names
	 */
	protected void writeOutputs(Map<String,byte[]> outputs, Collection<String> sources) {
		try {
			for(String source: sources) {
				int dot = source.lastIndexOf('.');
				String folder = dot>=0 ? source.substring(0,dot).replace('.', '/') : "";
				String simple = source.substring(dot+1);
				for(String file: targetFactory.listClassFiles(folder)) {
					String name = file.substring(folder.isEmpty() ? 0 : folder.length()+1);
					if((name.equals(simple+".class") || name.startsWith(simple+"$")) && !outputs.containsKey(file)) {
						if(targetFactory.delete(file)) {
							rewrittenClasses.add(toClassName(file));
						}
					}
				}
			}
			for(Map.Entry<String,byte[]> e: outputs.entrySet()) {
				try(OutputStream os = targetFactory.openOutputStream(e.getKey())) {
					os.write(e.getValue());
				}
				if(e.getKey().endsWith(".class")) {
					rewrittenClasses.add(toClassName(e.getKey()));
				}
			}
		} catch(IOException ex) {
			throw new JavaCompilerException(ex,"Error while writing the compiled classes to the target");
		}
	}

	private static String toClassName(String fileName) {
		return fileName.substring(0, fileName.length()-".class".length()).replace('/', '.');
	}
}
