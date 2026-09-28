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
package tests.galtajs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.monflabs.playground.ExecutionContext;
import org.monflabs.playground.ExecutionResult;
import org.monflabs.playground.PlaygroundConfiguration;
import org.monflabs.playground.Snippet;
import org.monflabs.playground.SnippetFactory;
import org.monflabs.tests.UnitTestSupport;

import com.monflabs.playground.galtajs.GaltaJSExecutionEngine;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import tests.util.TestExecutionContext;

/**
 * One test per playground example under src/main/resources/snippets - a
 * regression guard confirming every example still executes without an
 * uncaught exception, matching the playground UI's own defaults (GaltaJS
 * extensions and strict mode both enabled).
 */
public class AllSnippetsExecutionTest extends TestSuite {

	public static Test suite() throws Exception {
		TestSuite suite = new TestSuite("AllSnippetsExecutionTest");
		Path root = new UnitTestSupport(AllSnippetsExecutionTest.class)
				.getMainResourcesDirectory().toPath().resolve("snippets");
		// SnippetEnvironment.newBuilder() (called from inside
		// GaltaJSExecutionEngine._execute() for every example) reaches into
		// PlaygroundConfiguration.get().getSnippetFactory() - both for its
		// own module resolver and, once, from a static field initializer
		// evaluated the first time the class loads. Point it at the real
		// examples directory rather than relying on ProjectTestCase's
		// classpath-backed factory, which resolves to src/test/resources'
		// unrelated single-fixture "snippets" folder (target/test-classes
		// shadows target/classes on the test classpath).
		PlaygroundConfiguration.get().setSnippetFactory(new SnippetFactory(root));
		for (Path example : findExamples(root)) {
			suite.addTest(new SnippetCase(root, example));
		}
		return suite;
	}

	// A leaf directory is a runnable example iff it directly contains
	// main.js - every real example follows this convention, including the
	// multi-file ones (Snippet.createSnippetFs() copies every other file in
	// the same folder, plus anything mapped by _links.properties, into the
	// snippet's own in-memory filesystem automatically).
	private static List<Path> findExamples(Path root) throws Exception {
		List<Path> examples = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(root)) {
			walk.filter(Files::isDirectory)
				.filter(dir -> Files.isRegularFile(dir.resolve(GaltaJSExecutionEngine.DEFAULT_JS)))
				.forEach(examples::add);
		}
		examples.sort(Comparator.comparing(Path::toString));
		return examples;
	}

	/** One example, run once and asserted not to throw. */
	private static final class SnippetCase extends TestCase {
		private final Path exampleDir;

		SnippetCase(Path root, Path exampleDir) {
			super(root.relativize(exampleDir).toString());
			this.exampleDir = exampleDir;
		}

		@Override
		protected void runTest() throws Throwable {
			Snippet snippet = new Snippet(exampleDir);
			ExecutionContext ctx = new TestExecutionContext(snippet) {
				@Override
				public Object getExecutionOption(String key, Object defaultValue) {
					// Matches GaltaJSPlaygroundFrame's own UI checkbox defaults.
					if (GaltaJSExecutionEngine.OPTION_GALTAJS.equals(key)) {
						return true;
					}
					if (GaltaJSExecutionEngine.OPTION_STRICTMODE.equals(key)) {
						return true;
					}
					return defaultValue;
				}
			};
			GaltaJSExecutionEngine engine = new GaltaJSExecutionEngine(ctx);
			ExecutionResult result = engine.execute();
			if (result.getException() != null) {
				throw new AssertionError("Example threw: " + getName(), result.getException());
			}
		}
	}
}
