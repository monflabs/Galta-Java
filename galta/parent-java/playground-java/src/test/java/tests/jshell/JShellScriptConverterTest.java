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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.monflabs.filesystem.memory.MemoryFileSystem;
import org.monflabs.javacompiler.JavaCompiler;
import org.monflabs.javacompiler.JavaCompilerFactory;
import org.monflabs.util.path.PathClassLoader;

import playground.impl.engine.jshell.JShellScriptConverter;
import tests.ProjectTestCase;

public class JShellScriptConverterTest extends ProjectTestCase {

	// Converts, compiles and runs a script; returns what it prints
	private static String run(String script) throws Exception {
		String source = JShellScriptConverter.toJavaClass(script, "Main");
		try(var src = MemoryFileSystem.newBuilder().build(); var out = MemoryFileSystem.newBuilder().build()) {
			Files.writeString(src.getPath("/Main.java"), source);
			try(JavaCompiler c = JavaCompilerFactory.newBuilder()
					.classLoader(JShellScriptConverterTest.class.getClassLoader())
					.sourceFolder(src, StandardCharsets.UTF_8)
					.targetFolder(out)
					.build()) {
				c.compile("Main");
			}
			Method main = new PathClassLoader(JShellScriptConverterTest.class.getClassLoader(), out).loadClass("Main").getMethod("main", String[].class);
			PrintStream saved = System.out;
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			System.setOut(new PrintStream(bos, true, StandardCharsets.UTF_8));
			try {
				main.invoke(null, (Object)new String[0]);
			} finally {
				System.setOut(saved);
			}
			return bos.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
		}
	}

	public void testStatementsAndImports() throws Exception {
		// JShell's default imports (java.util.*) and a hoisted import
		assertEquals("[1, 2]\n3\n", run("""
				import java.util.concurrent.atomic.AtomicInteger;
				var l = new ArrayList<Integer>(List.of(1, 2));
				System.out.println(l);
				var n = new AtomicInteger(3);
				System.out.println(n)
				"""));
	}

	public void testMethodsAndClasses() throws Exception {
		assertEquals("6\nP[x=1]\nhi\n", run("""
				int twice(int x) {
					return x * 2;   // a brace in a comment: {
				}
				record P(int x) {}
				class Greeter { String greet() { return "hi"; } }
				System.out.println(twice(3));
				System.out.println(new P(1));
				System.out.println(new Greeter().greet());
				"""));
	}

	public void testBlocksThatGoOn() throws Exception {
		assertEquals("caught\n3\nelse\nanonymous\n", run("""
				try {
					throw new IllegalStateException("}");
				} catch(IllegalStateException e) {
					System.out.println("caught");
				}
				int i = 0;
				do {
					i++;
				} while(i < 3);
				System.out.println(i);
				if(i > 5) {
					System.out.println("then");
				} else {
					System.out.println("else");
				}
				Runnable r = new Runnable() {
					public void run() { System.out.println("anonymous"); }
				};
				r.run();
				"""));
	}

	public void testStringsAndTextBlocks() throws Exception {
		assertEquals("{ ; }\n'}'\nline { ;\n", run("""
				System.out.println("{ ; }");
				System.out.println("'" + '}' + "'");
				String t = \"\"\"
					line { ;
					\"\"\";
				System.out.print(t);
				"""));
	}
}
