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
package tests.javac;

import org.monflabs.playground.PlaygroundConfiguration;
import org.monflabs.playground.Snippet;
import org.monflabs.tests.UnitTestSupport;

import playground.impl.engine.java.JavaExecutionEngine;
import tests.ProjectTestCase;
import tests.util.TestExecutionContext;

public class ExecuteJavaTest extends ProjectTestCase {

	public void testJavaEngine() throws Exception {
		Snippet hw = PlaygroundConfiguration.get().getSnippetFactory().getSnippet("Hello Java");
		
		TestExecutionContext ctx = new TestExecutionContext(hw);

		JavaExecutionEngine eng = new JavaExecutionEngine(ctx);
		eng.execute();
		
		assertEquals( "Hello, world!\n", UnitTestSupport.normalizeLineBreaks(ctx.getConsoleText()));
	}	
}
