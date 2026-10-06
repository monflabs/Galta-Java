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
package tests.snippets;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.monflabs.playground.ExecutionEngine;
import org.monflabs.playground.Snippet;
import org.monflabs.playground.SnippetFactory;

import playground.impl.engine.java.JavaExecutionEngine;
import playground.impl.engine.jshell.JShellExecutionEngine;
import tests.ProjectTestCase;
import tests.util.TestExecutionContext;

/**
 * Runs every snippet packaged with the playground (src/main/resources/snippets): a change
 * of the Galta API that breaks one fails the build, like the doc_examples of the guides.
 */
public class PackagedSnippetsTest extends ProjectTestCase {

	public void testAllSnippetsRun() throws Exception {
		Path root = new File(support.getProjectRoot(), "src/main/resources/snippets").toPath();
		SnippetFactory factory = new SnippetFactory(root);
		List<Path> folders;
		try(Stream<Path> files = Files.walk(root)) {
			folders = files.filter(p -> {
					String name = p.getFileName().toString();
					return name.equals(JShellExecutionEngine.DEFAULT_JSHELL) || name.equals(JavaExecutionEngine.DEFAULT_JAVA);
				})
				.map(Path::getParent)
				.distinct()
				.sorted()
				.toList();
		}
		assertTrue("No snippet found in "+root, folders.size()>10);
		List<String> failures = new ArrayList<>();
		for(Path folder: folders) {
			String name = root.relativize(folder).toString().replace(File.separatorChar, '/');
			boolean jshell = Files.exists(folder.resolve(JShellExecutionEngine.DEFAULT_JSHELL));
			// A JShell script runs with JShell, and converted to a Java class as on a runtime
			// without JShell (CheerpJ): both must print the same
			String jshellOutput = run(factory, name, jshell, false, failures);
			if(jshell) {
				String convertedOutput = run(factory, name+" (converted)", jshell, true, failures);
				if(jshellOutput!=null && convertedOutput!=null && !normalize(jshellOutput).equals(normalize(convertedOutput))) {
					failures.add(name+": the converted script prints\n"+convertedOutput+"\ninstead of\n"+jshellOutput);
				}
			}
		}
		assertTrue(folders.size()+" snippets, "+failures.size()+" failed:\n"+String.join("\n\n", failures), failures.isEmpty());
	}

	private String run(SnippetFactory factory, String name, boolean jshell, boolean convert, List<String> failures) {
		Snippet snippet = factory.getSnippet(name.replace(" (converted)", ""));
		TestExecutionContext ctx = new TestExecutionContext(snippet);
		ExecutionEngine engine = jshell ? new JShellExecutionEngine(ctx) : new JavaExecutionEngine(ctx);
		if(convert) {
			System.setProperty(JShellExecutionEngine.CONVERT_PROPERTY, "true");
		}
		try {
			engine.execute();
			if(ctx.getConsoleText().isBlank()) {
				failures.add(name+": printed nothing");
			}
			return ctx.getConsoleText();
		} catch(Throwable t) {
			failures.add(name+": "+t.getMessage()+(t.getCause()!=null ? " ("+t.getCause()+")" : ""));
			return null;
		} finally {
			System.clearProperty(JShellExecutionEngine.CONVERT_PROPERTY);
		}
	}

	// The output without what varies between two runs (times)
	private static String normalize(String output) {
		return output.replaceAll("\\d+ ms or more", "N ms or more").replace("\r\n", "\n");
	}
}
