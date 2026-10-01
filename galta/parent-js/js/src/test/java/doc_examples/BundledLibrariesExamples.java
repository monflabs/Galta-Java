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

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.monflabs.galtajs.JSEnvironment;
import org.monflabs.galtajs.environments.JavaScriptEnvironment;
import org.monflabs.galtajs.library.StaticLibrary;
import org.monflabs.galtajs.library.UnitTestLibrary;
import org.monflabs.galtajs.library.node.NodeLibrary;
import org.monflabs.galtajs.library.node.NodeModuleResolver;
import org.monflabs.galtajs.library.platform.FetchLibrary;
import org.monflabs.galtajs.library.platform.HostLibrary;
import org.monflabs.galtajs.rt.JSRuntimeUncatchableException;
import org.monflabs.tests.__BaseTestCase;

import static doc_examples.DocExampleSupport.*;

/**
 * Samples for docs/GaltaJS/Extending/BundledLibraries.md
 */
public class BundledLibrariesExamples extends __BaseTestCase {

	public void testHostLibrary() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new HostLibrary()).build();
		assertEquals(List.of("aGVsbG8=", "hello"), list(env.evaluateExpression("[btoa('hello'), atob('aGVsbG8=')]")));
		assertEquals("done", (Object)env.evaluateScript("await new Promise(r => setTimeout(() => r('done'), 5))"));
		assertEquals(List.of("sync", "micro"), list(env.evaluateScript("let log = []; queueMicrotask(() => log.push('micro')); log.push('sync'); await null; log")));
	}

	public void testNodeFileSystemModules() throws Exception {
		Path file = Files.createTempFile("galta", ".txt");
		StaticLibrary globals = new StaticLibrary();
		globals.addStaticGlobal("path", file.toString());
		JSEnvironment env = JavaScriptEnvironment.newBuilder()
				.registerLibrary(new NodeLibrary())   // registers the 'fs' / 'fs/promises' module resolver
				.registerLibrary(globals)
				.build();
		assertEquals("hello", (Object)env.evaluateScript("import fs from 'node:fs'; fs.writeFileSync(path, 'hello'); fs.readFileSync(path, 'utf8')"));
		assertEquals("hello", (Object)env.evaluateScript("import { readFile } from 'node:fs/promises'; await readFile(path, 'utf8')"));
		assertEquals(true, (Object)env.evaluateScript("import { existsSync } from 'fs'; existsSync(path)"));
	}

	public void testNodeModulesInAGivenFileSystem() throws Exception {
		// A zip file system stands in for any java.nio.file.FileSystem (the
		// playground passes its snippet file system)
		Path zip = Files.createTempDirectory("galta").resolve("sandbox.zip");
		try(FileSystem fs = FileSystems.newFileSystem(zip, Map.of("create", "true"))) {
			JSEnvironment env = JavaScriptEnvironment.newBuilder()
					.addModuleResolver(new NodeModuleResolver(fs))
					.build();
			assertEquals("hello", (Object)env.evaluateScript("import fs from 'node:fs'; fs.writeFileSync('/note.txt', 'hello'); fs.readFileSync('/note.txt', 'utf8')"));
			assertEquals("hello", (Object)env.evaluateScript("import { readFile } from 'node:fs/promises'; await readFile('/note.txt', 'utf8')"));
			assertEquals("hello", Files.readString(fs.getPath("/note.txt")));
		}
	}

	public void testFetchLibraryObjects() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new FetchLibrary()).build();
		Object r = env.evaluateScript("""
			const headers = new Headers({ 'Content-Type': 'text/plain' });
			const request = new Request('https://example.com/items', { method: 'POST', headers });
			const response = new Response('body', { status: 201 });
			[typeof fetch, headers.get('content-type'), request.method, request.url, response.status]
			""");
		assertEquals("[function, text/plain, POST, https://example.com/items, 201]", String.valueOf(list(r)));
	}

	public void testUnitTestLibrary() {
		JSEnvironment env = JavaScriptEnvironment.newBuilder().registerLibrary(new UnitTestLibrary()).build();
		env.evaluateScript("assertEquals([1, 2], [1, 2]); assertTrue(1 < 2); assertUndefined(void 0)");
		// A failed assertion is uncatchable from JavaScript and reaches Java directly
		try {
			env.evaluateScript("try { assertEquals(1, 2) } catch(e) { /* never reached */ }");
			fail();
		} catch(JSRuntimeUncatchableException e) {
			assertTrue(e.getMessage().contains("Assertion error"));
		}
	}
}
