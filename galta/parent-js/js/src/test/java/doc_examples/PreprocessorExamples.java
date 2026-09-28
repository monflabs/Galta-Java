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
package doc_examples;

import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.preprocessor.PreProcessorException;
import org.monflabs.galtajs.preprocessor.ScriptPreProcessor;
import org.monflabs.tests.__BaseTestCase;

/**
 * Samples for docs/GaltaJS/UserGuide/Preprocessor.md
 */
public class PreprocessorExamples extends __BaseTestCase {

	private static final String SOURCE = """
		let mode;
		// #if DEBUG
		mode = 'debug';
		// #else
		mode = 'prod';
		// #endif
		mode
		""";

	public void testConditionalBlocks() {
		// The preprocessor is a separate step: the host runs it before compiling
		String debug = ScriptPreProcessor.preprocess(SOURCE, Map.of("DEBUG", true));
		String prod  = ScriptPreProcessor.preprocess(SOURCE, Map.of("DEBUG", false));

		JSEnvironment env = JavaScriptEnvironment.create();
		assertEquals("debug", (Object)env.evaluateScript(debug));
		assertEquals("prod", (Object)env.evaluateScript(prod));

		// Suppressed lines are commented out, not removed, so line numbers are preserved
		assertTrue(debug.contains("//mode = 'prod';"));
		assertEquals(SOURCE.lines().count(), debug.lines().count());
	}

	public void testSymbolsAreTruthyValues() {
		String r = ScriptPreProcessor.preprocess("// #if LEVEL\nx\n// #endif", Map.of("LEVEL", 0));
		assertTrue(r.contains("//x"));
		r = ScriptPreProcessor.preprocess("// #if NAME\nx\n// #endif", Map.of("NAME", "prod"));
		assertFalse(r.contains("//x"));
		// An unknown symbol is false
		r = ScriptPreProcessor.preprocess("// #if OTHER\nx\n// #endif", Map.of());
		assertTrue(r.contains("//x"));
	}

	public void testSourceWithoutDirectivesIsReturnedAsIs() {
		String source = "const a = 1; // # not a directive\n";
		assertEquals(source, ScriptPreProcessor.preprocess(source, Map.of()));
	}

	public void testNestedIfIsNotSupported() {
		try {
			ScriptPreProcessor.preprocess("// #if A\n// #if B\nx\n// #endif\n// #endif", Map.of("A", true, "B", true));
			fail();
		} catch(PreProcessorException e) {
			assertTrue(e.getMessage().contains("#if cannot be nested"));
		}
	}
}
