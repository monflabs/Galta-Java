/*
 * Copyright (c) 2023-2026 Philippe Riand
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
package playground.impl.engine.java;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;

import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.javacompiler.JavaCompiler;
import org.monflabs.javacompiler.JavaCompilerFactory;
import org.monflabs.playground.ExecutionContext;
import org.monflabs.playground.ExecutionEngine;
import org.monflabs.playground.ExecutionResult;
import org.monflabs.playground.PlaygroundException;
import org.monflabs.util.PathUtil;
import org.monflabs.util.path.PathClassLoader;

import playground.impl.engine.util.StreamThreadDispatcher;

public class JavaExecutionEngine extends ExecutionEngine {
	
	public static final String DEFAULT_JAVA = "Main.java";
	
	public JavaExecutionEngine(ExecutionContext context) {
		super(context);
	}
	
	@Override
	protected ExecutionResult _execute() throws Exception {
		ExecutionContext context = getExecutionContext();
		return compileAndRun(context, context.getSnippetFs(), PathUtil.FILE.removeExtension(DEFAULT_JAVA), getExecutionThread());
	}

	/**
	 * Compiles the Java sources of a file system, then runs the static main(String[])
	 * method of a class, with its console output going to the execution context.
	 */
	public static ExecutionResult compileAndRun(ExecutionContext context, FileSystem sources, String className, Thread executionThread) throws Exception {
		MemoryFileSystem tgtVfs = MemoryFileSystem.newBuilder().build();
		
		try(JavaCompiler cp = JavaCompilerFactory.newBuilder()
				.classLoader(JavaExecutionEngine.class.getClassLoader()) 
				.sourceFolder(sources,StandardCharsets.UTF_8)
				.targetFolder(tgtVfs)
				.build()) {
			cp.compile(className);
		}
		
		PathClassLoader cl = new PathClassLoader(JavaExecutionEngine.class.getClassLoader(), tgtVfs);
		Class<?> mainClass;
		try {
			mainClass = cl.loadClass(className);
		} catch(ClassNotFoundException e) {
			throw new PlaygroundException(null,"{0}.java must declare a class {0}, with a static main(String[]) method", className);
		}
		
		Method m = mainClass.getMethod("main", String[].class);
		if((m.getModifiers() & Modifier.STATIC) == 0) {
			throw new PlaygroundException(null,"main() is not a static method");
		}
		
		StreamThreadDispatcher.install();
		
		try {
			StreamThreadDispatcher.installThread(executionThread,context);
			try {
				m.invoke(null, (Object)new String[0]);
			} finally {
				StreamThreadDispatcher.uninstallThread(executionThread,context);
			}
			return new ExecutionResult();
		} catch(InvocationTargetException e) {
			throw new PlaygroundException(e.getCause(),"Error while executing Java class");
		} catch(Exception e) {
			throw new PlaygroundException(e,"Error while executing Java class");
		}
	}
}
