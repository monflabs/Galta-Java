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
package tests.jshell;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.monflabs.playground.PlaygroundException;

import playground.impl.engine.jshell.engine.JShellEngine;
import tests.ProjectTestCase;

public class JShellTest extends ProjectTestCase {
	
	static final String HELLO_WORLD = 
"""
var three = 1 + 2;
System.out.println("one+two="+three);
""";	

	// The snippets run in this JVM: their System.out is the process one
	private static String run(String script) throws Exception {
		PrintStream saved = System.out;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		System.setOut(new PrintStream(bos, true, StandardCharsets.UTF_8));
		try {
			new JShellEngine().execute(script);
		} finally {
			System.setOut(saved);
		}
		return bos.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	public void testJShell() throws Exception {
		assertEquals("one+two=3\n", run(HELLO_WORLD));
	}

	public void testRedeclaredVariable() throws Exception {
		// The OVERWRITTEN event of the first declaration used to end in "Unknown Jshell error"
		assertEquals("2\n", run("int x = 1;\nint x = 2;\nSystem.out.println(x);\n"));
	}

	public void testTrailingComment() throws Exception {
		assertEquals("ok\n", run("System.out.println(\"ok\");\n// done\n   \n"));
	}

	public void testErrors() throws Exception {
		try {
			run("int y = \"text\";");
			fail();
		} catch(PlaygroundException ex) {
			assertTrue(ex.getMessage(), ex.getMessage().contains("int y"));
		}
		try {
			run("throw new IllegalStateException(\"boom\");");
			fail();
		} catch(PlaygroundException ex) {
			// Class, message and source are all reported
			assertTrue(ex.getMessage(), ex.getMessage().contains("IllegalStateException"));
			assertTrue(ex.getMessage(), ex.getMessage().contains("boom"));
			assertTrue(ex.getMessage(), ex.getMessage().contains("throw new"));
		}
	}
}
