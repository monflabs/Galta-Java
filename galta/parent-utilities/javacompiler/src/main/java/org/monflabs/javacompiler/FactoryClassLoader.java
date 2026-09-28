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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.monflabs.util.StringFormat;

/**
 * Class loader reading the classes from a {@link TargetFactory}.
 * <p>
 * The classes found in the factory take precedence over the parent class loader
 * (child-first): a compiled class is never shadowed by a class with the same name
 * on the application class path. Classes from the <code>java.*</code> packages are
 * always delegated, as they cannot be redefined.
 * <p>
 * A class loader defines a class once: after the bytes of a class change in the factory
 * (a recompilation), a new class loader is needed to load the new version.
 * {@link JavaCompiler#getClassLoader()} takes care of this.
 */
public class FactoryClassLoader extends ClassLoader {
	
	static {
		registerAsParallelCapable();
	}
	
	private TargetFactory factory;
	private volatile boolean definedClasses;
	
	public FactoryClassLoader(ClassLoader parent, TargetFactory factory) {
		super(parent);
		this.factory = factory;
	}
	
	public TargetFactory getFileFactory() {
		return factory;
	}
	
	/**
	 * Tell if this class loader already defined at least one class from its factory.
	 * @return true if a class was defined
	 */
	public boolean hasDefinedClasses() {
		return definedClasses;
	}
	
	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		if(name.startsWith("java.")) {
			return super.loadClass(name, resolve);
		}
		synchronized(getClassLoadingLock(name)) {
			Class<?> c = findLoadedClass(name);
			if(c==null) {
				byte[] bytes = readClassBytes(name);
				if(bytes!=null) {
					c = defineClass(name, bytes, 0, bytes.length);
					definedClasses = true;
				} else {
					return super.loadClass(name, resolve);
				}
			}
			if(resolve) {
				resolveClass(c);
			}
			return c;
		}
	}
	
	@Override
	public Class<?> findClass(String name) throws ClassNotFoundException {
		// Only reached for a class that the parent does not have (see loadClass)
		byte[] bytes = readClassBytes(name);
		if(bytes!=null) {
			definedClasses = true;
			return defineClass(name, bytes, 0, bytes.length);
		}
		return super.findClass(name);
	}
	
	private byte[] readClassBytes(String name) throws ClassNotFoundException {
		try {
			return factory.readBytes(name.replace('.', '/')+".class");
		} catch(IOException ex) {
			throw new ClassNotFoundException(StringFormat.format("Error while loading class '{0}'",name),ex);
		}
	}
	
	@Override
	public InputStream getResourceAsStream(String name) {
		// Makes the compiled .class files readable as resources (e.g. by bytecode libraries)
		if(name.endsWith(".class")) {
			try {
				byte[] bytes = factory.readBytes(name.startsWith("/") ? name.substring(1) : name);
				if(bytes!=null) {
					return new ByteArrayInputStream(bytes);
				}
			} catch(IOException ex) {
				// Fall back to the parent
			}
		}
		return super.getResourceAsStream(name);
	}
	
	public Class<?> alreadyLoaded(String className) {
		return findLoadedClass(className);
	}
}
