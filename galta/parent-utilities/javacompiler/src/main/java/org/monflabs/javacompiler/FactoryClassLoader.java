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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.monflabs.util.StringFormat;

/**
 * Class loader reading the classes from a {@link TargetFactory}.
 * <p>
 * The classes found in the factory take precedence over the parent class loader
 * (child-first): a compiled class is never shadowed by a class with the same name
 * on the application class path. Classes from the platform packages (<code>java.</code>,
 * <code>javax.</code>, <code>jdk.</code>, <code>sun.</code> and <code>com.sun.</code>)
 * are always delegated to the parent.
 * <p>
 * The other files of the factory (e.g. resources created by an annotation processor, such
 * as <code>META-INF/services</code> files) are served as resources, also child-first.
 * <p>
 * A class loader defines a class once: after the bytes of a class change in the factory
 * (a recompilation), a new class loader is needed to load the new version.
 * {@link JavaCompiler#getClassLoader()} takes care of this.
 */
public class FactoryClassLoader extends ClassLoader {
	
	static {
		registerAsParallelCapable();
	}
	
	private static final String[] PARENT_FIRST = {"java.", "javax.", "jdk.", "sun.", "com.sun."};
	
	private TargetFactory factory;
	private volatile boolean definedClasses;
	private final URLStreamHandler handler = new URLStreamHandler() {
		@Override
		protected URLConnection openConnection(URL u) throws IOException {
			String name = u.getPath().substring(1);
			return new URLConnection(u) {
				@Override
				public void connect() {
					// Nothing to connect
				}
				@Override
				public InputStream getInputStream() throws IOException {
					byte[] bytes = factory.readBytes(name);
					if(bytes==null) {
						throw new FileNotFoundException(name);
					}
					return new ByteArrayInputStream(bytes);
				}
			};
		}
	};
	
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
	
	private static boolean isParentFirst(String name) {
		for(String p: PARENT_FIRST) {
			if(name.startsWith(p)) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		if(isParentFirst(name)) {
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
	
	/**
	 * Read a resource from the factory.
	 * @return the bytes, or null if the factory does not have it
	 */
	private byte[] readResourceBytes(String name) {
		if(name==null) {
			return null;
		}
		String n = name.startsWith("/") ? name.substring(1) : name;
		// Never escape the factory root
		if(n.isEmpty() || n.equals("..") || n.startsWith("../") || n.contains("/../") || n.endsWith("/..")) {
			return null;
		}
		try {
			return factory.readBytes(n);
		} catch(IOException ex) {
			return null;
		}
	}
	
	private URL factoryURL(String name) {
		String n = name.startsWith("/") ? name.substring(1) : name;
		try {
			return URL.of(new URI("galta-factory", null, "/"+n, null), handler);
		} catch(URISyntaxException | IOException ex) {
			return null;
		}
	}
	
	@Override
	public InputStream getResourceAsStream(String name) {
		// Child-first, like the classes: makes the compiled .class files and the generated
		// resources readable (e.g. by bytecode libraries)
		byte[] bytes = readResourceBytes(name);
		if(bytes!=null) {
			return new ByteArrayInputStream(bytes);
		}
		return super.getResourceAsStream(name);
	}

	@Override
	public URL getResource(String name) {
		// Child-first, as for the classes
		URL u = findResource(name);
		return u!=null ? u : super.getResource(name);
	}

	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		// Child-first too: the factory's resource, then the parent's ones
		Enumeration<URL> parent = super.getResources(name);
		URL u = findResource(name);
		if(u==null) {
			return parent;
		}
		List<URL> all = new ArrayList<>();
		all.add(u);
		while(parent.hasMoreElements()) {
			URL p = parent.nextElement();
			if(!p.equals(u)) {
				all.add(p);
			}
		}
		return Collections.enumeration(all);
	}
	
	@Override
	protected URL findResource(String name) {
		if(readResourceBytes(name)!=null) {
			return factoryURL(name);
		}
		return null;
	}
	
	@Override
	protected Enumeration<URL> findResources(String name) throws IOException {
		URL u = findResource(name);
		return u!=null ? Collections.enumeration(Collections.singletonList(u)) : Collections.emptyEnumeration();
	}
	
	public Class<?> alreadyLoaded(String className) {
		return findLoadedClass(className);
	}
}
