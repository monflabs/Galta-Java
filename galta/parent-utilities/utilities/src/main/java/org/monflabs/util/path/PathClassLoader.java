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
package org.monflabs.util.path;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;

import org.monflabs.util.StringFormat;

/**
 * FileSystem Class loader 
 * 
 * @author Philippe Riand
 */
public class PathClassLoader extends ClassLoader {
	
	private Path path;
	
	public PathClassLoader(ClassLoader parent, FileSystem fs) {
		super(parent);
		this.path = FilesUtil.getRoot(fs);
	}
	public PathClassLoader(ClassLoader parent, Path path) {
		super(parent);
		this.path = path;
	}
	
	public Path getPath() {
		return path;
	}
	
    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
    	try {
	    	String classPath = name.replace(".", path.getFileSystem().getSeparator())+".class";
	    	
	    	Path file = path.resolve(classPath);
	    	if(Files.isRegularFile(file)) {
	    		byte[] bytes = Files.readAllBytes(file);
	    		definePackageFor(name);
	            return defineClass(name, bytes, 0, bytes.length);
	    	}
	    	return super.findClass(name);
    	} catch(IOException ex) {
    		throw new ClassNotFoundException(StringFormat.format("Error while loading class '{0}'",name),ex);
    	}
    }
    
    // So Class.getPackage() / getPackageName() based code works for the loaded classes
    private void definePackageFor(String className) {
    	int dot = className.lastIndexOf('.');
    	if(dot>0) {
    		String pkg = className.substring(0,dot);
    		if(getDefinedPackage(pkg)==null) {
    			try {
    				definePackage(pkg, null, null, null, null, null, null, null);
    			} catch(IllegalArgumentException ex) {
    				// Defined concurrently by another thread
    			}
    		}
    	}
    }
    
    // Resource names always use '/'
    private Path resourcePath(String name) {
    	if(name==null || name.isEmpty() || name.startsWith("/")) {
    		return null;
    	}
    	Path file = path.resolve(name.replace("/", path.getFileSystem().getSeparator())).normalize();
    	// A resource name cannot escape the root with ".."
    	if(!file.startsWith(path.normalize()) || !Files.isRegularFile(file)) {
    		return null;
    	}
    	return file;
    }

    /**
     * Returns the URL of a resource under the path. Some file systems (e.g. in memory ones)
     * don't provide URLs for their files: use {@link #getResourceAsStream(String)} for those.
     */
    @Override
    protected URL findResource(String name) {
    	Path file = resourcePath(name);
    	if(file!=null) {
    		try {
    			return file.toUri().toURL();
    		} catch(MalformedURLException | IllegalArgumentException | UnsupportedOperationException ex) {
    			// No URL handler for this file system
    		}
    	}
    	return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
    	URL url = findResource(name);
    	return url!=null ? Collections.enumeration(Collections.singletonList(url)) : Collections.emptyEnumeration();
    }
    
    @Override
    public InputStream getResourceAsStream(String name) {
    	InputStream is = super.getResourceAsStream(name);
    	if(is==null) {
    		// Works even when the file system doesn't provide URLs
    		Path file = resourcePath(name);
    		if(file!=null) {
    			try {
    				return Files.newInputStream(file);
    			} catch(IOException ex) {
    				return null;
    			}
    		}
    	}
    	return is;
    }
    
    public Class<?> alreadyLoaded(String className) {
        return findLoadedClass(className);
    }
}
