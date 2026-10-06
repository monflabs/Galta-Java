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
package playground.impl.engine.jshell;

import java.nio.file.FileSystem;
import java.nio.file.Files;

import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.playground.ExecutionContext;
import org.monflabs.playground.ExecutionEngine;
import org.monflabs.playground.ExecutionResult;
import org.monflabs.playground.PlaygroundException;
import org.monflabs.util.StringUtil;

import playground.impl.engine.java.JavaExecutionEngine;
import playground.impl.engine.jshell.engine.JShellEngine;
import playground.impl.engine.util.StreamThreadDispatcher;

public class JShellExecutionEngine extends ExecutionEngine {
	
	public static final String DEFAULT_JSHELL = "Main.jshell";

	/**
	 * Set to true, the scripts are always converted to a Java class instead of being run by
	 * JShell: what happens on a runtime without JShell (used by the tests).
	 */
	public static final String CONVERT_PROPERTY = "playground.jshell.convert";

	private static final String CONVERTED_CLASS = "Main";

	private static Boolean jshellAvailable;

	/**
	 * Whether JShell (the jdk.jshell module) is available: not on a JRE, nor on CheerpJ in
	 * the browser.
	 */
	public static boolean isJShellAvailable() {
		if(Boolean.getBoolean(CONVERT_PROPERTY)) {
			return false;
		}
		if(jshellAvailable==null) {
			try {
				Class.forName("jdk.jshell.JShell");
				jshellAvailable = Boolean.TRUE;
			} catch(Throwable t) {
				jshellAvailable = Boolean.FALSE;
			}
		}
		return jshellAvailable;
	}

	// Without JShell: the script converted to a class, compiled and run
	private ExecutionResult runConverted(ExecutionContext context, String script) throws Exception {
		String source = JShellScriptConverter.toJavaClass(script, CONVERTED_CLASS);
		try(FileSystem fs = MemoryFileSystem.newBuilder().build()) {
			Files.writeString(fs.getPath("/"+CONVERTED_CLASS+".java"), source);
			return JavaExecutionEngine.compileAndRun(context, fs, CONVERTED_CLASS, getExecutionThread());
		}
	}
	
	public JShellExecutionEngine(ExecutionContext context) {
		super(context);
	}
	
	@Override
	protected ExecutionResult _execute() throws Exception {
		ExecutionContext context = getExecutionContext();
		String mainFile = DEFAULT_JSHELL;
		if(StringUtil.isNotEmpty(mainFile)) {
			String mainSource = context.getContent(mainFile);
			if(StringUtil.isNotEmpty(mainSource)) {
				if(!isJShellAvailable()) {
					return runConverted(context, mainSource);
				}
				StreamThreadDispatcher.install();
				
				Thread t = getExecutionThread();
				StreamThreadDispatcher.installThread(t,context);
				try {
					JShellEngine shell = new JShellEngine();
					shell.execute(mainSource);	
					return new ExecutionResult();
				} finally {
					StreamThreadDispatcher.uninstallThread(t,context);
				}
			}
		}
		throw new PlaygroundException(null,"Error while executing Java shell");
	}
}
