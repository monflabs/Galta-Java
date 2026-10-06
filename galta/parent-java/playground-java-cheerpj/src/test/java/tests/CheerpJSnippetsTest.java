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
package tests;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.monflabs.filesystem.resources.ResourceFileSystem;
import org.monflabs.playground.ExecutionContext;
import org.monflabs.playground.ExecutionEngine;
import org.monflabs.playground.Snippet;
import org.monflabs.playground.SnippetFactory;
import org.monflabs.tests.__BaseTestCase;

import playground.impl.engine.java.JavaExecutionEngine;
import playground.impl.engine.jshell.JShellExecutionEngine;

/**
 * Runs every snippet of the Java playground as in the browser: the tests of this module run
 * without the jdk.compiler and jdk.jshell modules (see the pom), so the Java snippets are
 * compiled by the Eclipse compiler, and the JShell ones converted to a class first.
 */
public class CheerpJSnippetsTest extends __BaseTestCase {

	public void testRuntimeIsLikeCheerpJ() {
		assertFalse("jdk.jshell must not be available", JShellExecutionEngine.isJShellAvailable());
		assertNull("jdk.compiler must not be available", javax.tools.ToolProvider.getSystemJavaCompiler());
	}

	public void testAllSnippetsRun() throws Exception {
		// As on CheerpJ, a Java home without lib/jrt-fs.jar and without a release file: the
		// compiler reads the JDK classes from the jrt:/ file system of the runtime
		// The runtime's jrt:/ file system, created from the real Java home (CheerpJ's exists
		// from the start; the JDK's is created on first use, from java.home)
		java.nio.file.FileSystems.getFileSystem(java.net.URI.create("jrt:/"));
		String savedHome = System.getProperty("java.home");
		Path home = Files.createTempDirectory("cheerpj-home");
		Files.createDirectories(home.resolve("lib"));
		System.setProperty("java.home", home.toString());
		try {
			assertTrue(playground.EcjJrtSupport.install());
			runAllSnippets();
		} finally {
			System.setProperty("java.home", savedHome);
			org.monflabs.util.path.FilesUtil.deleteRecursively(home);
		}
	}

	private void runAllSnippets() throws Exception {
		FileSystem fs = ResourceFileSystem.newBuilder()
				.classLoader(CheerpJSnippetsTest.class.getClassLoader())
				.root("snippets")
				.build();
		SnippetFactory factory = new SnippetFactory(fs);
		List<Path> folders;
		try(Stream<Path> files = Files.walk(fs.getPath("/"))) {
			folders = files.filter(p -> p.getFileName()!=null && p.getFileName().toString().startsWith("Main."))
					.map(Path::getParent)
					.distinct()
					.sorted()
					.toList();
		}
		assertTrue("No snippet found", folders.size()>10);
		List<String> failures = new ArrayList<>();
		for(Path folder: folders) {
			String name = fs.getPath("/").relativize(folder).toString();
			Snippet snippet = factory.getSnippet(name);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			PrintStream ps = new PrintStream(bos, true);
			ExecutionContext ctx = new ExecutionContext(snippet) {
				@Override
				public PrintStream getConsoleOut() {
					return ps;
				}
				@Override
				public PrintStream getConsoleErr() {
					return ps;
				}
				@Override
				public String getConsoleText() {
					return bos.toString();
				}
			};
			ExecutionEngine engine = Files.exists(folder.resolve(JShellExecutionEngine.DEFAULT_JSHELL))
					? new JShellExecutionEngine(ctx)
					: new JavaExecutionEngine(ctx);
			try {
				engine.execute();
				if(bos.toString().isBlank()) {
					failures.add(name+": printed nothing");
				}
			} catch(Throwable t) {
				failures.add(name+": "+t.getMessage()+(t.getCause()!=null ? " ("+t.getCause()+")" : ""));
			}
		}
		assertTrue(folders.size()+" snippets, "+failures.size()+" failed:\n"+String.join("\n\n", failures), failures.isEmpty());
	}
}
