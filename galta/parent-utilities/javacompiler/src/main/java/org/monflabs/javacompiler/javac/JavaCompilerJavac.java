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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.monflabs.javacompiler.JavaCompilerException;
import org.monflabs.javacompiler.SourceFactory;
import org.monflabs.javacompiler.TargetFactory;
import org.monflabs.util.Console;


/**
 * Java compiler based on the JDK compiler (javac).
 * <p>
 * The class path used by javac is made of the JVM class path (<code>java.class.path</code>),
 * the <code>file:</code> URLs of the {@link URLClassLoader}s found in the parent class loader
 * chain, and the classes of the target factory and of the parent <code>FactoryClassLoader</code>s.
 * It is left untouched when the options set it explicitly (<code>-classpath</code>, <code>-cp</code>
 * or <code>--class-path</code>).
 */
public class JavaCompilerJavac extends org.monflabs.javacompiler.JavaCompiler {

	private boolean failOnWarnings;
	
	// Runtime members
	private JavaCompiler javac;
	private StandardJavaFileManager stdFileManager;
	private PathFileManager fileManager;

	public JavaCompilerJavac(ClassLoader parentClassLoader, SourceFactory sourceFactory, TargetFactory targetFactory, List<String> options, boolean failOnWarnings) {
		super(parentClassLoader,sourceFactory,targetFactory,options);
		this.failOnWarnings = failOnWarnings;
		
		this.javac = ToolProvider.getSystemJavaCompiler();
		if(javac==null) {
			throw new JavaCompilerException(null,"No Java compiler available: the application must run on a JDK, not a JRE (the jdk.compiler module is missing)");
		}
		this.stdFileManager = javac.getStandardFileManager(null, null, null);
		if(!hasClassPathOption(options)) {
			List<File> cp = computeClassPath(parentClassLoader);
			if(cp!=null) {
				try {
					stdFileManager.setLocation(StandardLocation.CLASS_PATH, cp);
				} catch(IOException ex) {
					throw new JavaCompilerException(ex,"Cannot set the compiler class path");
				}
			}
		}
		// The compiler's class loader is only exposed to the annotation processors: use its parent,
		// as the compiler class loader instance changes after a recompilation
		this.fileManager = new PathFileManager(stdFileManager, getTargetFactory(), new org.monflabs.javacompiler.FactoryClassLoader(parentClassLoader, getTargetFactory()));
	}
	
	private static boolean hasClassPathOption(List<String> options) {
		if(options!=null) {
			for(String o: options) {
				if(o.equals("-classpath") || o.equals("-cp") || o.equals("--class-path") || o.startsWith("--class-path=")) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * The JVM class path, extended with the URLs of the URL class loaders in the chain.
	 * @return the class path, or null if there is nothing to add to the default one
	 */
	private static List<File> computeClassPath(ClassLoader parent) {
		Set<File> extra = new LinkedHashSet<>();
		for(ClassLoader c=parent; c!=null; c=c.getParent()) {
			if(c instanceof URLClassLoader u) {
				for(URL url: u.getURLs()) {
					if("file".equals(url.getProtocol())) {
						try {
							extra.add(Path.of(url.toURI()).toFile());
						} catch(URISyntaxException | IllegalArgumentException ex) {
							// Not a usable file URL
						}
					}
				}
			}
		}
		if(extra.isEmpty()) {
			return null;
		}
		Set<File> cp = new LinkedHashSet<>();
		String jvmCp = System.getProperty("java.class.path");
		if(jvmCp!=null) {
			for(String e: jvmCp.split(File.pathSeparator)) {
				if(!e.isEmpty()) {
					cp.add(new File(e));
				}
			}
		}
		cp.addAll(extra);
		return new ArrayList<>(cp);
	}
	
	@Override
	public synchronized void close() {
		javac = null;
		if(stdFileManager!=null) {
			try {
				stdFileManager.close();
			} catch(IOException ex) {
				Console.log(ex);
			}
			stdFileManager= null;
		}
	}
	
	@Override
	protected List<String> doCompile(List<String> sources) {
		if(javac==null) {
			throw new JavaCompilerException(null,"Compiler is closed");
		}
		
		DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
		
		List<PathSourceFile> compilationUnits = new ArrayList<>();
		for(String name: sources) {
			compilationUnits.add(new PathSourceFile(getSourceFactory(), name));
		}
		
		JavaCompiler.CompilationTask task = javac.getTask(null, fileManager, collector, getOptions(), null, compilationUnits);
		boolean result = task.call();
		List<String> warnings = new ArrayList<>();
		if (!result || collector.getDiagnostics().size() > 0) {
			StringBuilder exceptionMsg = new StringBuilder();
			exceptionMsg.append("Unable to compile the source");
			boolean hasWarnings = false;
			// A failed task is a failure even when javac reported no ERROR diagnostic
			boolean hasErrors = !result;
			for (Diagnostic<? extends JavaFileObject> d : collector.getDiagnostics()) {
				String formatted = format(d);
				switch (d.getKind()) {
				case NOTE:
				case OTHER:
					// Informational: neither a warning nor an error
					break;
				case MANDATORY_WARNING:
				case WARNING:
					hasWarnings = true;
					warnings.add(formatted);
					break;
				case ERROR:
				default:
					hasErrors = true;
					break;
				}
				exceptionMsg.append("\n").append(formatted);
			}
			if (hasWarnings && failOnWarnings || hasErrors) {
				throw new JavaCompilerException(null,exceptionMsg.toString());
			}
		}
		return warnings;
	}
	
	private static String format(Diagnostic<? extends JavaFileObject> d) {
		StringBuilder b = new StringBuilder();
		b.append("[kind=").append(d.getKind());
		b.append(", ").append("line=").append(d.getLineNumber());
		b.append(", ").append("col=").append(d.getColumnNumber());
		b.append(", ").append("message=").append(d.getMessage(Locale.US)).append("]");
		return b.toString();
	}
}
