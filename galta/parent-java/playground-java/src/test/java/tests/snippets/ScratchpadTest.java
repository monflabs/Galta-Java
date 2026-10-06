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

import static org.junit.Assert.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.monflabs.playground.PlaygroundException;
import org.monflabs.playground.SnippetFactory;
import org.monflabs.util.path.FilesUtil;

import playground.impl.engine.java.JavaExecutionEngine;
import playground.impl.engine.jshell.JShellExecutionEngine;
import tests.ProjectTestCase;
import tests.util.TestExecutionContext;

/**
 * The scratchpad of the Java playground: a new one holds only a comment, and runs.
 */
public class ScratchpadTest extends ProjectTestCase {

	private static final String COMMENT = "// Scratchpad - saved automatically, not part of the snippet library\n";

	private TestExecutionContext context(Path root, String file, String content) throws Exception {
		Path folder = Files.createDirectories(root.resolve("scratch"));
		Files.writeString(folder.resolve(file), content);
		return new TestExecutionContext(new SnippetFactory(root).getSnippet("scratch"));
	}

	public void testNewJShellScratchpadRuns() throws Exception {
		Path root = Files.createTempDirectory("scratchpad");
		try {
			TestExecutionContext ctx = context(root, JShellExecutionEngine.DEFAULT_JSHELL, COMMENT);
			new JShellExecutionEngine(ctx).execute();   // nothing to run, no error
			assertEquals("", ctx.getConsoleText());
		} finally {
			FilesUtil.deleteRecursively(root);
		}
	}

	public void testMainJavaWithoutClass() throws Exception {
		Path root = Files.createTempDirectory("scratchpad");
		try {
			TestExecutionContext ctx = context(root, JavaExecutionEngine.DEFAULT_JAVA, COMMENT);
			PlaygroundException e = assertThrows(PlaygroundException.class, () -> new JavaExecutionEngine(ctx).execute());
			assertEquals("Main.java must declare a class Main, with a static main(String[]) method", e.getMessage());
		} finally {
			FilesUtil.deleteRecursively(root);
		}
	}
}
